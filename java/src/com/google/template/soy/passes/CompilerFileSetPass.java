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
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * A pass that runs over all {@link SoyFileKind#SRC source} files.
 *
 * <p>Prefer implementing {@link CompilerFilePass} whenever possible. This should only be used for
 * passes that need to access transitive callee information.
 */
abstract class CompilerFileSetPass extends CompilerPass {
  enum Result {
    CONTINUE,
    STOP;
  }

  /** Runs the pass and returns whether or not compilation should abort. */
  abstract Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry);
}
