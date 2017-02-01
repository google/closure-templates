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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.msgs.internal.IcuSyntaxUtils;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
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
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class to generate python code for one {@link MsgNode}.
 *
 */
public final class MsgFuncGenerator {

  /** Factory for assisted injection */
  public static interface MsgFuncGeneratorFactory {
    MsgFuncGenerator create(
        MsgNode node, LocalVariableStack localVarExprs, ErrorReporter errorReporter);
  }

  /** The msg node to generate the function calls from. */
  private final MsgNode msgNode;

  /** The generated msg id with the same algorithm for translation service. */
  private final long msgId;

  private final ImmutableList<SoyMsgPart> msgParts;

  private final GenPyExprsVisitor genPyExprsVisitor;

  /** The function builder for the prepare_*() method */
  private final PyFunctionExprBuilder prepareFunc;

  /** The function builder for the render_*() method */
  private final PyFunctionExprBuilder renderFunc;

  private final TranslateToPyExprVisitor translateToPyExprVisitor;

  @AssistedInject
  MsgFuncGenerator(
      GenPyExprsVisitorFactory genPyExprsVisitorFactory,
      @Assisted MsgNode msgNode,
      @Assisted LocalVariableStack localVarExprs,
      @Assisted ErrorReporter errorReporter) {
    this.msgNode = msgNode;
    this.genPyExprsVisitor = genPyExprsVisitorFactory.create(localVarExprs, errorReporter);
    this.translateToPyExprVisitor = new TranslateToPyExprVisitor(localVarExprs, errorReporter);
    String translator = PyExprUtils.TRANSLATOR_NAME;

    if (this.msgNode.isPlrselMsg()) {
      if (this.msgNode.isPluralMsg()) {
        this.prepareFunc = new PyFunctionExprBuilder(translator + ".prepare_plural");
        this.renderFunc = new PyFunctionExprBuilder(translator + ".render_plural");
      } else {
        this.prepareFunc = new PyFunctionExprBuilder(translator + ".prepare_icu");
        this.renderFunc = new PyFunctionExprBuilder(translator + ".render_icu");
      }
    } else if (this.msgNode.isRawTextMsg()) {
      this.prepareFunc = new PyFunctionExprBuilder(translator + ".prepare_literal");
      this.renderFunc = new PyFunctionExprBuilder(translator + ".render_literal");
    } else {
      this.prepareFunc = new PyFunctionExprBuilder(translator + ".prepare");
      this.renderFunc = new PyFunctionExprBuilder(translator + ".render");
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
    if (this.msgNode.isPlrselMsg()) {
      return this.msgNode.isPluralMsg() ? pyFuncForPluralMsg() : pyFuncForSelectMsg();
    } else {
      return this.msgNode.isRawTextMsg() ? pyFuncForRawTextMsg() : pyFuncForGeneralMsg();
    }
  }

  private PyStringExpr pyFuncForRawTextMsg() {
    String pyMsgText = processMsgPartsHelper(msgParts, escaperForPyFormatString);

    prepareFunc.addArg(msgId).addArg(pyMsgText);
    return renderFunc.addArg(prepareFunc.asPyExpr()).asPyStringExpr();
  }

  private PyStringExpr pyFuncForGeneralMsg() {
    String pyMsgText = processMsgPartsHelper(msgParts, escaperForPyFormatString);
    Map<PyExpr, PyExpr> nodePyVarToPyExprMap = collectVarNameListAndToPyExprMap();

    prepareFunc
        .addArg(msgId)
        .addArg(pyMsgText)
        .addArg(PyExprUtils.convertIterableToPyTupleExpr(nodePyVarToPyExprMap.keySet()));

    return renderFunc
        .addArg(prepareFunc.asPyExpr())
        .addArg(PyExprUtils.convertMapToPyExpr(nodePyVarToPyExprMap))
        .asPyStringExpr();
  }

  private PyStringExpr pyFuncForPluralMsg() {
    SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) msgParts.get(0);
    MsgPluralNode pluralNode = msgNode.getRepPluralNode(pluralPart.getPluralVarName());
    Map<PyExpr, PyExpr> nodePyVarToPyExprMap = collectVarNameListAndToPyExprMap();
    Map<PyExpr, PyExpr> caseSpecStrToMsgTexts = new LinkedHashMap<>();

    for (Case<SoyMsgPluralCaseSpec> pluralCase : pluralPart.getCases()) {
      caseSpecStrToMsgTexts.put(
          new PyStringExpr("'" + pluralCase.spec() + "'"),
          new PyStringExpr("'" + processMsgPartsHelper(pluralCase.parts(), nullEscaper) + "'"));
    }

    prepareFunc
        .addArg(msgId)
        .addArg(PyExprUtils.convertMapToPyExpr(caseSpecStrToMsgTexts))
        .addArg(PyExprUtils.convertIterableToPyTupleExpr(nodePyVarToPyExprMap.keySet()));

    // Translates {@link MsgPluralNode#pluralExpr} into a Python lookup expression.
    // Note that pluralExpr represent the Soy expression inside the attributes of a plural tag.
    PyExpr pluralPyExpr = translateToPyExprVisitor.exec(pluralNode.getExpr());

    return renderFunc
        .addArg(prepareFunc.asPyExpr())
        .addArg(pluralPyExpr)
        .addArg(PyExprUtils.convertMapToPyExpr(nodePyVarToPyExprMap))
        .asPyStringExpr();
  }

