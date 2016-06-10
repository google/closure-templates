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

import static com.google.template.soy.soytree.AutoescapeMode.parseAutoEscapeMode;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionUpperBound;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.TemplateNodeBuilder.DeclInfo.OptionalStatus;
import com.google.template.soy.soytree.TemplateNodeBuilder.DeclInfo.Type;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.SoyDocParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.parse.TypeParser;
import com.google.template.soy.types.primitive.NullType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

  private static final SoyErrorKind INVALID_CSS_BASE_NAMESPACE_NAME =
      SoyErrorKind.of("Invalid CSS base namespace name ''{0}''");
  private static final SoyErrorKind INVALID_SOYDOC_PARAM =
      SoyErrorKind.of("Found invalid soydoc param name ''{0}''");
  private static final SoyErrorKind INVALID_TEMPLATE_NAME =
      SoyErrorKind.of("Invalid template name ''{0}''");
  private static final SoyErrorKind INVALID_PARAM_NAMED_IJ =
      SoyErrorKind.of("Invalid param name ''ij'' (''ij'' is for injected data).");
  private static final SoyErrorKind KIND_BUT_NOT_STRICT =
      SoyErrorKind.of("kind=\"...\" attribute is only valid with autoescape=\"strict\".");
  private static final SoyErrorKind LEGACY_COMPATIBLE_PARAM_TAG =
      SoyErrorKind.of(
          "Found invalid SoyDoc param tag ''{0}'', tags like this are only allowed in "
              + "legacy templates marked ''deprecatedV1=\"true\"''.  The proper soydoc @param "
              + "syntax is: ''@param <name> <optional comment>''. Soy does not understand JsDoc "
              + "style type declarations in SoyDoc.");
  private static final SoyErrorKind PARAM_ALREADY_DECLARED =
      SoyErrorKind.of("Param ''{0}'' already declared");

  /**
   * Value class used in the input to method {@link #setHeaderDecls}.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static final class DeclInfo {

    /** The type of declaration (either regular param or injected param). */
    public enum Type {
      PARAM("@param"),
      INJECTED_PARAM("@inject");

      private final String name;

      Type(String name) {
        this.name = name;
      }

      @Override
      public String toString() {
        return name;
      }
    }

    /** Whether this is an optional parameter. */
    public enum OptionalStatus {
      REQUIRED,
      OPTIONAL
    }

    private final Type type;
    private final String name;
    private final String paramTypeExpr;
    private final OptionalStatus optionalStatus;
    private final SourceLocation sourceLocation;
    @Nullable private final String soyDoc;

    public DeclInfo(
        Type type,
        OptionalStatus optionalStatus,
        String name,
        String paramTypeExpr,
        @Nullable String soyDoc,
        SourceLocation sourceLocation) {
      this.type = type;
      this.name = name;
      this.paramTypeExpr = paramTypeExpr;
      this.soyDoc = soyDoc;
      this.sourceLocation = sourceLocation;
      this.optionalStatus = optionalStatus;
    }

    public Type type() {
      return type;
    }

    public String name() {
      return name;
    }

    public String paramTypeExpr() {
      return paramTypeExpr;
    }

    @Nullable public String soyDoc() {
      return soyDoc;
    }

    public SourceLocation location() {
      return sourceLocation;
    }
  }

  /** Info from the containing Soy file's header declarations. */
  protected final SoyFileHeaderInfo soyFileHeaderInfo;

  /** For reporting parse errors. */
  protected final ErrorReporter errorReporter;

  /** The registry of named types. */
  private final SoyTypeRegistry typeRegistry;

  /** The id for this node. */
  protected Integer id;

  /** The lowest known syntax version bound. Value may be adjusted multiple times. */
  @Nullable protected SyntaxVersionUpperBound syntaxVersionBound;

  /** The command text. */
  protected String cmdText;

  /** This template's name.
   *  This is private instead of protected to enforce use of setTemplateNames(). */
  private String templateName;

  /** This template's partial name. Only applicable for V2.
   *  This is private instead of protected to enforce use of setTemplateNames(). */
  private String partialTemplateName;

  /** A string suitable for display in user msgs as the template name. */
  protected String templateNameForUserMsgs;

  /** This template's visibility level. */
  protected Visibility visibility;

  /** The mode of autoescaping for this template.
   *  This is private instead of protected to enforce use of setAutoescapeInfo(). */
  private AutoescapeMode autoescapeMode;

  /** Required CSS namespaces. */
  private ImmutableList<String> requiredCssNamespaces;

  /** Base CSS namespace for package-relative CSS selectors. */
  private String cssBaseNamespace;

  /** Strict mode context. Nonnull iff autoescapeMode is strict.
   *  This is private instead of protected to enforce use of setAutoescapeInfo(). */
  private ContentKind contentKind;

  /** Whether setSoyDoc() has been called. */
  protected boolean isSoyDocSet;

  /** The full SoyDoc, including the start/end tokens, or null. */
  protected String soyDoc;

  /** The description portion of the SoyDoc (before declarations), or null. */
  protected String soyDocDesc;

  /** The params from template header and/or SoyDoc. Null if no decls and no SoyDoc. */
  @Nullable protected ImmutableList<TemplateParam> params;

  protected boolean isMarkedV1;

  final SourceLocation sourceLocation;

  /**
   * @param soyFileHeaderInfo Info from the containing Soy file's header declarations.
   * @param typeRegistry Type registry used in parsing type declarations.
   */
  protected TemplateNodeBuilder(
      SoyFileHeaderInfo soyFileHeaderInfo,
      SourceLocation sourceLocation,
      ErrorReporter errorReporter,
      @Nullable SoyTypeRegistry typeRegistry) {
    this.soyFileHeaderInfo = soyFileHeaderInfo;
    this.sourceLocation = sourceLocation;
    this.errorReporter = errorReporter;
    this.typeRegistry = typeRegistry;
    this.syntaxVersionBound = null;
    this.isSoyDocSet = false;
    // All other fields default to null.
  }

  /**
   * Sets the id for the node to be built.
   * @return This builder.
   */
  public TemplateNodeBuilder setId(int id) {
    Preconditions.checkState(this.id == null);
    this.id = id;
    return this;
  }

  /**
   * Sets the command text for the node to be built. The command text will be parsed to fill in
   * fields such as templateName and autoescapeMode.
   * @return This builder.
   */
  public abstract TemplateNodeBuilder setCmdText(String cmdText);

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
   * @return This builder.
   */
  public TemplateNodeBuilder setSoyDoc(String soyDoc) {
    Preconditions.checkState(!isSoyDocSet);
    Preconditions.checkState(cmdText != null);

    this.isSoyDocSet = true;
    this.soyDoc = soyDoc;

    if (soyDoc != null) {
      Preconditions.checkArgument(soyDoc.startsWith("/**") && soyDoc.endsWith("*/"));
      String cleanedSoyDoc = cleanSoyDocHelper(soyDoc);
      this.soyDocDesc = parseSoyDocDescHelper(cleanedSoyDoc);
      this.addParams(parseSoyDocDeclsHelper(cleanedSoyDoc));
    } else {
      this.soyDocDesc = null;
      // Note: Don't set this.params to null here because params can also come from header decls.
    }

    return this;
  }

  /**
   * Sets the template header decls.
   * @param declInfos DeclInfo objects for the decls found in the template header.
   * @return This builder.
   */
  public TemplateNodeBuilder setHeaderDecls(Collection<DeclInfo> declInfos) {
    List<TemplateParam> params = new ArrayList<>(declInfos.size());
    for (DeclInfo declInfo : declInfos) {
      Optional<HeaderParam> headerParam = forDeclInfo(declInfo);
      if (headerParam.isPresent()) {
        params.add(headerParam.get());
      }
    }
    this.addParams(params);
    return this;
  }

  /**
   * Sets the template header decls.
   * @param declInfos DeclInfo objects for the decls found in the template header.
   * @return This builder.
   */
  public TemplateNodeBuilder setHeaderDecls(DeclInfo... declInfos) {
    List<TemplateParam> params = new ArrayList<>(declInfos.length);
    for (DeclInfo declInfo : declInfos) {
      Optional<HeaderParam> headerParam = forDeclInfo(declInfo);
      if (headerParam.isPresent()) {
        params.add(headerParam.get());
      }
    }
    this.addParams(params);
    return this;
  }

  private Optional<HeaderParam> forDeclInfo(DeclInfo declInfo) {
    SoyType type;
    boolean isInjected = declInfo.type == Type.INJECTED_PARAM;
    boolean isRequired = true;
    Preconditions.checkNotNull(typeRegistry);
    type = new TypeParser(declInfo.paramTypeExpr(), declInfo.location(), typeRegistry)
        .parseTypeDeclaration();
    if (declInfo.optionalStatus == OptionalStatus.OPTIONAL) {
      isRequired = false;
      type = typeRegistry.getOrCreateUnionType(type, NullType.getInstance());
    } else if (type instanceof UnionType && ((UnionType) type).isNullable()) {
      isRequired = false;
    }
    return Optional.of(new HeaderParam(declInfo.name(), declInfo.paramTypeExpr(), type,
        isRequired, isInjected, declInfo.soyDoc));
  }

  /**
   * Helper for {@code setSoyDoc()} and {@code setHeaderDecls()}. This method is intended to be
   * called at most once for SoyDoc params and at most once for header params.
   * @param params The params to add.
   */
  protected TemplateNodeBuilder addParams(Iterable<? extends TemplateParam> params) {

    if (this.params == null) {
      this.params = ImmutableList.copyOf(params);
    } else {
      this.params = ImmutableList.<TemplateParam>builder()
          .addAll(this.params)
          .addAll(params)
          .build();
    }

    // Check params.
    Set<String> seenParamKeys = new HashSet<>();
    for (TemplateParam param : this.params) {
      if (param.name().equals("ij")) {
        errorReporter.report(sourceLocation, INVALID_PARAM_NAMED_IJ);
      }
      if (seenParamKeys.contains(param.name())) {
        errorReporter.report(sourceLocation, PARAM_ALREADY_DECLARED, param.name());
      }
      seenParamKeys.add(param.name());
    }
    return this;
  }

  /**
   * Builds the template node. Will error if not enough info as been set on this builder.
   */
  public abstract TemplateNode build();

  // -----------------------------------------------------------------------------------------------
  // Protected helpers for fields that need extra logic when being set.

  protected final void setAutoescapeCmdText(Map<String, String> attributes) {
    AutoescapeMode autoescapeMode;
    String autoescapeModeStr = attributes.get("autoescape");
    if (autoescapeModeStr != null) {
      autoescapeMode = parseAutoEscapeMode(autoescapeModeStr);
    } else {
      autoescapeMode = soyFileHeaderInfo.defaultAutoescapeMode;  // inherit from file default
    }

    ContentKind contentKind = (attributes.get("kind") != null) ?
        NodeContentKinds.forAttributeValue(attributes.get("kind")) : null;

    setAutoescapeInfo(autoescapeMode, contentKind);
  }

  protected final void setRequireCssCmdText(Map<String, String> attributes) {
    setRequiredCssNamespaces(RequirecssUtils.parseRequirecssAttr(attributes.get("requirecss"),
        sourceLocation));
  }

  protected final void setCssBaseCmdText(Map<String, String> attributes) {
    String cssBaseNamespace = attributes.get("cssbase");
    if (cssBaseNamespace != null) {
      if (!BaseUtils.isDottedIdentifier(cssBaseNamespace)) {
        errorReporter.report(sourceLocation, INVALID_CSS_BASE_NAMESPACE_NAME, cssBaseNamespace);
      }
      setCssBaseNamespace(cssBaseNamespace);
    }
  }

  protected final void setV1Marker(Map<String, String> attributes) {
    if ("true".equals(attributes.get("deprecatedV1"))) {
      this.isMarkedV1 = true;
      SyntaxVersionUpperBound newSyntaxVersionBound = new SyntaxVersionUpperBound(
          SyntaxVersion.V2_0, "Template is marked as deprecatedV1.");
      this.syntaxVersionBound =
          SyntaxVersionUpperBound.selectLower(this.syntaxVersionBound, newSyntaxVersionBound);
    }
  }

  protected void setAutoescapeInfo(
      AutoescapeMode autoescapeMode, @Nullable ContentKind contentKind) {

    Preconditions.checkArgument(autoescapeMode != null);
    this.autoescapeMode = autoescapeMode;

    if (contentKind == null && autoescapeMode == AutoescapeMode.STRICT) {
      // Default mode is HTML.
      contentKind = ContentKind.HTML;
    } else if (contentKind != null && autoescapeMode != AutoescapeMode.STRICT) {
      // TODO: Perhaps this could imply strict escaping?
      errorReporter.report(sourceLocation, KIND_BUT_NOT_STRICT);
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
  @Nullable protected ContentKind getContentKind() {
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

  protected final void setTemplateNames(String templateName, @Nullable String partialTemplateName) {
    this.templateName = templateName;
    this.partialTemplateName = partialTemplateName;

    if (partialTemplateName != null && !BaseUtils.isIdentifierWithLeadingDot(partialTemplateName)) {
      errorReporter.report(sourceLocation, INVALID_TEMPLATE_NAME, partialTemplateName);
    }

    if (!BaseUtils.isDottedIdentifier(templateName)) {
      errorReporter.report(sourceLocation, INVALID_TEMPLATE_NAME, templateName);
    }
  }

  protected String getTemplateName() {
    return templateName;
  }

  @Nullable protected String getPartialTemplateName() {
    return partialTemplateName;
  }

  // -----------------------------------------------------------------------------------------------
  // Private static helpers for parsing template SoyDoc.

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

  /**
   * Private helper for the constructor to clean the SoyDoc.
   * (1) Changes all newlines to "\n".
   * (2) Escapes deprecated javadoc tags.
   * (3) Strips the start/end tokens and spaces (including newlines if they occupy their own lines).
   * (4) Removes common indent from all lines (e.g. space-star-space).
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
    return CharMatcher.whitespace().trimTrailingFrom(soyDocDesc);
  }

  /**
   * Private helper for the constructor to parse the SoyDoc declarations.
   *
   * @param cleanedSoyDoc The cleaned SoyDoc text. Must not be null.
   * @return A SoyDocDeclsInfo object with the parsed info.
   */
  private List<SoyDocParam> parseSoyDocDeclsHelper(String cleanedSoyDoc) {
    List<SoyDocParam> params = new ArrayList<>();

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

        if (SOY_DOC_PARAM_TEXT_PATTERN.matcher(declText).matches()) {
          params.add(new SoyDocParam(declText, declKeyword.equals("@param"), desc));

        } else {
          if (declText.startsWith("{")) {
            // v1 is allowed for compatibility reasons
            if (!isMarkedV1) {
              errorReporter.report(sourceLocation, LEGACY_COMPATIBLE_PARAM_TAG, declText);
            }
          } else {
            // TODO(lukes): the source location here is not accurate (points to the template, not
            // the doc line.
            errorReporter.report(sourceLocation, INVALID_SOYDOC_PARAM, declText);
          }
        }

      } else {
        throw new AssertionError();
      }
    }

    return params;
  }
}
