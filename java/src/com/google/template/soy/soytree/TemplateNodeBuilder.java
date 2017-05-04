/*
 * Copyright 2013 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionUpperBound;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.SoyDocParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Builder for TemplateNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public abstract class TemplateNodeBuilder {

  private static final SoyErrorKind INVALID_SOYDOC_PARAM =
      SoyErrorKind.of("Found invalid soydoc param name ''{0}''.");
  private static final SoyErrorKind INVALID_PARAM_NAMED_IJ =
      SoyErrorKind.of("Invalid param name ''ij'' (''ij'' is for injected data).");
  private static final SoyErrorKind KIND_BUT_NOT_STRICT =
      SoyErrorKind.of(
          "kind=\"...\" attribute is only valid with autoescape=\"strict\".",
          StyleAllowance.NO_CAPS);
  private static final SoyErrorKind LEGACY_COMPATIBLE_PARAM_TAG =
      SoyErrorKind.of(
          "Found invalid SoyDoc param tag ''{0}'', tags like this are only allowed in "
              + "legacy templates marked ''deprecatedV1=\"true\"''.  The proper soydoc @param "
              + "syntax is: ''@param <name> <optional comment>''. Soy does not understand JsDoc "
              + "style type declarations in SoyDoc.");
  private static final SoyErrorKind PARAM_ALREADY_DECLARED =
      SoyErrorKind.of("Param ''{0}'' already declared.");

  /** Info from the containing Soy file's header declarations. */
  protected final SoyFileHeaderInfo soyFileHeaderInfo;

  /** For reporting parse errors. */
  protected final ErrorReporter errorReporter;

  /** The id for this node. */
  protected Integer id;

  /** The lowest known syntax version bound. Value may be adjusted multiple times. */
  @Nullable protected SyntaxVersionUpperBound syntaxVersionBound;

  /** The command text. */
  protected String cmdText;

  /**
   * This template's name. This is private instead of protected to enforce use of
   * setTemplateNames().
   */
  private String templateName;

  /**
   * This template's partial name. Only applicable for V2. This is private instead of protected to
   * enforce use of setTemplateNames().
   */
  private String partialTemplateName;

  /** A string suitable for display in user msgs as the template name. */
  protected String templateNameForUserMsgs;

  /** This template's visibility level. */
  protected Visibility visibility;

  /**
   * The mode of autoescaping for this template. This is private instead of protected to enforce use
   * of setAutoescapeInfo().
   */
  private AutoescapeMode autoescapeMode;

  /** Required CSS namespaces. */
  private ImmutableList<String> requiredCssNamespaces = ImmutableList.of();

  /** Base CSS namespace for package-relative CSS selectors. */
  private String cssBaseNamespace;

  /**
   * Strict mode context. Nonnull iff autoescapeMode is strict. This is private instead of protected
   * to enforce use of setAutoescapeInfo().
   */
  private ContentKind contentKind;

  /** The full SoyDoc, including the start/end tokens, or null. */
  protected String soyDoc;

  /** The description portion of the SoyDoc (before declarations), or null. */
  protected String soyDocDesc;

  /** The params from template header and/or SoyDoc. Null if no decls and no SoyDoc. */
  @Nullable protected ImmutableList<TemplateParam> params;

  protected boolean isMarkedV1;

  protected TriState strictHtmlMode;

  SourceLocation sourceLocation;

  /** @param soyFileHeaderInfo Info from the containing Soy file's header declarations. */
  protected TemplateNodeBuilder(SoyFileHeaderInfo soyFileHeaderInfo, ErrorReporter errorReporter) {
    this.soyFileHeaderInfo = soyFileHeaderInfo;
    this.errorReporter = errorReporter;
    this.syntaxVersionBound = null;
    this.strictHtmlMode = TriState.UNSET;
    // All other fields default to null.
  }

  /**
   * Sets the id for the node to be built.
   *
   * @return This builder.
   */
  public TemplateNodeBuilder setId(int id) {
    Preconditions.checkState(this.id == null);
    this.id = id;
    return this;
  }

  /** Sets the source location. */
  public TemplateNodeBuilder setSourceLocation(SourceLocation location) {
    checkState(sourceLocation == null);
    this.sourceLocation = checkNotNull(location);
    return this;
  }

  /**
   * Set the parsed data from the command tag.
   *
   * @param name The template name
   * @param attrs The attributes that are set on the tag {e.g. {@code kind="strict"}}
   */
  public abstract TemplateNodeBuilder setCommandValues(
      Identifier name, List<CommandTagAttribute> attrs);

  /**
   * Returns a template name suitable for display in user msgs.
   *
   * <p>Note: This public getter exists because this info is needed by SoyFileParser for error
   * reporting before the TemplateNode is ready to be built.
   */
  public String getTemplateNameForUserMsgs() {
    return templateNameForUserMsgs;
  }

  /**
   * Sets the SoyDoc for the node to be built. The SoyDoc will be parsed to fill in SoyDoc param
   * info.
   *
   * @return This builder.
   */
  public TemplateNodeBuilder setSoyDoc(String soyDoc, SourceLocation soyDocLocation) {
    Preconditions.checkState(this.soyDoc == null);
    Preconditions.checkState(cmdText != null);
    this.soyDoc = soyDoc;
    Preconditions.checkArgument(soyDoc.startsWith("/**") && soyDoc.endsWith("*/"));
    String cleanedSoyDoc = cleanSoyDocHelper(soyDoc);
    this.soyDocDesc = parseSoyDocDescHelper(cleanedSoyDoc);
    this.addParams(parseSoyDocDeclsHelper(soyDoc, cleanedSoyDoc, soyDocLocation));

    return this;
  }

  /**
   * Helper for {@code setSoyDoc()} and {@code setHeaderDecls()}. This method is intended to be
   * called at most once for SoyDoc params and at most once for header params.
   *
   * @param params The params to add.
   */
  public TemplateNodeBuilder addParams(Iterable<? extends TemplateParam> params) {

    Set<String> seenParamKeys = new HashSet<>();
    if (this.params == null) {
      this.params = ImmutableList.copyOf(params);
    } else {
      for (TemplateParam oldParam : this.params) {
        seenParamKeys.add(oldParam.name());
      }
      this.params =
          ImmutableList.<TemplateParam>builder().addAll(this.params).addAll(params).build();
    }

    // Check new params.
    for (TemplateParam param : params) {
      if (param.name().equals("ij")) {
        errorReporter.report(param.nameLocation(), INVALID_PARAM_NAMED_IJ);
      }
      if (!seenParamKeys.add(param.name())) {
        errorReporter.report(param.nameLocation(), PARAM_ALREADY_DECLARED, param.name());
      }
    }
    return this;
  }

  /** Builds the template node. Will error if not enough info as been set on this builder. */
  public abstract TemplateNode build();

  // -----------------------------------------------------------------------------------------------
  // Protected helpers for fields that need extra logic when being set.

  protected final void markDeprecatedV1(boolean isDeprecatedV1) {
    isMarkedV1 = isDeprecatedV1;
    if (isDeprecatedV1) {
      SyntaxVersionUpperBound newSyntaxVersionBound =
          new SyntaxVersionUpperBound(SyntaxVersion.V2_0, "Template is marked as deprecatedV1.");
      this.syntaxVersionBound =
          SyntaxVersionUpperBound.selectLower(this.syntaxVersionBound, newSyntaxVersionBound);
    }
  }

  protected void setAutoescapeInfo(
      AutoescapeMode autoescapeMode,
      @Nullable ContentKind contentKind,
      @Nullable SourceLocation kindLocation) {

    Preconditions.checkArgument(autoescapeMode != null);
    this.autoescapeMode = autoescapeMode;

    if (contentKind == null && autoescapeMode == AutoescapeMode.STRICT) {
      // Default mode is HTML.
      contentKind = ContentKind.HTML;
    } else if (contentKind != null && autoescapeMode != AutoescapeMode.STRICT) {
      // TODO: Perhaps this could imply strict escaping?
      errorReporter.report(kindLocation, KIND_BUT_NOT_STRICT);
    }
    this.contentKind = contentKind;
  }

  /** @return the id for this node. */
  Integer getId() {
    return id;
  }

  /** @return The lowest known syntax version bound. */
  SyntaxVersionUpperBound getSyntaxVersionBound() {
    return syntaxVersionBound;
  }

  /** @return The command text. */
  String getCmdText() {
    return cmdText;
  }

  /** @return The full SoyDoc, including the start/end tokens, or null. */
  String getSoyDoc() {
    return soyDoc;
  }

  /** @return The description portion of the SoyDoc (before declarations), or null. */
  String getSoyDocDesc() {
    return soyDocDesc;
  }

  /** @return The mode of autoescaping for this template. */
  protected AutoescapeMode getAutoescapeMode() {
    Preconditions.checkState(autoescapeMode != null);
    return autoescapeMode;
  }

  /** @return Strict mode context. Nonnull iff autoescapeMode is strict. */
  @Nullable
  public ContentKind getContentKind() {
    checkState(autoescapeMode != null); // make sure setAutoescapeInfo was called
    return contentKind;
  }

  /** @return Required CSS namespaces. */
  protected ImmutableList<String> getRequiredCssNamespaces() {
    return Preconditions.checkNotNull(requiredCssNamespaces);
  }

  protected void setRequiredCssNamespaces(ImmutableList<String> requiredCssNamespaces) {
    this.requiredCssNamespaces = Preconditions.checkNotNull(requiredCssNamespaces);
  }

  /** @return Base CSS namespace for package-relative CSS selectors. */
  protected String getCssBaseNamespace() {
    return cssBaseNamespace;
  }

  protected void setCssBaseNamespace(String cssBaseNamespace) {
    this.cssBaseNamespace = cssBaseNamespace;
  }

  protected final void setTemplateNames(
      String templateName, SourceLocation nameLocation, @Nullable String partialTemplateName) {
    this.templateName = templateName;
    this.partialTemplateName = partialTemplateName;
  }

  protected TriState getStrictHtmlMode() {
    return strictHtmlMode;
  }

  protected String getTemplateName() {
    return templateName;
  }

  @Nullable
  protected String getPartialTemplateName() {
    return partialTemplateName;
  }

  // -----------------------------------------------------------------------------------------------
  // Private static helpers for parsing template SoyDoc.

  /** Pattern for a newline. */
  private static final Pattern NEWLINE = Pattern.compile("\\n|\\r\\n?");

  /** Pattern for a SoyDoc start token, including spaces up to the first newline. */
  private static final Pattern SOY_DOC_START =
      Pattern.compile("^ [/][*][*] [\\ ]* \\r?\\n?", Pattern.COMMENTS);

  /** Pattern for a SoyDoc end token, including preceding spaces up to the last newline. */
  private static final Pattern SOY_DOC_END =
      Pattern.compile("\\r?\\n? [\\ ]* [*][/] $", Pattern.COMMENTS);

  /** Pattern for a SoyDoc declaration. */
  // group(1) = declaration keyword; group(2) = declaration text.
  private static final Pattern SOY_DOC_DECL_PATTERN =
      Pattern.compile("( @param[?]? ) \\s+ ( \\S+ )", Pattern.COMMENTS);

  /** Pattern for SoyDoc parameter declaration text. */
  private static final Pattern SOY_DOC_PARAM_TEXT_PATTERN =
      Pattern.compile("[a-zA-Z_]\\w*", Pattern.COMMENTS);

  /**
   * Private helper for the constructor to clean the SoyDoc. (1) Changes all newlines to "\n". (2)
   * Escapes deprecated javadoc tags. (3) Strips the start/end tokens and spaces (including newlines
   * if they occupy their own lines). (4) Removes common indent from all lines (e.g.
   * space-star-space).
   *
   * @param soyDoc The SoyDoc to clean.
   * @return The cleaned SoyDoc.
   */
  private static String cleanSoyDocHelper(String soyDoc) {
    // Change all newlines to "\n".
    soyDoc = NEWLINE.matcher(soyDoc).replaceAll("\n");

    // Escape all @deprecated javadoc tags.
    // TODO(cushon): add this to the specification and then also generate @Deprecated annotations
    soyDoc = soyDoc.replace("@deprecated", "&#64;deprecated");

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
   * Private helper for {@code cleanSoyDocHelper()}. Removes a common character at the start of all
   * lines, either once or as many times as possible.
   *
   * <p>Special case: Empty lines count as if they do have the common character for the purpose of
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
          continue; // empty lines are okay
        }
        areAllLinesEmpty = false;
        if (line.length() <= numCharsToRemove || line.charAt(numCharsToRemove) != charToRemove) {
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
          continue; // don't change empty lines
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
    return CharMatcher.whitespace().trimTrailingFrom(soyDocDesc);
  }

  /**
   * Private helper for the constructor to parse the SoyDoc declarations.
   *
   * @param cleanedSoyDoc The cleaned SoyDoc text. Must not be null.
   * @return A SoyDocDeclsInfo object with the parsed info.
   */
  private List<SoyDocParam> parseSoyDocDeclsHelper(
      String originalSoyDoc, String cleanedSoyDoc, SourceLocation soyDocSourceLocation) {
    List<SoyDocParam> params = new ArrayList<>();
    RawTextNode originalSoyDocAsNode = new RawTextNode(-1, originalSoyDoc, soyDocSourceLocation);
    Matcher matcher = SOY_DOC_DECL_PATTERN.matcher(cleanedSoyDoc);
    // Important: This statement finds the param for the first iteration of the loop.
    boolean isFound = matcher.find();
    while (isFound) {

      // Save the match groups.
      String declKeyword = matcher.group(1);
      String declText = matcher.group(2);

      String fullMatch = matcher.group();
      // find the param in the original soy doc and use the RawTextNode support for
      // calculating substring locations to get a more accurate location
      int indexOfParamName = originalSoyDoc.indexOf(declText, originalSoyDoc.indexOf(fullMatch));
      SourceLocation paramLocation =
          originalSoyDocAsNode.substringLocation(
              indexOfParamName, indexOfParamName + declText.length());
      // Find the next declaration in the SoyDoc and extract this declaration's desc string.
      int descStart = matcher.end();
      // Important: This statement finds the param for the next iteration of the loop.
      // We must find the next param now in order to know where the current param's desc ends.
      isFound = matcher.find();
      int descEnd = (isFound) ? matcher.start() : cleanedSoyDoc.length();
      String desc = cleanedSoyDoc.substring(descStart, descEnd).trim();

      if (declKeyword.equals("@param") || declKeyword.equals("@param?")) {

        if (SOY_DOC_PARAM_TEXT_PATTERN.matcher(declText).matches()) {
          params.add(new SoyDocParam(declText, declKeyword.equals("@param"), desc, paramLocation));

        } else {
          if (declText.startsWith("{")) {
            // v1 is allowed for compatibility reasons
            if (!isMarkedV1) {
              errorReporter.report(paramLocation, LEGACY_COMPATIBLE_PARAM_TAG, declText);
            }
          } else {
            errorReporter.report(paramLocation, INVALID_SOYDOC_PARAM, declText);
          }
        }

      } else {
        throw new AssertionError();
      }
    }

    return params;
  }
}
