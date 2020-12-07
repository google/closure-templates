/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.basicdirectives;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective.Streamable.AppendableAndOptions;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.internal.Sanitizers;
import com.google.template.soy.shared.internal.ShortCircuitable;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * An escaping directive that is backed by {@link Sanitizers} in java, {@code soyutils.js} or the
 * closure equivalent in JavaScript, and {@code sanitize.py} in Python.
 *
 * <p>See {@link com.google.template.soy.jssrc.internal.GenerateSoyUtilsEscapingDirectiveCode} which
 * creates the JS code that backs escaping directives, and {@link
 * com.google.template.soy.pysrc.internal.GeneratePySanitizeEscapingDirectiveCode} which creates the
 * Python backing code.
 *
 */
public abstract class BasicEscapeDirective
    implements SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective {

  private static final ImmutableSet<Integer> VALID_ARGS_SIZES = ImmutableSet.of(0);

  /** The directive name, including the leading vertical bar ("|"). */
  private final String name;

  @LazyInit private IdentityHashMap<Class<?>, MethodRef> javaSanitizerByParamType;
  @LazyInit private MethodRef javaStreamingSanitizer;

  /** @param name E.g. {@code |escapeUri}. */
  public BasicEscapeDirective(String name) {
    this.name = name;
  }

  /** Performs the actual escaping. */
  protected abstract String escape(SoyValue value);

  /** The name of the Soy directive that this instance implements. */
  @Override
  public final String getName() {
    return name;
  }

  @Override
  public final ImmutableSet<Integer> getValidArgsSizes() {
    return VALID_ARGS_SIZES;
  }

  /**
   * Returns whether or not the streaming version of this directive is closeable.
   *
   * <p>The default is {@code false}, override this to change it to {@code true};
   */
  protected boolean isCloseable() {
    return false;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    return StringData.forValue(escape(value));
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr(
        "soy.$$" + name.substring(1) + "(" + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    String pyFnName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name.substring(1));
    return new PyExpr("sanitize." + pyFnName + "(" + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    // All the escaper functions have versions which accept a raw String and we should prefer
    // that one if we can avoid boxing.  However, we have to be careful not to throw away
    // information about the contentKind of the string.  So we only do this if the value is already
    // unboxed.  In all other cases we call the SoyValue version
    if (!value.isBoxed()) {
      return SoyExpression.forString(javaSanitizer(String.class).invoke(value.coerceToString()));
    }
    return SoyExpression.forString(javaSanitizer(SoyValue.class).invoke(value));
  }

  @VisibleForTesting
  synchronized MethodRef javaSanitizer(Class<?> paramType) {
    if (javaSanitizerByParamType == null) {
      javaSanitizerByParamType = new IdentityHashMap<>();
    }
    return javaSanitizerByParamType.computeIfAbsent(paramType, this::findMethodRefForType);
  }

  private MethodRef findMethodRefForType(Class<?> paramType) {
    return MethodRef.create(Sanitizers.class, name.substring(1), paramType).asNonNullable();
  }

  /**
   * Default implementation for {@link Streamable}.
   *
   * <p>Subclasses can simply add {@code implements Streamable} if they have an implementation in
   * Sanitizers.<name>Streaming. If they don't, this method will throw while trying to find it.
   */
  public final AppendableAndOptions applyForJbcSrcStreaming(
      JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
    MethodRef sanitizerMethod = javaStreamingSanitizer;
    if (sanitizerMethod == null) {
      // lazily allocated
      sanitizerMethod =
          MethodRef.create(
                  Sanitizers.class,
                  name.substring(1) + "Streaming",
                  LoggingAdvisingAppendable.class)
              .asNonNullable();
      javaStreamingSanitizer = sanitizerMethod;
    }
    Expression streamingSanitizersExpr = sanitizerMethod.invoke(delegateAppendable);
    if (isCloseable()) {
      return AppendableAndOptions.createCloseable(streamingSanitizersExpr);
    } else {
      return AppendableAndOptions.create(streamingSanitizersExpr);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Concrete subclasses.

  /** Implements the |escapeCssString directive. */
  @SoyPurePrintDirective
  static final class EscapeCssString extends BasicEscapeDirective implements Streamable {

    EscapeCssString() {
      super("|escapeCssString");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeCssString(value);
    }
  }

  /** Implements the |filterCssValue directive. */
  @SoyPurePrintDirective
  static final class FilterCssValue extends BasicEscapeDirective {

    FilterCssValue() {
      super("|filterCssValue");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterCssValue(value);
    }
  }

  /**
   * Implements the |normalizeHtml directive. This escapes the same as escapeHtml except does not
   * escape attributes.
   */
  @SoyPurePrintDirective
  static final class NormalizeHtml extends BasicEscapeDirective implements Streamable {

    NormalizeHtml() {
      super("|normalizeHtml");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.normalizeHtml(value);
    }
  }

  /** Implements the |escapeHtmlRcdata directive. */
  @SoyPurePrintDirective
  static final class EscapeHtmlRcdata extends BasicEscapeDirective implements Streamable {

    EscapeHtmlRcdata() {
      super("|escapeHtmlRcdata");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeHtmlRcdata(value);
    }
  }

  /** Implements the |escapeHtmlAttribute directive. */
  @SoyPurePrintDirective
  static final class EscapeHtmlAttribute extends BasicEscapeDirective implements Streamable {

    EscapeHtmlAttribute() {
      super("|escapeHtmlAttribute");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeHtmlAttribute(value);
    }

    @Override
    public boolean isCloseable() {
      return true;
    }
  }

  /** Implements the |escapeHtmlHtmlAttribute directive. */
  @SoyPurePrintDirective
  static final class EscapeHtmlHtmlAttribute extends BasicEscapeDirective {

    EscapeHtmlHtmlAttribute() {
      super("|escapeHtmlHtmlAttribute");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeHtmlHtmlAttribute(value);
    }
  }

  /** Implements the |escapeHtmlAttributeNospace directive. */
  @SoyPurePrintDirective
  static final class EscapeHtmlAttributeNospace extends BasicEscapeDirective implements Streamable {

    EscapeHtmlAttributeNospace() {
      super("|escapeHtmlAttributeNospace");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeHtmlAttributeNospace(value);
    }

    @Override
    public boolean isCloseable() {
      return true;
    }
  }

  /** Implements the |filterHtmlAttributes directive. */
  @SoyPurePrintDirective
  static final class FilterHtmlAttributes extends BasicEscapeDirective implements Streamable {

    FilterHtmlAttributes() {
      super("|filterHtmlAttributes");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterHtmlAttributes(value);
    }

    @Override
    protected boolean isCloseable() {
      return true;
    }
  }

  /** Implements the |filterNumber directive. */
  @SoyPurePrintDirective
  static final class FilterNumber extends BasicEscapeDirective {
    FilterNumber() {
      super("|filterNumber");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterNumber(value);
    }
  }

  /** Implements the |filterHtmlElementName directive. */
  @SoyPurePrintDirective
  static final class FilterHtmlElementName extends BasicEscapeDirective {

    FilterHtmlElementName() {
      super("|filterHtmlElementName");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterHtmlElementName(value);
    }
  }

  /** Implements the |escapeJsRegex directive. */
  @SoyPurePrintDirective
  static final class EscapeJsRegex extends BasicEscapeDirective implements Streamable {

    EscapeJsRegex() {
      super("|escapeJsRegex");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeJsRegex(value);
    }
  }

  /** Implements the |escapeJsString directive. */
  @SoyPurePrintDirective
  static final class EscapeJsString extends BasicEscapeDirective implements Streamable {

    EscapeJsString() {
      super("|escapeJsString");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeJsString(value);
    }
  }

  /** Implements the |escapeJsValue directive. */
  @SoyPurePrintDirective
  static final class EscapeJsValue extends BasicEscapeDirective implements ShortCircuitable {

    EscapeJsValue() {
      super("|escapeJsValue");
    }

    @Override
    public SoyExpression applyForJbcSrc(
        JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
      // escapeJsValue is special since it has special cases for numeric and boolean data
      if (!value.isBoxed()) {
        if (value.soyRuntimeType().isKnownString()) {
          return SoyExpression.forString(
              javaSanitizer(String.class).invoke(value.coerceToString()));
        }
        if (value.soyRuntimeType().isKnownNumber()) {
          return SoyExpression.forString(
              javaSanitizer(double.class).invoke(value.coerceToDouble()));
        }
        if (value.soyRuntimeType().isKnownBool()) {
          return SoyExpression.forString(
              javaSanitizer(boolean.class).invoke(value.coerceToBoolean()));
        }
        // otherwise fall through to boxing, this handles cases like 'null'
        value = value.box();
      }
      return SoyExpression.forString(javaSanitizer(SoyValue.class).invoke(value));
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeJsValue(value);
    }

    @Override
    public boolean isNoopForKind(ContentKind kind) {
      return kind == SanitizedContent.ContentKind.JS;
    }
  }

  /** Implements the |filterNormalizeUri directive. */
  @SoyPurePrintDirective
  static final class FilterNormalizeUri extends BasicEscapeDirective {

    FilterNormalizeUri() {
      super("|filterNormalizeUri");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterNormalizeUri(value);
    }
  }

  /** Implements the |filterNormalizeMediaUri directive. */
  @SoyPurePrintDirective
  static final class FilterNormalizeMediaUri extends BasicEscapeDirective {

    FilterNormalizeMediaUri() {
      super("|filterNormalizeMediaUri");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterNormalizeMediaUri(value);
    }
  }

  /** Implements the |filterNormalizeRefreshUri directive. */
  @SoyPurePrintDirective
  static final class FilterNormalizeRefreshUri extends BasicEscapeDirective {

    FilterNormalizeRefreshUri() {
      super("|filterNormalizeRefreshUri");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterNormalizeRefreshUri(value);
    }
  }

  /** Implements the |normalizeUri directive. */
  @SoyPurePrintDirective
  static final class NormalizeUri extends BasicEscapeDirective implements Streamable {

    NormalizeUri() {
      super("|normalizeUri");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.normalizeUri(value);
    }
  }

  /** Implements the |escapeUri directive. */
  @SoyPurePrintDirective
  static final class EscapeUri extends BasicEscapeDirective {

    EscapeUri() {
      super("|escapeUri");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.escapeUri(value);
    }
  }

  /** Implements the |filterTrustedResourceUri directive. */
  @SoyPurePrintDirective
  static final class FilterTrustedResourceUri extends BasicEscapeDirective
      implements ShortCircuitable {

    FilterTrustedResourceUri() {
      super("|filterTrustedResourceUri");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterTrustedResourceUri(value);
    }

    @Override
    public boolean isNoopForKind(ContentKind kind) {
      return kind == ContentKind.TRUSTED_RESOURCE_URI;
    }
  }

  /**
   * Implements the |filterHtmlScriptPhrasingData directive.
   *
   * <p>Escapes data for embedding in a <script> tag with a non JS content type. (JS content is
   * handled elsewhere). See
   * https://html.spec.whatwg.org/multipage/scripting.html#restrictions-for-contents-of-script-elements
   * for the requirements.
   *
   * <p>A streaming implementation is likely feasible but not currently implemented for a few
   * reasons
   *
   * <ul>
   *   <li>It is unlikely that large amounts of data would be printed in this context.
   *   <li>It is impossible for logging statements to exist in this context.
   * </ul>
   *
   * So with low motivation and no hard requirement streaming will not be implemented without a
   * specific well justified request.
   */
  @SoyPurePrintDirective
  static final class FilterHtmlScriptPhrasingData extends BasicEscapeDirective {

    FilterHtmlScriptPhrasingData() {
      super("|filterHtmlScriptPhrasingData");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterHtmlScriptPhrasingData(value);
    }
  }

  /**
   * Implements the |filterCspNonceValue directive.
   *
   * <p>See https://www.w3.org/TR/CSP2/#nonce_value
   */
  @SoyPurePrintDirective
  static final class FilterCspNonceValue extends BasicEscapeDirective {

    FilterCspNonceValue() {
      super("|filterCspNonceValue");
    }

    @Override
    protected String escape(SoyValue value) {
      return Sanitizers.filterCspNonceValue(value);
    }
  }
}
