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
import static com.google.template.soy.jbcsrc.TemplateTester.asRecord;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.data.AbstractLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.internal.ParamStore;
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
  static final class TestAppendable extends AbstractLoggingAdvisingAppendable {
    private final StringBuilder delegate = new StringBuilder();
    boolean softLimitReached;

    @Override
    protected final void doAppend(CharSequence s) {
      delegate.append(s);
    }

    @Override
    protected final void doAppend(CharSequence s, int start, int end) {
      delegate.append(s, start, end);
    }

    @Override
    protected final void doAppend(char c) {
      delegate.append(c);
    }

    @Override
    public boolean softLimitReached() {
      return softLimitReached;
    }

    @Override
    public String toString() {
      return delegate.toString();
    }

    @Override
    protected void doEnterLoggableElement(LogStatement statement) {}

    @Override
    protected void doExitLoggableElement() {}

    @Override
    protected void doAppendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      delegate.append(funCall.placeholderValue());
    }
  }

  @Test
  public void testDetach_singleRawTextNode() throws IOException {
    CompiledTemplates templates = TemplateTester.compileTemplateBody("hello world");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template =
        factory.create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("hello world");

    output = new TestAppendable();
    output.softLimitReached = true;
    // detached!!!
    assertThat(template.render(output, context)).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("hello world");
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("hello world"); // nothing was added
  }

  @Test
  public void testDetach_css() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody(
            "{let $foo: 'foo'/}", "{css($foo, 'bar')}", "{css('baz')}");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template =
        factory.create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("foo-barbaz");

    output = new TestAppendable();
    output.softLimitReached = true;
    // css() does not detach
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("foo-barbaz");
  }

  @Test
  public void testDetach_xid() throws IOException {
    CompiledTemplates templates = TemplateTester.compileTemplateBody("{xid('foo')}");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template =
        factory.create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("foo_");

    output = new TestAppendable();
    output.softLimitReached = true;
    // xid() does not detach
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("foo_");
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
    CompiledTemplate template =
        factory.create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("hello world");

    output = new TestAppendable();
    output.softLimitReached = true;
    // detached!!!
    assertThat(template.render(output, context)).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("hello");
    assertThat(template.render(output, context)).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("hello ");
    assertThat(template.render(output, context)).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("hello world");
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("hello world"); // nothing was added
  }

  // ensure that when we call back in, locals are restored
  @Test
  public void testDetach_saveRestore() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody("{for $i in range(10)}", "  {$i}", "{/for}");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    CompiledTemplate template =
        factory.create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE);
    // Basic stuff works
    TestAppendable output = new TestAppendable();
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("0123456789");

    output = new TestAppendable();
    output.softLimitReached = true;
    for (int i = 0; i < 10; i++) {
      assertThat(template.render(output, context)).isEqualTo(RenderResult.limited());
      assertThat(output.toString()).isEqualTo(String.valueOf(i));
      output.delegate.setLength(0);
    }
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
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
        factory.create(asRecord(ImmutableMap.of("foo", future)), ParamStore.EMPTY_INSTANCE);

    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    RenderResult result = template.render(output, context);
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isEqualTo(future);
    assertThat(output.toString()).isEqualTo("prefix ");

    // No progress is made, our caller is an idiot and didn't wait for the future
    result = template.render(output, context);
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isEqualTo(future);
    assertThat(output.toString()).isEqualTo("prefix ");

    future.set("future");
    result = template.render(output, context);
    assertThat(result).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("prefix future suffix");
  }

  @Test
  public void testDetachOnEachIteration() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody(
            "{@param list : list<string>}",
            "prefix{\\n}",
            "{for $item in $list}",
            "  loop-prefix{\\n}",
            "  {$item}{\\n}",
            "  loop-suffix{\\n}",
            "{/for}",
            "suffix");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    List<SettableFuture<String>> futures =
        ImmutableList.of(SettableFuture.create(), SettableFuture.create(), SettableFuture.create());
    CompiledTemplate template =
        factory.create(asRecord(ImmutableMap.of("list", futures)), ParamStore.EMPTY_INSTANCE);

    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    RenderResult result = template.render(output, context);
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isEqualTo(futures.get(0));
    assertThat(output.getAndClearBuffer()).isEqualTo("prefix\nloop-prefix\n");

    futures.get(0).set("first");
    result = template.render(output, context);
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isEqualTo(futures.get(1));
    assertThat(output.getAndClearBuffer()).isEqualTo("first\nloop-suffix\nloop-prefix\n");

    futures.get(1).set("second");
    result = template.render(output, context);
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isEqualTo(futures.get(2));
    assertThat(output.getAndClearBuffer()).isEqualTo("second\nloop-suffix\nloop-prefix\n");

    futures.get(2).set("third");
    result = template.render(output, context);
    assertThat(result).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("third\nloop-suffix\nsuffix");
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
            "{for $item in $list}",
            "  {$item + $foo}",
            "{/for}");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    SoyRecord params = asRecord(ImmutableMap.of("list", ImmutableList.of(1, 2, 3, 4), "foo", 1));
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    assertThat(factory.create(params, ParamStore.EMPTY_INSTANCE).render(output, context))
        .isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("2345");
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
    CompiledTemplate template = factory.create(params, ParamStore.EMPTY_INSTANCE);
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    assertThat(template.render(output, getDefaultContext(templates)))
        .isEqualTo(RenderResult.continueAfter(param));
    assertThat(output.toString()).isEqualTo("prefix ");
    param.set("foo");
    assertThat(template.render(output, getDefaultContext(templates)))
        .isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("prefix foo suffix");
  }

  @Test
  public void testDetachOnParamTransclusion() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
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
    CompiledTemplate template = factory.create(params, ParamStore.EMPTY_INSTANCE);
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    assertThat(template.render(output, context)).isEqualTo(RenderResult.continueAfter(param));
    assertThat(output.toString()).isEqualTo("prefix ");
    param.set("foo");
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("prefix foo suffix");
  }

  @Test
  public void testDetach_msg() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template .t}",
            "  {@param p : string}",
            "  {msg desc='...'}",
            "    Hello {$p phname='name'}!",
            "  {/msg}",
            "{/template}",
            "");
    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.t");
    RenderContext context = getDefaultContext(templates);
    SettableFuture<String> param = SettableFuture.create();
    SoyRecord params = asRecord(ImmutableMap.of("p", param));
    CompiledTemplate template = factory.create(params, ParamStore.EMPTY_INSTANCE);
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    assertThat(template.render(output, context)).isEqualTo(RenderResult.continueAfter(param));
    assertThat(output.toString()).isEqualTo("Hello ");
    param.set("foo");
    assertThat(template.render(output, context)).isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEqualTo("Hello foo!");
  }

  @Test
  public void testNoDetachesForTrivialBlocks() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile("{namespace ns}", "", "{template .t}", "", "{/template}", "");
    CompiledTemplate template =
        templates
            .getTemplateFactory("ns.t")
            .create(ParamStore.EMPTY_INSTANCE, ParamStore.EMPTY_INSTANCE);
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    assertThat(template.render(output, getDefaultContext(templates)))
        .isEqualTo(RenderResult.done());
    assertThat(output.toString()).isEmpty();
    assertThat(template.getClass().getDeclaredFields()).hasLength(0); // no $state field
  }
}
