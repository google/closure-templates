/*
 * Copyright 2024 Google Inc.
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

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.types.ast.TypeNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 *
 *
 * <pre>{@params {p1, p2, p3}: Type = record(...)}</pre>
 */
public final class TemplateParamsNode extends AbstractCommandNode {

  private final ImmutableList<Identifier> names;
  private final TypeNode typeNode;
  @Nullable private final ExprRootNode defaultValue;

  public TemplateParamsNode(
      int id,
      SourceLocation location,
      List<Identifier> names,
      TypeNode typeNode,
      @Nullable ExprRootNode defaultValue) {
    super(id, location, "@params");
    this.names = ImmutableList.copyOf(names);
    this.typeNode = typeNode;
    this.defaultValue = defaultValue;
  }

  private TemplateParamsNode(TemplateParamsNode orig, CopyState copyState) {
    super(orig, copyState);
    this.names = orig.names;
    this.typeNode = orig.typeNode.copy();
    this.defaultValue = copyState.copyNullable(orig.defaultValue);
  }

  public ImmutableList<Identifier> getNames() {
    return names;
  }

  public TypeNode getTypeNode() {
    return typeNode;
  }

  @Nullable
  public ExprRootNode getDefaultValue() {
    return defaultValue;
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_PARAMS_NODE;
  }

  @Override
  public SoyNode copy(CopyState copyState) {
    return new TemplateParamsNode(this, copyState);
  }

  @Override
  public String toSourceString() {
    return "{@params ["
        + names.stream().map(Identifier::identifier).collect(joining(", "))
        + "] : "
        + typeNode
        + (defaultValue != null ? " = " + defaultValue.toSourceString() : "")
        + "}";
  }
}
