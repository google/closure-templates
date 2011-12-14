// This file was automatically generated from ParseInfoExample.soy.
// Please don't edit this file by hand.

package com.google.template.soy.test_data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.parseinfo.SoyFileInfo;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.parseinfo.SoyTemplateInfo.ParamRequisiteness;


/**
 * Soy parse info for ParseInfoExample.soy.
 */
public class ParseInfoExampleSoyInfo extends SoyFileInfo {


  public static class Param {
    private Param() {}

    /** Listed by .aaa, .bbbCcc, .ddd (private). */
    public static final String PPP_1 = "ppp1";
    /** Listed by .bbbCcc, .ddd (private). */
    public static final String QQQ_22 = "qqq22";
    /** Listed by .ddd (private). */
    public static final String RRR_3 = "rrr_3";
    /** Listed by .aaa. */
    public static final String XXX = "xxx";
    /** Listed by .bbbCcc. */
    public static final String YYY_ZZZ = "yyyZzz";
  }


  /**
   * This template has no params.
   */
  public static final SoyTemplateInfo HELLO = new SoyTemplateInfo(
      "examples.abc.hello",
      ImmutableMap.<String, ParamRequisiteness>of(),
      ImmutableSortedSet.<String>of());


  /**
   * Aaa template.
   */
  public static final AaaSoyTemplateInfo AAA =
      new AaaSoyTemplateInfo();

  public static class AaaSoyTemplateInfo extends SoyTemplateInfo {
    private AaaSoyTemplateInfo() {
      super("examples.abc.aaa",
            ImmutableMap.<String, ParamRequisiteness>builder()
                .put("xxx", ParamRequisiteness.OPTIONAL)
                .put("ppp1", ParamRequisiteness.REQUIRED)
                .put("qqq22", ParamRequisiteness.REQUIRED)
                .put("rrr_3", ParamRequisiteness.REQUIRED)
                .put("yyyZzz", ParamRequisiteness.REQUIRED)
                .build(),
            ImmutableSortedSet.<String>of());
    }

    /** One line description. */
    public final String XXX = "xxx";

    /** Desc of ppp from aaa. */
    public final String PPP_1 = "ppp1";

    // Indirect params.
    /** Listed by .bbbCcc, .ddd (private). */
    public final String QQQ_22 = "qqq22";
    /** Listed by .ddd (private). */
    public final String RRR_3 = "rrr_3";
    /** Listed by .bbbCcc. */
    public final String YYY_ZZZ = "yyyZzz";
  }


  /**
   * Bbb Ccc
   * template.
   */
  public static final BbbCccSoyTemplateInfo BBB_CCC =
      new BbbCccSoyTemplateInfo();

  public static class BbbCccSoyTemplateInfo extends SoyTemplateInfo {
    private BbbCccSoyTemplateInfo() {
      super("examples.abc.bbbCcc",
            ImmutableMap.<String, ParamRequisiteness>builder()
                .put("yyyZzz", ParamRequisiteness.REQUIRED)
                .put("ppp1", ParamRequisiteness.REQUIRED)
                .put("qqq22", ParamRequisiteness.REQUIRED)
                .put("rrr_3", ParamRequisiteness.REQUIRED)
                .build(),
            ImmutableSortedSet.<String>of());
    }

    /**
     * Multiline
     * description.
     */
    public final String YYY_ZZZ = "yyyZzz";

    /** Desc of ppp from bbbCcc. */
    public final String PPP_1 = "ppp1";

    /** Desc of qqq from bbbCcc. */
    public final String QQQ_22 = "qqq22";

    // Indirect params.
    /** Listed by .ddd (private). */
    public final String RRR_3 = "rrr_3";
  }


  private ParseInfoExampleSoyInfo() {
    super("ParseInfoExample.soy",
          "examples.abc",
          ImmutableSortedSet.<String>of(
              Param.PPP_1,
              Param.QQQ_22,
              Param.RRR_3,
              Param.XXX,
              Param.YYY_ZZZ),
          ImmutableList.<SoyTemplateInfo>of(
              HELLO,
              AAA,
              BBB_CCC),
          ImmutableMap.<String, CssTagsPrefixPresence>builder()
              .put("GGG_HHH", CssTagsPrefixPresence.NEVER)
              .put("_eee_fff", CssTagsPrefixPresence.ALWAYS)
              .put("aaa-bbb", CssTagsPrefixPresence.NEVER)
              .put("cccDdd", CssTagsPrefixPresence.SOMETIMES)
              .build());
  }


  private static final ParseInfoExampleSoyInfo __INSTANCE__ = new ParseInfoExampleSoyInfo();


  public static ParseInfoExampleSoyInfo getInstance() {
    return __INSTANCE__;
  }

}
