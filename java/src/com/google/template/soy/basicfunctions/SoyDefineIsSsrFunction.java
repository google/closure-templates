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

package com.google.template.soy.basicfunctions;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.shared.internal.SoyCompileTimeFunction;
import com.google.template.soy.shared.internal.SoyCompilerContext;
import com.google.template.soy.shared.internal.SoyCompilerContext.OutputTarget;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import java.util.List;

/**
 * Compile-time define indicating whether the template is being compiled for server-side rendering
 * (TOFU or JBCSRC).
 */
@SoyFunctionSignature(name = "soyDefineIsSsr", value = @Signature(returnType = "bool"))
final class SoyDefineIsSsrFunction implements SoyCompileTimeFunction {

  @Override
  public SoyValue eval(List<SoyValue> args, SoyCompilerContext context) {
    OutputTarget target = context.outputTarget();
    return BooleanData.forValue(target == OutputTarget.JBCSRC || target == OutputTarget.TOFU);
  }
}
