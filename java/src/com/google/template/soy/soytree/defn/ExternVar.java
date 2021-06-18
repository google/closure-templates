/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.soytree.defn;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/** A file-level extern declaration. */
public final class ExternVar extends AbstractVarDefn {

  public ExternVar(String name, @Nullable SourceLocation nameLocation, @Nullable SoyType type) {
    super(name, nameLocation, type);
  }

  public ExternVar(ExternVar localVar) {
    super(localVar);
  }

  @Override
  public Kind kind() {
    return Kind.EXTERN;
  }

  @Override
  public boolean isInjected() {
    return false;
  }

  public void setType(SoyType type) {
    this.type = type;
  }
}
