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

package com.google.template.soy.logging;


import com.google.auto.value.AutoValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validates a group of visual elements, ensures that there are no duplicate names or ids. */
public final class LoggingConfigValidator {

  private static final SoyErrorKind INVALID_VE_NAME =
      SoyErrorKind.of(
          "''{0}'' is not a valid identifier.",
          Impression.ERROR_LOGGING_CONFIG_VALIDATOR_INVALID_VE_NAME);

  private static final SoyErrorKind INVALID_VE_ID =
      SoyErrorKind.of(
          "ID {0,number,#} for ''{1}'' must be between {2,number,#} and {3,number,#} (inclusive).",
          Impression.ERROR_LOGGING_CONFIG_VALIDATOR_INVALID_VE_ID);

  private static final SoyErrorKind CONFLICTING_VE =
      SoyErrorKind.of(
          "Found VE definition that conflicts with {0}.",
          Impression.ERROR_LOGGING_CONFIG_VALIDATOR_CONFLICTING_VE);

  private static final SoyErrorKind UNDEFINED_VE_WITH_DATA_TYPE =
      SoyErrorKind.of(
          "The special VE ''UndefinedVe'' cannot be defined with a proto type.",
          Impression.ERROR_LOGGING_CONFIG_VALIDATOR_UNDEFINED_VE_WITH_DATA_TYPE);

  private static final SoyErrorKind UNDEFINED_VE_WITH_METADATA =
      SoyErrorKind.of(
          "The special VE ''UndefinedVe'' cannot be defined with metadata.",
          Impression.ERROR_LOGGING_CONFIG_VALIDATOR_UNDEFINED_VE_WITH_METADATA);

  public static void validate(List<VisualElement> ves, ErrorReporter errorReporter) {
    Map<String, VisualElement> elementsByName = new LinkedHashMap<>();
    Map<Long, VisualElement> elementsById = new LinkedHashMap<>();

    for (VisualElement ve : ves) {
      if (ve.getId() == SoyLogger.UNDEFINED_VE_ID
          || ve.getName().equals(SoyLogger.UNDEFINED_VE_NAME)) {
        if (ve.getProtoName().isPresent()) {
          errorReporter.report(ve.getSourceLocation(), UNDEFINED_VE_WITH_DATA_TYPE);
        }
        if (ve.hasMetadata()) {
          errorReporter.report(ve.getSourceLocation(), UNDEFINED_VE_WITH_METADATA);
        }
      }

      String name = ve.getName();
      if (!BaseUtils.isDottedIdentifier(name)) {
        errorReporter.report(ve.getSourceLocation(), INVALID_VE_NAME, name);
      }

      VisualElement oldWithSameId = elementsById.put(ve.getId(), ve);
      VisualElement oldWithSameName = elementsByName.put(ve.getName(), ve);
      if (oldWithSameId != null && !elementsEquivalent(ve, oldWithSameId)) {
        errorReporter.report(ve.getSourceLocation(), CONFLICTING_VE, oldWithSameId);
      } else if (oldWithSameName != null && !elementsEquivalent(ve, oldWithSameName)) {
        errorReporter.report(ve.getSourceLocation(), CONFLICTING_VE, oldWithSameName);
      }
    }
  }

  /**
   * This is used to allow duplicates for "equivalent" elements. SourceLocation is allowed to
   * differ, two identical elements defined in two different places is valid.
   */
  private static boolean elementsEquivalent(VisualElement a1, VisualElement a2) {
    return a1.getName().equals(a2.getName())
        && a1.getId() == a2.getId()
        && a1.getProtoName().equals(a2.getProtoName())
        && a1.getMetadata().equals(a2.getMetadata());
  }

  /** The configuration of a single visual element. */
  @AutoValue
  public abstract static class VisualElement {

    public static VisualElement create(
        String name,
        long id,
        Optional<String> protoName,
        Optional<Object> metadata,
        SourceLocation sourceLocation) {
      return new AutoValue_LoggingConfigValidator_VisualElement(
          name, id, protoName, metadata, sourceLocation);
    }

    VisualElement() {}

    public abstract String getName();

    public abstract long getId();

    public abstract Optional<String> getProtoName();

    public abstract Optional<Object> getMetadata();

    public abstract SourceLocation getSourceLocation();

    public boolean hasMetadata() {
      return getMetadata().isPresent();
    }

    @Override
    public final String toString() {
      return String.format(
          "Ve{name=%s, id=%s%s%s} declared at %s",
          getName(),
          getId(),
          getProtoName().isPresent() ? ", data=" + getProtoName().get() : "",
          getMetadata().isPresent() ? ", metadata=" + getMetadata().get().toString().trim() : "",
          getSourceLocation());
    }
  }
}
