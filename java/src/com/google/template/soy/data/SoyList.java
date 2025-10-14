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
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.NumericCoercions;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A list containing values. Each value is a SoyValue (can be unresolved).
 *
 * <p>A list also supports the map interface. In that usage, the item keys are the list indices in
 * the form of IntegerData.
 */
@ParametersAreNonnullByDefault
public abstract class SoyList extends SoyIterable {

  /**
   * Gets the length of this SoyList.
   *
   * @return The length.
   */
  public abstract int length();

  /**
   * Gets a Java list of all value providers in this SoyList. Note that value providers are often
   * just the values themselves, since all values are also providers.
   *
   * @return A Java list of all value providers.
   */
  @Nonnull
  @Override
  public abstract List<? extends SoyValueProvider> asJavaList();

  /**
   * Gets a Java list all values in this SoyList. All value providers will be eagerly resolved.
   *
   * @return A Java list of all resolved values.
   */
  @Nonnull
  public List<? extends SoyValue> asResolvedJavaList() {
    return Lists.transform(asJavaList(), SoyValueProvider::resolve);
  }

  /**
   * Gets a value of this SoyList.
   *
   * @param index The index to get.
   * @return The value for the given index, or null if no such index.
   */
  @Nullable
  public SoyValue get(int index) {
    SoyValueProvider valueProvider = getProvider(index);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }

  /**
   * Gets a provider of a value of this SoyList.
   *
   * @param index The index to get.
   * @return A provider of the value for the given index, or null if no such index.
   */
  public abstract SoyValueProvider getProvider(int index);

  // -----------------------------------------------------------------------------------------------
  // SoyLegacyObjectMap methods

  @Override
  public final int getItemCnt() {
    return length();
  }

  @Override
  @Nonnull
  public final ImmutableList<IntegerData> getItemKeys() {
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
  private static int getIntegerIndex(SoyValue key) {
    if (key instanceof StringData) {
      try {
        // TODO(gboyer): Remove this case as non-compliant code is fixed. However, since this works
        // in Javascript, it is particularly difficult to fix callers.  (internal) b/11416037
        return Integer.parseInt(key.stringValue());
      } catch (IllegalArgumentException e) {
        throw new SoyDataException("\"" + key + "\" is not a valid list index (must be an int)", e);
      }
    } else {
      return NumericCoercions.safeInt(key.coerceToIndex());
    }
  }

  // -----------------------------------------------------------------------------------------------
  // SoyValue methods

  @Override
  public final String coerceToString() {

    LoggingAdvisingAppendable listStr = LoggingAdvisingAppendable.buffering();
    try {
      render(listStr);
    } catch (IOException e) {
      throw new AssertionError(e); // impossible
    }
    return listStr.toString();
  }

  @Override
  public final void render(LoggingAdvisingAppendable appendable) throws IOException {
    boolean isFirst = true;
    for (SoyValueProvider valueProvider : asJavaList()) {
      if (isFirst) {
        isFirst = false;
      } else {
        appendable.append(",");
      }
      valueProvider.resolve().render(appendable);
    }
  }

  @Override
  public final String toString() {
    return coerceToString();
  }

  @Override
  public final String getSoyTypeName() {
    return "list";
  }
}
