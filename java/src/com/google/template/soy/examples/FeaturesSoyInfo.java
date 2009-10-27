// This file was automatically generated from features.soy.
// Please don't edit this file by hand.

package com.google.template.soy.examples;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.parseinfo.SoyFileInfo;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.parseinfo.SoyTemplateInfo.ParamRequisiteness;
import static com.google.template.soy.parseinfo.SoyTemplateInfo.ParamRequisiteness.OPTIONAL;
import static com.google.template.soy.parseinfo.SoyTemplateInfo.ParamRequisiteness.REQUIRED;


/**
 * Soy parse info for features.soy.
 */
public class FeaturesSoyInfo {

  private FeaturesSoyInfo() {}


  public static class Param {
    private Param() {}

    /** Listed by .demoBidiSupport. */
    public static final String AUTHOR = "author";
    /** Listed by .demoPrint. */
    public static final String BOO = "boo";
    /** Listed by .demoCallWithParam. */
    public static final String COMPANION_NAME = "companionName";
    /** Listed by .demoPrintDirectives. */
    public static final String CSS_CLASS = "cssClass";
    /** Listed by .demoExpressions. */
    public static final String CURRENT_YEAR = "currentYear";
    /** Listed by .tripReport_ (private). */
    public static final String DESTINATION = "destination";
    /** Listed by .demoCallWithParam. */
    public static final String DESTINATIONS = "destinations";
    /** Listed by .demoPrintDirectives. */
    public static final String ELEMENT_ID = "elementId";
    /** Listed by .exampleHeader. */
    public static final String EXAMPLE_NAME = "exampleName";
    /** Listed by .exampleHeader. */
    public static final String EXAMPLE_NUM = "exampleNum";
    /** Listed by .demoAutoescapeTrue, .demoAutoescapeFalse. */
    public static final String ITALIC_HTML = "italicHtml";
    /** Listed by .buildCommaSeparatedList_ (private). */
    public static final String ITEMS = "items";
    /** Listed by .demoBidiSupport. */
    public static final String KEYWORDS = "keywords";
    /** Listed by .demoMsg. */
    public static final String LABS_URL = "labsUrl";
    /** Listed by .demoPrintDirectives. */
    public static final String LONG_VAR_NAME = "longVarName";
    /**
     * Listed by .demoMsg, .demoSwitch, .demoCallWithoutParam, .demoCallWithParam,
     * .demoCallWithParamBlock, .tripReport_ (private).
     */
    public static final String NAME = "name";
    /** Listed by .demoFor. */
    public static final String NUM_LINES = "numLines";
    /** Listed by .demoForeach. */
    public static final String PERSONS = "persons";
    /** Listed by .demoIf. */
    public static final String PI = "pi";
    /** Listed by .demoDoubleBraces. */
    public static final String SET_MEMBERS = "setMembers";
    /** Listed by .demoDoubleBraces. */
    public static final String SET_NAME = "setName";
    /** Listed by .demoExpressions. */
    public static final String STUDENTS = "students";
    /** Listed by .demoBidiSupport. */
    public static final String TITLE = "title";
    /** Listed by .demoCallWithoutParam. */
    public static final String TRIP_INFO = "tripInfo";
    /** Listed by .demoPrint. */
    public static final String TWO = "two";
    /** Listed by .demoBidiSupport. */
    public static final String YEAR = "year";
  }


  /**
   * Demo comments.
   */
  public static final SoyTemplateInfo DEMO_COMMENTS = new SoyTemplateInfo(
      "soy.examples.features.demoComments",
      ImmutableMap.<String, ParamRequisiteness>of());


  /**
   * Demo line joining.
   */
  public static final SoyTemplateInfo DEMO_LINE_JOINING = new SoyTemplateInfo(
      "soy.examples.features.demoLineJoining",
      ImmutableMap.<String, ParamRequisiteness>of());


  /**
   * Demo raw text commands.
   */
  public static final SoyTemplateInfo DEMO_RAW_TEXT_COMMANDS = new SoyTemplateInfo(
      "soy.examples.features.demoRawTextCommands",
      ImmutableMap.<String, ParamRequisiteness>of());


  /**
   * Demo 'print'.
   */
  public static final DemoPrintSoyTemplateInfo DEMO_PRINT =
      new DemoPrintSoyTemplateInfo();

