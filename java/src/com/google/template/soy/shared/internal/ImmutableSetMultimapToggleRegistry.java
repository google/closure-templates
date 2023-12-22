/*
 * Copyright 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.shared.internal;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.shared.ToggleRegistry;

/** Implementation of {@link ToggleRegistry} used throughout compiler. */
public final class ImmutableSetMultimapToggleRegistry implements ToggleRegistry {
  private final ImmutableSetMultimap<SourceLogicalPath, String> filePathToToggleMap;

  public ImmutableSetMultimapToggleRegistry(
      ImmutableSetMultimap<SourceLogicalPath, String> filePathToToggleMap) {
    this.filePathToToggleMap = filePathToToggleMap;
  }

  @Override
  public ImmutableSet<String> getToggles(SourceLogicalPath path) {
    return filePathToToggleMap.get(path);
  }

  @Override
  public ImmutableSet<SourceLogicalPath> getPaths() {
    return filePathToToggleMap.keySet();
  }
}
