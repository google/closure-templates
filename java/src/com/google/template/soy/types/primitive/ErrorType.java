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

package com.google.template.soy.types.primitive;

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.SoyType;

/**
 * A placeholder for errors during parsing.
 */
public final class ErrorType implements SoyType {


  private final String name;


  public ErrorType(String name) {
    this.name = name;
  }


  public String getName() {
    return name;
  }


  @Override public Kind getKind() {
    return Kind.ERROR;
  }


  @Override public boolean isAssignableFrom(SoyType srcType) {
    return false;
  }


  @Override public boolean isInstance(SoyValue value) {
    return false;
  }


  @Override public String toString() {
    return name;
  }


  @Override public boolean equals(Object other) {
    // We don't need to override hashCode() since we're only using equals()
    // for assertions in unit tests.
    return other != null &&
        other.getClass() == ErrorType.class &&
        ((ErrorType) other).name.equals(name);
  }
}
