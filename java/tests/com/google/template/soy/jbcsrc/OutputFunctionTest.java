/*
 * Copyright 2026 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;

import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmls;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.jbcsrc.api.OutputAppendable;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.logging.SoyLogger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for externs with {@code fallbackMethod} in jbcsrc. */
@RunWith(JUnit4.class)
public final class OutputFunctionTest {

  public static final List<String> calls = new ArrayList<>();

  public static String renderStringPrimary(String s) {
    calls.add("primary: " + s);
    return "primary: " + s;
  }

  public static String renderStringFallback(String s) {
    calls.add("fallback: " + s);
    return "fallback: " + s;
  }

  public static SanitizedContent renderHtmlPrimary(String s) {
    calls.add("primary: " + s);
    return SanitizedContents.fromSafeHtml(SafeHtmls.htmlEscape("primary: " + s));
  }

  public static SanitizedContent renderHtmlFallback(String s) {
    calls.add("fallback: " + s);
    return SanitizedContents.fromSafeHtml(SafeHtmls.htmlEscape("fallback: " + s));
  }

  private static final class TestLogger implements SoyLogger {
    @Override
    public EnterData enter(LogStatement statement) {
      return EnterData.EMPTY;
    }

    @Override
    public Optional<SafeHtml> exit() {
      return Optional.empty();
    }

    @Override
    public String evalLoggingFunction(LoggingFunctionInvocation value) {
      return "";
    }
  }

  private static final String SOY_FILE =
      """
      {namespace test}

      {extern testString: (s: string) => string}
        {javaimpl class="com.google.template.soy.jbcsrc.OutputFunctionTest"
                  method="renderStringPrimary"
                  fallbackMethod="renderStringFallback"
                  params="java.lang.String"
                  return="java.lang.String" /}
      {/extern}

      {extern testHtml: (s: string) => html}
        {javaimpl class="com.google.template.soy.jbcsrc.OutputFunctionTest"
                  method="renderHtmlPrimary"
                  fallbackMethod="renderHtmlFallback"
                  params="java.lang.String"
                  return="com.google.template.soy.data.SanitizedContent" /}
      {/extern}

      {template directString}
        {testString('hello')}
      {/template}

      {template concatString}
        {testString('hello') + '!'}
      {/template}

      {template coercedString}
        {'' + testString('hello')}
      {/template}

      {template bufferedStringReplayed}
        {let $buf kind="text"}
          {testString('buffered')}
        {/let}
        {$buf}
      {/template}

      {template bufferedStringCoerced}
        {let $buf kind="text"}
          {testString('buffered')}
        {/let}
        {'' + $buf}
      {/template}

      {template directHtml}
        {testHtml('world')}
      {/template}

      {template coercedHtml}
        {'' + testHtml('world')}
      {/template}

      {template bufferedHtmlReplayed}
        {let $buf kind="html"}
          {testHtml('world')}
        {/let}
        {$buf}
      {/template}

      {const FooVe = ve_def('FooVe', 1) /}

      {template velogLogonlyTrue}
        {velog FooVe logonly="true"}
          <div>{testString('in_velog')}</div>
        {/velog}
      {/template}

      {template velogLogonlyFalse}
        {velog FooVe logonly="false"}
          <div>{testString('in_velog')}</div>
        {/velog}
      {/template}

      {template velogLogonlyBuffered}
        {velog FooVe logonly="true"}
          {let $buf kind="html"}
            <div>{testString('buffered_velog')}</div>
          {/let}
          {$buf}
        {/velog}
      {/template}
      """;

  private CompiledTemplates templates;

  @Before
  public void setUp() {
    calls.clear();
    templates = TemplateTester.compileFile(SOY_FILE);
  }

  private String render(String templateName) throws IOException {
    CompiledTemplate template = templates.getTemplate(templateName);
    StringBuilder sb = new StringBuilder();
    OutputAppendable output = OutputAppendable.create(sb);
    assertThat(
            template.render(null, ParamStore.EMPTY_INSTANCE, output, getDefaultContext(templates)))
        .isNull();
    return sb.toString();
  }

