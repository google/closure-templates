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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Set;

/**
 * A graph where vertices are {@link TemplateNode}s and edges are present when there is a {@link
 * CallBasicNode call} from the source vertex to the {@link CallBasicNode#getCalleeName() target}
 * vertex.
 *
 */
final class TemplateCallGraph {

  /** The edges. */
  private final ListMultimap<TemplateNode, TemplateNode> callers =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();

  /**
   * @param templatesByName A map whose values are the vertices for the call graph, and whose keys
   *     are matched against {@link CallBasicNode#getCalleeName()} to come up with the edges.
   */
  TemplateCallGraph(final ImmutableListMultimap<String, TemplateNode> templatesByName) {
    // Visit each template's body to find call nodes and build the edge multimap.
    for (TemplateNode tn : templatesByName.values()) {
      /** Finds calls in templates to build a call graph. */
      for (CallBasicNode call : SoyTreeUtils.getAllNodesOfType(tn, CallBasicNode.class)) {
        ImmutableList<TemplateNode> callees = templatesByName.get(call.getCalleeName());
        if (callees != null) { // Might be a call to an external template.
          for (TemplateNode callee : callees) {
            callers.put(callee, tn);
          }
        }
      }
    }
  }

  /** Returns the set of templates from which control can reach any of the input templates. */
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
