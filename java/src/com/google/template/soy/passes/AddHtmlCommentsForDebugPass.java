/*
 * Copyright 2017 Google Inc.
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

import static com.google.common.html.HtmlEscapers.htmlEscaper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basicfunctions.DebugSoyTemplateInfoFunction;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlCommentNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.primitive.BoolType;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

/**
 * Prepends and appends HTML comments for every {@code TemplateNode}.
 *
 * <p>This pass supports the debug view for inspecting template information in rendered pages. See
 * go/inspect-template-info-fw for details.
 */
public final class AddHtmlCommentsForDebugPass extends CompilerFileSetPass {
  private static final String HTML_COMMENTS_PREFIX = "dta_of(%s, %s, %s)";
  private static final String HTML_COMMENTS_SUFFIX = "dta_cf(%s)";

  @Override
  public void run(SoyFileSetNode fileSet, TemplateRegistry registry) {
    // First get all templates that definitely need to be rewritten. This only relies on the local
    // information and none of the callees are checked.
    Set<TemplateNode> templatesToRewrite = getTemplatesToRewrite(registry);
    // Build a caller map that can be used for finding all callers of a particular template.
    Multimap<TemplateNode, TemplateNode> strictHtmlCallers = buildStrictHtmlCallers(registry);
    // All transitive callers of templates in {@code templatesToRewrite} also need to be rewritten.
    collectCallers(templatesToRewrite, strictHtmlCallers);
    // Actually rewrite all templates and insert prefix/suffix.
    rewriteTemplates(templatesToRewrite, fileSet.getNodeIdGenerator());
  }

  /**
   * Builds a lookup map that can be used for finding all callers of a particular template. All
   * entries in the map (both keys and values) are HTML templates with strict auto escape mode.
   */
  private Multimap<TemplateNode, TemplateNode> buildStrictHtmlCallers(TemplateRegistry registry) {
    Multimap<TemplateNode, TemplateNode> strictHtmlCallers = LinkedHashMultimap.create();
    for (TemplateNode template : registry.getAllTemplates()) {
      if (!isStrictAndKindHtml(template)) {
        continue;
      }
      // Updates callers map.
      for (CallNode callNode : SoyTreeUtils.getAllNodesOfType(template, CallNode.class)) {
        if (callNode instanceof CallBasicNode) {
          TemplateNode callee =
              registry.getBasicTemplate(((CallBasicNode) callNode).getCalleeName());
          // Do not add edges if the callee is non-html and/or non-strict auto escape mode.
          if (isStrictAndKindHtml(callee)) {
            strictHtmlCallers.put(callee, template);
          }
        } else if (callNode instanceof CallDelegateNode) {
          ImmutableList<TemplateDelegateNode> potentialCallees =
              registry
                  .getDelTemplateSelector()
                  .delTemplateNameToValues()
                  .get(((CallDelegateNode) callNode).getDelCalleeName());
          for (TemplateDelegateNode callee : potentialCallees) {
            // Do not add edges if the callee is non-html and/or non-strict auto escape mode.
            if (isStrictAndKindHtml(callee)) {
              strictHtmlCallers.put(callee, template);
            }
          }
        }
      }
    }
    return strictHtmlCallers;
  }

  /** Returns a set of templates that definitely need to be instrumented. */
  private Set<TemplateNode> getTemplatesToRewrite(TemplateRegistry registry) {
    Set<TemplateNode> templatesToRewrite = Sets.newLinkedHashSet();
    for (TemplateNode template : registry.getAllTemplates()) {
      // We don't care about templates that are non-html and/or non-strict auto escape mode.
      if (!isStrictAndKindHtml(template)) {
        continue;
      }
      // If the current template contains HTML nodes (tags and/or comments) or HTML parameters,
      // we should always rewrite it.
      if (hasHtmlParamOrNode(template)) {
        templatesToRewrite.add(template);
      }
    }
    return templatesToRewrite;
  }

