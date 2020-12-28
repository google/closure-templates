/*
 * Copyright 2016 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.data.internalutils.InternalValueUtils;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** A {@link CompilerFilePass} that searches for compile time globals and substitutes values. */
@RunAfter({
  VeRewritePass.class, // rewrites some VE references that are parsed as globals in a different way
})
@RunBefore({CheckGlobalsPass.class})
final class RewriteGlobalsPass implements CompilerFilePass {

  private final ImmutableMap<String, PrimitiveData> compileTimeGlobals;

  RewriteGlobalsPass(ImmutableMap<String, PrimitiveData> compileTimeGlobals) {
    this.compileTimeGlobals = compileTimeGlobals;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, GlobalNode.class)
        .forEach(global -> resolveGlobal(file, global));
  }

  private void resolveGlobal(SoyFileNode file, GlobalNode global) {
    // First check to see if this global matches a proto enum.  We do this because the enums from
    // the type registry have better type information and for applications with legacy globals
    // configs there is often overlap, so the order in which we check is actually important.
    // proto enums are dotted identifiers
    Identifier alias = file.resolveAlias(global.getIdentifier());
    if (alias != null) {
      global.setName(alias.identifier());
    }
    String name = global.getName();
    // if that doesn't work, see if it was registered in the globals file.
    PrimitiveData value = compileTimeGlobals.get(name);

    if (value != null) {
      PrimitiveNode expr =
          InternalValueUtils.convertPrimitiveDataToExpr(value, global.getSourceLocation());
      global.resolve(expr.getType(), expr);
    }
  }
}
