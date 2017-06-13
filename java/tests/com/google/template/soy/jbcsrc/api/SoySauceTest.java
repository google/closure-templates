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
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyModule;
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
    Injector injector = Guice.createInjector(new SoyModule());
    SoyFileSet.Builder builder = injector.getInstance(SoyFileSet.Builder.class);
    builder.add(SoySauceTest.class.getResource("strict.soy"));
    builder.add(SoySauceTest.class.getResource("non_strict.soy"));
    sauce = builder.build().compileTemplates();
  }

  @Test
  public void testStrictContentKindHandling_html() {
    assertEquals("Hello world", sauce.renderTemplate("strict_test.helloHtml").render().get());
    assertEquals(
        ordainAsSafe("Hello world", ContentKind.HTML),
        sauce.renderTemplate("strict_test.helloHtml").renderStrict().get());
    assertEquals(
        SanitizedContents.unsanitizedText("Hello world"),
        sauce
            .renderTemplate("strict_test.helloHtml")
            .setExpectedContentKind(ContentKind.TEXT)
            .renderStrict()
            .get());
    try {
      sauce
          .renderTemplate("strict_test.helloHtml")
          .setExpectedContentKind(ContentKind.JS)
          .renderStrict()
          .get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Expected template to be kind=\"js\" but was kind=\"html\": strict_test.helloHtml");
    }
  }

  @Test
  public void testStrictContentKindHandling_js() {
    try {
      sauce.renderTemplate("strict_test.helloJs").render();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Expected template to be kind=\"html\" but was kind=\"js\": strict_test.helloJs");
    }
    try {
      sauce.renderTemplate("strict_test.helloJs").renderStrict();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Expected template to be kind=\"html\" but was kind=\"js\": strict_test.helloJs");
    }
    assertEquals(
        ordainAsSafe("'Hello world'", ContentKind.JS),
        sauce
            .renderTemplate("strict_test.helloJs")
            .setExpectedContentKind(ContentKind.JS)
            .renderStrict()
            .get());
    assertEquals(
        ordainAsSafe("'Hello world'", ContentKind.TEXT),
        sauce
            .renderTemplate("strict_test.helloJs")
            .setExpectedContentKind(ContentKind.TEXT) // TEXT always works
            .renderStrict()
            .get());
    assertEquals(
        "'Hello world'",
        sauce
            .renderTemplate("strict_test.helloJs")
            .setExpectedContentKind(ContentKind.TEXT)
            .render()
            .get());
  }

  @Test
  public void testNonStrictContentHandling() {
    assertEquals("Hello world", sauce.renderTemplate("nonstrict_test.hello").render().get());
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
      assertThat(e.getMessage()).isEqualTo("Cannot render a non strict template as 'html'");
    }

    try {
      sauce.renderTemplate("nonstrict_test.hello").setExpectedContentKind(ContentKind.JS).render();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).isEqualTo("Cannot render a non strict template as 'js'");
    }
  }

  @Test
  public void testDetaching_string() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withParam");

    SettableFuture<String> p = SettableFuture.create();
    Continuation<String> stringContinuation = tmpl.setData(ImmutableMap.of("p", p)).render();
    assertEquals(RenderResult.Type.DETACH, stringContinuation.result().type());
    assertEquals(p, stringContinuation.result().future());
    p.set("tigger");
    stringContinuation = stringContinuation.continueRender();
    assertEquals(RenderResult.done(), stringContinuation.result());
    assertEquals("Hello, tigger", stringContinuation.get());
  }

  @Test
  public void testDetaching_strict() {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withParam");

    SettableFuture<String> p = SettableFuture.create();
    Continuation<SanitizedContent> strictContinuation =
        tmpl.setData(ImmutableMap.of("p", p)).renderStrict();
    assertEquals(RenderResult.Type.DETACH, strictContinuation.result().type());
    assertEquals(p, strictContinuation.result().future());
    p.set("pooh bear");
    strictContinuation = strictContinuation.continueRender();
    assertEquals(RenderResult.done(), strictContinuation.result());
    assertEquals("Hello, pooh bear", strictContinuation.get().getContent());
  }

  @Test
  public void testDetaching_appendable() throws IOException {
    SoySauce.Renderer tmpl = sauce.renderTemplate("strict_test.withParam");
    TestAppendable builder = new TestAppendable();
    builder.softLimitReached = true;
    SettableFuture<String> p = SettableFuture.create();
    WriteContinuation continuation = tmpl.setData(ImmutableMap.of("p", p)).render(builder);
    assertEquals(RenderResult.Type.LIMITED, continuation.result().type());
    assertEquals("Hello, ", builder.toString());
    builder.softLimitReached = false;

    continuation = continuation.continueRender();
    assertEquals(RenderResult.Type.DETACH, continuation.result().type());
    assertEquals(p, continuation.result().future());
    p.set("piglet");
    continuation = continuation.continueRender();
    assertEquals(RenderResult.done(), continuation.result());
    assertEquals("Hello, piglet", builder.toString());
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
      assertThat(stackTrace[0].toString())
          .isEqualTo("strict_test.callsItself.render(strict.soy:32)");

      for (int i = 1; i < 11; i++) {
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
