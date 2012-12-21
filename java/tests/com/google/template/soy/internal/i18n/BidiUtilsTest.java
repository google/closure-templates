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

import com.ibm.icu.lang.UCharacter;
import junit.framework.TestCase;

/**
 * Test cases for BidiUtils
 */
public class BidiUtilsTest extends TestCase {
  private static final String LRE = "\u202A";
  private static final String RLE = "\u202B";
  private static final String PDF = "\u202C";
  private static final String LRO = "\u202D";
  private static final String RLO = "\u202E";
  private static final String HE = "\u05D0";

  public void testLanguageDir() {
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("he"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("iw"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("ar"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("fa"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("FA"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("ar-EG"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("Ar-eg"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("az-Arab"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("az-Arab-IR"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("az-ARAB-IR"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.languageDir("az_arab_IR"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("es"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("zh-CN"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("fil"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("az"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("iw-Latn"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("iw-LATN"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.languageDir("iw-latn"));
  }

  public void testDirectionalityEstimator_dirTypeOps() {
    BidiUtils.DirectionalityEstimator de = new BidiUtils.DirectionalityEstimator(
        "my my \uD835\uDFCE\uD840\uDC00!", false);
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_EUROPEAN_NUMBER, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeForward());
    try {
      de.dirTypeForward();  // Should throw.
      assertTrue(false);
    } catch (IndexOutOfBoundsException e) {
    }

    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_EUROPEAN_NUMBER, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeBackward());
    try {
      de.dirTypeBackward();  // Should throw.
      assertTrue(false);
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testDirectionalityEstimator_dirTypeOpsHtml() {
    BidiUtils.DirectionalityEstimator de = new BidiUtils.DirectionalityEstimator(
        "<span x='>" + HE + "'>my&nbsp;\uD835\uDFCE!</span>;>", true);
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_EUROPEAN_NUMBER, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeForward());
    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeForward());
    try {
      de.dirTypeForward();  // Should throw.
      assertTrue(false);
    } catch (IndexOutOfBoundsException e) {
    }

    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_OTHER_NEUTRALS, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_EUROPEAN_NUMBER, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT, de.dirTypeBackward());
    assertEquals(UCharacter.DIRECTIONALITY_WHITESPACE, de.dirTypeBackward());
    try {
      de.dirTypeBackward();  // Should throw.
      assertTrue(false);
    } catch (IndexOutOfBoundsException e) {
    }
  }

  public void testHasAnyLtr() {
    assertFalse(BidiUtils.hasAnyLtr(""));
    assertFalse(BidiUtils.hasAnyLtr("123\t... \n"));
    assertFalse(BidiUtils.hasAnyLtr(HE + HE + HE));
    assertTrue(BidiUtils.hasAnyLtr(HE + "z" + HE + HE));

    // LRE/RLE/LRO/RLO/PDF are ignored.
    assertFalse(BidiUtils.hasAnyLtr(LRE + PDF));
    assertFalse(BidiUtils.hasAnyLtr(LRE + HE + PDF));
    assertFalse(BidiUtils.hasAnyLtr(LRE + RLE + HE + PDF + PDF));
    assertFalse(BidiUtils.hasAnyLtr(LRO + PDF));
    assertFalse(BidiUtils.hasAnyLtr(LRO + HE + PDF));
    assertFalse(BidiUtils.hasAnyLtr(LRO + RLE + HE + PDF + PDF));
    assertTrue(BidiUtils.hasAnyLtr(RLE + "x" + PDF));
    assertTrue(BidiUtils.hasAnyLtr(RLE + LRE + "x" + PDF + PDF));
    assertTrue(BidiUtils.hasAnyLtr(RLO + "x" + PDF));
    assertTrue(BidiUtils.hasAnyLtr(RLO + LRE + "x" + PDF + PDF));

    assertTrue(BidiUtils.hasAnyLtr(RLE + HE + PDF + "x"));
    assertTrue(BidiUtils.hasAnyLtr(RLE + RLE + HE + PDF + PDF + "x"));
    assertTrue(BidiUtils.hasAnyLtr(RLO + HE + PDF + "x"));
    assertTrue(BidiUtils.hasAnyLtr(RLO + RLO + HE + PDF + PDF + "x"));

    assertTrue(BidiUtils.hasAnyLtr("<nasty title='a'>" + HE, false));
    assertFalse(BidiUtils.hasAnyLtr("<nasty title='a'>" + HE, true));
  }

  public void testHasAnyRtl() {
    assertFalse(BidiUtils.hasAnyRtl(""));
    assertFalse(BidiUtils.hasAnyRtl("123\t... \n"));
    assertFalse(BidiUtils.hasAnyRtl("abc"));
    assertTrue(BidiUtils.hasAnyRtl("ab" + HE + "c"));

    // LRE/RLE/LRO/RLO/PDF are ignored.
    assertFalse(BidiUtils.hasAnyRtl(RLE + PDF));
    assertFalse(BidiUtils.hasAnyRtl(RLE + "x" + PDF));
    assertFalse(BidiUtils.hasAnyRtl(RLE + LRE + "x" + PDF + PDF));
    assertFalse(BidiUtils.hasAnyRtl(RLO + PDF));
    assertFalse(BidiUtils.hasAnyRtl(RLO + "x" + PDF));
    assertFalse(BidiUtils.hasAnyRtl(RLO + LRE + "x" + PDF + PDF));
    assertTrue(BidiUtils.hasAnyRtl(LRE + HE + PDF));
    assertTrue(BidiUtils.hasAnyRtl(LRE + RLE + HE + PDF + PDF));
    assertTrue(BidiUtils.hasAnyRtl(LRO + HE + PDF));
    assertTrue(BidiUtils.hasAnyRtl(LRO + RLE + HE + PDF + PDF));

    assertTrue(BidiUtils.hasAnyRtl(LRE + "x" + PDF + HE));
    assertTrue(BidiUtils.hasAnyRtl(LRE + LRE + "x" + PDF + PDF + HE));
    assertTrue(BidiUtils.hasAnyRtl(LRO + "x" + PDF + HE));
    assertTrue(BidiUtils.hasAnyRtl(LRO + LRO + "x" + PDF + PDF + HE));

    assertTrue(BidiUtils.hasAnyRtl("<nasty title='" + HE + "'>a", false));
    assertFalse(BidiUtils.hasAnyRtl("<nasty title='" + HE + "'>a", true));
  }

  public void testGetUnicodeDir_NeutralText() {
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getUnicodeDir(""));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getUnicodeDir("\t   \r\n"));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getUnicodeDir("123"));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getUnicodeDir(" 123-()"));
  }

  public void testGetUnicodeDir_LtrFirst() {
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("\t   a"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("\t   a " + HE));
  }

  public void testGetUnicodeDir_RtlFirst() {
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("\t   " + HE));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("\t   " + HE + " a"));
  }

  public void testGetUnicodeDir_IgnoreEmbeddings() {
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir(RLE + PDF + "x"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir(LRE + HE + PDF + "x"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir(RLO + PDF + "x"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir(LRO + HE + PDF + "x"));

    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir(LRE + PDF + HE));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir(RLE + "x" + PDF + HE));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir(LRO + PDF + HE));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir(RLO + "x" + PDF + HE));
  }

  public void testGetUnicodeDirOfHtml_MarkupSkipped() {
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("<a tag>" + HE));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("<a tag>" + HE, true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("<a x=\"y>\" tag>" + HE, true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("<a x=\"<y>\" tag>" + HE, true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("<a x='<y>' tag>" + HE, true));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("<a x=\"<y>\" tag>a" + HE, true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("<a x=\"<y>\" tag><b>" + HE, true));

    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("<notatag", true));
  }

  public void testGetUnicodeDirOfHtml_EntitySkipped() {
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getUnicodeDir("&nbsp;", true));

    // TODO: Uncomment these lines and rename test to ...Parsed() when we start to map
    // entities to the characters for which they stand.
    // assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("&nbsp;&rlm;", true));
    // assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("&nbsp;a&rlm;", true));
    // assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("&rlm;"));
    // assertEquals(BidiUtils.Dir.RTL, BidiUtils.getUnicodeDir("&rlm;", true));
    // assertEquals(BidiUtils.Dir.LTR, BidiUtils.getUnicodeDir("&nosuchentity;", true));
  }

  public void testGetExitDir_NeutralText() {
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getExitDir(""));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getExitDir("\t   \r\n"));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getExitDir("123"));
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getExitDir(" 123-()"));
  }

