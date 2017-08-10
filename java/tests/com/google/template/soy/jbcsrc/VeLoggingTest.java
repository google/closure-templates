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
import static com.google.template.soy.data.SoyValueConverter.EMPTY_DICT;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.api.OutputAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.logging.LoggableElement;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@code {velog}} support. */
@RunWith(JUnit4.class)
public final class VeLoggingTest {
  private static final ValidatedLoggingConfig config =
      ValidatedLoggingConfig.create(
          LoggingConfig.newBuilder()
              .addElement(
                  LoggableElement.newBuilder()
                      .setName("Foo")
                      .setId(1L)
                      .setProtoType("soy.test.Foo"))
              .addElement(
                  LoggableElement.newBuilder()
                      .setName("Bar")
                      .setId(2L)
                      .setProtoType("soy.test.Foo"))
              .addElement(
                  LoggableElement.newBuilder()
                      .setName("Baz")
                      .setId(3L)
                      .setProtoType("soy.test.Foo"))
              .addElement(
                  LoggableElement.newBuilder()
                      .setName("Quux")
                      .setId(4L)
                      .setProtoType("soy.test.Foo"))
              .build());

  private static class TestLogger implements SoyLogger {
    final StringBuilder builder = new StringBuilder();
    int depth;

    @Override
    public void enter(LogStatement statement) {
      if (builder.length() > 0) {
        builder.append('\n');
      }
      builder.append(Strings.repeat("  ", depth)).append(statement);
      depth++;
    }

    @Override
    public void exit() {
      depth--;
    }

    @Override
    public String evalLoggingFunction(LoggingFunctionInvocation value) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void testBasicLogging_treeStructure() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{velog Foo}<div id=1>{velog Bar}<div id=2></div>"
            + "{/velog}{velog Baz}<div id=3></div>{/velog}</div>{/velog}");
    assertThat(sb.toString()).isEqualTo("<div id=1><div id=2></div><div id=3></div></div>");
    assertThat(testLogger.builder.toString())
        .isEqualTo("velog{id=1}\n" + "  velog{id=2}\n" + "  velog{id=3}");
  }

  @Test
  public void testBasicLogging_withData() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{velog Foo data=\"soy.test.Foo(intField: 123)\"}<div id=1></div>{/velog}");
    assertThat(sb.toString()).isEqualTo("<div id=1></div>");
    assertThat(testLogger.builder.toString())
        .isEqualTo("velog{id=1, data=soy.test.Foo{int_field: 123}}");
  }

  @Test
  public void testBasicLogging_logonly() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{velog Foo logonly=\"true\"}<div id=1></div>{/velog}");
    // TODO(b/63699313): nothing should be printed here.  we don't actually respect logonly yet
    assertThat(sb.toString()).isEqualTo("<div id=1></div>");
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1, logonly}");
  }

  @Test
  public void testLogging_letVariables() throws Exception {
    StringBuilder sb = new StringBuilder();
    TestLogger testLogger = new TestLogger();
    renderTemplate(
        OutputAppendable.create(sb, testLogger),
        "{let $foo kind=\"html\"}{velog Foo}<div id=1></div>{/velog}{/let}{$foo}{$foo}");
    // TODO(b/63699313): we lost one of the log statements... fix that by changing how we coerce
    // content blocks to strings
    assertThat(testLogger.builder.toString()).isEqualTo("velog{id=1}");
    assertThat(sb.toString()).isEqualTo("<div id=1></div><div id=1></div>");
  }

  private void renderTemplate(OutputAppendable output, String... templateBodyLines)
      throws IOException {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                "{namespace ns stricthtml=\"true\"}\n"
                    + "{template .foo}\n"
                    + Joiner.on("\n").join(templateBodyLines)
                    + "\n{/template}")
            .typeRegistry(
                new SoyTypeRegistry(
                    ImmutableSet.<SoyTypeProvider>of(
                        new SoyProtoTypeProvider.Builder()
                            .addDescriptors(com.google.template.soy.testing.Foo.getDescriptor())
                            .buildNoFiles())))
            .setLoggingConfig(config)
            .options(
                new SoyGeneralOptions().setExperimentalFeatures(Arrays.asList("logging_support")))
            .parse()
            .fileSet();
    TemplateRegistry templateRegistry = new TemplateRegistry(soyTree, ErrorReporter.exploding());
    CompiledTemplates templates =
        BytecodeCompiler.compile(templateRegistry, false, ErrorReporter.exploding()).get();
    RenderContext ctx = TemplateTester.getDefaultContext(templates);
    RenderResult result =
        templates.getTemplateFactory("ns.foo").create(EMPTY_DICT, EMPTY_DICT).render(output, ctx);
    assertEquals(RenderResult.done(), result);
  }
}
