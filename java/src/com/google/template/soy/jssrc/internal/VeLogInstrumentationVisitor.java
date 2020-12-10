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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.passes.DesugarHtmlNodesPass;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode.TagExistence;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.VeLogNode;

/**
 * Instruments {velog} commands and adds necessary data attributes to the top-level DOM node and
 * tags with logging functions.
 */
final class VeLogInstrumentationVisitor extends AbstractSoyNodeVisitor<Void> {
  private final TemplateRegistry templateRegistry;
  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;

  VeLogInstrumentationVisitor(TemplateRegistry templateRegistry) {
    this.templateRegistry = templateRegistry;
  }

  @Override
  protected void visitSoyFileSetNode(SoyFileSetNode node) {
    // Retrieve the node id generator.
    nodeIdGen = node.getNodeIdGenerator();
    ImmutableList<SoyFileNode> sourceFiles = ImmutableList.copyOf(node.getChildren());
    for (SoyFileNode fileNode : sourceFiles) {
      visitSoyFileNode(fileNode);
    }
    // Run the desugaring pass and combine raw text nodes after we instrument velog node.
    new DesugarHtmlNodesPass().run(sourceFiles, nodeIdGen, templateRegistry);
  }

  private static FunctionNode getLoggingFunction(CallParamNode paramNode) {
    if (!(paramNode instanceof CallParamContentNode)) {
      return null;
    }
    CallParamContentNode callParamNode = (CallParamContentNode) paramNode;
    if (callParamNode.numChildren() != 1 || !(callParamNode.getChild(0) instanceof PrintNode)) {
      return null;
    }
    PrintNode printNode = (PrintNode) callParamNode.getChild(0);
    if (!(printNode.getExpr().getRoot() instanceof FunctionNode)) {
      return null;
    }
    FunctionNode fnNode = (FunctionNode) printNode.getExpr().getRoot();
    if (fnNode.getSoyFunction() instanceof LoggingFunction) {
      return fnNode;
    }
    return null;
  }

  /**
   * Element composition calls are deconstructed into call nodes. However, some of the attributes
   * contain velogging functions. This takes those attributes and puts them on a wrapping `veAttr`
   * element, which the runtime libarary then manages. So the overall DOM structure becomes
   *
   * <pre>{@code
   * <velog>
   *   <veattr data-loggingsoyfunction-{ATTR}="...">
   *      <tag {ATTR}="placeholder"></tag>
   *   </veattr>
   * </velog>
   * }</pre>
   *
   * Both <velog> and <vattr> become reparented at some point.
   */
  @Override
  protected void visitCallNode(CallNode node) {
    ImmutableList<CallParamContentNode> paramsContainingVelogFunctions =
        node.getChildren().stream()
            .filter(c -> getLoggingFunction(c) != null)
            .map(c -> (CallParamContentNode) c)
            .collect(toImmutableList());
    if (paramsContainingVelogFunctions.isEmpty()) {
      visitChildrenAllowingConcurrentModification(node);
      return;
    }
    HtmlOpenTagNode openTagNode = soyHtmlOpenTagNode(nodeIdGen);
    for (CallParamContentNode callParamContentNode : paramsContainingVelogFunctions) {
      // Construct the data-loggingsoyfunction-{ATTR}() call below.
      FunctionNode funcNode =
          FunctionNode.newPositional(
              Identifier.create(VeLogJsSrcLoggingFunction.NAME, SourceLocation.UNKNOWN),
              VeLogJsSrcLoggingFunction.INSTANCE,
              SourceLocation.UNKNOWN);
      FunctionNode function = getLoggingFunction(callParamContentNode);
      funcNode.addChild(
          new StringNode(
              function.getStaticFunctionName(), QuoteStyle.SINGLE, SourceLocation.UNKNOWN));
      funcNode.addChild(new ListLiteralNode(function.getChildren(), SourceLocation.UNKNOWN));
      funcNode.addChild(
          new StringNode(
              callParamContentNode.getOriginalName(), QuoteStyle.SINGLE, SourceLocation.UNKNOWN));
      PrintNode loggingFunctionAttribute =
          new PrintNode(
              nodeIdGen.genId(),
              SourceLocation.UNKNOWN,
              /* isImplicit= */ true,
              /* expr= */ funcNode,
              /* attributes= */ ImmutableList.of(),
              ErrorReporter.exploding());
      // Add the attribute to our synthetic tag
      openTagNode.addChild(loggingFunctionAttribute);
      // Replace the call param content with a placeholder
      callParamContentNode.replaceChild(
          0,
          new PrintNode(
              nodeIdGen.genId(),
              SourceLocation.UNKNOWN,
              /* isImplicit= */ true,
              /* expr= */ new StringNode(
                  ((LoggingFunction) function.getSoyFunction()).getPlaceholder(),
                  QuoteStyle.SINGLE,
                  SourceLocation.UNKNOWN),
              /* attributes= */ ImmutableList.of(),
              ErrorReporter.exploding()));
    }
    node.getParent().addChild(node.getParent().getChildIndex(node), openTagNode);
    node.getParent()
        .addChild(node.getParent().getChildIndex(node) + 1, soyHtmlCloseTagNode(nodeIdGen));
  }

  private static HtmlOpenTagNode soyHtmlOpenTagNode(IdGenerator idGenerator) {
    return new HtmlOpenTagNode(
        idGenerator.genId(),
        new RawTextNode(idGenerator.genId(), "veAttr", SourceLocation.UNKNOWN),
        SourceLocation.UNKNOWN,
        /** selfClosing */
        false,
        TagExistence.IN_TEMPLATE);
  }

