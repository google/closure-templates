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

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.Sanitizers;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuPrintDirective;

import java.util.List;
import java.util.Set;


/**
 * An escaping directive that is backed by {@link Sanitizers} in java, and {@code soyutils.js} or
 * the closure equivalent in JavaScript.
 * See {@link com.google.template.soy.jssrc.internal.GenerateSoyUtilsEscapingDirectiveCode} which
 * creates the JS code that backs escaping directives.
 *
 * @author Mike Samuel
 */
public abstract class BasicEscapeDirective extends SoyAbstractTofuPrintDirective
    implements SoyJsSrcPrintDirective, SoyJavaSrcPrintDirective {


  /**
   * Implements the |escapeCssString directive.
   */
  static final class EscapeCssString extends BasicEscapeDirective {

    EscapeCssString() {
      super("|escapeCssString");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.escapeCssString(value);
    }
  }


  /**
   * Implements the |filterCssValue directive.
   */
  static final class FilterCssValue extends BasicEscapeDirective {

    FilterCssValue() {
      super("|filterCssValue");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.filterCssValue(value);
    }
  }


  /**
   * Implements the |escapeHtmlRcdata directive.
   */
  static final class EscapeHtmlRcdata extends BasicEscapeDirective {

    EscapeHtmlRcdata() {
      super("|escapeHtmlRcdata");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.escapeHtmlRcdata(value);
    }
  }


  /**
   * Implements the |escapeHtmlAttribute directive.
   */
  static final class EscapeHtmlAttribute extends BasicEscapeDirective {

    EscapeHtmlAttribute() {
      super("|escapeHtmlAttribute");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.escapeHtmlAttribute(value);
    }
  }


  /**
   * Implements the |escapeHtmlAttributeNospace directive.
   */
  static final class EscapeHtmlAttributeNospace extends BasicEscapeDirective {

    EscapeHtmlAttributeNospace() {
      super("|escapeHtmlAttributeNospace");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.escapeHtmlAttributeNospace(value);
    }
  }


  /**
   * Implements the |filterHtmlAttribute directive.
   */
  static final class FilterHtmlAttribute extends BasicEscapeDirective {

    FilterHtmlAttribute() {
      super("|filterHtmlAttribute");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.filterHtmlAttribute(value);
    }
  }


  /**
   * Implements the |filterHtmlElementName directive.
   */
  static final class FilterHtmlElementName extends BasicEscapeDirective {

    FilterHtmlElementName() {
      super("|filterHtmlElementName");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.filterHtmlElementName(value);
    }
  }


  /**
   * Implements the |escapeJsString directive.
   */
  static final class EscapeJsString extends BasicEscapeDirective {

    EscapeJsString() {
      super("|escapeJsString");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.escapeJsString(value);
    }
  }


  /**
   * Implements the |escapeJsValue directive.
   */
  static final class EscapeJsValue extends BasicEscapeDirective {

    EscapeJsValue() {
      super("|escapeJsValue");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.escapeJsValue(value);
    }
  }


  /**
   * Implements the |filterNormalizeUri directive.
   */
  static final class FilterNormalizeUri extends BasicEscapeDirective {

    FilterNormalizeUri() {
      super("|filterNormalizeUri");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.filterNormalizeUri(value);
    }
  }


  /**
   * Implements the |normalizeUri directive.
   */
  static final class NormalizeUri extends BasicEscapeDirective {

    NormalizeUri() {
      super("|normalizeUri");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.normalizeUri(value);
    }
  }


  /**
   * Implements the |escapeUri directive.
   */
  static final class EscapeUri extends BasicEscapeDirective {

    EscapeUri() {
      super("|escapeUri");
    }

    @Override protected String escape(SoyData value) {
      return Sanitizers.escapeUri(value);
    }
  }


  /** The directive name with leading <code>|</code>. */
  private final String name;


  /**
   * @param name E.g. {@code |escapeUri}.
   */
  public BasicEscapeDirective(String name) {
    this.name = name;
  }


  /**
   * Performs the actual escaping.
   */
  protected abstract String escape(SoyData value);


  /**
   * The name of the Soy directive that this instance implements.
   */
  @Override
  public final String getName() {
    return name;
  }


  @Override
  public final Set<Integer> getValidArgsSizes() {
    return VALID_ARGS_SIZES;
  }
  private static final Set<Integer> VALID_ARGS_SIZES = ImmutableSet.of(0);


  @Override
  public final boolean shouldCancelAutoescape() {
    return true;
  }


  @Override
  public final String apply(SoyData value, List<SoyData> args) {
    return escape(value);
  }


  @Override
  public JavaExpr applyForJavaSrc(JavaExpr value, List<JavaExpr> args) {
    return new JavaExpr(
        JavaCodeUtils.genNewStringData(
            JavaCodeUtils.genFunctionCall(
                JavaCodeUtils.UTILS_LIB + ".$$" + name.substring(1),
                JavaCodeUtils.genMaybeCast(value, SoyData.class))),
        StringData.class, Integer.MAX_VALUE);
  }


  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr(
        "soy.$$" + name.substring(1) + "(" + value.getText() + ")", Integer.MAX_VALUE);
  }

}
