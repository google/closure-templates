/*
 * Copyright 2023 Google Inc.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** A key for looking up a value in a SoyRecord. */
public final class RecordProperty {
  private static final ConcurrentMap<String, RecordProperty> symbols = new ConcurrentHashMap<>();

  // A few well known values
  public static final RecordProperty KEY = get("key");
  public static final RecordProperty VALUE = get("value");

  public static RecordProperty get(String name) {
    return symbols.computeIfAbsent(name, RecordProperty::new);
  }

  private final String name;

  private RecordProperty(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  // equals and hashCode not overridden since this type has identity semantics

  @Override
  public String toString() {
    return "RecordProperty{name=" + name + "}";
  }
}
