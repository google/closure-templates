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

package com.google.template.soy.data.internalutils;

import static org.junit.Assert.assertEquals;

import com.google.template.soy.base.internal.SanitizedContentKind;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for NodeContentKinds.
 */
@RunWith(JUnit4.class)
public class NodeContentKindsTest {

  @Test
  public void testToJsSanitizedContentCtorName() {
    assertEquals(
        "SanitizedHtml", NodeContentKinds.toJsSanitizedContentCtorName(SanitizedContentKind.HTML));
    assertEquals(
        "SanitizedHtmlAttribute",
        NodeContentKinds.toJsSanitizedContentCtorName(SanitizedContentKind.ATTRIBUTES));
    assertEquals(
        "SanitizedCss", NodeContentKinds.toJsSanitizedContentCtorName(SanitizedContentKind.CSS));
    assertEquals(
        "SanitizedUri", NodeContentKinds.toJsSanitizedContentCtorName(SanitizedContentKind.URI));
    assertEquals(
        "SanitizedTrustedResourceUri",
        NodeContentKinds.toJsSanitizedContentCtorName(SanitizedContentKind.TRUSTED_RESOURCE_URI));
    assertEquals(
        "SanitizedJs", NodeContentKinds.toJsSanitizedContentCtorName(SanitizedContentKind.JS));
    assertEquals(
        "string", NodeContentKinds.toJsSanitizedContentCtorName(SanitizedContentKind.TEXT));
  }

  @Test
  public void testToJsSanitizedContentOrdainer() {
    assertEquals(
        "soy.VERY_UNSAFE.ordainSanitizedHtml",
        NodeContentKinds.toJsSanitizedContentOrdainer(SanitizedContentKind.HTML));
    assertEquals(
        "soy.VERY_UNSAFE.ordainSanitizedHtmlAttribute",
        NodeContentKinds.toJsSanitizedContentOrdainer(SanitizedContentKind.ATTRIBUTES));
    assertEquals(
        "soy.VERY_UNSAFE.ordainSanitizedCss",
        NodeContentKinds.toJsSanitizedContentOrdainer(SanitizedContentKind.CSS));
    assertEquals(
        "soy.VERY_UNSAFE.ordainSanitizedUri",
        NodeContentKinds.toJsSanitizedContentOrdainer(SanitizedContentKind.URI));
    assertEquals(
        "soy.VERY_UNSAFE.ordainSanitizedTrustedResourceUri",
        NodeContentKinds.toJsSanitizedContentOrdainer(SanitizedContentKind.TRUSTED_RESOURCE_URI));
    assertEquals(
        "soy.VERY_UNSAFE.ordainSanitizedJs",
        NodeContentKinds.toJsSanitizedContentOrdainer(SanitizedContentKind.JS));
    assertEquals("", NodeContentKinds.toJsSanitizedContentOrdainer(SanitizedContentKind.TEXT));
  }
}
