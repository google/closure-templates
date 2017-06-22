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

package com.google.template.soy.jssrc;

import com.google.common.base.Preconditions;

/**
 * Compilation options for the JS Src output target (backend).
 *
 */
public final class SoyJsSrcOptions implements Cloneable {

  /** Whether to allow deprecated syntax (semi backwards compatible mode). */
  private boolean shouldAllowDeprecatedSyntax;

  /** Whether we should generate JSDoc with type info for the Closure Compiler. */
  private boolean shouldGenerateJsdoc;

  /** Whether we should generate code to provide/require Soy namespaces. */
  private boolean shouldProvideRequireSoyNamespaces;

  /** Whether we should generate code to provide/require template JS functions. */
  private boolean shouldProvideRequireJsFunctions;

  /** Whether we should generate code to provide both Soy namespaces and JS functions. */
  private boolean shouldProvideBothSoyNamespacesAndJsFunctions;

  /** Whether we should generate code to declare the top level namespace. */
  private boolean shouldDeclareTopLevelNamespaces;

  /** Whether we should generate code to declare goog.modules. */
  private boolean shouldGenerateGoogModules;

  /** Whether we should generate Closure Library message definitions (i.e. goog.getMsg). */
  private boolean shouldGenerateGoogMsgDefs;

  /** Whether the Closure Library messages are external, i.e. "MSG_EXTERNAL_[soyGeneratedMsgId]". */
  private boolean googMsgsAreExternal;

  /**
   * The bidi global directionality as a static value, 1: ltr, -1: rtl, 0: unspecified. If 0, and
   * useGoogIsRtlForBidiGlobalDir is false, the bidi global directionality will actually be inferred
   * from the message bundle locale. This must not be the case when shouldGenerateGoogMsgDefs is
   * true, but is the recommended mode of operation otherwise.
   */
  private int bidiGlobalDir;

  /**
   * Whether to determine the bidi global direction at template runtime by evaluating
   * goog.i18n.bidi.IS_RTL. May only be true when both shouldGenerateGoogMsgDefs and either
   * shouldProvideRequireSoyNamespaces or shouldProvideRequireJsFunctions is true.
   */
  private boolean useGoogIsRtlForBidiGlobalDir;

  public SoyJsSrcOptions() {
    shouldAllowDeprecatedSyntax = false;
    shouldGenerateJsdoc = false;
    shouldProvideRequireSoyNamespaces = false;
    shouldProvideRequireJsFunctions = false;
    shouldProvideBothSoyNamespacesAndJsFunctions = false;
    shouldDeclareTopLevelNamespaces = true;
    shouldGenerateGoogMsgDefs = false;
    shouldGenerateGoogModules = false;
    googMsgsAreExternal = false;
    bidiGlobalDir = 0;
    useGoogIsRtlForBidiGlobalDir = false;
  }

  private SoyJsSrcOptions(SoyJsSrcOptions orig) {
    this.shouldAllowDeprecatedSyntax = orig.shouldAllowDeprecatedSyntax;
    this.shouldGenerateJsdoc = orig.shouldGenerateJsdoc;
    this.shouldProvideRequireSoyNamespaces = orig.shouldProvideRequireSoyNamespaces;
    this.shouldProvideRequireJsFunctions = orig.shouldProvideRequireJsFunctions;
    this.shouldProvideBothSoyNamespacesAndJsFunctions =
        orig.shouldProvideBothSoyNamespacesAndJsFunctions;
    this.shouldDeclareTopLevelNamespaces = orig.shouldDeclareTopLevelNamespaces;
    this.shouldGenerateGoogModules = orig.shouldGenerateGoogModules;
    this.shouldGenerateGoogMsgDefs = orig.shouldGenerateGoogMsgDefs;
    this.googMsgsAreExternal = orig.googMsgsAreExternal;
    this.bidiGlobalDir = orig.bidiGlobalDir;
    this.useGoogIsRtlForBidiGlobalDir = orig.useGoogIsRtlForBidiGlobalDir;
  }

