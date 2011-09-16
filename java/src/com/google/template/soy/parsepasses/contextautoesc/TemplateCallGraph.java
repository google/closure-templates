/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * A graph where vertices are {@link TemplateNode}s and edges are present when there is a
 * {@link CallBasicNode call} from the source vertex to the
 * {@link CallBasicNode#getCalleeName() target} vertex.
 *
 * @author Mike Samuel
 */
final class TemplateCallGraph {

  /** The edges. */
  private final Multimap<TemplateNode, TemplateNode> callers = Multimaps.newSetMultimap(
      Maps.<TemplateNode, Collection<TemplateNode>>newLinkedHashMap(),
      new Supplier<Set<TemplateNode>>() {
        @Override
        public Set<TemplateNode> get() {
          return Sets.newLinkedHashSet();
        }
      });

  /**
   * @param templatesByName A map whose values are the vertices for the call graph, and whose
   *      keys are matched against {@link CallBasicNode#getCalleeName()} to come up with the edges.
   */
  TemplateCallGraph(final Map<String, ImmutableList<TemplateNode>> templatesByName) {
    // Visit each template's body to find call nodes and build the edge multimap.
    for (ImmutableList<TemplateNode> templateNodes : templatesByName.values()) {
      for (final TemplateNode tn : templateNodes) {
        /**
         * Finds calls in templates to build a call graph.
         */
        class CallGraphBuilder extends AbstractSoyNodeVisitor<Void> {
          @Override
          public void visitCallBasicNode(CallBasicNode call) {
            ImmutableList<TemplateNode> callees = templatesByName.get(call.getCalleeName());
            if (callees != null) {  // Might be a call to an external template.
              for (TemplateNode callee : callees) {
                callers.put(callee, tn);
              }
            }
          }

          @Override protected void visitSoyNode(SoyNode node) {
            if (node instanceof ParentSoyNode<?>) {
              visitChildren((ParentSoyNode<?>) node);
            }
          }
        }

        new CallGraphBuilder().exec(tn);
      }
    }
  }

  /**
   * Returns the set of templates from which control can reach any of the input templates.
   */
  Set<TemplateNode> callersOf(Iterable<TemplateNode> templates) {
    Set<TemplateNode> callerSet = Sets.newLinkedHashSet();
    for (TemplateNode templateNode : templates) {
      addTransitively(templateNode, callerSet);
    }
    return callerSet;
  }

  private void addTransitively(TemplateNode callee, Set<? super TemplateNode> out) {
    for (TemplateNode caller : callers.get(callee)) {
      if (out.add(caller)) {
        addTransitively(caller, out);
      }
    }
  }
}
