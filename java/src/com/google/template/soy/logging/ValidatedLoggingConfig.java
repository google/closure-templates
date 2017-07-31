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

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.BaseUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A validated wrapper of {@link LoggingConfig}.
 *
 * <p>Ensures that there are no duplicate names or ids and Enables easy lookup.
 */
public final class ValidatedLoggingConfig {
  public static final ValidatedLoggingConfig EMPTY =
      new ValidatedLoggingConfig(ImmutableMap.<String, ValidatedLoggableElement>of());

  /**
   * Parses the logging config proto into a {@link ValidatedLoggingConfig}.
   *
   * @throws IllegalArgumentException if there is an error during parsing.
   */
  public static ValidatedLoggingConfig create(LoggingConfig configProto) {
    Map<String, ValidatedLoggableElement> elementsByName = new LinkedHashMap<>();
    Map<Long, ValidatedLoggableElement> elementsById = new LinkedHashMap<>();
    for (LoggableElement element : configProto.getElementList()) {
      String name = element.getName();
      checkArgument(BaseUtils.isDottedIdentifier(name), "'%s' is not a valid identifier", name);
      ValidatedLoggableElement elementConfig = ValidatedLoggableElement.create(element);
      ValidatedLoggableElement oldWithSameId =
          elementsById.put(elementConfig.getId(), elementConfig);
      if (oldWithSameId != null) {
        throw new IllegalArgumentException(
            String.format(
                "Found 2 LoggableElements with the same id %d: %s and %s",
                elementConfig.getId(), oldWithSameId.getName(), elementConfig.getName()));
      }
      ValidatedLoggableElement oldWithSameName =
          elementsByName.put(elementConfig.getName(), elementConfig);
      if (oldWithSameName != null) {
        throw new IllegalArgumentException(
            String.format(
                "Found 2 LoggableElements with the same name %s, their ids are %d and %d",
                elementConfig.getName(), oldWithSameName.getId(), elementConfig.getId()));
      }
    }
    return new ValidatedLoggingConfig(ImmutableMap.copyOf(elementsByName));
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

  /** A validated wrapper for {@link LoggableElement}. */
  @AutoValue
  public abstract static class ValidatedLoggableElement {
    static ValidatedLoggableElement create(LoggableElement element) {
      return new AutoValue_ValidatedLoggingConfig_ValidatedLoggableElement(
          element.getName(),
          element.getId(),
          element.getProtoType().isEmpty()
              ? Optional.<String>absent()
              : Optional.of(element.getProtoType()));
    }

    ValidatedLoggableElement() {}

    public abstract String getName();

    public abstract long getId();

    public abstract Optional<String> getProtoName();
  }
}