  /** Tries to do simple BFS and mark all callers. */
  private void collectCallers(
      Set<TemplateNode> templatesToRewrite,
      Multimap<TemplateNode, TemplateNode> strictHtmlCallers) {
    Queue<TemplateNode> queue = new ArrayDeque<>();
    queue.addAll(templatesToRewrite);
    while (!queue.isEmpty()) {
      TemplateNode node = queue.poll();
      for (TemplateNode caller : strictHtmlCallers.get(node)) {
        // Only push to queue if caller has not been visited yet.
        if (templatesToRewrite.add(caller)) {
          queue.add(caller);
        }
      }
    }
  }

  /** Actual rewrite the template nodes for adding HTML comments. */
  private void rewriteTemplates(Set<TemplateNode> templatesToRewrite, IdGenerator nodeIdGen) {
    for (TemplateNode node : templatesToRewrite) {
      String templateName = getTemplateName(node);
      SourceLocation insertLocation = node.getSourceLocation();
      node.addChild(
          0,
          createSoyDebug(
              insertLocation,
              nodeIdGen,
              String.format(
                  HTML_COMMENTS_PREFIX,
                  templateName,
                  insertLocation.getFilePath(),
                  insertLocation.getBeginLine())));
      node.addChild(
          createSoyDebug(
              insertLocation, nodeIdGen, String.format(HTML_COMMENTS_SUFFIX, templateName)));
    }
  }

  /**
   * Return template name for display. For deltemplates, getTemplateName() contains hashes that
   * should not be displayed in the rendered HTML comments.
   */
  private static String getTemplateName(TemplateNode node) {
    if (node instanceof TemplateDelegateNode) {
      return ((TemplateDelegateNode) node).getDelTemplateName();
    } else {
      return node.getTemplateName();
    }
  }

  /**
   * Generates an AST fragment that looks like:
   *
   * <p>{@code {if debugSoyTemplateInfo()}<!--dta_of...-->{/if}}
   *
   * @param insertionLocation The location where it is being inserted
   * @param nodeIdGen The id generator to use
   * @param htmlComment The content of the HTML comment
   */
  private IfNode createSoyDebug(
      SourceLocation insertionLocation, IdGenerator nodeIdGen, String htmlComment) {
    IfNode ifNode = new IfNode(nodeIdGen.genId(), insertionLocation);
    FunctionNode funcNode = new FunctionNode(DebugSoyTemplateInfoFunction.NAME, insertionLocation);
    funcNode.setSoyFunction(DebugSoyTemplateInfoFunction.INSTANCE);
    funcNode.setType(BoolType.getInstance());
    IfCondNode ifCondNode = new IfCondNode(nodeIdGen.genId(), insertionLocation, "if", funcNode);
    HtmlCommentNode htmlCommentNode = new HtmlCommentNode(nodeIdGen.genId(), insertionLocation);
    // We need to escape the input HTML comments, in cases the file location contains "-->".
    htmlCommentNode.addChild(
        new RawTextNode(nodeIdGen.genId(), htmlEscaper().escape(htmlComment), insertionLocation));
    ifCondNode.addChild(htmlCommentNode);
    ifNode.addChild(ifCondNode);
    return ifNode;
  }

  /**
   * Checks if we should definitely add HTML comments for a template. In particular, return true if
   * any of the template params is HTML, or the template contains HTML tag/comment nodes.
   */
  private boolean hasHtmlParamOrNode(TemplateNode node) {
    // If any of the template params is HTML, we always add HTML comments.
    for (TemplateParam param : node.getParams()) {
      if (param.type().getKind() == SoyType.Kind.HTML) {
        return true;
      }
    }
    return SoyTreeUtils.hasNodesOfType(
        node, HtmlOpenTagNode.class, HtmlCloseTagNode.class, HtmlCommentNode.class);
  }

  /**
   * Checks if a template has kind="html" and autoescape="strict". Generally we do not care about a
   * template if this returns false.
   */
  private boolean isStrictAndKindHtml(TemplateNode node) {
    return node != null
        && node.getContentKind() == SanitizedContentKind.HTML
        && node.getAutoescapeMode() == AutoescapeMode.STRICT;
  }
}
