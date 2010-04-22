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

package com.google.template.soy.soytree;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Node representing a template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class TemplateNode extends AbstractParentSoyCommandNode<SoyNode> {


  /**
   * Info about a parameter that's explicitly listed in the SoyDoc.
   */
  public static class SoyDocParam {

    /** The param key. */
    public final String key;

    /** Wehther the param is required. */
    public final boolean isRequired;

    /** The SoyDoc text describing the param. */
    public String desc;

    public SoyDocParam(String key, boolean isRequired, @Nullable String desc) {
      this.key = key;
      this.isRequired = isRequired;
      this.desc = desc;
    }

    /** Clears the param desc text. */
    private void clearDesc() {
      desc = null;
    }
  }


  /** Pattern for a template name not listed as an attribute name="...". */
  private static final Pattern NONATTRIBUTE_TEMPLATE_NAME =
      Pattern.compile("^ (?! name=\") [.\\w]+ (?= \\s | $)", Pattern.COMMENTS);

  /** Pattern for valid template name in V2 syntax. */
  private static final Pattern VALID_V2_NAME = Pattern.compile("[.][a-zA-Z_]\\w*");

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("template",
          new Attribute("name", Attribute.ALLOW_ALL_VALUES,
                        Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("private", Attribute.BOOLEAN_VALUES, "false"),
          new Attribute("override", Sets.newHashSet("true", "false", null), null), // V1
          new Attribute("autoescape", Attribute.BOOLEAN_VALUES, "true"));

  /** Pattern for a newline. */
  private static final Pattern NEWLINE = Pattern.compile("\\n|\\r\\n?");

  /** Pattern for a SoyDoc start token, including spaces up to the first newline.*/
  private static final Pattern SOY_DOC_START =
      Pattern.compile("^ [/][*][*] [\\ ]* \\r?\\n?", Pattern.COMMENTS);

  /** Pattern for a SoyDoc end token, including preceding spaces up to the last newline.*/
  private static final Pattern SOY_DOC_END =
      Pattern.compile("\\r?\\n? [\\ ]* [*][/] $", Pattern.COMMENTS);

  /** Pattern for a SoyDoc @param or @param? tag, including the param key. */
  // group(1) = the @param or @param? tag; group(2) = param key
  private static final Pattern SOY_DOC_PARAM =
      Pattern.compile("(@param [?]?) \\s+ ([a-zA-Z_]\\w*)", Pattern.COMMENTS);


  /** This template's name. */
  private String templateName;

  /** This template's partial name. Only applicable for V2. */
  private final String partialTemplateName;

  /** Whether this template is private. */
  private final boolean isPrivate;

  /** Whether this template overrides another (always false for syntax version V2). */
  private final boolean isOverride;

  /** Whether autoescape is on for this template. */
  private final boolean shouldAutoescape;

  /** The full SoyDoc, including the start/end tokens, or null. */
  private String soyDoc;

  /** The description portion of the SoyDoc (before @param tags), or null. */
  private String soyDocDesc;

  /** The parameters listed in the SoyDoc, or null if no SoyDoc. */
  private List<SoyDocParam> soyDocParams;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null if the template is not
   *     preceded by SoyDoc.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public TemplateNode(String id, String commandText, @Nullable String soyDoc)
      throws SoySyntaxException {
    super(id, "template", commandText);

    // Handle template name not listed as an attribute name="...".
    Matcher ntnMatcher = NONATTRIBUTE_TEMPLATE_NAME.matcher(commandText);
    if (ntnMatcher.find()) {
      commandText = ntnMatcher.replaceFirst("name=\"" + ntnMatcher.group() + "\"");
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandText);

    templateName = attributes.get("name");
    if (!BaseUtils.isDottedIdentifier(templateName)) {
      throw new SoySyntaxException("Invalid template name \"" + templateName + "\".");
    }
    if (VALID_V2_NAME.matcher(templateName).matches()) {
      partialTemplateName = templateName;
    } else {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
      partialTemplateName = null;
    }

    isPrivate = attributes.get("private").equals("true");

    String overrideAttribute = attributes.get("override");
    if (overrideAttribute == null) {
      isOverride = false;
    } else {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
      isOverride = overrideAttribute.equals("true");
    }

    shouldAutoescape = attributes.get("autoescape").equals("true");

    this.soyDoc = soyDoc;
    if (soyDoc != null) {
      Preconditions.checkArgument(soyDoc.startsWith("/**") && soyDoc.endsWith("*/"));
      String cleanedSoyDoc = cleanSoyDoc(soyDoc);
      this.soyDocDesc = parseSoyDocDesc(cleanedSoyDoc);
      this.soyDocParams = parseSoyDocParams(cleanedSoyDoc);
    } else {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
      this.soyDocDesc = null;
      this.soyDocParams = null;
    }
  }


  /**
   * Private helper for the constructor to clean the SoyDoc.
   * (1) Changes all newlines to "\n".
   * (2) Strips the start/end tokens and spaces (including newlines if they occupy their own lines).
   * (3) Removes common indent from all lines (e.g. space-star-space).
   *
   * @param soyDoc The SoyDoc to clean.
   * @return The cleaned SoyDoc.
   */
  private static String cleanSoyDoc(String soyDoc) {

    // Change all newlines to "\n".
    soyDoc = NEWLINE.matcher(soyDoc).replaceAll("\n");

    // Strip start/end tokens and spaces (including newlines if they occupy their own lines).
    soyDoc = SOY_DOC_START.matcher(soyDoc).replaceFirst("");
    soyDoc = SOY_DOC_END.matcher(soyDoc).replaceFirst("");

    // Split into lines.
    List<String> lines = Lists.newArrayList(Splitter.on(NEWLINE).split(soyDoc));

    // Remove indent common to all lines. Note that SoyDoc indents often include a star
    // (specifically the most common indent is space-star-space). Thus, we first remove common
    // spaces, then remove one common star, and finally, if we did remove a star, then we once again
    // remove common spaces.
    removeCommonStartChar(lines, ' ', true);
    if (removeCommonStartChar(lines, '*', false) == 1) {
      removeCommonStartChar(lines, ' ', true);
    }

    return Joiner.on('\n').join(lines);
  }


  /**
   * Private helper for {@code cleanSoyDoc()}.
   * Removes a common character at the start of all lines, either once or as many times as possible.
   *
   * <p> Special case: Empty lines count as if they do have the common character for the purpose of
   * deciding whether all lines have the common character.
   *
   * @param lines The list of lines. If removal happens, then the list elements will be modified.
   * @param charToRemove The char to remove from the start of all lines.
   * @param shouldRemoveMultiple Whether to remove the char as many times as possible.
   * @return The number of chars removed from the start of each line.
   */
  private static int removeCommonStartChar(
      List<String> lines, char charToRemove, boolean shouldRemoveMultiple) {

    int numCharsToRemove = 0;

    // Count num chars to remove.
    boolean isStillCounting = true;
    do {
      boolean areAllLinesEmpty = true;
      for (String line : lines) {
        if (line.length() == 0) {
          continue;  // empty lines are okay
        }
        areAllLinesEmpty = false;
        if (line.length() <= numCharsToRemove ||
            line.charAt(numCharsToRemove) != charToRemove) {
          isStillCounting = false;
          break;
        }
      }
      if (areAllLinesEmpty) {
        isStillCounting = false;
      }
      if (isStillCounting) {
        numCharsToRemove += 1;
      }
    } while (isStillCounting && shouldRemoveMultiple);

    // Perform the removal.
    if (numCharsToRemove > 0) {
      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        if (line.length() == 0) {
          continue;  // don't change empty lines
        }
        lines.set(i, line.substring(numCharsToRemove));
      }
    }

    return numCharsToRemove;
  }


  /**
   * Private helper for the constructor to parse the SoyDoc description.
   *
   * @param cleanedSoyDoc The cleaned SoyDoc text. Must not be null.
   * @return The description (with trailing whitespace removed).
   */
  private static String parseSoyDocDesc(String cleanedSoyDoc) {

    Matcher paramMatcher = SOY_DOC_PARAM.matcher(cleanedSoyDoc);
    int endOfDescPos = (paramMatcher.find()) ? paramMatcher.start() : cleanedSoyDoc.length();
    String soyDocDesc = cleanedSoyDoc.substring(0, endOfDescPos);
    return CharMatcher.WHITESPACE.trimTrailingFrom(soyDocDesc);
  }


  /**
   * Private helper for the constructor to parse the SoyDoc params.
   *
   * @param cleanedSoyDoc The cleaned SoyDoc text. Must not be null.
   * @return The list of params.
   */
  private static List<SoyDocParam> parseSoyDocParams(String cleanedSoyDoc) {

    List<SoyDocParam> soyDocParams = Lists.newArrayList();
    Set<String> seenKeys = Sets.newHashSet();

    Matcher paramMatcher = SOY_DOC_PARAM.matcher(cleanedSoyDoc);
    // Important: This statement finds the param for the first iteration of the loop.
    boolean isFound = paramMatcher.find();
    while (isFound) {

      String key = paramMatcher.group(2);
      if (seenKeys.contains(key)) {
        throw new SoySyntaxException("Duplicate declaration of param in SoyDoc: " + key);
      }
      seenKeys.add(key);

      boolean isRequired;
      if (paramMatcher.group(1).equals("@param")) {
        isRequired = true;
      } else if (paramMatcher.group(1).equals("@param?")) {
        isRequired = false;
      } else {
        throw new AssertionError();
      }

      int descStart = paramMatcher.end();
      // Important: This statement finds the param for the next iteration of the loop.
      // We must find the next param now in order to know where the current param's desc ends.
      isFound = paramMatcher.find();
      int descEnd = (isFound) ? paramMatcher.start() : cleanedSoyDoc.length();
      String desc = cleanedSoyDoc.substring(descStart, descEnd).trim();

      soyDocParams.add(new SoyDocParam(key, isRequired, desc));
    }

    return soyDocParams;
  }


  /**
   * Sets this template's full name (must not be a partial name).
   * @param templateName This template's full name.
   */
  public void setTemplateName(String templateName) {
    Preconditions.checkArgument(
        BaseUtils.isDottedIdentifier(templateName) && templateName.charAt(0) != '.');
    this.templateName = templateName;
  }

  /** Returns this template's name. */
  public String getTemplateName() {
    return templateName;
  }

  /** Returns this template's partial name. Only applicable for V2. */
  public String getPartialTemplateName() {
    return partialTemplateName;
  }

  /** Returns whether this template is private. */
  public boolean isPrivate() {
    return isPrivate;
  }

  /** Returns whether this template overrides another (always false for syntax version V2). */
  public boolean isOverride() {
    return isOverride;
  }

  /** Returns whether autoescape is on for this template. */
  public boolean shouldAutoescape() {
    return shouldAutoescape;
  }

  /** Clears the SoyDoc text, description, and param descriptions. */
  public void clearSoyDocStrings() {
    soyDoc = null;
    soyDocDesc = null;
    for (SoyDocParam param : soyDocParams) {
      param.clearDesc();
    }
  }

  /** Returns the SoyDoc, or null. */
  public String getSoyDoc() {
    return soyDoc;
  }

  /** Returns the description portion of the SoyDoc (before @param tags), or null. */
  public String getSoyDocDesc() {
    return soyDocDesc;
  }

  /** Returns the parameters listed in the SoyDoc, or null if no SoyDoc. */
  public List<SoyDocParam> getSoyDocParams() {
    return soyDocParams;
  }


  @Override public String toSourceString() {

    StringBuilder sb = new StringBuilder();

    if (soyDoc != null) {
      sb.append(soyDoc).append("\n");
    }

    sb.append(getTagString()).append("\n");

    // If first or last char of template body is a space, must be turned into '{sp}'.
    StringBuilder bodySb = new StringBuilder();
    appendSourceStringForChildren(bodySb);
    if (bodySb.charAt(0) == ' ') {
      bodySb.replace(0, 1, "{sp}");
    }
    int bodyLen = bodySb.length();
    if (bodySb.charAt(bodyLen-1) == ' ') {
      bodySb.replace(bodyLen-1, bodyLen, "{sp}");
    }

    sb.append(bodySb);
    sb.append("\n");
    sb.append("{/template}\n");
    return sb.toString();
  }

}
