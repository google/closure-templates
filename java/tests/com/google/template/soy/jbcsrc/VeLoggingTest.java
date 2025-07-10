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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeUrls;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.api.OutputAppendable;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.logging.SoyLogger.LoggingAttrs;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.testing.Foo;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@code {velog}} support. */
@RunWith(JUnit4.class)
public final class VeLoggingTest {

  private static class TestLogger implements SoyLogger {
    final StringBuilder builder = new StringBuilder();
    final HashMap<Long, LoggingAttrs> attrsMap = new HashMap<>();
    int depth;

    @Override
    public EnterData enter(LogStatement statement) {
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append("  ".repeat(depth)).append(statement);
      depth++;
      LoggingAttrs attrs = attrsMap.get(statement.id());
      if (attrs != null) {
        return EnterData.create(attrs);
      }
      return EnterData.EMPTY;
    }

    @Override
    public Optional<SafeHtml> exit() {
      depth--;
      return Optional.empty();
    }

    @Override
    public String evalLoggingFunction(LoggingFunctionInvocation value) {
      switch (value.functionName()) {
        case "depth":
          return Integer.toString(depth);
        default:
          throw new UnsupportedOperationException(value.toString());
      }
    }
  }

  @SoyFunctionSignature(name = "depth", value = @Signature(returnType = "string"))
  private static final class DepthFunction implements LoggingFunction {
    @Override
    public String getPlaceholder() {
      return "depth_placeholder";
    }
  }

