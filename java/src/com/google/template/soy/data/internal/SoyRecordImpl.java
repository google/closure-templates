/*
 * Copyright 2020 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import java.io.IOException;
import java.util.function.BiConsumer;

/** A record implementation. */
public final class SoyRecordImpl extends SoyRecord {

  public static final SoyRecordImpl EMPTY = new SoyRecordImpl(new ParamStore());

  private final ParamStore map;

  public SoyRecordImpl(ParamStore map) {
    this.map = map.freeze();
  }

  @Override
  public ImmutableMap<String, SoyValueProvider> recordAsMap() {
    return ImmutableSortedMap.copyOf(map.asStringMap());
  }

  @Override
  public void forEach(BiConsumer<RecordProperty, ? super SoyValueProvider> action) {
    map.forEach(action);
  }

  @Override
  public int recordSize() {
    return map.size();
  }

  @Override
  public String coerceToString() {
    LoggingAdvisingAppendable mapStr = LoggingAdvisingAppendable.buffering();
    try {
      render(mapStr);
    } catch (IOException e) {
      throw new AssertionError(e); // impossible
    }
    return mapStr.toString();
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable.append('{');

    boolean isFirst = true;
    for (var property : map.properties()) {
      if (isFirst) {
        isFirst = false;
      } else {
        appendable.append(", ");
      }
      appendable.append(property.getName()).append(": ");
      map.getFieldProvider(property).resolve().render(appendable);
    }
    appendable.append('}');
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public boolean hasField(RecordProperty name) {
    return map.containsKey(name);
  }

  @Override
  public SoyValueProvider getFieldProvider(RecordProperty name) {
    return map.getFieldProvider(name);
  }

  @Override
  public SoyValue getField(RecordProperty name) {
    SoyValueProvider svp = map.getFieldProvider(name);
    if (svp == null) {
      return null;
    }
    return svp.resolve();
  }

  ParamStore getParamStore() {
    return map;
  }

  @Override
  public String getSoyTypeName() {
    return "record";
  }

  @Override
  public String toString() {
    return coerceToString();
  }
}
