/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.idomsrc;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.internal.KytheMode;
import com.google.template.soy.base.internal.SourceMapMode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;

/** Compilation options for idomsrc. */
@AutoValue
public abstract class SoyIdomSrcOptions {

  /** Returns whether we should add a requirecss annotation for the generated GSS header file. */
  public abstract boolean dependOnCssHeader();

  /** Returns whether we should add a requirecss annotation for the generated GSS header file. */
  public abstract boolean googMsgsAreExternal();

  public abstract KytheMode kytheMode();

  public abstract SourceMapMode sourceMapMode();

  public static Builder builder() {
    return new AutoValue_SoyIdomSrcOptions.Builder()
        .setDependOnCssHeader(false)
        .setGoogMsgsAreExternal(true)
        .setKytheMode(KytheMode.DISABLED)
        .setSourceMapMode(SourceMapMode.DISABLED);
  }

  public abstract Builder toBuilder();

  public static SoyIdomSrcOptions getDefault() {
    return builder().build();
  }

  /** Builder. */
  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * Sets whether we should add a requirecss annotation for the generated GSS header file.
     *
     * @param dependOnCssHeader The value to set.
     */
    public abstract Builder setDependOnCssHeader(boolean dependOnCssHeader);

    /** Sets whether we should add a requirecss annotation for the generated GSS header file. */
    public abstract Builder setGoogMsgsAreExternal(boolean googMsgsAreExternal);

    public abstract Builder setKytheMode(KytheMode kytheMode);

    public abstract Builder setSourceMapMode(SourceMapMode sourceMapMode);

    public abstract SoyIdomSrcOptions build();
  }

  /**
   * Convert to {@link SoyJsSrcOptions}. This is necessary since {@code idomsrc} reuses lots of
   * {@code jssrc} which needs to interact with this object.
   */
  SoyJsSrcOptions toJsSrcOptions() {
    return SoyJsSrcOptions.builder()
        // Only goog.module generation supported
        .setShouldGenerateGoogModules(true)
        .setShouldGenerateGoogMsgDefs(true)
        .setGoogMsgsAreExternal(googMsgsAreExternal())
        .setBidiGlobalDir(0)
        .setUseGoogIsRtlForBidiGlobalDir(true)
        .setDependOnCssHeader(dependOnCssHeader())
        .setKytheMode(kytheMode())
        .setSourceMapMode(sourceMapMode())
        .build();
  }
}
