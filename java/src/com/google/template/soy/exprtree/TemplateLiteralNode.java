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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.NamedTemplateType;
import com.google.template.soy.types.SoyType;
import java.util.Optional;
import javax.annotation.Nullable;

/** Node representing a template literal. */
public final class TemplateLiteralNode extends AbstractExprNode {

  // True for 'synthetic' template literal nodes that are the direct children of {call} statements.
  // These are exempt from some checks around the explicit template() expressions.
  private final boolean isSynthetic;

  private Identifier templateIdentifier;
  private Optional<String> resolvedName;

  private SoyType type;

  public TemplateLiteralNode(
      Identifier templateIdentifier, SourceLocation sourceLocation, boolean isSynthetic) {
    super(sourceLocation);
    this.templateIdentifier = templateIdentifier;
    this.resolvedName = Optional.empty();
    this.type = NamedTemplateType.create(templateIdentifier.identifier());
    this.isSynthetic = isSynthetic;
  }

  private TemplateLiteralNode(TemplateLiteralNode orig, CopyState copyState) {
    super(orig, copyState);
    this.templateIdentifier = orig.templateIdentifier;
    this.resolvedName = orig.resolvedName;
    this.type = orig.type;
    this.isSynthetic = orig.isSynthetic;
  }

  public boolean isSynthetic() {
    return isSynthetic;
  }

  public void resolveTemplateName(Identifier resolvedIdent) {
    checkState(!resolvedName.isPresent(), "Template identifier has already been resolved.");
    this.templateIdentifier = resolvedIdent;
    this.resolvedName = Optional.of(resolvedIdent.identifier());

    // Only set the type if it hasn't been upgraded already.
    if (type instanceof NamedTemplateType) {
      type = NamedTemplateType.create(resolvedIdent.identifier());
    }
  }

  public boolean isResolved() {
    return resolvedName.isPresent();
  }

  /** Returns the resolved template name, or null if not resolved yet. */
  @Nullable
  public String getResolvedName() {
    return resolvedName.orElse(null);
  }

  public Identifier getIdentifier() {
    return templateIdentifier;
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
    return isSynthetic
        ? templateIdentifier.originalName()
        : "template(" + templateIdentifier.originalName() + ")";
  }

  @Override
  public TemplateLiteralNode copy(CopyState copyState) {
    return new TemplateLiteralNode(this, copyState);
  }
}
