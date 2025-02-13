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
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.data.ForwardingLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective.Streamable.AppendableAndOptions;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** A test for the behavior of the compiler when streaming print directives are in use. */
@RunWith(JUnit4.class)
public final class StreamingPrintDirectivesTest {
  @FunctionalInterface
  interface TemplateRenderer {
    @Nullable
    StackFrame render(StackFrame frame) throws IOException;

    @Nullable
    default StackFrame render() throws IOException {
      return render(null);
    }
  }

  @Test
  public void testStreaming() throws Exception {
    BufferingAppendable output = BufferingAppendable.buffering();
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template foo}",
            "  {@param future1 : ?}",
            "  {@param future2 : ?}",
            "  foo_prefix{sp}",
            "  {call streamable}",
            "    {param streamableParam kind=\"html\"}",
            "      param_prefix{sp}{$future1}{sp}param_suffix",
            "    {/param}",
            "  {/call}",
            "",
            "  {sp}interlude{sp}",
            "  {call unstreamable}",
            "    {param unstreamableParam kind=\"html\"}",
            "      param_prefix{sp}{$future2}{sp}param_suffix",
            "    {/param}",
            "  {/call}",
            "  {sp}foo_suffix",
            "{/template}",
            "",
            "{template streamable}",
            "  {@param streamableParam : ?}",
            "  streamable_prefix{sp}",
            "  {$streamableParam |streaming}{sp}",
            "  streamable_suffix",
            "{/template}",
            "",
            "{template unstreamable}",
            "  {@param unstreamableParam : ?}",
            "  unstreamable_prefix{sp}",
            "  {$unstreamableParam |nonstreaming}{sp}",
            "  unstreamable_suffix",
            "{/template}",
            "");

    CompiledTemplate template = templates.getTemplate("ns.foo");
    RenderContext context = getDefaultContext(templates);
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    ParamStore params =
        new ParamStore(2)
            .setField(RecordProperty.get("future1"), SoyValueConverter.INSTANCE.convert(future1))
            .setField(RecordProperty.get("future2"), SoyValueConverter.INSTANCE.convert(future2));
    TemplateRenderer renderer = frame -> template.render(frame, params, output, context);

    var result = renderer.render();
    // rendering paused because it found our future
    assertThat(result.asRenderResult().future()).isSameInstanceAs(future1);
    // but we actually rendered the first half of the param even though it went through a print
    // directive. all the content in parens went through our directive
    assertThat(output.getAndClearBuffer())
        .isEqualTo("foo_prefix streamable_prefix (stream: param_prefix )");

    future1.set("future1");
    result = renderer.render(result);
    assertThat(result.asRenderResult().future()).isSameInstanceAs(future2);
    // here we made it into .unstreamable, but printed no part of the parameter due to the non
    // streamable print directive
    assertThat(output.getAndClearBuffer())
        .isEqualTo(
            "(stream: future1)(stream:  param_suffix) streamable_suffix interlude "
                + "unstreamable_prefix ");

    future2.set("future2");
    result = renderer.render(result);
    assertThat(result).isNull();
    // now we render the full future2 parameter all at once and the
    assertThat(output.getAndClearBuffer())
        .isEqualTo("param_prefix future2 param_suffix unstreamable_suffix foo_suffix");
  }

  /**
   * This test demonstrates a change in behavior when streaming print directives are in use, they
   * can elide some runtime type checking. This is because the compiler explicitly puts in {@code
   * checkcast} instructions whenever calling {@link SoyValueProvider#resolve} (see every caller of
   * {@link ExpressionDetacher#resolveSoyValueProvider(Expression)}). However, when we are able to
   * stream a soy value provider we can't insert a {@code checkcast} instruction because we never
   * actually calculate the full value.
   *
   * <p>We could change SoyValueProvider.renderAndResolve to accept a TypePredicate and we could
   * sometimes enforce it, but for the time being this isn't happening.
   */
  @Test
  public void testStreamingDisablesRuntimeTypeChecks() throws Exception {
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template streamable}",
            "  {@param i : int}",
            "  {$i |streaming}",
            "{/template}",
            "",
            "{template nonstreamable}",
            "  {@param i : int}",
            "  {$i |nonstreaming}",
            "{/template}");
    RenderContext context = getDefaultContext(templates);
    ParamStore badParam = SoyValueConverterUtility.newParams("i", "notAnInt");
    BufferingAppendable output = BufferingAppendable.buffering();
    assertThat(templates.getTemplate("ns.streamable").render(null, badParam, output, context))
        .isNull();
    assertThat(output.getAndClearBuffer()).isEqualTo("(stream: notAnInt)");

    ClassCastException cce =
        assertThrows(
            ClassCastException.class,
            () ->
                templates.getTemplate("ns.nonstreamable").render(null, badParam, output, context));
    assertThat(cce).hasMessageThat().contains("expected number, got string");
  }

  @Test
  public void testStreamingEscapeHtml() throws IOException {
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template tag}",
            "  {let $tag kind=\"html\"}",
            "    <div {call attrs /}></div>",
            "  {/let}",
            "  {$tag}",
            "{/template}",
            "",
            "{template attrs kind=\"attributes\"}",
            "  class=\"foo\"",
            "{/template}");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = BufferingAppendable.buffering();
    assertThat(
            templates
                .getTemplate("ns.tag")
                .render(null, ParamStore.EMPTY_INSTANCE, output, context))
        .isNull();
    assertThat(output.getAndClearBuffer()).isEqualTo("<div class=\"foo\"></div>");
  }

  @Test
  public void testStreamingCall() throws Exception {
    // As of right now only a few directives support streaming, but this includes |escapeHtml and
    // |escapeJsString, so we should be able to transitively stream through all of that.
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template foo}",
            "  {call bar data=\"all\"/}",
            "{/template}",
            "",
            "{template bar}",
            "  <script>var x=\"{call baz data=\"all\" /}\";</script>",
            "{/template}",
            "",
            "{template baz kind=\"text\"}",
            "  {@param future : ?}",
            "  \"{$future}\" ",
            "{/template}",
            "");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = BufferingAppendable.buffering();
    SettableFuture<String> future = SettableFuture.create();
    CompiledTemplate template = templates.getTemplate("ns.foo");
    TemplateRenderer renderer =
        frame ->
            template.render(
                frame, SoyValueConverterUtility.newParams("future", future), output, context);

    var result = renderer.render();
    assertThat(result).isNotNull();
    assertThat(output.getAndClearBuffer()).isEqualTo("<script>var x=\"\\x22");
    future.set("hello");
    assertThat(renderer.render(result)).isNull();
    assertThat(output.getAndClearBuffer()).isEqualTo("hello\\x22\";</script>");
  }

  // There was a bug that caused us to apply print directives in the wrong order when there were
  // multiple streaming print directives.
  @Test
  public void testStreamingPrintOrdering() throws IOException {
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template foo}",
            "  {@param s : ?}",
            "  {$s |streaming:'first' |streaming:'second'}",
            "{/template}",
            "");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = BufferingAppendable.buffering();
    assertThat(
            templates
                .getTemplate("ns.foo")
                .render(null, SoyValueConverterUtility.newParams("s", "hello"), output, context))
        .isNull();
    assertThat(output.getAndClearBuffer()).isEqualTo("(second: (first: hello))");
  }

  @Test
  public void testStreamingCloseable() throws IOException {
    // Test to make sure the .close() is consistently called
    // the |streamingCloseable directive buffers all data until close is called so if close isn't
    // called it should print nothing.
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template basic}",
            "  {@param p : ?}",
            "  {$p|streamingCloseable:' closed!'}",
            "{/template}",
            "",
            "{template nested kind=\"text\"}",
            "  {@param p : ?}",
            "  {$p|streamingCloseable:'(c1)'|streamingCloseable:'(close)'}",
            "{/template}",
            "",
            "{template nestedDeeper kind=\"text\"}",
            "  {@param p : ?}",
            "  {$p|streamingCloseable:'(c1)'",
            "     |streamingCloseable:'(c2)'",
            "     |streamingCloseable:'(c3)'}",
            "{/template}",
            "");
    RenderContext context = getDefaultContext(templates);
    assertThat(renderToString("ns.basic", ImmutableMap.of("p", "hello"), templates, context))
        .isEqualTo("hello closed!");
    assertThat(renderToString("ns.nested", ImmutableMap.of("p", "hello"), templates, context))
        .isEqualTo("hello(c1)(close)");
    assertThat(renderToString("ns.nestedDeeper", ImmutableMap.of("p", "hello"), templates, context))
        .isEqualTo("hello(c1)(c2)(c3)");
  }

  private static String renderToString(
      String name,
      ImmutableMap<String, Object> params,
      CompiledTemplates templates,
      RenderContext context)
      throws IOException {
    BufferingAppendable output = BufferingAppendable.buffering();
    var result =
        templates
            .getTemplate(name)
            .render(
                null,
                ParamStore.fromRecord((SoyRecord) SoyValueConverter.INSTANCE.convert(params)),
                output,
                context);
    assertThat(result).isNull();
    return output.getAndClearBuffer();
  }

  static CompiledTemplates compileFile(String... fileBody) {
    String file = Joiner.on('\n').join(fileBody);
    SoyFileSetParser parser =
        SoyFileSetParserBuilder.forFileContents(file)
            .addPrintDirective(new StreamingDirective())
            .addPrintDirective(new NonStreamingDirective())
            .addPrintDirective(new StreamingCloseableDirective())
            .runAutoescaper(true)
            .build();
    ParseResult parseResult = parser.parse();
    return BytecodeCompiler.compile(
            parseResult.registry(),
            parseResult.fileSet(),
            ErrorReporter.exploding(),
            parser.soyFileSuppliers(),
            parser.typeRegistry())
        .get();
  }

  static final class StreamingDirective implements SoyJbcSrcPrintDirective.Streamable {
    @Override
    public SoyExpression applyForJbcSrc(
        JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
      throw new UnsupportedOperationException("should use the streaming interface");
    }

    @Override
    public String getName() {
      return "|streaming";
    }

    @Override
    public ImmutableSet<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0, 1);
    }

    @Override
    public AppendableAndOptions applyForJbcSrcStreaming(
        JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
      Expression wrapperText;
      if (!args.isEmpty()) {
        wrapperText = args.get(0).coerceToString();
      } else {
        wrapperText = BytecodeUtils.constant("stream");
      }
      return AppendableAndOptions.create(
          MethodRef.createNonPureConstructor(
                  AnnotatingAppendable.class, String.class, LoggingAdvisingAppendable.class)
              .invoke(wrapperText, delegateAppendable));
    }
  }

  static final class NonStreamingDirective implements SoyJbcSrcPrintDirective {
    @CanIgnoreReturnValue
    @Override
    public SoyExpression applyForJbcSrc(
        JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
      return value;
    }

    @Override
    public String getName() {
      return "|nonstreaming";
    }

    @Override
    public ImmutableSet<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }
  }

  /** An appendable that annotates each printed chunk with {@code (<wrapperText>: %s)}. */
  public static final class AnnotatingAppendable extends LoggingAdvisingAppendable {
    private final LoggingAdvisingAppendable delegate;
    private final String wrapperText;

    public AnnotatingAppendable(String wrapperText, LoggingAdvisingAppendable delegate) {
      this.delegate = delegate;
      this.wrapperText = wrapperText;
    }

    @Override
    public boolean softLimitReached() {
      return delegate.softLimitReached();
    }

    @Override
    public void flushBuffers(int depth) throws IOException {
      if (depth > 0) {
        delegate.flushBuffers(depth - 1);
      }
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
      delegate.append(wrap(csq));
      return this;
    }

    @Override
    public LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
        throws IOException {
      return append(csq.subSequence(start, end).toString());
    }

    @Override
    public LoggingAdvisingAppendable append(char c) throws IOException {
      return append("" + c);
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      delegate.enterLoggableElement(statement);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      delegate.exitLoggableElement();
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      delegate.appendLoggingFunctionInvocation(funCall, escapers);
      return this;
    }

    private String wrap(CharSequence s) {
      return String.format("(%s: %s)", wrapperText, s);
    }
  }

  static final class StreamingCloseableDirective implements SoyJbcSrcPrintDirective.Streamable {
    @Override
    public SoyExpression applyForJbcSrc(
        JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
      throw new UnsupportedOperationException("shouldn't be called");
    }

    @Override
    public String getName() {
      return "|streamingCloseable";
    }

    @Override
    public ImmutableSet<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }

    @Override
    public AppendableAndOptions applyForJbcSrcStreaming(
        JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
      return AppendableAndOptions.createCloseable(
          MethodRef.createNonPureConstructor(
                  CloseableAppendable.class, LoggingAdvisingAppendable.class, String.class)
              .invoke(delegateAppendable, args.get(0).coerceToString()));
    }
  }

  /** An appendable that buffers all content until a call to close. */
  public static final class CloseableAppendable extends ForwardingLoggingAdvisingAppendable {
    private final String suffix;
    private boolean appendCalled;

    public CloseableAppendable(LoggingAdvisingAppendable delegate, String suffix) {
      super(delegate);
      this.suffix = suffix;
    }

    @Override
    public LoggingAdvisingAppendable append(char c) throws IOException {
      appendCalled = true;
      return super.append(c);
    }

    @Override
    public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
      appendCalled = true;
      return super.append(csq);
    }

    @Override
    public LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
        throws IOException {
      appendCalled = true;
      return super.append(csq, start, end);
    }

    @Override
    public void flushBuffers(int depth) throws IOException {
      if (appendCalled) {
        delegate.append(suffix);
      }
      super.flushBuffers(depth);
    }
  }
}