  @Test
  public void testBasicLogging_treeStructure() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{velog FooVe}<div data-id=1>{velog Bar}<div data-id=2></div>"
            + "{/velog}{velog Baz}<div data-id=3></div>{/velog}</div>{/velog}");
    assertThat(sb.toString())
        .isEqualTo("<div data-id=1><div data-id=2></div><div data-id=3></div></div>");
    assertThat(testLogger.builder.toString())
        .isEqualTo("velog{id=1}\n" + "  velog{id=2}\n" + "  velog{id=3}");
  }

  @Test
  public void testBasicLogging_withData() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{velog ve_data(FooVe, Foo(intField: 123))}<div data-id=1></div>{/velog}");
    assertThat(sb.toString()).isEqualTo("<div data-id=1></div>");
    assertThat(testLogger.builder.toString())
        .isEqualTo("velog{id=1, data=soy.test.Foo{int_field: 123}}");
  }

  @Test
  public void testBasicLogging_logonly() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{velog FooVe logonly=\"true\"}<div data-id=1></div>{/velog}");
    // logonly ve's disable content generation
    assertThat(sb.toString()).isEmpty();
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1, logonly}");
  }

  @Test
  public void testBasicLogging_logonly_dynamic() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        ImmutableMap.of("t", true, "f", false, "n", 0),
        OutputAppendable.create(sb, testLogger),
        "{@param t : bool}",
        "{@param f : bool}",
        "{@param n : int}",
        // add the let as a regression test for a bug where we would generate code in the wrong
        // order which would cause us to try to save/restore the let value which hadn't been defined
        // yet!
        "{velog FooVe logonly=\"$t\"}<div data-id=1>{let $foo: 1 + $n /}{$foo +"
            + " $foo}</div>{/velog}",
        "{velog Bar logonly=\"$f\"}<div data-id=2></div>{/velog}");
    // logonly ve's disable content generation
    assertThat(sb.toString()).isEqualTo("<div data-id=2></div>");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1, logonly}\nvelog{id=2}");
  }

  @Test
  public void testBasicLogging_logonly_false_noLogger() throws Exception {
    StringBuilder sb = new StringBuilder();
    renderTemplate(
        ImmutableMap.of("b", false),
        OutputAppendable.create(sb),
        "{@param b : bool}",
        "{velog FooVe logonly=\"$b\"}<div></div>{/velog}");
    // logonly ve's disable content generation
    assertThat(sb.toString()).isEqualTo("<div></div>");
  }

  @Test
  public void testBasicLogging_logonly_true_noLogger() throws Exception {
    try {
      renderTemplate(
          ImmutableMap.of("b", true),
          OutputAppendable.create(new StringBuilder()),
          "{@param b : bool}",
          "{velog FooVe logonly=\"$b\"}<div></div>{/velog}");
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected)
          .hasMessageThat()
          .isEqualTo("Cannot set logonly=\"true\" unless there is a logger configured");
    }
  }

  @Test
  public void testLogging_letVariables() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $foo kind=\"html\"}{velog FooVe}<div data-id=1></div>{/velog}{/let}{$foo}{$foo}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}\nvelog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div data-id=1></div><div data-id=1></div>");
  }

  @Test
  public void testLogging_msg() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        ""
            + "{msg desc=\"a message!\"}\n"
            + "  Greetings, {velog FooVe}<a href='./wiki?human'>Human</a>{/velog}\n"
            + "{/msg}");
    assertThat(sb.toString()).isEqualTo("Greetings, <a href='./wiki?human'>Human</a>");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
  }

  // Regression test for a bug where logging would get dropped if there was a velog, in a msg around
  // a void element.
  @Test
  public void testLogging_msg_void_element() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        ""
            + "{msg desc=\"a message!\"}\n"
            + "  Greetings, {velog FooVe}<input type=text>{/velog}\n"
            + "{/msg}");
    assertThat(sb.toString()).isEqualTo("Greetings, <input type=text>");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
  }

  @Test
  public void testLogging_nestedLogOnly() throws IOException {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{velog FooVe logonly=\"true\"}<div data-id=1>{velog FooVe logonly=\"false\"}<div"
            + " data-id=1>{velog FooVe logonly=\"true\"}<div data-id=1>{velog FooVe"
            + " logonly=\"true\"}<div data-id=1></div>{/velog}"
            + "</div>{/velog}</div>{/velog}</div>{/velog}");
    assertThat(sb.toString()).isEmpty();
    assertThat(testLogger.builder.toString())
        .isEqualTo(
            "velog{id=1, logonly}\n"
                + "  velog{id=1}\n"
                + "    velog{id=1, logonly}\n"
                + "      velog{id=1, logonly}");
  }

  @Test
  public void testLogging_loggingFunction_basic() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "<div data-depth={depth()}></div>"
            + "{velog FooVe}<div data-depth={depth()}></div>{/velog}"
            + "<div data-depth={depth()}></div>");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString())
        .isEqualTo("<div data-depth=0></div><div data-depth=1></div><div data-depth=0></div>");
  }

  @Test
  public void testLogging_loggingFunction_usesPlaceholders() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $html kind=\"html\"}{velog FooVe}<div data-depth={depth()}></div>{/velog}{/let}"
            + "<script>{'' + $html}</script>");
    // nothing is logged because no elements were rendered
    assertThat(testLogger.builder.toString()).isEmpty();
    // everything is escaped, and the placeholder is used instead of a 'real value'
    assertThat(sb.toString()).contains("depth_placeholder");
  }

  @Test
  public void testLogging_elvis() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $log kind='html'}",
        "  {velog FooVe}",
        "    <div>hello</div>",
        "  {/velog}",
        "{/let}",
        "{$log ?? ''}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div>hello</div>");
  }

  @Test
  public void testLoggingAttributes_basic() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(1L, LoggingAttrs.builder().addDataAttribute("data-foo", "bar").build());
    renderTemplate(
        OutputAppendable.create(sb, testLogger), "{velog FooVe}<div>hello</div>{/velog}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div data-foo=\"bar\">hello</div>");
  }

  @Test
  public void testLoggingAttributes_withCall() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(1L, LoggingAttrs.builder().addDataAttribute("data-foo", "bar").build());
    renderTemplate(
        OutputAppendable.create(sb, testLogger), "{velog FooVe}{call another /}{/velog}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div data-foo=\"bar\">called</div>");
  }

  @Test
  public void testLoggingAttributes_withLet() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(1L, LoggingAttrs.builder().addDataAttribute("data-foo", "bar").build());
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $div kind='html'}<div>hello</div>{/let}",
        "{velog FooVe}{$div}{/velog}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div data-foo=\"bar\">hello</div>");
  }

  @Test
  public void testLoggingAttributes_withIf() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(1L, LoggingAttrs.builder().addDataAttribute("data-foo", "bar").build());
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $cond: true /}{velog FooVe}{if $cond}<div></div>{/if}{/velog}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div data-foo=\"bar\"></div>");
  }

  @Test
  public void testLoggingAttributes_withElse() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(1L, LoggingAttrs.builder().addDataAttribute("data-foo", "bar").build());
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $cond: false /}{velog FooVe}{if $cond}<span></span>{else}<div></div>{/if}{/velog}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div data-foo=\"bar\"></div>");
  }

  @Test
  public void testLoggingAttributes_withElseIf() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(1L, LoggingAttrs.builder().addDataAttribute("data-foo", "bar").build());
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $cond: false /}{let $otherCond: true /}{velog FooVe}{if $cond}<span></span>{elseif"
            + " $otherCond}<div></div>{/if}{/velog}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div data-foo=\"bar\"></div>");
  }

  @Test
  public void testLoggingAttributes_anchor() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(
        1L, LoggingAttrs.builder().addAnchorHref(SafeUrls.fromConstant("./go")).build());
    renderTemplate(OutputAppendable.create(sb, testLogger), "{velog FooVe}<a>hello</a>{/velog}");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<a href=\"./go\">hello</a>");
  }

  @Test
  public void testLoggingAttributes_hrefToNonAnchor() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    testLogger.attrsMap.put(
        1L, LoggingAttrs.builder().addAnchorHref(SafeUrls.fromConstant("./go")).build());
    var t =
        assertThrows(
            IllegalStateException.class,
            () ->
                renderTemplate(
                    OutputAppendable.create(sb, testLogger),
                    "{velog FooVe}<div>hello</div>{/velog}"));
    assertThat(t)
        .hasMessageThat()
        .isEqualTo("logger attempted to add anchor attributes to a non-anchor element.");
  }

  private void renderTemplate(OutputAppendable output, String... templateBodyLines)
      throws IOException {
    renderTemplate(ImmutableMap.of(), output, templateBodyLines);
  }

  private void renderTemplate(
      Map<String, ?> params, OutputAppendable output, String... templateBodyLines)
      throws IOException {
    SoyFileSetParserBuilder builder =
        SoyFileSetParserBuilder.forTemplateAndImports(
                "{const FooVe = ve_def('FooVe', 1, Foo) /}"
                    + "{const Bar = ve_def('Bar', 2, Foo) /}"
                    + "{const Baz = ve_def('Baz', 3, Foo) /}"
                    + "{const Quux = ve_def('Quux', 4, Foo) /}"
                    + "{template foo}\n"
                    + Joiner.on("\n").join(templateBodyLines)
                    + "\n{/template}"
                    + "{template another}<div>called</div>{/template}\n",
                Foo.getDescriptor())
            .addSoySourceFunction(new DepthFunction())
            .addHtmlAttributesForLogging(true)
            .runAutoescaper(true);
    SoyFileSetParser parser = builder.build();
    ParseResult parseResult = parser.parse();
    CompiledTemplates templates =
        BytecodeCompiler.compile(
                parseResult.registry(),
                parseResult.fileSet(),
                ErrorReporter.exploding(),
                parser.soyFileSuppliers(),
                builder.getTypeRegistry())
            .get();
    RenderContext ctx = TemplateTester.getDefaultContext(templates).toBuilder().build();
    StackFrame result =
        templates.getTemplate("ns.foo").render(null, TemplateTester.asParams(params), output, ctx);
    assertThat(result).isNull();
  }
}
