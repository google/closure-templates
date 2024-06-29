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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyIterable;
import com.google.template.soy.data.SoyValueProvider;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.ParametersAreNonnullByDefault;

/** A Set implementation. */
@ParametersAreNonnullByDefault
public final class IterableImpl extends SoyAbstractValue implements SoyIterable {

  public static final SoyIterable EMPTY_ITERABLE = forIterable(ImmutableList.of());

  public static SoyIterable forIterable(Iterable<? extends SoyValueProvider> impl) {
    if (impl instanceof List) {
      return ListImpl.forProviderList((List<? extends SoyValueProvider>) impl);
    } else if (impl instanceof Set) {
      return new SetImpl((Set<? extends SoyValueProvider>) impl);
    } else {
      return new IterableImpl(impl);
    }
  }

  private final Iterable<? extends SoyValueProvider> impl;

  public IterableImpl(Iterable<? extends SoyValueProvider> impl) {
    this.impl = impl;
  }

  @Override
  public Iterator<? extends SoyValueProvider> javaIterator() {
    return impl.iterator();
  }

  @Override
  public List<? extends SoyValueProvider> asJavaList() {
    if (impl instanceof List) {
      return (List<? extends SoyValueProvider>) impl;
    }
    return ImmutableList.copyOf(impl);
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return impl.hashCode();
  }

  @Override
  public boolean coerceToBoolean() {
    return true;
  }

  @Override
  public String coerceToString() {
    return "[Iterable]";
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append(coerceToString());
  }
}
