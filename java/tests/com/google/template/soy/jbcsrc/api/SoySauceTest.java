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

package com.google.template.soy.jbcsrc.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.template.soy.data.UnsafeSanitizedContentOrdainer.ordainAsSafe;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.jbcsrc.api.SoySauce.Continuation;
import com.google.template.soy.jbcsrc.api.SoySauce.WriteContinuation;
import com.google.template.soy.testing.Foo;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests basic soy sauce interaction */
@RunWith(JUnit4.class)
public class SoySauceTest {

  private SoySauce sauce;
  private TestAsyncPlugin testAsyncPlugin;

  @Before
  public void setUp() throws Exception {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    builder.add(SoySauceTest.class.getResource("strict.soy"));
    testAsyncPlugin = new TestAsyncPlugin();
    builder.addSourceFunction(testAsyncPlugin);
    builder.addProtoDescriptors(SoyFileKind.DEP, Foo.getDescriptor());
    sauce = builder.build().compileTemplates();
  }

  /** Verifies SoySauce#hasTemplate(String). */
  @Test
  public void testHasTemplate() {
    assertThat(sauce.hasTemplate("strict_test.helloHtml")).isTrue();
    assertThat(sauce.hasTemplate("i.do.not.exist")).isFalse();
  }

  /** Verifies SoySauce.Renderer#renderHtml(). */
  @Test
  public void testRenderHtml() {
    SanitizedContent sanitizedContent =
        sauce.renderTemplate("strict_test.helloHtml").renderHtml().get();
    assertThat(sanitizedContent).isEqualTo(ordainAsSafe("Hello world", ContentKind.HTML));
    assertThat(sanitizedContent.toString()).isEqualTo("Hello world");
    try {
      sauce.renderTemplate("strict_test.helloJs").renderHtml().get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloJs' to be kind=\"html\" but was kind=\"js\"");
    }
  }

  /** Verifies SoySauce.Renderer#renderHtml(AdvisingAppendable). */
  @Test
  public void testRenderHtml_toAppendable() throws IOException {
    TestAppendable builder = new TestAppendable();
    WriteContinuation continuation =
        sauce.renderTemplate("strict_test.helloHtml").renderHtml(builder);
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo("Hello world");
  }

  /** Verifies SoySauce.Renderer#renderJs(). */
  @Test
  public void testRenderJs() {
    SanitizedContent sanitizedContent =
        sauce.renderTemplate("strict_test.helloJs").renderJs().get();
    assertThat(sanitizedContent).isEqualTo(ordainAsSafe("'Hello world'", ContentKind.JS));
    assertThat(sanitizedContent.toString()).isEqualTo("'Hello world'");
    try {
      sauce.renderTemplate("strict_test.helloHtml").renderJs().get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloHtml' to be kind=\"js\" but was kind=\"html\"");
    }
  }

  /** Verifies SoySauce.Renderer#renderJs(AdvisingAppendable). */
  @Test
  public void testRenderJs_toAppendable() throws IOException {
    TestAppendable builder = new TestAppendable();
    WriteContinuation continuation = sauce.renderTemplate("strict_test.helloJs").renderJs(builder);
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo("'Hello world'");
  }

  /** Verifies SoySauce.Renderer#renderUri(). */
  @Test
  public void testRenderUri() {
    SanitizedContent sanitizedContent =
        sauce.renderTemplate("strict_test.helloUri").renderUri().get();
    assertThat(sanitizedContent).isEqualTo(ordainAsSafe("https://helloworld.com", ContentKind.URI));
    assertThat(sanitizedContent.toString()).isEqualTo("https://helloworld.com");
    try {
      sauce.renderTemplate("strict_test.helloHtml").renderUri().get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloHtml' to be kind=\"uri\" but was kind=\"html\"");
    }
  }

  /** Verifies SoySauce.Renderer#renderUri(AdvisingAppendable). */
  @Test
  public void testRenderUri_toAppendable() throws IOException {
    TestAppendable builder = new TestAppendable();
    WriteContinuation continuation =
        sauce.renderTemplate("strict_test.helloUri").renderUri(builder);
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo("https://helloworld.com");
  }

