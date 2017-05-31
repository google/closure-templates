/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.exprparse;

import com.google.template.soy.base.SourceLocation;

/** Helper for manipulating tokens */
final class Tokens {

  /** Creates a new source location based on the parent source location and a given token. */
  static SourceLocation createSrcLoc(SourceLocation parentSourceLocation, Token token) {
    // The Math.max fiddling is required because some callers (in particular, unit tests)
    // instantiate the expression parser with SourceLocation.UNKNOWN.
    return new SourceLocation(
        parentSourceLocation.getFilePath(),
        Math.max(1, parentSourceLocation.getBeginLine()),
        Math.max(1, parentSourceLocation.getBeginColumn() + token.beginColumn),
        Math.max(1, parentSourceLocation.getBeginLine()),
        Math.max(1, parentSourceLocation.getBeginColumn() + token.endColumn));
  }

  private Tokens() {}
}
