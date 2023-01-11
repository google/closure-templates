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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A validated wrapper of {@link LoggingConfig}.
 *
 * <p>Ensures that there are no duplicate names or ids and Enables easy lookup.
 */
public final class ValidatedLoggingConfig {

  private static final SoyErrorKind INVALID_VE_NAME =
      SoyErrorKind.of("''{0}'' is not a valid identifier.");

  private static final SoyErrorKind INVALID_VE_ID =
      SoyErrorKind.of(
          "ID {0,number,#} for ''{1}'' must be between {2,number,#} and {3,number,#} (inclusive).");

  private static final SoyErrorKind DUPLICATE_VE_ID =
      SoyErrorKind.of("Found 2 LoggableElements with the same id {0,number,#}:\n{1}\nand\n{2}.");

  private static final SoyErrorKind DUPLICATE_VE_NAME =
      SoyErrorKind.of("Found 2 LoggableElements with the same name {0}:\n{1}\nand\n{2}.");

  public static final String UNDEFINED_VE_NAME = "UndefinedVe";

  public static final AnnotatedLoggableElement UNDEFINED_VE =
      AnnotatedLoggableElement.newBuilder()
          .setElement(
              LoggableElement.newBuilder()
                  .setName(UNDEFINED_VE_NAME)
                  .setId(SoyLogger.UNDEFINED_VE_ID)
                  .build())
          .build();

  public static final ValidatedLoggingConfig EMPTY =
      create(AnnotatedLoggingConfig.newBuilder().addElement(UNDEFINED_VE).build());

  /** The maximum safe integer value in JavaScript: 2^53 - 1 */
  private static final long MAX_ID_VALUE = 9007199254740991L;

  /** The minimum safe integer value in JavaScript. */
  private static final long MIN_ID_VALUE = -MAX_ID_VALUE;

  /**
   * Parses the logging config proto into a {@link ValidatedLoggingConfig}.
   *
   * @throws IllegalArgumentException if there is an error during parsing.
   */
  public static ValidatedLoggingConfig create(AnnotatedLoggingConfig configProto) {
    if (configProto.getElementCount() == 0) {
      return EMPTY;
    }
    return new ValidatedLoggingConfig(
        ImmutableMap.copyOf(
            validate(
                configProto.getElementList().stream()
                    .map(ValidatedLoggableElement::create)
                    .collect(toImmutableSet()),
                ErrorReporter.illegalArgumentExceptionExploding())));
  }

  public static void validate(
      ValidatedLoggingConfig loggingConfig,
      Set<ValidatedLoggableElement> ves,
      ErrorReporter errorReporter) {
    validate(
        ImmutableSet.<ValidatedLoggableElement>builder()
            .addAll(loggingConfig.elementsByName.values())
            .addAll(ves)
            .build(),
        errorReporter);
  }

  @CanIgnoreReturnValue
  private static Map<String, ValidatedLoggableElement> validate(
      Set<ValidatedLoggableElement> ves, ErrorReporter errorReporter) {
    Map<String, ValidatedLoggableElement> elementsByName = new LinkedHashMap<>();
    Map<Long, ValidatedLoggableElement> elementsById = new LinkedHashMap<>();

    for (ValidatedLoggableElement ve : ves) {
      if (ve.getId() == SoyLogger.UNDEFINED_VE_ID) {
        checkState(!ve.hasMetadata(), "UndefinedVe cannot have metadata.");
      }

      String name = ve.getName();
      if (!BaseUtils.isDottedIdentifier(name)) {
        errorReporter.report(ve.getSourceLocation(), INVALID_VE_NAME, name);
      }

      if (ve.getId() < MIN_ID_VALUE || ve.getId() > MAX_ID_VALUE) {
        errorReporter.report(
            ve.getSourceLocation(), INVALID_VE_ID, ve.getId(), name, MIN_ID_VALUE, MAX_ID_VALUE);
      }

      ValidatedLoggableElement oldWithSameId = elementsById.put(ve.getId(), ve);
      ValidatedLoggableElement oldWithSameName = elementsByName.put(ve.getName(), ve);
      if (oldWithSameId != null && !elementsEquivalent(ve, oldWithSameId)) {
        errorReporter.report(
            ve.getSourceLocation(), DUPLICATE_VE_ID, ve.getId(), ve, oldWithSameId);
      } else if (oldWithSameName != null && !elementsEquivalent(ve, oldWithSameName)) {
        errorReporter.report(
            ve.getSourceLocation(), DUPLICATE_VE_NAME, ve.getName(), ve, oldWithSameName);
      }
    }

    checkState(
        elementsByName.containsKey(UNDEFINED_VE_NAME)
            && elementsByName.get(UNDEFINED_VE_NAME).getId() == SoyLogger.UNDEFINED_VE_ID,
        "Logging config is missing UndefinedVe.");

    return elementsByName;
  }

