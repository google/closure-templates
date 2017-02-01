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

package com.google.template.soy.basetree;

import javax.annotation.Nullable;

/**
 * Value class representing a known upper bound for the syntax version of a node.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class SyntaxVersionUpperBound {

  /**
   * Returns the lower of the two given bounds. If they are the same syntax version, then returns
   * the orig bound.
   */
  public static SyntaxVersionUpperBound selectLower(
      @Nullable SyntaxVersionUpperBound origBound, SyntaxVersionUpperBound newBound) {
    if (origBound != null && origBound.syntaxVersion.num <= newBound.syntaxVersion.num) {
      return origBound;
    } else {
      return newBound;
    }
  }

  /** The syntax version upper bound (exclusive!). */
  public final SyntaxVersion syntaxVersion;

  /** A user-friendly explanation of the reason for the bound. */
  public final String reasonStr;

  /**
   * @param syntaxVersion The syntax version upper bound (exclusive!).
   * @param reasonStr A user-friendly explanation of the reason for the bound.
   */
  public SyntaxVersionUpperBound(SyntaxVersion syntaxVersion, String reasonStr) {
    this.syntaxVersion = syntaxVersion;
    this.reasonStr = reasonStr;
  }
}
