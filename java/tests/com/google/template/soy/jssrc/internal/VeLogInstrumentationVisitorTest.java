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
import com.google.common.truth.StringSubject;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.logging.LoggableElement;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.testing.LoggingConfigs;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.Foo;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class VeLogInstrumentationVisitorTest {
  private static final ValidatedLoggingConfig LOGGING_CONFIG =
      LoggingConfigs.createLoggingConfig(
          LoggableElement.newBuilder()
              .setName("FooVe")
              .setId(1L)
              .setProtoType("soy.test.Foo")
              .build(),
          LoggableElement.newBuilder().setName("Bar").setId(2L).build(),
          LoggableElement.newBuilder().setName("Baz").setId(3L).build());

  @Test
  public void testVeLogInstrumentation() throws Exception {
    assertThatSourceString(runPass("")).isEqualTo("");
    assertThatSourceString(runPass("<div></div>")).isEqualTo("<div></div>");
    assertThatSourceString(runPass("{velog FooVe}<div></div>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(FooVe), null)}"
                + "<div{$$velog(ve_data(ve(FooVe), null))}>"
                + "</div>"
                + "{/velog}");
    assertThatSourceString(runPass("{velog Bar}<input/>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Bar), null)}"
                + "<input{$$velog(ve_data(ve(Bar), null))}/>"
                + "{/velog}");
    assertThatSourceString(runPass("{velog Bar logonly=\"true\"}<input/>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Bar), null) logonly=\"true\"}"
                + "<input{$$velog(ve_data(ve(Bar), null), true)}/>"
                + "{/velog}");
    assertThatSourceString(
            runPass("{@param foo: bool}" + "{velog Bar logonly=\"$foo\"}<input/>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Bar), null) logonly=\"$foo\"}"
                + "<input{$$velog(ve_data(ve(Bar), null), $foo)}/>"
                + "{/velog}");
  }

  @Test
  public void testVeLogInstrumentationWithAttributes() throws Exception {
    assertThatSourceString(runPass("{velog Baz}<div id=\"1\"></div>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Baz), null)}"
                + "<div id=\"1\"{$$velog(ve_data(ve(Baz), null))}>"
                + "</div>"
                + "{/velog}");
    assertThatSourceString(runPass("{velog Bar logonly=\"true\"}<input id=\"1\"/>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Bar), null) logonly=\"true\"}"
                + "<input id=\"1\"{$$velog(ve_data(ve(Bar), null), true)}/>"
                + "{/velog}");
    assertThatSourceString(
            runPass(
                "{velog ve_data(FooVe, Foo(intField: 123))}"
                    + "<input id=\"1\" class=\"fooClass\"/>"
                    + "{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(FooVe), Foo(intField: 123))}"
                + "<input id=\"1\" class=\"fooClass\""
                + "{$$velog(ve_data(ve(FooVe), Foo(intField: 123)))}/>"
                + "{/velog}");
  }

  @Test
  public void testVeLogInstrumentationMultipleVeLogs() throws Exception {
    // Multiple velogs
    assertThatSourceString(
            runPass("{velog FooVe}<div></div>{/velog}{velog Bar}<div></div>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(FooVe), null)}"
                + "<div{$$velog(ve_data(ve(FooVe), null))}>"
                + "</div>{/velog}"
                + "{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "</div>{/velog}");
  }

  @Test
  public void testVeLogInstrumentationNestedVeLogs() throws Exception {
    // Nested
    assertThatSourceString(runPass("{velog Bar}<div>{velog Baz}<div></div>{/velog}</div>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "{velog ve_data(ve(Baz), null)}"
                + "<div{$$velog(ve_data(ve(Baz), null))}>"
                + "</div>{/velog}</div>{/velog}");
  }

  @Test
  public void testLoggingFunctionSimple() throws Exception {
    assertThatSourceString(
            runPass("{velog Bar}<div><span data-ved={currentVed()}></span></div>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "<span data-ved=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], 'data-ved')}>"
                + "</span>"
                + "</div>"
                + "{/velog}");
    assertThatSourceString(
            runPass("{velog Bar}<div><span data-ved={currentVed(1)}></span></div>{/velog}"))
        .isEqualTo(
            "{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "<span data-ved=\"placeholder\""
                + "{$$loggingFunction('currentVed', [1], 'data-ved')}>"
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
            "{let $foo : 'data-ved' /}{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "<span"
                + "{let $soy_logging_function_attribute_10 kind=\"text\"}{$foo}{/let} "
                + "{$soy_logging_function_attribute_10}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_10)}"
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
            "{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "<span"
                + "{let $soy_logging_function_attribute_9 kind=\"text\"}{$foo}{/let} "
                + "{$soy_logging_function_attribute_9}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_9)}"
                + "{let $soy_logging_function_attribute_13 kind=\"text\"}{$bar}{/let} "
                + "{$soy_logging_function_attribute_13}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [1], $soy_logging_function_attribute_13)}"
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
            "{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "<span"
                + "{let $soy_logging_function_attribute_9 kind=\"text\"}{$foo}{/let} "
                + "{$soy_logging_function_attribute_9}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_9)}"
                + "{let $baz kind=\"html\"}<input>{/let}"
                + "{let $soy_logging_function_attribute_16 kind=\"text\"}{$bar}{/let} "
                + "{$soy_logging_function_attribute_16}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [1], $soy_logging_function_attribute_16)}"
                + ">"
                + "</span>"
                + "</div>"
                + "{/velog}");
    // Test for calling another template.
    SoyFileSetNode node =
        runPass(
            "{velog Bar}<div>"
                + "<span {call attr}{param foo: 'data-ved' /}{/call}></span>"
                + "</div>{/velog}"
                + "{/template}"
                + ""
                + "{template attr kind=\"attributes\"}"
                + "{@param foo: string}"
                + "{$foo}={currentVed()}");
    StringBuilder sb = new StringBuilder();
    SoyTreeUtils.getAllNodesOfType(node, TemplateNode.class)
        .get(0)
        .appendSourceStringForChildren(sb);
    assertThat(sb.toString())
        .isEqualTo(
            "{velog ve_data(ve(Bar), null)}"
                + "<div{$$velog(ve_data(ve(Bar), null))}>"
                + "<span{call attr}{param foo: 'data-ved' /}{/call}>"
                + "</span>"
                + "</div>"
                + "{/velog}");
    sb = new StringBuilder();
    SoyTreeUtils.getAllNodesOfType(node, TemplateNode.class)
        .get(1)
        .appendSourceStringForChildren(sb);
    assertThat(sb.toString())
        .isEqualTo(
            "{let $soy_logging_function_attribute_17 kind=\"text\"}{$foo}{/let}"
                + "{$soy_logging_function_attribute_17}=\"placeholder\""
                + "{$$loggingFunction('currentVed', [], $soy_logging_function_attribute_17)}");
  }

  @SoyFunctionSignature(
      name = "currentVed",
      value = {
        @Signature(returnType = "string"),
        @Signature(
            parameterTypes = {"int"},
            returnType = "string")
      })
  private static final class TestLoggingFunction implements LoggingFunction {
    @Override
    public String getPlaceholder() {
      return "placeholder";
    }
  }

  /** Parses the given input as a template content. */
  private static SoyFileSetNode runPass(String input) {
    String soyFile = Joiner.on('\n').join("{template t}", input, "{/template}");
    ParseResult result =
        SoyFileSetParserBuilder.forTemplateAndImports(soyFile, Foo.getDescriptor())
            // Disable desguaring pass and manually run it later
            .desugarHtmlAndStateNodes(false)
            .setLoggingConfig(LOGGING_CONFIG)
            .addSoySourceFunction(new TestLoggingFunction())
            .errorReporter(ErrorReporter.exploding())
            .parse();
    SoyFileSetNode soyTree = result.fileSet();
    new VeLogInstrumentationVisitor().exec(soyTree);
    return soyTree;
  }

  private static StringSubject assertThatSourceString(SoyFileSetNode node) {
    StringBuilder sb = new StringBuilder();
    SoyTreeUtils.getAllNodesOfType(node, TemplateNode.class)
        .get(0)
        .appendSourceStringForChildren(sb);
    return assertThat(sb.toString());
  }
}
