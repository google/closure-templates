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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.template.soy.SoyUtils;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Compilation options applicable to the Soy frontend and/or to multiple Soy backends.
 *
 */
public final class SoyGeneralOptions implements Cloneable {

  /** Whether to allow external calls (calls to undefined templates). Null if not explicitly set. */
  private TriState allowExternalCalls = TriState.UNSET;

  /** Map from compile-time global name to value. */
  private ImmutableMap<String, PrimitiveData> compileTimeGlobals;

  /** A list of experimental features that are not generally available. */
  private ImmutableSet<String> experimentalFeatures = ImmutableSet.of();

  /** Whether we should run optimizer. */
  private boolean enabledOptimizer = true;

  public SoyGeneralOptions() {}

  private SoyGeneralOptions(SoyGeneralOptions orig) {
    this.allowExternalCalls = orig.allowExternalCalls;
    this.compileTimeGlobals = orig.compileTimeGlobals;
    this.experimentalFeatures = ImmutableSet.copyOf(orig.experimentalFeatures);
    this.enabledOptimizer = orig.isOptimizerEnabled();
  }

  /** Disallow optimizer. */
  public SoyGeneralOptions disableOptimizer() {
    this.enabledOptimizer = false;
    return this;
  }

  /** Return true if we want to run optimizer in the compiler. */
  public boolean isOptimizerEnabled() {
    return this.enabledOptimizer;
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

  /**
   * Sets whether to allow external calls (calls to undefined templates).
   *
   * @param allowExternalCalls The value to set.
   */
  public SoyGeneralOptions setAllowExternalCalls(boolean allowExternalCalls) {
    this.allowExternalCalls = TriState.from(allowExternalCalls);
    return this;
  }

  /**
   * Returns whether to allow external calls (calls to undefined templates). If this option was
   * never explicitly set, then returns {@link TriState#UNSET}.
   */
  public TriState allowExternalCalls() {
    return allowExternalCalls;
  }

  /**
   * Sets the map from compile-time global name to value.
   *
   * <p>The values can be any of the Soy primitive types: null, boolean, integer, float (Java
   * double), or string.
   *
   * @param compileTimeGlobalsMap Map from compile-time global name to value. The values can be any
   *     of the Soy primitive types: null, boolean, integer, float (Java double), or string.
   * @throws IllegalArgumentException If one of the values is not a valid Soy primitive type.
   */
  public SoyGeneralOptions setCompileTimeGlobals(Map<String, ?> compileTimeGlobalsMap) {
    setCompileTimeGlobalsInternal(
        InternalValueUtils.convertCompileTimeGlobalsMap(compileTimeGlobalsMap));
    return this;
  }

  /**
   * Sets the map from compile-time global name to value using Soy primitive types.
   *
   * @param compileTimeGlobalsMap Map from compile-time global name to value.
   */
  private void setCompileTimeGlobalsInternal(
      ImmutableMap<String, PrimitiveData> compileTimeGlobalsMap) {
    Preconditions.checkState(compileTimeGlobals == null, "Compile-time globals already set.");
    compileTimeGlobals = compileTimeGlobalsMap;
  }

  /**
   * Sets the file containing compile-time globals.
   *
   * <p>Each line of the file should have the format
   *
   * <pre>
   *     &lt;global_name&gt; = &lt;primitive_data&gt;
   * </pre>
   *
   * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
   * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
   * should be encoded in UTF-8.
   *
   * <p>If you need to generate a file in this format from Java, consider using the utility {@code
   * SoyUtils.generateCompileTimeGlobalsFile()}.
   *
   * @param compileTimeGlobalsFile The file containing compile-time globals.
   * @throws IOException If there is an error reading the compile-time globals file.
   */
  public SoyGeneralOptions setCompileTimeGlobals(File compileTimeGlobalsFile) throws IOException {
    setCompileTimeGlobalsInternal(
        SoyUtils.parseCompileTimeGlobals(Files.asCharSource(compileTimeGlobalsFile, UTF_8)));
    return this;
  }

  /**
   * Sets the resource file containing compile-time globals.
   *
   * <p>Each line of the file should have the format
   *
   * <pre>
   *     &lt;global_name&gt; = &lt;primitive_data&gt;
   * </pre>
   *
   * where primitive_data is a valid Soy expression literal for a primitive type (null, boolean,
   * integer, float, or string). Empty lines and lines beginning with "//" are ignored. The file
   * should be encoded in UTF-8.
   *
   * <p>If you need to generate a file in this format from Java, consider using the utility {@code
   * SoyUtils.generateCompileTimeGlobalsFile()}.
   *
   * @param compileTimeGlobalsResource The resource file containing compile-time globals.
   * @throws IOException If there is an error reading the compile-time globals file.
   */
  public SoyGeneralOptions setCompileTimeGlobals(URL compileTimeGlobalsResource)
      throws IOException {
    setCompileTimeGlobalsInternal(
        SoyUtils.parseCompileTimeGlobals(
            Resources.asCharSource(compileTimeGlobalsResource, UTF_8)));
    return this;
  }

  /** Returns the map from compile-time global name to value. */
  public ImmutableMap<String, PrimitiveData> getCompileTimeGlobals() {
    return compileTimeGlobals == null ? ImmutableMap.of() : compileTimeGlobals;
  }

  @Override
  public final SoyGeneralOptions clone() {
    return new SoyGeneralOptions(this);
  }

  @Override
  public final String toString() {
    return MoreObjects.toStringHelper(this)
        .add("allowExternalCalls", allowExternalCalls)
        .add("compileTimeGlobals", compileTimeGlobals)
        .add("experimentalFeatures", experimentalFeatures)
        .add("enabledOptimizer", enabledOptimizer)
        .toString();
  }
}
