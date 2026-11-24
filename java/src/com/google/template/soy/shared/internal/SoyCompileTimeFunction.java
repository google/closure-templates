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

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import java.util.List;

/**
 * An internal {@link SoySourceFunction} evaluated at compile time using {@link SoyCompilerContext}.
 * Implementations are implicitly pure and assumed to have constant arguments.
 */
public interface SoyCompileTimeFunction extends SoySourceFunction {

  /** Evaluates this function at compile time for the given arguments and compiler context. */
  SoyValue eval(List<SoyValue> args, SoyCompilerContext context);
}
