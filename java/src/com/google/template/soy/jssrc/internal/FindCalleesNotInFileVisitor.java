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

package com.google.template.soy.jssrc.internal;

import com.google.common.collect.Sets;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Set;
import java.util.SortedSet;


/**
 * Visitor for finding the templates called in a file that are not defined in the file.
 *
 * <p> Important: Only deals with basic callees (not delegates). Delegates are not applicable here
 * because we cannot tell at compile time which delegate will be called (if any). Thus, during
 * compilation, we usually handle delegate calls the same way as external calls.
 *
 * <p> Precondition: All template and callee names should be full names (i.e. you must execute
 * {@code PrependNamespacesVisitor} before executing this visitor).
 *
 * <p> {@link #exec} should be called on a {@code SoyFileNode}. The returned set will be the full
 * names of all templates called by the templates in this file that that not in this file. In other
 * words, if T is the set of templates in this file and U is the set of templates not in this file,
 * then the returned set consists of the full names of all templates in U called by any template
 * in T.
 *
 * @author Kai Huang
 */
class FindCalleesNotInFileVisitor extends AbstractSoyNodeVisitor<Set<String>> {


  /** The names of templates defined in this file. */
  private Set<String> templatesInFile;

  /** The names of called templates not defined in this file (the result). */
  private SortedSet<String> calleesNotInFile;


  @Override public Set<String> exec(SoyNode node) {
    visit(node);
    return calleesNotInFile;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileNode(SoyFileNode node) {

    templatesInFile = Sets.newHashSet();
    for (TemplateNode template : node.getChildren()) {
      templatesInFile.add(template.getTemplateName());
    }

    calleesNotInFile = Sets.newTreeSet();

    visitChildren(node);
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    String calleeName = node.getCalleeName();
    if (!templatesInFile.contains(calleeName)) {
      calleesNotInFile.add(calleeName);
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
