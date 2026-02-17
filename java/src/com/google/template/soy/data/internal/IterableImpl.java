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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyIterable;
import com.google.template.soy.data.SoyValueProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.ParametersAreNonnullByDefault;

/** A Set implementation. */
@ParametersAreNonnullByDefault
public final class IterableImpl extends SoyIterable {

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

  public static <T> SoyIterable forJavaIterable(
      Iterable<T> items, Function<? super T, ? extends SoyValueProvider> mapper) {
    if (items instanceof List) {
      // Create a list backed by a Java list which has eagerly converted each value into a lazy
      // value provider. Specifically, the list iteration is done eagerly so that the lazy value
      // provider can cache its value.
      return ListImpl.forProviderList(
          ((List<T>) items).stream().map(mapper).collect(toImmutableList()));
    }
    if (items instanceof Set) {
      return new SetImpl(((Set<T>) items).stream().map(mapper).iterator());
    }

    return new IterableImpl(
        new Iterable<SoyValueProvider>() {
          // Cache accessed providers to avoid converting the same object multiple times.
          private final List<SoyValueProvider> providers = new ArrayList<>();

          @Override
          public Iterator<SoyValueProvider> iterator() {
            return new AbstractIterator<SoyValueProvider>() {
              int index = 0;
              final Iterator<T> delegate = items.iterator();

              @Override
              protected SoyValueProvider computeNext() {
                if (delegate.hasNext()) {
                  var obj = delegate.next();
                  SoyValueProvider provider;
                  if (index < providers.size()) {
                    provider = providers.get(index);
                  } else {
                    provider = mapper.apply(obj);
                    providers.add(provider);
                  }
                  index++;
                  return provider;
                }
                return endOfData();
              }
            };
          }
        });
  }

  private final Iterable<? extends SoyValueProvider> impl;

  private IterableImpl(Iterable<? extends SoyValueProvider> impl) {
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
  public Iterable<? extends SoyValueProvider> asJavaIterable() {
    return impl;
  }

  @Override
  public boolean equals(Object other) {
    return other == this;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String coerceToString() {
    return "[Iterable]";
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append(coerceToString());
  }

  @Override
  public String getSoyTypeName() {
    return "iterable";
  }
}