  public static class DemoPrintSoyTemplateInfo extends SoyTemplateInfo {
    private DemoPrintSoyTemplateInfo() {
      super("soy.examples.features.demoPrint",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("boo", REQUIRED)
            .put("two", REQUIRED)
            .build());
    }

    /** Something scary. */
    public final String BOO = "boo";

    /** Preferably the number 2. */
    public final String TWO = "two";
  }


  /**
   * Demo print directives.
   */
  public static final DemoPrintDirectivesSoyTemplateInfo DEMO_PRINT_DIRECTIVES =
      new DemoPrintDirectivesSoyTemplateInfo();

  public static class DemoPrintDirectivesSoyTemplateInfo extends SoyTemplateInfo {
    private DemoPrintDirectivesSoyTemplateInfo() {
      super("soy.examples.features.demoPrintDirectives",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("longVarName", REQUIRED)
            .put("elementId", REQUIRED)
            .put("cssClass", REQUIRED)
            .build());
    }

    /** Some ridiculously long variable name. */
    public final String LONG_VAR_NAME = "longVarName";

    /** The id for an element. */
    public final String ELEMENT_ID = "elementId";

    /** A CSS class name. */
    public final String CSS_CLASS = "cssClass";
  }


  /**
   * Demo autoescape true.
   */
  public static final DemoAutoescapeTrueSoyTemplateInfo DEMO_AUTOESCAPE_TRUE =
      new DemoAutoescapeTrueSoyTemplateInfo();

  public static class DemoAutoescapeTrueSoyTemplateInfo extends SoyTemplateInfo {
    private DemoAutoescapeTrueSoyTemplateInfo() {
      super("soy.examples.features.demoAutoescapeTrue",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("italicHtml", REQUIRED)
            .build());
    }

    /** A string surrounded by HTML italics tags. */
    public final String ITALIC_HTML = "italicHtml";
  }


  /**
   * Demo autoescape false.
   */
  public static final DemoAutoescapeFalseSoyTemplateInfo DEMO_AUTOESCAPE_FALSE =
      new DemoAutoescapeFalseSoyTemplateInfo();

  public static class DemoAutoescapeFalseSoyTemplateInfo extends SoyTemplateInfo {
    private DemoAutoescapeFalseSoyTemplateInfo() {
      super("soy.examples.features.demoAutoescapeFalse",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("italicHtml", REQUIRED)
            .build());
    }

    /** A string surrounded by HTML italics tags. */
    public final String ITALIC_HTML = "italicHtml";
  }


  /**
   * Demo 'msg'.
   */
  public static final DemoMsgSoyTemplateInfo DEMO_MSG =
      new DemoMsgSoyTemplateInfo();

  public static class DemoMsgSoyTemplateInfo extends SoyTemplateInfo {
    private DemoMsgSoyTemplateInfo() {
      super("soy.examples.features.demoMsg",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("name", REQUIRED)
            .put("labsUrl", REQUIRED)
            .build());
    }

    /** The name of the person to say hello to. */
    public final String NAME = "name";

    /** The URL of the unreleased 'Labs' feature. */
    public final String LABS_URL = "labsUrl";
  }


  /**
   * Demo 'if'.
   */
  public static final DemoIfSoyTemplateInfo DEMO_IF =
      new DemoIfSoyTemplateInfo();

  public static class DemoIfSoyTemplateInfo extends SoyTemplateInfo {
    private DemoIfSoyTemplateInfo() {
      super("soy.examples.features.demoIf",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("pi", REQUIRED)
            .build());
    }

    /** An approximate value for pi. */
    public final String PI = "pi";
  }


  /**
   * Demo 'switch'.
   */
  public static final DemoSwitchSoyTemplateInfo DEMO_SWITCH =
      new DemoSwitchSoyTemplateInfo();

  public static class DemoSwitchSoyTemplateInfo extends SoyTemplateInfo {
    private DemoSwitchSoyTemplateInfo() {
      super("soy.examples.features.demoSwitch",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("name", REQUIRED)
            .build());
    }

    /** The name of a kid. */
    public final String NAME = "name";
  }


  /**
   * Demo 'foreach'.
   */
  public static final DemoForeachSoyTemplateInfo DEMO_FOREACH =
      new DemoForeachSoyTemplateInfo();

