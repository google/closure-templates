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
package com.google.template.soy.shared.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.javascript.restricted.SoyJavaScriptSourceFunction;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class InternalPluginsTest {

  private SoyScopedData data;

  @Before
  public void setUp() {
    data = new NoOpScopedData();
  }

  @Test
  public void testBuiltinPluginsSupportAllBackends() throws Exception {
    for (SoyPrintDirective directive : InternalPlugins.internalDirectives(data)) {
      assertThat(directive).isInstanceOf(SoyJsSrcPrintDirective.class);
      assertThat(directive).isInstanceOf(SoyJavaPrintDirective.class);
      assertThat(directive).isInstanceOf(SoyJbcSrcPrintDirective.class);
      assertThat(directive).isInstanceOf(SoyPySrcPrintDirective.class);
    }
  }

  @Test
  public void testFunctionsSupportAllBackends() {
    for (SoySourceFunction function : InternalPlugins.internalFunctions()) {
      assertThat(function.getClass().isAnnotationPresent(SoyFunctionSignature.class)).isTrue();
      assertThat(function).isInstanceOf(SoyJavaScriptSourceFunction.class);
      assertThat(function).isInstanceOf(SoyJavaSourceFunction.class);
      assertThat(function).isInstanceOf(SoyPythonSourceFunction.class);
      // Internal functions should no longer implement SoyJavaFunction or SoyJsSrcFunction
      assertThat(function).isNotInstanceOf(SoyJsSrcFunction.class);
      assertThat(function).isNotInstanceOf(SoyJavaFunction.class);
      assertThat(function).isNotInstanceOf(SoyPySrcFunction.class);
    }
  }

  @Test
  public void testMethodsSupportAllBackends() {
    for (SoySourceFunction function : InternalPlugins.internalMethods()) {
      assertThat(function.getClass().isAnnotationPresent(SoyMethodSignature.class)).isTrue();
      assertThat(function).isInstanceOf(SoyJavaScriptSourceFunction.class);
      assertThat(function).isInstanceOf(SoyJavaSourceFunction.class);
      assertThat(function).isInstanceOf(SoyPythonSourceFunction.class);
    }
  }

  // This test serves to document exactly which escaping directives do and do not support streaming
  // in jbcsrc.  If someone adds a new one, they will need to update this test and document why
  // it doesn't support streaming.
  @Test
  public void testStreamingPrintDirectives() throws Exception {
    ImmutableSet.Builder<String> streamingPrintDirectives = ImmutableSet.builder();
    ImmutableSet.Builder<String> nonStreamingPrintDirectives = ImmutableSet.builder();
    for (SoyPrintDirective directive : InternalPlugins.internalDirectives(data)) {
      if (directive instanceof SoyJbcSrcPrintDirective.Streamable) {
        streamingPrintDirectives.add(directive.getName());
      } else {
        nonStreamingPrintDirectives.add(directive.getName());
      }
    }
    nonStreamingPrintDirectives.addAll(InternalPlugins.internalAliasedDirectivesMap().keySet());
    assertThat(streamingPrintDirectives.build())
        .containsExactly(
            "|escapeHtml",
            "|escapeCssString",
            "|normalizeHtml",
            "|escapeJsString",
            "|escapeHtmlRcdata",
            "|escapeJsRegex",
            "|text",
            "|normalizeUri",
            "|changeNewlineToBr",
            "|bidiSpanWrap",
            "|bidiUnicodeWrap",
            "|insertWordBreaks",
            "|truncate",
            "|cleanHtml",
            "|filterHtmlAttributes");
    assertThat(nonStreamingPrintDirectives.build())
        .containsExactly(
            // These aren't worth doing. The values should be small and unlikely to have logging
            // statements.
            "|escapeUri",
            "|formatNum",
            "|filterNumber",
            // These can't be made streaming because it would require a complex state machine or
            // they require knowing the full content to work.  For example all the filters, which
            // generally validate via a regular expression.
            // As below, we may want to make these support the Streamable interface but internally
            // buffer.
            "|filterHtmlElementName",
            "|filterCssValue",
            "|escapeJsValue",
            "|filterNormalizeUri",
            "|filterNormalizeMediaUri",
            "|filterNormalizeRefreshUri",
            "|filterTrustedResourceUri",
            "|filterImageDataUri",
            "|filterSipUri",
            "|filterTelUri",
            // This is used only with escapeHtmlAttribute* which are not streaming.
            "|escapeHtmlHtmlAttribute",
            // These two could be made streaming, it would require some refactoring of the
            // Sanitizers.stripHtmlTags method but it is probably a good idea.
            "|escapeHtmlAttribute",
            "|escapeHtmlAttributeNospace",
            // Could be made streaming, but it would be a bit tricky and is unlikely to be
            // important.  See comment on definition.
            "|filterHtmlScriptPhrasingData");
  }
}
