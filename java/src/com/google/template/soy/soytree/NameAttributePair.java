/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;

/**
 * A name-attribute pair (e.g. {@code <name>="<attribute>"}) as parsed from a soy command.
 */
public final class NameAttributePair {
  private final SourceLocation location;
  private final String key;
  private final String value;

  public NameAttributePair(String key, String value, SourceLocation location) {
    this.key = checkNotNull(key);
    this.value = checkNotNull(value);
    this.location = checkNotNull(location);
  }

  public String getName() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public SourceLocation getLocation() {
    return location;
  }
}
