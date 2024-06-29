/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.data.internal;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoySet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.ParametersAreNonnullByDefault;

/** A Set implementation. */
@ParametersAreNonnullByDefault
public final class SetImpl extends SoyAbstractValue implements SoySet {

  private final ImmutableSet<SoyValue> providerSet;

  public SetImpl(SoyValue iterable) {
    this(iterable.javaIterator());
  }

  public SetImpl(Set<? extends SoyValueProvider> value) {
    providerSet = value.stream().map(SoyValueProvider::resolve).collect(toImmutableSet());
  }

  public SetImpl(Iterator<? extends SoyValueProvider> iterator) {
    providerSet = Streams.stream(iterator).map(SoyValueProvider::resolve).collect(toImmutableSet());
  }

  @Override
  public Iterator<? extends SoyValueProvider> javaIterator() {
    return providerSet.iterator();
  }

  @Override
  public boolean contains(SoyValue value) {
    return providerSet.contains(value);
  }

  @Override
  public int size() {
    return providerSet.size();
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return providerSet.hashCode();
  }

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
    return "[Set]";
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append(coerceToString());
  }
}
