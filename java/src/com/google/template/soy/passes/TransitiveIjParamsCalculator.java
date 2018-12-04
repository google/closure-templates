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

package com.google.template.soy.passes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateMetadata.Parameter;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.CallGraphNode;
import java.util.HashMap;
import java.util.Map;

/**
 * Visitor for finding the injected params used by a given template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>If you need to call this visitor for multiple templates in the same tree (without modifying
 * the tree), it's more efficient to reuse the same instance of this visitor because we memoize
 * results from previous calls to exec.
 *
 */
public final class TransitiveIjParamsCalculator {

  /** Return value for {@code FindIjParamsVisitor}. */
  public static final class IjParamsInfo {

    /** Sorted set of inject params (i.e. the keys of the multimap below). */
    public final ImmutableSortedSet<String> ijParamSet;

    /** Multimap from injected param key to transitive callees that use the param. */
    @VisibleForTesting final ImmutableMultimap<String, TemplateMetadata> ijParamToCalleesMultimap;

    /**
     * @param ijParamToCalleesMultimap Multimap from injected param key to transitive callees that
     *     use the param.
     */
    private IjParamsInfo(ImmutableMultimap<String, TemplateMetadata> ijParamToCalleesMultimap) {
      this.ijParamToCalleesMultimap = ijParamToCalleesMultimap;
      this.ijParamSet = ImmutableSortedSet.copyOf(ijParamToCalleesMultimap.keySet());
    }
  }

  // -----------------------------------------------------------------------------------------------
  // FindIjParamsVisitor body.

  /**
   * Map from TransitiveDepTemplatesInfo to IjParamsInfo, containing memoized results that were
   * computed in previous calls to exec.
   */
  private final Map<CallGraphNode, IjParamsInfo> depsInfoToIjParamsInfoMap = new HashMap<>();

  private final TemplateRegistry templateRegistry;

  /** @param templateRegistry Map from template name to TemplateNode to use during the pass. */
  public TransitiveIjParamsCalculator(TemplateRegistry templateRegistry) {
    this.templateRegistry = templateRegistry;
  }

  /**
   * Computes injected params info for a template.
   *
   * <p>Note: This method is not thread-safe.
   */
  public IjParamsInfo calculateIjs(TemplateMetadata rootTemplate) {
    CallGraphNode callGraph = templateRegistry.getCallGraph(rootTemplate);

    IjParamsInfo ijParamsInfo = depsInfoToIjParamsInfoMap.get(callGraph);
    if (ijParamsInfo == null) {

      ImmutableMultimap.Builder<String, TemplateMetadata> ijParamToCalleesMultimapBuilder =
          ImmutableMultimap.builder();

      for (TemplateMetadata template : callGraph.transitiveCallees()) {
        for (Parameter param : template.getParameters()) {
          if (param.isInjected()) {
            ijParamToCalleesMultimapBuilder.put(param.getName(), template);
          }
        }
      }

      ijParamsInfo = new IjParamsInfo(ijParamToCalleesMultimapBuilder.build());
      depsInfoToIjParamsInfoMap.put(callGraph, ijParamsInfo);
    }

    return ijParamsInfo;
  }
}
