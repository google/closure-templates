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
import static com.google.template.soy.data.UnsafeSanitizedContentOrdainer.ordainAsSafe;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.jbcsrc.api.SoySauce.Continuation;
import com.google.template.soy.jbcsrc.api.SoySauce.WriteContinuation;
import com.google.template.soy.jbcsrc.runtime.DetachableSoyValueProvider;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests basic soy sauce interaction */
@RunWith(JUnit4.class)
public class SoySauceTest {

  private SoySauce sauce;

  @Before
  public void setUp() throws Exception {
    SoyFileSet.Builder builder = SoyFileSet.builder();
    builder.add(SoySauceTest.class.getResource("strict.soy"));
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
  public void testExceptionRewriting() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.callsItself");

    SoyValueProvider intProvider =
        new DetachableSoyValueProvider() {
          @Override
          protected RenderResult doResolve() {
            resolvedValue = IntegerData.ZERO;
            return RenderResult.done();
          }
        };

    try {
      tmpl.setData(ImmutableMap.of("depth", 10, "p", intProvider)).renderHtml();
      fail();
    } catch (ClassCastException cce) {
      // we get an CCE because we passed an int but it expected a string
      StackTraceElement[] stackTrace = cce.getStackTrace();
      assertThat(stackTrace[0].toString()).isEqualTo("strict_test.callsItself(strict.soy:52)");

      for (int i = 1; i < 11; i++) {
        assertThat(stackTrace[i].toString()).isEqualTo("strict_test.callsItself(strict.soy:54)");
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

  private static final class TestAppendable implements AdvisingAppendable {
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
}
