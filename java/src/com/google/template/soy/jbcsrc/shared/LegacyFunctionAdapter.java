/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.lang.reflect.Method;
import java.util.List;

/**
 * An adapter that SoySauceImpl installs for SoyJavaFunctions, which ExpressionCompiler delegates to
 * for running the java code.
 */
public final class LegacyFunctionAdapter {
  public static final Method METHOD =
      JavaValueFactory.createMethod(LegacyFunctionAdapter.class, "computeForJava", List.class);

  private final SoyJavaFunction legacyFn;

  public LegacyFunctionAdapter(SoyJavaFunction legacyFn) {
    this.legacyFn = legacyFn;
  }

  public SoyValue computeForJava(List<SoyValue> args) {
    return legacyFn.computeForJava(args);
  }

  @Override
  public String toString() {
    return "LegacyFunctionAdapter{" + legacyFn + "}";
  }
}
