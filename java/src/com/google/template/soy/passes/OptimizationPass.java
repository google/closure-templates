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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.soytree.SoyFileNode;

/** Runs the optimizer on the whole file set. */
final class OptimizationPass implements CompilerFileSetPass {

  private final ErrorReporter errorReporter;

  public OptimizationPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    SimplifyVisitor visitor = SimplifyVisitor.create(idGenerator, sourceFiles, errorReporter);
    for (SoyFileNode file : sourceFiles) {
      visitor.simplify(file);
    }
    return Result.CONTINUE;
  }
}
