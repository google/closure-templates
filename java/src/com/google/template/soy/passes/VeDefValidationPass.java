/*
 * Copyright 2023 Google Inc.
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
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.VeDefNode;
import com.google.template.soy.logging.ValidatedLoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig.ValidatedLoggableElement;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.ArrayList;
import java.util.List;

/** Validates VEs created with ve_def(). */
@RunAfter({ResolveExpressionTypesPass.class})
final class VeDefValidationPass implements CompilerFileSetPass {

  private final ValidatedLoggingConfig validatedLoggingConfig;
  private final ErrorReporter errorReporter;
  private final ExprEquivalence exprEquivalence;
  private final List<ValidatedLoggableElement> vedefs;
  private final CopyState copyState;

  VeDefValidationPass(ValidatedLoggingConfig validatedLoggingConfig, ErrorReporter errorReporter) {
    this.validatedLoggingConfig = validatedLoggingConfig;
    this.errorReporter = errorReporter;
    this.exprEquivalence = new ExprEquivalence();
    this.copyState = new CopyState();
    this.vedefs = new ArrayList<>();
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      SoyTreeUtils.allNodesOfType(file, VeDefNode.class).forEach(this::buildVeDefAndValidate);
    }
    ValidatedLoggingConfig.validate(validatedLoggingConfig, vedefs, errorReporter);
    return Result.CONTINUE;
  }

  private void buildVeDefAndValidate(VeDefNode veDef) {
    if (veDef.getName().isEmpty()) {
      // ve_def() that had errors, ignore.
      return;
    }
    vedefs.add(
        ValidatedLoggingConfig.ValidatedLoggableElement.create(
            veDef.getName(),
            veDef.getId(),
            veDef.getDataProtoTypeName(),
            veDef.getStaticMetadataExpr().map(expr -> exprEquivalence.wrap(expr.copy(copyState))),
            veDef.getSourceLocation()));
  }
}
