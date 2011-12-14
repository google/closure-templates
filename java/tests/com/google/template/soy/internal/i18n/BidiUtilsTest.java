/*
 * Copyright 2007 Google Inc.
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
 * Test cases for BidiUtils
 */
public class BidiUtilsTest extends TestCase {
  public void testLanguageDir() {
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("he"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("iw"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("ar"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("fa"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("ar-EG"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("az-Arab"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("az-Arab-IR"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("es"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("zh-CN"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("fil"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("az"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("iw-Latn"));
  }

  public void testDirTypeIterator_charOps() {
    BidiUtils.DirTypeIterator dti = new BidiUtils.DirTypeIterator("my \uD800\uDF80!", false);
    assertTrue(dti.atStart());
    assertFalse(dti.atEnd());
    assertEquals('m', dti.charForward());
    assertEquals('m', dti.getLastChar());
    assertEquals('y', dti.charForward());
    assertEquals('y', dti.getLastChar());
    assertEquals(' ', dti.charForward());
    assertEquals('\uD800', dti.charForward());
    assertEquals('\uD800', dti.getLastChar());
    assertEquals('!', dti.charForward());
    assertTrue(dti.atEnd());

    dti.rewind(false);
    assertEquals('m', dti.charForward());
    assertFalse(dti.matchForward("yX", true));
    assertTrue(dti.matchForward("y ", true));
    assertTrue(dti.matchForward("\uD800\uDF80", false));
    assertTrue(dti.matchForward("\uD800\uDF80", true));

    dti.rewind(true);
    assertTrue(dti.atEnd());
    assertFalse(dti.atStart());
    assertEquals('!', dti.charBackward());
    assertEquals('!', dti.getLastChar());
    assertEquals('\uDF80', dti.charBackward());
    assertEquals('\uDF80', dti.getLastChar());
    assertEquals(' ', dti.charBackward());
    assertEquals('y', dti.charBackward());
    assertEquals('m', dti.charBackward());
    assertTrue(dti.atStart());
  }

  public void testDirTypeIterator_dirTypeOps() {
    BidiUtils.DirTypeIterator dti = new BidiUtils.DirTypeIterator(
        "my my \uD835\uDFCE\uD840\uDC00!", false);
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeForward());
    assertEquals('m', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeForward());
    assertEquals('y', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeForward());
    assertEquals(' ', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_EUROPEAN_NUMBER, dti.dirTypeForward());
    assertEquals('\uD835', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeForward());
    assertEquals('\uD840', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeForward());
    assertEquals('!', dti.getLastChar());
    assertTrue(dti.atEnd());

    dti.rewind(true);
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeBackward());
    assertEquals('!', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeBackward());
    assertEquals('\uDC00', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_EUROPEAN_NUMBER, dti.dirTypeBackward());
    assertEquals('\uDFCE', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeBackward());
    assertEquals(' ', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeBackward());
    assertEquals('y', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeBackward());
    assertEquals('m', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeBackward());
    assertEquals(' ', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeBackward());
    assertEquals('y', dti.getLastChar());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeBackward());
    assertEquals('m', dti.getLastChar());
    assertTrue(dti.atStart());
  }

  public void testDirTypeIterator_dirTypeOpsHtml() {
    BidiUtils.DirTypeIterator dti = new BidiUtils.DirTypeIterator(
        "<span>my&nbsp;\uD835\uDFCE!</span>;>", true);
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_EUROPEAN_NUMBER, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeForward());
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeForward());
    assertTrue(dti.atEnd());

    dti.rewind(true);
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_OTHER_NEUTRALS, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_EUROPEAN_NUMBER, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_LEFT_TO_RIGHT, dti.dirTypeBackward());
    assertEquals(Character.DIRECTIONALITY_WHITESPACE, dti.dirTypeBackward());
    assertTrue(dti.atStart());
  }

  public void testHasAnyLtr() {
    assertFalse(BidiUtils.hasAnyLtr(""));
    assertFalse(BidiUtils.hasAnyLtr("123\t... \n"));
    assertFalse(BidiUtils.hasAnyLtr("\u05e0\u05e1\u05e2"));
    assertTrue(BidiUtils.hasAnyLtr("\u05e0z\u05e1\u05e2"));

    // LRE/RLE/LRO/RLO/PDF are ignored.
    assertFalse(BidiUtils.hasAnyLtr("\u202A\u202C"));
    assertFalse(BidiUtils.hasAnyLtr("\u202A\u05e0\u202C"));
    assertFalse(BidiUtils.hasAnyLtr("\u202A\u202B\u05e0\u202C\u202C"));
    assertFalse(BidiUtils.hasAnyLtr("\u202D\u202C"));
    assertFalse(BidiUtils.hasAnyLtr("\u202D\u05e0\u202C"));
    assertFalse(BidiUtils.hasAnyLtr("\u202D\u202B\u05e0\u202C\u202C"));
    assertTrue(BidiUtils.hasAnyLtr("\u202Bx\u202C"));
    assertTrue(BidiUtils.hasAnyLtr("\u202B\u202Ax\u202C\u202C"));
    assertTrue(BidiUtils.hasAnyLtr("\u202Ex\u202C"));
    assertTrue(BidiUtils.hasAnyLtr("\u202E\u202Ax\u202C\u202C"));

    assertTrue(BidiUtils.hasAnyLtr("\u202B\u05e0\u202Cx"));
    assertTrue(BidiUtils.hasAnyLtr("\u202B\u202B\u05e0\u202C\u202Cx"));
    assertTrue(BidiUtils.hasAnyLtr("\u202E\u05e0\u202Cx"));
    assertTrue(BidiUtils.hasAnyLtr("\u202E\u202E\u05e0\u202C\u202Cx"));

    assertTrue(BidiUtils.hasAnyLtr("<nasty title='a'>\u05e0", false));
    assertFalse(BidiUtils.hasAnyLtr("<nasty title='a'>\u05e0", true));
  }

  public void testHasAnyRtl() {
    assertFalse(BidiUtils.hasAnyRtl(""));
    assertFalse(BidiUtils.hasAnyRtl("123\t... \n"));
    assertFalse(BidiUtils.hasAnyRtl("abc"));
    assertTrue(BidiUtils.hasAnyRtl("ab\u05e0c"));

    // LRE/RLE/LRO/RLO/PDF are ignored.
    assertFalse(BidiUtils.hasAnyRtl("\u202B\u202C"));
    assertFalse(BidiUtils.hasAnyRtl("\u202Bx\u202C"));
    assertFalse(BidiUtils.hasAnyRtl("\u202B\u202Ax\u202C\u202C"));
    assertFalse(BidiUtils.hasAnyRtl("\u202E\u202C"));
    assertFalse(BidiUtils.hasAnyRtl("\u202Ex\u202C"));
    assertFalse(BidiUtils.hasAnyRtl("\u202E\u202Ax\u202C\u202C"));
    assertTrue(BidiUtils.hasAnyRtl("\u202A\u05e0\u202C"));
    assertTrue(BidiUtils.hasAnyRtl("\u202A\u202B\u05e0\u202C\u202C"));
    assertTrue(BidiUtils.hasAnyRtl("\u202D\u05e0\u202C"));
    assertTrue(BidiUtils.hasAnyRtl("\u202D\u202B\u05e0\u202C\u202C"));

    assertTrue(BidiUtils.hasAnyRtl("\u202Ax\u202C\u05e0"));
    assertTrue(BidiUtils.hasAnyRtl("\u202A\u202Ax\u202C\u202C\u05e0"));
    assertTrue(BidiUtils.hasAnyRtl("\u202Dx\u202C\u05e0"));
    assertTrue(BidiUtils.hasAnyRtl("\u202D\u202Dx\u202C\u202C\u05e0"));

    assertTrue(BidiUtils.hasAnyRtl("<nasty title='\u05e0'>a", false));
    assertFalse(BidiUtils.hasAnyRtl("<nasty title='\u05e0'>a", true));
  }

  public void testStartsWithLtr() {
    assertTrue(BidiUtils.startsWithLtr("\t   a"));
    assertFalse(BidiUtils.startsWithLtr("\t   "));
    assertFalse(BidiUtils.startsWithLtr("\t    \u05d0"));

    assertTrue(BidiUtils.startsWithLtr("\u202A\u202C\u05d0"));
    assertTrue(BidiUtils.startsWithLtr("\u202A\u202B\u202C\u202C\u05d0"));
    assertTrue(BidiUtils.startsWithLtr("\u202D\u202C\u05d0"));
    assertTrue(BidiUtils.startsWithLtr("\u202D\u202B\u202C\u202C\u05d0"));
    assertFalse(BidiUtils.startsWithLtr("\u202Bx\u202Cx"));
    assertFalse(BidiUtils.startsWithLtr("\u202B\u202Ax\u202C\u202Cx"));
    assertFalse(BidiUtils.startsWithLtr("\u202Ex\u202Cx"));
    assertFalse(BidiUtils.startsWithLtr("\u202E\u202Ax\u202C\u202Cx"));

    assertTrue(BidiUtils.startsWithLtr("<nasty tag>\u05e0", false));
    assertFalse(BidiUtils.startsWithLtr("<nasty tag>\u05e0", true));
  }

  public void testStartsWithRtl() {
    assertTrue(BidiUtils.startsWithRtl("\t   \u05d0"));
    assertFalse(BidiUtils.startsWithRtl("\t   "));
    assertFalse(BidiUtils.startsWithRtl("\t    a"));

    assertTrue(BidiUtils.startsWithRtl("\u202B\u202Cx"));
    assertTrue(BidiUtils.startsWithRtl("\u202B\u202A\u202C\u202Cx"));
    assertTrue(BidiUtils.startsWithRtl("\u202E\u202Cx"));
    assertTrue(BidiUtils.startsWithRtl("\u202E\u202A\u202C\u202Cx"));
    assertFalse(BidiUtils.startsWithRtl("\u202A\u05d0\u202C\u05d0"));
    assertFalse(BidiUtils.startsWithRtl("\u202A\u202B\u05d0\u202C\u202C\u05d0"));
    assertFalse(BidiUtils.startsWithRtl("\u202D\u05d0\u202C\u05d0"));
    assertFalse(BidiUtils.startsWithRtl("\u202D\u202B\u05d0\u202C\u202C\u05d0"));

    assertFalse(BidiUtils.startsWithRtl("<nasty tag>\u05e0", false));
    assertTrue(BidiUtils.startsWithRtl("<nasty tag>\u05e0", true));
  }

  public void testEndsWithLtr() {
    assertTrue(BidiUtils.endsWithLtr("a"));
    assertTrue(BidiUtils.endsWithLtr("abc"));
    assertTrue(BidiUtils.endsWithLtr("a (!)"));
    assertTrue(BidiUtils.endsWithLtr("a.1"));
    assertTrue(BidiUtils.endsWithLtr("http://www.google.com "));
    assertTrue(BidiUtils.endsWithLtr("\u05e0 \u05e0 \u05e0a"));
    assertTrue(BidiUtils.endsWithLtr(" \u05e0 \u05e0\u05e1a \u05e2 a !"));
    assertFalse(BidiUtils.endsWithLtr(""));
    assertFalse(BidiUtils.endsWithLtr(" "));
    assertFalse(BidiUtils.endsWithLtr("1"));
    assertFalse(BidiUtils.endsWithLtr("\u05e0"));
    assertFalse(BidiUtils.endsWithLtr("\u05e0 1(!)"));
    assertFalse(BidiUtils.endsWithLtr("a a a \u05e0"));
    assertFalse(BidiUtils.endsWithLtr("a a abc\u05e0\u05e1def\u05e2. 1"));

    assertTrue(BidiUtils.endsWithLtr("\u05d0\u202A\u202C"));
    assertTrue(BidiUtils.endsWithLtr("\u05d0\u202A\u05d0\u202C"));
    assertTrue(BidiUtils.endsWithLtr("\u05d0\u202A\u202B\u05d0\u202C\u202C"));
    assertTrue(BidiUtils.endsWithLtr("\u05d0\u202D\u202C"));
    assertTrue(BidiUtils.endsWithLtr("\u05d0\u202D\u05d0\u202C"));
    assertTrue(BidiUtils.endsWithLtr("\u05d0\u202D\u202B\u05d0\u202C\u202C"));
    assertFalse(BidiUtils.endsWithLtr("\u202Bx\u202C"));
    assertFalse(BidiUtils.endsWithLtr("x\u202Bx\u202C"));
    assertFalse(BidiUtils.endsWithLtr("x\u202B\u202Ax\u202C\u202C"));
    assertFalse(BidiUtils.endsWithLtr("\u202Ex\u202C"));
    assertFalse(BidiUtils.endsWithLtr("x\u202Ex\u202C"));
    assertFalse(BidiUtils.endsWithLtr("x\u202E\u202Ax\u202C\u202C"));

    assertTrue(BidiUtils.endsWithLtr("a a abc\u05e0<nasty tag>", false));
    assertFalse(BidiUtils.endsWithLtr("a a abc\u05e0<nasty tag>", true));
  }

  public void testEndsWithRtl() {
    assertTrue(BidiUtils.endsWithRtl("\u05e0"));
    assertTrue(BidiUtils.endsWithRtl("\u05e0\u05e1\u05e2"));
    assertTrue(BidiUtils.endsWithRtl("\u05e0 (!)"));
    assertTrue(BidiUtils.endsWithRtl("\u05e0.1"));
    assertTrue(BidiUtils.endsWithRtl("http://www.google.com/\u05e0 "));
    assertTrue(BidiUtils.endsWithRtl("a a a a\u05e0"));
    assertTrue(BidiUtils.endsWithRtl(" a a a abc\u05e0def\u05e3. 1"));
    assertFalse(BidiUtils.endsWithRtl(""));
    assertFalse(BidiUtils.endsWithRtl(" "));
    assertFalse(BidiUtils.endsWithRtl("1"));
    assertFalse(BidiUtils.endsWithRtl("a"));
    assertFalse(BidiUtils.endsWithRtl("a 1(!)"));
    assertFalse(BidiUtils.endsWithRtl("\u05e0 \u05e0 \u05e0a"));
    assertFalse(BidiUtils.endsWithRtl("\u05e0 \u05e0\u05e1ab\u05e2 a (!)"));

    assertTrue(BidiUtils.endsWithRtl("x\u202B\u202C"));
    assertTrue(BidiUtils.endsWithRtl("x\u202Bx\u202C"));
    assertTrue(BidiUtils.endsWithRtl("x\u202B\u202Ax\u202C\u202C"));
    assertTrue(BidiUtils.endsWithRtl("x\u202E\u202C"));
    assertTrue(BidiUtils.endsWithRtl("x\u202Ex\u202C"));
    assertTrue(BidiUtils.endsWithRtl("x\u202E\u202Ax\u202C\u202C"));
    assertFalse(BidiUtils.endsWithRtl("\u202A\u05d0\u202C"));
    assertFalse(BidiUtils.endsWithRtl("\u05d0\u202A\u05d0\u202C"));
    assertFalse(BidiUtils.endsWithRtl("\u05d0\u202A\u202B\u05d0\u202C\u202C"));
    assertFalse(BidiUtils.endsWithRtl("\u202D\u05d0\u202C"));
    assertFalse(BidiUtils.endsWithRtl("\u05d0\u202D\u05d0\u202C"));
    assertFalse(BidiUtils.endsWithRtl("\u05d0\u202D\u202B\u05d0\u202C\u202C"));

    assertFalse(BidiUtils.endsWithRtl("a a abc\u05e0<nasty tag>", false));
    assertTrue(BidiUtils.endsWithRtl("a a abc\u05e0<nasty tag>", true));
  }

  public void testEstimateDirection() {
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.estimateDirection("", false));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.estimateDirection(" ", false));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.estimateDirection("! (...)", false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection("Pure Ascii content", false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection("-17.0%", false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection("http://foo/bar/", false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection(
        "http://foo/bar/?s=\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0"
        + "\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0\u05d0",
        false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection("\u202d\u05d0\u05d0\u202c", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection("\u05d0", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "9 \u05d0 -> 17.5, 23, 45, 19", false));
    // We want to consider URLs "weakly LTR" like numbers, so they do not affect the estimation
    // if there are any strong directional words around. This should work regardless of the number
    // of spaces preceding the URL (which is a concern in the implementation.)
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "http://foo/bar/ \u05d0 http://foo2/bar2/  http://foo3/bar3/   http://foo4/bar4/", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection("\u202efoo\u202c", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "\u05d0\u05d9\u05df \u05de\u05de\u05e9 "
        + "\u05de\u05d4 \u05dc\u05e8\u05d0\u05d5\u05ea: "
        + "\u05dc\u05d0 \u05e6\u05d9\u05dc\u05de\u05ea\u05d9 "
        + "\u05d4\u05e8\u05d1\u05d4 \u05d5\u05d2\u05dd \u05d0"
        + "\u05dd \u05d4\u05d9\u05d9\u05ea\u05d9 \u05de\u05e6\u05dc"
        + "\u05dd, \u05d4\u05d9\u05d4 \u05e9\u05dd", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "\u05db\u05d0\u05df - http://geek.co.il/gallery/v/2007-06"
        + " - \u05d0\u05d9\u05df \u05de\u05de\u05e9 \u05de\u05d4 "
        + "\u05dc\u05e8\u05d0\u05d5\u05ea: \u05dc\u05d0 \u05e6"
        + "\u05d9\u05dc\u05de\u05ea\u05d9 \u05d4\u05e8\u05d1\u05d4 "
        + "\u05d5\u05d2\u05dd \u05d0\u05dd \u05d4\u05d9\u05d9\u05ea"
        + "\u05d9 \u05de\u05e6\u05dc\u05dd, \u05d4\u05d9\u05d4 "
        + "\u05e9\u05dd \u05d1\u05e2\u05d9\u05e7\u05e8 \u05d4\u05e8"
        + "\u05d1\u05d4 \u05d0\u05e0\u05e9\u05d9\u05dd. \u05de"
        + "\u05d4 \u05e9\u05db\u05df - \u05d0\u05e4\u05e9\u05e8 "
        + "\u05dc\u05e0\u05e6\u05dc \u05d0\u05ea \u05d4\u05d4 "
        + "\u05d3\u05d6\u05de\u05e0\u05d5\u05ea \u05dc\u05d4\u05e1"
        + "\u05ea\u05db\u05dc \u05e2\u05dc \u05db\u05de\u05d4 "
        + "\u05ea\u05de\u05d5\u05e0\u05d5\u05ea \u05de\u05e9\u05e2"
        + "\u05e9\u05e2\u05d5\u05ea \u05d9\u05e9\u05e0\u05d5\u05ea "
        + "\u05d9\u05d5\u05ea\u05e8 \u05e9\u05d9\u05e9 \u05dc"
        + "\u05d9 \u05d1\u05d0\u05ea\u05e8", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "CAPTCHA \u05de\u05e9\u05d5\u05db\u05dc\u05dc \u05de\u05d3\u05d9?", false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection(
        "CAPTCHA blah \u05de\u05d3\u05d9?", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "\u05d0\u05d0 \u202d\u05d0\u05d0\u202c", false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection(
        "\u05d0\u05d0 \u202d\u05d0\u05d0\u202c \u202d\u05d0\u05d0\u202c", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "hello \u202efoo\u202c \u202ebar\u202c", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "Yes Prime Minister \u05e2\u05d3\u05db\u05d5\u05df. "
        + "\u05e9\u05d0\u05dc\u05d5 \u05d0\u05d5\u05ea\u05d9 "
        + "\u05de\u05d4 \u05d0\u05e0\u05d9 \u05e8\u05d5\u05e6"
        + "\u05d4 \u05de\u05ea\u05e0\u05d4 \u05dc\u05d7\u05d2",
        false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "17.4.02 \u05e9\u05e2\u05d4:13-20 .15-00 .\u05dc\u05d0 "
        + "\u05d4\u05d9\u05d9\u05ea\u05d9 \u05db\u05d0\u05df.",
        false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "5710 5720 5730. \u05d4\u05d3\u05dc\u05ea. "
        + "\u05d4\u05e0\u05e9\u05d9\u05e7\u05d4", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "\u05d4\u05d3\u05dc\u05ea http://www.google.com "
        + "http://www.gmail.com", false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection(
        "\u05d4\u05d3\u05dc\u05ea <some quite nasty html mark up>", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "\u05d4\u05d3\u05dc\u05ea <some quite nasty html mark up>", true));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection(
        "\u05d4\u05d3\u05dc\u05ea &amp; &lt; &gt;", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "\u05d4\u05d3\u05dc\u05ea &amp; &lt; &gt;", true));
  }
}
