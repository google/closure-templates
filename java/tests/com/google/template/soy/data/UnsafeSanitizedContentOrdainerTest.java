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

import com.google.template.soy.data.SanitizedContent.ContentKind;

import junit.framework.TestCase;


/**
 * Unit tests for UnsafeSanitizedContentOrdainer utility class.
 *
 */
public class UnsafeSanitizedContentOrdainerTest extends TestCase {

  public void testOrdainAsSafe() {
    assertEquals(
        SanitizedContent.create("Hello World", ContentKind.TEXT, null),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT));
    assertEquals(
        SanitizedContent.create("Hello <b>World</b>", ContentKind.HTML, null),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello <b>World</b>", ContentKind.HTML));
    assertEquals(
        SanitizedContent.create("hello_world();", ContentKind.JS, Dir.LTR),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("hello_world();", ContentKind.JS));
    assertEquals(
        SanitizedContent.create("hello_world();", ContentKind.CSS, Dir.LTR),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("hello_world();", ContentKind.CSS));
    assertEquals(
        SanitizedContent.create("hello/world", ContentKind.URI, Dir.LTR),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("hello/world", ContentKind.URI));
    assertEquals(
        SanitizedContent.create("hello=world", ContentKind.ATTRIBUTES, Dir.LTR),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("hello=world", ContentKind.ATTRIBUTES));
  }

  public void testOrdainAsSafeWithDir() {
    assertEquals(
        SanitizedContent.create("Hello World", ContentKind.TEXT, Dir.LTR),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT, Dir.LTR));
    assertEquals(
        SanitizedContent.create("Hello World", ContentKind.TEXT, Dir.RTL),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT, Dir.RTL));
    assertEquals(
        SanitizedContent.create("Hello World", ContentKind.TEXT, Dir.NEUTRAL),
        UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT, Dir.NEUTRAL));
  }

}
