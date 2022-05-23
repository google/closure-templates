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
package com.google.template.soy.exprtree;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.TriState;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.UnknownType;
import javax.annotation.Nullable;

/** Node representing a template literal. */
public final class TemplateLiteralNode extends AbstractParentExprNode {

  private TriState isStaticCall = TriState.UNSET;
  private String templateFqn;
  private SoyType type;

  public static TemplateLiteralNode forVarRef(VarRefNode varRef) {
    return forVarRef(varRef, varRef.getSourceLocation());
  }

  public static TemplateLiteralNode forVarRef(VarRefNode varRef, SourceLocation sourceLocation) {
    TemplateLiteralNode node = new TemplateLiteralNode(sourceLocation);
    node.addChild(varRef);
    if (varRef.hasType() && varRef.getType().getKind() == SoyType.Kind.TEMPLATE_TYPE) {
      node.resolveTemplateName();
    }
    return node;
  }

  private TemplateLiteralNode(SourceLocation sourceLocation) {
    super(sourceLocation);
  }

  private TemplateLiteralNode(TemplateLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.isStaticCall = orig.isStaticCall;
    this.templateFqn = orig.templateFqn;
    this.type = orig.type;
  }

  /** Returns whether this node is the root expression of a CallNode. */
  public boolean isStaticCall() {
    Preconditions.checkState(isStaticCall.isSet(), "May not call before setStaticCall()");
    return isStaticCall == TriState.ENABLED;
  }

  public void setStaticCall(boolean isStaticCall) {
    this.isStaticCall = TriState.from(isStaticCall);
  }

  public void resolveTemplateName() {
    checkState(!isResolved(), "Template identifier has already been resolved.");

    SoyType type = getChild(0).getType();
    if (type.getKind() == SoyType.Kind.TEMPLATE_TYPE) {
      templateFqn = ((TemplateImportType) type).getName();
      this.type = type;
    } else {
      templateFqn = "";
      this.type = UnknownType.getInstance();
    }
  }

  public boolean isResolved() {
    return templateFqn != null;
  }

  /** Returns the resolved template name, or null if not resolved yet. */
  @Nullable
  public String getResolvedName() {
    return templateFqn;
  }

  @Override
  public SoyType getType() {
    return type;
  }

  @Override
  public void setType(SoyType type) {
    this.type = Preconditions.checkNotNull(type);
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {
    return getChild(0).toSourceString();
  }

  @Override
  public TemplateLiteralNode copy(CopyState copyState) {
    return new TemplateLiteralNode(this, copyState);
  }
}
