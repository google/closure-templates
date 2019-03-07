/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.incrementaldomsrc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.NodeVisitor;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SoyTreeUtils.VisitDirective;

/**
 * The contextual autoescaper adds some escaping directives that are not necessary for
 * incrementaldom since we are not dealing with raw text. For example, instead of using {@code
 * |escapeHtml} to embed untrusted content into html PCDATA we can just rely on the {@code
 * incrementaldom.text} function to handle this.
 *
 * <p>This pass will remove such escaping directives so that output is not overescaped.
 *
 * <p>TODO(b/71896143): this is a partial solution and in general incremental dom requires a full
 * security review. Also, a better solution would probably involve adding a specialization of these
 * directives for incremental dom or possibly moving this logic directly into the code generator.
 */
final class RemoveUnnecessaryEscapingDirectives {

  private final ImmutableMap<String, SoyLibraryAssistedJsSrcPrintDirective> directives;

  private static final String BIDI_UNICODE_WRAP = "|bidiUnicodeWrap";

  public RemoveUnnecessaryEscapingDirectives(BidiGlobalDir bidiGlobalDir) {
    directives =
        ImmutableMap.of(
            EscapingMode.ESCAPE_HTML.directiveName,
            new EscapeHtmlDirective(),
            EscapingMode.FILTER_HTML_ATTRIBUTES.directiveName,
            new FilterHtmlAttributesDirective(),
            BIDI_UNICODE_WRAP,
            new BidiUnicodeWrapDirective(bidiGlobalDir));
  }

  private NodeVisitor<Node, VisitDirective> getVisitor() {
    return new NodeVisitor<Node, VisitDirective>() {
      @Override
      public VisitDirective exec(Node node) {
        // escaping directives are only applied to print,call, and msg nodes
        if (node instanceof ExprNode) {
          return VisitDirective.SKIP_CHILDREN;
        }
        SoyNode soyNode = (SoyNode) node;
        switch (soyNode.getKind()) {
          case PRINT_NODE:
            {
              PrintNode printNode = (PrintNode) soyNode;
              // Remove all skippable nodes from the right first
              for (int i = printNode.numChildren() - 1; i >= 0; i--) {
                PrintDirectiveNode pdn = printNode.getChild(i);
                SoyPrintDirective directive = pdn.getPrintDirective();
                // If there are any directives to the left of a sanitized content operator,
                // keep them.
                if (directive instanceof SanitizedContentOperator) {
                  break;
                }
                if (pdn.isSynthetic() && canSkip(directive)) {
                  printNode.removeChild(i);
                }
              }
              // Replace directives with their Incremental DOM version
              for (PrintDirectiveNode pdn : printNode.getChildren()) {
                String directiveName = pdn.getPrintDirective().getName();
                // Replacing this directive is required for plumbing through the IncrementalDom
                // renderer. However, in a text context this isn't required since only text
                // is being emitted.
                if (printNode.getHtmlContext() == HtmlContext.TEXT
                    && directiveName.equals(BIDI_UNICODE_WRAP)) {
                  continue;
                }
                if (directives.containsKey(directiveName)) {
                  printNode.replaceChild(
                      pdn,
                      PrintDirectiveNode.createSyntheticNode(
                          pdn.getId(),
                          Identifier.create(pdn.getName(), pdn.getNameLocation()),
                          pdn.getSourceLocation(),
                          directives.get(directiveName)));
                }
              }
              return VisitDirective.SKIP_CHILDREN; // no need to look at the PrintDirectiveNodes
            }

          case CALL_BASIC_NODE:
          case CALL_DELEGATE_NODE:
            {
              CallNode callNode = (CallNode) soyNode;
              callNode.setEscapingDirectives(
                  filterEscapingDirectives(callNode.getEscapingDirectives()));
              return VisitDirective.CONTINUE;
            }
          case MSG_FALLBACK_GROUP_NODE:
            {
              MsgFallbackGroupNode msgNode = (MsgFallbackGroupNode) soyNode;
              msgNode.setEscapingDirectives(
                  filterEscapingDirectives(msgNode.getEscapingDirectives()));

              return VisitDirective.CONTINUE;
            }
          default:
            return VisitDirective.CONTINUE;
        }
      }
    };
  }

  void run(SoyFileSetNode fileSet) {
    NodeVisitor<Node, VisitDirective> visitor = getVisitor();
    // no point in modifying non-src files since we don't be generating code for them
    for (SoyFileNode file : fileSet.getChildren()) {
      SoyTreeUtils.visitAllNodes(file, visitor);
    }
  }

  private static ImmutableList<SoyPrintDirective> filterEscapingDirectives(
      ImmutableList<SoyPrintDirective> escapingDirectives) {
    for (int i = 0; i < escapingDirectives.size(); i++) {
      SoyPrintDirective directive = escapingDirectives.get(i);
      if (canSkip(directive)) {
        ImmutableList.Builder<SoyPrintDirective> builder = ImmutableList.builder();
        builder.addAll(escapingDirectives.subList(0, i));
        for (; i < escapingDirectives.size(); i++) {
          directive = escapingDirectives.get(i);
          if (!canSkip(directive)) {
            builder.add(directive);
          }
        }
        return builder.build();
      }
    }
    return escapingDirectives;
  }

  private static final ImmutableSet<String> SKIPPABLE_ESCAPING_MODES =
      ImmutableSet.of(
          // These will get automatically handled by the incrementaldom.text method
          EscapingMode.ESCAPE_HTML.directiveName,
          EscapingMode.ESCAPE_HTML.directiveName,
          EscapingMode.NORMALIZE_HTML.directiveName,
          EscapingMode.ESCAPE_HTML_RCDATA.directiveName,
          // Attribute values are handled by the incrementaldom.attr method
          EscapingMode.ESCAPE_HTML_ATTRIBUTE.directiveName,
          EscapingMode.ESCAPE_HTML_ATTRIBUTE_NOSPACE.directiveName);

  private static boolean canSkip(SoyPrintDirective printDirective) {
    return SKIPPABLE_ESCAPING_MODES.contains(printDirective.getName());
  }
}
