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
 * A node representing a map literal (with keys and values as alternating children).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class MapLiteralNode extends AbstractParentExprNode {

  /** @param alternatingKeysAndValues The keys and values (alternating) in this map. */
  public MapLiteralNode(List<ExprNode> alternatingKeysAndValues, SourceLocation sourceLocation) {
    super(sourceLocation);
    addChildren(alternatingKeysAndValues);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private MapLiteralNode(MapLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.MAP_LITERAL_NODE;
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
  public MapLiteralNode copy(CopyState copyState) {
    return new MapLiteralNode(this, copyState);
  }
}
