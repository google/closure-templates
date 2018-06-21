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

package com.google.template.soy.i18ndirectives;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link I18nUtils}.
 *
 */
@RunWith(JUnit4.class)
public class I18nUtilsTest {

  @Test
  public void testParseLocale() {
    assertEquals("en_US", I18nUtils.parseLocale(null).toString());
    assertEquals("en_US", I18nUtils.parseLocale("en-us").toString());
    assertEquals("en_UK", I18nUtils.parseLocale("en_uk").toString());
    assertEquals("sv_SE", I18nUtils.parseLocale("sv-SE").toString());
    assertEquals("de", I18nUtils.parseLocale("de").toString());
    assertEquals("no_NO_NY", I18nUtils.parseLocale("no_no_NY").toString());
    assertEquals("no_NO", I18nUtils.parseLocale("no_no").toString());
  }

  @Test
  public void testParseLocale_InvalidLocale() {
    // ParseLocale throws an error if the locale has too many parts.
    try {
      I18nUtils.parseLocale("xx-yy_zz_as");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
