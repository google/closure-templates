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

package com.google.template.soy.passes;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.passes.CompilerFileSetPass.Result;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode.Quotes;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.types.StringType;
import java.util.Optional;

/**
 * This is run in all non-incrementaldom backends and does the following:
 * <li>Downgrades @state in Soy elements to lets.
 * <li>Downgrades {key} to ssk attributes.
 */
final class DesugarStateNodesPass implements CompilerFileSetPass {

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
    }
    return Result.CONTINUE;
  }

  private void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (TemplateNode template : file.getTemplates()) {
      SoyTreeUtils.allNodesOfType(template, HtmlOpenTagNode.class)
          // Template calls are handled in ElementAttributePass and SoyElementCompositionPass
          .filter(tag -> !tag.getTagName().isTemplateCall())
          .forEach(tag -> desugarSkipAndKeyNodes(tag, nodeIdGen));
      if (!(template instanceof TemplateElementNode)) {
        continue;
      }
      TemplateElementNode soyElement = (TemplateElementNode) template;
      ListMultimap<VarDefn, VarRefNode> map = ArrayListMultimap.create();
      for (VarRefNode ref : SoyTreeUtils.getAllNodesOfType(template, VarRefNode.class)) {
        if (ref.getDefnDecl().kind() == VarDefn.Kind.STATE) {
          map.put(ref.getDefnDecl(), ref);
        }
      }
      int stateVarIndex = 0;
      for (TemplateStateVar stateVar : soyElement.getStateVars()) {
        LetValueNode node =
            new LetValueNode(
                nodeIdGen.genId(),
                stateVar.nameLocation(),
                "$" + stateVar.name(),
                stateVar.nameLocation(),
                stateVar.defaultValue().getRoot());
        node.getVar().setType(stateVar.type());
        // insert them in the same order as they were declared
        soyElement.addChild(stateVarIndex, node);
        stateVarIndex++;
        for (VarRefNode ref : map.get(stateVar)) {
          ref.setDefn(node.getVar());
        }
      }
      // After this, no code should be referencing state outside of Incremental DOM.
      soyElement.clearStateVars();
    }
  }

  static FunctionNode extractKeyFunctionFromHtmlTag(
      HtmlOpenTagNode openTag, IdGenerator idGenerator) {
    FunctionNode wrappedFn =
        FunctionNode.newPositional(
            Identifier.create(
                BuiltinFunction.SOY_SERVER_KEY.getName(), openTag.getSourceLocation()),
            BuiltinFunction.SOY_SERVER_KEY,
            openTag.getSourceLocation());
    wrappedFn.setType(StringType.getInstance());
    SourceLocation attributeSourceLocation;

    if (openTag.getKeyNode() != null) {
      attributeSourceLocation = openTag.getKeyNode().getSourceLocation();
      wrappedFn.addChild(openTag.getKeyNode().getExpr().getRoot().copy(new CopyState()));
    } else {
      attributeSourceLocation = SourceLocation.UNKNOWN;
      FunctionNode funcNode =
          FunctionNode.newPositional(
              Identifier.create(BuiltinFunction.XID.getName(), attributeSourceLocation),
              BuiltinFunction.XID,
              openTag.getSourceLocation());
      funcNode.addChild(
          new StringNode(openTag.getKeyId(), QuoteStyle.SINGLE, attributeSourceLocation));
      funcNode.setType(StringType.getInstance());
      wrappedFn.addChild(funcNode);
    }
    return wrappedFn;
  }

  static void desugarSkipAndKeyNodes(HtmlOpenTagNode openTag, IdGenerator idGenerator) {
    if (openTag.isSkipRoot() || openTag.getKeyNode() != null) {
      // {skip} + {key} nodes are turned into ssk="{$key}". For more information why,
      // see go/typed-html-templates. For Incremental DOM, these are handled in
      // GenIncrementalDomCodeVisitor.
      // Note: when users do not use their own key, the ssk looks like
      // "ssk="{soyServerKey(xid('template'-0))}. When users use their own key, we just
      // use their key verbatim.
      Optional<SkipNode> skipNode =
          openTag.getChildren().stream()
              .filter(c -> c instanceof SkipNode)
              .map(SkipNode.class::cast)
              .findFirst();
      if (skipNode.isPresent()) {
        HtmlAttributeNode skip =
            new HtmlAttributeNode(idGenerator.genId(), SourceLocation.UNKNOWN, null, false);
        skip.addChild(new RawTextNode(idGenerator.genId(), "soy-skip", SourceLocation.UNKNOWN));
        openTag.addChild(skip);
        if (!openTag.getTagName().isTemplateCall()) {
          openTag.removeChild(skipNode.get());
        }
      }
      SourceLocation attributeSourceLocation;
      if (openTag.getKeyNode() != null) {
        attributeSourceLocation = openTag.getKeyNode().getSourceLocation();
      } else {
        attributeSourceLocation = skipNode.get().getSourceLocation();
      }
      HtmlAttributeNode htmlAttributeNode =
          new HtmlAttributeNode(
              idGenerator.genId(),
              attributeSourceLocation,
              SourceLocation.Point.UNKNOWN_POINT,
              false);
      htmlAttributeNode.addChild(
          new RawTextNode(idGenerator.genId(), "ssk", SourceLocation.UNKNOWN));
      HtmlAttributeValueNode value =
          new HtmlAttributeValueNode(idGenerator.genId(), SourceLocation.UNKNOWN, Quotes.SINGLE);
      FunctionNode wrappedFn = extractKeyFunctionFromHtmlTag(openTag, idGenerator);
      PrintNode printNode =
          new PrintNode(
              idGenerator.genId(),
              openTag.getSourceLocation(),
              /* isImplicit= */ true,
              /* expr= */ wrappedFn,
              /* attributes= */ ImmutableList.of(),
              ErrorReporter.exploding());
      printNode.getExpr().setType(wrappedFn.getType());
      value.addChild(printNode);
      htmlAttributeNode.addChild(value);
      openTag.addChild(htmlAttributeNode);
    }
  }
}
