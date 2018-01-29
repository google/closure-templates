/*
 * Copyright 2018 Google Inc.
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

import java.util.Map;

/**
 * Signals to the Java rendering API that the wrapped {@code java.util.Map} represents a Soy {@code
 * map}, and not a {@code legacy_object_map} or record. In particular, this allows the map to
 * contain non-string keys. See discussion in {@link
 * com.google.template.soy.data.internal.DictImpl}.
 *
 * <p>If you want to use non-string keys in a map in Soy, you need to do three things:
 *
 * <ul>
 *   <li>Change the type of your map from {@code legacy_object_map} to {@code map}
 *   <li>Change the map passed in from JS from a plain JS object to an ES6 Map
 *   <li>Wrap the map passed in from Java with {@link #thisIsASoyMap}
 * </ul>
 */
public final class ThisIsASoyMap<K, V> {

  public static <K, V> ThisIsASoyMap<K, V> thisIsASoyMap(Map<K, V> delegate) {
    return new ThisIsASoyMap<>(delegate);
  }

  private final Map<K, V> delegate;

  private ThisIsASoyMap(Map<K, V> delegate) {
    this.delegate = delegate;
  }

  Map<K, V> delegate() {
    return delegate;
  }
}