  private PyStringExpr pyFuncForSelectMsg() {
    Map<PyExpr, PyExpr> nodePyVarToPyExprMap = collectVarNameListAndToPyExprMap();

    ImmutableList<SoyMsgPart> msgPartsInIcuSyntax =
        IcuSyntaxUtils.convertMsgPartsToEmbeddedIcuSyntax(msgParts, true);
    String pyMsgText = processMsgPartsHelper(msgPartsInIcuSyntax, nullEscaper);

    prepareFunc
        .addArg(msgId)
        .addArg(pyMsgText)
        .addArg(PyExprUtils.convertIterableToPyTupleExpr(nodePyVarToPyExprMap.keySet()));

    return renderFunc
        .addArg(prepareFunc.asPyExpr())
        .addArg(PyExprUtils.convertMapToPyExpr(nodePyVarToPyExprMap))
        .asPyStringExpr();
  }

  /**
   * Private helper to process and collect all variables used within this msg node for code
   * generation.
   *
   * @return A Map populated with all the variables used with in this message node, using {@link
   *     MsgPlaceholderInitialNode#genBasePhName}.
   */
  private Map<PyExpr, PyExpr> collectVarNameListAndToPyExprMap() {
    Map<PyExpr, PyExpr> nodePyVarToPyExprMap = new LinkedHashMap<>();
    for (Map.Entry<String, MsgSubstUnitNode> entry : msgNode.getVarNameToRepNodeMap().entrySet()) {
      MsgSubstUnitNode substUnitNode = entry.getValue();
      PyExpr substPyExpr = null;

      if (substUnitNode instanceof MsgPlaceholderNode) {
        SoyNode phInitialNode = ((AbstractParentSoyNode<?>) substUnitNode).getChild(0);

        if (phInitialNode instanceof PrintNode
            || phInitialNode instanceof CallNode
            || phInitialNode instanceof RawTextNode) {
          substPyExpr =
              PyExprUtils.concatPyExprs(genPyExprsVisitor.exec(phInitialNode)).toPyString();
        }

        // when the placeholder is generated by HTML tags
        if (phInitialNode instanceof MsgHtmlTagNode) {
          substPyExpr =
              PyExprUtils.concatPyExprs(
                      genPyExprsVisitor.execOnChildren((ParentSoyNode<?>) phInitialNode))
                  .toPyString();
        }
      } else if (substUnitNode instanceof MsgPluralNode) {
        // Translates {@link MsgPluralNode#pluralExpr} into a Python lookup expression.
        // Note that {@code pluralExpr} represents the soy expression of the {@code plural} attr,
        // i.e. the {@code $numDrafts} in {@code {plural $numDrafts}...{/plural}}.
        substPyExpr = translateToPyExprVisitor.exec(((MsgPluralNode) substUnitNode).getExpr());
      } else if (substUnitNode instanceof MsgSelectNode) {
        substPyExpr = translateToPyExprVisitor.exec(((MsgSelectNode) substUnitNode).getExpr());
      }

      if (substPyExpr != null) {
        nodePyVarToPyExprMap.put(new PyStringExpr("'" + entry.getKey() + "'"), substPyExpr);
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
   * <p>For {@link SoyMsgRawTextPart}, it appends the raw text and applies necessary escaping; For
   * {@link SoyMsgPlaceholderPart}, it turns the placeholder's variable name into Python replace
   * format.
   *
   * @param parts The SoyMsgPart parts to convert.
   * @param escaper A Function which provides escaping for raw text.
   * @return A String representing all the {@code parts} in Python.
   */
  private static String processMsgPartsHelper(
      ImmutableList<SoyMsgPart> parts, Function<String, String> escaper) {
    StringBuilder rawMsgTextSb = new StringBuilder();
    for (SoyMsgPart part : parts) {
      if (part instanceof SoyMsgRawTextPart) {
        rawMsgTextSb.append(escaper.apply(((SoyMsgRawTextPart) part).getRawText()));
      }

      if (part instanceof SoyMsgPlaceholderPart) {
        String phName = ((SoyMsgPlaceholderPart) part).getPlaceholderName();
        rawMsgTextSb.append("{" + phName + "}");
      }
    }
    return rawMsgTextSb.toString();
  }

  /**
   * A mapper to apply escaping for python format string.
   *
   * <p>It escapes '{' and '}' to '{{' and '}}' in the String.
   *
   * @see "https://docs.python.org/2/library/string.html#formatstrings"
   */
  private static final Function<String, String> escaperForPyFormatString =
      new Function<String, String>() {
        @Override
        public String apply(String str) {
          return str.replaceAll("\\{", "{{").replaceAll("\\}", "}}").replace("'", "\\\'");
        }
      };

  /** A mapper which does nothing. */
  private static final Function<String, String> nullEscaper =
      new Function<String, String>() {
        @Override
        public String apply(String str) {
          return str;
        }
      };
}
