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

package com.google.template.soy.base.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceLocation;

/**
 * A simple tuple of an identifier and a source location.
 *
 * <p>This is either a full dotted identifier or a partial identifier.
 *
 * <p>Errorprone can't handle that type() is memorized. See
 * https://github.com/google/closure-templates/issues/183
 */
@AutoValue.CopyAnnotations
@AutoValue
@Immutable
@SuppressWarnings("Immutable")
public abstract class Identifier {

  /** What flavor of identifier this is. */
  public enum Type {
    /** A single identifier, for example: {@code foo} */
    SINGLE_IDENT,
    /** A partial identifier, for example: {@code .foo} */
    DOT_IDENT,
    /**
     * A dotted identifier, for example: {@code foo.bar.baz}.
     *
     * <p>Note: if you want to handle 'fully qualified names' you will typically need to handle this
     * and {@link #SINGLE_IDENT}.
     */
    DOTTED_IDENT;
  }

  /**
   * Creates a new identifier. The identifier can be either a partial (e.g. {@code .foo} or a full
   * name (e.g. {@code foo} or {@code foo.bar.bar.quux}.
   */
  public static Identifier create(String identifier, SourceLocation location) {
    checkArgument(!identifier.isEmpty());
    return new AutoValue_Identifier(identifier, identifier, location);
  }

  public static Identifier create(String identifier, String alias, SourceLocation location) {
    checkArgument(!identifier.isEmpty());
    return new AutoValue_Identifier(identifier, alias, location);
  }

  public abstract String identifier();

  public abstract String originalName();

  public abstract SourceLocation location();

  public boolean isRenamed() {
    return !identifier().equals(originalName());
  }

  @Override
  public final String toString() {
    return identifier();
  }

  // This field is only rarely accessed, memoize it.
  @Memoized
  public Type type() {
    int dotIndex = identifier().indexOf('.');
    if (dotIndex == 0) {
      checkArgument(BaseUtils.isIdentifierWithLeadingDot(identifier()));
      return Type.DOT_IDENT;
    } else {
      checkArgument(BaseUtils.isDottedIdentifier(identifier()));
      return dotIndex == -1 ? Type.SINGLE_IDENT : Type.DOTTED_IDENT;
    }
  }

  /**
   * Gets the part after the last dot in a dotted identifier. If there are no dots, returns the
   * whole identifier.
   *
   * <p><b>Important:</b> The input must be a dotted identifier. This is not checked.
   */
  public Identifier extractPartAfterLastDot() {
    String part = BaseUtils.extractPartAfterLastDot(identifier());
    return Identifier.create(
        part, location().offsetStartCol(identifier().length() - part.length()));
  }
}
