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

package com.google.template.soy.shared;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLogicalPath;

/** Registry of known toggles provided by the --toggle compiler flag. */
public interface ToggleRegistry {

  /** Get all toggles for given file path in toggle registry. */
  ImmutableSet<String> getToggles(SourceLogicalPath path);

  /** Get all file paths in toggle registry. */
  ImmutableSet<SourceLogicalPath> getPaths();

  ToggleRegistry EMPTY =
      new ToggleRegistry() {
        @Override
        public ImmutableSet<SourceLogicalPath> getPaths() {
          return ImmutableSet.of();
        }

        @Override
        public ImmutableSet<String> getToggles(SourceLogicalPath path) {
          return ImmutableSet.of();
        }
      };
}
