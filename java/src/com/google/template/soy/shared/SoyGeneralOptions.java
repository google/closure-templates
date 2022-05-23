/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.shared;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;

/**
 * Compilation options applicable to the Soy frontend and/or to multiple Soy backends.
 */
public final class SoyGeneralOptions implements Cloneable {

  /** A list of experimental features that are not generally available. */
  private ImmutableSet<String> experimentalFeatures = ImmutableSet.of();

  public SoyGeneralOptions() {}

  private SoyGeneralOptions(SoyGeneralOptions orig) {
    this.experimentalFeatures = ImmutableSet.copyOf(orig.experimentalFeatures);
  }

  /** Sets experimental features. These features are unreleased and are not generally available. */
  public SoyGeneralOptions setExperimentalFeatures(Iterable<String> experimentalFeatures) {
    this.experimentalFeatures = ImmutableSet.copyOf(experimentalFeatures);
    return this;
  }

  /** Returns the set of enabled experimental features. */
  public ImmutableSet<String> getExperimentalFeatures() {
    return experimentalFeatures;
  }

  @Override
  public final SoyGeneralOptions clone() {
    return new SoyGeneralOptions(this);
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("experimentalFeatures", experimentalFeatures)
        .toString();
  }
}
