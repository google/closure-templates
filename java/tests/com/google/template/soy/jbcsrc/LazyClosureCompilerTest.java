/*
 * Copyright 2015 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jbcsrc.TemplateTester.asParams;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;
import static com.google.template.soy.jbcsrc.TemplateTester.compileTemplateBody;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;
import static java.util.Arrays.asList;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LazyClosureCompiler}. */
@RunWith(JUnit4.class)
public class LazyClosureCompilerTest {
  @Test
  public void testLetContentNode() {
    assertThatTemplateBody("{let $foo kind=\"text\"}", "  foo bar baz", "{/let}", "{$foo}")
        .rendersAs("foo bar baz");
  }

  @Test
  public void testLetContentNode_typed() {
    assertThatTemplateBody("{let $foo kind=\"html\"}", "  foo bar baz", "{/let}", "{$foo}")
        .rendersAs("foo bar baz");
  }

  @Test
  public void testLetNodes_nested() {
    assertThatTemplateBody(
            "{let $foo kind=\"text\"}",
            "  {let $foo kind=\"text\"}foo bar baz{/let}",
            "  {$foo}",
            "{/let}",
            "{$foo}")
        .rendersAs("foo bar baz");
  }

  @FunctionalInterface
  interface TemplateRenderer {
    RenderResult render() throws IOException;
  }

  @Test
  public void testLetContentNode_detaching() throws IOException {
    SettableFuture<String> bar = SettableFuture.create();
    CompiledTemplates templates =
        compileTemplateBody(
            "{@param bar : string }",
            "{let $foo kind=\"text\"}",
            "  hello {$bar}",
            "{/let}",
            "{$foo}");
    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        () ->
            template.render(
                asParams(ImmutableMap.of("bar", bar)), ParamStore.EMPTY_INSTANCE, output, context);
    RenderResult result = renderer.render();
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isSameInstanceAs(bar); // we found bar!
    assertThat(output.toString()).isEqualTo("hello ");

    // make sure no progress is made
    result = renderer.render();
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isSameInstanceAs(bar);
    assertThat(output.toString()).isEqualTo("hello ");
    bar.set("bar");

    assertThat(renderer.render()).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("hello bar");
  }

  @Test
  public void testLetValueNode() {
    assertThatTemplateBody("{let $foo : 1+2 /}", "{$foo}").rendersAs("3");

    assertThatTemplateBody("{let $null : null /}", "{$null}").rendersAs("null");

    assertThatTemplateBody("{let $bar : 'a' /}", "{let $foo : $bar + 'b' /}", "{$foo}")
        .rendersAs("ab");
  }

  @Test
  public void testLetValueNode_captureParameter() {
    assertThatTemplateBody("{@param param: string}", "{let $foo : $param + '_suffix' /}", "{$foo}")
        .rendersAs("string_suffix", ImmutableMap.of("param", "string"));
  }

