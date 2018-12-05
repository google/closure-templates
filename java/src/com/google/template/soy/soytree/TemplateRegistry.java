/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.soytree.TemplateMetadata.CallSituation;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A registry or index of all templates in a Soy tree.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateRegistry {

  private static final SoyErrorKind DUPLICATE_TEMPLATES =
      SoyErrorKind.of("Template/element ''{0}'' already defined at {1}.");
  private static final SoyErrorKind TEMPLATE_OR_ELEMENT_AND_DELTEMPLATE_WITH_SAME_NAME =
      SoyErrorKind.of("Found deltemplate {0} with the same name as a template/element at {1}.");
  private static final SoyErrorKind DUPLICATE_DEFAULT_DELEGATE_TEMPLATES =
      SoyErrorKind.of("Delegate template ''{0}'' already has a default defined at {1}.");
  private static final SoyErrorKind DUPLICATE_DELEGATE_TEMPLATES_IN_DELPACKAGE =
      SoyErrorKind.of(
          "Delegate template ''{0}'' already defined in delpackage {1}: {2}",
          StyleAllowance.NO_PUNCTUATION);

  /** Map from basic template or element name to node. */
  private final ImmutableMap<String, TemplateMetadata> basicTemplatesOrElementsMap;

  private final DelTemplateSelector<TemplateMetadata> delTemplateSelector;
  private final ImmutableMap<String, TemplateMetadata> allTemplates;

  /** Lazily allocated. */
  private final Map<TemplateMetadata, CallGraphNode> callGraphs = new HashMap<>();

  /**
   * Constructor.
   *
   * @param soyTree The Soy tree from which to build a template registry.
   */
  public TemplateRegistry(List<TemplateMetadata> templates, ErrorReporter errorReporter) {

    // ------ Iterate through all templates to collect data. ------
    Map<String, TemplateMetadata> allTemplatesBuilder = new LinkedHashMap<>();
    DelTemplateSelector.Builder<TemplateMetadata> delTemplateSelectorBuilder =
        new DelTemplateSelector.Builder<>();
    Map<String, TemplateMetadata> basicTemplatesOrElementsMap = new LinkedHashMap<>();
    Multimap<String, TemplateMetadata> delegateTemplates = HashMultimap.create();
    for (TemplateMetadata template : templates) {
      allTemplatesBuilder.put(template.getTemplateName(), template);
      switch (template.getTemplateKind()) {
        case BASIC:
        case ELEMENT:
          // Case 1: Basic Template or Element node
          TemplateMetadata prev =
              basicTemplatesOrElementsMap.put(template.getTemplateName(), template);
          if (prev != null) {
            errorReporter.report(
                template.getSourceLocation(),
                DUPLICATE_TEMPLATES,
                template.getTemplateName(),
                prev.getSourceLocation());
          }
          break;
        case DELTEMPLATE:
          // Case 2: Delegate template.
          String delTemplateName = template.getDelTemplateName();
          String delPackageName = template.getDelPackageName();
          String variant = template.getDelTemplateVariant();
          TemplateMetadata previous;
          if (delPackageName == null) {
            // default delegate
            previous = delTemplateSelectorBuilder.addDefault(delTemplateName, variant, template);
            if (previous != null) {
              errorReporter.report(
                  template.getSourceLocation(),
                  DUPLICATE_DEFAULT_DELEGATE_TEMPLATES,
                  delTemplateName,
                  previous.getSourceLocation());
            }
          } else {
            previous =
                delTemplateSelectorBuilder.add(delTemplateName, delPackageName, variant, template);
            if (previous != null) {
              errorReporter.report(
                  template.getSourceLocation(),
                  DUPLICATE_DELEGATE_TEMPLATES_IN_DELPACKAGE,
                  delTemplateName,
                  delPackageName,
                  previous.getSourceLocation());
            }
          }
          delegateTemplates.put(delTemplateName, template);
          break;
      }
    }
    // make sure no basic nodes conflict with deltemplates
    for (Map.Entry<String, TemplateMetadata> entry : delegateTemplates.entries()) {
      TemplateMetadata node = basicTemplatesOrElementsMap.get(entry.getKey());
      if (node != null) {
        errorReporter.report(
            entry.getValue().getSourceLocation(),
            TEMPLATE_OR_ELEMENT_AND_DELTEMPLATE_WITH_SAME_NAME,
            entry.getKey(),
            node.getSourceLocation());
      }
    }

    // ------ Build the final data structures. ------

    this.basicTemplatesOrElementsMap = ImmutableMap.copyOf(basicTemplatesOrElementsMap);
    this.delTemplateSelector = delTemplateSelectorBuilder.build();
    this.allTemplates = ImmutableMap.copyOf(allTemplatesBuilder);
  }

  /** Returns all basic template names. */
  public ImmutableSet<String> getBasicTemplateOrElementNames() {
    return basicTemplatesOrElementsMap.keySet();
  }

  /** Look up possible targets for a call. */
  public ImmutableList<TemplateMetadata> getTemplates(CallNode node) {
    if (node instanceof CallBasicNode) {
      String calleeName = ((CallBasicNode) node).getCalleeName();
      TemplateMetadata template = basicTemplatesOrElementsMap.get(calleeName);
      return template == null ? ImmutableList.of() : ImmutableList.of(template);
    } else {
      String calleeName = ((CallDelegateNode) node).getDelCalleeName();
      return delTemplateSelector.delTemplateNameToValues().get(calleeName);
    }
  }

  /**
   * Retrieves a template or element given the template name.
   *
   * @param templateName The basic template name to retrieve.
   * @return The corresponding template/element, or null if the name is not defined.
   */
  @Nullable
  public TemplateMetadata getBasicTemplateOrElement(String templateName) {
    return basicTemplatesOrElementsMap.get(templateName);
  }

  /** Returns a multimap from delegate template name to set of keys. */
  public DelTemplateSelector<TemplateMetadata> getDelTemplateSelector() {
    return delTemplateSelector;
  }

  public TemplateMetadata getMetadata(TemplateNode node) {
    return checkNotNull(
        allTemplates.get(checkNotNull(node.getTemplateName())),
        "couldn't find metadata for %s in %s",
        node,
        allTemplates);
  }

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and {@link
   * TemplateDelegateNode delegate} nodes), in no particular order.
   */
  public ImmutableList<TemplateMetadata> getAllTemplates() {
    return allTemplates.values().asList();
  }

  /**
   * Gets the content kind that a call results in. If used with delegate calls, the delegate
   * templates must use strict autoescaping. This relies on the fact that all delegate calls must
   * have the same kind when using strict autoescaping. This is enforced by CheckDelegatesPass.
   *
   * @param node The {@link CallBasicNode} or {@link CallDelegateNode}.
   * @return The kind of content that the call results in.
   */
  public Optional<SanitizedContentKind> getCallContentKind(CallNode node) {
    ImmutableList<TemplateMetadata> templateNodes = getTemplates(node);
    // For per-file compilation, we may not have any of the delegate templates in the compilation
    // unit.
    if (!templateNodes.isEmpty()) {
      return Optional.fromNullable(templateNodes.get(0).getContentKind());
    }
    // The template node may be null if the template is being compiled in isolation.
    return Optional.absent();
  }

  /** Returns the callgraph for a template. */
  public CallGraphNode getCallGraph(TemplateMetadata template) {
    checkNotNull(template);
    CallGraphNode callGraph = callGraphs.get(template);
    if (callGraph != null) {
      return callGraph;
    }
    callGraph = new CallGraphNode(template);
    // Place the partially constructed callgraph into the map to short circuit following recursive
    // template calls.
    callGraphs.put(template, callGraph);
    for (CallSituation call : template.getCallSituations()) {

      if (call.isDelCall()) {
        for (TemplateMetadata callee :
            delTemplateSelector.delTemplateNameToValues().get(call.getTemplateName())) {
          callGraph.addDelCallee(call, getCallGraph(callee));
        }
      } else {
        TemplateMetadata callee = getBasicTemplateOrElement(call.getTemplateName());
        if (callee != null) {
          callGraph.putBasicCallee(call, getCallGraph(callee));
        }
      }
    }
    callGraph.finishConstruction();
    return callGraph;
  }

  /**
   * Represents a call graph of templates.
   *
   * <p>Note that callgraphs are directed but not acyclic. Self edges are possible. So code
   * traversing this data structure should protect against reentrancy.
   */
  public static final class CallGraphNode {
    // We can't make this object immutable because the graph may have cycles.
    // So instead we use this field to force that all mutations happen before any queries occur.
    private boolean frozen = false;
    private final TemplateMetadata template;
    private final Map<CallSituation, CallGraphNode> basicCallees = new LinkedHashMap<>();
    private final ListMultimap<CallSituation, CallGraphNode> delCallees =
        MultimapBuilder.linkedHashKeys().arrayListValues().build();
    // lazily caches the set of transitive callees.
    private Set<TemplateMetadata> transiveCallees;

    private CallGraphNode(TemplateMetadata template) {
      this.template = checkNotNull(template);
    }

    public TemplateMetadata template() {
      return template;
    }

    private void addDelCallee(CallSituation call, CallGraphNode callee) {
      checkState(!frozen);
      checkArgument(call.isDelCall());
      boolean changed = delCallees.put(call, checkNotNull(callee));
      if (!changed) {
        throw new IllegalArgumentException(
            "a callgraph mapping was already assigned for: " + call + " -> " + callee);
      }
    }

    private void putBasicCallee(CallSituation call, CallGraphNode callee) {
      checkState(!frozen);
      checkArgument(!call.isDelCall());
      CallGraphNode prev = basicCallees.put(call, checkNotNull(callee));
      if (prev != null) {
        throw new IllegalArgumentException("a callgraph was already assigned for: " + call);
      }
    }

    private void finishConstruction() {
      checkState(!frozen);
      frozen = true;
    }

    public Set<TemplateMetadata> transitiveCallees() {
      checkState(frozen);
      Set<TemplateMetadata> callees = transiveCallees;
      if (callees != null) {
        return callees;
      }
      callees = new LinkedHashSet<>();
      collectTransitiveCallees(callees);
      // only return unmodifiable views
      transiveCallees = Collections.unmodifiableSet(callees);
      return transiveCallees;
    }

    private void collectTransitiveCallees(Set<TemplateMetadata> callees) {
      if (callees.add(template)) {
        if (transiveCallees != null) {
          callees.addAll(transiveCallees);
        } else {
          for (CallGraphNode callee : basicCallees.values()) {
            callee.collectTransitiveCallees(callees);
          }
          for (CallGraphNode callee : delCallees.values()) {
            callee.collectTransitiveCallees(callees);
          }
        }
      }
    }

    /**
     * Returns the call graph for the callee, or nuill. if null is returned, then it means we could
     * not resolve the target. This can happen for delcalls or regular calls when allowExternalCalls
     * is enabled. (or temporarily while building).
     */
    public CallGraphNode getBasicCallee(CallSituation call) {
      checkArgument(!call.isDelCall());
      return basicCallees.get(checkNotNull(call));
    }

    /**
     * Returns the call graph for the potential callees, or an empty collection. if null is
     * returned, then it means we could not resolve the target. This can happen for delcalls or
     * regular calls when allowExternalCalls is enabled. (or temporarily while building).
     */
    public List<CallGraphNode> getDelCallees(CallSituation call) {
      checkArgument(call.isDelCall());
      return delCallees.get(checkNotNull(call));
    }
  }
}
