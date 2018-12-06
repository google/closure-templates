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
import static com.google.template.soy.jbcsrc.TemplateTester.getDefaultContext;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.SoyFileSetParser;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.ForwardingLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** A test for the behavior of the compiler when streaming print directives are in use. */
@RunWith(JUnit4.class)
public final class StreamingPrintDirectivesTest {

  @Test
  public void testStreaming() throws IOException {
    BufferingAppendable output = BufferingAppendable.buffering();
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template .foo}",
            "  {@param future1 : ?}",
            "  {@param future2 : ?}",
            "  foo_prefix{sp}",
            "  {call .streamable}",
            "    {param p kind=\"html\"}",
            "      param_prefix{sp}{$future1}{sp}param_suffix",
            "    {/param}",
            "  {/call}",
            "",
            "  {sp}interlude{sp}",
            "",
            "  {call .unstreamable}",
            "    {param p kind=\"html\"}",
            "      param_prefix{sp}{$future2}{sp}param_suffix",
            "    {/param}",
            "  {/call}",
            "  {sp}foo_suffix",
            "{/template}",
            "",
            "{template .streamable}",
            "  {@param p : ?}",
            "  streamable_prefix{sp}",
            "  {$p |streaming}{sp}",
            "  streamable_suffix",
            "{/template}",
            "",
            "{template .unstreamable}",
            "  {@param p : ?}",
            "  unstreamable_prefix{sp}",
            "  {$p |nonstreaming}{sp}",
            "  unstreamable_suffix",
            "{/template}",
            "");

    CompiledTemplate.Factory factory = templates.getTemplateFactory("ns.foo");
    RenderContext context = getDefaultContext(templates);
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    CompiledTemplate create =
        factory.create(
            SoyValueConverterUtility.newDict("future1", future1, "future2", future2),
            SoyValueConverter.EMPTY_DICT);

    RenderResult result = create.render(output, context);
    // rendering paused because it found our future
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isSameAs(future1);
    // but we actually rendered the first half of the param even though it went through a print
    // directive. all the content in parens went through our directive
    assertThat(output.getAndClearBuffer())
        .isEqualTo("foo_prefix streamable_prefix (stream: param_prefix )");

    future1.set("future1");
    result = create.render(output, context);
    assertThat(result.type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(result.future()).isSameAs(future2);
    // here we made it into .unstreamable, but printed no part of the parameter due to the non
    // streamable print directive
    assertThat(output.getAndClearBuffer())
        .isEqualTo(
            "(stream: future1)(stream:  param_suffix) streamable_suffix interlude "
                + "unstreamable_prefix ");

    future2.set("future2");
    result = create.render(output, context);
    assertThat(result.isDone()).isTrue();
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
  public void testStreamingDisablesRuntimeTypeChecks() throws IOException {
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template .streamable}",
            "  {@param i : int}",
            "  {$i |streaming}",
            "{/template}",
            "",
            "{template .nonstreamable}",
            "  {@param i : int}",
            "  {$i |nonstreaming}",
            "{/template}");
    RenderContext context = getDefaultContext(templates);
    SoyDict badParam = SoyValueConverterUtility.newDict("i", "notAnInt");
    BufferingAppendable output = BufferingAppendable.buffering();
    templates
        .getTemplateFactory("ns.streamable")
        .create(badParam, EMPTY_DICT)
        .render(output, context);
    assertThat(output.getAndClearBuffer()).isEqualTo("(stream: notAnInt)");

    try {
      templates
          .getTemplateFactory("ns.nonstreamable")
          .create(badParam, EMPTY_DICT)
          .render(output, context);
      fail("Expected ClassCastException");
    } catch (ClassCastException cce) {
      assertThat(cce)
          .hasMessageThat()
          .isEqualTo(
              "com.google.template.soy.data.restricted.StringData cannot be cast to "
                  + "com.google.template.soy.data.restricted.IntegerData");
    }
  }

  @Test
  public void testStreamingEscapeHtml() throws IOException {
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template .tag}",
            "  {let $tag kind=\"html\"}",
            "    <div {call .attrs /}></div>",
            "  {/let}",
            "  {$tag}",
            "{/template}",
            "",
            "{template .attrs kind=\"attributes\"}",
            "  class=\"foo\"",
            "{/template}");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = BufferingAppendable.buffering();
    templates.getTemplateFactory("ns.tag").create(EMPTY_DICT, EMPTY_DICT).render(output, context);
    assertThat(output.getAndClearBuffer()).isEqualTo("<div class=\"foo\"></div>");
  }

  @Test
  public void testStreamingCall() throws IOException {
    // As of right now only a few directives support streaming, but this includes |escapeHtml and
    // |escapeJsString, so we should be able to transitively stream through all of that.
    CompiledTemplates templates =
        compileFile(
            "{namespace ns}",
            "",
            "{template .foo}",
            "  {call .bar data=\"all\"/}",
            "{/template}",
            "",
            "{template .bar}",
            "  <script>var x=\"{call .baz data=\"all\" /}\";</script>",
            "{/template}",
            "",
            "{template .baz kind=\"text\"}",
            "  {@param future : ?}",
            "  \"{$future}\" ",
            "{/template}",
            "");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = BufferingAppendable.buffering();
    SettableFuture<String> future = SettableFuture.create();
    CompiledTemplate template =
        templates
            .getTemplateFactory("ns.foo")
            .create(SoyValueConverterUtility.newDict("future", future), EMPTY_DICT);
    template.render(output, context);
    assertThat(output.getAndClearBuffer()).isEqualTo("<script>var x=\"\\x22");
    future.set("hello");
    template.render(output, context);
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
            "{template .foo}",
            "  {@param s : ?}",
            "  {$s |streaming:'first' |streaming:'second'}",
            "{/template}",
            "");
    RenderContext context = getDefaultContext(templates);
    BufferingAppendable output = BufferingAppendable.buffering();
    CompiledTemplate template =
        templates
            .getTemplateFactory("ns.foo")
            .create(SoyValueConverterUtility.newDict("s", "hello"), EMPTY_DICT);
    template.render(output, context);
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
            "{template .basic}",
            "  {@param p : ?}",
            "  {$p|streamingCloseable:' closed!'}",
            "{/template}",
            "",
            "{template .nested kind=\"text\"}",
            "  {@param p : ?}",
            "  {$p|streamingCloseable:'(c1)'|streamingCloseable:'(close)'}",
            "{/template}",
            "",
            "{template .nestedDeeper kind=\"text\"}",
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
    RenderResult result =
        templates
            .getTemplateFactory(name)
            .create(SoyValueConverter.INSTANCE.newDictFromMap(params), EMPTY_DICT)
            .render(output, context);
    assertThat(result.isDone()).isTrue();
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
            /*developmentMode=*/ false,
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
    public Set<Integer> getValidArgsSizes() {
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
          ConstructorRef.create(
                  AnnotatingAppendable.class, String.class, LoggingAdvisingAppendable.class)
              .construct(wrapperText, delegateAppendable));
    }
  }

  static final class NonStreamingDirective implements SoyJbcSrcPrintDirective {
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
    public Set<Integer> getValidArgsSizes() {
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

    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      delegate.enterLoggableElement(statement);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      delegate.exitLoggableElement();
      return this;
    }

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
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(1);
    }

    @Override
    public AppendableAndOptions applyForJbcSrcStreaming(
        JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
      return AppendableAndOptions.createCloseable(
          ConstructorRef.create(
                  CloseableAppendable.class, LoggingAdvisingAppendable.class, String.class)
              .construct(delegateAppendable, args.get(0).coerceToString()));
    }
  }

  /** An appendable that buffers all content until a call to close. */
  public static final class CloseableAppendable extends ForwardingLoggingAdvisingAppendable
      implements Closeable {
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
    public void close() throws IOException {
      if (appendCalled) {
        delegate.append(suffix);
      }
    }
  }
}
