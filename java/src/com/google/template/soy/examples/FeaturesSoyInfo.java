// This file was automatically generated from features.soy.
// Please don't edit this file by hand.

package com.google.template.soy.examples;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.parseinfo.SoyFileInfo;
import com.google.template.soy.parseinfo.SoyTemplateInfo;


/**
 * Soy parse info for features.soy.
 */
public final class FeaturesSoyInfo extends SoyFileInfo {


  /** This Soy file's namespace. */
  public static final String __NAMESPACE__ = "soy.examples.features";


  public static final class TemplateName {
    private TemplateName() {}

    /** The full template name of the .demoComments template. */
    public static final String DEMO_COMMENTS = "soy.examples.features.demoComments";
    /** The full template name of the .demoLineJoining template. */
    public static final String DEMO_LINE_JOINING = "soy.examples.features.demoLineJoining";
    /** The full template name of the .demoRawTextCommands template. */
    public static final String DEMO_RAW_TEXT_COMMANDS = "soy.examples.features.demoRawTextCommands";
    /** The full template name of the .demoPrint template. */
    public static final String DEMO_PRINT = "soy.examples.features.demoPrint";
    /** The full template name of the .demoAutoescapeTrue template. */
    public static final String DEMO_AUTOESCAPE_TRUE = "soy.examples.features.demoAutoescapeTrue";
    /** The full template name of the .demoMsg template. */
    public static final String DEMO_MSG = "soy.examples.features.demoMsg";
    /** The full template name of the .demoIf template. */
    public static final String DEMO_IF = "soy.examples.features.demoIf";
    /** The full template name of the .demoSwitch template. */
    public static final String DEMO_SWITCH = "soy.examples.features.demoSwitch";
    /** The full template name of the .demoForeach template. */
    public static final String DEMO_FOREACH = "soy.examples.features.demoForeach";
    /** The full template name of the .demoFor template. */
    public static final String DEMO_FOR = "soy.examples.features.demoFor";
    /** The full template name of the .demoCallWithoutParam template. */
    public static final String DEMO_CALL_WITHOUT_PARAM = "soy.examples.features.demoCallWithoutParam";
    /** The full template name of the .demoCallOtherFile template. */
    public static final String DEMO_CALL_OTHER_FILE = "soy.examples.features.demoCallOtherFile";
    /** The full template name of the .demoCallWithParam template. */
    public static final String DEMO_CALL_WITH_PARAM = "soy.examples.features.demoCallWithParam";
    /** The full template name of the .demoCallWithParamBlock template. */
    public static final String DEMO_CALL_WITH_PARAM_BLOCK = "soy.examples.features.demoCallWithParamBlock";
    /** The full template name of the .demoParamWithKindAttribute template. */
    public static final String DEMO_PARAM_WITH_KIND_ATTRIBUTE = "soy.examples.features.demoParamWithKindAttribute";
    /** The full template name of the .demoExpressions template. */
    public static final String DEMO_EXPRESSIONS = "soy.examples.features.demoExpressions";
    /** The full template name of the .demoDoubleBraces template. */
    public static final String DEMO_DOUBLE_BRACES = "soy.examples.features.demoDoubleBraces";
    /** The full template name of the .demoBidiSupport template. */
    public static final String DEMO_BIDI_SUPPORT = "soy.examples.features.demoBidiSupport";
    /** The full template name of the .bidiGlobalDir template. */
    public static final String BIDI_GLOBAL_DIR = "soy.examples.features.bidiGlobalDir";
    /** The full template name of the .exampleHeader template. */
    public static final String EXAMPLE_HEADER = "soy.examples.features.exampleHeader";
  }


  /**
   * Param names from all templates in this Soy file.
   */
  public static final class Param {
    private Param() {}

