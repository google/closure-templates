/*
 * Copyright 2025 Google Inc.
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


import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SourceMapMode;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Builds a sourcemap. */
public final class SourceMapHelper {

  public static final SourceMapHelper NO_OP = new SourceMapHelper(null, SourceMapMode.DISABLED);

  private static final String BASE_64_COMMENT_PREFIX =
      "\n//# sourceMappingURL=data:application/json;base64,";

  private final SourceMapMode sourceMapMode;

  private final List<String> debugLines = new ArrayList<>();
  private String lastPath = "";
  private Map<CodeChunk, SourceLocation> locationMap = new HashMap<>();

  public SourceMapHelper(SoyFileNode file, SourceMapMode sourceMapMode) {
    this.sourceMapMode = sourceMapMode;
  }

  @Nullable
  public SourceLocation getPrimaryLocation(CodeChunk value) {
    return locationMap.get(value);
  }

  @CanIgnoreReturnValue
  public <T extends CodeChunk> T setPrimaryLocation(T value, SourceLocation loc) {
    locationMap.put(value, loc);
    return value;
  }

  public void mark(
      SourceLocation location,
      int fromLine,
      int fromColumn,
      int currentLine,
      int currentColumn,
      @Nullable String token) {
  }

  public void appendSourceMapComment(StringBuilder output) {
  }
}
