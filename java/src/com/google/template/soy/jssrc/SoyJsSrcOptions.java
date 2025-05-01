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

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.internal.KytheMode;
import com.google.template.soy.base.internal.SourceMapMode;

/** Compilation options for the JS Src output target (backend). */
@AutoValue
public abstract class SoyJsSrcOptions {

  enum JsDepsStrategy {
    /** Whether we should generate code to provide/require Soy namespaces. */
    NAMESPACES,
    /** Whether we should generate code to declare/require goog.modules. */
    MODULE
  }

  abstract JsDepsStrategy depsStrategy();

  /** Whether we should generate Closure Library message definitions (i.e. goog.getMsg). */
  public abstract boolean shouldGenerateGoogMsgDefs();

  /** Whether we should add a requirecss annotation for the generated CSS header file. */
  public abstract boolean dependOnCssHeader();

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
  public abstract boolean googMsgsAreExternal();

  abstract int bidiGlobalDir();

  /**
   * The bidi global directionality as a static value, 1: ltr, -1: rtl, 0: unspecified. If 0, and
   * useGoogIsRtlForBidiGlobalDir is false, the bidi global directionality will actually be inferred
   * from the message bundle locale. This must not be the case when shouldGenerateGoogMsgDefs is
   * true, but is the recommended mode of operation otherwise.
   */
  public int getBidiGlobalDir() {
    return bidiGlobalDir();
  }

  public abstract boolean useGoogIsRtlForBidiGlobalDir();

  /**
   * Whether to determine the bidi global direction at template runtime by evaluating
   * goog.i18n.bidi.IS_RTL. May only be true when both shouldGenerateGoogMsgDefs and either
   * shouldProvideRequireSoyNamespaces or shouldProvideRequireJsFunctions is true.
   */
  public boolean getUseGoogIsRtlForBidiGlobalDir() {
    return useGoogIsRtlForBidiGlobalDir();
  }


  /** Returns whether we're set to generate code to provide/require Soy namespaces. */
  public boolean shouldProvideRequireSoyNamespaces() {
    return depsStrategy() == JsDepsStrategy.NAMESPACES;
  }

  /** Returns whether goog.modules should be generated. */
  public boolean shouldGenerateGoogModules() {
    return depsStrategy() == JsDepsStrategy.MODULE;
  }

  public abstract KytheMode kytheMode();

  public abstract SourceMapMode sourceMapMode();

  public static Builder builder() {
    return new AutoValue_SoyJsSrcOptions.Builder()
        .setDepsStrategy(JsDepsStrategy.NAMESPACES)
        .setDependOnCssHeader(false)
        .setShouldGenerateGoogMsgDefs(false)
        .setGoogMsgsAreExternal(false)
        .setBidiGlobalDir(0)
        .setUseGoogIsRtlForBidiGlobalDir(false)
        .setKytheMode(KytheMode.DISABLED)
        .setSourceMapMode(SourceMapMode.DISABLED)
        .setEnableLazyJs(true);
  }

  public abstract Builder toBuilder();

  public abstract boolean enableLazyJs();

  public static SoyJsSrcOptions getDefault() {
    return builder().build();
  }

  /** Builder. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract Builder setDepsStrategy(JsDepsStrategy strategy);

    /**
     * Sets whether we should generate code to provide/require Soy namespaces.
     *
     * @param shouldProvideRequireSoyNamespaces The value to set.
     */
    @CanIgnoreReturnValue
    public Builder setShouldProvideRequireSoyNamespaces(boolean shouldProvideRequireSoyNamespaces) {
      if (shouldProvideRequireSoyNamespaces) {
        setDepsStrategy(JsDepsStrategy.NAMESPACES);
      }
      return this;
    }

    /**
     * Sets whether goog.modules should be generated.
     *
     * @param shouldGenerateGoogModules The value to set.
     */
    @CanIgnoreReturnValue
    public Builder setShouldGenerateGoogModules(boolean shouldGenerateGoogModules) {
      if (shouldGenerateGoogModules) {
        setDepsStrategy(JsDepsStrategy.MODULE);
      }
      return this;
    }

