/*
 * Copyright 2009 Google Inc.
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

import com.google.template.soy.data.Dir;
import com.google.template.soy.internal.i18n.BidiFormatter.BidiWrappingText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for BidiFormatter. */
@RunWith(JUnit4.class)
public class BidiFormatterTest {
  private static final Dir RTL = Dir.RTL;
  private static final Dir LTR = Dir.LTR;
  private static final Dir NEUTRAL = Dir.NEUTRAL;

  private static final String EN = "abba";
  private static final String EN_TAG = " <br> ";
  private static final String HE = "\u05e0\u05e1";
  private static final String HE_TAG = " <\u05d0> ";

  private static final String LRM = "\u200E";
  private static final String RLM = "\u200F";
  private static final String LRE = "\u202A";
  private static final String RLE = "\u202B";
  private static final String PDF = "\u202C";

  private static final BidiFormatter LTR_FMT = BidiFormatter.getInstance(LTR);
  private static final BidiFormatter RTL_FMT = BidiFormatter.getInstance(RTL);

  @Test
  public void testEstimateDirection() {
    assertEquals(RTL, BidiFormatter.estimateDirection(HE, true));
    assertEquals(LTR, BidiFormatter.estimateDirection(EN, true));
    assertEquals(NEUTRAL, BidiFormatter.estimateDirection(".", true));

    // Text contains HTML or HTML-escaping.
    assertEquals(LTR, BidiFormatter.estimateDirection(HE + "<some sort of an HTML tag/>", false));
    assertEquals(RTL, BidiFormatter.estimateDirection(HE + "<some sort of an HTML tag/>", true));
    assertEquals(NEUTRAL, BidiFormatter.estimateDirection(".<some sort of an HTML tag/>", true));
  }

  @Test
  public void testKnownDirAttr() {
    assertEquals("", LTR_FMT.knownDirAttrSanitized(LTR).stringValue());
    assertEquals("dir=\"ltr\"", RTL_FMT.knownDirAttrSanitized(LTR).stringValue());
    assertEquals("dir=\"rtl\"", LTR_FMT.knownDirAttrSanitized(RTL).stringValue());
    assertEquals("", RTL_FMT.knownDirAttrSanitized(RTL).stringValue());
    assertEquals("", LTR_FMT.knownDirAttrSanitized(NEUTRAL).stringValue());
    assertEquals("", RTL_FMT.knownDirAttrSanitized(NEUTRAL).stringValue());
  }