  public static class DemoForeachSoyTemplateInfo extends SoyTemplateInfo {
    private DemoForeachSoyTemplateInfo() {
      super("soy.examples.features.demoForeach",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("persons", REQUIRED)
            .build());
    }

    /** List of persons. Each person must have 'name' and 'numWaffles'. */
    public final String PERSONS = "persons";
  }


  /**
   * Demo 'for'.
   */
  public static final DemoForSoyTemplateInfo DEMO_FOR =
      new DemoForSoyTemplateInfo();

  public static class DemoForSoyTemplateInfo extends SoyTemplateInfo {
    private DemoForSoyTemplateInfo() {
      super("soy.examples.features.demoFor",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("numLines", REQUIRED)
            .build());
    }

    /** The number of lines to display. */
    public final String NUM_LINES = "numLines";
  }


  /**
   * Demo 'call' without 'param's.
   */
  public static final DemoCallWithoutParamSoyTemplateInfo DEMO_CALL_WITHOUT_PARAM =
      new DemoCallWithoutParamSoyTemplateInfo();

  public static class DemoCallWithoutParamSoyTemplateInfo extends SoyTemplateInfo {
    private DemoCallWithoutParamSoyTemplateInfo() {
      super("soy.examples.features.demoCallWithoutParam",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("name", REQUIRED)
            .put("tripInfo", REQUIRED)
            .put("destination", OPTIONAL)
            .build());
    }

    /** The name of the person who took a trip. */
    public final String NAME = "name";

    /** The full record of the trip ('name' and 'destination'). */
    public final String TRIP_INFO = "tripInfo";

    // Indirect params.
    /** Listed by .tripReport_ (private). */
    public final String DESTINATION = "destination";
  }


  /**
   * Demo 'call' with 'param's.
   */
  public static final DemoCallWithParamSoyTemplateInfo DEMO_CALL_WITH_PARAM =
      new DemoCallWithParamSoyTemplateInfo();

  public static class DemoCallWithParamSoyTemplateInfo extends SoyTemplateInfo {
    private DemoCallWithParamSoyTemplateInfo() {
      super("soy.examples.features.demoCallWithParam",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("name", REQUIRED)
            .put("companionName", REQUIRED)
            .put("destinations", REQUIRED)
            .build());
    }

    /** The name of the person who took the trips. */
    public final String NAME = "name";

    /** The name of the person who went along for the odd-numbered trips only. */
    public final String COMPANION_NAME = "companionName";

    /** List of destinations visited by this person. */
    public final String DESTINATIONS = "destinations";
  }


  /**
   * Demo 'call' with a 'param' block.
   */
  public static final DemoCallWithParamBlockSoyTemplateInfo DEMO_CALL_WITH_PARAM_BLOCK =
      new DemoCallWithParamBlockSoyTemplateInfo();

  public static class DemoCallWithParamBlockSoyTemplateInfo extends SoyTemplateInfo {
    private DemoCallWithParamBlockSoyTemplateInfo() {
      super("soy.examples.features.demoCallWithParamBlock",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("name", REQUIRED)
            .build());
    }

    /** The name of the person who took the trip. */
    public final String NAME = "name";
  }


  /**
   * Demo expressions.
   */
  public static final DemoExpressionsSoyTemplateInfo DEMO_EXPRESSIONS =
      new DemoExpressionsSoyTemplateInfo();

  public static class DemoExpressionsSoyTemplateInfo extends SoyTemplateInfo {
    private DemoExpressionsSoyTemplateInfo() {
      super("soy.examples.features.demoExpressions",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("students", REQUIRED)
            .put("currentYear", REQUIRED)
            .build());
    }

    /** Nonempty list of students. Each student must have 'name', 'major', and 'year'. */
    public final String STUDENTS = "students";

    /** The current year. */
    public final String CURRENT_YEAR = "currentYear";
  }


  /**
   * Demo double braces.
   */
  public static final DemoDoubleBracesSoyTemplateInfo DEMO_DOUBLE_BRACES =
      new DemoDoubleBracesSoyTemplateInfo();

  public static class DemoDoubleBracesSoyTemplateInfo extends SoyTemplateInfo {
    private DemoDoubleBracesSoyTemplateInfo() {
      super("soy.examples.features.demoDoubleBraces",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("setName", REQUIRED)
            .put("setMembers", REQUIRED)
            .build());
    }

