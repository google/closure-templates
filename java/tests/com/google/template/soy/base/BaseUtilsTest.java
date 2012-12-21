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

package com.google.template.soy.base;

import junit.framework.TestCase;


/**
 * Unit tests for BaseUtils.
 *
 * @author Kai Huang
 */
public class BaseUtilsTest extends TestCase {


  public void testIsIdentifier() {

    assertTrue(BaseUtils.isIdentifier("boo"));
    assertTrue(BaseUtils.isIdentifier("boo8Foo"));
    assertTrue(BaseUtils.isIdentifier("_8"));
    assertTrue(BaseUtils.isIdentifier("BOO_FOO"));
    assertTrue(BaseUtils.isIdentifier("boo_foo"));
    assertTrue(BaseUtils.isIdentifier("BooFoo_"));
    assertFalse(BaseUtils.isIdentifier(""));
    assertFalse(BaseUtils.isIdentifier("boo."));
    assertFalse(BaseUtils.isIdentifier(".boo"));
    assertFalse(BaseUtils.isIdentifier("boo.foo"));
    assertFalse(BaseUtils.isIdentifier("boo-foo"));
    assertFalse(BaseUtils.isIdentifier("8boo"));
  }


  public void testIsIdentifierWithLeadingDot() {

    assertTrue(BaseUtils.isIdentifierWithLeadingDot(".boo"));
    assertTrue(BaseUtils.isIdentifierWithLeadingDot("._8"));
    assertTrue(BaseUtils.isIdentifierWithLeadingDot(".BOO_FOO"));
    assertFalse(BaseUtils.isIdentifierWithLeadingDot(""));
    assertFalse(BaseUtils.isIdentifierWithLeadingDot("boo."));
    assertFalse(BaseUtils.isIdentifierWithLeadingDot("boo"));
    assertFalse(BaseUtils.isIdentifierWithLeadingDot("boo.foo"));
    assertFalse(BaseUtils.isIdentifierWithLeadingDot(".boo-foo"));
    assertFalse(BaseUtils.isIdentifierWithLeadingDot(".8boo"));
  }


  public void testIsDottedIdentifier() {

    assertTrue(BaseUtils.isDottedIdentifier("boo"));
    assertTrue(BaseUtils.isDottedIdentifier("boo.foo8.goo"));
    assertTrue(BaseUtils.isDottedIdentifier("Boo.FooGoo"));
    assertTrue(BaseUtils.isDottedIdentifier("___I_._I._I_.__"));
    assertFalse(BaseUtils.isDottedIdentifier(".boo.fooGoo"));
    assertFalse(BaseUtils.isDottedIdentifier("boo."));
    assertFalse(BaseUtils.isDottedIdentifier("boo.8"));
    assertFalse(BaseUtils.isDottedIdentifier("boo-foo"));
    assertFalse(BaseUtils.isDottedIdentifier("_...I._I_.."));
  }


  public void testConvertToUpperUnderscore() {

    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("booFoo"));
    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("_booFoo"));
    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("booFoo_"));
    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("BooFoo"));
    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("boo_foo"));
    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("BOO_FOO"));
    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("__BOO__FOO__"));
    assertEquals("BOO_FOO", BaseUtils.convertToUpperUnderscore("Boo_Foo"));
    assertEquals("BOO_8_FOO", BaseUtils.convertToUpperUnderscore("boo8Foo"));
    assertEquals("BOO_FOO_88", BaseUtils.convertToUpperUnderscore("booFoo88"));
    assertEquals("BOO_88_FOO", BaseUtils.convertToUpperUnderscore("boo88_foo"));
    assertEquals("BOO_8_FOO", BaseUtils.convertToUpperUnderscore("_boo_8foo"));
    assertEquals("BOO_FOO_8", BaseUtils.convertToUpperUnderscore("boo_foo8"));
    assertEquals("BOO_8_FOO", BaseUtils.convertToUpperUnderscore("_BOO__8_FOO_"));
  }


}
