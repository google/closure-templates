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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.soytree.SoyFileNode;

/**
 * A pass that runs over a single {@link SoyFileNode}.
 *
 * <p>Generally this should be used in preference to {@link CompilerFileSetPass} since ASTs operated
 * on by these nodes can be cached individually leading to better compile times.
 *
 * <p>TODO(lukes): it would be nice to somehow use the type system to flag a pass as 'readonly' or
 * not. Making the ASTs immutable probably isn't worth it, but we could consider adding a
 * 'freeze/unfreeze' API.
 */
public abstract class CompilerFilePass {
  public abstract void run(SoyFileNode file, IdGenerator nodeIdGen);

  public String name() {
    String simpleName = getClass().getSimpleName();
    if (simpleName.endsWith("Pass")) {
      return simpleName.substring(0, simpleName.length() - "Pass".length());
    }
    return simpleName;
  }

  @Override
  public String toString() {
    return name();
  }
}
