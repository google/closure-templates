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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.restricted.UndefinedData;
import java.util.function.BiConsumer;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A record containing name-to-value mappings referred to as fields. Each name is a string and each
 * value is a SoyValue (can be unresolved).
 */
@ParametersAreNonnullByDefault
public abstract class SoyRecord extends SoyLegacyObjectMap {

  /**
   * Checks whether this SoyRecord has a field of the given name.
   *
   * @param name The field name to check.
   * @return Whether this SoyRecord has a field of the given name.
   */
  public abstract boolean hasField(RecordProperty name);

  /**
   * @deprecated Call {@link #hasField(RecordSymbol)} instead
   */
  @Deprecated
  public final boolean hasField(String name) {
    return hasField(RecordProperty.get(name));
  }

  /**
   * Gets a field value of this SoyRecord.
   *
   * @param name The field name to get.
   * @return The field value for the given field name, or null if no such field name.
   */
  public abstract SoyValue getField(RecordProperty name);

  /**
   * @deprecated Call {@link #getField(RecordSymbol)} instead
   */
  @Deprecated
  public final SoyValue getField(String name) {
    return getField(RecordProperty.get(name));
  }

  /**
   * Gets a provider of a field value of this SoyRecord.
   *
   * @param name The field name to get.
   * @return A provider of the field value for the given field name, or null if no such field name.
   */
  public abstract SoyValueProvider getFieldProvider(RecordProperty name);

  /**
   * @deprecated Call {@link #getFieldProvider(RecordSymbol)} instead
   */
  @Deprecated
  public final SoyValueProvider getFieldProvider(String name) {
    return getFieldProvider(RecordProperty.get(name));
  }

  /**
   * Returns the value of a positional parameter when invoking the positional template method from
   * the record template method. Returns UndefinedData if no such named parameter exists in this
   * record, indicating that the parameter default should be applied in the positional template
   * method.
   */
  public final SoyValueProvider getPositionalParam(RecordProperty name) {
    SoyValueProvider provider = getFieldProvider(name);
    return provider != null ? provider : UndefinedData.INSTANCE;
  }

  /** Returns a view of this object as a java map. The elements of the map are sorted by key. */
  public abstract ImmutableMap<String, SoyValueProvider> recordAsMap();

  public abstract void forEach(BiConsumer<RecordProperty, ? super SoyValueProvider> action);

  public abstract int recordSize();

  @Override
  public final SoyRecord asSoyRecord() {
    return this;
  }
}