  /**
   * Sets whether to allow deprecated syntax (semi backwards compatible mode).
   *
   * @param shouldAllowDeprecatedSyntax The value to set.
   */
  // TODO SOON: Deprecate. (Use setDeclaredSyntaxVersionName() on SoyFileSet or SoyGeneralOptions.)
  public void setShouldAllowDeprecatedSyntax(boolean shouldAllowDeprecatedSyntax) {
    this.shouldAllowDeprecatedSyntax = shouldAllowDeprecatedSyntax;
  }

  /**
   * Returns whether we're set to allow deprecated syntax (semi backwards compatible mode).
   *
   * @deprecated Use {@code SoyGeneralOptions.getDeclaredSyntaxVersion()}.
   */
  @Deprecated
  public boolean shouldAllowDeprecatedSyntax() {
    return shouldAllowDeprecatedSyntax;
  }

  /**
   * Sets whether we should generate JSDoc with type info for the Closure Compiler.
   *
   * @param shouldGenerateJsdoc The value to set.
   */
  public void setShouldGenerateJsdoc(boolean shouldGenerateJsdoc) {
    this.shouldGenerateJsdoc = shouldGenerateJsdoc;
  }

  /** Returns whether we should generate JSDoc with type info for the Closure Compiler. */
  public boolean shouldGenerateJsdoc() {
    return shouldGenerateJsdoc;
  }

