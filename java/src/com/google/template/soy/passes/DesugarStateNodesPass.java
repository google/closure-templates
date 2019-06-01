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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateStateVar;

/** Downgrades @state in Soy elements to lets. This is run in all non-incrementaldom backends */
final class DesugarStateNodesPass extends CompilerFileSetPass {

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }
    return Result.CONTINUE;
  }

  private void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getChildren()) {
      if (!(template instanceof TemplateElementNode)) {
        continue;
      }
      TemplateElementNode soyElement = (TemplateElementNode) template;
      Multimap<VarDefn, VarRefNode> map = ArrayListMultimap.create();
      for (VarRefNode ref : SoyTreeUtils.getAllNodesOfType(template, VarRefNode.class)) {
        if (ref.getDefnDecl().kind() == VarDefn.Kind.STATE) {
          map.put(ref.getDefnDecl(), ref);
        }
      }
      int stateVarIndex = 0;
      for (TemplateStateVar stateVar : soyElement.getStateVars()) {
        LetValueNode node =
            new LetValueNode(
                nodeIdGen.genId(),
                stateVar.nameLocation(),
                "$" + stateVar.name(),
                stateVar.nameLocation(),
                stateVar.defaultValue().getRoot());
        node.getVar().setType(stateVar.type());
        // insert them in the same order as they were declared
        soyElement.addChild(stateVarIndex, node);
        stateVarIndex++;
        for (VarRefNode ref : map.get(stateVar)) {
          ref.setDefn(node.getVar());
        }
      }
      // After this, no code should be referencing state outside of Incremental DOM.
      soyElement.clearStateVars();
    }
  }
}