    /** Listed by .demoBidiSupport. */
    public static final String AUTHOR = "author";
    /** Listed by .demoPrint. */
    public static final String BOO = "boo";
    /** Listed by .demoCallWithParam. */
    public static final String COMPANION_NAME = "companionName";
    /** Listed by .demoExpressions. */
    public static final String CURRENT_YEAR = "currentYear";
    /** Listed by .tripReport_ (private). */
    public static final String DESTINATION = "destination";
    /** Listed by .demoCallWithParam. */
    public static final String DESTINATIONS = "destinations";
    /** Listed by .exampleHeader. */
    public static final String EXAMPLE_NAME = "exampleName";
    /** Listed by .exampleHeader. */
    public static final String EXAMPLE_NUM = "exampleNum";
    /** Listed by .demoAutoescapeTrue. */
    public static final String ITALIC_HTML = "italicHtml";
    /** Listed by .buildCommaSeparatedList_ (private). */
    public static final String ITEMS = "items";
    /** Listed by .demoBidiSupport. */
    public static final String KEYWORDS = "keywords";
    /** Listed by .demoMsg. */
    public static final String LABS_URL = "labsUrl";
    /** Listed by .demoParamWithKindAttribute. */
    public static final String LIST = "list";
    /** Listed by .demoParamWithKindAttributeCallee_ (private). */
    public static final String LIST_ITEMS = "listItems";
    /** Listed by .demoParamWithKindAttribute, .demoParamWithKindAttributeCallee_ (private). */
    public static final String MESSAGE = "message";
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
  public static final class DemoCommentsSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoComments";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoComments";

