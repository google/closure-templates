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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.Dir;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiGlobalDir.
 *
 */
@RunWith(JUnit4.class)
public class BidiGlobalDirTest {

  @Test
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

    bidiGlobalDir = BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL", null, SoyBackendKind.JS_SRC);
    assertFalse(bidiGlobalDir.isStaticValue());
    assertEquals("IS_RTL?-1:1", bidiGlobalDir.getCodeSnippet());

    bidiGlobalDir = BidiGlobalDir.forIsRtlCodeSnippet("IS_RTL", null, SoyBackendKind.PYTHON_SRC);
    assertFalse(bidiGlobalDir.isStaticValue());
    assertEquals("-1 if IS_RTL else 1", bidiGlobalDir.getCodeSnippet());
    try {
      bidiGlobalDir.getStaticValue();
      fail();
    } catch (RuntimeException e) {
      // Test passes.
    }
  }

  @Test
  public void testToDirRTL() {
    assertEquals(Dir.RTL, BidiGlobalDir.RTL.toDir());
  }

  @Test
  public void testToDirLTR() {
    assertEquals(Dir.LTR, BidiGlobalDir.LTR.toDir());
  }

  @Test
  public void testToDirNonStatic() {
    BidiGlobalDir dir = BidiGlobalDir.forIsRtlCodeSnippet("snippet", null, SoyBackendKind.JS_SRC);
    try {
      dir.toDir();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }
}
