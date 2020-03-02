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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.NamedTemplateType;
import com.google.template.soy.types.SoyType;

/** Node representing a template literal. */
public final class TemplateLiteralNode extends AbstractExprNode {

  private final Identifier templateIdentifier;
  private final String resolvedName;

  private SoyType type;

  public TemplateLiteralNode(Identifier templateIdentifier, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.templateIdentifier = templateIdentifier;
    this.resolvedName = templateIdentifier.identifier();
    this.type = new NamedTemplateType(resolvedName);
  }

  private TemplateLiteralNode(TemplateLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.templateIdentifier = orig.templateIdentifier;
    this.resolvedName = orig.resolvedName;
  }

  public String getResolvedName() {
    return resolvedName;
  }

  @Override
  public SoyType getType() {
    return type;
  }

  public void setType(SoyType type) {
    this.type = type;
  }

  @Override
  public Kind getKind() {
    return Kind.TEMPLATE_LITERAL_NODE;
  }

  @Override
  public String toSourceString() {
    return templateIdentifier.identifier();
  }

  @Override
  public TemplateLiteralNode copy(CopyState copyState) {
    return new TemplateLiteralNode(this, copyState);
  }
}