    /**
     * Sets whether we should generate Closure Library message definitions (i.e. goog.getMsg).
     *
     * @param shouldGenerateGoogMsgDefs The value to set.
     */
    public abstract Builder setShouldGenerateGoogMsgDefs(boolean shouldGenerateGoogMsgDefs);

    /**
     * Sets whether we should add a requirecss annotation for the generated CSS header file.
     *
     * @param dependOnCssHeader The value to set.
     */
    public abstract Builder setDependOnCssHeader(boolean dependOnCssHeader);

    /**
     * Sets whether the generated Closure Library message definitions are for external messages
     * (only applicable if shouldGenerateGoogMsgDefs is true).
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
    public abstract Builder setGoogMsgsAreExternal(boolean googMsgsAreExternal);

    /**
     * Sets the bidi global directionality to a static value, 1: ltr, -1: rtl, 0: unspecified. If 0,
     * and useGoogIsRtlForBidiGlobalDir is false, the bidi global directionality will actually be
     * inferred from the message bundle locale. This is the recommended mode of operation when
     * shouldGenerateGoogMsgDefs is false. When shouldGenerateGoogMsgDefs is true, the bidi global
     * direction can not be left unspecified, but the recommended way of doing so is via
     * setUseGoogIsRtlForBidiGlobalDir(true). Thus, whether shouldGenerateGoogMsgDefs is true or
     * not, THERE IS USUALLY NO NEED TO USE THIS METHOD!
     *
     * @param bidiGlobalDir 1: ltr, -1: rtl, 0: unspecified. Checks that no other value is used.
     */
    public abstract Builder setBidiGlobalDir(int bidiGlobalDir);

    /**
     * Sets the Javascript code snippet that will evaluate at template runtime to a boolean value
     * indicating whether the bidi global direction is rtl. Can only be used when
     * shouldGenerateGoogMsgDefs is true.
     *
     * @param useGoogIsRtlForBidiGlobalDir Whether to determine the bidi global direction at
     *     template runtime by evaluating goog.i18n.bidi.IS_RTL.
     */
    public abstract Builder setUseGoogIsRtlForBidiGlobalDir(boolean useGoogIsRtlForBidiGlobalDir);

    public abstract Builder setKytheMode(KytheMode kytheMode);

    public abstract Builder setSourceMapMode(SourceMapMode sourceMapMode);

    public abstract Builder setEnableLazyJs(boolean enableLazyJs);

    abstract SoyJsSrcOptions autoBuild();

    public SoyJsSrcOptions build() {
      SoyJsSrcOptions options = autoBuild();
      int bidiGlobalDir = options.getBidiGlobalDir();
      boolean useGoogIsRtlForBidiGlobalDir = options.getUseGoogIsRtlForBidiGlobalDir();

      Preconditions.checkArgument(
          bidiGlobalDir >= -1 && bidiGlobalDir <= 1,
          "bidiGlobalDir must be 1 for LTR, or -1 for RTL (or 0 to leave unspecified).");
      Preconditions.checkState(
          !useGoogIsRtlForBidiGlobalDir || bidiGlobalDir == 0,
          "Must not specify both bidiGlobalDir and useGoogIsRtlForBidiGlobalDir.");

      Preconditions.checkState(
          !useGoogIsRtlForBidiGlobalDir || options.shouldGenerateGoogMsgDefs(),
          "Do not specify useGoogIsRtlForBidiGlobalDir without shouldGenerateGoogMsgDefs.");
      Preconditions.checkState(
          !useGoogIsRtlForBidiGlobalDir || bidiGlobalDir == 0,
          "Must not specify both bidiGlobalDir and useGoogIsRtlForBidiGlobalDir.");

      return options;
    }
  }
}
