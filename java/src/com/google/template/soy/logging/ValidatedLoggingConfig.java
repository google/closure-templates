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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.BaseUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A validated wrapper of {@link LoggingConfig}.
 *
 * <p>Ensures that there are no duplicate names or ids and Enables easy lookup.
 */
public final class ValidatedLoggingConfig {

  private static final String UNDEFINED_VE_NAME = "UndefinedVe";

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

    Map<String, ValidatedLoggableElement> elementsByName = new LinkedHashMap<>();
    Map<Long, ValidatedLoggableElement> elementsById = new LinkedHashMap<>();
    Map<String, AnnotatedLoggableElement> rawElementsByName = new LinkedHashMap<>();
    Map<Long, AnnotatedLoggableElement> rawElementsById = new LinkedHashMap<>();

    for (AnnotatedLoggableElement annotatedElement : configProto.getElementList()) {
      if (annotatedElement.getElement().getId() == SoyLogger.UNDEFINED_VE_ID) {
        checkState(!annotatedElement.getHasMetadata(), "UndefinedVe cannot have metadata.");
      }

      LoggableElement element = annotatedElement.getElement();
      String name = element.getName();
      checkArgument(BaseUtils.isDottedIdentifier(name), "'%s' is not a valid identifier", name);
      checkArgument(
          MIN_ID_VALUE <= element.getId() && element.getId() <= MAX_ID_VALUE,
          "ID %s for '%s' must be between %s and %s (inclusive).",
          element.getId(),
          name,
          MIN_ID_VALUE,
          MAX_ID_VALUE);
      ValidatedLoggableElement elementConfig = ValidatedLoggableElement.create(annotatedElement);

      ValidatedLoggableElement oldWithSameId =
          elementsById.put(elementConfig.getId(), elementConfig);
      if (oldWithSameId != null
          && !elementsEquivalent(annotatedElement, rawElementsById.get(elementConfig.getId()))) {
        throw new IllegalArgumentException(
            String.format(
                "Found 2 LoggableElements with the same id %d:\n\n%s\nand\n\n%s",
                elementConfig.getId(),
                annotatedElement,
                rawElementsById.get(elementConfig.getId())));
      }
      rawElementsById.put(elementConfig.getId(), annotatedElement);

      ValidatedLoggableElement oldWithSameName =
          elementsByName.put(elementConfig.getName(), elementConfig);
      if (oldWithSameName != null
          && !elementsEquivalent(
              annotatedElement, rawElementsByName.get(elementConfig.getName()))) {
        throw new IllegalArgumentException(
            String.format(
                "Found 2 LoggableElements with the same name %s:\n\n%s\nand\n\n%s",
                elementConfig.getName(),
                annotatedElement,
                rawElementsByName.get(elementConfig.getName())));
      }
      rawElementsByName.put(elementConfig.getName(), annotatedElement);
    }
    checkState(
        elementsByName.containsKey(UNDEFINED_VE_NAME)
            && elementsByName.get(UNDEFINED_VE_NAME).getId() == SoyLogger.UNDEFINED_VE_ID,
        "Logging config is missing UndefinedVe.");
    return new ValidatedLoggingConfig(ImmutableMap.copyOf(elementsByName));
  }

  /**
   * This is used to allow duplicates for "equivalent" elements.
   *
   * <p>Certain aspects of an element don't matter for equivalence. For example, the location
   * (package and class name) of the metadata file doesn't matter, because as long as the metadata
   * is equivalent it doesn't matter which file it gets it from.
   */
  private static boolean elementsEquivalent(
      AnnotatedLoggableElement a1, AnnotatedLoggableElement a2) {
    AnnotatedLoggableElement mod1 = createForComparison(a1);
    AnnotatedLoggableElement mod2 = createForComparison(a2);
    return mod1.equals(mod2);
  }

  private static AnnotatedLoggableElement createForComparison(AnnotatedLoggableElement e) {
    return e.toBuilder().clearJavaPackage().clearJsPackage().clearClassName().build();
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
    static ValidatedLoggableElement create(AnnotatedLoggableElement annotatedElement) {
      LoggableElement element = annotatedElement.getElement();
      return new AutoValue_ValidatedLoggingConfig_ValidatedLoggableElement(
          element.getName(),
          element.getId(),
          Optional.ofNullable(Strings.emptyToNull(element.getProtoType())),
          annotatedElement.getJavaPackage(),
          annotatedElement.getJsPackage(),
          annotatedElement.getClassName(),
          annotatedElement.getHasMetadata());
    }

    ValidatedLoggableElement() {}

    public abstract String getName();

    public abstract long getId();

    public abstract Optional<String> getProtoName();

    public abstract String getJavaPackage();

    public abstract String getJsPackage();

    public abstract String getClassName();

    public abstract boolean hasMetadata();

    /** The name of the generated method to access the VE metadata for this VE ID. */
    public final String getGeneratedVeMetadataMethodName() {
      return String.format("v%s", getId());
    }
  }
}
