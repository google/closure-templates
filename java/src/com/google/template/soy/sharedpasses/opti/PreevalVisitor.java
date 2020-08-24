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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.plugin.internal.JavaPluginExecContext;
import com.google.template.soy.shared.restricted.SoyFunctions;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.sharedpasses.render.Environment;
import com.google.template.soy.sharedpasses.render.EvalVisitor;
import com.google.template.soy.sharedpasses.render.RenderException;
import java.util.List;

/**
 * Visitor for preevaluating expressions in which all data values are known at compile time.
 *
 * <p>Package-private helper for {@link SimplifyExprVisitor} and {@link PrerenderVisitor}, both of
 * which in turn are helpers for {@link SimplifyVisitor}.
 *
 * <p>{@link #exec} may be called on any expression. The result of evaluating the expression (in the
 * context of the {@code data} and {@code env} passed into the constructor) is returned as a {@code
 * SoyValue} object.
 *
 */
final class PreevalVisitor extends EvalVisitor {

  PreevalVisitor(Environment env) {
    super(
        env,
        /* cssRenamingMap= */ null,
        /* xidRenamingMap= */ null,
        /* msgBundle= */ null,
        /* debugSoyTemplateInfo= */ false,
        /* pluginInstances= */ ImmutableMap.of(),
        UndefinedDataHandlingMode.NORMAL);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected SoyValue visitVarRefNode(VarRefNode node) {

    // Cannot preevaluate injected data.
    if (node.isInjected()) {
      throw RenderException.create("Cannot preevaluate reference to ijData.");
    }

    // Otherwise, super method can handle it.
    SoyValue value = super.visitVarRefNode(node);

    if (value instanceof UndefinedData) {
      throw RenderException.create("Encountered undefined reference during preevaluation.");
    }

    return value;
  }

  @Override
  protected SoyValue computeFunctionHelper(
      SoyJavaFunction fn, List<SoyValue> args, FunctionNode fnNode) {
    checkArgument(fnNode.getSoyFunction() == fn);
    checkPure(fn);
    return super.computeFunctionHelper(fn, args, fnNode);
  }

  @Override
  protected SoyValue computeFunctionHelper(List<SoyValue> args, JavaPluginExecContext fnNode) {
    checkPure(fnNode.getSourceFunction());
    return super.computeFunctionHelper(args, fnNode);
  }

  private static void checkPure(Object fn) {
    if (!SoyFunctions.isPure(fn)) {
      throw RenderException.create("Cannot preevaluate impure function.");
    }
  }
}
