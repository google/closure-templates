/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.data;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for UnsafeSanitizedContentOrdainer utility class.
 */
@RunWith(JUnit4.class)
public class UnsafeSanitizedContentOrdainerTest {

  @Test
  public void testOrdainAsSafe() {
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello <b>World</b>", ContentKind.HTML))
        .isEqualTo(SanitizedContent.create("Hello <b>World</b>", ContentKind.HTML, null));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello_world();", ContentKind.JS))
        .isEqualTo(SanitizedContent.create("hello_world();", ContentKind.JS, Dir.LTR));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello_world();", ContentKind.CSS))
        .isEqualTo(SanitizedContent.create("hello_world();", ContentKind.CSS, Dir.LTR));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello/world", ContentKind.URI))
        .isEqualTo(SanitizedContent.create("hello/world", ContentKind.URI, Dir.LTR));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello=world", ContentKind.ATTRIBUTES))
        .isEqualTo(SanitizedContent.create("hello=world", ContentKind.ATTRIBUTES, Dir.LTR));
  }

  @Test
  public void testTextIsntSupported() {
    try {
      UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT, Dir.LTR);
      fail();
    } catch (IllegalArgumentException iae) {
      assertThat(iae)
          .hasMessageThat()
          .isEqualTo("Use plain strings instead SanitizedContent with kind of TEXT");
    }
  }

  @Test
  public void testOrdainAsSafeWithDir() {
    SanitizedContent html =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.HTML, Dir.LTR);
    assertThat(html.getContent()).isEqualTo("Hello World");
    assertThat(html.getContentDirection()).isEqualTo(Dir.LTR);

    html = UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.HTML, Dir.RTL);
    assertThat(html.getContent()).isEqualTo("Hello World");
    assertThat(html.getContentDirection()).isEqualTo(Dir.RTL);

    html =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.HTML, Dir.NEUTRAL);
    assertThat(html.getContent()).isEqualTo("Hello World");
    assertThat(html.getContentDirection()).isEqualTo(Dir.NEUTRAL);

    html = UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.HTML);
    assertThat(html.getContent()).isEqualTo("Hello World");
    assertThat(html.getContentDirection()).isNull();
  }

  @Test
  public void testOrdainAttributesAsSafe() {
    SanitizedContent attributes =
        UnsafeSanitizedContentOrdainer.ordainAsSafeAttributes(
            ImmutableMap.of(
                "a", SanitizedContent.AttributeValue.createFromEscapedValue("b"),
                "c", SanitizedContent.AttributeValue.createFromEscapedValue("d")));
    assertThat(attributes.getContentKind()).isEqualTo(ContentKind.ATTRIBUTES);
    assertThat(attributes.getContent()).isEqualTo("a=\"b\" c=\"d\"");

    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafeAttributes(ImmutableMap.of()).getContent())
        .isEqualTo("");

    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafeAttributes(
                    ImmutableMap.of(
                        "a",
                        SanitizedContent.AttributeValue.none(),
                        "b",
                        SanitizedContent.AttributeValue.createFromEscapedValue("")))
                .getContent())
        .isEqualTo("a b=\"\"");
  }

  @Test
  public void testOrdainAttributesAsSafe_errors() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            UnsafeSanitizedContentOrdainer.ordainAsSafeAttributes(
                ImmutableMap.of("A", SanitizedContent.AttributeValue.createFromEscapedValue("b"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            UnsafeSanitizedContentOrdainer.ordainAsSafeAttributes(
                ImmutableMap.of("", SanitizedContent.AttributeValue.createFromEscapedValue("b"))));

    assertThrows(
        IllegalArgumentException.class,
        () -> SanitizedContent.AttributeValue.createFromEscapedValue("\""));
  }
}
