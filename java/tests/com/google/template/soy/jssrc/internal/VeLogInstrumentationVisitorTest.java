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
package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.StringSubject;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.logging.LoggableElement;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VeLogInstrumentationVisitorTest {
  private static final ValidatedLoggingConfig LOGGING_CONFIG =
      ValidatedLoggingConfig.create(
          LoggingConfig.newBuilder()
              .addElement(
                  LoggableElement.newBuilder()
                      .setName("Foo")
                      .setId(1L)
                      .setProtoType("soy.test.Foo")
                      .build())
              .addElement(LoggableElement.newBuilder().setName("Bar").setId(2L).build())
              .addElement(LoggableElement.newBuilder().setName("Baz").setId(3L).build())
              .build());

  @Test
  public void testVeLogInstrumentation() throws Exception {
    assertThatSourceString(runPass("")).isEqualTo("");
    assertThatSourceString(runPass("<div></div>")).isEqualTo("<div></div>");
    assertThatSourceString(runPass("{velog Foo}<div></div>{/velog}"))
        .isEqualTo("{velog Foo}" + "<div{$$velog(1, null)}>" + "</div>" + "{/velog}");
    assertThatSourceString(runPass("{velog Bar}<input/>{/velog}"))
        .isEqualTo("{velog Bar}" + "<input{$$velog(2, null)}/>" + "{/velog}");
    assertThatSourceString(runPass("{velog Bar logonly=\"true\"}<input/>{/velog}"))
        .isEqualTo(
            "{velog Bar logonly=\"true\"}" + "<input{$$velog(2, null, true)}/>" + "{/velog}");
    assertThatSourceString(
            runPass("{@param foo: bool}" + "{velog Bar logonly=\"$foo\"}<input/>{/velog}"))
        .isEqualTo(
            "{velog Bar logonly=\"$foo\"}" + "<input{$$velog(2, null, $foo)}/>" + "{/velog}");
  }

  @Test
  public void testVeLogInstrumentationWithAttributes() throws Exception {
    assertThatSourceString(runPass("{velog Baz}<div id=\"1\"></div>{/velog}"))
        .isEqualTo("{velog Baz}" + "<div id=\"1\"{$$velog(3, null)}>" + "</div>" + "{/velog}");
    assertThatSourceString(runPass("{velog Bar logonly=\"true\"}<input id=\"1\"/>{/velog}"))
        .isEqualTo(
            "{velog Bar logonly=\"true\"}"
                + "<input id=\"1\"{$$velog(2, null, true)}/>"
                + "{/velog}");
    assertThatSourceString(
            runPass(
                "{velog Foo data=\"soy.test.Foo(intField: 123)\"}"
                    + "<input id=\"1\" class=\"fooClass\"/>"
                    + "{/velog}"))
        .isEqualTo(
            "{velog Foo data=\"soy.test.Foo(intField: 123)\"}"
                + "<input id=\"1\" class=\"fooClass\"{$$velog(1, soy.test.Foo(intField: 123))}/>"
                + "{/velog}");
  }

  @Test
  public void testVeLogInstrumentationMultipleVeLogs() throws Exception {
    // Multiple velogs
    assertThatSourceString(runPass("{velog Foo}<div></div>{/velog}{velog Bar}<div></div>{/velog}"))
        .isEqualTo(
            "{velog Foo}"
                + "<div{$$velog(1, null)}>"
                + "</div>{/velog}"
                + "{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "</div>{/velog}");
  }

  @Test
  public void testVeLogInstrumentationNestedVeLogs() throws Exception {
    // Nested
    assertThatSourceString(runPass("{velog Bar}<div>{velog Baz}<div></div>{/velog}</div>{/velog}"))
        .isEqualTo(
            "{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "{velog Baz}"
                + "<div{$$velog(3, null)}>"
                + "</div>{/velog}</div>{/velog}");
  }

  @Test
  public void testLoggingFunctionSimple() throws Exception {
    assertThatSourceString(
            runPass("{velog Bar}<div><span data-ved={currentVed()}></span></div>{/velog}"))
        .isEqualTo(
            "{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "<span data-ved=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], 'data-ved', 0)}>"
                + "</span>"
                + "</div>"
                + "{/velog}");
    assertThatSourceString(
            runPass("{velog Bar}<div><span data-ved={currentVed(1)}></span></div>{/velog}"))
        .isEqualTo(
            "{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "<span data-ved=\"placeholder\""
                + "{$$loggingFunction('currentVed', [1], 'data-ved', 0)}>"
                + "</span>"
                + "</div>"
                + "{/velog}");
  }

  @Test
  public void testLoggingFunctionsInsertingLetBlocks() {
    // Test for using print node as attribute name.
    assertThatSourceString(
            runPass(
                "{let $foo : 'data-ved' /}"
                    + "{velog Bar}<div><span {$foo}={currentVed()}></span></div>{/velog}"))
        .isEqualTo(
            "{let $foo : 'data-ved' /}{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "<span"
                + "{let $soy_logging_function_attribute_0}{$foo}{/let} "
                + "{$soy_logging_function_attribute_0}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_0, 0)}"
                + "></span>"
                + "</div>"
                + "{/velog}");
    // Test for multiple logging functions.
    assertThatSourceString(
            runPass(
                "{@param foo: string}{@param bar: string}"
                    + "{velog Bar}<div>"
                    + "<span {$foo}={currentVed()} {$bar}={currentVed(1)}></span>"
                    + "</div>{/velog}"))
        .isEqualTo(
            "{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "<span"
                + "{let $soy_logging_function_attribute_0}{$foo}{/let} "
                + "{$soy_logging_function_attribute_0}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_0, 0)}"
                + "{let $soy_logging_function_attribute_1}{$bar}{/let} "
                + "{$soy_logging_function_attribute_1}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [1], $soy_logging_function_attribute_1, 1)}"
                + ">"
                + "</span>"
                + "</div>"
                + "{/velog}");
    // Test that counters work fine for nested tags.
    assertThatSourceString(
            runPass(
                "{@param foo: string}{@param bar: string}"
                    + "{velog Bar}<div>"
                    + "<span "
                    + "{$foo}={currentVed()} "
                    + "{let $baz kind=\"html\"}<input>{/let} "
                    + "{$bar}={currentVed(1)}"
                    + "></span>"
                    + "</div>{/velog}"))
        .isEqualTo(
            "{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "<span"
                + "{let $soy_logging_function_attribute_0}{$foo}{/let} "
                + "{$soy_logging_function_attribute_0}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_0, 0)}"
                + "{let $baz kind=\"html\"}<input>{/let}"
                + "{let $soy_logging_function_attribute_1}{$bar}{/let} "
                + "{$soy_logging_function_attribute_1}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [1], $soy_logging_function_attribute_1, 1)}"
                + ">"
                + "</span>"
                + "</div>"
                + "{/velog}");
    // Test for calling another template.
    SoyFileSetNode node =
        runPass(
            "{velog Bar}<div>"
                + "<span {call .attr}{param foo : 'data-ved' /}{/call}></span>"
                + "</div>{/velog}"
                + "{/template}"
                + ""
                + "{template .attr kind=\"attributes\"}"
                + "{@param foo : string}"
                + "{$foo}={currentVed()}");
    StringBuilder sb = new StringBuilder();
    node.getChild(0).getChild(0).appendSourceStringForChildren(sb);
    assertThat(sb.toString())
        .isEqualTo(
            "{velog Bar}"
                + "<div{$$velog(2, null)}>"
                + "<span {call .attr}{param foo : 'data-ved' /}{/call}>"
                + "</span>"
                + "</div>"
                + "{/velog}");
    sb = new StringBuilder();
    node.getChild(0).getChild(1).appendSourceStringForChildren(sb);
    assertThat(sb.toString())
        .isEqualTo(
            "{let $soy_logging_function_attribute_0}{$foo}{/let}"
                + "{$soy_logging_function_attribute_0}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_0, 0)}");
  }

  private static final class TestLoggingFunction implements LoggingFunction {
    @Override
    public String getName() {
      return "currentVed";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0, 1);
    }

    @Override
    public String getPlaceholder() {
      return "placeholder";
    }
  }

  /** Parses the given input as a template content. */
  private static SoyFileSetNode runPass(String input) {
    String soyFile =
        Joiner.on('\n').join("{namespace ns}", "", "{template .t}", input, "{/template}");
    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(soyFile)
            // Disable desguaring pass and manually run it later
            .desugarHtmlNodes(false)
            .typeRegistry(
                new SoyTypeRegistry.Builder()
                    .addDescriptors(
                        ImmutableList.of(com.google.template.soy.testing.Foo.getDescriptor()))
                    .build())
            .setLoggingConfig(LOGGING_CONFIG)
            .addSoyFunction(new TestLoggingFunction())
            .errorReporter(ErrorReporter.exploding())
            .parse();
    TemplateRegistry templateRegistry = result.registry();
    SoyFileSetNode soyTree = result.fileSet();
    new VeLogInstrumentationVisitor(templateRegistry).exec(soyTree);
    return soyTree;
  }

  private static StringSubject assertThatSourceString(SoyFileSetNode node) {
    StringBuilder sb = new StringBuilder();
    node.getChild(0).getChild(0).appendSourceStringForChildren(sb);
    return assertThat(sb.toString());
  }
}
