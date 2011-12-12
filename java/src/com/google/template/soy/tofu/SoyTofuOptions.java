/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.tofu;


/**
 * Compilation options for the Tofu backend.
 *
 * @author Kai Huang
 */
public class SoyTofuOptions implements Cloneable {


  /** Whether the resulting SoyTofu instance should cache intermediate results after substitutions
   *  from the SoyMsgBundle and the SoyCssRenamingMap. */
  private boolean useCaching;


  public SoyTofuOptions() {
    useCaching = false;
  }


  /**
   * Sets whether the resulting SoyTofu instance should cache intermediate results after
   * substitutions from the SoyMsgBundle and the SoyCssRenamingMap.
   *
   * <p> Specifically, if this param is set to true, then
   * (a) The first time the SoyTofu is used with a new combination of SoyMsgBundle and
   *     SoyCssRenamingMap, the render will be slower. (Note that this first-render slowness can
   *     be eliminated by calling the method {@link SoyTofu#addToCache} to prime the cache.)
   * (b) The subsequent times the SoyTofu is used with an already-seen combination of
   *     SoyMsgBundle and SoyCssRenamingMap, the render will be faster.
   *
   * <p> The cache will use memory proportional to the number of distinct combinations of
   * SoyMsgBundle and SoyCssRenamingMap your app uses (note most apps have at most one
   * SoyCssRenamingMap). If you find memory usage to be a problem, you can manually control the
   * contents of the cache. See {@link SoyTofu.Renderer#setDontAddToCache} for details.
   *
   * @param useCaching The value to set.
   */
  public void setUseCaching(boolean useCaching) {
    this.useCaching = useCaching;
  }


  /**
   * Returns whether the resulting SoyTofu instance should cache intermediate results after
   * substitutions from the SoyMsgBundle and the SoyCssRenamingMap.
   */
  public boolean useCaching() {
    return useCaching;
  }


  @Override public SoyTofuOptions clone() {
    try {
      return (SoyTofuOptions) super.clone();
    } catch (CloneNotSupportedException cnse) {
      throw new RuntimeException("Cloneable interface removed from SoyTofuOptions.");
    }
  }

}