    private DemoCommentsSoyTemplateInfo() {
      super(
          "soy.examples.features.demoComments",
          ImmutableMap.<String, ParamRequisiteness>of(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoCommentsSoyTemplateInfo __INSTANCE__ =
        new DemoCommentsSoyTemplateInfo();

    public static DemoCommentsSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoCommentsSoyTemplateInfo.getInstance(). */
  public static final DemoCommentsSoyTemplateInfo DEMO_COMMENTS =
      DemoCommentsSoyTemplateInfo.getInstance();


  /**
   * Demo line joining.
   */
  public static final class DemoLineJoiningSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoLineJoining";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoLineJoining";

    private DemoLineJoiningSoyTemplateInfo() {
      super(
          "soy.examples.features.demoLineJoining",
          ImmutableMap.<String, ParamRequisiteness>of(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoLineJoiningSoyTemplateInfo __INSTANCE__ =
        new DemoLineJoiningSoyTemplateInfo();

    public static DemoLineJoiningSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoLineJoiningSoyTemplateInfo.getInstance(). */
  public static final DemoLineJoiningSoyTemplateInfo DEMO_LINE_JOINING =
      DemoLineJoiningSoyTemplateInfo.getInstance();


  /**
   * Demo raw text commands.
   */
  public static final class DemoRawTextCommandsSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoRawTextCommands";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoRawTextCommands";

    private DemoRawTextCommandsSoyTemplateInfo() {
      super(
          "soy.examples.features.demoRawTextCommands",
          ImmutableMap.<String, ParamRequisiteness>of(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoRawTextCommandsSoyTemplateInfo __INSTANCE__ =
        new DemoRawTextCommandsSoyTemplateInfo();

    public static DemoRawTextCommandsSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoRawTextCommandsSoyTemplateInfo.getInstance(). */
  public static final DemoRawTextCommandsSoyTemplateInfo DEMO_RAW_TEXT_COMMANDS =
      DemoRawTextCommandsSoyTemplateInfo.getInstance();


  /**
   * Demo 'print'.
   */
  public static final class DemoPrintSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoPrint";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoPrint";

    /** Something scary. */
    public static final String BOO = "boo";
    /** Preferably the number 2. */
    public static final String TWO = "two";

    private DemoPrintSoyTemplateInfo() {
      super(
          "soy.examples.features.demoPrint",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("boo", ParamRequisiteness.REQUIRED)
              .put("two", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoPrintSoyTemplateInfo __INSTANCE__ =
        new DemoPrintSoyTemplateInfo();

    public static DemoPrintSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoPrintSoyTemplateInfo.getInstance(). */
  public static final DemoPrintSoyTemplateInfo DEMO_PRINT =
      DemoPrintSoyTemplateInfo.getInstance();


  /**
   * Demo autoescape true.
   */
  public static final class DemoAutoescapeTrueSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoAutoescapeTrue";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoAutoescapeTrue";

    /** A string surrounded by HTML italics tags. */
    public static final String ITALIC_HTML = "italicHtml";

    private DemoAutoescapeTrueSoyTemplateInfo() {
      super(
          "soy.examples.features.demoAutoescapeTrue",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("italicHtml", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoAutoescapeTrueSoyTemplateInfo __INSTANCE__ =
        new DemoAutoescapeTrueSoyTemplateInfo();

    public static DemoAutoescapeTrueSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoAutoescapeTrueSoyTemplateInfo.getInstance(). */
  public static final DemoAutoescapeTrueSoyTemplateInfo DEMO_AUTOESCAPE_TRUE =
      DemoAutoescapeTrueSoyTemplateInfo.getInstance();


  /**
   * Demo 'msg'.
   */
  public static final class DemoMsgSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoMsg";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoMsg";

    /** The name of the person to say hello to. */
    public static final String NAME = "name";
    /** The URL of the unreleased 'Labs' feature. */
    public static final String LABS_URL = "labsUrl";

    private DemoMsgSoyTemplateInfo() {
      super(
          "soy.examples.features.demoMsg",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("name", ParamRequisiteness.REQUIRED)
              .put("labsUrl", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoMsgSoyTemplateInfo __INSTANCE__ =
        new DemoMsgSoyTemplateInfo();

    public static DemoMsgSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoMsgSoyTemplateInfo.getInstance(). */
  public static final DemoMsgSoyTemplateInfo DEMO_MSG =
      DemoMsgSoyTemplateInfo.getInstance();


  /**
   * Demo 'if'.
   */
  public static final class DemoIfSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoIf";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoIf";

    /** An approximate value for pi. */
    public static final String PI = "pi";

    private DemoIfSoyTemplateInfo() {
      super(
          "soy.examples.features.demoIf",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("pi", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoIfSoyTemplateInfo __INSTANCE__ =
        new DemoIfSoyTemplateInfo();

    public static DemoIfSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoIfSoyTemplateInfo.getInstance(). */
  public static final DemoIfSoyTemplateInfo DEMO_IF =
      DemoIfSoyTemplateInfo.getInstance();


  /**
   * Demo 'switch'.
   */
  public static final class DemoSwitchSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoSwitch";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoSwitch";

    /** The name of a kid. */
    public static final String NAME = "name";

    private DemoSwitchSoyTemplateInfo() {
      super(
          "soy.examples.features.demoSwitch",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("name", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoSwitchSoyTemplateInfo __INSTANCE__ =
        new DemoSwitchSoyTemplateInfo();

    public static DemoSwitchSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoSwitchSoyTemplateInfo.getInstance(). */
  public static final DemoSwitchSoyTemplateInfo DEMO_SWITCH =
      DemoSwitchSoyTemplateInfo.getInstance();


  /**
   * Demo 'foreach'.
   */
  public static final class DemoForeachSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoForeach";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoForeach";

    /** List of persons. Each person must have 'name' and 'numWaffles'. */
    public static final String PERSONS = "persons";

    private DemoForeachSoyTemplateInfo() {
      super(
          "soy.examples.features.demoForeach",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("persons", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoForeachSoyTemplateInfo __INSTANCE__ =
        new DemoForeachSoyTemplateInfo();

    public static DemoForeachSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoForeachSoyTemplateInfo.getInstance(). */
  public static final DemoForeachSoyTemplateInfo DEMO_FOREACH =
      DemoForeachSoyTemplateInfo.getInstance();


  /**
   * Demo 'for'.
   */
  public static final class DemoForSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoFor";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoFor";

    /** The number of lines to display. */
    public static final String NUM_LINES = "numLines";

    private DemoForSoyTemplateInfo() {
      super(
          "soy.examples.features.demoFor",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("numLines", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoForSoyTemplateInfo __INSTANCE__ =
        new DemoForSoyTemplateInfo();

    public static DemoForSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoForSoyTemplateInfo.getInstance(). */
  public static final DemoForSoyTemplateInfo DEMO_FOR =
      DemoForSoyTemplateInfo.getInstance();


  /**
   * Demo 'call' without 'param's.
   */
  public static final class DemoCallWithoutParamSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoCallWithoutParam";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoCallWithoutParam";

    /** The name of the person who took a trip. */
    public static final String NAME = "name";
    /** The full record of the trip ('name' and 'destination'). */
    public static final String TRIP_INFO = "tripInfo";

    // Indirect params.
    /** Listed by .tripReport_ (private). */
    public static final String DESTINATION = "destination";

    private DemoCallWithoutParamSoyTemplateInfo() {
      super(
          "soy.examples.features.demoCallWithoutParam",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("name", ParamRequisiteness.REQUIRED)
              .put("tripInfo", ParamRequisiteness.REQUIRED)
              .put("destination", ParamRequisiteness.OPTIONAL)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoCallWithoutParamSoyTemplateInfo __INSTANCE__ =
        new DemoCallWithoutParamSoyTemplateInfo();

    public static DemoCallWithoutParamSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoCallWithoutParamSoyTemplateInfo.getInstance(). */
  public static final DemoCallWithoutParamSoyTemplateInfo DEMO_CALL_WITHOUT_PARAM =
      DemoCallWithoutParamSoyTemplateInfo.getInstance();


  /**
   * Demo a 'call' out to a template defined in another file.
   */
  public static final class DemoCallOtherFileSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoCallOtherFile";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoCallOtherFile";

    private DemoCallOtherFileSoyTemplateInfo() {
      super(
          "soy.examples.features.demoCallOtherFile",
          ImmutableMap.<String, ParamRequisiteness>of(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoCallOtherFileSoyTemplateInfo __INSTANCE__ =
        new DemoCallOtherFileSoyTemplateInfo();

    public static DemoCallOtherFileSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoCallOtherFileSoyTemplateInfo.getInstance(). */
  public static final DemoCallOtherFileSoyTemplateInfo DEMO_CALL_OTHER_FILE =
      DemoCallOtherFileSoyTemplateInfo.getInstance();


  /**
   * Demo 'call' with 'param's.
   */
  public static final class DemoCallWithParamSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoCallWithParam";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoCallWithParam";

    /** The name of the person who took the trips. */
    public static final String NAME = "name";
    /** The name of the person who went along for the odd-numbered trips only. */
    public static final String COMPANION_NAME = "companionName";
    /** List of destinations visited by this person. */
    public static final String DESTINATIONS = "destinations";

    private DemoCallWithParamSoyTemplateInfo() {
      super(
          "soy.examples.features.demoCallWithParam",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("name", ParamRequisiteness.REQUIRED)
              .put("companionName", ParamRequisiteness.REQUIRED)
              .put("destinations", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoCallWithParamSoyTemplateInfo __INSTANCE__ =
        new DemoCallWithParamSoyTemplateInfo();

    public static DemoCallWithParamSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoCallWithParamSoyTemplateInfo.getInstance(). */
  public static final DemoCallWithParamSoyTemplateInfo DEMO_CALL_WITH_PARAM =
      DemoCallWithParamSoyTemplateInfo.getInstance();


  /**
   * Demo 'call' with a 'param' block.
   */
  public static final class DemoCallWithParamBlockSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoCallWithParamBlock";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoCallWithParamBlock";

    /** The name of the person who took the trip. */
    public static final String NAME = "name";

    private DemoCallWithParamBlockSoyTemplateInfo() {
      super(
          "soy.examples.features.demoCallWithParamBlock",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("name", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoCallWithParamBlockSoyTemplateInfo __INSTANCE__ =
        new DemoCallWithParamBlockSoyTemplateInfo();

    public static DemoCallWithParamBlockSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoCallWithParamBlockSoyTemplateInfo.getInstance(). */
  public static final DemoCallWithParamBlockSoyTemplateInfo DEMO_CALL_WITH_PARAM_BLOCK =
      DemoCallWithParamBlockSoyTemplateInfo.getInstance();


  /**
   * Demo {param} blocks with 'kind' attribute.
   */
  public static final class DemoParamWithKindAttributeSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoParamWithKindAttribute";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoParamWithKindAttribute";

    /** A message text. */
    public static final String MESSAGE = "message";
    /** A list of things. */
    public static final String LIST = "list";

    private DemoParamWithKindAttributeSoyTemplateInfo() {
      super(
          "soy.examples.features.demoParamWithKindAttribute",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("message", ParamRequisiteness.REQUIRED)
              .put("list", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoParamWithKindAttributeSoyTemplateInfo __INSTANCE__ =
        new DemoParamWithKindAttributeSoyTemplateInfo();

    public static DemoParamWithKindAttributeSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoParamWithKindAttributeSoyTemplateInfo.getInstance(). */
  public static final DemoParamWithKindAttributeSoyTemplateInfo DEMO_PARAM_WITH_KIND_ATTRIBUTE =
      DemoParamWithKindAttributeSoyTemplateInfo.getInstance();


  /**
   * Demo expressions.
   */
  public static final class DemoExpressionsSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoExpressions";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoExpressions";

    /** Nonempty list of students. Each student must have 'name', 'major', and 'year'. */
    public static final String STUDENTS = "students";
    /** The current year. */
    public static final String CURRENT_YEAR = "currentYear";

    private DemoExpressionsSoyTemplateInfo() {
      super(
          "soy.examples.features.demoExpressions",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("students", ParamRequisiteness.REQUIRED)
              .put("currentYear", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoExpressionsSoyTemplateInfo __INSTANCE__ =
        new DemoExpressionsSoyTemplateInfo();

    public static DemoExpressionsSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoExpressionsSoyTemplateInfo.getInstance(). */
  public static final DemoExpressionsSoyTemplateInfo DEMO_EXPRESSIONS =
      DemoExpressionsSoyTemplateInfo.getInstance();


  /**
   * Demo double braces.
   */
  public static final class DemoDoubleBracesSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoDoubleBraces";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoDoubleBraces";

    /** The name of the infinite set. */
    public static final String SET_NAME = "setName";
    /** List of the first few members of the set. */
    public static final String SET_MEMBERS = "setMembers";

    private DemoDoubleBracesSoyTemplateInfo() {
      super(
          "soy.examples.features.demoDoubleBraces",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("setName", ParamRequisiteness.REQUIRED)
              .put("setMembers", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final DemoDoubleBracesSoyTemplateInfo __INSTANCE__ =
        new DemoDoubleBracesSoyTemplateInfo();

    public static DemoDoubleBracesSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoDoubleBracesSoyTemplateInfo.getInstance(). */
  public static final DemoDoubleBracesSoyTemplateInfo DEMO_DOUBLE_BRACES =
      DemoDoubleBracesSoyTemplateInfo.getInstance();


  /**
   * Demo BiDi support.
   */
  public static final class DemoBidiSupportSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.demoBidiSupport";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".demoBidiSupport";

    /** Book title. */
    public static final String TITLE = "title";
    /** Author's name. */
    public static final String AUTHOR = "author";
    /** Year published. */
    public static final String YEAR = "year";
    /** List of keywords. */
    public static final String KEYWORDS = "keywords";

    private DemoBidiSupportSoyTemplateInfo() {
      super(
          "soy.examples.features.demoBidiSupport",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("title", ParamRequisiteness.REQUIRED)
              .put("author", ParamRequisiteness.REQUIRED)
              .put("year", ParamRequisiteness.REQUIRED)
              .put("keywords", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of(
              "csp_nonce"));
    }

    private static final DemoBidiSupportSoyTemplateInfo __INSTANCE__ =
        new DemoBidiSupportSoyTemplateInfo();

    public static DemoBidiSupportSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as DemoBidiSupportSoyTemplateInfo.getInstance(). */
  public static final DemoBidiSupportSoyTemplateInfo DEMO_BIDI_SUPPORT =
      DemoBidiSupportSoyTemplateInfo.getInstance();


  /**
   * Template that outputs -1 in a right-to-left page and 1 in a left-to-right page, i.e. basically
   * exposes the results of Soy's bidiGlobalDir() to scripts.
   */
  public static final class BidiGlobalDirSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.bidiGlobalDir";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".bidiGlobalDir";

    private BidiGlobalDirSoyTemplateInfo() {
      super(
          "soy.examples.features.bidiGlobalDir",
          ImmutableMap.<String, ParamRequisiteness>of(),
          ImmutableSortedSet.<String>of());
    }

    private static final BidiGlobalDirSoyTemplateInfo __INSTANCE__ =
        new BidiGlobalDirSoyTemplateInfo();

    public static BidiGlobalDirSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as BidiGlobalDirSoyTemplateInfo.getInstance(). */
  public static final BidiGlobalDirSoyTemplateInfo BIDI_GLOBAL_DIR =
      BidiGlobalDirSoyTemplateInfo.getInstance();


  /**
   * Template for printing the header to add before each example.
   */
  public static final class ExampleHeaderSoyTemplateInfo extends SoyTemplateInfo {

    /** This template's full name. */
    public static final String __NAME__ = "soy.examples.features.exampleHeader";
    /** This template's partial name. */
    public static final String __PARTIAL_NAME__ = ".exampleHeader";

    /** The number of the example. */
    public static final String EXAMPLE_NUM = "exampleNum";
    /** The name of the example. */
    public static final String EXAMPLE_NAME = "exampleName";

    private ExampleHeaderSoyTemplateInfo() {
      super(
          "soy.examples.features.exampleHeader",
          ImmutableMap.<String, ParamRequisiteness>builder()
              .put("exampleNum", ParamRequisiteness.REQUIRED)
              .put("exampleName", ParamRequisiteness.REQUIRED)
              .build(),
          ImmutableSortedSet.<String>of());
    }

    private static final ExampleHeaderSoyTemplateInfo __INSTANCE__ =
        new ExampleHeaderSoyTemplateInfo();

    public static ExampleHeaderSoyTemplateInfo getInstance() {
      return __INSTANCE__;
    }
  }

  /** Same as ExampleHeaderSoyTemplateInfo.getInstance(). */
  public static final ExampleHeaderSoyTemplateInfo EXAMPLE_HEADER =
      ExampleHeaderSoyTemplateInfo.getInstance();


  private FeaturesSoyInfo() {
    super(
        "features.soy",
        "soy.examples.features",
        ImmutableList.<SoyTemplateInfo>of(
            DEMO_COMMENTS,
            DEMO_LINE_JOINING,
            DEMO_RAW_TEXT_COMMANDS,
            DEMO_PRINT,
            DEMO_AUTOESCAPE_TRUE,
            DEMO_MSG,
            DEMO_IF,
            DEMO_SWITCH,
            DEMO_FOREACH,
            DEMO_FOR,
            DEMO_CALL_WITHOUT_PARAM,
            DEMO_CALL_OTHER_FILE,
            DEMO_CALL_WITH_PARAM,
            DEMO_CALL_WITH_PARAM_BLOCK,
            DEMO_PARAM_WITH_KIND_ATTRIBUTE,
            DEMO_EXPRESSIONS,
            DEMO_DOUBLE_BRACES,
            DEMO_BIDI_SUPPORT,
            BIDI_GLOBAL_DIR,
            EXAMPLE_HEADER),
        ImmutableMap.<String, CssTagsPrefixPresence>of(),
        ImmutableList.<String>of());
  }


  private static final FeaturesSoyInfo __INSTANCE__ =
      new FeaturesSoyInfo();

  public static FeaturesSoyInfo getInstance() {
    return __INSTANCE__;
  }

}
