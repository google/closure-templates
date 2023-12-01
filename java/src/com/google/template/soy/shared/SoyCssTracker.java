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

/**
 * Interface to track what CSS paths are required by the Soy files used during rendering. This hook
 * can be used by implementations to dynamically collect CSS needed by the rendered content. Note
 * that the same CSS path may be reported more than once, and that there is no guaranteed order in
 * which the CSS paths will be reported.
 */
public interface SoyCssTracker {
  void trackRequiredCssPath(String path);

  void trackRequiredCssNamespace(String namespace);
}
