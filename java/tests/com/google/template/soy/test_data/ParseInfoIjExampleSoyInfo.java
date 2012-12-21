// This file was automatically generated from ParseInfoIjExample.soy.
// Please don't edit this file by hand.

package com.google.template.soy.test_data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.parseinfo.SoyFileInfo;
import com.google.template.soy.parseinfo.SoyTemplateInfo;


/**
 * Soy parse info for ParseInfoIjExample.soy.
 */
public class ParseInfoIjExampleSoyInfo extends SoyFileInfo {


  /** This Soy file's namespace. */
  public static final String __NAMESPACE__ = "examples.abc";


  public static class TemplateName {
    private TemplateName() {}

    /** The full template name of the .hello template. */
    public static final String HELLO = "examples.abc.hello";
    /** The full template name of the .aaa template. */
    public static final String AAA = "examples.abc.aaa";
    /** The full template name of the .bbbCcc template. */
    public static final String BBB_CCC = "examples.abc.bbbCcc";
  }


  /**
   * Param names from all templates in this Soy file.
   */
  public static class Param {
    private Param() {}

    /** Listed by .aaa, .bbbCcc, .ddd (private). */
    public static final String PPP_1 = "ppp1";
    /** Listed by .bbbCcc, .ddd (private). */
    public static final String QQQ_22 = "qqq22";
    /** Listed by .ddd (private). */
    public static final String RRR_3 = "rrr_3";
    /** Listed by myDelegate (delegate). */
    public static final String SSS = "sss";
    /** Listed by .aaa. */
    public static final String XXX = "xxx";
    /** Listed by .bbbCcc. */
    public static final String YYY_ZZZ = "yyyZzz";
  }


  /**
   * This template has no params.
   */
  public static class HelloSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "examples.abc.hello";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".hello";

    private HelloSoyTemplateInfo() {
      super(
          "examples.abc.hello",
          ImmutableMap.<String, ParamRequisiteness>of(),
          ImmutableSortedSet.<String>of(),
          false,
          false);
    }

    private static final HelloSoyTemplateInfo __INSTANCE__ =
        new HelloSoyTemplateInfo();

    public static HelloSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as HelloSoyTemplateInfo.getInstance(). */
  public static final HelloSoyTemplateInfo HELLO =
      HelloSoyTemplateInfo.getInstance();


  /**
   * Aaa template.
   */
  public static class AaaSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "examples.abc.aaa";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".aaa";

    /** One line description. */
    public static final String XXX = "xxx";
    /** Desc of ppp from aaa. */
    public static final String PPP_1 = "ppp1";

    // Indirect params.
    /** Listed by .bbbCcc, .ddd (private). */
    public static final String QQQ_22 = "qqq22";
    /** Listed by .ddd (private). */
    public static final String RRR_3 = "rrr_3";
    /** Listed by myDelegate (delegate). */
    public static final String SSS = "sss";
    /** Listed by .bbbCcc. */
    public static final String YYY_ZZZ = "yyyZzz";

    private AaaSoyTemplateInfo() {
      super(
          "examples.abc.aaa",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("xxx", ParamRequisiteness.OPTIONAL)
              .put("ppp1", ParamRequisiteness.REQUIRED)
              .put("qqq22", ParamRequisiteness.REQUIRED)
              .put("rrr_3", ParamRequisiteness.REQUIRED)
              .put("sss", ParamRequisiteness.REQUIRED)
              .put("yyyZzz", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of(
              "boo",
              "gooDefault",
              "gooNondefault",
              "moo"),
          false,
          true);
    }

    private static final AaaSoyTemplateInfo __INSTANCE__ =
        new AaaSoyTemplateInfo();

    public static AaaSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as AaaSoyTemplateInfo.getInstance(). */
  public static final AaaSoyTemplateInfo AAA =
      AaaSoyTemplateInfo.getInstance();


  /**
   * Bbb Ccc
   * template.
   */
  public static class BbbCccSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "examples.abc.bbbCcc";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".bbbCcc";

    /**
     * Multiline
     * description.
     */
    public static final String YYY_ZZZ = "yyyZzz";
    /** Desc of ppp from bbbCcc. */
    public static final String PPP_1 = "ppp1";
    /** Desc of qqq from bbbCcc. */
    public static final String QQQ_22 = "qqq22";

    // Indirect params.
    /** Listed by .ddd (private). */
    public static final String RRR_3 = "rrr_3";
    /** Listed by myDelegate (delegate). */
    public static final String SSS = "sss";

    private BbbCccSoyTemplateInfo() {
      super(
          "examples.abc.bbbCcc",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("yyyZzz", ParamRequisiteness.REQUIRED)
              .put("ppp1", ParamRequisiteness.REQUIRED)
              .put("qqq22", ParamRequisiteness.REQUIRED)
              .put("rrr_3", ParamRequisiteness.REQUIRED)
              .put("sss", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of(
              "boo",
              "gooDefault",
              "gooNondefault"),
          false,
          true);
    }

    private static final BbbCccSoyTemplateInfo __INSTANCE__ =
        new BbbCccSoyTemplateInfo();

    public static BbbCccSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as BbbCccSoyTemplateInfo.getInstance(). */
  public static final BbbCccSoyTemplateInfo BBB_CCC =
      BbbCccSoyTemplateInfo.getInstance();


  private ParseInfoIjExampleSoyInfo() {
    super(
        "ParseInfoIjExample.soy",
        "examples.abc",
        ImmutableSortedSet.<String>of(
            Param.PPP_1,
            Param.QQQ_22,
            Param.RRR_3,
            Param.SSS,
            Param.XXX,
            Param.YYY_ZZZ),
        ImmutableList.<SoyTemplateInfo>of(
            HELLO,
            AAA,
            BBB_CCC),
        ImmutableMap.<String, CssTagsPrefixPresence>of());
  }


  private static final ParseInfoIjExampleSoyInfo __INSTANCE__ =
      new ParseInfoIjExampleSoyInfo();

  public static ParseInfoIjExampleSoyInfo getInstance() {
    return __INSTANCE__;
  }

}
