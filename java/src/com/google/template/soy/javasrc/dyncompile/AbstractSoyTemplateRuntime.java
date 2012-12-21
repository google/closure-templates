/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.javasrc.dyncompile;

import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.javasrc.SoyTemplateRuntime;
import com.google.template.soy.shared.SoyCssRenamingMap;


/**
 * Base implementations of {@code SoyTemplateRuntime} methods.
 *
 * @author Mike Samuel
 */
abstract class AbstractSoyTemplateRuntime implements SoyTemplateRuntime {
  private SoyMapData data = null;
  private SoyMapData ijData = null;
  private SoyCssRenamingMap cssRenamingMap = SoyCssRenamingMap.IDENTITY;


  @Override
  public final SoyTemplateRuntime setData(SoyMapData data) {
    this.data = data;
    return this;
  }


  @Override
  public final SoyTemplateRuntime setIjData(SoyMapData ijData) {
    this.ijData = ijData;
    return this;
  }


  @Override
  public final SoyTemplateRuntime setCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
    this.cssRenamingMap = cssRenamingMap;
    return this;
  }


  /**
   * Does the work of rendering the template.
   * @param out Receives the template output.
   */
  protected abstract void renderMain(
      SoyMapData data, SoyMapData ijData, SoyCssRenamingMap renamingMap, StringBuilder out);


  @Override
  public void render(StringBuilder out) {
    renderMain(data, ijData, cssRenamingMap, out);
  }


  /** The average buffer size of all runs thus far or 0 if none. */
  private int bufferSizeRunningAverage = 0;
  /** The number of renders, used to maintain bufferSizeRunningAverage. */
  private long bufferSizeRunningCount = 0;

  @Override
  public String render() {
    // The 16 is the size of the default StringBuilder buffer, so on the first run we
    // behave exactly as if we didn't pre-size at all.
    // After that, we have a fixed overhead of 16 chars of slack to avoid buffer
    // copies for very minor changes in input size.
    StringBuilder sb = new StringBuilder(bufferSizeRunningAverage + 16);

    render(sb);

    // Keep track of the average output size so we can pre-size buffers to avoid
    // unnecessary array copies.
    // If a template has very predictable output size, then we will see at most 50%
    // buffer resize copy cost, and allocate 50% unnecessary space.
    // Without this, the cost is 100%, with up to 75% of the unnecessary memory
    // uncollectable while rendering is happening.
    long bufferSizeTotal = bufferSizeRunningAverage * bufferSizeRunningCount +
        sb.length() +
        // In division below, round towards closest, zero-biased.
        (bufferSizeRunningCount >> 1);
    // sb.length() is the last sample so we increment the count before dividing.
    ++bufferSizeRunningCount;
    bufferSizeRunningAverage = (int) (bufferSizeTotal / bufferSizeRunningCount);

    return sb.toString();
  }

}
