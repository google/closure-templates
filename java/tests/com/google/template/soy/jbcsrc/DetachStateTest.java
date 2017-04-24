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
import static com.google.template.soy.data.SoyValueConverter.EMPTY_DICT;
import static com.google.template.soy.jbcsrc.TemplateTester.asRecord;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DetachState}. */
@RunWith(JUnit4.class)
public final class DetachStateTest {
  static final class TestAppendable implements AdvisingAppendable {
    private final StringBuilder delegate = new StringBuilder();
    boolean softLimitReached;

    @Override
    public TestAppendable append(CharSequence s) {
      delegate.append(s);
      return this;
    }

    @Override
    public TestAppendable append(CharSequence s, int start, int end) {
      delegate.append(s, start, end);
      return this;
    }

    @Override
    public TestAppendable append(char c) {
      delegate.append(c);
      return this;
    }

    @Override
    public boolean softLimitReached() {
      return softLimitReached;
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  @Test
  public void testDetach_singleRawTextNode() throws IOException {
    CompiledTemplates templates = TemplateTester.compileTemplateBody("hello world");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template = factory.create(EMPTY_DICT, EMPTY_DICT);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertEquals(RenderResult.done(), template.render(output, context));
    assertEquals("hello world", output.toString());

    output = new TestAppendable();
    output.softLimitReached = true;
    // detached!!!
    assertEquals(RenderResult.limited(), template.render(output, context));
    assertEquals("hello world", output.toString());
    assertEquals(RenderResult.done(), template.render(output, context));
    assertEquals("hello world", output.toString()); // nothing was added
  }

  @Test
  public void testDetach_multipleNodes() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody(
            "hello",
            // this print node inserts a space character and ensures that our raw text nodes don't
            // get merged
            "{' '}",
            "world");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template = factory.create(EMPTY_DICT, EMPTY_DICT);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertEquals(RenderResult.done(), template.render(output, context));
    assertEquals("hello world", output.toString());

    output = new TestAppendable();
    output.softLimitReached = true;
    // detached!!!
    assertEquals(RenderResult.limited(), template.render(output, context));
    assertEquals("hello", output.toString());
    assertEquals(RenderResult.limited(), template.render(output, context));
    assertEquals("hello ", output.toString());
    assertEquals(RenderResult.limited(), template.render(output, context));
    assertEquals("hello world", output.toString());
    assertEquals(RenderResult.done(), template.render(output, context));
    assertEquals("hello world", output.toString()); // nothing was added
  }

  // ensure that when we call back in, locals are restored
  @Test
  public void testDetach_saveRestore() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody("{for $i in range(10)}", "  {$i}", "{/for}");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template = factory.create(EMPTY_DICT, EMPTY_DICT);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertEquals(RenderResult.done(), template.render(output, context));
    assertEquals("0123456789", output.toString());

    output = new TestAppendable();
    output.softLimitReached = true;
    for (int i = 0; i < 10; i++) {
      assertEquals(RenderResult.limited(), template.render(output, context));
      assertEquals(String.valueOf(i), output.toString());
      output.delegate.setLength(0);
    }
    assertEquals(RenderResult.done(), template.render(output, context));
    assertThat(output.toString()).isEmpty(); // last render was empty
  }

  @Test
  public void testDetachOnUnResolvedProvider() throws IOException {
    SettableFuture<String> future = SettableFuture.create();
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody("{@param foo : string}", "prefix{sp}{$foo}{sp}suffix");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template =
        factory.create(asRecord(ImmutableMap.of("foo", future)), EMPTY_DICT);

    AdvisingStringBuilder output = new AdvisingStringBuilder();
    RenderResult result = template.render(output, context);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertEquals(future, result.future());
    assertEquals("prefix ", output.toString());

    // No progress is made, our caller is an idiot and didn't wait for the future
    result = template.render(output, context);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertEquals(future, result.future());
    assertEquals("prefix ", output.toString());

    future.set("future");
    result = template.render(output, context);
    assertEquals(RenderResult.done(), result);
    assertEquals("prefix future suffix", output.toString());
  }

