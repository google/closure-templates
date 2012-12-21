/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SyntaxVersion;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;

import java.util.Map;


/**
 * Visitor for setting the full callee name on each CallBasicNode whose callee name in the source
 * code either (a) is a partial template name or (b) starts with an alias.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree or a Soy file. This pass mutates
 * {@code CallBasicNode}s. There is no return value.
 *
 * @author Kai Huang
 */
public class SetFullCalleeNamesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The namespace of the current file that we're in (during the pass). */
  private String currNamespace;

  /** Alias-to-namespace map of the current file (during the pass). */
  private Map<String, String> currAliasToNamespaceMap;


  @Override public Void exec(SoyNode soyNode) {
    Preconditions.checkArgument(
        soyNode instanceof SoyFileSetNode || soyNode instanceof SoyFileNode);
    return super.exec(soyNode);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileNode(SoyFileNode node) {
    currNamespace = node.getNamespace();
    currAliasToNamespaceMap = node.getAliasToNamespaceMap();
    visitChildren(node);
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    if (currNamespace == null) {
      String srcCalleeName = node.getSrcCalleeName();
      // TODO: If feasible, change existing instances and remove the startsWith(".") part below.
      if (node.getSyntaxVersion() != SyntaxVersion.V1 && srcCalleeName.startsWith(".")) {
        throw SoySyntaxExceptionUtils.createWithNode(
            "Missing namespace in Soy file containing 'call' with namespace-relative callee name" +
                " (" + node.getTagString() + ").",
            node);
      }
      node.setCalleeName(node.getSrcCalleeName());

    } else {
      String srcCalleeName = node.getSrcCalleeName();
      if (srcCalleeName.startsWith(".")) {
        // Case 1: Source callee name is partial.
        node.setCalleeName(currNamespace + srcCalleeName);
      } else if (srcCalleeName.contains(".")) {
        // Case 2: Source callee name is a proper dotted ident.
        String[] parts = srcCalleeName.split("[.]", 2);
        if (currAliasToNamespaceMap.containsKey(parts[0])) {
          // Case 2a: Source callee name's first part is an alias.
          String aliasNamespace = currAliasToNamespaceMap.get(parts[0]);
          node.setCalleeName(aliasNamespace + '.' + parts[1]);
        } else {
          // Case 2b: Source callee name's first part is not an alias.
          node.setCalleeName(srcCalleeName);
        }
      } else {
        // Case 3: Source callee name is a single ident (not dotted).
        if (currAliasToNamespaceMap.containsKey(srcCalleeName)) {
          throw SoySyntaxExceptionUtils.createWithNode(
              "In 'call' tag, found callee that is a single identifier (not dotted) and matches" +
                  " a namespace alias ('" + srcCalleeName + "'), which is not allowed.",
              node);
        }
        node.setCalleeName(srcCalleeName);
      }
    }

    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
