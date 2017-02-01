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
import static org.junit.Assert.assertNull;

import com.google.template.soy.data.Dir;
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
  private static final BidiFormatter UNKNOWN_FMT = BidiFormatter.getInstanceWithNoContext();
  private static final BidiFormatter LTR_ALWAYS_SPAN_FMT =
      new BidiFormatter.Builder(LTR).alwaysSpan(true).build();
  private static final BidiFormatter RTL_ALWAYS_SPAN_FMT =
      new BidiFormatter.Builder(RTL).alwaysSpan(true).build();
  private static final BidiFormatter UNKNOWN_ALWAYS_SPAN_FMT =
      new BidiFormatter.Builder((Dir) null).alwaysSpan(true).build();
  private static final BidiFormatter LTR_STEREO_RESET_FMT =
      new BidiFormatter.Builder(LTR).stereoReset(true).build();
  private static final BidiFormatter RTL_STEREO_RESET_FMT =
      new BidiFormatter.Builder(RTL).stereoReset(true).build();
  private static final BidiFormatter LTR_STEREO_RESET_ALWAYS_SPAN_FMT =
      new BidiFormatter.Builder(LTR).stereoReset(true).alwaysSpan(true).build();
  // We might as well try switching the order of the with...() calls.
  private static final BidiFormatter RTL_STEREO_RESET_ALWAYS_SPAN_FMT =
      new BidiFormatter.Builder(RTL).alwaysSpan(true).stereoReset(true).build();

  @Test
  public void testGetContextDir() {
    assertEquals(LTR, LTR_FMT.getContextDir());
    assertEquals(RTL, RTL_FMT.getContextDir());
    assertNull(UNKNOWN_FMT.getContextDir());
  }

  @Test
  public void testIsRtlContext() {
    assertEquals(false, LTR_FMT.isRtlContext());
    assertEquals(true, RTL_FMT.isRtlContext());
    assertEquals(false, UNKNOWN_FMT.isRtlContext());
  }

  @Test
  public void testGetInstanceByBooleanRtlContext() {
    assertEquals(LTR, BidiFormatter.getInstance(false).getContextDir());
    assertEquals(RTL, BidiFormatter.getInstance(true).getContextDir());
  }

  @Test
  public void testBuilderContextDir() {
    assertNull(new BidiFormatter.Builder((Dir) null).build().getContextDir());
    assertEquals(LTR, new BidiFormatter.Builder(LTR).build().getContextDir());
    assertEquals(LTR, new BidiFormatter.Builder(false).build().getContextDir());
    assertEquals(RTL, new BidiFormatter.Builder(RTL).build().getContextDir());
    assertEquals(RTL, new BidiFormatter.Builder(true).build().getContextDir());
  }

  @Test
  public void testEstimateDirection() {
    assertEquals(RTL, BidiFormatter.estimateDirection(HE, true));
    assertEquals(LTR, BidiFormatter.estimateDirection(EN, true));
    assertEquals(NEUTRAL, BidiFormatter.estimateDirection(".", true));

    // Text contains HTML or HTML-escaping.
    assertEquals(LTR, BidiFormatter.estimateDirection(HE + "<some sort of an HTML tag/>", false));
    assertEquals(RTL, BidiFormatter.estimateDirection(HE + "<some sort of an HTML tag/>", true));
    assertEquals(
        NEUTRAL, BidiFormatter.estimateDirection("." + "<some sort of an HTML tag/>", true));
  }

  @Test
  public void testDirAttrValue() {
    assertEquals("ltr", RTL_FMT.dirAttrValue(EN, true));
    assertEquals("ltr", LTR_FMT.dirAttrValue(EN, true));
    assertEquals("ltr", UNKNOWN_FMT.dirAttrValue(EN, true));
    assertEquals("rtl", LTR_FMT.dirAttrValue(HE, true));
    assertEquals("rtl", RTL_FMT.dirAttrValue(HE, true));
    assertEquals("rtl", UNKNOWN_FMT.dirAttrValue(HE, true));
    assertEquals("ltr", LTR_FMT.dirAttrValue("", true));
    assertEquals("rtl", RTL_FMT.dirAttrValue("", true));
    assertEquals("ltr", UNKNOWN_FMT.dirAttrValue("", true));

    // Text contains HTML or HTML-escaping:
    assertEquals("rtl", LTR_FMT.dirAttrValue(HE + "<some sort of an HTML tag>", true));
    assertEquals("ltr", LTR_FMT.dirAttrValue(HE + "<some sort of an HTML tag>", false));
  }

  @Test
  public void testKnownDirAttrValue() {
    assertEquals("ltr", LTR_FMT.knownDirAttrValue(LTR));
    assertEquals("ltr", RTL_FMT.knownDirAttrValue(LTR));
    assertEquals("ltr", UNKNOWN_FMT.knownDirAttrValue(LTR));
    assertEquals("rtl", LTR_FMT.knownDirAttrValue(RTL));
    assertEquals("rtl", RTL_FMT.knownDirAttrValue(RTL));
    assertEquals("rtl", UNKNOWN_FMT.knownDirAttrValue(RTL));
    assertEquals("ltr", LTR_FMT.knownDirAttrValue(NEUTRAL));
    assertEquals("rtl", RTL_FMT.knownDirAttrValue(NEUTRAL));
    assertEquals("ltr", UNKNOWN_FMT.knownDirAttrValue(NEUTRAL));
  }

  @Test
  public void testDirAttr() {
    assertEquals("", LTR_FMT.dirAttr(EN));
    assertEquals("dir=\"ltr\"", RTL_FMT.dirAttr(EN));
    assertEquals("dir=\"ltr\"", UNKNOWN_FMT.dirAttr(EN));
    assertEquals("dir=\"rtl\"", LTR_FMT.dirAttr(HE));
    assertEquals("", RTL_FMT.dirAttr(HE));
    assertEquals("dir=\"rtl\"", UNKNOWN_FMT.dirAttr(HE));
    assertEquals("", LTR_FMT.dirAttr("."));
    assertEquals("", RTL_FMT.dirAttr("."));
    assertEquals("", UNKNOWN_FMT.dirAttr("."));

    // Text contains HTML or HTML-escaping:
    assertEquals("dir=\"rtl\"", LTR_FMT.dirAttr(HE + "<some sort of an HTML tag>", true));
    assertEquals("", LTR_FMT.dirAttr(HE + "<some sort of an HTML tag>", false));
  }

  @Test
  public void testKnownDirAttr() {
    assertEquals("", LTR_FMT.knownDirAttr(LTR));
    assertEquals("dir=\"ltr\"", RTL_FMT.knownDirAttr(LTR));
    assertEquals("dir=\"ltr\"", UNKNOWN_FMT.knownDirAttr(LTR));
    assertEquals("dir=\"rtl\"", LTR_FMT.knownDirAttr(RTL));
    assertEquals("", RTL_FMT.knownDirAttr(RTL));
    assertEquals("dir=\"rtl\"", UNKNOWN_FMT.knownDirAttr(RTL));
    assertEquals("", LTR_FMT.knownDirAttr(NEUTRAL));
    assertEquals("", RTL_FMT.knownDirAttr(NEUTRAL));
    assertEquals("", UNKNOWN_FMT.knownDirAttr(NEUTRAL));
  }

  @Test
  public void testSpanWrap() {
    // Neutral directionality in whatever context.
    assertEquals("neutral dir in LTR context", "&amp; . &lt;", LTR_FMT.spanWrap("& . <"));
    assertEquals(
        "neutral dir in LTR context, HTML",
        HE_TAG + "." + HE_TAG,
        LTR_FMT.spanWrap(HE_TAG + "." + HE_TAG, true));
    assertEquals(
        "neutral dir in LTR context, always span",
        "<span>&amp; . &lt;</span>",
        LTR_ALWAYS_SPAN_FMT.spanWrap("& . <"));
    assertEquals("neutral dir in RTL context", "&amp; . &lt;", RTL_FMT.spanWrap("& . <"));
    assertEquals(
        "neutral dir in RTL context, HTML",
        EN_TAG + "." + EN_TAG,
        RTL_FMT.spanWrap(EN_TAG + "." + EN_TAG, true));
    assertEquals(
        "neutral dir in RTL context, always span",
        "<span>&amp; . &lt;</span>",
        RTL_ALWAYS_SPAN_FMT.spanWrap("& . <"));
    assertEquals("neutral dir in unknown context", "&amp; . &lt;", UNKNOWN_FMT.spanWrap("& . <"));
    assertEquals(
        "neutral dir in unknown context, HTML",
        EN_TAG + "." + EN_TAG,
        UNKNOWN_FMT.spanWrap(EN_TAG + "." + EN_TAG, true));
    assertEquals(
        "neutral dir in unknown context, always span",
        "<span>&amp; . &lt;</span>",
        UNKNOWN_ALWAYS_SPAN_FMT.spanWrap("& . <"));

    // Uniform directionality in matching context.
    assertEquals(
        "uniform dir matches LTR context",
        "&amp; " + EN + "&lt;",
        LTR_FMT.spanWrap("& " + EN + "<"));
    assertEquals(
        "uniform dir matches LTR context, HTML",
        HE_TAG + EN + HE_TAG,
        LTR_FMT.spanWrap(HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "uniform dir matches LTR context, always span",
        "<span>&amp; " + EN + "&lt;</span>",
        LTR_ALWAYS_SPAN_FMT.spanWrap("& " + EN + "<"));
    assertEquals(
        "neutral treated as matching LTR context", ".", LTR_FMT.spanWrapWithKnownDir(LTR, "."));
    assertEquals(
        "uniform dir matches RTL context",
        "&amp; " + HE + "&lt;",
        RTL_FMT.spanWrap("& " + HE + "<"));
    assertEquals(
        "uniform dir matches RTL context, HTML",
        EN_TAG + HE + EN_TAG,
        RTL_FMT.spanWrap(EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "uniform dir matches RTL context, always span",
        "<span>&amp; " + HE + "&lt;</span>",
        RTL_ALWAYS_SPAN_FMT.spanWrap("& " + HE + "<"));
    assertEquals(
        "neutral treated as matching RTL context", ".", RTL_FMT.spanWrapWithKnownDir(RTL, "."));

    // Uniform directionality in unknown context.
    assertEquals(
        "uniform LTR in unknown context",
        "<span dir=\"ltr\">." + EN + ".</span>",
        UNKNOWN_FMT.spanWrap("." + EN + "."));
    assertEquals(
        "neutral treated as LTR in unknown context",
        "<span dir=\"ltr\">" + "." + "</span>",
        UNKNOWN_FMT.spanWrapWithKnownDir(LTR, "."));
    assertEquals(
        "uniform RTL in unknown context",
        "<span dir=\"rtl\">." + HE + ".</span>",
        UNKNOWN_FMT.spanWrap("." + HE + "."));
    assertEquals(
        "neutral treated as RTL in unknown context",
        "<span dir=\"rtl\">" + "." + "</span>",
        UNKNOWN_FMT.spanWrapWithKnownDir(RTL, "."));

    // Uniform directionality in opposite context.
    assertEquals(
        "uniform dir opposite to LTR context",
        "<span dir=\"rtl\">." + HE + ".</span>" + LRM,
        LTR_FMT.spanWrap("." + HE + "."));
    assertEquals(
        "uniform dir opposite to LTR context, HTML",
        "<span dir=\"rtl\">" + EN_TAG + HE + EN_TAG + "</span>" + LRM,
        LTR_FMT.spanWrap(EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "uniform dir opposite to LTR context, stereo reset",
        LRM + "<span dir=\"rtl\">." + HE + ".</span>" + LRM,
        LTR_STEREO_RESET_FMT.spanWrap("." + HE + "."));
    assertEquals(
        "uniform dir opposite to LTR context, stereo reset, HTML",
        LRM + "<span dir=\"rtl\">" + EN_TAG + HE + EN_TAG + "</span>" + LRM,
        LTR_STEREO_RESET_FMT.spanWrap(EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "uniform dir opposite to LTR context, no isolation",
        "<span dir=\"rtl\">." + HE + ".</span>",
        LTR_FMT.spanWrap("." + HE + ".", false, false));
    assertEquals(
        "uniform dir opposite to LTR context, stereo reset, no isolation",
        "<span dir=\"rtl\">." + HE + ".</span>",
        LTR_STEREO_RESET_FMT.spanWrap("." + HE + ".", false, false));
    assertEquals(
        "neutral treated as opposite to LTR context",
        "<span dir=\"rtl\">" + "." + "</span>" + LRM,
        LTR_FMT.spanWrapWithKnownDir(RTL, "."));
    assertEquals(
        "uniform dir opposite to RTL context",
        "<span dir=\"ltr\">." + EN + ".</span>" + RLM,
        RTL_FMT.spanWrap("." + EN + "."));
    assertEquals(
        "uniform dir opposite to RTL context, HTML",
        "<span dir=\"ltr\">" + HE_TAG + EN + HE_TAG + "</span>" + RLM,
        RTL_FMT.spanWrap(HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "uniform dir opposite to RTL context, stereo reset",
        RLM + "<span dir=\"ltr\">." + EN + ".</span>" + RLM,
        RTL_STEREO_RESET_FMT.spanWrap("." + EN + "."));
    assertEquals(
        "uniform dir opposite to RTL context, stereo reset, HTML",
        RLM + "<span dir=\"ltr\">" + HE_TAG + EN + HE_TAG + "</span>" + RLM,
        RTL_STEREO_RESET_FMT.spanWrap(HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "uniform dir opposite to RTL context, no isolation",
        "<span dir=\"ltr\">." + EN + ".</span>",
        RTL_FMT.spanWrap("." + EN + ".", false, false));
    assertEquals(
        "uniform dir opposite to RTL context, stereo reset, no isolation",
        "<span dir=\"ltr\">." + EN + ".</span>",
        RTL_STEREO_RESET_FMT.spanWrap("." + EN + ".", false, false));
    assertEquals(
        "neutral treated as opposite to RTL context",
        "<span dir=\"ltr\">" + "." + "</span>" + RLM,
        RTL_FMT.spanWrapWithKnownDir(LTR, "."));

    // "Known" unknown directionality is estimated.
    assertEquals(
        "known unknown dir matching LTR context",
        "." + EN + ".",
        LTR_FMT.spanWrapWithKnownDir(null, "." + EN + "."));
    assertEquals(
        "known unknown dir opposite to LTR context",
        "<span dir=\"rtl\">." + HE + ".</span>" + LRM,
        LTR_FMT.spanWrapWithKnownDir(null, "." + HE + "."));
    assertEquals(
        "known unknown dir matching RTL context",
        "." + HE + ".",
        RTL_FMT.spanWrapWithKnownDir(null, "." + HE + "."));
    assertEquals(
        "known unknown dir opposite to RTL context",
        "<span dir=\"ltr\">." + EN + ".</span>" + RLM,
        RTL_FMT.spanWrapWithKnownDir(null, "." + EN + "."));

    // We test mixed-directionality cases only on spanWrapInKnownDir() because the estimation logic
    // is outside the sphere of BidiFormatter, and different estimators will treat them differently.

    // Overall directionality matching context, but with opposite exit directionality.
    assertEquals(
        "exit dir opposite to LTR context",
        EN + HE + LRM,
        LTR_FMT.spanWrapWithKnownDir(LTR, EN + HE));
    assertEquals(
        "exit dir opposite to LTR context, HTML",
        EN + HE + EN_TAG + LRM,
        LTR_FMT.spanWrapWithKnownDir(LTR, EN + HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to LTR context, stereo reset",
        EN + HE + LRM,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, EN + HE));
    assertEquals(
        "exit dir opposite to LTR context, stereo reset, HTML",
        EN + HE + EN_TAG + LRM,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, EN + HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to LTR context, no isolation",
        EN + HE,
        LTR_FMT.spanWrapWithKnownDir(LTR, EN + HE, false, false));
    assertEquals(
        "exit dir opposite to LTR context, stereo reset, no isolation",
        EN + HE,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, EN + HE, false, false));
    assertEquals(
        "exit dir opposite to LTR context, always span",
        "<span>" + EN + HE + "</span>" + LRM,
        LTR_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(LTR, EN + HE));
    assertEquals(
        "exit dir opposite to RTL context",
        HE + EN + RLM,
        RTL_FMT.spanWrapWithKnownDir(RTL, HE + EN));
    assertEquals(
        "exit dir opposite to RTL context, HTML",
        HE + EN + HE_TAG + RLM,
        RTL_FMT.spanWrapWithKnownDir(RTL, HE + EN + HE_TAG, true));
    assertEquals(
        "exit dir opposite to RTL context, stereo reset",
        HE + EN + RLM,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, HE + EN));
    assertEquals(
        "exit dir opposite to RTL context, stereo reset, HTML",
        HE + EN + HE_TAG + RLM,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, HE + EN + HE_TAG, true));
    assertEquals(
        "exit dir opposite to RTL context, no isolation",
        HE + EN,
        RTL_FMT.spanWrapWithKnownDir(RTL, HE + EN, false, false));
    assertEquals(
        "exit dir opposite to RTL context, stereo reset, no isolation",
        HE + EN,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, HE + EN, false, false));
    assertEquals(
        "exit dir opposite to RTL context, always span",
        "<span>" + HE + EN + "</span>" + RLM,
        RTL_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(RTL, HE + EN));

    // Overall directionality matching context, but with opposite entry directionality.
    assertEquals(
        "entry dir opposite to LTR context", HE + EN, LTR_FMT.spanWrapWithKnownDir(LTR, HE + EN));
    assertEquals(
        "entry dir opposite to LTR context, HTML",
        EN_TAG + HE + EN,
        LTR_FMT.spanWrapWithKnownDir(LTR, EN_TAG + HE + EN, true));
    assertEquals(
        "entry dir opposite to LTR context, stereo reset",
        LRM + HE + EN,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, HE + EN));
    assertEquals(
        "entry dir opposite to LTR context, stereo reset, HTML",
        LRM + EN_TAG + HE + EN,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, EN_TAG + HE + EN, true));
    assertEquals(
        "entry dir opposite to LTR context, no isolation",
        HE + EN,
        LTR_FMT.spanWrapWithKnownDir(LTR, HE + EN, false, false));
    assertEquals(
        "entry dir opposite to LTR context, stereo reset, no isolation",
        HE + EN,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, HE + EN, false, false));
    assertEquals(
        "entry dir opposite to LTR context, always span",
        "<span>" + HE + EN + "</span>",
        LTR_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(LTR, HE + EN));
    assertEquals(
        "entry dir opposite to LTR context, always span, stereo reset",
        LRM + "<span>" + HE + EN + "</span>",
        LTR_STEREO_RESET_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(LTR, HE + EN));
    assertEquals(
        "entry dir opposite to RTL context", EN + HE, RTL_FMT.spanWrapWithKnownDir(RTL, EN + HE));
    assertEquals(
        "entry dir opposite to RTL context, HTML",
        HE_TAG + EN + HE,
        RTL_FMT.spanWrapWithKnownDir(RTL, HE_TAG + EN + HE, true));
    assertEquals(
        "entry dir opposite to RTL context, stereo reset",
        RLM + EN + HE,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, EN + HE));
    assertEquals(
        "entry dir opposite to RTL context, stereo reset, HTML",
        RLM + HE_TAG + EN + HE,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, HE_TAG + EN + HE, true));
    assertEquals(
        "entry dir opposite to RTL context, no isolation",
        EN + HE,
        RTL_FMT.spanWrapWithKnownDir(RTL, EN + HE, false, false));
    assertEquals(
        "entry dir opposite to RTL context, stereo reset, no isolation",
        EN + HE,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, EN + HE, false, false));
    assertEquals(
        "entry dir opposite to RTL context, always span",
        "<span>" + EN + HE + "</span>",
        RTL_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(RTL, EN + HE));
    assertEquals(
        "entry dir opposite to RTL context, always span, stereo reset",
        RLM + "<span>" + EN + HE + "</span>",
        RTL_STEREO_RESET_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(RTL, EN + HE));

    // Overall directionality matching context, but with opposite entry and exit directionality.
    assertEquals(
        "entry and exit dir opposite to LTR context",
        HE + EN + HE + LRM,
        LTR_FMT.spanWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "entry and exit dir opposite to LTR context, HTML",
        EN_TAG + HE + EN + HE + EN_TAG + LRM,
        LTR_FMT.spanWrapWithKnownDir(LTR, EN_TAG + HE + EN + HE + EN_TAG, true));
    assertEquals(
        "entry and exit dir opposite to LTR context, stereo reset",
        LRM + HE + EN + HE + LRM,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "entry and exit dir opposite to LTR context, stereo reset, HTML",
        LRM + EN_TAG + HE + EN + HE + EN_TAG + LRM,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, EN_TAG + HE + EN + HE + EN_TAG, true));
    assertEquals(
        "entry and exit dir opposite to LTR context, stereo reset, always span",
        LRM + "<span>" + HE + EN + HE + "</span>" + LRM,
        LTR_STEREO_RESET_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "entry and exit dir opposite to LTR context, no isolation",
        HE + EN + HE,
        LTR_FMT.spanWrapWithKnownDir(LTR, HE + EN + HE, false, false));
    assertEquals(
        "entry and exit dir opposite to RTL context",
        EN + HE + EN + RLM,
        RTL_FMT.spanWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "entry and exit dir opposite to RTL context, HTML",
        HE_TAG + EN + HE + EN + HE_TAG + RLM,
        RTL_FMT.spanWrapWithKnownDir(RTL, HE_TAG + EN + HE + EN + HE_TAG, true));
    assertEquals(
        "entry and exit dir opposite to RTL context, stereo reset",
        RLM + EN + HE + EN + RLM,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "entry and exit dir opposite to RTL context, stereo reset, HTML",
        RLM + HE_TAG + EN + HE + EN + HE_TAG + RLM,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, HE_TAG + EN + HE + EN + HE_TAG, true));
    assertEquals(
        "entry and exit dir opposite to RTL context, stereo reset, always span",
        RLM + "<span>" + EN + HE + EN + "</span>" + RLM,
        RTL_STEREO_RESET_ALWAYS_SPAN_FMT.spanWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "entry and exit dir opposite to RTL context, no isolation",
        EN + HE + EN,
        RTL_FMT.spanWrapWithKnownDir(RTL, EN + HE + EN, false, false));

    // Entry and exit directionality matching context, but with opposite overall directionality.
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context",
        "<span dir=\"rtl\">" + EN + HE + EN + "</span>" + LRM,
        LTR_FMT.spanWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context, stereo reset",
        LRM + "<span dir=\"rtl\">" + EN + HE + EN + "</span>" + LRM,
        LTR_STEREO_RESET_FMT.spanWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context, no isolation",
        "<span dir=\"rtl\">" + EN + HE + EN + "</span>",
        LTR_FMT.spanWrapWithKnownDir(RTL, EN + HE + EN, false, false));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context",
        "<span dir=\"ltr\">" + HE + EN + HE + "</span>" + RLM,
        RTL_FMT.spanWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context, stereo reset",
        RLM + "<span dir=\"ltr\">" + HE + EN + HE + "</span>" + RLM,
        RTL_STEREO_RESET_FMT.spanWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context, no isolation",
        "<span dir=\"ltr\">" + HE + EN + HE + "</span>",
        RTL_FMT.spanWrapWithKnownDir(LTR, HE + EN + HE, false, false));
  }

  @Test
  public void testUnicodeWrap() {
    // Neutral directionality in whatever context.
    assertEquals("neutral dir in LTR context", "& . <", LTR_FMT.unicodeWrap("& . <"));
    assertEquals(
        "neutral dir in LTR context, HTML",
        HE_TAG + "." + HE_TAG,
        LTR_FMT.unicodeWrap(HE_TAG + "." + HE_TAG, true));
    assertEquals(
        "neutral dir in LTR context, always span",
        "& . <",
        LTR_ALWAYS_SPAN_FMT.unicodeWrap("& . <"));
    assertEquals("neutral dir in RTL context", "& . <", RTL_FMT.unicodeWrap("& . <"));
    assertEquals(
        "neutral dir in RTL context, HTML",
        EN_TAG + "." + EN_TAG,
        RTL_FMT.unicodeWrap(EN_TAG + "." + EN_TAG, true));
    assertEquals(
        "neutral dir in RTL context, always span",
        "& . <",
        RTL_ALWAYS_SPAN_FMT.unicodeWrap("& . <"));
    assertEquals("neutral dir in unknown context", "& . <", UNKNOWN_FMT.unicodeWrap("& . <"));
    assertEquals(
        "neutral dir in unknown context, HTML",
        EN_TAG + "." + EN_TAG,
        UNKNOWN_FMT.unicodeWrap(EN_TAG + "." + EN_TAG, true));
    assertEquals(
        "neutral dir in unknown context, always span",
        "& . <",
        UNKNOWN_ALWAYS_SPAN_FMT.unicodeWrap("& . <"));

    // Uniform directionality in matching context.
    assertEquals(
        "uniform dir matches LTR context", "& " + EN + "<", LTR_FMT.unicodeWrap("& " + EN + "<"));
    assertEquals(
        "uniform dir matches LTR context, HTML",
        HE_TAG + EN + HE_TAG,
        LTR_FMT.unicodeWrap(HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "uniform dir matches LTR context, always span",
        "& " + EN + "<",
        LTR_ALWAYS_SPAN_FMT.unicodeWrap("& " + EN + "<"));
    assertEquals(
        "neutral treated as matching LTR context", ".", LTR_FMT.unicodeWrapWithKnownDir(LTR, "."));
    assertEquals(
        "uniform dir matches RTL context", "& " + HE + "<", RTL_FMT.unicodeWrap("& " + HE + "<"));
    assertEquals(
        "uniform dir matches RTL context, HTML",
        EN_TAG + HE + EN_TAG,
        RTL_FMT.unicodeWrap(EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "uniform dir matches RTL context, always span",
        "& " + HE + "<",
        RTL_ALWAYS_SPAN_FMT.unicodeWrap("& " + HE + "<"));
    assertEquals(
        "neutral treated as matching RTL context", ".", RTL_FMT.unicodeWrapWithKnownDir(RTL, "."));

    // Uniform directionality in unknown context.
    assertEquals(
        "uniform LTR in unknown context",
        LRE + "." + EN + "." + PDF,
        UNKNOWN_FMT.unicodeWrap("." + EN + "."));
    assertEquals(
        "uniform LTR in unknown context, no isolation",
        LRE + "." + EN + "." + PDF,
        UNKNOWN_FMT.unicodeWrap("." + EN + ".", false, false));
    assertEquals(
        "neutral treated as LTR in unknown context",
        LRE + "." + PDF,
        UNKNOWN_FMT.unicodeWrapWithKnownDir(LTR, "."));
    assertEquals(
        "uniform RTL in unknown context",
        RLE + "." + HE + "." + PDF,
        UNKNOWN_FMT.unicodeWrap("." + HE + "."));
    assertEquals(
        "uniform RTL in unknown context, no isolation",
        RLE + "." + HE + "." + PDF,
        UNKNOWN_FMT.unicodeWrap("." + HE + ".", false, false));
    assertEquals(
        "neutral treated as RTL in unknown context",
        RLE + "." + PDF,
        UNKNOWN_FMT.unicodeWrapWithKnownDir(RTL, "."));

    // Uniform directionality in opposite context.
    assertEquals(
        "uniform dir opposite to LTR context",
        RLE + "." + HE + "." + PDF + LRM,
        LTR_FMT.unicodeWrap("." + HE + "."));
    assertEquals(
        "uniform dir opposite to LTR context, HTML",
        RLE + EN_TAG + HE + EN_TAG + PDF + LRM,
        LTR_FMT.unicodeWrap(EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "uniform dir opposite to LTR context, stereo reset",
        LRM + RLE + "." + HE + "." + PDF + LRM,
        LTR_STEREO_RESET_FMT.unicodeWrap("." + HE + "."));
    assertEquals(
        "uniform dir opposite to LTR context, stereo reset, HTML",
        LRM + RLE + EN_TAG + HE + EN_TAG + PDF + LRM,
        LTR_STEREO_RESET_FMT.unicodeWrap(EN_TAG + HE + EN_TAG, true));
    assertEquals(
        "uniform dir opposite to LTR context, no isolation",
        RLE + "." + HE + "." + PDF,
        LTR_FMT.unicodeWrap("." + HE + ".", false, false));
    assertEquals(
        "uniform dir opposite to LTR context, stereo reset, no isolation",
        RLE + "." + HE + "." + PDF,
        LTR_STEREO_RESET_FMT.unicodeWrap("." + HE + ".", false, false));
    assertEquals(
        "neutral treated as opposite to LTR context",
        RLE + "." + PDF + LRM,
        LTR_FMT.unicodeWrapWithKnownDir(RTL, "."));
    assertEquals(
        "uniform dir opposite to RTL context",
        LRE + "." + EN + "." + PDF + RLM,
        RTL_FMT.unicodeWrap("." + EN + "."));
    assertEquals(
        "uniform dir opposite to RTL context, HTML",
        LRE + HE_TAG + EN + HE_TAG + PDF + RLM,
        RTL_FMT.unicodeWrap(HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "uniform dir opposite to RTL context, stereo reset",
        RLM + LRE + "." + EN + "." + PDF + RLM,
        RTL_STEREO_RESET_FMT.unicodeWrap("." + EN + "."));
    assertEquals(
        "uniform dir opposite to RTL context, stereo reset, HTML",
        RLM + LRE + HE_TAG + EN + HE_TAG + PDF + RLM,
        RTL_STEREO_RESET_FMT.unicodeWrap(HE_TAG + EN + HE_TAG, true));
    assertEquals(
        "uniform dir opposite to RTL context, no isolation",
        LRE + "." + EN + "." + PDF,
        RTL_FMT.unicodeWrap("." + EN + ".", false, false));
    assertEquals(
        "uniform dir opposite to RTL context, stereo reset, no isolation",
        LRE + "." + EN + "." + PDF,
        RTL_STEREO_RESET_FMT.unicodeWrap("." + EN + ".", false, false));
    assertEquals(
        "neutral treated as opposite to RTL context",
        LRE + "." + PDF + RLM,
        RTL_FMT.unicodeWrapWithKnownDir(LTR, "."));

    // "Known" unknown directionality is estimated.
    assertEquals(
        "known unknown dir matching LTR context",
        "." + EN + ".",
        LTR_FMT.unicodeWrapWithKnownDir(null, "." + EN + "."));
    assertEquals(
        "known unknown dir opposite to LTR context",
        RLE + "." + HE + "." + PDF + LRM,
        LTR_FMT.unicodeWrapWithKnownDir(null, "." + HE + "."));
    assertEquals(
        "known unknown dir matching RTL context",
        "." + HE + ".",
        RTL_FMT.unicodeWrapWithKnownDir(null, "." + HE + "."));
    assertEquals(
        "known unknown dir opposite to RTL context",
        LRE + "." + EN + "." + PDF + RLM,
        RTL_FMT.unicodeWrapWithKnownDir(null, "." + EN + "."));

    // We test mixed-directionality cases only on unicodeWrapInKnownDir() because the estimation
    // logic is outside the sphere of BidiFormatter, and different estimators will treat them
    // differently.

    // Overall directionality matching context, but with opposite exit directionality.
    assertEquals(
        "exit dir opposite to LTR context",
        EN + HE + LRM,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, EN + HE));
    assertEquals(
        "exit dir opposite to LTR context, HTML",
        EN + HE + EN_TAG + LRM,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, EN + HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to LTR context, stereo reset",
        EN + HE + LRM,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, EN + HE));
    assertEquals(
        "exit dir opposite to LTR context, stereo reset, HTML",
        EN + HE + EN_TAG + LRM,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, EN + HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to LTR context, no isolation",
        EN + HE,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, EN + HE, false, false));
    assertEquals(
        "exit dir opposite to LTR context, stereo reset, no isolation",
        EN + HE,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, EN + HE, false, false));
    assertEquals(
        "exit dir opposite to LTR context, always span",
        EN + HE + LRM,
        LTR_ALWAYS_SPAN_FMT.unicodeWrapWithKnownDir(LTR, EN + HE));

    assertEquals(
        "exit dir opposite to RTL context",
        HE + EN + RLM,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, HE + EN));
    assertEquals(
        "exit dir opposite to RTL context, HTML",
        HE + EN + HE_TAG + RLM,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, HE + EN + HE_TAG, true));
    assertEquals(
        "exit dir opposite to RTL context, stereo reset",
        HE + EN + RLM,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, HE + EN));
    assertEquals(
        "exit dir opposite to RTL context, stereo reset, HTML",
        HE + EN + HE_TAG + RLM,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, HE + EN + HE_TAG, true));
    assertEquals(
        "exit dir opposite to RTL context, no isolation",
        HE + EN,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, HE + EN, false, false));
    assertEquals(
        "exit dir opposite to RTL context, stereo reset, no isolation",
        HE + EN,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, HE + EN, false, false));
    assertEquals(
        "exit dir opposite to RTL context, always span",
        HE + EN + RLM,
        RTL_ALWAYS_SPAN_FMT.unicodeWrapWithKnownDir(RTL, HE + EN));

    // Overall directionality matching context, but with opposite entry directionality.
    assertEquals(
        "entry dir opposite to LTR context",
        HE + EN,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, HE + EN));
    assertEquals(
        "entry dir opposite to LTR context, HTML",
        EN_TAG + HE + EN,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, EN_TAG + HE + EN, true));
    assertEquals(
        "entry dir opposite to LTR context, stereo reset",
        LRM + HE + EN,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, HE + EN));
    assertEquals(
        "entry dir opposite to LTR context, stereo reset, HTML",
        LRM + EN_TAG + HE + EN,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, EN_TAG + HE + EN, true));
    assertEquals(
        "entry dir opposite to LTR context, no isolation",
        HE + EN,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, HE + EN, false, false));
    assertEquals(
        "entry dir opposite to LTR context, stereo reset, no isolation",
        HE + EN,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, HE + EN, false, false));

    assertEquals(
        "entry dir opposite to RTL context",
        EN + HE,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, EN + HE));
    assertEquals(
        "entry dir opposite to RTL context, HTML",
        HE_TAG + EN + HE,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, HE_TAG + EN + HE, true));
    assertEquals(
        "entry dir opposite to RTL context, stereo reset",
        RLM + EN + HE,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, EN + HE));
    assertEquals(
        "entry dir opposite to RTL context, stereo reset, HTML",
        RLM + HE_TAG + EN + HE,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, HE_TAG + EN + HE, true));
    assertEquals(
        "entry dir opposite to RTL context, no isolation",
        EN + HE,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, EN + HE, false, false));
    assertEquals(
        "entry dir opposite to RTL context, stereo reset, no isolation",
        EN + HE,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, EN + HE, false, false));

    // Overall directionality matching context, but with opposite entry and exit directionality.
    assertEquals(
        "entry and exit dir opposite to LTR context",
        HE + EN + HE + LRM,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "entry and exit dir opposite to LTR context, HTML",
        EN_TAG + HE + EN + HE + EN_TAG + LRM,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, EN_TAG + HE + EN + HE + EN_TAG, true));
    assertEquals(
        "entry and exit dir opposite to LTR context, stereo reset",
        LRM + HE + EN + HE + LRM,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "entry and exit dir opposite to LTR context, stereo reset, HTML",
        LRM + EN_TAG + HE + EN + HE + EN_TAG + LRM,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, EN_TAG + HE + EN + HE + EN_TAG, true));
    assertEquals(
        "entry and exit dir opposite to LTR context, stereo reset, always span",
        LRM + HE + EN + HE + LRM,
        LTR_STEREO_RESET_ALWAYS_SPAN_FMT.unicodeWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "entry and exit dir opposite to LTR context, no isolation",
        HE + EN + HE,
        LTR_FMT.unicodeWrapWithKnownDir(LTR, HE + EN + HE, false, false));

    assertEquals(
        "entry and exit dir opposite to RTL context",
        EN + HE + EN + RLM,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "entry and exit dir opposite to RTL context, HTML",
        HE_TAG + EN + HE + EN + HE_TAG + RLM,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, HE_TAG + EN + HE + EN + HE_TAG, true));
    assertEquals(
        "entry and exit dir opposite to RTL context, stereo reset",
        RLM + EN + HE + EN + RLM,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "entry and exit dir opposite to RTL context, stereo reset, HTML",
        RLM + HE_TAG + EN + HE + EN + HE_TAG + RLM,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, HE_TAG + EN + HE + EN + HE_TAG, true));
    assertEquals(
        "entry and exit dir opposite to RTL context, stereo reset, always span",
        RLM + EN + HE + EN + RLM,
        RTL_STEREO_RESET_ALWAYS_SPAN_FMT.unicodeWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "entry and exit dir opposite to RTL context, no isolation",
        EN + HE + EN,
        RTL_FMT.unicodeWrapWithKnownDir(RTL, EN + HE + EN, false, false));

    // Entry and exit directionality matching context, but with opposite overall directionality.
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context",
        RLE + EN + HE + EN + PDF + LRM,
        LTR_FMT.unicodeWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context, stereo reset",
        LRM + RLE + EN + HE + EN + PDF + LRM,
        LTR_STEREO_RESET_FMT.unicodeWrapWithKnownDir(RTL, EN + HE + EN));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to LTR context, no isolation",
        RLE + EN + HE + EN + PDF,
        LTR_FMT.unicodeWrapWithKnownDir(RTL, EN + HE + EN, false, false));

    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context",
        LRE + HE + EN + HE + PDF + RLM,
        RTL_FMT.unicodeWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context, stereo reset",
        RLM + LRE + HE + EN + HE + PDF + RLM,
        RTL_STEREO_RESET_FMT.unicodeWrapWithKnownDir(LTR, HE + EN + HE));
    assertEquals(
        "overall dir (but not entry or exit dir) opposite to RTL context, no isolation",
        LRE + HE + EN + HE + PDF,
        RTL_FMT.unicodeWrapWithKnownDir(LTR, HE + EN + HE, false, false));
  }

  @Test
  public void testMarkAfter() {
    assertEquals("uniform dir matches LTR context, HTML", "", LTR_FMT.markAfter(EN + HE_TAG, true));
    assertEquals("uniform dir matches RTL context, HTML", "", RTL_FMT.markAfter(HE + EN_TAG, true));
    assertEquals("uniform LTR in unknown context", "", UNKNOWN_FMT.markAfter(EN));
    assertEquals("uniform RTL in unknown context", "", UNKNOWN_FMT.markAfter(HE));
    assertEquals(
        "exit dir opposite to LTR context, HTML",
        LRM,
        LTR_FMT.markAfterKnownDir(LTR, EN + HE + EN_TAG, true));
    assertEquals(
        "exit dir opposite to RTL context, HTML",
        RLM,
        RTL_FMT.markAfterKnownDir(RTL, HE + EN + HE_TAG, true));
    assertEquals(
        "overall dir (but not exit dir) opposite to LTR context",
        LRM,
        LTR_FMT.markAfterKnownDir(RTL, HE + EN));
    assertEquals(
        "overall dir (but not exit dir) opposite to RTL context",
        RLM,
        RTL_FMT.markAfterKnownDir(LTR, EN + HE));
  }

  @Test
  public void testMarkBefore() {
    assertEquals(
        "uniform dir matches LTR context, HTML", "", LTR_FMT.markBefore(HE_TAG + EN, true));
    assertEquals(
        "uniform dir matches RTL context, HTML", "", RTL_FMT.markBefore(EN_TAG + HE, true));
    assertEquals("uniform LTR in unknown context", "", UNKNOWN_FMT.markBefore(EN));
    assertEquals("uniform RTL in unknown context", "", UNKNOWN_FMT.markBefore(HE));
    assertEquals(
        "entry dir opposite to LTR context, HTML",
        LRM,
        LTR_FMT.markBeforeKnownDir(LTR, EN_TAG + HE + EN, true));
    assertEquals(
        "entry dir opposite to RTL context, HTML",
        RLM,
        RTL_FMT.markBeforeKnownDir(RTL, HE_TAG + EN + HE, true));
    assertEquals(
        "overall dir (but not entry dir) opposite to LTR context",
        LRM,
        LTR_FMT.markBeforeKnownDir(RTL, EN + HE));
    assertEquals(
        "overall dir (but not entry dir) opposite to RTL context",
        RLM,
        RTL_FMT.markBeforeKnownDir(LTR, HE + EN));
  }

  @Test
  public void testMark() {
    assertEquals(LRM, LTR_FMT.mark());
    assertEquals(RLM, RTL_FMT.mark());
    assertEquals("", UNKNOWN_FMT.mark());
  }

  @Test
  public void testStartEdge() {
    assertEquals(BidiUtils.LEFT, LTR_FMT.startEdge());
    assertEquals(BidiUtils.RIGHT, RTL_FMT.startEdge());
    assertEquals(BidiUtils.LEFT, UNKNOWN_FMT.startEdge());
  }

  @Test
  public void testEndEdge() {
    assertEquals(BidiUtils.RIGHT, LTR_FMT.endEdge());
    assertEquals(BidiUtils.LEFT, RTL_FMT.endEdge());
    assertEquals(BidiUtils.RIGHT, UNKNOWN_FMT.endEdge());
  }
}
