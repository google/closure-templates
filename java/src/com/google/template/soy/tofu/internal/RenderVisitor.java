/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.tofu.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.internal.AugmentedSoyMapData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.tofu.SoyTofuException;
import com.google.template.soy.tofu.internal.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * Visitor for rendering the template subtree rooted at a given SoyNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a template, and the {@code data} passed to the constructor
 * should of course be appropriate for the called template. The rendered output will be appended to
 * the {@code outputSb} provided to the constructor.
 *
 * @author Kai Huang
 */
public class RenderVisitor extends AbstractSoyNodeVisitor<Void> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface RenderVisitorFactory {

    /**
     * @param outputSb The StringBuilder to append the output to.
     * @param baseTofu The Tofu object containing all the compiled Soy functions that may be called.
     *     Should never be null (except in some unit tests).
     * @param data The current template data.
     * @param env The current environment, or null if this is the initial call.
     */
    public RenderVisitor create(
        StringBuilder outputSb, BaseTofu baseTofu, @Nullable SoyMapData data,
        @Nullable Deque<Map<String, SoyData>> env);
  }

  /** Map of all SoyTofuPrintDirectives (name to directive). */
  Map<String, SoyTofuPrintDirective> soyTofuDirectivesMap;

  /** Factory for creating an instance of RenderVisitor. */
  private final RenderVisitorFactory renderVisitorFactory;

  /** The StringBuilder to append the output to. */
  private final StringBuilder outputSb;

  /** The Tofu object containing all the compiled Soy functions that may be called. */
  private final BaseTofu baseTofu;

  /** The current template data. */
  private final SoyMapData data;

  /** The current environment. */
  private final Deque<Map<String, SoyData>> env;

  /** The bundle of translated messages, or null to use the messages from the Soy source. */
  private final SoyMsgBundle msgBundle;

  /** The EvalVisitor for this instance (can reuse since 'data' and 'env' references stay same). */
  // Note: Don't use directly. Call eval() instead.
  private final EvalVisitor evalVisitor;

  /** The 'foreach' list to iterate over (only defined while rendering 'foreach'). */
  private SoyListData foreachList;


  /**
   * @param soyTofuDirectivesMap Map of all SoyTofuPrintDirectives (name to directive).
   * @param evalVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param renderVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param outputSb The StringBuilder to append the output to.
   * @param baseTofu The Tofu object containing all the compiled Soy functions that may be called.
   *     Should never be null (except in some unit tests).
   * @param data The current template data.
   * @param env The current environment, or null if this is the initial call.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   */
  @Inject
  RenderVisitor(
      Map<String, SoyTofuPrintDirective> soyTofuDirectivesMap,
      EvalVisitorFactory evalVisitorFactory, RenderVisitorFactory renderVisitorFactory,
      @Assisted StringBuilder outputSb, @Assisted @Nullable BaseTofu baseTofu,
      @Assisted @Nullable SoyMapData data, @Assisted @Nullable Deque<Map<String, SoyData>> env,
      @Nullable SoyMsgBundle msgBundle) {

    this.soyTofuDirectivesMap = soyTofuDirectivesMap;
    this.renderVisitorFactory = renderVisitorFactory;
    this.outputSb = outputSb;
    this.baseTofu = baseTofu;
    this.data = data;
    this.env = (env != null) ? env : new ArrayDeque<Map<String, SoyData>>();
    this.msgBundle = msgBundle;

    this.evalVisitor = evalVisitorFactory.create(this.data, this.env);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(RawTextNode node) {
    outputSb.append(node.getRawText());
  }


  @Override protected void visitInternal(MsgNode node) {

    env.push(Maps.<String, SoyData>newHashMap());

    long msgId = MsgUtils.computeMsgId(node);
    SoyMsg soyMsg = (msgBundle == null) ? null : msgBundle.getMsg(msgId);
    if (soyMsg != null) {
      // Case 1: Localized message is provided by the msgBundle.
      for (SoyMsgPart msgPart : soyMsg.getParts()) {

        if (msgPart instanceof SoyMsgRawTextPart) {
          outputSb.append(((SoyMsgRawTextPart) msgPart).getRawText());

        } else if (msgPart instanceof SoyMsgPlaceholderPart) {
          String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
          visit(node.getPlaceholderNode(placeholderName));

        } else {
          throw new AssertionError();
        }
      }

    } else {
      // Case 2: No msgBundle or message not found. Just use the message from the Soy source.
      visitChildren(node);
    }

    env.pop();
  }


  @Override protected void visitInternal(MsgHtmlTagNode node) {
    // Note: We don't default to the ParentSoyNode implementation because we don't need to add
    // another frame to the environment.
    visitChildren(node);
  }


  @Override protected void visitInternal(PrintNode node) {

    SoyData resultValue = eval(node.getExpr());
    if (resultValue instanceof UndefinedData) {
      throw new SoyTofuException(
          "In 'print' tag, expression \"" + node.getExprText() + "\" evaluates to undefined.");
    }
    String result = resultValue.toString();

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyTofuPrintDirective directive = soyTofuDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        throw new SoyTofuException(
            "Failed to find SoyTofuPrintDirective with name '" + directiveNode.getName() + "'" +
            " (tag " + node.toSourceString() +")");
      }

      // Get directive args.
      List<ExprRootNode<ExprNode>> argsExprs = directiveNode.getArgs();
      if (! directive.getValidArgsSizes().contains(argsExprs.size())) {
        throw new SoyTofuException(
            "Print directive '" + directiveNode.getName() + "' used with the wrong number of" +
            " arguments (tag " + node.toSourceString() + ").");
      }

      // Evaluate directive args.
      List<SoyData> argsSoyDatas = Lists.newArrayListWithCapacity(argsExprs.size());
      for (ExprRootNode<ExprNode> argExpr : argsExprs) {
        argsSoyDatas.add(evalVisitor.exec(argExpr));
      }

      // Apply directive.
      result = directive.applyForTofu(result, argsSoyDatas);
    }

    outputSb.append(result);
  }


  @Override protected void visitInternal(IfNode node) {

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        if (eval(icn.getExpr()).toBoolean()) {
          visit(icn);
          return;
        }

      } else if (child instanceof IfElseNode) {
        visit((IfElseNode) child);
        return;

      } else {
        throw new AssertionError();
      }
    }
  }


  @Override protected void visitInternal(SwitchNode node) {

    SoyData switchValue = eval(node.getExpr());

    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;
        for (ExprNode caseExpr : scn.getExprList()) {
          if (switchValue.equals(eval(caseExpr))) {
            visit(scn);
            return;
          }
        }

      } else if (child instanceof SwitchDefaultNode) {
        visit((SwitchDefaultNode) child);
        return;

      } else {
        throw new AssertionError();
      }
    }
  }


  @Override protected void visitInternal(ForeachNode node) {

    SoyData dataRefValue = eval(node.getDataRef());
    if (!(dataRefValue instanceof SoyListData)) {
      throw new SoyTofuException(
          "In 'foreach' command " + node.toSourceString() +
          ", the data reference does not resolve to a SoyListData.");
    }
    foreachList = (SoyListData) dataRefValue;

    if (foreachList.length() > 0) {
      // Case 1: Nonempty list.
      visit((ForeachNonemptyNode) node.getChild(0));

    } else {
      // Case 2: Empty list. If the 'ifempty' node exists, visit it.
      if (node.numChildren() == 2) {
        visit((ForeachIfemptyNode) node.getChild(1));
      }
    }
  }


  @Override protected void visitInternal(ForeachNonemptyNode node) {

    // Important: Save the value of foreachList to a local variable because the field might be
    // reused when this node's subtree is visited (i.e. if there are other 'foreach' loops nested
    // within this one).
    SoyListData foreachList = this.foreachList;
    this.foreachList = null;

    String varName = node.getVarName();
    Map<String, SoyData> newEnvFrame = Maps.newHashMap();
    // Note: No need to save firstIndex as it's always 0.
    newEnvFrame.put(varName + "__lastIndex", new IntegerData(foreachList.length() - 1));
    env.push(newEnvFrame);

    for (int i = 0; i < foreachList.length(); ++i) {
      newEnvFrame.put(varName + "__index", new IntegerData(i));
      newEnvFrame.put(varName, foreachList.get(i));
      visitChildren(node);
    }

    env.pop();
  }


  @Override protected void visitInternal(ForNode node) {

    List<Integer> rangeArgValues = Lists.newArrayList();

    for (ExprNode rangeArg : node.getRangeArgs()) {
      SoyData rangeArgValue = eval(rangeArg);
      if (!(rangeArgValue instanceof IntegerData)) {
        throw new SoyTofuException(
            "In 'for' command " + node.toSourceString() + ", the expression \"" +
            rangeArg.toSourceString() + "\" does not resolve to an integer.");
      }
      rangeArgValues.add(((IntegerData) rangeArgValue).getValue());
    }

    int increment = (rangeArgValues.size() == 3) ? rangeArgValues.remove(2) : 1 /* default */;
    int init = (rangeArgValues.size() == 2) ? rangeArgValues.remove(0) : 0 /* default */;
    int limit = rangeArgValues.get(0);

    String localVarName = node.getLocalVarName();
    Map<String, SoyData> newEnvFrame = Maps.newHashMap();
    env.push(newEnvFrame);

    for (int i = init; i < limit; i += increment) {
      newEnvFrame.put(localVarName, new IntegerData(i));
      visitChildren(node);
    }

    env.pop();
  }


  @Override protected void visitInternal(CallNode node) {

    SoyMapData dataToPass;
    if (node.isPassingAllData()) {
      dataToPass = data;
    } else if (node.isPassingData()) {
      SoyData dataRefValue = eval(node.getDataRef());
      if (!(dataRefValue instanceof SoyMapData)) {
        throw new SoyTofuException(
            "In 'call' command " + node.toSourceString() +
            ", the data reference does not resolve to a SoyMapData.");
      }
      dataToPass = (SoyMapData) dataRefValue;
    } else {
      dataToPass = null;
    }

    SoyMapData callData;
    if (!node.isPassingData()) {
      // Case 1: Not passing data. Start with a fresh SoyMapData object.
      callData = new SoyMapData();
    } else if (node.numChildren() == 0) {
      // Case 2: No params. Just pass in the current data.
      callData = dataToPass;
    } else {
      // Case 3: Passing data and adding params. Need to augment the current data.
      callData = new AugmentedSoyMapData(dataToPass);
    }

    for (CallParamNode child : node.getChildren()) {

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        SoyData value = eval(cpvn.getValueExpr());
        callData.put(cpvn.getKey(), value);

      } else if (child instanceof CallParamContentNode) {
        CallParamContentNode cpcn = (CallParamContentNode) child;
        StringBuilder cpcnOutput = new StringBuilder();
        RenderVisitor rv = renderVisitorFactory.create(cpcnOutput, baseTofu, data, env);
        rv.visit(cpcn);
        callData.put(cpcn.getKey(), cpcnOutput.toString());

      } else {
        throw new AssertionError();
      }
    }

    baseTofu.renderInternal(outputSb, node.getCalleeName(), callData);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    env.push(Maps.<String, SoyData>newHashMap());
    visitChildren(node);
    env.pop();
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private SoyData eval(ExprNode expr) {

    try {
      return evalVisitor.exec(expr);
    } catch (Exception e) {
      throw new SoyTofuException(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage());
    }
  }

}
