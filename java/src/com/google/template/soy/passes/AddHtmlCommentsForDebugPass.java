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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;

/**
 * Prepends and appends HTML comments for every {@code TemplateNode}.
 *
 * <p>This pass supports the debug view for inspecting template information in rendered pages. See
 * go/inspect-template-info-fw for details.
 *
 * <p>This pass needs to be run before {@code ResolveNamesPass}. This pass creates a {@code
 * VarRefNode} with null definition that will be resolved by {@code ResolveNamesPass}.
 */
public final class AddHtmlCommentsForDebugPass extends CompilerFilePass {
  public static final String DEBUG_VARIABLE_NAME = "debug_soy_template_info";

  private static final SoyErrorKind IJ_DEBUG_REFERENCE =
      SoyErrorKind.of(
          "Found a use of the injected parameter ''debug_soy_template_info''. "
              + "This parameter is reserved by the Soy compiler.");

  private static final SoyErrorKind DEBUG_MODE_BANNED =
      SoyErrorKind.of(
          "Found a use of the reserved built-in function debugMode(). "
              + "This is currently disallowed.");

  private final ErrorReporter errorReporter;

  AddHtmlCommentsForDebugPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    new AddHtmlCommentsForDebugVisitor(nodeIdGen).exec(file);
  }

  private final class AddHtmlCommentsForDebugVisitor extends AbstractSoyNodeVisitor<Void> {
    private static final String HTML_COMMENTS_PREFIX = "<!--dta_of(%s, %s)-->";
    private static final String HTML_COMMENTS_SUFFIX = "<!--dta_cf(%s)-->";

    private final IdGenerator nodeIdGen;
    private String filePath = "";

    AddHtmlCommentsForDebugVisitor(IdGenerator nodeIdGen) {
      this.nodeIdGen = nodeIdGen;
    }

    @Override
    protected void visitSoyFileNode(SoyFileNode node) {
      this.filePath = node.getFilePath();
      visitChildren(node);
    }

    @Override
    protected void visitTemplateNode(TemplateNode node) {
      for (TemplateParam param : node.getAllParams()) {
        if (param.isInjected() && param.name().equals(DEBUG_VARIABLE_NAME)) {
          errorReporter.report(param.nameLocation(), IJ_DEBUG_REFERENCE);
        }
      }
      for (VarRefNode var : SoyTreeUtils.getAllNodesOfType(node, VarRefNode.class)) {
        if (var.isDollarSignIjParameter() && var.getName().equals(DEBUG_VARIABLE_NAME)) {
          errorReporter.report(var.getSourceLocation(), IJ_DEBUG_REFERENCE);
        }
      }
      for (FunctionNode func : SoyTreeUtils.getAllNodesOfType(node, FunctionNode.class)) {
        if (func.getFunctionName().equals(BuiltinFunction.DEBUG_MODE.getName())) {
          errorReporter.report(func.getSourceLocation(), DEBUG_MODE_BANNED);
        }
      }
      // Only adds HTML comments for HTML contents.
      if (node.getContentKind() != ContentKind.HTML) {
        return;
      }
      // Only adds HTML comments for strict auto escape mode.
      if (node.getAutoescapeMode() != AutoescapeMode.STRICT) {
        return;
      }
      String templateName;
      if (node instanceof TemplateDelegateNode) {
        templateName = ((TemplateDelegateNode) node).getDelTemplateName();
      } else {
        templateName = node.getTemplateName();
      }
      // TODO(b/62464400): Fix the source location.
      node.addChild(
          0,
          createSoyDebug(
              node.getSourceLocation(),
              nodeIdGen,
              String.format(HTML_COMMENTS_PREFIX, templateName, this.filePath)));
      node.addChild(
          createSoyDebug(
              node.getSourceLocation(),
              nodeIdGen,
              String.format(HTML_COMMENTS_SUFFIX, templateName)));
      visitChildren(node);
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }

    /**
     * Generates an AST fragment that looks like:
     *
     * <p>{@code {if debugMode() and $ij.debug_soy_template_info}<!--dta_of...-->{/if}}
     *
     * @param insertionLocation The location where it is being inserted
     * @param nodeIdGen The id generator to use
     * @param htmlComment The content of the HTML comment
     */
    private IfNode createSoyDebug(
        SourceLocation insertionLocation, IdGenerator nodeIdGen, String htmlComment) {
      IfNode ifNode = new IfNode(nodeIdGen.genId(), insertionLocation);
      AndOpNode exprNode = new AndOpNode(insertionLocation);
      exprNode.addChild(new FunctionNode(BuiltinFunction.DEBUG_MODE.getName(), insertionLocation));
      exprNode.addChild(
          new VarRefNode(
              DEBUG_VARIABLE_NAME, insertionLocation, /*injected=*/ true, /*defn=*/ null));
      IfCondNode ifCondNode = new IfCondNode(nodeIdGen.genId(), insertionLocation, "if", exprNode);
      ifNode.addChild(ifCondNode);
      RawTextNode htmlCommentNode =
          new RawTextNode(nodeIdGen.genId(), htmlComment, insertionLocation);
      ifCondNode.addChild(htmlCommentNode);
      return ifNode;
    }
  }
}
