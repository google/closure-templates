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
import static com.google.template.soy.jbcsrc.TemplateTester.asParams;
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.SoyInjector;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.testing.Foo;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link DetachState}. */
@RunWith(JUnit4.class)
public final class DetachStateTest {
  static class TestAppendable extends LoggingAdvisingAppendable.BufferingAppendable {
    boolean softLimitReached;

    @Override
    public boolean softLimitReached() {
      return softLimitReached;
    }

    @Override
    public void flushBuffers(int depth) {
      throw new AssertionError("should not be called");
    }
  }

  @FunctionalInterface
  interface TemplateRenderer {
    default StackFrame render() throws IOException {
      return render(null);
    }

    StackFrame render(StackFrame frame) throws IOException;
  }

  // ensure that when we call back in, locals are restored
  @Test
  public void testDetach_saveRestore() throws IOException {
    SettableFuture<String> future = SettableFuture.create();

    CompiledTemplates templates =
        TemplateTester.compileTemplateBody(
            "{@param l : list<string>}", "{for $i in $l}", "  {$i}", "{/for}");
    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    TestAppendable output = new TestAppendable();
    TemplateRenderer renderer =
        (frame) ->
            template.render(
                frame,
                asParams(ImmutableMap.of("l", ImmutableList.of("a", future, "c"))),
                output,
                context);

    output.softLimitReached = true;
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());
    // we started with a limited appendable so we return immediatley without rendering.
    assertThat(output.toString()).isEmpty();