  @Test
  public void testLetValueNode_nullableParameter() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? param : bool|null}",
            "{let $paramWithDefault : $param ?? true /}",
            "{$paramWithDefault ? 'true' : 'false'}");
    tester.rendersAs("true", ImmutableMap.of());
    tester.rendersAs("true", ImmutableMap.<String, Object>of("param", true));
    tester.rendersAs("false", ImmutableMap.<String, Object>of("param", false));
  }

  @Test
  public void testLetValueNode_nullableString() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? param : string|null}",
            "{@param? param2 : string|null}",
            "{let $paramWithDefault : $param ?? $param2 /}",
            "{$paramWithDefault}");
    tester.rendersAs("undefined", ImmutableMap.of());
    tester.rendersAs("1", ImmutableMap.<String, Object>of("param", "1"));
    tester.rendersAs("1", ImmutableMap.<String, Object>of("param", "1", "param2", "2"));
    tester.rendersAs("2", ImmutableMap.<String, Object>of("param2", "2"));
  }

  @Test
  public void testLetValueNode_optionalInts() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param comments: list<string>}",
            "{@param? numComments: number|null}",
            "  {let $numNotShown: ",
            "      ($numComments != null) and length($comments) > $numComments + 2 ?",
            "          length($comments) - $numComments : 0 /}",
            "  {$numNotShown}");
    tester.rendersAs("0", ImmutableMap.of("comments", ImmutableList.of(), "numComments", 2));
    tester.rendersAs("0", ImmutableMap.of("comments", ImmutableList.of()));
    tester.rendersAs(
        "3", ImmutableMap.of("comments", ImmutableList.of("a", "b", "c", "d"), "numComments", 1));
  }

  @Test
  public void testLetValueNode_complexConstant() throws Exception {
    CompiledTemplates templates =
        compileTemplateBody(
            "{let $fancyList: [$a + 1 for $a in range(100)] /}", "{join($fancyList,',')}");
    Class<?> fileClass = templates.getTemplateData("ns.foo").templateClass();
    Field fancyListField = fileClass.getDeclaredField("let_fancyList");
    assertThat(Modifier.toString(fancyListField.getModifiers())).isEqualTo("private static final");
    assertThat(fancyListField.getType()).isAssignableTo(SoyValue.class);
    fancyListField.setAccessible(true);
    ImmutableList<Long> list =
        ((SoyList) fancyListField.get(null))
            .asJavaList().stream()
                .map(svp -> ((SoyValue) svp).longValue())
                .collect(toImmutableList());
    assertThat(list).containsExactlyElementsIn(ContiguousSet.closedOpen(1L, 101L));
  }

  @Test
  public void testDetachOnFutureLazily() throws IOException {
    SettableFuture<String> bar = SettableFuture.create();
    CompiledTemplates templates =
        compileTemplateBody(
            "{@param bar : string }", "{let $foo : $bar + $bar /}", "before use", "{$foo}");
    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        () ->
            template.render(
                asParams(ImmutableMap.of("bar", bar)), ParamStore.EMPTY_INSTANCE, output, context);
    RenderResult result = renderer.render();
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isSameInstanceAs(bar); // we found bar!
    assertThat(output.toString()).isEqualTo("before use");

    // make sure no progress is made
    result = renderer.render();
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isSameInstanceAs(bar);
    assertThat(output.toString()).isEqualTo("before use");
    bar.set(" bar");

    assertThat(renderer.render()).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("before use bar bar");
  }

  @Test
  public void testLetValueNodeStructure() {
    // make sure we don't break normal reflection apis
    CompiledTemplates templates =
        compileTemplateBody("{let $bar : 'a' /}", "{let $foo : $bar + 1 /}");

    Class<?> fileClass = templates.getTemplateData("ns.foo").templateClass();

    assertThat(fileClass.getDeclaredClasses()).hasLength(1);
    List<Class<?>> innerClasses = Lists.newArrayList(fileClass.getDeclaredClasses());
    Class<?> let = Iterables.getOnlyElement(innerClasses);
    assertThat(let.getSimpleName()).isEqualTo("let_foo");
    assertThat(let.getDeclaringClass()).isEqualTo(fileClass);
  }

  private static final class IdentityJavaFunction implements SoyJavaFunction {
    @Override
    public String getName() {
      return "ident";
    }

    @Override
    public ImmutableSet<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }

    @Override
    public SoyValue computeForJava(List<SoyValue> args) {
      return args.get(0);
    }
  }

  @Test
  public void testConstantPluginFunction() {
    // There used to be a bug where we wouldn't properly box the expression into a SoyValueProvider
    // when it dynamically resolved to null.
    assertThatTemplateBody("{let $foo : ident(null) /}{$foo}")
        .withLegacySoyFunction(new IdentityJavaFunction())
        .rendersAs("null");
    assertThatTemplateBody("{let $foo : ident(undefined) /}{$foo}")
        .withLegacySoyFunction(new IdentityJavaFunction())
        .rendersAs("undefined");
    assertThatTemplateBody("{let $foo : ident(1) /}{$foo}")
        .withLegacySoyFunction(new IdentityJavaFunction())
        .rendersAs("1");
  }

  @SoyFunctionSignature(name = "ident", value = @Signature(parameterTypes = "?", returnType = "?"))
  private static final class IdentityJavaSourceFunction implements SoyJavaSourceFunction {
    @Override
    public JavaValue applyForJavaSource(
        JavaValueFactory factory, List<JavaValue> args, JavaPluginContext context) {
      return args.get(0);
    }
  }

  @Test
  public void testConstantPluginSourceFunction() {
    assertThatTemplateBody("{let $foo : ident(null) /}{$foo}")
        .withSoySourceFunction(new IdentityJavaSourceFunction())
        .rendersAs("null");
    assertThatTemplateBody("{let $foo : ident(undefined) /}{$foo}")
        .withSoySourceFunction(new IdentityJavaSourceFunction())
        .rendersAs("undefined");
    assertThatTemplateBody("{let $foo : ident(1) /}{$foo}")
        .withSoySourceFunction(new IdentityJavaSourceFunction())
        .rendersAs("1");
  }

  @Test
  public void testNullValue() {
    assertThatTemplateBody(
            "{let $null1 : null /}"
                + "{let $null2 : $null1 /}"
                + "{let $null3 : $null2 /}"
                + "{let $null4 : $null3 /}"
                + "{let $null5 : $null4 /}"
                + "{$null5}")
        .rendersAs("null");
  }

  @Test
  public void testTrivialLetClassStructure() throws Exception {
    CompiledTemplates templates =
        compileTemplateBody("{let $bar : [0,1,2][randomInt(1)] /}", "{let $foo : $bar /} {$foo}");
    Class<?> fileClass = templates.getTemplateData("ns.foo").templateClass();
    assertThat(asList(fileClass.getDeclaredClasses())).hasSize(1);
    assertThat(fileClass.getDeclaredClasses()[0].getSimpleName()).isEqualTo("let_bar");
    assertThat(asList(fileClass.getDeclaredFields())).isEmpty();
  }
}
