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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ProtoInitNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyPureFunction;
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
        /* ijData= */ null,
        /* cssRenamingMap= */ null,
        /* xidRenamingMap= */ null,
        /* msgBundle= */ null,
        /* debugSoyTemplateInfo= */ false,
        /* pluginInstances= */ ImmutableMap.of());
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

    if (!fn.getClass().isAnnotationPresent(SoyPureFunction.class)) {
      throw RenderException.create("Cannot preevaluate impure function.");
    }

    return super.computeFunctionHelper(fn, args, fnNode);
  }

  @Override
  protected SoyValue computeFunctionHelper(
      SoyJavaSourceFunction fn, List<SoyValue> args, FunctionNode fnNode) {

    if (!fn.getClass().isAnnotationPresent(SoyPureFunction.class)) {
      throw RenderException.create("Cannot preevaluate impure function.");
    }

    return super.computeFunctionHelper(fn, args, fnNode);
  }

  @Override
  protected SoyValue visitProtoInitNode(ProtoInitNode node) {
    // we don't have the classes for the protos so we can't run the normal implementation
    // We could do this in theory by returning a record with all the fields set, but it
    // would be a lot of work to set up defaults and types for each field and even then if we did
    // that, there are SoyFunctions that unpack args to raw protos which would then fail.
    // TODO(lukes): The easiest thing would just be to conditionally execute the code iff we have
    // the proto on our classpath.
    throw RenderException.create("Cannot preevaluate proto initializers.");
  }
}
