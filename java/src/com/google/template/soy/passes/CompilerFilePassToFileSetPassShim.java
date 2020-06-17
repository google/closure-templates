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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateRegistry;

/** Adapter class to allow a {@link CompileFilePass} to run as a {@link CompilerFileSetPass}. */
final class CompilerFilePassToFileSetPassShim implements CompilerFileSetPass {

  static CompilerFilePassToFileSetPassShim filePassAsFileSetPass(CompilerFilePass filePass) {
    return new CompilerFilePassToFileSetPassShim(filePass);
  }

  private final CompilerFilePass filePassDelegate;

  CompilerFilePassToFileSetPassShim(CompilerFilePass filePassDelegate) {
    this.filePassDelegate = filePassDelegate;
  }

  @Override
  public ImmutableList<Class<? extends CompilerPass>> runBefore() {
    return filePassDelegate.runBefore();
  }

  @Override
  public ImmutableList<Class<? extends CompilerPass>> runAfter() {
    return filePassDelegate.runAfter();
  }

  @Override
  public String name() {
    return filePassDelegate.name();
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode file : sourceFiles) {
      filePassDelegate.run(file, idGenerator);
    }
    return Result.CONTINUE;
  }

  public Class<? extends CompilerFilePass> getDelegateClass() {
    return this.filePassDelegate.getClass();
  }

  @VisibleForTesting
  public CompilerFilePass getDelegate() {
    return this.filePassDelegate;
  }
}
