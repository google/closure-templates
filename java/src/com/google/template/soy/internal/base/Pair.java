/*
 * Copyright 2007 Google Inc.
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

package com.google.template.soy.internal.base;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * An immutable, semantic-free ordered pair of nullable values. These can be accessed using the
 * {@link #first} and {@link #second} fields. Equality and hashing are defined in the natural way.
 *
 * <p>This type is devoid of semantics, best used for simple mechanical aggregations of unrelated
 * values in implementation code. Avoid using it in your APIs, preferring an explicit type that
 * conveys the exact semantics of the data. For example, instead of: <xmp>
 *
 * <p>Pair<T, T> findMinAndMax(List<T> list) {...}</xmp>
 *
 * <p>... use: <xmp>
 *
 * <p>Range<T> findRange(List<T> list) {...}</xmp>
 *
 * <p>This usually involves creating a new custom value-object type. This is difficult to do "by
 * hand" in Java, but avoid the temptation to extend {@code Pair} to accomplish this; consider using
 * the utilities {@link com.google.common.auto.AutoValue} to help you with this instead.
 *
 */
public final class Pair<A, B> {

  /** Creates a new pair containing the given elements in order. */
  public static <A, B> Pair<A, B> of(@Nullable A first, @Nullable B second) {
    return new Pair<>(first, second);
  }

  /** The first element of the pair. */
  public final A first;

  /** The second element of the pair. */
  public final B second;

  private Pair(@Nullable A first, @Nullable B second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof Pair<?, ?>) {
      Pair<?, ?> that = (Pair<?, ?>) object;
      return Objects.equals(this.first, that.first) && Objects.equals(this.second, that.second);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(first, second);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation returns a string in the form {@code (first, second)}, where {@code
   * first} and {@code second} are the String representations of the first and second elements of
   * this pair, as given by {@link String#valueOf(Object)}. Subclasses are free to override this
   * behavior.
   */
  @Override
  public String toString() {
    return "(" + first + ", " + second + ")";
  }
}