    // allow rendering to proceed
    output.softLimitReached = false;
    result = renderer.render(result);
    assertThat(result.asRenderResult().future()).isEqualTo(future);
    assertThat(output.toString()).isEqualTo("a");
    future.set("b");
    result = renderer.render(result);
    assertThat(result).isNull();
    // we jumped back into the loop and completed it.
    assertThat(output.toString()).isEqualTo("abc");
  }

  @Test
  public void testDetachOnUnResolvedProvider() throws IOException {
    SettableFuture<String> future = SettableFuture.create();
    CompiledTemplates templates =
        TemplateTester.compileTemplateBody("{@param foo : string}", "prefix{sp}{$foo}{sp}suffix");
    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        (frame) ->
            template.render(frame, asParams(ImmutableMap.of("foo", future)), output, context);

    var result = renderer.render();
    assertThat(result.asRenderResult().future()).isEqualTo(future);
    assertThat(output.toString()).isEqualTo("prefix ");

    // No progress is made, our caller is an idiot and didn't wait for the future
    result = renderer.render(result);
    assertThat(result.asRenderResult().future()).isEqualTo(future);
    assertThat(output.toString()).isEqualTo("prefix ");

    future.set("future");
    result = renderer.render(result);
    assertThat(result).isNull();
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
    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    ImmutableList<SettableFuture<String>> futures =
        ImmutableList.of(SettableFuture.create(), SettableFuture.create(), SettableFuture.create());
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        (frame) ->
            template.render(frame, asParams(ImmutableMap.of("list", futures)), output, context);

    var result = renderer.render();
    assertThat(result.asRenderResult().future()).isEqualTo(futures.get(0));
    assertThat(output.getAndClearBuffer()).isEqualTo("prefix\nloop-prefix\n");

    futures.get(0).set("first");
    result = renderer.render(result);
    assertThat(result.asRenderResult().future()).isEqualTo(futures.get(1));
    assertThat(output.getAndClearBuffer()).isEqualTo("first\nloop-suffix\nloop-prefix\n");

    futures.get(1).set("second");
    result = renderer.render(result);
    assertThat(result.asRenderResult().future()).isEqualTo(futures.get(2));
    assertThat(output.getAndClearBuffer()).isEqualTo("second\nloop-suffix\nloop-prefix\n");

    futures.get(2).set("third");
    result = renderer.render(result);
    assertThat(result).isNull();
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
    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    ParamStore params = asParams(ImmutableMap.of("list", ImmutableList.of(1, 2, 3, 4), "foo", 1));
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    assertThat(template.render(null, params, output, context)).isNull();
    assertThat(output.toString()).isEqualTo("2345");
  }

  @Test
  public void testDetachOnCall() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template caller}",
            "  {@param callerParam : string}",
            "  {call callee}",
            "    {param calleeParam: $callerParam /}",
            "  {/call}",
            "{/template}",
            "",
            "{template callee}",
            "  {@param calleeParam : string}",
            "  prefix {$calleeParam} suffix",
            "{/template}",
            "");
    CompiledTemplate template = templates.getTemplate("ns.caller");
    SettableFuture<String> param = SettableFuture.create();
    ParamStore params = asParams(ImmutableMap.of("callerParam", param));
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    RenderContext context = getDefaultContext(templates);
    TemplateRenderer renderer = frame -> template.render(frame, params, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(param));
    assertThat(output.toString()).isEqualTo("prefix ");
    param.set("foo");
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString()).isEqualTo("prefix foo suffix");
  }

  @Test
  public void testDetachOnParamTransclusion() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "/** */",
            "{template caller}",
            "  {@param callerParam : string}",
            "  {call callee}",
            "    {param calleeParam kind=\"text\"}",
            "      prefix {$callerParam} suffix",
            "    {/param}",
            "  {/call}",
            "{/template}",
            "",
            "/** */",
            "{template callee}",
            "  {@param calleeParam : string}",
            "  {$calleeParam}",
            "{/template}",
            "");
    CompiledTemplate template = templates.getTemplate("ns.caller");
    RenderContext context = getDefaultContext(templates);
    SettableFuture<String> param = SettableFuture.create();
    ParamStore params = asParams(ImmutableMap.of("callerParam", param));
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer = frame -> template.render(frame, params, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(param));
    assertThat(output.toString()).isEqualTo("prefix ");
    param.set("foo");
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString()).isEqualTo("prefix foo suffix");
  }

  @Test
  public void testDetach_msg() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template t}",
            "  {@param p : string}",
            "  {msg desc='...'}",
            "    Hello {$p phname='name'}!",
            "  {/msg}",
            "{/template}",
            "");
    CompiledTemplate template = templates.getTemplate("ns.t");
    RenderContext context = getDefaultContext(templates);
    SettableFuture<String> param = SettableFuture.create();
    ParamStore params = asParams(ImmutableMap.of("p", param));
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer = frame -> template.render(frame, params, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(param));
    assertThat(output.toString()).isEqualTo("Hello ");
    param.set("foo");
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString()).isEqualTo("Hello foo!");
  }

  @Test
  public void testDetach_msg_plural() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "{template t}",
            "  {@param count: number}",
            "  {msg desc='...'}",
            "    {plural $count}",
            "      {case 1}",
            "        1 item in cart",
            "      {default}",
            "        {$count} items in cart",
            "    {/plural}",
            "  {/msg}",
            "{/template}",
            "");
    CompiledTemplate template = templates.getTemplate("ns.t");
    SettableFuture<Integer> count = SettableFuture.create();
    ParamStore params = asParams(ImmutableMap.of("count", count));
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    RenderContext context = getDefaultContext(templates);
    TemplateRenderer renderer = frame -> template.render(frame, params, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(count));
    assertThat(output.toString()).isEmpty();
    count.set(2);
    assertThat(renderer.render()).isNull();
    assertThat(output.toString()).isEqualTo("2 items in cart");
  }

  /** Tests a ve log inside msg to make we can successfully detach in the ve_data expr. */
  @Test
  public void testDetach_veLogInsideMsg() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFileWithImports(
            new GenericDescriptor[] {Foo.getDescriptor()},
            "{const WithData = ve_def('WithData', 1, Foo) /}",
            "{template t}",
            "  {@param myBool : bool}",
            "  {msg desc=\"foo\"}",
            "    No definitions found for this word.{sp}",
            "    {velog ve_data(WithData, Foo(stringField: $myBool ? 'www.google.es' :"
                + " 'www.google.com'))}",
            "      <a phname=\"START_LINK\">",
            "        Try searching the web",
            "      </a phname=\"END_LINK\">",
            "    {/velog}",
            "    .",
            "  {/msg}",
            "{/template}",
            "");
    CompiledTemplate template = templates.getTemplate("ns.t");
    RenderContext context = getDefaultContext(templates);
    SettableFuture<Boolean> param = SettableFuture.create();
    ParamStore params = asParams(ImmutableMap.of("myBool", param));
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer = frame -> template.render(frame, params, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult().future()).isEqualTo(param);
    assertThat(output.toString()).isEqualTo("No definitions found for this word. ");

    // Resolve $myBool and finish rendering.
    param.set(false);
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString())
        .isEqualTo("No definitions found for this word. <a>Try searching the web</a>.");
  }

  @Test
  public void testNoDetachesForTrivialBlocks() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile("{namespace ns}", "", "{template t}", "", "{/template}", "");
    CompiledTemplate template = templates.getTemplate("ns.t");
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    assertThat(
            template.render(null, ParamStore.EMPTY_INSTANCE, output, getDefaultContext(templates)))
        .isNull();
    assertThat(output.toString()).isEmpty();
    assertThat(template.getClass().getDeclaredFields()).hasLength(0); // no $state field
  }

  @Test
  public void testLimitedAtTemplateEntryPoint() throws IOException {
    CompiledTemplates templates = TemplateTester.compileTemplateBody("hello world");
    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);

    TestAppendable output = new TestAppendable();
    TemplateRenderer renderer =
        (frame) -> template.render(frame, ParamStore.EMPTY_INSTANCE, output, context);
    output.softLimitReached = true;
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());
    // we started with a limited appendable so we return immediatley without rendering.
    assertThat(output.toString()).isEmpty();

    // even if we call back in we are still stuck
    result = renderer.render(result);
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());

    // allow rendering to proceed
    output.softLimitReached = false;

    // now we actually render
    result = renderer.render(result);
    assertThat(result).isNull();
    assertThat(output.toString()).isEqualTo("hello world");
  }

  // Regression test for a bug where a detach in the middle of a non-streaming escape directive
  // would cras
  @Test
  public void testDetachInNonStreamingCall() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFileAndRunAutoescaper(
            "{namespace ns}",
            "{template c}<a href=\"{call u /}\"></a>{/template}",
            "{template u kind='uri'}{@inject p:?}{$p}{/template}");
    CompiledTemplate template = templates.getTemplate("ns.c");

    SettableFuture<String> pending = SettableFuture.create();
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    RenderContext context =
        getDefaultContext(templates).toBuilder()
            .withIj(SoyInjector.fromStringMap(ImmutableMap.of("p", pending)))
            .build();
    TemplateRenderer renderer =
        (frame) -> template.render(frame, ParamStore.EMPTY_INSTANCE, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult().future()).isEqualTo(pending);
    assertThat(output.toString()).isEqualTo("<a href=\"");
    pending.set("www.foo.com");
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString()).isEqualTo("<a href=\"www.foo.com\"></a>");
  }

  @Test
  public void testLimitedAtTemplateEntryPoint_severalCalls() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "",
            "{template t}",
            "{@param depth: number}",
            "  {if $depth >0}",
            "    {$depth}",
            "    {call t}{param depth: $depth-1 /}{/call}",
            "  {/if}",
            "{/template}",
            "");
    CompiledTemplate template = templates.getTemplate("ns.t");
    TestAppendable output =
        new TestAppendable() {
          @Override
          public boolean softLimitReached() {
            boolean current = this.softLimitReached;
            this.softLimitReached = !current;
            return current;
          }
        };
    RenderContext context = getDefaultContext(templates);
    TemplateRenderer renderer =
        frame -> template.render(frame, asParams(ImmutableMap.of("depth", 4)), output, context);
    output.softLimitReached = true;
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEmpty();

    result = renderer.render(result);
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("4");

    result = renderer.render(result);
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("43");

    result = renderer.render(result);
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("432");

    result = renderer.render(result);
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.limited());
    assertThat(output.toString()).isEqualTo("4321");

    result = renderer.render(result);
    assertThat(result).isNull();
    assertThat(output.toString()).isEqualTo("4321");
  }

  @Test
  public void testDetachInErrorFallbackCall() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFileAndRunAutoescaper(
            "{namespace ns}",
            "{template c}<div>{call u errorfallback=\"skip\" /}</div>{/template}",
            "{template u}{@inject p:?}{$p}{/template}");
    CompiledTemplate template = templates.getTemplate("ns.c");

    SettableFuture<String> pending = SettableFuture.create();
    RenderContext context =
        getDefaultContext(templates).toBuilder()
            .withIj(SoyInjector.fromStringMap(ImmutableMap.of("p", pending)))
            .build();
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        frame -> template.render(frame, ParamStore.EMPTY_INSTANCE, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(pending));
    assertThat(output.toString()).isEqualTo("<div>");
    pending.set("HELLO");
    result = renderer.render(result);
    assertThat(result).isNull();
    assertThat(output.toString()).isEqualTo("<div>HELLO</div>");
  }

  // Regression test for a bug where our StackFrame stack manipulation would get confused when a
  // LetValueNode would depend on a LetContentNode and would fail to correctly restore the
  // StackFrame objects.  This would manifest as a ClassCastException (because the frame at the top
  // of the stack had the wrong shape), or a mis-render as parts of templates were skipped or
  // rendered multiple times.
  @Test
  public void testDetachInValueBlockDependingOnContentBlock() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "{template c}",
            "  {@inject p: ?}",
            "  {let $a kind='text'}<a>{$p}</a>{/let}",
            "  {let $aExpr: $a + '' /}",
            "  {let $h kind='html'}",
            "    {call u}{param c: $aExpr.length > 1 ? 1 : 0 /}{/call}",
            "  {/let}",
            "  <top>{$h}</top>",
            "{/template}",
            "{template u}",
            "  {@inject p: ?}",
            "  {@param c: ?}",
            "  {let $d : $c + 1 /}",
            "  {$p}<bottom>{$d}</bottom>",
            "{/template}");
    CompiledTemplate template = templates.getTemplate("ns.c");

    SettableFuture<Integer> pending = SettableFuture.create();
    RenderContext context =
        getDefaultContext(templates).toBuilder()
            .withIj(SoyInjector.fromStringMap(ImmutableMap.of("p", pending)))
            .build();
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        frame -> template.render(frame, ParamStore.EMPTY_INSTANCE, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(pending));
    assertThat(output.toString()).isEqualTo("<top>");
    pending.set(2);
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString()).isEqualTo("<top>2<bottom>2</bottom></top>");
  }

  // Tests an issue where a content block would get evaluated multiple times with multiple different
  // appendables.
  @Test
  public void testEvaluateContentBlockFromMultiplePlaces() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "{template c}",
            "  {@inject p: ?}",
            "  {let $content kind='text'}<a>{$p}</a>{/let}",
            "  {let $t kind='text'}",
            "    text({$content})",
            "  {/let}",
            "  {let $h kind='html'}",
            "    <pre>{$content}</pre>",
            "  {/let}",
            "  {let $te: $t + 'expr' /}",
            "  {let $he: $h + 'expr' /}",
            "  <top>{$he}:{$te}</top>",
            "{/template}");
    CompiledTemplate template = templates.getTemplate("ns.c");

    SettableFuture<Integer> pending = SettableFuture.create();
    RenderContext context =
        getDefaultContext(templates).toBuilder()
            .withIj(SoyInjector.fromStringMap(ImmutableMap.of("p", pending)))
            .build();
    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        (frame) -> template.render(frame, ParamStore.EMPTY_INSTANCE, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(pending));
    assertThat(output.toString()).isEqualTo("<top>");
    pending.set(2);
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString())
        .isEqualTo("<top><pre><a>2</a></pre>expr:text(<a>2</a>)expr</top>");
  }

  public static final class ExternRuntime {

    private final SettableFuture<String> value;

    public ExternRuntime(SettableFuture<String> value) {
      this.value = value;
    }

    public ListenableFuture<String> futureMessage() {
      return value;
    }
  }

  @Test
  public void testAsyncExtern() throws IOException {
    CompiledTemplates templates =
        TemplateTester.compileFile(
            "{namespace ns}",
            "{extern futureReturn: () => string}",
            "  {javaimpl class='" + ExternRuntime.class.getName() + "'",
            "    method='futureMessage' params='' type='instance'",
            "    return='com.google.common.util.concurrent.ListenableFuture<java.lang.String>' /}",
            "{/extern}",
            "{template c}",
            "  <span>{futureReturn()}</span>",
            "{/template}");

    CompiledTemplate template = templates.getTemplate("ns.c");

    SettableFuture<String> pending = SettableFuture.create();
    ExternRuntime runtime = new ExternRuntime(pending);

    RenderContext context =
        getDefaultContext(templates).toBuilder()
            .withPluginInstances(
                PluginInstances.of(ImmutableMap.of(ExternRuntime.class.getName(), () -> runtime)))
            .build();

    BufferingAppendable output = LoggingAdvisingAppendable.buffering();
    TemplateRenderer renderer =
        (frame) -> template.render(frame, ParamStore.EMPTY_INSTANCE, output, context);
    var result = renderer.render();
    assertThat(result.asRenderResult()).isEqualTo(RenderResult.continueAfter(pending));
    assertThat(output.toString()).isEqualTo("<span>");
    pending.set("a message from the future");
    assertThat(renderer.render(result)).isNull();
    assertThat(output.toString()).isEqualTo("<span>a message from the future</span>");
  }
}
