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

package com.google.template.soy.jssrc.dsl;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.internal.KytheMode;

/** Options for code formatting in {@link CodeChunk#getCode} et al. */
@AutoValue
public abstract class FormatOptions {

  public static final FormatOptions JSSRC = builder().build();

  public static Builder builder() {
    return new AutoValue_FormatOptions.Builder()
        .setHtmlEscapeStrings(true)
        .setUseTsxLineBreaks(false)
        .setKytheMode(KytheMode.DISABLED);
  }

  /**
   * Whether to apply {@code FormattingContext.applyTsxLineBreaks()}, which will do things like
   * limit lines to 80 chars, and break lines between curly braces to try to make the gencode more
   * readable.
   */
  public abstract boolean useTsxLineBreaks();

  /**
   * Whether to escape all string literals to ASCII. Usually true when producing code for
   * compilation and false when transpiling / creating source code.
   */
  public abstract boolean htmlEscapeStrings();

  public abstract Builder toBuilder();

  public abstract KytheMode kytheMode();

  /** Builder. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setUseTsxLineBreaks(boolean useTsxLineBreaks);

    public abstract Builder setHtmlEscapeStrings(boolean htmlEscapeStrings);

    public abstract Builder setKytheMode(KytheMode kytheMode);

    public abstract FormatOptions build();
  }
}
