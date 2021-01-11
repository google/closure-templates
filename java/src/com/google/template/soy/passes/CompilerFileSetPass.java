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
import com.google.template.soy.soytree.TemplateNameRegistry;
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * A pass that runs over all {@link SoyFileKind#SRC source} files.
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
  public enum Result {
    CONTINUE,
    STOP;
  }

  default Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    throw new UnsupportedOperationException(
        "run(  ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) is not"
            + " implemented.");
  }

  /**
   * Runs the pass and returns whether or not compilation should abort.
   *
   * @param fileSetRegistry This can either be a complete registry (for crossTemplateCheckingPasses)
   *     for a registry containing metadata about dependencies (for
   *     templateReturnTypeInferencePasses). The latter is for modifying template node information
   *     (such as whether the template is a Soy element) based off of its callees.
   */
  default Result run(
      ImmutableList<SoyFileNode> sourceFiles,
      IdGenerator idGenerator,
      TemplateRegistry fileSetRegistry) {
    return run(sourceFiles, idGenerator);
  }

  default Result run(
      ImmutableList<SoyFileNode> sourceFiles,
      IdGenerator idGenerator,
      // A complete, lightweight registry of template names in each file.
      TemplateNameRegistry templateNameRegistry,
      // Either a partial (just deps) or complete registry of template metadata, depending on which
      // round of passes you're in.
      TemplateRegistry fileSetRegistry) {
    return run(sourceFiles, idGenerator, fileSetRegistry);
  }
}
