/*
 * Copyright 2009 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateMetadata.Parameter;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Visitor for finding the indirect params of a given template.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>{@link #exec} should be called on a {@code TemplateNode}.
 *
 */
public final class IndirectParamsCalculator {

  /** Return value for {@code IndirectParamsCalculator}. */
  public static class IndirectParamsInfo {
    // TODO(lukes): combine indirectParams and indirectParamTypes, they are largely redundant

    /** Map from indirect param key to param object. */
    public final ImmutableSortedMap<String, TemplateMetadata.Parameter> indirectParams;

    /**
     * Multimap from param key (direct or indirect) to transitive callees that declare the param.
     */
    public final ImmutableSetMultimap<String, TemplateMetadata> paramKeyToCalleesMultimap;

    /** Multimap from indirect param key to param types. */
    public final ImmutableSetMultimap<String, SoyType> indirectParamTypes;

    /**
     * Whether the template (that the pass was run on) may have indirect params in external basic
     * calls.
     */
    public final boolean mayHaveIndirectParamsInExternalCalls;

    /**
     * Whether the template (that the pass was run on) may have indirect params in external delegate
     * calls.
     */
    public final boolean mayHaveIndirectParamsInExternalDelCalls;

    /**
     * @param indirectParams Indirect params of the template (that the pass was run on).
     * @param paramKeyToCalleesMultimap Multimap from param key to callees that explicitly list the
     *     param.
     * @param mayHaveIndirectParamsInExternalCalls Whether the template (that the pass was run on)
     *     may have indirect params in external basic calls.
     * @param mayHaveIndirectParamsInExternalDelCalls Whether the template (that the pass was run
     *     on) may have indirect params in external delegate calls.
     */
    public IndirectParamsInfo(
        ImmutableSortedMap<String, TemplateMetadata.Parameter> indirectParams,
        ImmutableSetMultimap<String, TemplateMetadata> paramKeyToCalleesMultimap,
        ImmutableSetMultimap<String, SoyType> indirectParamTypes,
        boolean mayHaveIndirectParamsInExternalCalls,
        boolean mayHaveIndirectParamsInExternalDelCalls) {
      this.indirectParams = indirectParams;
      this.paramKeyToCalleesMultimap = paramKeyToCalleesMultimap;
      this.indirectParamTypes = indirectParamTypes;
      this.mayHaveIndirectParamsInExternalCalls = mayHaveIndirectParamsInExternalCalls;
      this.mayHaveIndirectParamsInExternalDelCalls = mayHaveIndirectParamsInExternalDelCalls;
    }
  }

  /**
   * Private value class to hold all the facets that make up a unique call situation. The meaning is
   * that if the same call situation is encountered multiple times in the pass, we only have to
   * visit the callee once for that situation. But if a new call situation is encountered, we must
   * visit the callee in that situation, even if we've previously visited the same callee under a
   * different situation.
   *
   * <p>The call situation facets include the callee (obviously) and allCallParamKeys, which is the
   * set of all param keys that were explicitly passed in the current call chain. The reason we need
   * allCallParamKeys is because, in this visitor, we're only interested in searching for indirect
   * params that may have been passed via data="all". As soon as a data key is passed explicitly in
   * the call chain, it becomes a key that was computed somewhere in the call chain, and not a key
   * that was passed via data="all". However, if this same callee is called during a different call
   * chain where the data key was not passed explicitly along the way, then that key once again
   * becomes a candidate for being an indirect param, which makes it a different situation for the
   * purpose of this visitor.
   *
   * <p>This class can be used for hash keys.
   */
  private static final class TransitiveCallSituation {

    /** The current callee. */
    private final TemplateMetadata callee;

    /**
     * Set of all param keys passed explicitly (using the 'param' command) in any call in the
     * current call stack, including the call to the current callee.
     */
    private final Set<String> allCallParamKeys;

