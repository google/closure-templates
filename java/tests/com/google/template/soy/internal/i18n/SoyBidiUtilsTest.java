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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyBidiUtils.
 *
 */
@RunWith(JUnit4.class)
public class SoyBidiUtilsTest {

  @Test
  public void testGetBidiGlobalDir() {
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir(null));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("en"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("fr"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("ru"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("ja"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("zh-CN"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("fil"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("az"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("iw-Latn"));
    assertEquals(BidiGlobalDir.LTR, SoyBidiUtils.getBidiGlobalDir("zz-ZZ"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("qbi"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("en-US-psrtl"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("en-x-psrtl"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("iw"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("iw-IL"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("he"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("ar"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("ar-EG"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("fa"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("ur"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("az-Arab"));
    assertEquals(BidiGlobalDir.RTL, SoyBidiUtils.getBidiGlobalDir("az-Arab-IR"));
  }

  @Test
  public void testDecodeBidiGlobalDirFromJsOptions() {
    assertNull(SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(0, false));

    BidiGlobalDir bidiGlobalDir;

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(1, false);
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(1, bidiGlobalDir.getStaticValue());

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(-1, false);
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(-1, bidiGlobalDir.getStaticValue());

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromJsOptions(0, true);
    assertFalse(bidiGlobalDir.isStaticValue());
    assertEquals("soy.$$IS_LOCALE_RTL?-1:1", bidiGlobalDir.getCodeSnippet());
  }

  @Test
  public void testDecodeBidiGlobalDirFromPyOptions() {
    assertNull(SoyBidiUtils.decodeBidiGlobalDirFromPyOptions(null));
    assertNull(SoyBidiUtils.decodeBidiGlobalDirFromPyOptions(""));

    BidiGlobalDir bidiGlobalDir;

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromPyOptions("mod.is_rtl");
    assertFalse(bidiGlobalDir.isStaticValue());
    assertEquals("-1 if external_bidi.is_rtl() else 1", bidiGlobalDir.getCodeSnippet());

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromPyOptions("package.mod.is_rtl");
    assertFalse(bidiGlobalDir.isStaticValue());
    assertEquals("-1 if external_bidi.is_rtl() else 1", bidiGlobalDir.getCodeSnippet());
  }

  @Test
  public void testInvalidDecodeBidiGlobalDirFromPyOptions() {
    try {
      SoyBidiUtils.decodeBidiGlobalDirFromPyOptions("is_rtl");
      fail("bidiIsRtlFn without a module path did not except");
    } catch (IllegalArgumentException expected) {
    }

    try {
      SoyBidiUtils.decodeBidiGlobalDirFromPyOptions(".is_rtl");
      SoyBidiUtils.decodeBidiGlobalDirFromPyOptions("is_rtl.");
      fail("bidiIsRtlFn with invalid path did not except");
    } catch (IllegalArgumentException expected) {
    }
  }
}