  public void testGetExitDir_LtrLast() {
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("a"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("a   \t"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("abc   \t"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + " a   \t"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + "a   \t"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + HE + " " + HE + " a   \t"));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + " http://www.google.com"));
  }

  public void testGetExitDir_RtlLast() {
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + "   \t"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + HE + HE + "   \t"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a " + HE + "   \t"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + HE + "   \t"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("abc a " + HE + "   \t"));
  }

  public void testGetExitDir_EmptyEmbeddingIgnored() {
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("a" + RLE + PDF));
    // U+200B is the zero-width space, a BN-class character, ignored by the UBA.
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("a" + RLE + "\u200B" + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("a" + RLO + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("a" + RLO + "\u200B" + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + LRE + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + LRE + "\u200B" + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + LRO + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + LRO + "\u200B" + PDF));
  }

  public void testGetExitDir_NonEmptyLtrEmbeddingLast() {
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + LRE + "." + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + LRE + HE + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + LRE + HE + PDF + RLE + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + LRE + HE + RLE + PDF + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + LRE + RLE + HE + PDF + HE + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + RLE + HE + LRE + HE + PDF + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + RLE + HE + LRE + HE + RLE + PDF + PDF + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + LRO + "." + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + LRO + HE + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + LRO + HE + PDF + RLE + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + LRO + HE + RLE + PDF + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + LRO + RLE + HE + PDF + HE + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + RLE + HE + LRO + HE + PDF + PDF));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(
        HE + RLE + HE + LRO + HE + RLE + PDF + PDF + PDF));
  }

  public void testGetExitDir_NonEmptyRtlEmbeddingLast() {
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLE + "." + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLE + "a" + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLE + "a" + PDF + LRE + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLE + "a" + LRE + PDF + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLE + LRE + "a" + PDF + "a" + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + LRE + "a" + RLE + "a" + PDF + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(
        "a" + LRE + "a" + RLE + "a" + LRE + PDF + PDF + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLO + "." + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLO + "a" + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLO + "a" + PDF + LRE + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLO + "a" + LRE + PDF + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + RLO + LRE + "a" + PDF + "a" + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("a" + LRE + "a" + RLO + "a" + PDF + PDF));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(
        "a" + LRE + "a" + RLO + "a" + LRE + PDF + PDF + PDF));
  }

  public void testGetExitDirOfHtml_MarkupSkipped() {
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + "<a tag>"));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + "<a tag>", true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + "<a x=\"y>\" tag>", true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + "<a x=\"<y>\" tag>", true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + "<a x='<y>' tag>", true));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir(HE + "a<a x=\"<y>\" tag>", true));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir(HE + "<a x=\"<y>\" tag><b>", true));

    assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("<notatag", true));
  }

  public void testGetExitDirOfHtml_EntitySkipped() {
    assertEquals(BidiUtils.Dir.UNKNOWN, BidiUtils.getExitDir("&nbsp;", true));

    // TODO: Uncomment these lines and rename test to ...Parsed() when we start to map
    // entities to the characters for which they stand.
    // assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("&rlm;"));
    // assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("&rlm;", true));
    // assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("hello&rlm;!!!!", true));
    // assertEquals(BidiUtils.Dir.RTL, BidiUtils.getExitDir("&rlm;&nbsp;", true));
    // assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("&rlm;a&nbsp;", true));
    // assertEquals(BidiUtils.Dir.LTR, BidiUtils.getExitDir("&nosuchentity;", true));
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
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection(LRO + HE + HE + PDF, false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(HE, false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "9 " + HE + " -> 17.5, 23, 45, 19", false));
    // We want to consider URLs "weakly LTR" like numbers, so they do not affect the estimation
    // if there are any strong directional words around. This should work regardless of the number
    // of spaces preceding the URL (which is a concern in the implementation.)
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "http://foo/bar/ " + HE + " http://foo2/bar/  http://foo/bar3/   http://foo4/bar/", false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(RLO + "foo" + PDF, false));
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
        HE + HE + " " + LRO + HE + HE + PDF, false));
    assertEquals(BidiUtils.Dir.LTR, BidiUtils.estimateDirection(
        HE + HE + " " + LRO + HE + HE + PDF + " " + LRO + HE + HE + PDF, false));
    assertEquals(BidiUtils.Dir.RTL, BidiUtils.estimateDirection(
        "hello " + RLO + "foo" + PDF + " " + RLO + "bar" + PDF, false));
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