  @Test
  public void testSpanWrap() {
    // Neutral directionality in whatever context.
    assertEquals(
        "neutral dir in LTR context", "&amp; . &lt;", LTR_FMT.spanWrap(null, "& . <", false));
    assertEquals(
        "neutral dir in LTR context, HTML",
        HE_TAG + "." + HE_TAG,
        LTR_FMT.spanWrap(null, HE_TAG + "." + HE_TAG, true));
    assertEquals(
        "neutral dir in RTL context", "&amp; . &lt;", RTL_FMT.spanWrap(null, "& . <", false));
    assertEquals(
        "neutral dir in RTL context, HTML",
        EN_TAG + "." + EN_TAG,
        RTL_FMT.spanWrap(null, EN_TAG + "." + EN_TAG, true));

    // Uniform directionality in matching context.
    assertEquals(
        "uniform dir matches LTR context",
        "&amp; " + EN + "&lt;",
        LTR_FMT.spanWrap(null, "& " + EN + "<", false));
    assertEquals(
        "uniform dir matches LTR context, HTML",
        HE_TAG + EN + HE_TAG,
        LTR_FMT.spanWrap(null, HE_TAG + EN + HE_TAG, true));
    assertEquals("neutral treated as matching LTR context", ".", LTR_FMT.spanWrap(LTR, ".", false));
    assertEquals(
        "uniform dir matches RTL context",
        "&amp; " + HE + "&lt;",
        RTL_FMT.spanWrap(null, "& " + HE + "<", false));
    assertEquals(
        "uniform dir matches RTL context, HTML",
        EN_TAG + HE + EN_TAG,
        RTL_FMT.spanWrap(null, EN_TAG + HE + EN_TAG, true));
    assertEquals("neutral treated as matching RTL context", ".", RTL_FMT.spanWrap(RTL, ".", false));

    // Uniform directionality in opposite context.
    assertEquals(
        "uniform dir opposite to LTR context",
        "<span dir=\"rtl\">." + HE + ".</span>" + LRM,
        LTR_FMT.spanWrap(null, "." + HE + ".", false));
    assertEquals(
        "uniform dir opposite to LTR context, HTML",
        "<span dir=\"rtl\">" + EN_TAG + HE + EN_TAG + "</span>" + LRM,
        LTR_FMT.spanWrap(null, EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "neutral treated as opposite to LTR context",
        "<span dir=\"rtl\">.</span>" + LRM,
        LTR_FMT.spanWrap(RTL, ".", false));
    assertEquals(
        "uniform dir opposite to RTL context",
        "<span dir=\"ltr\">." + EN + ".</span>" + RLM,
        RTL_FMT.spanWrap(null, "." + EN + ".", false));
    assertEquals(
        "uniform dir opposite to RTL context, HTML",
        "<span dir=\"ltr\">" + HE_TAG + EN + HE_TAG + "</span>" + RLM,
        RTL_FMT.spanWrap(null, HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "neutral treated as opposite to RTL context",
        "<span dir=\"ltr\">.</span>" + RLM,
        RTL_FMT.spanWrap(LTR, ".", false));

    // "Known" unknown directionality is estimated.
    assertEquals(
        "known unknown dir matching LTR context",
        "." + EN + ".",
        LTR_FMT.spanWrap(null, "." + EN + ".", false));
    assertEquals(
        "known unknown dir matching RTL context",
        "." + HE + ".",
        RTL_FMT.spanWrap(null, "." + HE + ".", false));

    // We test mixed-directionality cases only on spanWrap() because the estimation logic is outside
    // the sphere of BidiFormatter, and different estimators will treat them differently.

    // Overall directionality matching context, but with opposite exit directionality.
    assertEquals(
        "exit dir opposite to LTR context", EN + HE + LRM, LTR_FMT.spanWrap(LTR, EN + HE, false));
    assertEquals(
        "exit dir opposite to LTR context, HTML",
        EN + HE + EN_TAG + LRM,
        LTR_FMT.spanWrap(LTR, EN + HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to RTL context", HE + EN + RLM, RTL_FMT.spanWrap(RTL, HE + EN, false));
    assertEquals(
        "exit dir opposite to RTL context, HTML",
        HE + EN + HE_TAG + RLM,
        RTL_FMT.spanWrap(RTL, HE + EN + HE_TAG, true));

    // Overall directionality matching context, but with opposite entry directionality.
    assertEquals(
        "entry dir opposite to LTR context", HE + EN, LTR_FMT.spanWrap(LTR, HE + EN, false));
    assertEquals(
        "entry dir opposite to LTR context, HTML",
        EN_TAG + HE + EN,
        LTR_FMT.spanWrap(LTR, EN_TAG + HE + EN, true));
    assertEquals(
        "entry dir opposite to LTR context, HTML",
        BidiWrappingText.create("", ""),
        LTR_FMT.spanWrappingText(LTR, EN_TAG + HE + EN, true));
    assertEquals(
        "entry dir opposite to RTL context", EN + HE, RTL_FMT.spanWrap(RTL, EN + HE, false));
    assertEquals(
        "entry dir opposite to RTL context, HTML",
        HE_TAG + EN + HE,
        RTL_FMT.spanWrap(RTL, HE_TAG + EN + HE, true));

    // Overall directionality matching context, but with opposite entry and exit directionality.
    assertEquals(
        "entry and exit dir opposite to LTR context",
        HE + EN + HE + LRM,
        LTR_FMT.spanWrap(LTR, HE + EN + HE, false));
    assertEquals(
        "entry and exit dir opposite to LTR context, HTML",
        EN_TAG + HE + EN + HE + EN_TAG + LRM,
        LTR_FMT.spanWrap(LTR, EN_TAG + HE + EN + HE + EN_TAG, true));
    assertEquals(
        "entry and exit dir opposite to RTL context",
        EN + HE + EN + RLM,
        RTL_FMT.spanWrap(RTL, EN + HE + EN, false));
    assertEquals(
        "entry and exit dir opposite to RTL context, HTML",
        HE_TAG + EN + HE + EN + HE_TAG + RLM,
        RTL_FMT.spanWrap(RTL, HE_TAG + EN + HE + EN + HE_TAG, true));

    // Entry and exit directionality matching context, but with opposite overall directionality.
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context",
        "<span dir=\"rtl\">" + EN + HE + EN + "</span>" + LRM,
        LTR_FMT.spanWrap(RTL, EN + HE + EN, false));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context",
        "<span dir=\"ltr\">" + HE + EN + HE + "</span>" + RLM,
        RTL_FMT.spanWrap(LTR, HE + EN + HE, false));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context",
        BidiWrappingText.create("<span dir=\"ltr\">", "</span>" + RLM),
        RTL_FMT.spanWrappingText(LTR, HE + EN + HE, false));
  }

  @Test
  public void testUnicodeWrap() {
    // Neutral directionality in whatever context.
    assertEquals("neutral dir in LTR context", "& . <", LTR_FMT.unicodeWrap(null, "& . <", false));
    assertEquals(
        "neutral dir in LTR context, HTML",
        HE_TAG + "." + HE_TAG,
        LTR_FMT.unicodeWrap(null, HE_TAG + "." + HE_TAG, true));
    assertEquals("neutral dir in RTL context", "& . <", RTL_FMT.unicodeWrap(null, "& . <", false));
    assertEquals(
        "neutral dir in RTL context, HTML",
        EN_TAG + "." + EN_TAG,
        RTL_FMT.unicodeWrap(null, EN_TAG + "." + EN_TAG, true));

    // Uniform directionality in matching context.
    assertEquals(
        "uniform dir matches LTR context",
        "& " + EN + "<",
        LTR_FMT.unicodeWrap(null, "& " + EN + "<", false));
    assertEquals(
        "uniform dir matches LTR context, HTML",
        HE_TAG + EN + HE_TAG,
        LTR_FMT.unicodeWrap(null, HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "neutral treated as matching LTR context", ".", LTR_FMT.unicodeWrap(LTR, ".", false));
    assertEquals(
        "uniform dir matches RTL context",
        "& " + HE + "<",
        RTL_FMT.unicodeWrap(null, "& " + HE + "<", false));
    assertEquals(
        "uniform dir matches RTL context, HTML",
        EN_TAG + HE + EN_TAG,
        RTL_FMT.unicodeWrap(null, EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "neutral treated as matching RTL context", ".", RTL_FMT.unicodeWrap(RTL, ".", false));

    // Uniform directionality in opposite context.
    assertEquals(
        "uniform dir opposite to LTR context",
        RLE + "." + HE + "." + PDF + LRM,
        LTR_FMT.unicodeWrap(null, "." + HE + ".", false));
    assertEquals(
        "uniform dir opposite to LTR context, HTML",
        RLE + EN_TAG + HE + EN_TAG + PDF + LRM,
        LTR_FMT.unicodeWrap(null, EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "neutral treated as opposite to LTR context",
        RLE + "." + PDF + LRM,
        LTR_FMT.unicodeWrap(RTL, ".", false));
    assertEquals(
        "uniform dir opposite to RTL context",
        LRE + "." + EN + "." + PDF + RLM,
        RTL_FMT.unicodeWrap(null, "." + EN + ".", false));
    assertEquals(
        "uniform dir opposite to RTL context, HTML",
        LRE + HE_TAG + EN + HE_TAG + PDF + RLM,
        RTL_FMT.unicodeWrap(null, HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "neutral treated as opposite to RTL context",
        LRE + "." + PDF + RLM,
        RTL_FMT.unicodeWrap(LTR, ".", false));

    // "Known" unknown directionality is estimated.
    assertEquals(
        "known unknown dir matching LTR context",
        "." + EN + ".",
        LTR_FMT.unicodeWrap(null, "." + EN + ".", false));
    assertEquals(
        "known unknown dir opposite to LTR context",
        RLE + "." + HE + "." + PDF + LRM,
        LTR_FMT.unicodeWrap(null, "." + HE + ".", false));
    assertEquals(
        "known unknown dir matching RTL context",
        "." + HE + ".",
        RTL_FMT.unicodeWrap(null, "." + HE + ".", false));
    assertEquals(
        "known unknown dir opposite to RTL context",
        LRE + "." + EN + "." + PDF + RLM,
        RTL_FMT.unicodeWrap(null, "." + EN + ".", false));

    // We test mixed-directionality cases only on unicodeWrap() because the estimation logic is
    // outside the sphere of BidiFormatter, and different estimators will treat them differently.

    // Overall directionality matching context, but with opposite exit directionality.
    assertEquals(
        "exit dir opposite to LTR context",
        EN + HE + LRM,
        LTR_FMT.unicodeWrap(LTR, EN + HE, false));
    assertEquals(
        "exit dir opposite to LTR context, HTML",
        EN + HE + EN_TAG + LRM,
        LTR_FMT.unicodeWrap(LTR, EN + HE + EN_TAG, true));

    assertEquals(
        "exit dir opposite to RTL context",
        HE + EN + RLM,
        RTL_FMT.unicodeWrap(RTL, HE + EN, false));
    assertEquals(
        "exit dir opposite to RTL context, HTML",
        HE + EN + HE_TAG + RLM,
        RTL_FMT.unicodeWrap(RTL, HE + EN + HE_TAG, true));

    // Overall directionality matching context, but with opposite entry directionality.
    assertEquals(
        "entry dir opposite to LTR context", HE + EN, LTR_FMT.unicodeWrap(LTR, HE + EN, false));
    assertEquals(
        "entry dir opposite to LTR context, HTML",
        EN_TAG + HE + EN,
        LTR_FMT.unicodeWrap(LTR, EN_TAG + HE + EN, true));

    assertEquals(
        "entry dir opposite to RTL context", EN + HE, RTL_FMT.unicodeWrap(RTL, EN + HE, false));
    assertEquals(
        "entry dir opposite to RTL context, HTML",
        HE_TAG + EN + HE,
        RTL_FMT.unicodeWrap(RTL, HE_TAG + EN + HE, true));

    // Overall directionality matching context, but with opposite entry and exit directionality.
    assertEquals(
        "entry and exit dir opposite to LTR context",
        HE + EN + HE + LRM,
        LTR_FMT.unicodeWrap(LTR, HE + EN + HE, false));
    assertEquals(
        "entry and exit dir opposite to LTR context, HTML",
        EN_TAG + HE + EN + HE + EN_TAG + LRM,
        LTR_FMT.unicodeWrap(LTR, EN_TAG + HE + EN + HE + EN_TAG, true));

    assertEquals(
        "entry and exit dir opposite to RTL context",
        EN + HE + EN + RLM,
        RTL_FMT.unicodeWrap(RTL, EN + HE + EN, false));
    assertEquals(
        "entry and exit dir opposite to RTL context, HTML",
        HE_TAG + EN + HE + EN + HE_TAG + RLM,
        RTL_FMT.unicodeWrap(RTL, HE_TAG + EN + HE + EN + HE_TAG, true));
    assertEquals(
        "entry and exit dir opposite to RTL context, HTML",
        BidiWrappingText.create("", RLM),
        RTL_FMT.unicodeWrappingText(RTL, HE_TAG + EN + HE + EN + HE_TAG, true));

    // Entry and exit directionality matching context, but with opposite overall directionality.
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context",
        RLE + EN + HE + EN + PDF + LRM,
        LTR_FMT.unicodeWrap(RTL, EN + HE + EN, false));

    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context",
        LRE + HE + EN + HE + PDF + RLM,
        RTL_FMT.unicodeWrap(LTR, HE + EN + HE, false));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context",
        BidiWrappingText.create(LRE, PDF + RLM),
        RTL_FMT.unicodeWrappingText(LTR, HE + EN + HE, false));
  }

  @Test
  public void testMarkAfter() {
    assertEquals(
        "uniform dir matches LTR context, HTML", "", LTR_FMT.markAfter(null, EN + HE_TAG, true));
    assertEquals(
        "uniform dir matches RTL context, HTML", "", RTL_FMT.markAfter(null, HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to LTR context, HTML",
        LRM,
        LTR_FMT.markAfter(LTR, EN + HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to RTL context, HTML",
        RLM,
        RTL_FMT.markAfter(RTL, HE + EN + HE_TAG, true));
    assertEquals(
        "overall dir (but not exit dir) opposite to LTR context",
        LRM,
        LTR_FMT.markAfter(RTL, HE + EN, false));
    assertEquals(
        "overall dir (but not exit dir) opposite to RTL context",
        RLM,
        RTL_FMT.markAfter(LTR, EN + HE, false));
  }
}
