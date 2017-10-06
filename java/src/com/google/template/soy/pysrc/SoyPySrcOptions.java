/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Compilation options for the Python backend.
 *
 */
public final class SoyPySrcOptions implements Cloneable {
  /** The full module and fn path to a runtime library for determining global directionality. */
  private final String bidiIsRtlFn;

  /** The base module path for loading the runtime modules. */
  private final String runtimePath;

  /** The custom environment module path. */
  private final String environmentModulePath;

  /** The full module and class path to a runtime library for translation. */
  private final String translationClass;

  /** A namespace manifest mapping soy namespaces to their python path. */
  private final ImmutableMap<String, String> namespaceManifest;

  /** The name of a manifest file to generate, or null. */
  @Nullable private final String namespaceManifestFile;

  public SoyPySrcOptions(
      String runtimePath,
      String environmentModulePath,
      String bidiIsRtlFn,
      String translationClass,
      ImmutableMap<String, String> namespaceManifest,
      String namespaceManifestFile) {
    this.runtimePath = runtimePath;
    this.environmentModulePath = environmentModulePath;
    this.bidiIsRtlFn = bidiIsRtlFn;
    this.translationClass = translationClass;
    this.namespaceManifest = namespaceManifest;
    this.namespaceManifestFile = namespaceManifestFile;
  }

  private SoyPySrcOptions(SoyPySrcOptions orig) {
    this.runtimePath = orig.runtimePath;
    this.environmentModulePath = orig.environmentModulePath;
    this.bidiIsRtlFn = orig.bidiIsRtlFn;
    this.translationClass = orig.translationClass;
    this.namespaceManifest = orig.namespaceManifest;
    this.namespaceManifestFile = orig.namespaceManifestFile;
  }

  public String getBidiIsRtlFn() {
    return bidiIsRtlFn;
  }

  public String getRuntimePath() {
    return runtimePath;
  }

  public String getEnvironmentModulePath() {
    return environmentModulePath;
  }

  public String getTranslationClass() {
    return translationClass;
  }

  public Map<String, String> getNamespaceManifest() {
    return namespaceManifest;
  }

  @Nullable
  public String namespaceManifestFile() {
    return namespaceManifestFile;
  }

  @Override
  public final SoyPySrcOptions clone() {
    return new SoyPySrcOptions(this);
  }
}
