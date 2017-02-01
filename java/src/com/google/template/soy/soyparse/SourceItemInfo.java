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

package com.google.template.soy.soyparse;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.base.SourceLocation;

/**
 * Parsed content and information about the source from which it is derived.
 *
 * <p>TODO(lukes): consider whether or not this class carries its own weight. Maybe the grammar
 * should be enhanced (or the AST refactored) so that we can more easily generate the AST from the
 * javacc primitives, at which point passing these glorified pairs around in the parser may be less
 * necessary (e.g. every time something returns a SourceItemInfo, maybe it should be returning a
 * SoyNode and every time we return a SourceItemInfo<Void>, why not a Token).
 */
final class SourceItemInfo<T> {
  private final T parsedContent;
  private final SourceLocation location;

  SourceItemInfo(T parsedContent, SourceItemInfo<?> begin, SourceItemInfo<?> end) {
    this(
        begin.location.getFilePath(),
        parsedContent,
        begin.location.getBeginLine(),
        begin.location.getBeginColumn(),
        end.location.getEndLine(),
        end.location.getEndColumn());
    checkArgument(begin.location.getFileName().equals(end.location.getFileName()));
  }

  SourceItemInfo(
      String filePath,
      T parsedContent,
      int lineNum,
      int columnNum,
      int lineNumEnd,
      int columnNumEnd) {
    this.location = new SourceLocation(filePath, lineNum, columnNum, lineNumEnd, columnNumEnd);
    this.parsedContent = parsedContent;
  }

  SourceItemInfo(T parsedContent, SourceLocation location) {
    this.location = location;
    this.parsedContent = parsedContent;
  }

  /** Content derived from tokens. */
  T parsedContent() {
    return parsedContent;
  }

  SourceLocation srcLocation() {
    return location;
  }
}
