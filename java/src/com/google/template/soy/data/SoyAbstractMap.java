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

import java.io.IOException;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Abstract implementation of SoyMap.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that extend this class.
 *
 */
@ParametersAreNonnullByDefault
public abstract class SoyAbstractMap extends SoyAbstractValue implements SoyMap {


  @Override public SoyValue getItem(SoyValue key) {
    SoyValueProvider valueProvider = getItemProvider(key);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }


  // -----------------------------------------------------------------------------------------------
  // SoyValue.


  @Override public final String coerceToString() {
    StringBuilder mapStr = new StringBuilder();
    try {
      render(mapStr);
    } catch (IOException e) {
      throw new RuntimeException(e);  // impossible
    }
    return mapStr.toString();
  }

  @Override public void render(Appendable appendable) throws IOException {
    appendable.append('{');

    boolean isFirst = true;
    for (SoyValue key : getItemKeys()) {
      SoyValue value = getItem(key);
      if (isFirst) {
        isFirst = false;
      } else {
        appendable.append(", ");
      }
      key.render(appendable);
      appendable.append(": ");
      value.render(appendable);
    }
    appendable.append('}');
  }

  @Override public final boolean equals(Object other) {
    // Instance equality, to match Javascript behavior.
    return this == other;
  }


  @Override public final boolean coerceToBoolean() {
    return true;
  }

  @Override public String toString() {
    // TODO(gboyer): Remove this override, and instead change RenderVisitor to use coerceToString()
    // instead of simply toString().  Alternately, have SoyAbstractValue ensure that toString()
    // always matchse coerceToString().
    return coerceToString();
  }
}
