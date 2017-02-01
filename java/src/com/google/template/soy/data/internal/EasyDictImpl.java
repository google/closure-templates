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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyEasyDict;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.internal.base.Pair;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Internal implementation of SoyEasyDict. Do not use directly. Instead, use
 * SoyValueConverter.newEasyDict*().
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Deprecated
@ParametersAreNonnullByDefault
public final class EasyDictImpl extends AbstractDict implements SoyEasyDict {

  /** The instance of SoyValueConverter to use for internal conversions. */
  private final SoyValueConverter converter;

  /** Whether this instance is still mutable (immutability cannot be undone, of course). */
  private boolean isMutable;

  /**
   * Important: Do not use outside of Soy code (treat as superpackage-private).\
   *
   * @param converter The instance of SoyValueConverter to use for internal conversions.
   */
  public EasyDictImpl(SoyValueConverter converter) {
    super(new LinkedHashMap<String, SoyValueProvider>());

    this.converter = converter;
    this.isMutable = true;
  }

  // -----------------------------------------------------------------------------------------------
  // SoyEasyDict.

  @Override
  public void setField(String name, SoyValueProvider valueProvider) {
    // TODO: Maybe eventually transition to a state where we can enforce that field names are
    // always identifiers. Currently, we can't do this because some existing usages use
    // non-identifier keys.
    // if (! BaseUtils.isIdentifier(name)) {
    //   throw new SoyDataException(
    //       "SoyRecord field name must be an identifier (got \"" + name + "\").");
    // }
    Preconditions.checkState(isMutable, "Cannot modify immutable SoyEasyDict.");
    @SuppressWarnings("unchecked") // As specified in the constructor.
    Map<String, SoyValueProvider> concreteMap = (Map<String, SoyValueProvider>) providerMap;
    concreteMap.put(name, checkNotNull(valueProvider));
  }

  @Override
  public void delField(String name) {
    Preconditions.checkState(isMutable, "Cannot modify immutable SoyEasyDict.");
    providerMap.remove(name);
  }

  @Override
  public void setFieldsFromJavaStringMap(Map<String, ?> javaStringMap) {
    for (Map.Entry<String, ?> entry : javaStringMap.entrySet()) {
      setField(entry.getKey(), converter.convert(entry.getValue()));
    }
  }

  @Override
  public void set(String dottedName, @Nullable Object value) {
    Pair<SoyRecord, String> pair = getLastRecordAndLastName(dottedName, true);
    if (!(pair.first instanceof SoyEasyDict)) {
      throw new SoyDataException("Cannot set data at dotted name '" + dottedName + "'.");
    }
    ((SoyEasyDict) pair.first).setField(pair.second, converter.convert(value));
  }

  @Override
  public SoyValue get(String dottedName) {
    Pair<SoyRecord, String> pair = getLastRecordAndLastName(dottedName, false);
    return (pair.first != null) ? pair.first.getField(pair.second) : null;
  }

  // -----------------------------------------------------------------------------------------------
  // Private helpers.

  /**
   * Private helper to get the last record and last field name for a given string of dot-separated
   * field names. The last name is the part after the last dot. The last record is the SoyRecord
   * that the rest of the name resolves to, or null if it doesn't resolve to a SoyRecord. In other
   * words, the last name should be resolved as a field name of the last record (though this method
   * does not attempt to perform this resolution of the last part).
   *
   * @param dottedName One or more field names, dot-separated.
   * @param doCreateRecordsIfNecessary Whether to create intermediate records if necessary. This
   *     option supports implementing method set().
   * @return A pair of the last record and last field name. This method returns null for the last
   *     record if the portion of the dotted name other than the last name does not resolve to a
   *     SoyRecord. However, note that if the dotted name only contains one name, then this method
   *     returns this SoyRecord itself as the last record.
   */
  private Pair<SoyRecord, String> getLastRecordAndLastName(
      String dottedName, boolean doCreateRecordsIfNecessary) {

    String[] names = dottedName.split("[.]");
    int n = names.length;

    String lastName = names[n - 1];

    SoyRecord lastRecord;
    if (n == 1) {
      lastRecord = this;
    } else {
      lastRecord = this;
      for (int i = 0; i <= n - 2; i++) {
        SoyValue value = lastRecord.getField(names[i]);
        if (value instanceof SoyRecord) {
          lastRecord = (SoyRecord) value;
        } else if (value == null
            && doCreateRecordsIfNecessary
            && lastRecord instanceof SoyEasyDict) {
          SoyEasyDict newRecord = new EasyDictImpl(converter);
          ((SoyEasyDict) lastRecord).setField(names[i], newRecord);
          lastRecord = newRecord;
        } else {
          lastRecord = null;
          break;
        }
      }
    }

    return Pair.of(lastRecord, lastName);
  }
}
