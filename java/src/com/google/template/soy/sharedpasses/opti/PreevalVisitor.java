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

package com.google.template.soy.sharedpasses.opti;

import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.restricted.SoyJavaRuntimeFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.sharedpasses.render.EvalVisitor;
import com.google.template.soy.sharedpasses.render.RenderException;

import java.util.Deque;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * Visitor for preevaluating expressions in which all data values known at compile time.
 *
 * Package-private helper for {@link SimplifyExprVisitor} and {@link PrerenderVisitor}, both of
 * which in turn are helpers for {@link SimplifyVisitor}.
 *
 * <p> {@link #exec} may be called on any expression. The result of evaluating the expression (in
 * the context of the {@code data} and {@code env} passed into the constructor) is returned as a
 * {@code SoyData} object.
 *
 */
class PreevalVisitor extends EvalVisitor {


  /**
   * @param soyJavaRuntimeFunctionsMap Map of all SoyJavaRuntimeFunctions (name to function).
   * @param data The current template data.
   * @param env The current environment.
   */
  PreevalVisitor(
      Map<String, SoyJavaRuntimeFunction> soyJavaRuntimeFunctionsMap, @Nullable SoyMapData data,
      Deque<Map<String, SoyData>> env) {

    super(soyJavaRuntimeFunctionsMap, data, null, env);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected SoyData visitDataRefNode(DataRefNode node) {

    // Cannot preevaluate injected data.
    if (node.isIjDataRef()) {
      throw new RenderException("Cannot preevaluate reference to ijData.");
    }

    // Otherwise, super method can handle it.
    return super.visitDataRefNode(node);
  }


  @Override protected SoyData computeFunctionHelper(
      SoyJavaRuntimeFunction fn, List<SoyData> args, FunctionNode fnNode) {

    if (! fn.getClass().isAnnotationPresent(SoyPureFunction.class)) {
      throw new RenderException("Cannot prerender impure function.");
    }

    return super.computeFunctionHelper(fn, args, fnNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  @Override protected SoyData resolveDataRefFirstKey(DataRefNode dataRefNode) {

    SoyData value = super.resolveDataRefFirstKey(dataRefNode);
    if (value == UndefinedData.INSTANCE) {
      throw new RenderException("Encountered undefined reference during preevaluation.");
    }
    return value;
  }

}
