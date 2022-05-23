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
package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

/** Adapter for using an Appendable as an AdvisingAppendable. */
final class AppendableAsAdvisingAppendable implements AdvisingAppendable {
  static AdvisingAppendable asAdvisingAppendable(Appendable appendable) {
    return appendable instanceof AdvisingAppendable
        ? (AdvisingAppendable) appendable
        : new AppendableAsAdvisingAppendable(appendable);
  }

  private final Appendable appendable;

  private AppendableAsAdvisingAppendable(Appendable appendable) {
    this.appendable = checkNotNull(appendable);
  }

  @Override
  public AdvisingAppendable append(CharSequence csq) throws IOException {
    appendable.append(csq);
    return this;
  }

  @Override
  public AdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
    appendable.append(csq, start, end);
    return this;
  }

  @Override
  public AdvisingAppendable append(char c) throws IOException {
    appendable.append(c);
    return this;
  }

  @Override
  public boolean softLimitReached() {
    // no limits can be inferred
    return false;
  }
}
