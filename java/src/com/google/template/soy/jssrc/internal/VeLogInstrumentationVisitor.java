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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.basicfunctions.VeLogFunction;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.passes.CombineConsecutiveRawTextNodesPass;
import com.google.template.soy.passes.DesugarHtmlNodesPass;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.defn.InjectedParam;

/** Instruments {velog} commands and adds necessary data attribute to the top-level DOM node. */
final class VeLogInstrumentationVisitor extends AbstractSoyNodeVisitor<Void> {
  private final TemplateRegistry templateRegistry;
  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;

  private static final String LOGGING_METADATA = "$$loggingMetaData";
  private static final InjectedParam LOGGING_METADATA_IJ = new InjectedParam(LOGGING_METADATA);

  VeLogInstrumentationVisitor(TemplateRegistry templateRegistry) {
    this.templateRegistry = templateRegistry;
  }

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // Retrieve the node id generator.
    nodeIdGen = node.getNodeIdGenerator();
    visitChildren(node);
    // Run the desugaring pass and combine raw text nodes after we instrument velog node.
    new DesugarHtmlNodesPass().run(node, templateRegistry);
    new CombineConsecutiveRawTextNodesPass().run(node);
  }

  @Override
  protected void visitVeLogNode(VeLogNode node) {
    // VeLogValidationPass enforces that the first child is a open tag. We can safely cast it here.
    HtmlOpenTagNode tag = (HtmlOpenTagNode) node.getChild(0);
    SourceLocation insertionLocation =
        tag.getSourceLocation()
            .getEndPoint()
            .offset(0, tag.isSelfClosing() ? -2 : -1)
            .asLocation(tag.getSourceLocation().getFilePath());
    IfNode ifNode = new IfNode(nodeIdGen.genId(), insertionLocation);
    IfCondNode ifCondNode = createIfCondForLoggingFunction(nodeIdGen.genId(), insertionLocation);
    ifNode.addChild(ifCondNode);
    FunctionNode funcNode = new FunctionNode(VeLogFunction.NAME, insertionLocation);
    funcNode.setSoyFunction(VeLogFunction.INSTANCE);
    funcNode.addChild(new IntegerNode(node.getLoggingId(), insertionLocation));
    PrintNode attributeValue =
        new PrintNode(
            nodeIdGen.genId(),
            insertionLocation,
            /* isImplicit= */ true,
            /* expr= */ funcNode,
            /* phname */ null,
            ErrorReporter.exploding());
    HtmlAttributeNode dataAttributeNode =
        createHtmlAttribute("soylog", attributeValue, nodeIdGen, insertionLocation);
    ifCondNode.addChild(dataAttributeNode);
    tag.addChild(ifNode);
    visitChildren(node);
  }

  @Override
  protected void visitHtmlAttributeValueNode(HtmlAttributeValueNode node) {
    SourceLocation insertionLocation = node.getSourceLocation();
    for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(node, FunctionNode.class)) {
      if (!(function.getSoyFunction() instanceof LoggingFunction)) {
        continue;
      }
      IfNode ifNode = new IfNode(nodeIdGen.genId(), insertionLocation);
      IfCondNode ifCondNode = createIfCondForLoggingFunction(nodeIdGen.genId(), insertionLocation);
      // TODO(user): The value should be the logging function.
      ifCondNode.addChild(new RawTextNode(nodeIdGen.genId(), "foo", insertionLocation));
      IfElseNode ifElseNode = new IfElseNode(nodeIdGen.genId(), insertionLocation);
      ifElseNode.addChild(
          new RawTextNode(
              nodeIdGen.genId(),
              ((LoggingFunction) function.getSoyFunction()).getPlaceholder(),
              insertionLocation));
      ifNode.addChild(ifCondNode);
      ifNode.addChild(ifElseNode);
      node.replaceChild(0, ifNode);
      // We can break here since VeLogValidationPass guarantees that there is exactly one
      // logging function in a html attribute value.
      break;
    }
    visitChildren(node);
  }

  private HtmlAttributeNode createHtmlAttribute(
      String attributeName,
      StandaloneNode attributeValue,
      IdGenerator nodeIdGen,
      SourceLocation insertionLocation) {
    HtmlAttributeNode dataAttributeNode =
        new HtmlAttributeNode(
            nodeIdGen.genId(), insertionLocation, insertionLocation.getBeginPoint());
    PlusOpNode plusOp = new PlusOpNode(insertionLocation);
    plusOp.addChild(new StringNode("data-", insertionLocation));
    FunctionNode xidFunction = new FunctionNode(BuiltinFunction.XID.getName(), insertionLocation);
    xidFunction.setSoyFunction(BuiltinFunction.XID);
    xidFunction.addChild(new StringNode(attributeName, insertionLocation));
    plusOp.addChild(xidFunction);
    PrintNode attributeNameNode =
        new PrintNode(
            nodeIdGen.genId(),
            insertionLocation,
            /* isImplicit= */ true,
            /* expr= */ plusOp,
            /* phname */ null,
            ErrorReporter.exploding());
    dataAttributeNode.addChild(attributeNameNode);
    HtmlAttributeValueNode attributeValueNode =
        new HtmlAttributeValueNode(
            nodeIdGen.genId(), insertionLocation, HtmlAttributeValueNode.Quotes.NONE);
    attributeValueNode.addChild(attributeValue);
    dataAttributeNode.addChild(attributeValueNode);
    return dataAttributeNode;
  }

  private static IfCondNode createIfCondForLoggingFunction(
      int nodeId, SourceLocation insertionLocation) {
    return new IfCondNode(
        nodeId,
        insertionLocation,
        "if",
        new VarRefNode(
            LOGGING_METADATA,
            insertionLocation,
            /*injected=*/ true,
            /*defn=*/ LOGGING_METADATA_IJ));
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }
}
