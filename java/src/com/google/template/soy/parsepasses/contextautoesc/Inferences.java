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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Maps;
import com.google.template.soy.coredirectives.NoAutoescapeDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates information inferred about a Soy file and decisions made to change it.
 *
 * <p>The mutator methods on this class do not change the Soy file. All changes are delayed until
 * after all inference has been done so that we can safely try a variety of speculative techniques.
 *
 * <p>To make it easier to do speculative inference, this class cascades : each instance has a
 * parent, and it delegates to that when it does not have info itself. And there is a {@link
 * Inferences#foldIntoParent()} method that propagates all decisions into the parent when a set of
 * inference decisions are considered final.
 *
 * <p>The {@link ContextualAutoescaper} creates a single root instance and its passes fold
 * successful inferences into the parent until it ends up with a final set of rewriting decisions
 * that the {@link Rewriter} applies to the input Soy parse tree.
 *
 */
final class Inferences {

  /** Map of template names to instances used to type <code>{call}</code> commands. */
  private final ImmutableListMultimap<String, TemplateNode> templatesByName;

  /** The types of templates. */
  private final Map<String, Context> templateNameToEndContext = Maps.newLinkedHashMap();

  /** Maps print, msg and call commands to the inferred escaping modes. */
  private final Map<SoyNode, ImmutableList<EscapingMode>> nodeToEscapingModes =
      Maps.newIdentityHashMap();

  /** Maps print, msg and call commands to the context. */
  private final Map<SoyNode, Context> nodeToContext = Maps.newIdentityHashMap();
  /**
   * An instance that does not inherit from a parent.
   *
   * @param idGen Used to generate unique IDs for cloned templates.
   * @param templatesByName Map of template names to instances used to type <code>{call}</code>
   *     commands.
   */
  public Inferences(ImmutableListMultimap<String, TemplateNode> templatesByName) {
    this.templatesByName = templatesByName;
  }

  /**
   * Stores a type conclusion.  This may be speculative.
   * @param templateName A qualified template name.
   */
  public void recordTemplateEndContext(String templateName, Context context) {
    templateNameToEndContext.put(templateName, context);
  }

  /**
   * Finds the named templates.
   *
   * @param templateName A qualified template name.
   */
  List<TemplateNode> lookupTemplates(String templateName) {
    return templatesByName.get(templateName);
  }

  /**
   * Null if no typing has been done for the named template, or otherwise the context after a call
   * to the named template.  Since we derive templates by start context at the call site, there
   * is no start context parameter.
   *
   * @param templateName A qualified template name.
   */
  public Context getTemplateEndContext(String templateName) {
    return templateNameToEndContext.get(templateName);
  }

  /**
   * Null if there is no escaping mode for the given <code>{print}</code> node.
   */
  public ImmutableList<EscapingMode> getEscapingMode(PrintNode printNode) {
    // See if we have already inferred an escaping mode for the node.
    ImmutableList<EscapingMode> escapingModes = nodeToEscapingModes.get(printNode);
    if (escapingModes != null) {
      return escapingModes;
    }

    // Look for an escaping mode in the existing directives.
    ImmutableList.Builder<EscapingMode> modes = ImmutableList.builder();
    for (PrintDirectiveNode directiveNode : printNode.getChildren()) {
      // TODO(lukes): it is mostly illegal to add an escaping directive to a template as most have
      // been marked as internalOnly.  See if this can be simplified or deleted.
      EscapingMode mode = EscapingMode.fromDirective(directiveNode.getName());
      if (mode != null) {
        modes.add(mode);
      } else if (directiveNode.getPrintDirective() instanceof NoAutoescapeDirective) {
        modes.add(EscapingMode.NO_AUTOESCAPE);
      }
    }
    return modes.build();
  }

  /** Records inferred escaping modes so a directive can be later added to the Soy parse tree. */
  public void setEscapingDirectives(
      SoyNode node, Context context, List<EscapingMode> escapingModes) {
    Preconditions.checkArgument(
        (node instanceof PrintNode)
            || (node instanceof CallNode)
            || (node instanceof MsgFallbackGroupNode),
        "Escaping directives may only be set for {print}, {msg}, or {call} nodes");
    if (escapingModes != null) {
      nodeToEscapingModes.put(node, ImmutableList.copyOf(escapingModes));
    }
    nodeToContext.put(node, context);
  }

  /**
   * The escaping modes for the print command with the given ID in the order in which they should be
   * applied.
    *
   * @param node a node instance
   */
  public ImmutableList<EscapingMode> getEscapingModesForNode(SoyNode node) {
    ImmutableList<EscapingMode> modes = nodeToEscapingModes.get(node);
    if (modes == null) {
      modes = ImmutableList.of();
    }
    return modes;
  }

  @VisibleForTesting
  Context getContextForNode(SoyNode node) {
    return nodeToContext.get(node);
  }

  /**
   * All known templates.
   */
  public List<TemplateNode> getAllTemplates() {
    return ImmutableList.copyOf(templatesByName.values());
  }

}