  /** Verifies SoySauce.Renderer#renderTrustedResourceUri(). */
  @Test
  public void testRenderTrustedResourceUri() {
    SanitizedContent sanitizedContent =
        sauce
            .renderTemplate("strict_test.helloTrustedResourceUri")
            .renderTrustedResourceUri()
            .get();
    assertThat(sanitizedContent)
        .isEqualTo(ordainAsSafe("/hello.world", ContentKind.TRUSTED_RESOURCE_URI));
    assertThat(sanitizedContent.toString()).isEqualTo("/hello.world");
    try {
      sauce.renderTemplate("strict_test.helloUri").renderTrustedResourceUri().get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloUri' to be kind=\"trusted_resource_uri\" but"
                  + " was kind=\"uri\"");
    }
  }

  /** Verifies SoySauce.Renderer#renderTrustedResourceUri(AdvisingAppendable). */
  @Test
  public void testRenderTrustedResourceUri_toAppendable() throws IOException {
    TestAppendable builder = new TestAppendable();
    WriteContinuation continuation =
        sauce
            .renderTemplate("strict_test.helloTrustedResourceUri")
            .renderTrustedResourceUri(builder);
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo("/hello.world");
  }

  /** Verifies SoySauce.Renderer#renderAttributes(). */
  @Test
  public void testRenderAttributes() {
    SanitizedContent sanitizedContent =
        sauce.renderTemplate("strict_test.helloAttributes").renderAttributes().get();
    assertThat(sanitizedContent)
        .isEqualTo(ordainAsSafe("hello-world=\"true\"", ContentKind.ATTRIBUTES));
    assertThat(sanitizedContent.toString()).isEqualTo("hello-world=\"true\"");
    try {
      sauce.renderTemplate("strict_test.helloUri").renderAttributes().get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloUri' to be kind=\"attributes\" but"
                  + " was kind=\"uri\"");
    }
  }

  /** Verifies SoySauce.Renderer#renderAttributes(AdvisingAppendable). */
  @Test
  public void testRenderAttributes_toAppendable() throws IOException {
    TestAppendable builder = new TestAppendable();
    WriteContinuation continuation =
        sauce.renderTemplate("strict_test.helloAttributes").renderAttributes(builder);
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo("hello-world=\"true\"");
  }

  /** Verifies SoySauce.Renderer#renderCss(). */
  @Test
  public void testRenderCss() {
    SanitizedContent sanitizedContent =
        sauce.renderTemplate("strict_test.helloCss").renderCss().get();
    assertThat(sanitizedContent)
        .isEqualTo(ordainAsSafe(".helloWorld {display: none}", ContentKind.CSS));
    assertThat(sanitizedContent.toString()).isEqualTo(".helloWorld {display: none}");
    try {
      sauce.renderTemplate("strict_test.helloUri").renderCss().get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloUri' to be kind=\"css\" but"
                  + " was kind=\"uri\"");
    }
  }

  /** Verifies SoySauce.Renderer#renderCss(AdvisingAppendable). */
  @Test
  public void testRenderCss_toAppendable() throws IOException {
    TestAppendable builder = new TestAppendable();
    WriteContinuation continuation =
        sauce.renderTemplate("strict_test.helloCss").renderCss(builder);
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo(".helloWorld {display: none}");
  }

  /**
   * Verifies SoySauce.Renderer#renderText(), checking that all content types can be rendered as
   * text.
   */
  @Test
  public void testRenderText() {
    assertThat(sauce.renderTemplate("strict_test.hello").renderText().get())
        .isEqualTo("Hello world");
    assertThat(sauce.renderTemplate("strict_test.helloHtml").renderText().get())
        .isEqualTo("Hello world");
    assertThat(sauce.renderTemplate("strict_test.helloJs").renderText().get())
        .isEqualTo("'Hello world'");
    assertThat(sauce.renderTemplate("strict_test.helloUri").renderText().get())
        .isEqualTo("https://helloworld.com");
    assertThat(sauce.renderTemplate("strict_test.helloTrustedResourceUri").renderText().get())
        .isEqualTo("/hello.world");
    assertThat(sauce.renderTemplate("strict_test.helloAttributes").renderText().get())
        .isEqualTo("hello-world=\"true\"");
    assertThat(sauce.renderTemplate("strict_test.helloCss").renderText().get())
        .isEqualTo(".helloWorld {display: none}");
  }

  /** Verifies SoySauce.Renderer#renderText(AdvisingAppendable). */
  @Test
  public void testRenderText_toAppendable() throws IOException {
    TestAppendable builder = new TestAppendable();
    WriteContinuation continuation = sauce.renderTemplate("strict_test.hello").renderText(builder);
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo("Hello world");
  }

  @Test
  public void testDetaching_string() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withParam");

    SettableFuture<String> p = SettableFuture.create();
    Continuation<String> stringContinuation = tmpl.setData(ImmutableMap.of("p", p)).renderText();
    assertThat(stringContinuation.result().type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(stringContinuation.result().future()).isEqualTo(p);
    p.set("tigger");
    stringContinuation = stringContinuation.continueRender();
    assertThat(stringContinuation.result()).isEqualTo(RenderResult.done());
    assertThat(stringContinuation.get()).isEqualTo("Hello, tigger");
  }

  @Test
  public void testDetaching_strict() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withParam");

    SettableFuture<String> p = SettableFuture.create();
    Continuation<SanitizedContent> strictContinuation =
        tmpl.setData(ImmutableMap.of("p", p)).renderHtml();
    assertThat(strictContinuation.result().type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(strictContinuation.result().future()).isEqualTo(p);
    p.set("pooh bear");
    strictContinuation = strictContinuation.continueRender();
    assertThat(strictContinuation.result()).isEqualTo(RenderResult.done());
    assertThat(strictContinuation.get().getContent()).isEqualTo("Hello, pooh bear");
  }

  @Test
  public void testDetaching_appendable() throws IOException {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withParam");
    TestAppendable builder = new TestAppendable();
    builder.softLimitReached = true;
    SettableFuture<String> p = SettableFuture.create();
    WriteContinuation continuation = tmpl.setData(ImmutableMap.of("p", p)).renderText(builder);
    assertThat(continuation.result().type()).isEqualTo(RenderResult.Type.LIMITED);
    // we check at the beginning of the template, so we immediately pause
    assertThat(builder.toString()).isEmpty();
    builder.softLimitReached = false;

    continuation = continuation.continueRender();
    assertThat(continuation.result().type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(continuation.result().future()).isEqualTo(p);
    p.set("piglet");
    continuation = continuation.continueRender();
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(builder.toString()).isEqualTo("Hello, piglet");
  }

  @Test
  public void testPluginDetaching_string() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withAsyncPluginCall");
    tmpl.setPluginInstances(ImmutableMap.of("testAsyncPlugin", () -> testAsyncPlugin));
    Continuation<SanitizedContent> continuation = tmpl.renderHtml();
    assertThat(continuation.result().type()).isEqualTo(RenderResult.Type.DETACH);
    assertThat(continuation.result().future()).isEqualTo(testAsyncPlugin.testAsyncPlugin());
    testAsyncPlugin.resolveTo("Charlie");
    continuation = continuation.continueRender();
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(continuation.get().getContent()).isEqualTo("Hello, Charlie!");
  }


  @Test
  public void testExceptionRewriting() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.callsItself");

    try {
      tmpl.setData(ImmutableMap.of("depth", 10, "p", IntegerData.ZERO)).renderHtml();
      fail();
    } catch (ClassCastException cce) {
      // we get a CCE because we passed an int but it expected a string
      StackTraceElement[] stackTrace = cce.getStackTrace();
      String fullStack = "Full stack:\n" + Joiner.on('\n').join(stackTrace) + "\n";
      assertWithMessage(fullStack)
          .that(stackTrace[1].toString())
          .isEqualTo("strict_test.callsItself(strict.soy:71)");

      for (int i = 2; i < 12; i++) {
        assertWithMessage(fullStack)
            .that(stackTrace[i].toString())
            .isEqualTo("strict_test.callsItself(strict.soy:73)");
      }
    }
  }

  /** Tests that a parameter set to {@code NullData} doesn't trigger the default parameter logic. */
  @Test
  public void testDefaultParam() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.defaultParam");

    assertThat(tmpl.setData(ImmutableMap.of("p", NullData.INSTANCE)).renderText().get())
        .isEqualTo("null");
  }

  // When eager evaluation fails, we defer the error and log it at the end

  // But if we report the error then we don't log it
  @Test
  public void testDeferredErrorLogging_throws() {
    SoySauce.Renderer tmpl =
        sauce
            .renderTemplate("strict_test.testEagerExecutionFailure")
            .setData(ImmutableMap.of("proto", Foo.getDefaultInstance(), "counter", 2));

    var exception = assertThrows(Exception.class, () -> tmpl.renderText().get());
    assertThat(exception).hasMessageThat().contains("expected Message, got undefined");
  }

  @Test
  public void testDeferredErrorLogging_throws_extrasAreSuppressed() {
    SoySauce.Renderer tmpl =
        sauce
            .renderTemplate("strict_test.testMultipleEagerExecutionFailures")
            .setData(ImmutableMap.of("proto", Foo.getDefaultInstance()));

    var exception = assertThrows(NullPointerException.class, () -> tmpl.renderText().get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo("'$proto.getMessageField()' evaluates to null");
    // The template optimistically evaluated two fields, and both failed.
    assertThat(exception.getSuppressed()).hasLength(2);
    Throwable suppressed0 = exception.getSuppressed()[0];
    assertThat(suppressed0)
        .hasMessageThat()
        .isEqualTo("Failed optimistic evaluation during rendering, this will soon become an error");
    assertThat(suppressed0)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("expected Message, got undefined");
    Throwable suppressed1 = exception.getSuppressed()[1];
    assertThat(suppressed1)
        .hasMessageThat()
        .isEqualTo("Failed optimistic evaluation during rendering, this will soon become an error");
    assertThat(suppressed1)
        .hasCauseThat()
        .hasMessageThat()
        .isEqualTo("expected Message, got undefined");
  }

  /**
   * Regression test for http://b/296964679. This ensures that execution order in == is preserved
   * (i.e. the left expression is always executed first). We analyze templates to figure out which
   * values have been resolved already so we won't generate detach code when we can prove the value
   * is already resolved, but that relies on the execution order staying the same.
   */
  @Test
  public void testExecutionOrder() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.testExecutionOrder");
    SettableFuture<Foo> s = SettableFuture.create();

    Continuation<SanitizedContent> continuation =
        tmpl.setData(ImmutableMap.of("protoFuture", s)).renderHtml();

    assertThat(continuation.result().type()).isEqualTo(RenderResult.Type.DETACH);

    s.set(
        Foo.newBuilder()
            .setBoolField(true)
            .addAllStringA(ImmutableList.of("these", "are", "strings", "!!"))
            .build());

    continuation = continuation.continueRender();
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(continuation.get().getContent()).isEqualTo("it works!");
  }

  @Test
  public void testCompareHtmlWithFutureImmediateNullDoesNotExecuteHtml() {
    // $html == $futureVal and $html != $futureVal with immediate future null
    SoySauce.Renderer tmpl1 = sauce.renderTemplate("strict_test.testCompareHtmlWithFuture");
    SanitizedContent result1 =
        tmpl1
            .setData(ImmutableMap.of("futureVal", Futures.immediateFuture(null)))
            .renderHtml()
            .get();
    assertThat(result1.getContent()).isEqualTo("looseNotEqualtripleNotEqual");

    // $futureVal == $html and $futureVal != $html with immediate future null
    SoySauce.Renderer tmpl2 = sauce.renderTemplate("strict_test.testCompareFutureWithHtml");
    SanitizedContent result2 =
        tmpl2
            .setData(ImmutableMap.of("futureVal", Futures.immediateFuture(null)))
            .renderHtml()
            .get();
    assertThat(result2.getContent()).isEqualTo("looseNotEqualtripleNotEqual");
  }

  @Test
  public void testCompareHtmlWithPendingFutureNullDoesNotExecuteHtml() {
    // $html == $futureVal with pending future resolving to null
    SoySauce.Renderer tmpl1 = sauce.renderTemplate("strict_test.testCompareHtmlWithFuture");
    SettableFuture<String> future1 = SettableFuture.create();

    Continuation<SanitizedContent> continuation1 =
        tmpl1.setData(ImmutableMap.of("futureVal", future1)).renderHtml();
    assertThat(continuation1.result().type()).isEqualTo(RenderResult.Type.DETACH);

    // Resolve future to null
    future1.set(null);
    continuation1 = continuation1.continueRender();
    assertThat(continuation1.result()).isEqualTo(RenderResult.done());
    assertThat(continuation1.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");

    // $futureVal == $html with pending future resolving to null
    SoySauce.Renderer tmpl2 = sauce.renderTemplate("strict_test.testCompareFutureWithHtml");
    SettableFuture<String> future2 = SettableFuture.create();

    Continuation<SanitizedContent> continuation2 =
        tmpl2.setData(ImmutableMap.of("futureVal", future2)).renderHtml();
    assertThat(continuation2.result().type()).isEqualTo(RenderResult.Type.DETACH);

    future2.set(null);
    continuation2 = continuation2.continueRender();
    assertThat(continuation2.result()).isEqualTo(RenderResult.done());
    assertThat(continuation2.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");
  }

  @Test
  public void testCompareHtmlWithPendingFutureNonNullDoesExecuteHtml() {
    // When future resolves to "hello", loose equality ($html == $futureVal) must evaluate $html and
    // match!
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.testCompareHtmlWithFuture");
    SettableFuture<String> future = SettableFuture.create();

    Continuation<SanitizedContent> continuation =
        tmpl.setData(ImmutableMap.of("futureVal", future, "failVal", "safe")).renderHtml();
    assertThat(continuation.result().type()).isEqualTo(RenderResult.Type.DETACH);

    future.set("hello");
    continuation = continuation.continueRender();
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(continuation.get().getContent()).isEqualTo("looseEqualtripleNotEqual");

    // Also test reverse operand order: $futureVal == $html
    SoySauce.Renderer tmpl2 = sauce.renderTemplate("strict_test.testCompareFutureWithHtml");
    SettableFuture<String> future2 = SettableFuture.create();

    Continuation<SanitizedContent> continuation2 =
        tmpl2.setData(ImmutableMap.of("futureVal", future2, "failVal", "safe")).renderHtml();
    assertThat(continuation2.result().type()).isEqualTo(RenderResult.Type.DETACH);

    future2.set("hello");
    continuation2 = continuation2.continueRender();
    assertThat(continuation2.result()).isEqualTo(RenderResult.done());
    assertThat(continuation2.get().getContent()).isEqualTo("looseEqualtripleNotEqual");
  }

  @Test
  public void testCompareBlobWithNullDoesNotExecuteHtml() {
    // $blob has static type ? but runtime type HTML. Comparing against null literal must not
    // execute HTML.
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.callTestCompareBlobWithNull");
    // failVal is not set, so checkNotNull($failVal) will throw if executed!
    var continuation = tmpl.renderHtml();
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(continuation.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");
  }

  @Test
  public void testCompareBlobWithOtherImmediateNullDoesNotExecuteHtml() {
    // Both $blob and $other have static type ?, with $blob = HTML and $other = null / immediate
    // future null.
    SoySauce.Renderer tmpl1 = sauce.renderTemplate("strict_test.callTestCompareBlobWithOther");
    var c1 = tmpl1.setData(ImmutableMap.of("other", NullData.INSTANCE)).renderHtml();
    assertThat(c1.result()).isEqualTo(RenderResult.done());
    assertThat(c1.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");

    SoySauce.Renderer tmpl2 = sauce.renderTemplate("strict_test.callTestCompareOtherWithBlob");
    var c2 = tmpl2.setData(ImmutableMap.of("other", NullData.INSTANCE)).renderHtml();
    assertThat(c2.result()).isEqualTo(RenderResult.done());
    assertThat(c2.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");

    // Immediate future null
    SoySauce.Renderer tmpl3 = sauce.renderTemplate("strict_test.callTestCompareBlobWithOther");
    var c3 = tmpl3.setData(ImmutableMap.of("other", Futures.immediateFuture(null))).renderHtml();
    assertThat(c3.result()).isEqualTo(RenderResult.done());
    assertThat(c3.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");

    SoySauce.Renderer tmpl4 = sauce.renderTemplate("strict_test.callTestCompareOtherWithBlob");
    var c4 = tmpl4.setData(ImmutableMap.of("other", Futures.immediateFuture(null))).renderHtml();
    assertThat(c4.result()).isEqualTo(RenderResult.done());
    assertThat(c4.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");

    // Non-null immediate string and immediate future (matching "hello")
    SoySauce.Renderer tmpl5 = sauce.renderTemplate("strict_test.callTestCompareBlobWithOther");
    var c5 = tmpl5.setData(ImmutableMap.of("other", "hello", "failVal", "safe")).renderHtml();
    assertThat(c5.result()).isEqualTo(RenderResult.done());
    assertThat(c5.get().getContent()).isEqualTo("looseEqualtripleNotEqual");

    SoySauce.Renderer tmpl6 = sauce.renderTemplate("strict_test.callTestCompareBlobWithOther");
    var c6 =
        tmpl6
            .setData(ImmutableMap.of("other", Futures.immediateFuture("hello"), "failVal", "safe"))
            .renderHtml();
    assertThat(c6.result()).isEqualTo(RenderResult.done());
    assertThat(c6.get().getContent()).isEqualTo("looseEqualtripleNotEqual");

    SoySauce.Renderer tmpl7 = sauce.renderTemplate("strict_test.callTestCompareOtherWithBlob");
    var c7 = tmpl7.setData(ImmutableMap.of("other", "hello", "failVal", "safe")).renderHtml();
    assertThat(c7.result()).isEqualTo(RenderResult.done());
    assertThat(c7.get().getContent()).isEqualTo("looseEqualtripleNotEqual");

    SoySauce.Renderer tmpl8 = sauce.renderTemplate("strict_test.callTestCompareOtherWithBlob");
    var c8 =
        tmpl8
            .setData(ImmutableMap.of("other", Futures.immediateFuture("hello"), "failVal", "safe"))
            .renderHtml();
    assertThat(c8.result()).isEqualTo(RenderResult.done());
    assertThat(c8.get().getContent()).isEqualTo("looseEqualtripleNotEqual");
  }

  @Test
  public void testCompareBlobWithPendingFutureNullDoesNotExecuteHtml() {
    // Both $blob and $other have static type ?, with $blob = HTML and $other = pending
    // SettableFuture.
    SoySauce.Renderer tmpl1 = sauce.renderTemplate("strict_test.callTestCompareBlobWithOther");
    SettableFuture<String> future1 = SettableFuture.create();

    Continuation<SanitizedContent> continuation1 =
        tmpl1.setData(ImmutableMap.of("other", future1)).renderHtml();
    assertThat(continuation1.result().type()).isEqualTo(RenderResult.Type.DETACH);

    future1.set(null);
    continuation1 = continuation1.continueRender();
    assertThat(continuation1.result()).isEqualTo(RenderResult.done());
    assertThat(continuation1.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");

    // Reverse operand order: $other == $blob
    SoySauce.Renderer tmpl2 = sauce.renderTemplate("strict_test.callTestCompareOtherWithBlob");
    SettableFuture<String> future2 = SettableFuture.create();

    Continuation<SanitizedContent> continuation2 =
        tmpl2.setData(ImmutableMap.of("other", future2)).renderHtml();
    assertThat(continuation2.result().type()).isEqualTo(RenderResult.Type.DETACH);

    future2.set(null);
    continuation2 = continuation2.continueRender();
    assertThat(continuation2.result()).isEqualTo(RenderResult.done());
    assertThat(continuation2.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");
  }

  @Test
  public void testCompareBlobWithPendingFutureNonNullDoesExecuteHtml() {
    // When future resolves to "hello", loose equality must evaluate $blob and match!
    SoySauce.Renderer tmpl1 = sauce.renderTemplate("strict_test.callTestCompareBlobWithOther");
    SettableFuture<String> future1 = SettableFuture.create();

    Continuation<SanitizedContent> continuation1 =
        tmpl1.setData(ImmutableMap.of("other", future1, "failVal", "safe")).renderHtml();
    assertThat(continuation1.result().type()).isEqualTo(RenderResult.Type.DETACH);

    future1.set("hello");
    continuation1 = continuation1.continueRender();
    assertThat(continuation1.result()).isEqualTo(RenderResult.done());
    assertThat(continuation1.get().getContent()).isEqualTo("looseEqualtripleNotEqual");

    // Reverse operand order: $other == $blob
    SoySauce.Renderer tmpl2 = sauce.renderTemplate("strict_test.callTestCompareOtherWithBlob");
    SettableFuture<String> future2 = SettableFuture.create();

    Continuation<SanitizedContent> continuation2 =
        tmpl2.setData(ImmutableMap.of("other", future2, "failVal", "safe")).renderHtml();
    assertThat(continuation2.result().type()).isEqualTo(RenderResult.Type.DETACH);

    future2.set("hello");
    continuation2 = continuation2.continueRender();
    assertThat(continuation2.result()).isEqualTo(RenderResult.done());
    assertThat(continuation2.get().getContent()).isEqualTo("looseEqualtripleNotEqual");
  }

  @Test
  public void testCompareNullWithBlobDoesNotExecuteHtml() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.callTestCompareNullWithBlob");
    var continuation = tmpl.renderHtml();
    assertThat(continuation.result()).isEqualTo(RenderResult.done());
    assertThat(continuation.get().getContent()).isEqualTo("looseNotEqualtripleNotEqual");
  }

  private static final class TestAppendable implements AdvisingAppendable {
    private final StringBuilder delegate = new StringBuilder();
    boolean softLimitReached;

    @CanIgnoreReturnValue
    @Override
    public TestAppendable append(CharSequence s) {
      delegate.append(s);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public TestAppendable append(CharSequence s, int start, int end) {
      delegate.append(s, start, end);
      return this;
    }

    @CanIgnoreReturnValue
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
}
