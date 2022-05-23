/*
 * Copyright 2015 Google Inc.
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
import com.google.template.soy.soytree.SoyFileNode;

/**
 * A pass that runs over all {@link SoyFileNode source} files.
 *
 * <p>Prefer implementing {@link CompilerFilePass} whenever possible. This should only be used for
 * passes that need to access transitive callee information.
 *
 * <p>TODO(b/157519545): Refactor this interface. Right now half of these passes need the file set
 * registry, and the other half use the per-file registries (once they're ready, after the second
 * template imports pass). We should restructure this once we've decided how we want to refactor the
 * template registry object first.
 *
 * <p>For now, each CompilerFileSetPass should implement one of the run methods in this file;
 * run(sourceFiles, idGenerator) should be preferred (and sourceFile.getTemplateRegistry() should be
 * used to get a file-local template registry that includes imports), unless the file-set template
 * registyr is needed for the pass.
 */
public interface CompilerFileSetPass extends CompilerPass {

  /**
   * Marker interface for passes that would like to receive {@code sourceFiles} topologically
   * sorted. Such passes must run after {@link FileDependencyOrderPass}.
   */
  interface TopologicallyOrdered extends CompilerFileSetPass {}

  /**
   * Result after running this pass, indicating whether it's "safe" to continue compiling.
   *
   * <p>Result.CONTINUE doesn't mean there weren't any errors, just that we think the errors (if
   * any) were "recoverable" enough that we can keep compiling (so that we can gather and report as
   * many errors as possible to the user at once, rather than reporting at the first error and
   * quitting).
   *
   * <p>Result.STOP should be used if things are so broken that the compiler might actually crash if
   * we continue.
   */
  enum Result {
    CONTINUE,
    STOP;
  }

  Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator);
}
