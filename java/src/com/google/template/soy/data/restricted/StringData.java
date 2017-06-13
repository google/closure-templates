/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.data.restricted;

import com.google.common.base.Preconditions;
import com.google.template.soy.data.internal.RenderableThunk;
import java.io.IOException;
import javax.annotation.concurrent.Immutable;

/**
 * String data.
 *
 * <p>Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 */
@Immutable
public abstract class StringData extends PrimitiveData implements SoyString {

  /** Static instance of StringData with value "". */
  public static final StringData EMPTY_STRING = new ConstantString("");

  private StringData() {}

  /**
   * Gets a StringData instance for the given value.
   *
   * @param value The desired value.
   * @return A StringData instance with the given value.
   */
  public static StringData forValue(String value) {
    return (value.length() == 0) ? EMPTY_STRING : new ConstantString(value);
  }

  /** Returns a StringData instance for the given {@link RenderableThunk}. */
  public static StringData forThunk(RenderableThunk thunk) {
    return new LazyString(thunk);
  }

  /** Returns the string value. */
  public abstract String getValue();

  @Override
  public String stringValue() {
    return getValue();
  }

  @Override
  public String toString() {
    return getValue();
  }

  /**
   * {@inheritDoc}
   *
   * <p>The empty string is falsy.
   */
  @Override
  public boolean coerceToBoolean() {
    return getValue().length() > 0;
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public boolean equals(Object other) {
    return other != null && getValue().equals(other.toString());
  }

  @Override
  public int hashCode() {
    return getValue().hashCode();
  }

  private static final class ConstantString extends StringData {
    final String content;

    ConstantString(String content) {
      this.content = Preconditions.checkNotNull(content);
    }

    @Override
    public void render(Appendable appendable) throws IOException {
      appendable.append(content);
    }

    @Override
    public String getValue() {
      return content;
    }
  }

  private static final class LazyString extends StringData {
    // N.B. This is nearly identical to SanitizedContent.LazyContent.  When changing this you
    // probably need to change that also.

    final RenderableThunk thunk;

    LazyString(RenderableThunk thunk) {
      this.thunk = thunk;
    }

    @Override
    public void render(Appendable appendable) throws IOException {
      thunk.render(appendable);
    }

    @Override
    public String getValue() {
      return thunk.renderAsString();
    }
  }
}
