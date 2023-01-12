/*
 * Copyright 2023 Google Inc.
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

import static java.util.stream.Collectors.joining;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import java.util.Optional;

/**
 * A node representing the {@code ve_def()} expression, to create a VE for VE logging. The children
 * should be the second and third function arguments, if they exist: the proto data type expression
 * and the static metadata.
 */
public final class VeDefNode extends AbstractParentExprNode {
  private final String name;
  private final long id;

  public VeDefNode(String name, long id, SourceLocation srcLoc) {
    super(srcLoc);
    this.name = name;
    this.id = id;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private VeDefNode(VeDefNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.id = orig.id;
  }

  public Optional<ExprNode> getDataProtoTypeExpr() {
    return numChildren() >= 1 ? Optional.of(getChild(0)) : Optional.empty();
  }

  public Optional<String> getDataProtoTypeName() {
    return getDataProtoTypeExpr().map(expr -> expr.getType().toString());
  }

  public Optional<ExprNode> getStaticMetadataExpr() {
    return numChildren() == 2 ? Optional.of(getChild(1)) : Optional.empty();
  }

  public String getName() {
    return name;
  }

  public Long getId() {
    return id;
  }

  @Override
  public VeDefNode copy(CopyState copyState) {
    return new VeDefNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append(String.format("ve_def(%s", id));
    sourceSb.append(getChildren().stream().map(ExprNode::toSourceString).collect(joining(", ")));
    sourceSb.append(")");
    return sourceSb.toString();
  }

  @Override
  public Kind getKind() {
    return Kind.VE_DEF_NODE;
  }
}