  @Test
  public void testDetachOnEachIteration() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody(
            "{@param list : list<string>}",
            "prefix{\\n}",
            "{foreach $item in $list}",
            "  loop-prefix{\\n}",
            "  {$item}{\\n}",
            "  loop-suffix{\\n}",
            "{/foreach}",
            "suffix");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    List<SettableFuture<String>> futures =
        ImmutableList.of(
            SettableFuture.<String>create(),
            SettableFuture.<String>create(),
            SettableFuture.<String>create());
    CompiledTemplate template =
        factory.create(asRecord(ImmutableMap.of("list", futures)), EMPTY_DICT);

    AdvisingStringBuilder output = new AdvisingStringBuilder();
    RenderResult result = template.render(output, context);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertEquals(futures.get(0), result.future());
    assertEquals("prefix\nloop-prefix\n", output.getAndClearBuffer());

    futures.get(0).set("first");
    result = template.render(output, context);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertEquals(futures.get(1), result.future());
    assertEquals("first\nloop-suffix\nloop-prefix\n", output.getAndClearBuffer());

    futures.get(1).set("second");
    result = template.render(output, context);
    assertEquals(RenderResult.Type.DETACH, result.type());
    assertEquals(futures.get(2), result.future());
    assertEquals("second\nloop-suffix\nloop-prefix\n", output.getAndClearBuffer());

    futures.get(2).set("third");
    result = template.render(output, context);
    assertEquals(RenderResult.done(), result);
    assertEquals("third\nloop-suffix\nsuffix", output.toString());
  }

  // This test is for a bug where we were generating one detach logic block for a full expressions
  // but it caused stack merge errors because the runtime stack wasn't consistent across all detach
  // points.  See http://mail.ow2.org/wws/arc/asm/2015-04/msg00001.html
  @Test
  public void testDetachOnMultipleParamsInOneExpression() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody(
            "{@param list : list<int>}",
            "{@param foo : int}",
            "{foreach $item in $list}",
            "  {$item + $foo}",
            "{/foreach}");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    SoyRecord params = asRecord(ImmutableMap.of("list", ImmutableList.of(1, 2, 3, 4), "foo", 1));
    AdvisingStringBuilder output = new AdvisingStringBuilder();
    assertEquals(RenderResult.done(), factory.create(params, EMPTY_DICT).render(output, context));
    assertEquals("2345", output.toString());
  }

  @Test
  public void testDetachOnCall() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template .caller}",
            "  {@param callerParam : string}",
            "  {call .callee}",
            "    {param calleeParam: $callerParam /}",
            "  {/call}",
            "{/template}",
            "",
            "{template .callee}",
            "  {@param calleeParam : string}",
            "  prefix {$calleeParam} suffix",
            "{/template}",
            "");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.caller");
    SettableFuture<String> param = SettableFuture.create();
    SoyRecord params = asRecord(ImmutableMap.of("callerParam", param));
    CompiledTemplate template = factory.create(params, EMPTY_DICT);
    AdvisingStringBuilder output = new AdvisingStringBuilder();
    assertEquals(
        RenderResult.continueAfter(param), template.render(output, getDefaultContext(templates)));
    assertEquals("prefix ", output.toString());
    param.set("foo");
    assertEquals(RenderResult.done(), template.render(output, getDefaultContext(templates)));
    assertEquals("prefix foo suffix", output.toString());
  }

  @Test
  public void testDetachOnParamTransclusion() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns autoescape=\"strict\"}",
            "",
            "/** */",
            "{template .caller}",
            "  {@param callerParam : string}",
            "  {call .callee}",
            "    {param calleeParam kind=\"text\"}",
            "      prefix {$callerParam} suffix",
            "    {/param}",
            "  {/call}",
            "{/template}",
            "",
            "/** */",
            "{template .callee}",
            "  {@param calleeParam : string}",
            "  {$calleeParam}",
            "{/template}",
            "");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.caller");
    RenderContext context = getDefaultContext(templates);
    SettableFuture<String> param = SettableFuture.create();
    SoyRecord params = asRecord(ImmutableMap.of("callerParam", param));
    CompiledTemplate template = factory.create(params, EMPTY_DICT);
    AdvisingStringBuilder output = new AdvisingStringBuilder();
    assertEquals(RenderResult.continueAfter(param), template.render(output, context));
    assertEquals("prefix ", output.toString());
    param.set("foo");
    assertEquals(RenderResult.done(), template.render(output, context));
    assertEquals("prefix foo suffix", output.toString());
  }
}
