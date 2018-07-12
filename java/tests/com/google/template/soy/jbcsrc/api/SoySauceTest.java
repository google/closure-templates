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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
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
    builder.add(SoySauceTest.class.getResource("non_strict.soy"));
    sauce = builder.build().compileTemplates();
  }

  @Test
  public void testStrictContentKindHandling_html() {
    assertThat(sauce.renderTemplate("strict_test.helloHtml").render().get())
        .isEqualTo("Hello world");
    assertThat(sauce.renderTemplate("strict_test.helloHtml").renderStrict().get())
        .isEqualTo(ordainAsSafe("Hello world", ContentKind.HTML));
    assertThat(
            sauce
                .renderTemplate("strict_test.helloHtml")
                .setExpectedContentKind(ContentKind.TEXT)
                .renderStrict()
                .get())
        .isEqualTo(SanitizedContents.unsanitizedText("Hello world"));
    try {
      sauce
          .renderTemplate("strict_test.helloHtml")
          .setExpectedContentKind(ContentKind.JS)
          .renderStrict()
          .get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloHtml' to be kind=\"js\" but was kind=\"html\"");
    }
  }

  @Test
  public void testStrictContentKindHandling_js() {
    try {
      sauce.renderTemplate("strict_test.helloJs").render();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloJs' to be kind=\"html\" but was kind=\"js\"");
    }
    try {
      sauce.renderTemplate("strict_test.helloJs").renderStrict();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Expected template 'strict_test.helloJs' to be kind=\"html\" but was kind=\"js\"");
    }
    assertThat(
            sauce
                .renderTemplate("strict_test.helloJs")
                .setExpectedContentKind(ContentKind.JS)
                .renderStrict()
                .get())
        .isEqualTo(ordainAsSafe("'Hello world'", ContentKind.JS));
    assertEquals(
        ordainAsSafe("'Hello world'", ContentKind.TEXT),
        sauce
            .renderTemplate("strict_test.helloJs")
            .setExpectedContentKind(ContentKind.TEXT) // TEXT always works
            .renderStrict()
            .get());
    assertThat(
            sauce
                .renderTemplate("strict_test.helloJs")
                .setExpectedContentKind(ContentKind.TEXT)
                .render()
                .get())
        .isEqualTo("'Hello world'");
  }

  @Test
  public void testNonStrictContentHandling() {
    assertThat(sauce.renderTemplate("nonstrict_test.hello").render().get())
        .isEqualTo("Hello world");
    assertEquals(
        "Hello world",
        sauce
            .renderTemplate("nonstrict_test.hello")
            .setExpectedContentKind(ContentKind.TEXT) // text is always fine
            .render()
            .get());
    assertEquals(
        SanitizedContents.unsanitizedText("Hello world"),
        sauce
            .renderTemplate("nonstrict_test.hello")
            .setExpectedContentKind(ContentKind.TEXT) // text is always fine, even with renderStrict
            .renderStrict()
            .get());
    try {
      sauce.renderTemplate("nonstrict_test.hello").renderStrict();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Cannot render a non strict template 'nonstrict_test.hello' as 'html'");
    }

    try {
      sauce.renderTemplate("nonstrict_test.hello").setExpectedContentKind(ContentKind.JS).render();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Cannot render a non strict template 'nonstrict_test.hello' as 'js'");
    }
  }

  @Test
  public void testDetaching_string() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withParam");

    SettableFuture<String> p = SettableFuture.create();
    Continuation<String> stringContinuation = tmpl.setData(ImmutableMap.of("p", p)).render();
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
        tmpl.setData(ImmutableMap.of("p", p)).renderStrict();
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
    WriteContinuation continuation = tmpl.setData(ImmutableMap.of("p", p)).render(builder);
    assertThat(continuation.result().type()).isEqualTo(RenderResult.Type.LIMITED);
    assertThat(builder.toString()).isEqualTo("Hello, ");
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
      tmpl.setData(ImmutableMap.of("depth", 10, "p", intProvider)).render();
      fail();
    } catch (ClassCastException cce) {
      // we get an CCE because we passed an int but it expected a string
      StackTraceElement[] stackTrace = cce.getStackTrace();
      assertThat(stackTrace[1].toString())
          .isEqualTo("strict_test.callsItself.render(strict.soy:32)");

      for (int i = 2; i < 12; i++) {
        assertThat(stackTrace[i].toString())
            .isEqualTo("strict_test.callsItself.render(strict.soy:34)");
      }
    }
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
