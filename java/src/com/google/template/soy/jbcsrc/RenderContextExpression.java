/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.jbcsrc;

import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.types.UnknownType;
import java.util.List;
import javax.annotation.Nullable;

/** An expression for a {@link RenderContext} object. */
final class RenderContextExpression extends Expression implements JbcSrcPluginContext {

  private static final MethodRef GET_DELTEMPLATE =
      MethodRef.create(RenderContext.class, "getDelTemplate", String.class, String.class);

  private static final MethodRef RENDER_MODIFIABLE =
      MethodRef.create(
          RenderContext.class,
          "renderModifiable",
          String.class,
          SoyRecord.class,
          SoyRecord.class,
          LoggingAdvisingAppendable.class);

  private static final MethodRef GET_PLUGIN_INSTANCE =
      MethodRef.create(RenderContext.class, "getPluginInstance", String.class);

  private static final MethodRef GET_LOCALE = MethodRef.create(RenderContext.class, "getLocale");

  private static final MethodRef GET_PRINT_DIRECTIVE =
      MethodRef.create(RenderContext.class, "getPrintDirective", String.class);

  private static final MethodRef GET_SOY_MSG_PARTS =
      MethodRef.create(RenderContext.class, "getSoyMsgParts", long.class, ImmutableList.class);

  private static final MethodRef GET_SOY_MSG_PARTS_NO_DEFAULT =
      MethodRef.create(RenderContext.class, "getSoyMsgParts", long.class);

  private static final MethodRef GET_SOY_MSG_PARTS_WITH_ALTERNATE_ID =
      MethodRef.create(
          RenderContext.class,
          "getSoyMsgPartsWithAlternateId",
          long.class,
          ImmutableList.class,
          long.class);

  private static final MethodRef GET_SOY_MSG_PARTS_WITH_ALTERNATE_ID_NO_DEFAULT =
      MethodRef.create(
          RenderContext.class, "getSoyMsgPartsWithAlternateId", long.class, long.class);

  private static final MethodRef RENAME_CSS_SELECTOR =
      MethodRef.create(RenderContext.class, "renameCssSelector", String.class);

  private static final MethodRef RENAME_XID =
      MethodRef.create(RenderContext.class, "renameXid", String.class);

  private static final MethodRef USE_PRIMARY_MSG_IF_FALLBACK =
      MethodRef.create(RenderContext.class, "usePrimaryMsgIfFallback", long.class, long.class);

  private static final MethodRef USE_PRIMARY_OR_ALTERNATE_IF_FALLBACK =
      MethodRef.create(
          RenderContext.class,
          "usePrimaryOrAlternateIfFallback",
          long.class,
          long.class,
          long.class);

  private static final MethodRef USE_PRIMARY_IF_FALLBACK_OR_FALLBACK_ALTERNATE =
      MethodRef.create(
          RenderContext.class,
          "usePrimaryIfFallbackOrFallbackAlternate",
          long.class,
          long.class,
          long.class);

  private static final MethodRef USE_PRIMARY_OR_ALTERNATE_IF_FALLBACK_OR_FALLBACK_ALTERNATE =
      MethodRef.create(
          RenderContext.class,
          "usePrimaryOrAlternateIfFallbackOrFallbackAlternate",
          long.class,
          long.class,
          long.class,
          long.class);

  private static final MethodRef GET_DEBUG_SOY_TEMPLATE_INFO =
      MethodRef.create(RenderContext.class, "getDebugSoyTemplateInfo");

  private static final MethodRef GET_BIDI_GLOBAL_DIR =
      MethodRef.create(RenderContext.class, "getBidiGlobalDir");

  private static final MethodRef GET_BIDI_GLOBAL_DIR_DIR =
      MethodRef.create(RenderContext.class, "getBidiGlobalDirDir");

  private static final MethodRef GET_ALL_REQUIRED_CSS_NAMESPACES =
      MethodRef.create(RenderContext.class, "getAllRequiredCssNamespaces", String.class);

  private static final MethodRef GET_ALL_REQUIRED_CSS_PATHS =
      MethodRef.create(RenderContext.class, "getAllRequiredCssPaths", String.class);

  private static final MethodRef GET_ESCAPING_DIRECTIVE_AS_FUNCTION =
      MethodRef.create(RenderContext.class, "getEscapingDirectiveAsFunction", String.class);

  private static final MethodRef HAS_LOGGER =
      MethodRef.create(RenderContext.class, "hasLogger").asCheap();

  private static final MethodRef GET_LOGGER = MethodRef.create(RenderContext.class, "getLogger");
  private static final MethodRef POP_FRAME = MethodRef.create(RenderContext.class, "popFrame");
  private static final MethodRef GET_RENDER_CSS_HELPER =
      MethodRef.create(RenderContext.class, "getRenderCssHelper");

  private static final MethodRef GET_CONST =
      MethodRef.create(RenderContext.class, "getConst", String.class);

  private static final MethodRef STORE_CONST =
      MethodRef.create(RenderContext.class, "storeConst", String.class, Object.class);

  private final Expression delegate;

  RenderContextExpression(Expression renderContext) {
    super(renderContext.resultType(), renderContext.features());
    this.delegate = renderContext;
  }

  @Override
  protected void doGen(CodeBuilder adapter) {
    delegate.gen(adapter);
  }

  @Override
  public Expression getBidiGlobalDir() {
    return delegate.invoke(GET_BIDI_GLOBAL_DIR);
  }

