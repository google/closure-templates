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

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/**
 * Compilation options for the JS Src output target (backend).
 */
public final class SoyJsSrcOptions implements Cloneable {

  private enum JsDepsStrategy {
    /** Whether we should generate code to provide/require Soy namespaces. */
    NAMESPACES,
    /** Whether we should generate code to declare/require goog.modules. */
    MODULE
  }

  private JsDepsStrategy depsStrategy;

  /** Whether we should generate Closure Library message definitions (i.e. goog.getMsg). */
  private boolean shouldGenerateGoogMsgDefs;

  /** Whether we should add a requirecss annotation for the generated CSS header file. */
  private boolean dependOnCssHeader;

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

  private boolean replaceXidNodes;

  public SoyJsSrcOptions() {
    depsStrategy = JsDepsStrategy.NAMESPACES;

    dependOnCssHeader = false;
    shouldGenerateGoogMsgDefs = false;
    googMsgsAreExternal = false;
    bidiGlobalDir = 0;
    useGoogIsRtlForBidiGlobalDir = false;
    replaceXidNodes = false;
  }

  private SoyJsSrcOptions(SoyJsSrcOptions orig) {
    this.depsStrategy = orig.depsStrategy;
    this.dependOnCssHeader = orig.dependOnCssHeader;
    this.shouldGenerateGoogMsgDefs = orig.shouldGenerateGoogMsgDefs;
    this.googMsgsAreExternal = orig.googMsgsAreExternal;
    this.bidiGlobalDir = orig.bidiGlobalDir;
    this.useGoogIsRtlForBidiGlobalDir = orig.useGoogIsRtlForBidiGlobalDir;
    this.replaceXidNodes = orig.replaceXidNodes;
  }

  /**
   * Sets whether we should generate code to provide/require Soy namespaces.
   *
   * @param shouldProvideRequireSoyNamespaces The value to set.
   */
  public void setShouldProvideRequireSoyNamespaces(boolean shouldProvideRequireSoyNamespaces) {
    if (shouldProvideRequireSoyNamespaces) {
      depsStrategy = JsDepsStrategy.NAMESPACES;
    }
  }

  /** Returns whether we're set to generate code to provide/require Soy namespaces. */
  public boolean shouldProvideRequireSoyNamespaces() {
    return depsStrategy == JsDepsStrategy.NAMESPACES;
  }

  /**
   * Sets whether goog.modules should be generated.
   *
   * @param shouldGenerateGoogModules The value to set.
   */
  public void setShouldGenerateGoogModules(boolean shouldGenerateGoogModules) {
    if (shouldGenerateGoogModules) {
      depsStrategy = JsDepsStrategy.MODULE;
    }
  }

  /** Returns whether goog.modules should be generated. */
  public boolean shouldGenerateGoogModules() {
    return depsStrategy == JsDepsStrategy.MODULE;
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

  public void setReplaceXidNodes(boolean replaceXidNodes) {
    this.replaceXidNodes = replaceXidNodes;
  }

  public boolean replaceXidNodes() {
    return replaceXidNodes;
  }

  /**
   * Sets whether we should add a requirecss annotation for the generated CSS header file.
   *
   * @param dependOnCssHeader The value to set.
   */
  public void setDependOnCssHeader(boolean dependOnCssHeader) {
    this.dependOnCssHeader = dependOnCssHeader;
  }

  /** Returns whether we should add a requirecss annotation for the generated CSS header file. */
  public boolean dependOnCssHeader() {
    return dependOnCssHeader;
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
  public SoyJsSrcOptions clone() {
    return new SoyJsSrcOptions(this);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("shouldProvideRequireSoyNamespaces", shouldProvideRequireSoyNamespaces())
        .add("dependOnCssHeader", dependOnCssHeader)
        .add("shouldGenerateGoogMsgDefs", shouldGenerateGoogMsgDefs)
        .add("shouldGenerateGoogModules", shouldGenerateGoogModules())
        .add("googMsgsAreExternal", googMsgsAreExternal)
        .add("bidiGlobalDir", bidiGlobalDir)
        .add("useGoogIsRtlForBidiGlobalDir", useGoogIsRtlForBidiGlobalDir)
        .toString();
  }
}
