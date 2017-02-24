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

package com.google.template.soy.jbcsrc.runtime;

import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.restricted.SoyString;
import java.io.IOException;

/**
 * A simple Tombstone SoyValue for state transitions in our SoyValueProvider subtypes.
 *
 * <p>This should never be exposed to users or end up being accessed via a template.
 */
final class TombstoneValue extends SoyAbstractValue implements SoyString {
  static final TombstoneValue INSTANCE = new TombstoneValue();

  @Override
  public void render(Appendable appendable) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String coerceToString() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean coerceToBoolean() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "TOMBSTONE";
  }

  private TombstoneValue() {}
}
