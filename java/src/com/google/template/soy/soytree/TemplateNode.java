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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.base.Pair;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;


/**
 * Node representing a template.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class TemplateNode extends AbstractBlockCommandNode {


  /** Priorities range from 0 to MAX_PRIORITY, inclusive. */
  public static final int MAX_PRIORITY = 1;


  /**
   * Info from the containing Soy file's {@code delpackage} and {@code namespace} declarations.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   *
   * <p> Note: Currently, there are only 2 delegate priority values: 0 and 1. Delegate templates
   * that are not in a delegate package are given priority 0 (lowest). Delegate templates in a
   * delegate package are given priority 1. There is currently no syntax for the user to override
   * these default priority values.
   */
  @Immutable
  public static class SoyFileHeaderInfo {

    @Nullable public final String delPackageName;
    public final int defaultDelPriority;
    public final String namespace;
    public final AutoescapeMode defaultAutoescapeMode;

    public SoyFileHeaderInfo(SoyFileNode soyFileNode) {
      this(soyFileNode.getDelPackageName(), soyFileNode.getNamespace(),
          soyFileNode.getDefaultAutoescapeMode());
    }

    public SoyFileHeaderInfo(String namespace) {
      this(null, namespace, AutoescapeMode.TRUE);
    }

    public SoyFileHeaderInfo(
        String delPackageName, String namespace, AutoescapeMode defaultAutoescapeMode) {
      this.delPackageName = delPackageName;
      this.defaultDelPriority = (delPackageName == null) ? 0 : 1;
      this.namespace = namespace;
      this.defaultAutoescapeMode = defaultAutoescapeMode;
    }
  }


  /**
   * Private helper class used by constructors. Encapsulates all the info derived from the command
   * text.
   */
  @Immutable
  protected static class CommandTextInfo {

    public final String commandText;
    public final String templateName;
    @Nullable public final String partialTemplateName;
    public final boolean isPrivate;
    public final AutoescapeMode autoescapeMode;
    public final SyntaxVersion syntaxVersion;

    public CommandTextInfo(
        String commandText, String templateName, @Nullable String partialTemplateName,
        boolean isPrivate, AutoescapeMode autoescapeMode, SyntaxVersion syntaxVersion) {
      Preconditions.checkArgument(BaseUtils.isDottedIdentifier(templateName));
      Preconditions.checkArgument(
          partialTemplateName == null || BaseUtils.isIdentifierWithLeadingDot(partialTemplateName));
      this.commandText = commandText;
      this.templateName = templateName;
      this.partialTemplateName = partialTemplateName;
      this.isPrivate = isPrivate;
      this.autoescapeMode = autoescapeMode;
      this.syntaxVersion = syntaxVersion;
    }
  }


  /**
   * Abstract base class for a SoyDoc declaration.
   */
  @Immutable
  private abstract static class SoyDocDecl {

    /** The SoyDoc text describing the declaration. */
    @Nullable public final String desc;

    private SoyDocDecl(@Nullable String desc) {
      this.desc = desc;
    }
  }


  /**
   * Info for a parameter declaration in the SoyDoc.
   */
  @Immutable
  public static final class SoyDocParam extends SoyDocDecl {

    /** The param key. */
    public final String key;

    /** Wehther the param is required. */
    public final boolean isRequired;

    public SoyDocParam(String key, boolean isRequired, @Nullable String desc) {
      super(desc);
      Preconditions.checkArgument(key != null);
      this.key = key;
      this.isRequired = isRequired;
    }

    @Override public boolean equals(Object o) {
      if (! (o instanceof SoyDocParam)) { return false; }
      SoyDocParam other = (SoyDocParam) o;
      return this.key.equals(other.key) && this.isRequired == other.isRequired;
    }

    @Override public int hashCode() {
      return key.hashCode() + (isRequired ? 1 : 0);
    }
  }


  /** Pattern for a newline. */
  private static final Pattern NEWLINE = Pattern.compile("\\n|\\r\\n?");

  /** Pattern for a SoyDoc start token, including spaces up to the first newline.*/
  private static final Pattern SOY_DOC_START =
      Pattern.compile("^ [/][*][*] [\\ ]* \\r?\\n?", Pattern.COMMENTS);

  /** Pattern for a SoyDoc end token, including preceding spaces up to the last newline.*/
  private static final Pattern SOY_DOC_END =
      Pattern.compile("\\r?\\n? [\\ ]* [*][/] $", Pattern.COMMENTS);

  /** Pattern for a SoyDoc declaration. */
  // group(1) = declaration keyword; group(2) = declaration text.
  private static final Pattern SOY_DOC_DECL_PATTERN =
      Pattern.compile("( @param[?]? ) \\s+ ( \\S+ )", Pattern.COMMENTS);

  /** Pattern for SoyDoc parameter declaration text. */
  private static final Pattern SOY_DOC_PARAM_TEXT_PATTERN =
      Pattern.compile("[a-zA-Z_]\\w*", Pattern.COMMENTS);


  /** Info from the containing Soy file's header declarations. */
  private final SoyFileHeaderInfo soyFileHeaderInfo;

  /** This template's name. */
  private final String templateName;

  /** This template's partial name. Only applicable for V2. */
  @Nullable private final String partialTemplateName;

  /** Whether this template is private. */
  private final boolean isPrivate;

  /** The kind of autoescaping, if any, done for this template. */
  private final AutoescapeMode autoescapeMode;

  /** The full SoyDoc, including the start/end tokens, or null. */
  private String soyDoc;

  /** The description portion of the SoyDoc (before declarations), or null. */
  private String soyDocDesc;

  /** The parameters listed in the SoyDoc, or null if no SoyDoc. */
  private ImmutableList<SoyDocParam> soyDocParams;


  /**
   * Protected constructor for use by subclasses.
   *
   * @param id The id for this node.
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param commandName The command name, either {@code template} or {@code deltemplate}.
   * @param commandTextInfo All the info derived from the command text.
   * @param soyDoc The full SoyDoc, including the start/end tokens, or null if the template is not
   */
  protected TemplateNode(
      int id, SoyFileHeaderInfo soyFileHeaderInfo, String commandName,
      CommandTextInfo commandTextInfo, @Nullable String soyDoc) {

    super(id, commandName, commandTextInfo.commandText);

    this.soyFileHeaderInfo = soyFileHeaderInfo;

    this.templateName = commandTextInfo.templateName;
    this.partialTemplateName = commandTextInfo.partialTemplateName;
    this.isPrivate = commandTextInfo.isPrivate;
    this.autoescapeMode = commandTextInfo.autoescapeMode;
    maybeSetSyntaxVersion(commandTextInfo.syntaxVersion);

    this.soyDoc = soyDoc;
    if (soyDoc != null) {
      Preconditions.checkArgument(soyDoc.startsWith("/**") && soyDoc.endsWith("*/"));
      String cleanedSoyDoc = cleanSoyDocHelper(soyDoc);
      this.soyDocDesc = parseSoyDocDescHelper(cleanedSoyDoc);
      Pair<Boolean, List<SoyDocParam>> soyDocParamsInfo = parseSoyDocDeclsHelper(cleanedSoyDoc);
      this.soyDocParams = ImmutableList.copyOf(soyDocParamsInfo.second);
      if (soyDocParamsInfo.first) {
        maybeSetSyntaxVersion(SyntaxVersion.V1);
      }
    } else {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
      this.soyDocDesc = null;
      this.soyDocParams = null;
    }

    // Check template name.
    if (partialTemplateName != null) {
      if (! BaseUtils.isIdentifierWithLeadingDot(partialTemplateName)) {
        throw new SoySyntaxException("Invalid template name \"" + partialTemplateName + "\".");
      }
    } else {
      if (! BaseUtils.isDottedIdentifier(templateName)) {
        throw new SoySyntaxException("Invalid template name \"" + templateName + "\".");
      }
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
  private static String cleanSoyDocHelper(String soyDoc) {

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
    removeCommonStartCharHelper(lines, ' ', true);
    if (removeCommonStartCharHelper(lines, '*', false) == 1) {
      removeCommonStartCharHelper(lines, ' ', true);
    }

    return Joiner.on('\n').join(lines);
  }


  /**
   * Private helper for {@code cleanSoyDocHelper()}.
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
  private static int removeCommonStartCharHelper(
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
  private static String parseSoyDocDescHelper(String cleanedSoyDoc) {

    Matcher paramMatcher = SOY_DOC_DECL_PATTERN.matcher(cleanedSoyDoc);
    int endOfDescPos = (paramMatcher.find()) ? paramMatcher.start() : cleanedSoyDoc.length();
    String soyDocDesc = cleanedSoyDoc.substring(0, endOfDescPos);
    return CharMatcher.WHITESPACE.trimTrailingFrom(soyDocDesc);
  }


  /**
   * Private helper for the constructor to parse the SoyDoc declarations.
   *
   * @param cleanedSoyDoc The cleaned SoyDoc text. Must not be null.
   * @return Pair of (whether there are any params in incorrect syntax, the list of parameters).
   */
  private static Pair<Boolean, List<SoyDocParam>> parseSoyDocDeclsHelper(String cleanedSoyDoc) {

    boolean hasParamsWithIncorrectSyntax = false;
    List<SoyDocParam> soyDocParams = Lists.newArrayList();

    Set<String> seenParamKeys = Sets.newHashSet();

    Matcher matcher = SOY_DOC_DECL_PATTERN.matcher(cleanedSoyDoc);
    // Important: This statement finds the param for the first iteration of the loop.
    boolean isFound = matcher.find();
    while (isFound) {

      // Save the match groups.
      String declKeyword = matcher.group(1);
      String declText = matcher.group(2);

      // Find the next declaration in the SoyDoc and extract this declaration's desc string.
      int descStart = matcher.end();
      // Important: This statement finds the param for the next iteration of the loop.
      // We must find the next param now in order to know where the current param's desc ends.
      isFound = matcher.find();
      int descEnd = (isFound) ? matcher.start() : cleanedSoyDoc.length();
      String desc = cleanedSoyDoc.substring(descStart, descEnd).trim();

      if (declKeyword.equals("@param") || declKeyword.equals("@param?")) {
        if (! SOY_DOC_PARAM_TEXT_PATTERN.matcher(declText).matches()) {
          if (declText.startsWith("{")) {
            hasParamsWithIncorrectSyntax = true;
            continue;  // for now, allow incorrect syntax where '{' is the start of the declText
          } else {
            throw new SoySyntaxException(
                "Invalid SoyDoc declaration \"" + declKeyword + " " + declText + "\".");
          }
        }
        if (declText.equals("ij")) {
          throw new SoySyntaxException("Invalid param name 'ij' ('ij' is for injected data ref).");
        }
        if (seenParamKeys.contains(declText)) {
          throw new SoySyntaxException(
              "Duplicate declaration of param in SoyDoc: '" + declText + "'.");
        }
        seenParamKeys.add(declText);
        soyDocParams.add(new SoyDocParam(declText, declKeyword.equals("@param"), desc));

      } else {
        throw new AssertionError();
      }
    }

    return Pair.of(hasParamsWithIncorrectSyntax, soyDocParams);
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected TemplateNode(TemplateNode orig) {
    super(orig);
    this.soyFileHeaderInfo = orig.soyFileHeaderInfo;
    this.templateName = orig.templateName;
    this.partialTemplateName = orig.partialTemplateName;
    this.isPrivate = orig.isPrivate;
    this.autoescapeMode = orig.autoescapeMode;
    this.soyDoc = orig.soyDoc;
    this.soyDocDesc = orig.soyDocDesc;
    this.soyDocParams = orig.soyDocParams;  // safe to reuse (immutable)
  }


  /** Returns info from the containing Soy file's header declarations. */
  public SoyFileHeaderInfo getSoyFileHeaderInfo() {
    return soyFileHeaderInfo;
  }


  /** Returns the name of the containing delegate package, or null if none. */
  public String getDelPackageName() {
    return soyFileHeaderInfo.delPackageName;
  }


  /** Returns a template name suitable for display in user msgs. */
  public abstract String getTemplateNameForUserMsgs();


  /** Returns this template's name. */
  public String getTemplateName() {
    return templateName;
  }


  /** Returns this template's partial name. Only applicable for V2 (null for V1). */
  public String getPartialTemplateName() {
    return partialTemplateName;
  }


  /** Returns whether this template is private. */
  public boolean isPrivate() {
    return isPrivate;
  }


  /**
   * Returns the kind of autoescaping, if any, done for this template.
   */
  public AutoescapeMode getAutoescapeMode() {
    return autoescapeMode;
  }


  /** Clears the SoyDoc text, description, and param descriptions. */
  public void clearSoyDocStrings() {
    soyDoc = null;
    soyDocDesc = null;

    List<SoyDocParam> newSoyDocParams = Lists.newArrayListWithCapacity(soyDocParams.size());
    for (SoyDocParam origParam : soyDocParams) {
      newSoyDocParams.add(new SoyDocParam(origParam.key, origParam.isRequired, null));
    }
    soyDocParams = ImmutableList.copyOf(newSoyDocParams);
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
    int bodyLen = bodySb.length();
    if (bodyLen != 0) {
      if (bodyLen != 1 && bodySb.charAt(bodyLen-1) == ' ') {
        bodySb.replace(bodyLen-1, bodyLen, "{sp}");
      }
      if (bodySb.charAt(0) == ' ') {
        bodySb.replace(0, 1, "{sp}");
      }
    }

    sb.append(bodySb);
    sb.append("\n");
    sb.append("{/").append(getCommandName()).append("}\n");
    return sb.toString();
  }

}
