/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.tofu.internal;

import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.sharedpasses.render.EvalVisitor;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.tofu.restricted.SoyTofuFunction;

import java.util.Deque;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * Version of {@code EvalVisitor} for the Tofu backend.
 *
 * <p> Uses {@code SoyTofuFunction}s instead of {@code SoyJavaRuntimeFunction}s.
 *
 * @author Kai Huang
 */
class TofuEvalVisitor extends EvalVisitor {


  /** Map of all SoyTofuFunctions (name to function). */
  private final Map<String, SoyTofuFunction> soyTofuFunctionsMap;


  /**
   * @param soyTofuFunctionsMap Map of all SoyTofuFunctions (name to function).
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param env The current environment.
   */
  protected TofuEvalVisitor(
      @Nullable Map<String, SoyTofuFunction> soyTofuFunctionsMap, SoyMapData data,
      @Nullable SoyMapData ijData, Deque<Map<String, SoyData>> env) {

    super(null, data, ijData, env);

    this.soyTofuFunctionsMap = soyTofuFunctionsMap;
  }


  @Override protected SoyData computeFunction(
      String fnName, List<SoyData> args, FunctionNode fnNode) {

    SoyTofuFunction fn = soyTofuFunctionsMap.get(fnName);
    if (fn == null) {
      throw new RenderException(
          "Failed to find Soy function with name '" + fnName + "'" +
          " (function call \"" + fnNode.toSourceString() + "\").");
    }

    // Arity has already been checked by CheckFunctionCallsVisitor.

    try {
      return fn.computeForTofu(args);
    } catch (Exception e) {
      throw new RenderException(
          "Error while computing function \"" + fnNode.toSourceString() + "\": " + e.getMessage());
    }
  }

}
