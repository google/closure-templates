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

package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.jssrc.SoyJsSrcOptions;

/**
 * Compilation options for incrementaldomsrc.
 *
 * <p>Currently there are no options, this object exists for future expansion.
 */
public final class SoyIncrementalDomSrcOptions {

  /** Whether we should add a requirecss annotation for the generated GSS header file. */
  private boolean dependOnCssHeader;

  private boolean googMsgsAreExternal;

  private boolean generateMaybeRequireForControllerAndModelXids; // MOE: strip_line

  public SoyIncrementalDomSrcOptions() {
    dependOnCssHeader = false;
    googMsgsAreExternal = true;
    generateMaybeRequireForControllerAndModelXids = false; // MOE: strip_line
  }

  private SoyIncrementalDomSrcOptions(SoyIncrementalDomSrcOptions orig) {
    this.dependOnCssHeader = orig.dependOnCssHeader;
    this.googMsgsAreExternal = orig.googMsgsAreExternal;
    // MOE: begin_strip
    this.generateMaybeRequireForControllerAndModelXids =
        orig.generateMaybeRequireForControllerAndModelXids;
    // MOE: end_strip
  }

  /**
   * Sets whether we should add a requirecss annotation for the generated GSS header file.
   *
   * @param dependOnCssHeader The value to set.
   */
  public void setDependOnCssHeader(boolean dependOnCssHeader) {
    this.dependOnCssHeader = dependOnCssHeader;
  }

  /** Returns whether we should add a requirecss annotation for the generated GSS header file. */
  public boolean dependOnCssHeader() {
    return dependOnCssHeader;
  }

  // MOE: begin_strip
  public void setGenerateMaybeRequireForControllerAndModelXids(
      boolean generateMaybeRequireForControllerAndModelXids) {
    this.generateMaybeRequireForControllerAndModelXids =
        generateMaybeRequireForControllerAndModelXids;
  }

  public boolean generateMaybeRequireForControllerAndModelXids() {
    return generateMaybeRequireForControllerAndModelXids;
  }

  // MOE: end_strip

  /**
   * Sets whether we should add a requirecss annotation for the generated GSS header file.
   *
   * @param dependOnCssHeader The value to set.
   */
  public void setGoogMsgsAreExternal(boolean googMsgsAreExternal) {
    this.googMsgsAreExternal = googMsgsAreExternal;
  }

  /** Returns whether we should add a requirecss annotation for the generated GSS header file. */
  public boolean googMsgsAreExternal() {
    return googMsgsAreExternal;
  }

  /**
   * Convert to {@link SoyJsSrcOptions}. This is necessary since {@code incrementaldomsrc} reuses
   * lots of {@code jssrc} which needs to interact with this object.
   */
  SoyJsSrcOptions toJsSrcOptions() {
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
    // Only goog.module generation supported
    jsSrcOptions.setShouldGenerateGoogModules(true);
    jsSrcOptions.setShouldGenerateGoogMsgDefs(true);
    jsSrcOptions.setGoogMsgsAreExternal(googMsgsAreExternal);
    jsSrcOptions.setBidiGlobalDir(0);
    jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(true);
    jsSrcOptions.setDependOnCssHeader(dependOnCssHeader);
    // MOE: begin_strip
    jsSrcOptions.setGenerateMaybeRequireForControllerAndModelXids(
        generateMaybeRequireForControllerAndModelXids);
    // MOE: end_strip
    return jsSrcOptions;
  }

  @Override
  public SoyIncrementalDomSrcOptions clone() {
    return new SoyIncrementalDomSrcOptions(this);
  }
}
