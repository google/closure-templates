/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.internal.i18n;

import junit.framework.TestCase;


/**
 * Unit tests for BidiGlobalDir.
 *
 * @author Aharon Lanin
 */
public class BidiGlobalDirTest extends TestCase {


  public void testBidiGlobalDir() {

    BidiGlobalDir bidiGlobalDir;

    bidiGlobalDir = BidiGlobalDir.forStaticIsRtl(false);
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(1, bidiGlobalDir.getStaticValue());
    assertEquals("1", bidiGlobalDir.getCodeSnippet());

    bidiGlobalDir = BidiGlobalDir.forStaticIsRtl(true);
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(-1, bidiGlobalDir.getStaticValue());
    assertEquals("-1", bidiGlobalDir.getCodeSnippet());

    bidiGlobalDir = BidiGlobalDir.forStaticLocale("en");
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(1, bidiGlobalDir.getStaticValue());
    assertEquals("1", bidiGlobalDir.getCodeSnippet());

    bidiGlobalDir = BidiGlobalDir.forStaticLocale("ar");
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(-1, bidiGlobalDir.getStaticValue());
    assertEquals("-1", bidiGlobalDir.getCodeSnippet());

    bidiGlobalDir = BidiGlobalDir.forStaticLocale(null);
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(1, bidiGlobalDir.getStaticValue());
    assertEquals("1", bidiGlobalDir.getCodeSnippet());

    bidiGlobalDir = BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL");
    assertFalse(bidiGlobalDir.isStaticValue());
    assertEquals("IS_RTL?-1:1", bidiGlobalDir.getCodeSnippet());
    try {
      bidiGlobalDir.getStaticValue();
      fail();
    } catch (RuntimeException e) {
      // Test passes.
    }
  }

}
