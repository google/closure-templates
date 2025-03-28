/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.CommandTagAttribute.CommandTagAttributesHolder;
import com.google.template.soy.soytree.defn.ExternVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.ast.FunctionTypeNode;
import java.util.Optional;

/**
 * Node representing a 'extern' statement with js/java implementations. TODO(b/191090743) Handle the
 * {export keyword.
 */
public final class ExternNode extends AbstractParentCommandNode<ExternImplNode>
    implements CommandTagAttributesHolder {

  private final FunctionTypeNode typeNode;
  private final Identifier name;
  private final SourceLocation openTagLocation;
  private final boolean exported;
  private final ExternVar var;
  private ImmutableList<TemplateParam> paramVars;

  public ExternNode(
      int id,
      SourceLocation location,
      Identifier name,
      FunctionTypeNode typeNode,
      SourceLocation headerLocation,
      boolean exported) {
    super(id, location, "extern");
    this.name = name;
    this.openTagLocation = headerLocation;
    this.typeNode = typeNode;
    this.exported = exported;
    this.var = new ExternVar(name.identifier(), name.location(), null);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ExternNode(ExternNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.typeNode = orig.typeNode.copy();
    this.openTagLocation = orig.openTagLocation;
    this.exported = orig.exported;
    this.var = new ExternVar(orig.var);
    copyState.updateRefs(orig.var, this.var);
    if (orig.paramVars != null) {
      this.paramVars =
          orig.paramVars.stream().map(v -> v.copy(copyState)).collect(toImmutableList());
      for (int i = 0; i < paramVars.size(); i++) {
        copyState.updateRefs(orig.paramVars.get(i), this.paramVars.get(i));
      }
    }
  }

  public Identifier getIdentifier() {
    return name;
  }

  public boolean isExported() {
    return exported;
  }

  public Optional<JsImplNode> getJsImpl() {
    return getChildOfType(JsImplNode.class);
  }

  public Optional<JavaImplNode> getJavaImpl() {
    return getChildOfType(JavaImplNode.class);
  }

  public boolean isJavaImplAsync() {
    return getJavaImpl().map(j -> !j.isAutoImpl() && j.isAsync()).orElse(false);
  }

  public boolean isAutoJava() {
    return getJavaImpl().map(j -> j.isAutoImpl()).orElse(false);
  }

  public FunctionTypeNode typeNode() {
    return typeNode;
  }

  @Override
  public ImmutableList<CommandTagAttribute> getAttributes() {
    return ImmutableList.of();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  @Override
  public Kind getKind() {
    return Kind.EXTERN_NODE;
  }

  @Override
  public SourceLocation getOpenTagLocation() {
    return openTagLocation;
  }

  public FunctionType getType() {
    return (FunctionType) typeNode.getResolvedType();
  }

  public ExternVar getVar() {
    return var;
  }

  public ImmutableList<TemplateParam> getParamVars() {
    if (paramVars == null) {
      paramVars =
          typeNode().parameters().stream()
              .map(
                  p ->
                      new TemplateParam(
                          p.name(),
                          p.nameLocation(),
                          p.nameLocation(),
                          p.type(),
                          false,
                          false,
                          false,
                          "",
                          null))
              .collect(toImmutableList());
    }
    return paramVars;
  }

  @Override
  public ExternNode copy(CopyState copyState) {
    return new ExternNode(this, copyState);
  }

  public boolean requiresRenderContext() {
    return getJavaImpl()
        .map(
            j -> {
              if (getType().getParameters().stream()
                  .anyMatch(p -> p.getType() instanceof FunctionType)) {
                return true;
              }
              return j.requiresRenderContext();
            })
        .orElse(false);
  }
}
