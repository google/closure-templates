/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/** A node representing the {@code ve(VeName)} expression, to create a VE for VE logging. */
public final class VeLiteralNode extends AbstractExprNode {

  private final Identifier name;
  @Nullable private Long id;
  private SoyType type;

  public VeLiteralNode(Identifier name, SourceLocation srcLoc) {
    super(srcLoc);
    this.name = name;
  }

  private VeLiteralNode(VeLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
  }

  public Identifier getName() {
    return name;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Nullable
  public Long getId() {
    return id;
  }

  public void setType(SoyType type) {
    this.type = type;
  }

  @Override
  public SoyType getType() {
    return type;
  }

  @Override
  public Kind getKind() {
    return Kind.VE_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {
    return "ve(" + name.identifier() + ")";
  }

  @Override
  public VeLiteralNode copy(CopyState copyState) {
    return new VeLiteralNode(this, copyState);
  }
}
