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

import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateRegistry;

/**
 * A pass that runs over the entire {@link SoyFileSetNode}.
 *
 * <p>Prefer implementing {@link CompilerFilePass} whenever possible. This should only be used for
 * passes that need to access transitive callee information.
 */
abstract class CompilerFileSetPass {
  public abstract void run(SoyFileSetNode fileSet, TemplateRegistry registry);

  public String name() {
    String simpleName = getClass().getSimpleName();
    if (simpleName.endsWith("Pass")) {
      return simpleName.substring(0, simpleName.length() - "Pass".length());
    }
    return simpleName;
  }
}
