/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.exprparse;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.SoyErrorKind;

/**
 * Holds information and services needed for parsing templates and expressions.
 *
 * <p>Do not store this class beyond the lifetime of the parse; ErrorReporter is not reusable like
 * that
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
@AutoValue
public abstract class SoyParsingContext {
  public static SoyParsingContext create(
      ErrorReporter errorReporter,
      String namespace,
      ImmutableMap<String, String> aliasToNamespaceMap) {
    return new AutoValue_SoyParsingContext(errorReporter, namespace, aliasToNamespaceMap);
  }

  public static SoyParsingContext empty(ErrorReporter errorReporter, String namespace) {
    return create(errorReporter, namespace, ImmutableMap.<String, String>of());
  }

  /**
   * Creates a context with an exploding error reporter and no contextual data. Use this method for
   * non-file contexts such as error objects.
   */
  public static SoyParsingContext exploding() {
    return empty(ExplodingErrorReporter.get(), "fake.namespace");
  }

  public abstract ErrorReporter errorReporter();

  /** The full namespace of the file being parsed. */
  public abstract String namespace();

  /** The alias declarations in the file, if any. */
  abstract ImmutableMap<String, String> aliasToNamespaceMap();

  /** Dealiases a name in the scope of this context (if it matches an alias). */
  public String resolveAlias(String sourceName) {
    String alias = aliasToNamespaceMap().get(sourceName);
    return alias != null ? alias : sourceName;
  }

  /**
   * Reports the given {@code error}, formatted according to {@code args} and associated with the
   * given {@code sourceLocation}. Convencience wrapper for errorReporter.
   */
  public void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {
    errorReporter().report(sourceLocation, error, args);
  }

  public SoyParsingContext withErrorReporter(ErrorReporter errorReporter) {
    return create(errorReporter, namespace(), aliasToNamespaceMap());
  }
}
