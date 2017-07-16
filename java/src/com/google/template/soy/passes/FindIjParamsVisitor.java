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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.passes.FindTransitiveDepTemplatesVisitor.TransitiveDepTemplatesInfo;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Visitor for finding the injected params used by a given template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} should be called on a {@code TemplateNode}.
 *
 * <p>If you need to call this visitor for multiple templates in the same tree (without modifying
 * the tree), it's more efficient to reuse the same instance of this visitor because we memoize
 * results from previous calls to exec.
 *
 */
public final class FindIjParamsVisitor {

  /** Return value for {@code FindIjParamsVisitor}. */
  public static final class IjParamsInfo {

    /** Sorted set of inject params (i.e. the keys of the multimap below). */
    public final ImmutableSortedSet<String> ijParamSet;

    /** Multimap from injected param key to transitive callees that use the param. */
    @VisibleForTesting final ImmutableMultimap<String, TemplateNode> ijParamToCalleesMultimap;

    /**
     * @param ijParamToCalleesMultimap Multimap from injected param key to transitive callees that
     *     use the param.
     */
    private IjParamsInfo(ImmutableMultimap<String, TemplateNode> ijParamToCalleesMultimap) {
      this.ijParamToCalleesMultimap = ijParamToCalleesMultimap;
      this.ijParamSet = ImmutableSortedSet.copyOf(ijParamToCalleesMultimap.keySet());
    }
  }

  // -----------------------------------------------------------------------------------------------
  // FindIjParamsVisitor body.

  /** The FindTransitiveDepTemplatesVisitor to use. */
  private final FindTransitiveDepTemplatesVisitor findTransitiveDepTemplatesVisitor;

  /**
   * Map from TransitiveDepTemplatesInfo to IjParamsInfo, containing memoized results that were
   * computed in previous calls to exec.
   */
  private final Map<TransitiveDepTemplatesInfo, IjParamsInfo> depsInfoToIjParamsInfoMap;

  /**
   * Map from template to set of ij params used locally in that template, containing memoized
   * results that were found in previous calls to exec.
   */
  private final Map<TemplateNode, Set<String>> templateToLocalIjParamsMap;

  /** @param templateRegistry Map from template name to TemplateNode to use during the pass. */
  public FindIjParamsVisitor(TemplateRegistry templateRegistry) {
    this.findTransitiveDepTemplatesVisitor =
        new FindTransitiveDepTemplatesVisitor(templateRegistry);
    depsInfoToIjParamsInfoMap = new HashMap<>();
    templateToLocalIjParamsMap = new HashMap<>();
  }

  /**
   * Computes injected params info for a template.
   *
   * <p>Note: This method is not thread-safe. If you need to get injected params info in a
   * thread-safe manner, then please use {@link #execOnAllTemplates}() in a thread-safe manner.
   */
  public IjParamsInfo exec(TemplateNode rootTemplate) {

    TransitiveDepTemplatesInfo depsInfo = findTransitiveDepTemplatesVisitor.exec(rootTemplate);

    if (!depsInfoToIjParamsInfoMap.containsKey(depsInfo)) {

      ImmutableMultimap.Builder<String, TemplateNode> ijParamToCalleesMultimapBuilder =
          ImmutableMultimap.builder();

      for (TemplateNode template : depsInfo.depTemplateSet) {

        if (!templateToLocalIjParamsMap.containsKey(template)) {
          templateToLocalIjParamsMap.put(template, getAllIjs(template));
        }

        for (String localIjParam : templateToLocalIjParamsMap.get(template)) {
          ijParamToCalleesMultimapBuilder.put(localIjParam, template);
        }

        for (TemplateParam injectedParam : template.getInjectedParams()) {
          ijParamToCalleesMultimapBuilder.put(injectedParam.name(), template);
        }
      }

      IjParamsInfo ijParamsInfo = new IjParamsInfo(ijParamToCalleesMultimapBuilder.build());
      depsInfoToIjParamsInfoMap.put(depsInfo, ijParamsInfo);
    }

    return depsInfoToIjParamsInfoMap.get(depsInfo);
  }

  /**
   * Precomputes injected params info for all templates.
   *
   * <p>Note: This method is not thread-safe. If you need to get injected params info in a
   * thread-safe manner, be sure to call this method only once and then use the precomputed map.
   *
   * @param soyTree The full Soy tree.
   * @return A map from template node to injected params info for all templates. The returned map is
   *     deeply immutable ({@code IjParamsInfo} is immutable).
   */
  public ImmutableMap<TemplateNode, IjParamsInfo> execOnAllTemplates(SoyFileSetNode soyTree) {

    ImmutableMap.Builder<TemplateNode, IjParamsInfo> resultMapBuilder = ImmutableMap.builder();

    for (SoyFileNode soyFile : soyTree.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        resultMapBuilder.put(template, exec(template));
      }
    }

    return resultMapBuilder.build();
  }

  /** Returns all ij parameters found in the subtree. */
  private static Set<String> getAllIjs(Node soyTree) {
    final Set<String> ijs = new HashSet<>();
    SoyTreeUtils.visitAllNodes(
        soyTree,
        new NodeVisitor<Node, Boolean>() {
          @Override
          public Boolean exec(Node node) {
            if (isIj(node)) {
              ijs.add(((VarRefNode) node).getName());
            }
            return true;
          }
        });
    return ijs;
  }

  private static boolean isIj(Node node) {
    if (node instanceof VarRefNode) {
      VarRefNode varRef = (VarRefNode) node;
      if (varRef.isInjected()) {
        return true;
      }
    }
    return false;
  }
}
