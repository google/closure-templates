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

package com.google.template.soy.passes;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateHeaderVarDefn;
import com.google.template.soy.types.SoyType;

/**
 * Runs validation checks skipped earlier in parsing now that template types have been resolved.
 * Currently, this just checks that the types of default values match the declared types.
 */
@RunAfter(UpgradeTemplateTypesPass.class)
final class TemplateTypeValidationPass implements CompilerFileSetPass {
  private static final SoyErrorKind DECLARED_DEFAULT_TYPE_MISMATCH =
      SoyErrorKind.of(
          "The initializer for ''{0}'' has type ''{1}'' which is not assignable to type ''{2}''.");

  private final ErrorReporter errorReporter;

  TemplateTypeValidationPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode templateNode : SoyTreeUtils.getAllNodesOfType(file, TemplateNode.class)) {
        for (TemplateHeaderVarDefn headerVar : templateNode.getHeaderParams()) {
          if (headerVar.type().getKind() == SoyType.Kind.TEMPLATE
              && headerVar.defaultValue() != null) {
            SoyType declaredType = headerVar.type();
            SoyType actualType = headerVar.defaultValue().getType();
            if (!declaredType.isAssignableFrom(actualType)) {
              errorReporter.report(
                  headerVar.defaultValue().getSourceLocation(),
                  DECLARED_DEFAULT_TYPE_MISMATCH,
                  headerVar.name(),
                  actualType,
                  declaredType);
            }
          }
        }
      }
    }
    return Result.CONTINUE;
  }
}
