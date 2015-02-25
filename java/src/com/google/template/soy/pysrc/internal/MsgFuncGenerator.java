/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.soytree.AbstractParentSoyNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderInitialNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to generate python code for one {@link MsgNode}.
 *
 */
final class MsgFuncGenerator {

  static interface MsgFuncGeneratorFactory {
    public MsgFuncGenerator create(MsgNode node, LocalVariableStack localVarExprs);
  }

  /** The msg node to generate the function calls from. */
  private final MsgNode msgNode;

  /** The generated msg id with the same algorithm for translation service. */
  private final long msgId;

  private final ImmutableList<SoyMsgPart> msgParts;

  private final GenPyExprsVisitor genPyExprsVisitor;

  /** The function builder for the prepare_*() method **/
  private final PyFunctionExprBuilder prepareFunc;

  /** The function builder for the render_*() method **/
  private final PyFunctionExprBuilder renderFunc;


  @AssistedInject
  MsgFuncGenerator(GenPyExprsVisitorFactory genPyExprsVisitorFactory, @Assisted MsgNode msgNode,
      @Assisted LocalVariableStack localVarExprs) {
    this.msgNode = msgNode;
    this.genPyExprsVisitor = genPyExprsVisitorFactory.create(localVarExprs);

    if (this.msgNode.isRawTextMsg()) {
      this.prepareFunc = new PyFunctionExprBuilder("prepare_literal");
      this.renderFunc = new PyFunctionExprBuilder("render_literal");
    } else {
      this.prepareFunc = new PyFunctionExprBuilder("prepare");
      this.renderFunc = new PyFunctionExprBuilder("render");
    }

    MsgPartsAndIds msgPartsAndIds = MsgUtils.buildMsgPartsAndComputeMsgIdForDualFormat(msgNode);
    Preconditions.checkNotNull(msgPartsAndIds);

    this.msgId = msgPartsAndIds.id;
    this.msgParts = msgPartsAndIds.parts;

    Preconditions.checkState(!msgParts.isEmpty());
  }

  /**
   * Return the PyStringExpr for the render function call, because we know render always return a
   * string in Python runtime.
   */

  PyStringExpr getPyExpr() {
    addMsgAttributesToPrepare();
    if (this.msgNode.isRawTextMsg()) {
      return pyFuncForRawTextMsg();
    } else {
      return pyFuncForGeneralMsg();
    }
  }

  private PyStringExpr pyFuncForRawTextMsg() {
    SoyMsgRawTextPart rawTextPart = (SoyMsgRawTextPart) msgParts.get(0);

    prepareFunc.addArg(msgId)
        .addArg(rawTextPart.getRawText());
    return renderFunc.addArg(prepareFunc.asPyExpr())
        .asPyStringExpr();
  }

  private PyStringExpr pyFuncForGeneralMsg() {
    String pyMsgText = processMsgPartsHelper(msgParts);
    Map<PyExpr, PyExpr> nodePyVarToPyExprMap = collectVarNameListAndToPyExprMap();

    prepareFunc.addArg(msgId)
        .addArg(pyMsgText)
        .addArg(PyExprUtils.convertIterableToPyTupleExpr(nodePyVarToPyExprMap.keySet()));

    return renderFunc.addArg(prepareFunc.asPyExpr())
        .addArg(PyExprUtils.convertMapToPyExpr(nodePyVarToPyExprMap))
        .asPyStringExpr();
  }

  private void addMsgAttributesToPrepare() {
    if (this.msgNode.getMeaning() != null) {
      prepareFunc.addKwarg("meaning", this.msgNode.getMeaning());
    }

    if (this.msgNode.getDesc() != null) {
      prepareFunc.addKwarg("desc", this.msgNode.getDesc());
    }
  }

  /**
   * Private helper to process and collect all variables used within this msg node for code
   * generation.
   *
   * @return A Map populated with all the variables used with in this message node, using
   *         {@link MsgPlaceholderInitialNode#genBasePhName}.
   */
  private Map<PyExpr, PyExpr> collectVarNameListAndToPyExprMap() {
    Map<PyExpr, PyExpr> nodePyVarToPyExprMap = new HashMap<>();
    for (Map.Entry<String, MsgSubstUnitNode> entry : msgNode.getVarNameToRepNodeMap().entrySet()) {
      String phName = entry.getKey();
      MsgSubstUnitNode substUnitNode = entry.getValue();
      PyExpr substPyExpr = null;

      if (substUnitNode instanceof MsgPlaceholderNode) {
        MsgPlaceholderInitialNode phInitialNode =
            (MsgPlaceholderInitialNode) ((AbstractParentSoyNode<?>) substUnitNode).getChild(0);

        if (phInitialNode instanceof PrintNode || phInitialNode instanceof CallNode) {
          substPyExpr = PyExprUtils.concatPyExprs(genPyExprsVisitor.exec(phInitialNode));
        }

        // when the placeholder is generated by HTML tags
        if (phInitialNode instanceof MsgHtmlTagNode) {
          substPyExpr = PyExprUtils.concatPyExprs(
              genPyExprsVisitor.execOnChildren((ParentSoyNode<?>) phInitialNode));
        }
      }

      if  (substPyExpr != null) {
        nodePyVarToPyExprMap.put(new PyStringExpr("'" + phName + "'"), substPyExpr.toPyString());
      }
    }

    return nodePyVarToPyExprMap;
  }

  /**
   * Private helper to build valid Python string for a list of {@link SoyMsgPart}s.
   *
   * <p>It only processes {@link SoyMsgRawTextPart} and {@link SoyMsgPlaceholderPart} and ignores
   * others, because we didn't generate a direct string for plural and select nodes.
   *
   * <p>For {@link SoyMsgRawTextPart}, it appends the raw text; For {@link SoyMsgPlaceholderPart},
   * it turns the placeholder's variable name into Python replace format.
   *
   * @param parts The SoyMsgPart parts to convert.
   *
   * @return A String representing all the {@code parts} in Python.
   */
  private static String processMsgPartsHelper(ImmutableList<SoyMsgPart> parts) {
    StringBuilder rawMsgTextSb = new StringBuilder();
    for (SoyMsgPart part : parts) {
      if (part instanceof SoyMsgRawTextPart) {
        rawMsgTextSb.append(((SoyMsgRawTextPart) part).getRawText());
      }

      if (part instanceof SoyMsgPlaceholderPart) {
        String phName = ((SoyMsgPlaceholderPart) part).getPlaceholderName();
        rawMsgTextSb.append("{" + phName + "}");
      }

    }
    return rawMsgTextSb.toString();
  }
}
