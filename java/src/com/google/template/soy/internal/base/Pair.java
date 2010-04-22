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

import com.google.common.base.Objects;

import java.util.Comparator;

import javax.annotation.Nullable;

/**
 * An immutable, semantic-free ordered pair of nullable values. These can be
 * accessed using the {@link #getFirst} and {@link #getSecond} methods. Equality
 * and hashing are defined in the natural way.
 *
 * <p>This type is devoid of semantics, best used for simple mechanical
 * aggregations of unrelated values in implementation code. Avoid using it in
 * your APIs, preferring an explicit type that conveys the exact semantics of
 * the data. For example, instead of: <pre>   {@code
 *
 *   Pair<T, T> findMinAndMax(List<T> list) {...}}</pre>
 *
 * ... use: <pre>   {@code
 *
 *   Range<T> findRange(List<T> list) {...}}</pre>
 *
 * This usually involves creating a new custom value-object type. This is
 * difficult to do "by hand" in Java, but avoid the temptation to extend {@code
 * Pair} to accomplish this; consider using the utilities {@link
 * com.google.common.labs.misc.ComparisonKeys} or {@link
 * com.google.common.labs.misc.ValueType} to help you with this instead.
 *
 * @author Kevin Bourrillion
 */
public class Pair<A, B> {

  /**
   * Creates a new pair containing the given elements in order.
   */
  public static <A, B> Pair<A, B> of(@Nullable A first, @Nullable B second) {
    return new Pair<A, B>(first, second);
  }

  /**
   * The first element of the pair; see also {@link #getFirst}.
   */
  public final A first;

  /**
   * The second element of the pair; see also {@link #getSecond}.
   */
  public final B second;

  /**
   * Constructor. It is usually easier to call {@link #of}.
   */
  public Pair(@Nullable A first, @Nullable B second) {
    this.first = first;
    this.second = second;
  }

  /**
   * Returns the first element of this pair; see also {@link #first}.
   */
  public A getFirst() {
    return first;
  }

  /**
   * Returns the second element of this pair; see also {@link #second}.
   */
  public B getSecond() {
    return second;
  }

  /**
   * Returns a comparator that compares two Pair objects by comparing the
   * result of {@link #getFirst()} for each.
   */
  @SuppressWarnings("unchecked")
  public static <A extends Comparable, B> Comparator<Pair<A,B>> compareByFirst() {
    return (Comparator) FirstComparator.FIRST_COMPARATOR;
  }

  /**
   * Returns a comparator that compares two Pair objects by comparing the
   * result of {@link #getSecond()} for each.
   */
  @SuppressWarnings("unchecked")
  public static <A, B extends Comparable> Comparator<Pair<A,B>> compareBySecond() {
    return (Comparator) SecondComparator.SECOND_COMPARATOR;
  }

  // uses raw Comparable to support classes defined without generics
  @SuppressWarnings("unchecked")
  private enum FirstComparator implements Comparator<Pair<Comparable,Object>> {
    FIRST_COMPARATOR;

    public int compare(Pair<Comparable, Object> pair1, Pair<Comparable, Object> pair2) {
      return pair1.getFirst().compareTo(pair2.getFirst());
    }
  }

  // uses raw Comparable to support classes defined without generics
  @SuppressWarnings("unchecked")
  private enum SecondComparator implements Comparator<Pair<Object,Comparable>> {
    SECOND_COMPARATOR;

    public int compare(Pair<Object, Comparable> pair1, Pair<Object, Comparable> pair2) {
      return pair1.getSecond().compareTo(pair2.getSecond());
    }
  }

  @Override public boolean equals(@Nullable Object object) {
    if (object instanceof Pair<?,?>) {
      Pair<?,?> that = (Pair<?,?>) object;
      return Objects.equal(this.first, that.first) && Objects.equal(this.second, that.second);
    }
    return false;
  }

  @Override public int hashCode() {
    return Objects.hashCode(first, second);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation returns a string in the form
   * {@code (first, second)}, where {@code first} and {@code second} are the
   * String representations of the first and second elements of this pair, as
   * given by {@link String#valueOf(Object)}. Subclasses are free to override
   * this behavior.
   */
  @Override public String toString() {
    return "(" + first + ", " + second + ")";
  }
}