  public Expression getBidiGlobalDirDir() {
    return delegate.invoke(GET_BIDI_GLOBAL_DIR_DIR);
  }

  @Override
  public Expression getAllRequiredCssNamespaces(SoyExpression template) {
    return delegate.invoke(GET_ALL_REQUIRED_CSS_NAMESPACES, template.unboxAsStringUnchecked());
  }

  @Override
  public Expression getAllRequiredCssPaths(SoyExpression template) {
    return delegate.invoke(GET_ALL_REQUIRED_CSS_PATHS, template.unboxAsStringUnchecked());
  }

  Expression getRenderCssHelper() {
    return delegate.invoke(GET_RENDER_CSS_HELPER);
  }

  Expression getDebugSoyTemplateInfo() {
    return delegate.invoke(GET_DEBUG_SOY_TEMPLATE_INFO);
  }

  Expression getPluginInstance(String pluginName) {
    return delegate.invoke(GET_PLUGIN_INSTANCE, constant(pluginName));
  }

  Expression renameXid(String value) {
    return delegate.invoke(RENAME_XID, constant(value));
  }

  Expression renameCss(String value) {
    return delegate.invoke(RENAME_CSS_SELECTOR, constant(value));
  }

  Expression getDeltemplate(String delCalleeName, Expression variantExpr) {
    return delegate.invoke(GET_DELTEMPLATE, constant(delCalleeName), variantExpr);
  }

  Expression renderModifiable(
      String delCalleeName, Expression params, Expression ijData, Expression appendableExpression) {
    return delegate.invoke(
        RENDER_MODIFIABLE, constant(delCalleeName), params, ijData, appendableExpression);
  }

  @Override
  public Expression getULocale() {
    return delegate.invoke(GET_LOCALE);
  }

  Expression getSoyMsgParts(long id, @Nullable Expression defaultParts) {
    return defaultParts == null
        ? delegate.invoke(GET_SOY_MSG_PARTS_NO_DEFAULT, constant(id))
        : delegate.invoke(GET_SOY_MSG_PARTS, constant(id), defaultParts);
  }

  Expression getSoyMsgPartsWithAlternateId(
      long id, @Nullable Expression defaultParts, long alternateId) {
    return defaultParts == null
        ? delegate.invoke(
            GET_SOY_MSG_PARTS_WITH_ALTERNATE_ID_NO_DEFAULT, constant(id), constant(alternateId))
        : delegate.invoke(
            GET_SOY_MSG_PARTS_WITH_ALTERNATE_ID, constant(id), defaultParts, constant(alternateId));
  }

  Expression getPrintDirective(String name) {
    return delegate.invoke(GET_PRINT_DIRECTIVE, constant(name));
  }

  Expression getEscapingDirectiveAsFunction(String name) {
    return delegate.invoke(GET_ESCAPING_DIRECTIVE_AS_FUNCTION, constant(name));
  }

  Expression getConst(String name) {
    return delegate.invoke(GET_CONST, constant(name));
  }

  Statement storeConst(String name, Expression value) {
    return delegate.invokeVoid(STORE_CONST, constant(name), value);
  }

  SoyExpression applyPrintDirective(SoyPrintDirective directive, SoyExpression value) {
    return applyPrintDirective(directive, value, ImmutableList.of());
  }

  SoyExpression applyPrintDirective(
      SoyPrintDirective directive, SoyExpression value, List<SoyExpression> args) {
    if (directive instanceof SoyJbcSrcPrintDirective) {
      value = ((SoyJbcSrcPrintDirective) directive).applyForJbcSrc(this, value, args);
    } else {
      value =
          SoyExpression.forSoyValue(
              UnknownType.getInstance(),
              MethodRef.RUNTIME_APPLY_PRINT_DIRECTIVE.invoke(
                  getPrintDirective(directive.getName()),
                  value.box(),
                  SoyExpression.asBoxedListWithJavaNullItems(args)));
    }
    return value;
  }

  Expression usePrimaryMsgIfFallback(long msgId, long fallbackId) {
    return delegate.invoke(USE_PRIMARY_MSG_IF_FALLBACK, constant(msgId), constant(fallbackId));
  }

  Expression usePrimaryOrAlternateIfFallback(long msgId, long alternateId, long fallbackId) {
    return delegate.invoke(
        USE_PRIMARY_OR_ALTERNATE_IF_FALLBACK,
        constant(msgId),
        constant(alternateId),
        constant(fallbackId));
  }

  Expression usePrimaryIfFallbackOrFallbackAlternate(
      long msgId, long fallbackId, long fallbackAlternateId) {
    return delegate.invoke(
        USE_PRIMARY_IF_FALLBACK_OR_FALLBACK_ALTERNATE,
        constant(msgId),
        constant(fallbackId),
        constant(fallbackAlternateId));
  }

  Expression usePrimaryOrAlternateIfFallbackOrFallbackAlternate(
      long msgId, long alternateId, long fallbackId, long fallbackAlternateId) {
    return delegate.invoke(
        USE_PRIMARY_OR_ALTERNATE_IF_FALLBACK_OR_FALLBACK_ALTERNATE,
        constant(msgId),
        constant(alternateId),
        constant(fallbackId),
        constant(fallbackAlternateId));
  }

  public Expression hasLogger() {
    return delegate.invoke(HAS_LOGGER);
  }

  public Expression getLogger() {
    return delegate.invoke(GET_LOGGER);
  }

  public Expression popFrame() {
    return delegate.invoke(POP_FRAME);
  }
}
