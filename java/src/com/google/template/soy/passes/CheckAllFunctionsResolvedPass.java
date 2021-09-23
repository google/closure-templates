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
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Reports errors for unresolved functions. */
@RunAfter(ResolvePluginsPass.class) // Run after all passes that resolve function nodes.
final class CheckAllFunctionsResolvedPass implements CompilerFilePass {

  private final PluginResolver resolver;

  CheckAllFunctionsResolvedPass(PluginResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, FunctionNode.class)
        .filter(fct -> !fct.isResolved())
        .forEach(resolver::reportUnresolved);
  }
}