  @Test
  public void testDirectStreamingExecutesPrimary() throws Exception {
    assertThat(render("test.directString")).isEqualTo("primary: hello");
    assertThat(render("test.directHtml")).isEqualTo("primary: world");
  }

  @Test
  public void testExpressionEvaluationExecutesFallback() throws Exception {
    assertThat(render("test.concatString")).isEqualTo("fallback: hello!");
    assertThat(render("test.coercedString")).isEqualTo("fallback: hello");
    assertThat(render("test.coercedHtml")).isEqualTo("fallback: world");
  }

  @Test
  public void testBufferedReplayExecutesPrimary() throws Exception {
    assertThat(render("test.bufferedStringReplayed")).isEqualTo("primary: buffered");
    assertThat(render("test.bufferedHtmlReplayed")).isEqualTo("primary: world");
  }

  @Test
  public void testBufferedCoercionExecutesFallback() throws Exception {
    assertThat(render("test.bufferedStringCoerced")).isEqualTo("fallback: buffered");
  }

  @Test
  public void testRenderDirectlyToOutputAppendable() throws IOException {
    CompiledTemplate template = templates.getTemplate("test.directString");

    // Rendering to OutputAppendable evaluates primary:
    StringBuilder sb = new StringBuilder();
    OutputAppendable output = OutputAppendable.create(sb);
    assertThat(
            template.render(null, ParamStore.EMPTY_INSTANCE, output, getDefaultContext(templates)))
        .isNull();
    assertThat(sb.toString()).isEqualTo("primary: hello");

    // Rendering to BufferingAppendable and calling toString() evaluates fallback:
    BufferingAppendable buffer = LoggingAdvisingAppendable.buffering();
    assertThat(
            template.render(null, ParamStore.EMPTY_INSTANCE, buffer, getDefaultContext(templates)))
        .isNull();
    assertThat(buffer.toString()).isEqualTo("fallback: hello");

    // Replaying that buffer to an OutputAppendable evaluates primary:
    StringBuilder replaySb = new StringBuilder();
    OutputAppendable replayOutput = OutputAppendable.create(replaySb);
    buffer.replayOn(replayOutput);
    assertThat(replaySb.toString()).isEqualTo("primary: hello");
  }

  @Test
  public void testVelogLogonlyCallsFallbackAndSuppressesOutput() throws Exception {
    CompiledTemplate template = templates.getTemplate("test.velogLogonlyTrue");
    StringBuilder sb = new StringBuilder();
    OutputAppendable output = OutputAppendable.create(sb, new TestLogger());
    assertThat(
            template.render(null, ParamStore.EMPTY_INSTANCE, output, getDefaultContext(templates)))
        .isNull();
    assertThat(sb.toString()).isEmpty();
    assertThat(calls).containsExactly("fallback: in_velog");
  }

  @Test
  public void testVelogLogonlyFalseCallsPrimaryAndOutputsHtml() throws Exception {
    CompiledTemplate template = templates.getTemplate("test.velogLogonlyFalse");
    StringBuilder sb = new StringBuilder();
    OutputAppendable output = OutputAppendable.create(sb);
    assertThat(
            template.render(null, ParamStore.EMPTY_INSTANCE, output, getDefaultContext(templates)))
        .isNull();
    assertThat(sb.toString()).isEqualTo("<div>primary: in_velog</div>");
    assertThat(calls).containsExactly("primary: in_velog");
  }

  @Test
  public void testVelogLogonlyBufferedCallsFallbackAndSuppressesOutput() throws Exception {
    CompiledTemplate template = templates.getTemplate("test.velogLogonlyBuffered");
    StringBuilder sb = new StringBuilder();
    OutputAppendable output = OutputAppendable.create(sb, new TestLogger());
    assertThat(
            template.render(null, ParamStore.EMPTY_INSTANCE, output, getDefaultContext(templates)))
        .isNull();
    assertThat(sb.toString()).isEmpty();
    assertThat(calls).containsExactly("fallback: buffered_velog");
  }
}
