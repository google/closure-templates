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

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.data.SoyValueHelper.EMPTY_DICT;
import static com.google.template.soy.jbcsrc.TemplateTester.DEFAULT_CONTEXT;
import static com.google.template.soy.jbcsrc.TemplateTester.asRecord;
import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;
import static com.google.template.soy.jbcsrc.TemplateTester.compileTemplateBody;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.List;

/**
 * Tests for {@link LazyClosureCompiler}.
 */
public class LazyClosureCompilerTest extends TestCase {
  public void testLetContentNode() {
    assertThatTemplateBody(
        "{let $foo}",
        "  foo bar baz",
        "{/let}",
        "{$foo}").rendersAs("foo bar baz");
  }
  
  public void testLetContentNode_typed() {
    assertThatTemplateBody(
        "{let $foo kind=\"html\"}",
        "  foo bar baz",
        "{/let}",
        "{$foo}").rendersAs("foo bar baz");
  }
  
  public void testLetNodes_nested() {
    assertThatTemplateBody(
        "{let $foo}",
        "  {let $foo}foo bar baz{/let}",
        "  {$foo}",
        "{/let}",
        "{$foo}").rendersAs("foo bar baz");
  }

  public void testLetContentNode_detaching() throws IOException {
    SettableFuture<String> bar = SettableFuture.create();
    CompiledTemplate.Factory factory = compileTemplateBody(
        "{@param bar : string }",
        "{let $foo}",
        "  hello {$bar}",
        "{/let}",
        "{$foo}");
    CompiledTemplate template = factory.create(asRecord(ImmutableMap.of("bar", bar)), EMPTY_DICT);
    AdvisingStringBuilder output = new AdvisingStringBuilder();
    RenderResult result = template.render(output, DEFAULT_CONTEXT);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertSame(bar, result.future());  // we found bar!
    assertEquals("hello ", output.toString());

    // make sure no progress is made
    result = template.render(output, DEFAULT_CONTEXT);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertSame(bar, result.future());
    assertEquals("hello ", output.toString());
    bar.set("bar");

    assertEquals(RenderResult.done(), template.render(output, DEFAULT_CONTEXT));
    assertEquals("hello bar", output.toString());
  }

  public void testLetValueNode() {
    assertThatTemplateBody(
        "{let $foo : 1+2 /}",
        "{$foo}").rendersAs("3");
    
    assertThatTemplateBody(
        "{let $null : null /}",
        "{$null}").rendersAs("null");
    
    assertThatTemplateBody(
        "{let $bar : 'a' /}",
        "{let $foo : $bar + 'b' /}",
        "{$foo}").rendersAs("ab");
  }

  public void testLetValueNode_captureParameter() {
    assertThatTemplateBody(
        "{@param param: string}",
        "{let $foo : $param + '_suffix' /}",
        "{$foo}").rendersAs("string_suffix", ImmutableMap.of("param", "string"));
  }

  public void testLetValueNode_nullableParameter() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? param : bool}",
            "{let $paramWithDefault : $param ?: true /}",
            "{$paramWithDefault ? 'true' : 'false'}");
    tester.rendersAs("true", ImmutableMap.<String, Object>of());
    tester.rendersAs("true", ImmutableMap.<String, Object>of("param", true));
    tester.rendersAs("false", ImmutableMap.<String, Object>of("param", false));
  }

  public void testLetValueNode_nullableString() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param? param : string}",
            "{@param? param2 : string}",
            "{let $paramWithDefault : $param ?: $param2 /}",
            "{$paramWithDefault}");
    tester.rendersAs("null", ImmutableMap.<String, Object>of());
    tester.rendersAs("1", ImmutableMap.<String, Object>of("param", "1"));
    tester.rendersAs("1", ImmutableMap.<String, Object>of("param", "1", "param2", "2"));
    tester.rendersAs("2", ImmutableMap.<String, Object>of("param2", "2"));
  }

  public void testLetValueNode_optionalInts() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody(
            "{@param comments: list<string>}",
            "{@param? numComments: number}",
            "  {let $numNotShown: ",
            "      isNonnull($numComments) and length($comments) > $numComments + 2 ?",
            "          length($comments) - $numComments : 0 /}",
            "  {$numNotShown}");
    tester.rendersAs("0", ImmutableMap.of("comments", ImmutableList.of(), "numComments", 2));
    tester.rendersAs("0", ImmutableMap.of("comments", ImmutableList.of()));
    tester.rendersAs(
        "3", ImmutableMap.of("comments", ImmutableList.of("a", "b", "c", "d"), "numComments", 1));
  }

  public void testDetachOnFutureLazily() throws IOException {
    SettableFuture<String> bar = SettableFuture.create();
    CompiledTemplate.Factory factory = compileTemplateBody(
        "{@param bar : string }",
        "{let $foo : $bar + $bar /}",
        "before use",
        "{$foo}");

    CompiledTemplate template = factory.create(asRecord(ImmutableMap.of("bar", bar)), EMPTY_DICT);
    AdvisingStringBuilder output = new AdvisingStringBuilder();
    RenderResult result = template.render(output, DEFAULT_CONTEXT);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertSame(bar, result.future());  // we found bar!
    assertEquals("before use", output.toString());
    
    // make sure no progress is made
    result = template.render(output, DEFAULT_CONTEXT);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertSame(bar, result.future());
    assertEquals("before use", output.toString());
    bar.set(" bar");

    assertEquals(RenderResult.done(), template.render(output, DEFAULT_CONTEXT));
    assertEquals("before use bar bar", output.toString());
  }

  public void testLetValueNodeStructure() {
    // make sure we don't break normal reflection apis
    CompiledTemplate.Factory factory = compileTemplateBody(
        "{let $bar : 'a' /}",
        "{let $foo : $bar + 1 /}");
    CompiledTemplate template = factory.create(EMPTY_DICT, EMPTY_DICT);
    
    assertThat(template.getClass().getDeclaredClasses()).asList().hasSize(2);
    List<Class<?>> innerClasses = Lists.newArrayList(template.getClass().getDeclaredClasses());
    innerClasses.remove(factory.getClass());
    Class<?> let = Iterables.getOnlyElement(innerClasses);
    assertEquals("let_foo", let.getSimpleName());
    assertEquals(template.getClass(), let.getDeclaringClass());
  }
}
