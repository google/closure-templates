/*
 * Copyright 2026 Google Inc.
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
import com.google.template.soy.shared.internal.SoyCompilerContext;
import com.google.template.soy.sharedpasses.opti.EvaluateCompileTimeFunctionsVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Evaluates {@link com.google.template.soy.shared.internal.SoyCompileTimeFunction} calls across the
 * file set when an {@link SoyCompilerContext.OutputTarget} is configured, before {@link
 * OptimizationPass} runs, so backends do not need runtime implementations and the optimizer can
 * fold constants and eliminate dead branches.
 */
@RunAfter(CallAnnotationPass.class)
@RunBefore(OptimizationPass.class)
final class EvaluateCompileTimeFunctionsPass implements CompilerFileSetPass {

  private final SoyCompilerContext compilerContext;

  EvaluateCompileTimeFunctionsPass(SoyCompilerContext compilerContext) {
    this.compilerContext = compilerContext;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    EvaluateCompileTimeFunctionsVisitor visitor =
        new EvaluateCompileTimeFunctionsVisitor(compilerContext);
    for (SoyFileNode file : sourceFiles) {
      SoyTreeUtils.execOnAllV2Exprs(file, visitor);
    }
    return Result.CONTINUE;
  }
}
