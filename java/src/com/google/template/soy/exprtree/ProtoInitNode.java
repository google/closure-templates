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

package com.google.template.soy.exprtree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;

/**
 * A node representing a proto initialization function (with args as children).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class ProtoInitNode extends AbstractParentExprNode {

  /** The function name. */
  private final String protoName;
  /** List of proto initialization param names. */
  private final ImmutableList<Identifier> paramNames;

  /**
   * @param protoName The fully qualified name of the proto.
   * @param paramNames An iterable of proto initialization arg param names.
   * @param sourceLocation The node's source location.
   */
  public ProtoInitNode(
      String protoName, Iterable<Identifier> paramNames, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.protoName = protoName;
    this.paramNames = ImmutableList.copyOf(paramNames);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ProtoInitNode(ProtoInitNode orig, CopyState copyState) {
    super(orig, copyState);
    this.protoName = orig.protoName;
    this.paramNames = orig.paramNames;
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO_INIT_NODE;
  }

  /** Returns the name of the fully qualified proto. */
  public String getProtoName() {
    return protoName;
  }

  /**
   * Returns the list of proto initialization call param names.
   *
   * <p>Each param name corresponds to each of this node's children, which are the param values.
   */
  public ImmutableList<Identifier> getParamNames() {
    return paramNames;
  }

  public Identifier getParamName(int i) {
    return paramNames.get(i);
  }

  @Override
  public String toSourceString() {
    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append(protoName).append('(');

    for (int i = 0; i < numChildren(); i++) {
      if (i > 0) {
        sourceSb.append(", ");
      }
      sourceSb.append(paramNames.get(i)).append(": ");
      sourceSb.append(getChild(i).toSourceString());
    }

    sourceSb.append(')');
    return sourceSb.toString();
  }

  @Override
  public ProtoInitNode copy(CopyState copyState) {
    return new ProtoInitNode(this, copyState);
  }
}