  private static HtmlCloseTagNode soyHtmlCloseTagNode(IdGenerator idGenerator) {
    return new HtmlCloseTagNode(
        idGenerator.genId(),
        new RawTextNode(idGenerator.genId(), "veAttr", SourceLocation.UNKNOWN),
        SourceLocation.UNKNOWN,
        TagExistence.IN_TEMPLATE);
  }

  /** Adds data-soylog attribute to the top-level DOM node in this {velog} block. */
  @Override
  protected void visitVeLogNode(VeLogNode node) {
    // VeLogValidationPass marks nodes where the first child is not either an open tag or a call as
    // needing a synthetic VE log node. Synthetic ve log nodes are handled separately in
    // GenJSCodeVisitor, so this only handles the case where the open tag is visible.
    if (!node.needsSyntheticVelogNode()) {
      HtmlOpenTagNode tag = node.getOpenTagNode();
      SourceLocation insertionLocation =
          tag.getSourceLocation()
              .getEndPoint()
              .offset(0, tag.isSelfClosing() ? -2 : -1)
              .asLocation(tag.getSourceLocation().getFilePath());
      FunctionNode funcNode =
          FunctionNode.newPositional(
              Identifier.create(VeLogFunction.NAME, insertionLocation),
              VeLogFunction.INSTANCE,
              insertionLocation);
      funcNode.addChild(node.getVeDataExpression().copy(new CopyState()));
      if (node.getLogonlyExpression() != null) {
        funcNode.addChild(node.getLogonlyExpression().copy(new CopyState()));
      }
      PrintNode attributeNode =
          new PrintNode(
              nodeIdGen.genId(),
              insertionLocation,
              /* isImplicit= */ true,
              /* expr= */ funcNode,
              /* attributes= */ ImmutableList.of(),
              ErrorReporter.exploding());
      tag.addChild(attributeNode);
    }
    visitChildrenAllowingConcurrentModification(node);
  }

  /**
   * For HtmlAttributeNode that has a logging function as its value, replace the logging function
   * with its place holder, and append a new data attribute that contains all the desired
   * information that are used later by the runtime library.
   */
  @Override
  protected void visitHtmlAttributeNode(HtmlAttributeNode node) {
    // Skip attributes that do not have a value.
    if (!node.hasValue()) {
      return;
    }
    SourceLocation insertionLocation = node.getSourceLocation();
    for (FunctionNode function : SoyTreeUtils.getAllNodesOfType(node, FunctionNode.class)) {
      if (!(function.getSoyFunction() instanceof LoggingFunction)) {
        continue;
      }
      FunctionNode funcNode =
          FunctionNode.newPositional(
              Identifier.create(VeLogJsSrcLoggingFunction.NAME, insertionLocation),
              VeLogJsSrcLoggingFunction.INSTANCE,
              insertionLocation);
      funcNode.addChild(
          new StringNode(function.getStaticFunctionName(), QuoteStyle.SINGLE, insertionLocation));
      funcNode.addChild(new ListLiteralNode(function.getChildren(), insertionLocation));
      StandaloneNode attributeName = node.getChild(0);
      if (attributeName instanceof RawTextNode) {
        // If attribute name is a plain text, directly pass it as a function argument.
        funcNode.addChild(
            new StringNode(
                ((RawTextNode) attributeName).getRawText(), QuoteStyle.SINGLE, insertionLocation));
      } else {
        // Otherwise wrap the print node or call node into a let block, and use the let variable
        // as a function argument.
        String varName = "$soy_logging_function_attribute_" + node.getId();
        LetContentNode letNode =
            LetContentNode.forVariable(
                nodeIdGen.genId(),
                attributeName.getSourceLocation(),
                varName,
                attributeName.getSourceLocation(),
                SanitizedContentKind.TEXT);
        // Adds a let var which references to the original attribute name, and move the name to
        // the let block.
        node.replaceChild(
            attributeName,
            new PrintNode(
                nodeIdGen.genId(),
                insertionLocation,
                /* isImplicit= */ true,
                /* expr= */ new VarRefNode(
                    "$" + letNode.getVar().name(), insertionLocation, letNode.getVar()),
                /* attributes= */ ImmutableList.of(),
                ErrorReporter.exploding()));
        letNode.addChild(attributeName);
        node.getParent().addChild(node.getParent().getChildIndex(node), letNode);
        funcNode.addChild(
            new VarRefNode("$" + letNode.getVar().name(), insertionLocation, letNode.getVar()));
      }
      PrintNode loggingFunctionAttribute =
          new PrintNode(
              nodeIdGen.genId(),
              insertionLocation,
              /* isImplicit= */ true,
              /* expr= */ funcNode,
              /* attributes= */ ImmutableList.of(),
              ErrorReporter.exploding());
      // Append the logging function attribute to its parent
      int appendIndex = node.getParent().getChildIndex(node) + 1;
      node.getParent().addChild(appendIndex, loggingFunctionAttribute);
      // Replace the original attribute value to the placeholder.
      HtmlAttributeValueNode placeHolder =
          new HtmlAttributeValueNode(nodeIdGen.genId(), insertionLocation, Quotes.DOUBLE);
      placeHolder.addChild(
          new RawTextNode(
              nodeIdGen.genId(),
              ((LoggingFunction) function.getSoyFunction()).getPlaceholder(),
              insertionLocation));
      node.replaceChild(node.getChild(1), placeHolder);
      // We can break here since VeLogValidationPass guarantees that there is exactly one
      // logging function in a html attribute value.
      break;
    }
    visitChildrenAllowingConcurrentModification(node);
  }

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }
}
