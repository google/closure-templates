/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.soytree.defn;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.ast.TypeNode;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A parameter declared in the template header.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Immutable
public final class HeaderParam extends TemplateParam {
  private final TypeNode typeNode;

  public HeaderParam(
      String name,
      SourceLocation nameLocation,
      SoyType type,
      TypeNode typeNode,
      boolean isRequired,
      boolean isInjected,
      @Nullable String desc) {
    super(name, type, isRequired, isInjected, desc, nameLocation);
    Preconditions.checkArgument(type != null);
    this.typeNode = typeNode;
  }

  @Override
  public DeclLoc declLoc() {
    return DeclLoc.HEADER;
  }

  /**
   * Returns the TypeNode.
   *
   * <p>May be null if type parsing failed.
   */
  @Nullable
  public TypeNode getTypeNode() {
    return typeNode;
  }

  @Override
  public HeaderParam copyEssential() {
    // Note: 'desc', nameLocation is nonessential.
    HeaderParam headerParam =
        new HeaderParam(name(), null, type, null, isRequired(), isInjected(), null);
    headerParam.setLocalVariableIndex(localVariableIndex());
    return headerParam;
  }
}
