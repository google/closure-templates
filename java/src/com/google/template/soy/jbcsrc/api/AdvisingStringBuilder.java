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

package com.google.template.soy.jbcsrc.api;

/**
 * An {@link AdvisingAppendable} that delegates to a StringBuilder.
 *
 * <p>NOTE: {@link #softLimitReached()} is hard coded to return {@code false}, since it is assumed
 * that users will not care about limiting buffer usage.
 */
public final class AdvisingStringBuilder implements AdvisingAppendable {
  private final StringBuilder delegate = new StringBuilder();

  @Override
  public AdvisingStringBuilder append(CharSequence s) {
    delegate.append(s);
    return this;
  }

  @Override
  public AdvisingStringBuilder append(CharSequence s, int start, int end) {
    delegate.append(s, start, end);
    return this;
  }

  @Override
  public AdvisingStringBuilder append(char c) {
    delegate.append(c);
    return this;
  }

  @Override
  public boolean softLimitReached() {
    return false;
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  public String getAndClearBuffer() {
    String value = delegate.toString();
    delegate.setLength(0);
    return value;
  }
}
