/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.msgs.restricted;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents a placeholder within a message.
 *
 */
public final class SoyMsgPlaceholderPart extends SoyMsgPart {

  /** The placeholder name (as seen by translators). */
  private final String placeholderName;

  /** An example for the placeholder to help translators. Optional. */
  @Nullable private final String placeholderExample;

  /**
   * @param placeholderName The placeholder name (as seen by translators).
   * @param placeholderExample An optional example
   */
  public SoyMsgPlaceholderPart(String placeholderName, @Nullable String placeholderExample) {
    this.placeholderName = checkNotNull(placeholderName);
    this.placeholderExample = placeholderExample;
  }

  /** Returns the placeholder name (as seen by translators). */
  public String getPlaceholderName() {
    return placeholderName;
  }

  /** Returns the (optional) placeholder example (as seen by translators). */
  @Nullable
  public String getPlaceholderExample() {
    return placeholderExample;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SoyMsgPlaceholderPart)) {
      return false;
    }
    SoyMsgPlaceholderPart otherPlacholder = (SoyMsgPlaceholderPart) other;
    return placeholderName.equals(otherPlacholder.placeholderName)
        && Objects.equals(placeholderExample, otherPlacholder.placeholderExample);
  }

  @Override
  public int hashCode() {
    return Objects.hash(SoyMsgPlaceholderPart.class, placeholderName, placeholderExample);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("Placeholder")
        .omitNullValues()
        .addValue(placeholderName)
        .add("ex", placeholderExample)
        .toString();
  }
}
