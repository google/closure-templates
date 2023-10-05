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

import static com.google.template.soy.soytree.defn.TemplateParam.isAlreadyOptionalType;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.AttrParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.TypeNodeConverter;

/** Resolve the TypeNode objects in TemplateParams to SoyTypes */
final class ResolveTemplateParamTypesPass implements CompilerFilePass {
  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;

  private static final SoyErrorKind ATTRIBUTE_PARAM_ONLY_IN_ELEMENT_TEMPLATE =
      SoyErrorKind.of("Only templates of kind=\"html<?>\" can have @attribute.");

  private static final SoyErrorKind OPTIONAL_AND_NULLABLE_DISAGREE =
      SoyErrorKind.of("Optional params should be declared with both a ''?'' and ''|null''.");

  public ResolveTemplateParamTypesPass(
      ErrorReporter errorReporter, boolean disableAllTypeChecking) {
    this.errorReporter = errorReporter;
    this.disableAllTypeChecking = disableAllTypeChecking;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    TypeNodeConverter converter =
        TypeNodeConverter.builder(errorReporter)
            .setTypeRegistry(file.getSoyTypeRegistry())
            .setDisableAllTypeChecking(disableAllTypeChecking)
            .build();

    for (ExternNode extern : file.getExterns()) {
      extern.setType(converter.getOrCreateFunctionTypeForExtern(extern.typeNode()));
    }

    for (TemplateNode template : file.getTemplates()) {
      for (TemplateParam param : template.getAllParams()) {
        if (param instanceof AttrParam
            && !(template.getTemplateContentKind()
                instanceof TemplateContentKind.ElementContentKind)) {
          errorReporter.report(param.getSourceLocation(), ATTRIBUTE_PARAM_ONLY_IN_ELEMENT_TEMPLATE);
        }
        if (param.getTypeNode() != null) {
          SoyType paramType = converter.getOrCreateType(param.getTypeNode());
          param.setType(paramType);
          // TODO(b/291132644): Remove this restriction.
          if (param.getOriginalTypeNode() != null
              && !param.hasDefault()
              && !SoyTypes.containsKinds(paramType, ImmutableSet.of(Kind.ANY, Kind.UNKNOWN))
              && param.isExplicitlyOptional()
                  != isAlreadyOptionalType(param.getOriginalTypeNode())) {
            errorReporter.warn(param.getSourceLocation(), OPTIONAL_AND_NULLABLE_DISAGREE);
          }
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