  /**
   * Sets whether we should generate code to provide/require Soy namespaces.
   *
   * @param shouldProvideRequireSoyNamespaces The value to set.
   */
  public void setShouldProvideRequireSoyNamespaces(boolean shouldProvideRequireSoyNamespaces) {
    // TODO(b/24275162) Replace these variables with a single Enum
    this.shouldProvideRequireSoyNamespaces = shouldProvideRequireSoyNamespaces;
    Preconditions.checkState(
        !(this.shouldProvideRequireSoyNamespaces && this.shouldProvideRequireJsFunctions),
        "Must not enable both shouldProvideRequireSoyNamespaces and"
            + " shouldProvideRequireJsFunctions.");
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequireSoyNamespaces),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when"
            + " shouldProvideRequireSoyNamespaces is enabled.");
  }

  /** Returns whether we're set to generate code to provide/require Soy namespaces. */
  public boolean shouldProvideRequireSoyNamespaces() {
    return shouldProvideRequireSoyNamespaces;
  }

  /**
   * Sets whether we should generate code to provide/require template JS functions.
   *
   * @param shouldProvideRequireJsFunctions The value to set.
   */
  public void setShouldProvideRequireJsFunctions(boolean shouldProvideRequireJsFunctions) {
    // TODO(b/24275162) Replace these variables with a single Enum
    this.shouldProvideRequireJsFunctions = shouldProvideRequireJsFunctions;
    Preconditions.checkState(
        !(this.shouldProvideRequireSoyNamespaces && this.shouldProvideRequireJsFunctions),
        "Must not enable both shouldProvideRequireSoyNamespaces and"
            + " shouldProvideRequireJsFunctions.");
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequireJsFunctions),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when"
            + " shouldProvideRequireJsFunctions is enabled.");
  }

  /** Returns whether we're set to generate code to provide/require template JS functions. */
  public boolean shouldProvideRequireJsFunctions() {
    return shouldProvideRequireJsFunctions;
  }

  /**
   * Sets whether we should generate code to provide both Soy namespaces and JS functions.
   *
   * @param shouldProvideBothSoyNamespacesAndJsFunctions The value to set.
   */
  public void setShouldProvideBothSoyNamespacesAndJsFunctions(
      boolean shouldProvideBothSoyNamespacesAndJsFunctions) {
    // TODO(b/24275162) Replace these variables with a single Enum
    this.shouldProvideBothSoyNamespacesAndJsFunctions =
        shouldProvideBothSoyNamespacesAndJsFunctions;
    if (shouldProvideBothSoyNamespacesAndJsFunctions) {
      Preconditions.checkState(
          this.shouldProvideRequireSoyNamespaces || this.shouldProvideRequireJsFunctions,
          "Must only enable shouldProvideBothSoyNamespacesAndJsFunctions after enabling either"
              + " shouldProvideRequireSoyNamespaces or shouldProvideRequireJsFunctions.");
    }
  }

  /** Returns whether we should generate code to provide both Soy namespaces and JS functions. */
  public boolean shouldProvideBothSoyNamespacesAndJsFunctions() {
    return shouldProvideBothSoyNamespacesAndJsFunctions;
  }

  /**
   * Sets whether we should generate code to declare the top level namespace.
   *
   * @param shouldDeclareTopLevelNamespaces The value to set.
   */
  public void setShouldDeclareTopLevelNamespaces(boolean shouldDeclareTopLevelNamespaces) {
    // TODO(b/24275162) Replace these variables with a single Enum
    this.shouldDeclareTopLevelNamespaces = shouldDeclareTopLevelNamespaces;
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequireSoyNamespaces),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when"
            + " shouldProvideRequireSoyNamespaces is enabled.");
    Preconditions.checkState(
        !(!this.shouldDeclareTopLevelNamespaces && this.shouldProvideRequireJsFunctions),
        "Turning off shouldDeclareTopLevelNamespaces has no meaning when"
            + " shouldProvideRequireJsFunctions is enabled.");
  }

  /** Returns whether we should attempt to declare the top level namespace. */
  public boolean shouldDeclareTopLevelNamespaces() {
    return shouldDeclareTopLevelNamespaces;
  }

  /**
   * Sets whether goog.modules should be generated.
   *
   * @param shouldGenerateGoogModules The value to set.
   */
  public void setShouldGenerateGoogModules(boolean shouldGenerateGoogModules) {
    // TODO(b/24275162) Replace these variables with a single Enum
    this.shouldGenerateGoogModules = shouldGenerateGoogModules;
    if (shouldGenerateGoogModules) {
      Preconditions.checkState(
          !shouldDeclareTopLevelNamespaces
              && !shouldProvideRequireSoyNamespaces
              && !shouldProvideRequireJsFunctions
              && !shouldProvideBothSoyNamespacesAndJsFunctions,
          "If generating goog.module calls, shouldDeclareTopLevelNamespaces, "
              + "shouldProvideRequireSoyNamespaces, shouldProvideRequireJsFunctions and "
              + "shouldProvideBothSoyNamespacesAndJsFunctions should not be enabled.");
    }
  }

  /** Returns whether goog.modules should be generated. */
  public boolean shouldGenerateGoogModules() {
    return shouldGenerateGoogModules;
  }

  /**
   * Sets whether we should generate Closure Library message definitions (i.e. goog.getMsg).
   *
   * @param shouldGenerateGoogMsgDefs The value to set.
   */
  public void setShouldGenerateGoogMsgDefs(boolean shouldGenerateGoogMsgDefs) {
    this.shouldGenerateGoogMsgDefs = shouldGenerateGoogMsgDefs;
  }

  /** Returns whether we should generate Closure Library message definitions (i.e. goog.getMsg). */
  public boolean shouldGenerateGoogMsgDefs() {
    return shouldGenerateGoogMsgDefs;
  }

  /**
   * Sets whether the generated Closure Library message definitions are for external messages (only
   * applicable if shouldGenerateGoogMsgDefs is true).
   *
   * <p>If this option is true, then we generate
   *
   * <pre>{@code var MSG_EXTERNAL_[soyGeneratedMsgId] = goog.getMsg(...);}</pre>
   *
   * <p>If this option is false, then we generate
   *
   * <pre>{@code var MSG_UNNAMED_[uniquefier] = goog.getMsg(...);}</pre>
   *
   * @param googMsgsAreExternal The value to set.
   */
  public void setGoogMsgsAreExternal(boolean googMsgsAreExternal) {
    this.googMsgsAreExternal = googMsgsAreExternal;
  }

  /**
   * Returns whether the generated Closure Library message definitions are for external messages
   * (only applicable if shouldGenerateGoogMsgDefs is true).
   *
   * <p>If this option is true, then we generate
   *
   * <pre>{@code var MSG_EXTERNAL_[soyGeneratedMsgId] = goog.getMsg(...);}</pre>
   *
   * <p>If this option is false, then we generate
   *
   * <pre>{@code var MSG_UNNAMED_[uniquefier] = goog.getMsg(...);}</pre>
   */
  public boolean googMsgsAreExternal() {
    return googMsgsAreExternal;
  }

  /**
   * Sets the bidi global directionality to a static value, 1: ltr, -1: rtl, 0: unspecified. If 0,
   * and useGoogIsRtlForBidiGlobalDir is false, the bidi global directionality will actually be
   * inferred from the message bundle locale. This is the recommended mode of operation when
   * shouldGenerateGoogMsgDefs is false. When shouldGenerateGoogMsgDefs is true, the bidi global
   * direction can not be left unspecified, but the recommended way of doing so is via
   * setUseGoogIsRtlForBidiGlobalDir(true). Thus, whether shouldGenerateGoogMsgDefs is true or not,
   * THERE IS USUALLY NO NEED TO USE THIS METHOD!
   *
   * @param bidiGlobalDir 1: ltr, -1: rtl, 0: unspecified. Checks that no other value is used.
   */
  public void setBidiGlobalDir(int bidiGlobalDir) {
    Preconditions.checkArgument(
        bidiGlobalDir >= -1 && bidiGlobalDir <= 1,
        "bidiGlobalDir must be 1 for LTR, or -1 for RTL (or 0 to leave unspecified).");
    Preconditions.checkState(
        !useGoogIsRtlForBidiGlobalDir || bidiGlobalDir == 0,
        "Must not specify both bidiGlobalDir and useGoogIsRtlForBidiGlobalDir.");
    this.bidiGlobalDir = bidiGlobalDir;
  }

  /** Returns the static bidi global directionality, 1: ltr, -1: rtl, 0: unspecified. */
  public int getBidiGlobalDir() {
    return bidiGlobalDir;
  }

  /**
   * Sets the Javascript code snippet that will evaluate at template runtime to a boolean value
   * indicating whether the bidi global direction is rtl. Can only be used when
   * shouldGenerateGoogMsgDefs is true.
   *
   * @param useGoogIsRtlForBidiGlobalDir Whether to determine the bidi global direction at template
   *     runtime by evaluating goog.i18n.bidi.IS_RTL.
   */
  public void setUseGoogIsRtlForBidiGlobalDir(boolean useGoogIsRtlForBidiGlobalDir) {
    Preconditions.checkState(
        !useGoogIsRtlForBidiGlobalDir || shouldGenerateGoogMsgDefs,
        "Do not specify useGoogIsRtlForBidiGlobalDir without shouldGenerateGoogMsgDefs.");
    Preconditions.checkState(
        !useGoogIsRtlForBidiGlobalDir
            || shouldProvideRequireSoyNamespaces
            || shouldProvideRequireJsFunctions
            || shouldGenerateGoogModules,
        "Do not specify useGoogIsRtlForBidiGlobalDir without one of"
            + " shouldProvideRequireSoyNamespaces, shouldProvideRequireJsFunctions or "
            + " shouldGenerateGoogModules.");
    Preconditions.checkState(
        !useGoogIsRtlForBidiGlobalDir || bidiGlobalDir == 0,
        "Must not specify both bidiGlobalDir and useGoogIsRtlForBidiGlobalDir.");
    this.useGoogIsRtlForBidiGlobalDir = useGoogIsRtlForBidiGlobalDir;
  }

  /**
   * Returns whether to determine the bidi global direction at template runtime by evaluating
   * goog.i18n.bidi.IS_RTL. May only be true when shouldGenerateGoogMsgDefs is true.
   */
  public boolean getUseGoogIsRtlForBidiGlobalDir() {
    return useGoogIsRtlForBidiGlobalDir;
  }

  @Override
  public final SoyJsSrcOptions clone() {
    return new SoyJsSrcOptions(this);
  }
}