  /**
   * This is used to allow duplicates for "equivalent" elements.
   *
   * <p>Certain aspects of an element don't matter for equivalence. For example, the location
   * (package and class name) of the metadata file doesn't matter, because as long as the metadata
   * is equivalent it doesn't matter which file it gets it from.
   */
  private static boolean elementsEquivalent(
      ValidatedLoggableElement a1, ValidatedLoggableElement a2) {
    return a1.getName().equals(a2.getName())
        && a1.getId() == a2.getId()
        && a1.getProtoName().equals(a2.getProtoName())
        && a1.getMetadata().equals(a2.getMetadata());
  }

  private final ImmutableMap<String, ValidatedLoggableElement> elementsByName;

  private ValidatedLoggingConfig(ImmutableMap<String, ValidatedLoggableElement> elementsByName) {
    this.elementsByName = elementsByName;
  }

  /** Returns a {@link ValidatedLoggableElement} based on the given element name or {@code null}. */
  @Nullable
  public ValidatedLoggableElement getElement(String identifier) {
    return elementsByName.get(identifier);
  }

  /** Returns all known element identifiers. */
  public ImmutableSet<String> allKnownIdentifiers() {
    return elementsByName.keySet();
  }

  /** A validated wrapper for {@link AnnotatedLoggableElement}. */
  @AutoValue
  public abstract static class ValidatedLoggableElement {

    public static ValidatedLoggableElement create(
        String name,
        long id,
        Optional<String> protoName,
        Optional<Object> metadata,
        SourceLocation sourceLocation) {
      return new AutoValue_ValidatedLoggingConfig_ValidatedLoggableElement(
          name, id, protoName, "", "", "", metadata, sourceLocation);
    }

    static ValidatedLoggableElement create(AnnotatedLoggableElement annotatedElement) {
      LoggableElement element = annotatedElement.getElement();
      return new AutoValue_ValidatedLoggingConfig_ValidatedLoggableElement(
          element.getName(),
          element.getId(),
          Optional.ofNullable(Strings.emptyToNull(element.getProtoType())),
          annotatedElement.getJavaPackage(),
          annotatedElement.getJsPackage(),
          annotatedElement.getClassName(),
          annotatedElement.getHasMetadata()
              ? Optional.of(annotatedElement.getElement().getMetadata())
              : Optional.empty(),
          SourceLocation.UNKNOWN);
    }

    ValidatedLoggableElement() {}

    public abstract String getName();

    public abstract long getId();

    public abstract Optional<String> getProtoName();

    public abstract String getJavaPackage();

    public abstract String getJsPackage();

    public abstract String getClassName();

    public abstract Optional<Object> getMetadata();

    public abstract SourceLocation getSourceLocation();

    public boolean hasMetadata() {
      return getMetadata().isPresent();
    }

    /** The name of the generated method to access the VE metadata for this VE ID. */
    public final String getGeneratedVeMetadataMethodName() {
      return String.format("v%s", getId());
    }

    @Override
    public final String toString() {
      return String.format(
          "Ve{name=%s, id=%s%s%s} @ %s",
          getName(),
          getId(),
          getProtoName().isPresent() ? ", data=" + getProtoName().get() : "",
          getMetadata().isPresent() ? ", metadata=" + getMetadata().get() : "",
          getSourceLocation());
    }
  }
}
