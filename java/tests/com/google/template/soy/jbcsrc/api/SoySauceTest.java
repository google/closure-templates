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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyModule;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;

import junit.framework.TestCase;

/**
 * Tests basic soy sauce interaction
 */
@SuppressWarnings("CheckReturnValue")
public class SoySauceTest extends TestCase {

  private SoySauce sauce;

  @Override protected void setUp() throws Exception {
    Injector injector = Guice.createInjector(new SoyModule());
    SoyFileSet.Builder builder = injector.getInstance(SoyFileSet.Builder.class);
    builder.add(SoySauceTest.class.getResource("strict.soy"));
    builder.add(SoySauceTest.class.getResource("non_strict.soy"));
    sauce = builder.build().compileTemplates();
  }

  public void testStrictContentKindHandling_html() {
    assertEquals("Hello world", 
        sauce.renderTemplate("strict_test.helloHtml").render().get());
    assertEquals(ordainAsSafe("Hello world", ContentKind.HTML),
        sauce.renderTemplate("strict_test.helloHtml").renderStrict().get());
    assertEquals(SanitizedContents.unsanitizedText("Hello world"),
        sauce.renderTemplate("strict_test.helloHtml")
            .setExpectedContentKind(ContentKind.TEXT)
            .renderStrict()
            .get());
    try {
      sauce.renderTemplate("strict_test.helloHtml")
          .setExpectedContentKind(ContentKind.JS)
          .renderStrict()
          .get();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo("Expected template to be kind=\"js\" but was kind=\"html\":"
              + " strict_test.helloHtml");
    }
  }

  public void testStrictContentKindHandling_js() {  
    try {
      sauce.renderTemplate("strict_test.helloJs").render();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo("Expected template to be kind=\"html\" but was kind=\"js\":"
              + " strict_test.helloJs");
    }
    try {
      sauce.renderTemplate("strict_test.helloJs").renderStrict();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo("Expected template to be kind=\"html\" but was kind=\"js\":"
              + " strict_test.helloJs");
    }
    assertEquals(ordainAsSafe("'Hello world'", ContentKind.JS),
        sauce.renderTemplate("strict_test.helloJs")
            .setExpectedContentKind(ContentKind.JS)
            .renderStrict()
            .get());
    assertEquals(ordainAsSafe("'Hello world'", ContentKind.TEXT),
        sauce.renderTemplate("strict_test.helloJs")
            .setExpectedContentKind(ContentKind.TEXT)  // TEXT always works
            .renderStrict()
            .get());
    assertEquals("'Hello world'",
        sauce.renderTemplate("strict_test.helloJs")
            .setExpectedContentKind(ContentKind.TEXT)
            .render()
            .get());
  }

  public void testNonStrictContentHandling() {
    assertEquals("Hello world", 
        sauce.renderTemplate("nonstrict_test.hello").render().get());
    assertEquals("Hello world", 
        sauce.renderTemplate("nonstrict_test.hello")
            .setExpectedContentKind(ContentKind.TEXT)  // text is always fine
            .render()
            .get());
    try {
      sauce.renderTemplate("nonstrict_test.hello").renderStrict();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage())
          .isEqualTo("Cannot render non strict templates to SanitizedContent");
    }
    
    try {
      sauce.renderTemplate("nonstrict_test.hello").setExpectedContentKind(ContentKind.JS).render();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).isEqualTo("Cannot render a non strict template as 'js'");
    }
  }
}
