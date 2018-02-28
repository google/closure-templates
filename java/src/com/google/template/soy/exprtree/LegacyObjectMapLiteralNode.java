/*
 * Copyright 2011 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import java.util.List;

/**
 * A node representing a legacy object map literal (with keys and values as alternating children).
 *
 * <p>Note: This map literal does not interoperate with proto maps, ES6 Maps, or {@link
 * com.google.template.soy.types.MapType}. We are introducing a second map type to handle proto maps
 * and ES6 Maps, and a second map literal syntax to create MapType values. We intend to migrate
 * everyone to the new map literal syntax and eventually delete LegacyObjectMapLiteralNode. See
 * b/69046114.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class LegacyObjectMapLiteralNode extends AbstractParentExprNode {

  /** @param alternatingKeysAndValues The keys and values (alternating) in this map. */
  public LegacyObjectMapLiteralNode(
      List<ExprNode> alternatingKeysAndValues, SourceLocation sourceLocation) {
    super(sourceLocation);
    addChildren(alternatingKeysAndValues);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private LegacyObjectMapLiteralNode(LegacyObjectMapLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.LEGACY_OBJECT_MAP_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {

    if (numChildren() == 0) {
      return "[:]";
    }

    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append('[');

    for (int i = 0, n = numChildren(); i < n; i += 2) {
      if (i != 0) {
        sourceSb.append(", ");
      }
      sourceSb
          .append(getChild(i).toSourceString())
          .append(": ")
          .append(getChild(i + 1).toSourceString());
    }

    sourceSb.append(']');
    return sourceSb.toString();
  }

  @Override
  public LegacyObjectMapLiteralNode copy(CopyState copyState) {
    return new LegacyObjectMapLiteralNode(this, copyState);
  }
}