    /** The name of the infinite set. */
    public final String SET_NAME = "setName";

    /** List of the first few members of the set. */
    public final String SET_MEMBERS = "setMembers";
  }


  /**
   * Demo BiDi support.
   */
  public static final DemoBidiSupportSoyTemplateInfo DEMO_BIDI_SUPPORT =
      new DemoBidiSupportSoyTemplateInfo();

  public static class DemoBidiSupportSoyTemplateInfo extends SoyTemplateInfo {
    private DemoBidiSupportSoyTemplateInfo() {
      super("soy.examples.features.demoBidiSupport",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("title", REQUIRED)
            .put("author", REQUIRED)
            .put("year", REQUIRED)
            .put("keywords", REQUIRED)
            .build());
    }

    /** Book title. */
    public final String TITLE = "title";

    /** Author's name. */
    public final String AUTHOR = "author";

    /** Year published. */
    public final String YEAR = "year";

    /** List of keywords. */
    public final String KEYWORDS = "keywords";
  }


  /**
   * Template that outputs -1 in a right-to-left page and 1 in a left-to-right page, i.e. basically
   * exposes the results of Soy's bidiGlobalDir() to scripts.
   */
  public static final SoyTemplateInfo BIDI_GLOBAL_DIR = new SoyTemplateInfo(
      "soy.examples.features.bidiGlobalDir",
      ImmutableMap.<String, ParamRequisiteness>of());


  /**
   * Template for printing the header to add before each example.
   */
  public static final ExampleHeaderSoyTemplateInfo EXAMPLE_HEADER =
      new ExampleHeaderSoyTemplateInfo();

  public static class ExampleHeaderSoyTemplateInfo extends SoyTemplateInfo {
    private ExampleHeaderSoyTemplateInfo() {
      super("soy.examples.features.exampleHeader",
            ImmutableMap.<String, ParamRequisiteness>builder()
            .put("exampleNum", REQUIRED)
            .put("exampleName", REQUIRED)
            .build());
    }

    /** The number of the example. */
    public final String EXAMPLE_NUM = "exampleNum";

    /** The name of the example. */
    public final String EXAMPLE_NAME = "exampleName";
  }


  private static final SoyFileInfo __SOY_FILE_INFO__ = new SoyFileInfo(
      "features.soy",
      "soy.examples.features",
      ImmutableSortedSet.of(
          Param.AUTHOR,
          Param.BOO,
          Param.COMPANION_NAME,
          Param.CSS_CLASS,
          Param.CURRENT_YEAR,
          Param.DESTINATION,
          Param.DESTINATIONS,
          Param.ELEMENT_ID,
          Param.EXAMPLE_NAME,
          Param.EXAMPLE_NUM,
          Param.ITALIC_HTML,
          Param.ITEMS,
          Param.KEYWORDS,
          Param.LABS_URL,
          Param.LONG_VAR_NAME,
          Param.NAME,
          Param.NUM_LINES,
          Param.PERSONS,
          Param.PI,
          Param.SET_MEMBERS,
          Param.SET_NAME,
          Param.STUDENTS,
          Param.TITLE,
          Param.TRIP_INFO,
          Param.TWO,
          Param.YEAR),
      ImmutableList.<SoyTemplateInfo>of(
          DEMO_COMMENTS,
          DEMO_LINE_JOINING,
          DEMO_RAW_TEXT_COMMANDS,
          DEMO_PRINT,
          DEMO_PRINT_DIRECTIVES,
          DEMO_AUTOESCAPE_TRUE,
          DEMO_AUTOESCAPE_FALSE,
          DEMO_MSG,
          DEMO_IF,
          DEMO_SWITCH,
          DEMO_FOREACH,
          DEMO_FOR,
          DEMO_CALL_WITHOUT_PARAM,
          DEMO_CALL_WITH_PARAM,
          DEMO_CALL_WITH_PARAM_BLOCK,
          DEMO_EXPRESSIONS,
          DEMO_DOUBLE_BRACES,
          DEMO_BIDI_SUPPORT,
          BIDI_GLOBAL_DIR,
          EXAMPLE_HEADER));


  public static SoyFileInfo getFileInfo() {
    return __SOY_FILE_INFO__;
  }

}
