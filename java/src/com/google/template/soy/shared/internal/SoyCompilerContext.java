/*
 * Copyright 2026 Google Inc.
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

import com.google.auto.value.AutoValue;

/** Compiler context passed when evaluating {@link SoyCompileTimeFunction}s during compilation. */
@AutoValue
public abstract class SoyCompilerContext {

  /** The output backend being compiled for. */
  public enum OutputTarget {
    JBCSRC,
    TOFU,
    JSSRC,
    IDOM,
    PYSRC,
    NONE
  }

  public static final SoyCompilerContext EMPTY = create(OutputTarget.NONE);

  public static SoyCompilerContext create(OutputTarget outputTarget) {
    return new AutoValue_SoyCompilerContext(outputTarget);
  }

  SoyCompilerContext() {}

  public abstract OutputTarget outputTarget();
}
