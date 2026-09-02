/*
 * Copyright 2026 Google Inc.
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

package com.google.template.soy.exprtree;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.RegexpType;

/** Node representing a regular expression literal. */
public final class RegexpLiteralNode extends AbstractPrimitiveNode {

  private final String pattern;
  private final String flags;

  /**
   * @param pattern The regular expression pattern string.
   * @param flags The regex flags (e.g., "g", "i", or empty string).
   * @param sourceLocation The node's source location.
   */
  public RegexpLiteralNode(String pattern, String flags, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.pattern = checkNotNull(pattern);
    this.flags = checkNotNull(flags);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private RegexpLiteralNode(RegexpLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.pattern = orig.pattern;
    this.flags = orig.flags;
  }

  @Override
  public Kind getKind() {
    return Kind.REGEXP_LITERAL_NODE;
  }

  @Override
  public RegexpType getType() {
    return RegexpType.getInstance();
  }

  /** Returns the regular expression pattern. */
  public String getPattern() {
    return pattern;
  }

  /** Returns the flags string (e.g. "g", "i"). */
  public String getFlags() {
    return flags;
  }

  @Override
  public String toSourceString() {
    return "/" + pattern + "/" + flags;
  }

  @Override
  public RegexpLiteralNode copy(CopyState copyState) {
    return new RegexpLiteralNode(this, copyState);
  }
}
