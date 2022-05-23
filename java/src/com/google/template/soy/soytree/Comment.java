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

package com.google.template.soy.soytree;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.SourceLocation;

/** A comment from a Soy source file. */
@AutoValue
public abstract class Comment {

  /** The comment type. */
  public enum Type {
    /** A line comment. */
    LINE,
    /** A range comment, including doc comments. */
    RANGE,
    /** One or more blank lines in the code, which are treated as an empty separator comment. */
    BLANK_LINES,
  }

  public static Comment create(Type type, String source, SourceLocation location) {
    return new AutoValue_Comment(type, source, location);
  }

  public abstract Type getType();

  /**
   * The full source of the comment, including leading and trailing {@code /*}, {@code *}{@code /},
   * {@code //}.
   */
  public abstract String getSource();

  public abstract SourceLocation getSourceLocation();

  public Comment withType(Type type) {
    return create(type, getSource(), getSourceLocation());
  }

  public SourceLocation.Point getBeginPoint() {
    return getSourceLocation().getBeginPoint();
  }

  public SourceLocation.Point getEndPoint() {
    return getSourceLocation().getEndPoint();
  }
}
