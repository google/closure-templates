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

package com.google.template.soy.data;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstract implementation of SoyList.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that extend this class.
 *
 */
@ParametersAreNonnullByDefault
public abstract class SoyAbstractList extends SoyAbstractValue implements SoyList {


  @Override public final SoyValue get(int index) {
    SoyValueProvider valueProvider = getProvider(index);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }


  // -----------------------------------------------------------------------------------------------
  // SoyMap.


  @Override public final int getItemCnt() {
    return length();
  }


  @Override @Nonnull public final Iterable<IntegerData> getItemKeys() {
    ImmutableList.Builder<IntegerData> indicesBuilder = ImmutableList.builder();
    for (int i = 0, n = length(); i < n; i++) {
      indicesBuilder.add(IntegerData.forValue(i));
    }
    return indicesBuilder.build();
  }


  @Override public final boolean hasItem(SoyValue key) {
    int index = getIntegerIndex(key);
    return 0 <= index && index < length();
  }


  @Override public final SoyValue getItem(SoyValue key) {
    return get(getIntegerIndex(key));
  }


  @Override public final SoyValueProvider getItemProvider(SoyValue key) {
    return getProvider(getIntegerIndex(key));
  }


  /**
   * Gets the integer index out of a SoyValue key, or throws SoyDataException if the key is not an
   * integer.
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


  @Override public final String coerceToString() {

    StringBuilder listStr = new StringBuilder();
    try {
      render(listStr);
    } catch (IOException e) {
      throw new RuntimeException(e);  // impossible
    }
    return listStr.toString();
  }

  @Override public void render(Appendable appendable) throws IOException {
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


  // -----------------------------------------------------------------------------------------------
  // SoyValue.


  /**
   * This implementation uses object identity (to match JS behavior).
   *
   * Note: Users will eventually be able to create their own implementations if they desire.
   */
  @Override public final boolean equals(Object other) {
    return this == other;
  }


  /**
   * This implementation is always truthy (to match JS behavior).
   *
   * Note: Users will eventually be able to create their own implementations if they desire.
   */
  @Override public final boolean coerceToBoolean() {
    return true;
  }
}