    /**
     * @param callee The current callee.
     * @param allCallParamKeys Set of all param keys passed explicitly (using the 'param' command)
     *     in any call in the current call stack, including the call to the current callee.
     */
    public TransitiveCallSituation(TemplateMetadata callee, Set<String> allCallParamKeys) {
      this.callee = callee;
      this.allCallParamKeys = allCallParamKeys;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof TransitiveCallSituation)) {
        return false;
      }
      TransitiveCallSituation otherCallSit = (TransitiveCallSituation) other;
      return Objects.equals(otherCallSit.callee, this.callee)
          && otherCallSit.allCallParamKeys.equals(this.allCallParamKeys);
    }

    @Override
    public int hashCode() {
      return callee.hashCode() * 31 + allCallParamKeys.hashCode();
    }
  }

  /** Registry of all templates in the Soy tree. */
  private final TemplateRegistry templateRegistry;

  /** The set of calls we've visited already (during pass). */
  private Set<TransitiveCallSituation> visitedCallSituations;

  /** Map from indirect param key to param object. */
  private Map<String, Parameter> indirectParams;

  /** Multimap from param key (direct or indirect) to callees that explicitly list the param. */
  private SetMultimap<String, TemplateMetadata> paramKeyToCalleesMultimap;

  /** Multimap from indirect param key to param types. */
  private SetMultimap<String, SoyType> indirectParamTypes;

  /**
   * Whether the template (that the pass was run on) may have indirect params in external basic
   * calls.
   */
  private boolean mayHaveIndirectParamsInExternalCalls;

  /**
   * Whether the template (that the pass was run on) may have indirect params in external delegate
   * calls.
   */
  private boolean mayHaveIndirectParamsInExternalDelCalls;

  /** @param templateRegistry Map from template name to TemplateNode to use during the pass. */
  public IndirectParamsCalculator(TemplateRegistry templateRegistry) {
    this.templateRegistry = checkNotNull(templateRegistry);
  }

  public IndirectParamsInfo calculateIndirectParams(TemplateNode node) {
    return calculateIndirectParams(
        TemplateMetadata.asTemplateType(templateRegistry.getMetadata(node)));
  }

  public IndirectParamsInfo calculateIndirectParams(TemplateType template) {

    visitedCallSituations = Sets.newHashSet();
    indirectParams = Maps.newHashMap();
    paramKeyToCalleesMultimap = HashMultimap.create();
    indirectParamTypes = LinkedHashMultimap.create();
    mayHaveIndirectParamsInExternalCalls = false;
    mayHaveIndirectParamsInExternalDelCalls = false;
    visit(template, new HashSet<>(), new HashSet<>());

    return new IndirectParamsInfo(
        ImmutableSortedMap.copyOf(indirectParams),
        ImmutableSetMultimap.copyOf(paramKeyToCalleesMultimap),
        ImmutableSetMultimap.copyOf(indirectParamTypes),
        mayHaveIndirectParamsInExternalCalls,
        mayHaveIndirectParamsInExternalDelCalls);
  }

  private void visit(
      TemplateType template, Set<String> allCallParamKeys, Set<TemplateType> allCallers) {
    if (!allCallers.add(template)) {
      return;
    }
    for (TemplateType.DataAllCallSituation call : template.getDataAllCallSituations()) {
      // only construct a new set if we are adding more parameters.
      // ideally we would use some kind of persistent datastructure, but this is probably fine since
      // the sets are small.
      Set<String> newAllCallParamKeys = allCallParamKeys;
      if (!allCallParamKeys.containsAll(call.getExplicitlyPassedParameters())) {
        newAllCallParamKeys = new HashSet<>();
        newAllCallParamKeys.addAll(allCallParamKeys);
        newAllCallParamKeys.addAll(call.getExplicitlyPassedParameters());
      }
      if (call.isDelCall()) {
        // There is no guarantee that we can see all delcall targets, so always assume that
        // there may be some unknown ones.
        mayHaveIndirectParamsInExternalDelCalls = true;
        // TODO(lukes): this should probably take variants into account if they are present
        for (TemplateMetadata delCallee :
            templateRegistry
                .getDelTemplateSelector()
                .delTemplateNameToValues()
                .get(call.getTemplateName())) {
          processCall(template, delCallee, newAllCallParamKeys, allCallers);
        }
      } else {
        TemplateMetadata basicCallee =
            templateRegistry.getBasicTemplateOrElement(call.getTemplateName());
        if (basicCallee == null) {
          mayHaveIndirectParamsInExternalCalls = true;
        } else {
          processCall(template, basicCallee, newAllCallParamKeys, allCallers);
        }
      }
    }
    allCallers.remove(template);
  }

  private void processCall(
      TemplateType caller,
      TemplateMetadata callee,
      Set<String> allCallParamKeys,
      Set<TemplateType> allCallers) {
    TemplateType calleeSignature = TemplateMetadata.asTemplateType(callee);
    if (caller.equals(calleeSignature) || allCallers.contains(calleeSignature)) {
      // We never recursive calls to bring in an indirect param.
      return;
    }
    for (Parameter p : callee.getParameters()) {
      if (!allCallParamKeys.contains(p.getName())) {
        // For some reason we only record the first one.
        indirectParams.putIfAbsent(p.getName(), p);
        indirectParamTypes.put(p.getName(), p.getType());
        paramKeyToCalleesMultimap.put(p.getName(), callee);
      }
    }
    TransitiveCallSituation transitiveCallSituation =
        new TransitiveCallSituation(callee, allCallParamKeys);
    // we have already seen this exact call.
    if (!visitedCallSituations.add(transitiveCallSituation)) {
      return;
    }
    visit(calleeSignature, allCallParamKeys, allCallers);
  }
}
