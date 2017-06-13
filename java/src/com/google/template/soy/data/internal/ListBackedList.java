/*
 * Copyright 2013 Google Inc.
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
import com.google.common.collect.Lists;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyList in terms of a list. Do not use directly.
 *
 * <p>There are two implementations - SoyEasyList which creates its own list and supports
 * modification via the API, and ListImpl, which wraps existing lists.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@ParametersAreNonnullByDefault
abstract class ListBackedList extends SoyAbstractValue implements SoyList {

  /** Backing list of providers. */
  protected final List<? extends SoyValueProvider> providerList;

  /** Constructs as a view of the given list. */
  protected ListBackedList(List<? extends SoyValueProvider> providerList) {
    this.providerList = providerList;
  }

  // -----------------------------------------------------------------------------------------------
  // SoyList.

  @Override
  public final int length() {
    return providerList.size();
  }

  @Override
  @Nonnull
  public final List<? extends SoyValueProvider> asJavaList() {
    return Collections.unmodifiableList(providerList);
  }

  @Override
  @Nonnull
  public final List<SoyValue> asResolvedJavaList() {
    return Lists.transform(asJavaList(), Transforms.RESOLVE_FUNCTION);
  }

  @Override
  public final SoyValue get(int index) {
    SoyValueProvider valueProvider = getProvider(index);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }

  @Override
  public final SoyValueProvider getProvider(int index) {
    try {
      return providerList.get(index);
    } catch (IndexOutOfBoundsException e) {
      return null;
    }
  }

  // -----------------------------------------------------------------------------------------------
  // SoyMap.

  @Override
  public final int getItemCnt() {
    return length();
  }

  @Override
  @Nonnull
  public final Iterable<IntegerData> getItemKeys() {
    ImmutableList.Builder<IntegerData> indicesBuilder = ImmutableList.builder();
    for (int i = 0, n = length(); i < n; i++) {
      indicesBuilder.add(IntegerData.forValue(i));
    }
    return indicesBuilder.build();
  }

  @Override
  public final boolean hasItem(SoyValue key) {
    int index = getIntegerIndex(key);
    return 0 <= index && index < length();
  }

  @Override
  public final SoyValue getItem(SoyValue key) {
    return get(getIntegerIndex(key));
  }

  @Override
  public final SoyValueProvider getItemProvider(SoyValue key) {
    return getProvider(getIntegerIndex(key));
  }

  /**
   * Gets the integer index out of a SoyValue key, or throws SoyDataException if the key is not an
   * integer.
   *
   * @param key The SoyValue key.
   * @return The index.
   */
  protected final int getIntegerIndex(SoyValue key) {
    if (key instanceof StringData) {
      try {
        // TODO(gboyer): Remove this case as non-compliant code is fixed. However, since this works
        // in Javascript, it is particularly difficult to fix callers.  (internal) b/11416037
        return Integer.parseInt(key.stringValue());
      } catch (IllegalArgumentException e) {
        throw new SoyDataException("\"" + key + "\" is not a valid list index (must be an int)");
      }
    } else {
      return key.integerValue();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // SoyValue.

  /**
   * This implementation uses object identity (to match JS behavior).
   *
   * <p>Note: Users will eventually be able to create their own implementations if they desire.
   */
  @Override
  public final boolean equals(Object other) {
    return this == other;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  /**
   * This implementation is always truthy (to match JS behavior).
   *
   * <p>Note: Users will eventually be able to create their own implementations if they desire.
   */
  @Override
  public final boolean coerceToBoolean() {
    return true;
  }

  @Override
  public final String coerceToString() {

    StringBuilder listStr = new StringBuilder();
    try {
      render(listStr);
    } catch (IOException e) {
      throw new AssertionError(e); // impossible
    }
    return listStr.toString();
  }

  @Override
  public void render(Appendable appendable) throws IOException {
    appendable.append('[');

    boolean isFirst = true;
    for (SoyValueProvider valueProvider : asJavaList()) {
      if (isFirst) {
        isFirst = false;
      } else {
        appendable.append(", ");
      }
      valueProvider.resolve().render(appendable);
    }

    appendable.append(']');
  }
}
