/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.shared.restricted;

import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import java.io.IOException;

/** A {@link LoggingAdvisingAppendable} that tracks if it is in HTML content. */
public abstract class IsInHtmlAppendable extends LoggingAdvisingAppendable {

  /**
   * The current number of calls to {@link #enterSanitizedContentKind} without a matching {@link
   * #exitSanitizedContentKind()} after a call with a content kind that matches {@link
   * ContentKind#HTML}.
   */
  private int htmlDepth;

  @Override
  public final LoggingAdvisingAppendable enterSanitizedContentKind(ContentKind kind)
      throws IOException {
    int depth = htmlDepth;
    if (depth > 0) {
      depth++;
      if (depth < 0) {
        throw new IllegalStateException("overflowed content kind depth");
      }
      htmlDepth = depth;
    } else if (kind == ContentKind.HTML) {
      depth = 1;
      htmlDepth = 1;
    }
    doEnterSanitizedContentKind(kind);
    return this;
  }

  @ForOverride
  protected void doEnterSanitizedContentKind(ContentKind kind) throws IOException {}

  @Override
  public final LoggingAdvisingAppendable exitSanitizedContentKind() throws IOException {
    int depth = htmlDepth;
    if (depth > 0) {
      depth--;
      htmlDepth = depth;
    }
    doExitSanitizedContentKind();
    return this;
  }

  @ForOverride
  protected void doExitSanitizedContentKind() throws IOException {}

  protected final boolean isInHtml() {
    return htmlDepth > 0;
  }
}
