/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

/** Constants for standard names used by the compiler. */
final class StandardNames {
  static final String PARAMS_FIELD = "$params";
  static final String IJ_FIELD = "$ij";
  static final String COMPILED_TEMPLATE = "$template";
  static final String STATE_FIELD = "$state";
  static final String RENDER_CONTEXT_FIELD = "$renderContext";
  static final String CURRENT_CALLEE_FIELD = "$currentCallee";
  static final String CURRENT_RENDEREE_FIELD = "$currentRenderee";
  static final String CURRENT_APPENDABLE_FIELD = "$currentAppendable";

  private StandardNames() {}
}
