// Copyright 2021 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.devtools.build.lib.bazel.bzlmod;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.bazel.bzlmod.ModuleFileFunction.ModuleFileFunctionException;
import com.google.devtools.build.lib.starlarkbuildapi.repository.ModuleFileGlobalsApi;
import com.google.devtools.build.lib.starlarkbuildapi.repository.StarlarkOverrideApi;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;

/** Implementation of the global functions available to a module file. */
public class ModuleFileGlobals implements ModuleFileGlobalsApi<ModuleFileFunctionException> {

  private boolean moduleCalled = false;
  private final Module.Builder module = Module.builder();
  private final Map<String, ModuleKey> deps = new LinkedHashMap<>();
  private final Map<String, StarlarkOverrideApi> overrides = new HashMap<>();

  public ModuleFileGlobals() {}

  @Override
  public void module(String name, String version, String compatibilityLevel) throws EvalException {
    if (moduleCalled) {
      throw Starlark.errorf("the module() directive can only be called once");
    }
    moduleCalled = true;
    module.setName(name).setVersion(version);
    // TODO(wyv): compatibility level
  }

  @Override
  public void bazelDep(String name, String version, String repoName) throws EvalException {
    if (repoName.isEmpty()) {
      repoName = name;
    }
    if (deps.putIfAbsent(repoName, ModuleKey.create(name, version)) != null) {
      throw Starlark.errorf("a bazel_dep with the repo name %s already exists", repoName);
    }
  }

  @Override
  public void overrideDep(String name, StarlarkOverrideApi override) throws EvalException {
    StarlarkOverrideApi existingOverride = overrides.putIfAbsent(name, override);
    if (existingOverride != null) {
      throw Starlark.errorf("multiple overrides for dep %s found", name);
    }
  }

  private static ImmutableList<String> checkAllStrings(Iterable<?> iterable, String where)
      throws EvalException {
    ImmutableList.Builder<String> result = ImmutableList.builder();

    for (Object o : iterable) {
      if (!(o instanceof String)) {
        throw Starlark.errorf(
            "Expected sequence of strings for '%s' argument, but got '%s' item in the sequence",
            where, Starlark.type(o));
      }
      result.add((String) o);
    }

    return result.build();
  }

  @Override
  public StarlarkOverrideApi singleVersionOverride(
      String version, String registry, Iterable<?> patches, StarlarkInt patchStrip)
      throws EvalException {
    return SingleVersionOverride.create(
        version,
        registry,
        checkAllStrings(patches, "patches"),
        patchStrip.toInt("single_version_override.patch_strip"));
  }

  @Override
  public StarlarkOverrideApi archiveOverride(
      Object urls,
      String integrity,
      String stripPrefix,
      Iterable<?> patches,
      StarlarkInt patchStrip)
      throws EvalException {
    ImmutableList<String> urlList =
        urls instanceof String
            ? ImmutableList.of((String) urls)
            : checkAllStrings((Iterable<?>) urls, "urls");
    return ArchiveOverride.create(
        urlList,
        checkAllStrings(patches, "patches"),
        integrity,
        stripPrefix,
        patchStrip.toInt("archive_override.patch_strip"));
  }

  @Override
  public StarlarkOverrideApi gitOverride(
      String remote, String commit, Iterable<?> patches, StarlarkInt patchStrip)
      throws EvalException {
    return GitOverride.create(
        remote,
        commit,
        checkAllStrings(patches, "patches"),
        patchStrip.toInt("git_override.patch_strip"));
  }

  @Override
  public StarlarkOverrideApi localPathOverride(String path) {
    return LocalPathOverride.create(path);
  }

  public Module buildModule(Registry registry) {
    return module.setDeps(ImmutableMap.copyOf(deps)).setRegistry(registry).build();
  }

  public ImmutableMap<String, StarlarkOverrideApi> buildOverrides() {
    return ImmutableMap.copyOf(overrides);
  }
}
