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
package com.google.template.soy.passes;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.TypeNodeConverter;

/** Resolve the TypeNode objects in TemplateParams to SoyTypes */
final class ResolveTemplateParamTypesPass extends CompilerFilePass {
  private final TypeNodeConverter converter;
  private final boolean disableAllTypeChecking;

  ResolveTemplateParamTypesPass(
      SoyTypeRegistry typeRegistry, ErrorReporter errorReporter, boolean disableAllTypeChecking) {
    this.converter = new TypeNodeConverter(errorReporter, typeRegistry);
    this.disableAllTypeChecking = disableAllTypeChecking;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getChildren()) {
      for (TemplateParam param : template.getAllParams()) {
        if (param.getTypeNode() != null) {
          param.setType(converter.getOrCreateType(param.getTypeNode()));
        } else if (disableAllTypeChecking) {
          // If there's no type node, this is a default parameter. Normally, we'd set the type on
          // this once we figure out the type of the default expression in
          // ResolveExpressionTypesPass. But if type checking is disabled that pass won't run, so
          // instead we set the type to unknown here, because later parts of the compiler require a
          // (non-null) type.
          param.setType(UnknownType.getInstance());
        }
      }
      if (template instanceof TemplateElementNode) {
        for (TemplateStateVar state : ((TemplateElementNode) template).getStateVars()) {
          if (state.getTypeNode() != null) {
            state.setType(converter.getOrCreateType(state.getTypeNode()));
          }
        }
      }
    }
  }
}
