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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_EVAL_LOG_FN;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.SOY_IDOM_IS_TRUTHY;
import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.STATE_PREFIX;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.number;
import static com.google.template.soy.jssrc.internal.JsRuntime.BIND_TEMPLATE_PARAMS_FOR_IDOM;
import static com.google.template.soy.jssrc.internal.JsRuntime.XID;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.ProtoEnumValueNode;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.internal.JavaScriptValueFactoryImpl;
import com.google.template.soy.jssrc.internal.JsType;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.internal.TranslateExprNodeVisitor;
import com.google.template.soy.jssrc.internal.TranslationContext;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;

/** Translates expressions, overriding methods for special-case idom behavior. */
public class IncrementalDomTranslateExprNodeVisitor extends TranslateExprNodeVisitor {
  public IncrementalDomTranslateExprNodeVisitor(
      JavaScriptValueFactoryImpl javaScriptValueFactory,
      TranslationContext translationContext,
      TemplateAliases templateAliases,
      ErrorReporter errorReporter) {
    super(javaScriptValueFactory, translationContext, templateAliases, errorReporter);
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
          XID.call(Expression.stringLiteral(node.getStaticFunctionName())),
          Expression.arrayLiteral(visitChildren(node)),
          Expression.stringLiteral(loggingNode.getPlaceholder()));
    }
    return super.visitFunctionNode(node);
  }

  @Override
  protected Expression genCodeForBind(
      Expression template, Expression paramRecord, SoyType templateType) {
    // Unions are enforced to have the same content kind in CheckTemplateCallsPass.
    SanitizedContentKind kind =
        Iterables.getOnlyElement(
                SoyTypes.expandUnions(templateType).stream()
                    .map(type -> ((TemplateType) type).getContentKind())
                    .collect(toImmutableSet()))
            .getSanitizedContentKind();
    if (kind.isHtml() || kind == SanitizedContentKind.ATTRIBUTES) {
      return BIND_TEMPLATE_PARAMS_FOR_IDOM.call(template, paramRecord);
    } else {
      return super.genCodeForBind(template, paramRecord, templateType);
    }
  }

  @Override
  protected Expression visitGlobalNode(GlobalNode node) {
    if (node.isResolved()) {
      return visit(node.getValue());
    }
    return super.visit(node);
  }

  @Override
  protected Expression visitProtoEnumValueNode(ProtoEnumValueNode node) {
    // TODO(b/128869068) Ensure that a hard require is added for this type.
    JsType type = JsType.forJsSrcStrict(SoyTypes.removeNull(node.getType()));
    return number(node.getValue()).castAs(type.typeExpr(), type.getGoogRequires());
  }

  /** Types that might possibly be idom function callbacks, which always need custom truthiness. */
  private static final ImmutableSet<Kind> FUNCTION_TYPES =
      Sets.immutableEnumSet(Kind.HTML, Kind.ELEMENT, Kind.ATTRIBUTES, Kind.UNKNOWN, Kind.ANY);

  @Override
  protected Expression maybeCoerceToBoolean(SoyType type, Expression chunk, boolean force) {
    if (SoyTypes.containsKinds(type, FUNCTION_TYPES)) {
      return SOY_IDOM_IS_TRUTHY.call(chunk);
    }

    return super.maybeCoerceToBoolean(type, chunk, force);
  }

  @Override
  protected JsType jsTypeFor(SoyType type) {
    return JsType.forIncrementalDomState(type);
  }
}
