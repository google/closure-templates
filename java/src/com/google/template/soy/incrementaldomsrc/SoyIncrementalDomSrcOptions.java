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
  public SoyIncrementalDomSrcOptions() {}

  /**
   * Convert to {@link SoyJsSrcOptions}. This is necessary since {@code incrementaldomsrc} reuses
   * lots of {@code jssrc} which needs to interact with this object.
   */
  SoyJsSrcOptions toJsSrcOptions() {
    SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
    // Only goog.module generation supported
    jsSrcOptions.setShouldGenerateGoogModules(true);
    jsSrcOptions.setShouldGenerateGoogMsgDefs(true);
    jsSrcOptions.setGoogMsgsAreExternal(true);
    jsSrcOptions.setBidiGlobalDir(0);
    jsSrcOptions.setUseGoogIsRtlForBidiGlobalDir(true);
    return jsSrcOptions;
  }

  @Override
  public SoyIncrementalDomSrcOptions clone() {
    // this object is currently immutable.  Change this to return a new instance if we ever add real
    // options
    return this;
  }
}
