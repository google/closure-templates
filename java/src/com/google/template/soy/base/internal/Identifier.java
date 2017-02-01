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
import com.google.template.soy.base.SourceLocation;

/**
 * A simple tuple of an identifier and a source location.
 *
 * <p>This is either a full dotted identifier or a partial identifier.
 */
@AutoValue
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
    Type type;
    int dotIndex = identifier.indexOf('.');
    if (dotIndex == 0) {
      type = Type.DOT_IDENT;
      checkArgument(BaseUtils.isIdentifierWithLeadingDot(identifier));
    } else {
      checkArgument(BaseUtils.isDottedIdentifier(identifier));
      type = dotIndex == -1 ? Type.SINGLE_IDENT : Type.DOTTED_IDENT;
    }
    return new AutoValue_Identifier(identifier, location, type);
  }

  /** The identifier */
  public abstract String identifier();

  public abstract SourceLocation location();

  public abstract Type type();

  /**
   * Returns true if this is a partial identifier. That is, a single dot followed by a single
   * identifier.
   */
  public boolean isPartialIdentifier() {
    return type() == Type.DOT_IDENT;
  }
}
