/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.incrementaldomsrc;

import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_EVAL_LOG_FN;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.STATE_PREFIX;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.internal.JsRuntime.XID;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.jssrc.internal.JsType;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor;
import com.google.template.soy.jssrc.internal.TranslationContext;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;

/** Translates expressions, overriding methods for special-case idom behavior. */
public class IncrementalDomTranslateExprNodeVisitor extends TranslateExprNodeVisitor {
  public IncrementalDomTranslateExprNodeVisitor(
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      TranslationContext translationContext,
      ErrorReporter errorReporter) {
    super(javaScriptValueFactory, translationContext, errorReporter);
  }

  @Override
  protected Expression genCodeForStateAccess(String paramName, TemplateStateVar stateVar) {
    return id("this").dotAccess(STATE_PREFIX + paramName);
  }

  @Override
  protected Expression sanitizedContentToProtoConverterFunction(Descriptor messageType) {
    return IncrementalDomRuntime.IDOM_JS_TO_PROTO_PACK_FN.get(messageType.getFullName());
  }

  @Override
  protected Expression visitFunctionNode(FunctionNode node) {
    if (node.getSoyFunction() instanceof LoggingFunction) {
      LoggingFunction loggingNode = (LoggingFunction) node.getSoyFunction();
      return INCREMENTAL_DOM_EVAL_LOG_FN.call(
          XID.call(Expression.stringLiteral(node.getFunctionName())),
          Expression.arrayLiteral(visitChildren(node)),
          Expression.stringLiteral(loggingNode.getPlaceholder()));
    }
    return super.visitFunctionNode(node);
  }

  @Override
  protected Expression visitGlobalNode(GlobalNode node) {
    if (node.isResolved()) {
      // If the types don't match this means this is a proto enum.  Add a cast to ensure the js
      // compiler knows the type
      // TODO(b/128869068) Ensure that a hard require is added for this type.
      if (!node.getType().equals(node.getValue().getType())) {
        JsType type = JsType.forJsSrcStrict(SoyTypes.removeNull(node.getType()));
        return visit(node.getValue()).castAs(type.typeExpr(), type.getGoogRequires());
      }
      return visit(node.getValue());
    }
    return super.visit(node);
  }

  @Override
  protected JsType jsTypeFor(SoyType type) {
    return JsType.forIncrementalDomState(type);
  }
}
