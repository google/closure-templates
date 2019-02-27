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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;

/**
 * A node representing a record literal (with keys and values as alternating children).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class RecordLiteralNode extends AbstractParentExprNode {

  private final Identifier recordIdentifier;
  private final ImmutableList<Identifier> keys;

  /**
   * Constructs a new record literal node with the given keys. The values should be set as children
   * of this node, in the same order as the keys.
   */
  public RecordLiteralNode(
      Identifier recordIdentifier, Iterable<Identifier> keys, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.recordIdentifier = recordIdentifier;
    this.keys = ImmutableList.copyOf(keys);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private RecordLiteralNode(RecordLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.recordIdentifier = orig.recordIdentifier;
    this.keys = orig.keys;
  }

  public Identifier getRecordIdentifier() {
    return recordIdentifier;
  }

  public ImmutableList<Identifier> getKeys() {
    return keys;
  }

  public Identifier getKey(int i) {
    return keys.get(i);
  }

  @Override
  public Kind getKind() {
    return Kind.RECORD_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {
    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append("record(");

    for (int i = 0, n = numChildren(); i < n; i++) {
      if (i != 0) {
        sourceSb.append(", ");
      }
      sourceSb.append(getKey(i)).append(": ").append(getChild(i).toSourceString());
    }

    sourceSb.append(')');
    return sourceSb.toString();
  }

  @Override
  public RecordLiteralNode copy(CopyState copyState) {
    return new RecordLiteralNode(this, copyState);
  }
}
