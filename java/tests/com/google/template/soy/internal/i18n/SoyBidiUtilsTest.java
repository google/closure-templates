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
 * Unit tests for SoyBidiUtils.
 *
 */
public class SoyBidiUtilsTest extends TestCase {


  public void testGetBidiGlobalDir() {
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir(null));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("en"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("fr"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("ru"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("ja"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("zh-CN"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("fil"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("az"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("iw-Latn"));
    assertEquals(1, SoyBidiUtils.getBidiGlobalDir("zz-ZZ"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("qbi"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("en-US-psrtl"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("en-x-psrtl"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("iw"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("iw-IL"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("he"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("ar"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("ar-EG"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("fa"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("ur"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("az-Arab"));
    assertEquals(-1, SoyBidiUtils.getBidiGlobalDir("az-Arab-IR"));
  }


  public void testGetBidiFormatter() {
    assertEquals(1, SoyBidiUtils.getBidiFormatter(1).getContextDir().ord);
    assertEquals(-1, SoyBidiUtils.getBidiFormatter(-1).getContextDir().ord);
    assertEquals(0, SoyBidiUtils.getBidiFormatter(0).getContextDir().ord);
    assertTrue(SoyBidiUtils.getBidiFormatter(1) == SoyBidiUtils.getBidiFormatter(100));
  }


  public void testDecodeBidiGlobalDirFromOptions() {
    assertNull(SoyBidiUtils.decodeBidiGlobalDirFromOptions(0, false));

    BidiGlobalDir bidiGlobalDir;

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromOptions(1, false);
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(bidiGlobalDir.getStaticValue(), 1);

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromOptions(-1, false);
    assertTrue(bidiGlobalDir.isStaticValue());
    assertEquals(bidiGlobalDir.getStaticValue(), -1);

    bidiGlobalDir = SoyBidiUtils.decodeBidiGlobalDirFromOptions(0, true);
    assertFalse(bidiGlobalDir.isStaticValue());
    assertEquals(bidiGlobalDir.getCodeSnippet(), "goog.i18n.bidi.IS_RTL?-1:1");
  }
}
