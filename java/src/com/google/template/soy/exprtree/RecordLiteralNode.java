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
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A node representing a record literal (with keys and values as alternating children).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class RecordLiteralNode extends AbstractParentExprNode {

  private static final SoyErrorKind ILLEGAL_RECORD_KEY_TYPE =
      SoyErrorKind.of("Record key ''{0}'' must be a string literal. Did you mean to use a map?");
  private static final SoyErrorKind ILLEGAL_RECORD_KEY_NAME =
      SoyErrorKind.of("Record key ''{0}'' must be a valid Soy identifier.");
  private static final SoyErrorKind DUPLICATE_KEY_IN_RECORD_LITERAL =
      SoyErrorKind.of(
          "Record literals with duplicate keys are not allowed.  Duplicate key: ''{0}''");
  private static final SoyErrorKind SINGLE_IDENTIFIER_KEY_IN_RECORD_LITERAL =
      SoyErrorKind.of(
          "Disallowed single-identifier key \"{0}\" in record literal (please surround with "
              + "quotes).");

  /** Construct a record literal node from the old syntax: ['a': 'foo', 'b': 32] */
  // TODO(b/80429224): Delete this once we remove support for the old record literal syntax.
  public static RecordLiteralNode create(
      List<ExprNode> alternatingKeysAndValues,
      SourceLocation sourceLocation,
      ErrorReporter errorReporter) {
    Set<Identifier> keys = new LinkedHashSet<>();
    Set<String> keyNames = new HashSet<>();
    List<ExprNode> values = new ArrayList<>();
    for (int i = 0; i < alternatingKeysAndValues.size(); i += 2) {
      ExprNode key = alternatingKeysAndValues.get(i);
      if (key instanceof GlobalNode) {
        errorReporter.report(
            key.getSourceLocation(),
            SINGLE_IDENTIFIER_KEY_IN_RECORD_LITERAL,
            ((GlobalNode) key).getName());
      } else if (!(key instanceof StringNode)) {
        errorReporter.report(
            key.getSourceLocation(), ILLEGAL_RECORD_KEY_TYPE, key.toSourceString());
      } else {
        Identifier keyId =
            Identifier.create(((StringNode) key).getValue(), key.getSourceLocation());
        if (!BaseUtils.isIdentifier(keyId.identifier())) {
          errorReporter.report(key.getSourceLocation(), ILLEGAL_RECORD_KEY_NAME, keyId);
        } else if (!keyNames.add(keyId.identifier())) {
          errorReporter.report(key.getSourceLocation(), DUPLICATE_KEY_IN_RECORD_LITERAL, keyId);
        } else {
          keys.add(keyId);
          values.add(alternatingKeysAndValues.get(i + 1));
        }
      }
    }
    RecordLiteralNode node = new RecordLiteralNode(keys, sourceLocation);
    node.addChildren(values);
    return node;
  }

  private final ImmutableList<Identifier> keys;

  /**
   * Constructs a new record literal node with the given keys. The values should be set as children
   * of this node, in the same order as the keys.
   */
  public RecordLiteralNode(Iterable<Identifier> keys, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.keys = ImmutableList.copyOf(keys);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private RecordLiteralNode(RecordLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.keys = orig.keys;
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
