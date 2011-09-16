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

package com.google.template.soy.sharedpasses.render;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.internal.AugmentedSoyMapData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaRuntimePrintDirective;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgPluralRemainderNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateConflictException;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import com.ibm.icu.text.PluralRules;
import com.ibm.icu.util.ULocale;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Visitor for rendering the template subtree rooted at a given SoyNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> The rendered output will be appended to the {@code outputSb} provided to the constructor.
 *
 * @author Kai Huang
 */
public class RenderVisitor extends AbstractSoyNodeVisitor<Void> {


  /**
   * Interface for a factory that creates an RenderVisitor.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static interface RenderVisitorFactory {

    /**
     * Creates a RenderVisitor.
     * @param outputSb The StringBuilder to append the output to.
     * @param templateRegistry A registry of all templates.
     * @param data The current template data.
     * @param ijData The current injected data.
     * @param env The current environment, or null if this is the initial call.
     * @param activeDelPackageNames The set of active delegate package names. Allowed to be null
     *     when known to be irrelevant, i.e. when not using delegates feature.
     * @param msgBundle The bundle of translated messages, or null to use the messages from the
     *     Soy source.
     * @param cssRenamingMap The CSS renaming map, or null if not applicable.
     * @return The newly created RenderVisitor instance.
     */
    public RenderVisitor create(
        StringBuilder outputSb, TemplateRegistry templateRegistry,
        @Nullable SoyMapData data, @Nullable SoyMapData ijData,
        @Nullable Deque<Map<String, SoyData>> env, @Nullable Set<String> activeDelPackageNames,
        @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap);
  }


  /** Map of all SoyJavaRuntimePrintDirectives (name to directive). */
  private final Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap;

  /** Factory for creating an instance of EvalVisitor. */
  private final EvalVisitorFactory evalVisitorFactory;

  /** Factory for creating an instance of RenderVisitor. */
  private final RenderVisitorFactory renderVisitorFactory;

  /** The StringBuilder to append the output to. */
  private final StringBuilder outputSb;

  /** The bundle containing all the templates that may be rendered. */
  private final TemplateRegistry templateRegistry;

  /** The current template data. */
  private final SoyMapData data;

  /** The current injected data. */
  private final SoyMapData ijData;

  /** The current environment. */
  private final Deque<Map<String, SoyData>> env;

  /** The set of active delegate package names. */
  private final Set<String> activeDelPackageNames;

  /** The bundle of translated messages, or null to use the messages from the Soy source. */
  private final SoyMsgBundle msgBundle;

  /** CSS renaming map. */
  private final SoyCssRenamingMap cssRenamingMap;

  /** The EvalVisitor for this instance (can reuse since 'data' and 'env' references stay same). */
  // Note: Don't use directly. Call eval() instead.
  private EvalVisitor evalVisitor;

  /** The 'foreach' list to iterate over (only defined while rendering 'foreach'). */
  private SoyListData foreachList;

  /** Holds the value of the remainder for the current enclosing plural node. */
  private int currPluralRemainderValue;


  /**
   * @param soyJavaRuntimeDirectivesMap Map of all SoyJavaRuntimePrintDirectives (name to
   *     directive). Can be null if the subclass that is calling this constructor plans to override
   *     the default implementation of {@code applyDirective()}.
   * @param evalVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param renderVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param outputSb The StringBuilder to append the output to.
   * @param templateRegistry A registry of all templates. Should never be null (except in some unit
   *     tests).
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param env The current environment, or null if this is the initial call.
   * @param activeDelPackageNames The set of active delegate package names. Allowed to be null when
   *     known to be irrelevant.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   */
  protected RenderVisitor(
      @Nullable Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap,
      EvalVisitorFactory evalVisitorFactory, RenderVisitorFactory renderVisitorFactory,
      StringBuilder outputSb, @Nullable TemplateRegistry templateRegistry,
      @Nullable SoyMapData data, @Nullable SoyMapData ijData,
      @Nullable Deque<Map<String, SoyData>> env, @Nullable Set<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap) {

    this.soyJavaRuntimeDirectivesMap = soyJavaRuntimeDirectivesMap;
    this.evalVisitorFactory = evalVisitorFactory;
    this.renderVisitorFactory = renderVisitorFactory;
    this.outputSb = outputSb;
    this.templateRegistry = templateRegistry;
    this.data = data;
    this.ijData = ijData;
    this.env = (env != null) ? env : new ArrayDeque<Map<String, SoyData>>();
    this.activeDelPackageNames = activeDelPackageNames;
    this.msgBundle = msgBundle;
    this.cssRenamingMap = cssRenamingMap;

    this.evalVisitor = null;  // lazily initialized
    this.currPluralRemainderValue = -1;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitTemplateNode(TemplateNode node) {
    try {
      visitBlockHelper(node);

    } catch (RenderException re) {
      throw (re.getTemplateName() != null) ? re : re.setTemplateName(node.getTemplateName());
    }
  }


  @Override protected void visitRawTextNode(RawTextNode node) {
    outputSb.append(node.getRawText());
  }


  @Override protected void visitMsgNode(MsgNode node) {

    boolean doAddEnvFrame = node.needsEnvFrameDuringInterp() != Boolean.FALSE /*true or unknown*/;
    if (doAddEnvFrame) {
      env.push(Maps.<String, SoyData>newHashMap());
    }

    SoyMsg soyMsg;
    if (msgBundle != null) {
      long msgId = MsgUtils.computeMsgId(node);
      soyMsg = msgBundle.getMsg(msgId);
    } else {
      soyMsg = null;
    }

    if (soyMsg != null) {
      // Case 1: Localized message is provided by the msgBundle.

      List<SoyMsgPart> msgParts = soyMsg.getParts();

      if (msgParts.size() > 0) {
        SoyMsgPart firstPart = msgParts.get(0);

        if (firstPart instanceof SoyMsgPluralPart) {
          new PluralSelectMsgPartsVisitor(node, new ULocale(soyMsg.getLocaleString()))
              .visitPart((SoyMsgPluralPart) firstPart);

        } else if (firstPart instanceof SoyMsgSelectPart) {
          new PluralSelectMsgPartsVisitor(node, new ULocale(soyMsg.getLocaleString()))
              .visitPart((SoyMsgSelectPart) firstPart);

        } else {
          for (SoyMsgPart msgPart : msgParts) {

            if (msgPart instanceof SoyMsgRawTextPart) {
              outputSb.append(((SoyMsgRawTextPart) msgPart).getRawText());

            } else if (msgPart instanceof SoyMsgPlaceholderPart) {
              String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
              visit(node.getRepPlaceholderNode(placeholderName));

            } else {
              throw new AssertionError();
            }
          }

        }
      }

    } else {
      // Case 2: No msgBundle or message not found. Just use the message from the Soy source.
      visitChildren(node);
    }

    if (doAddEnvFrame) {
      env.pop();
    }
  }


  @Override protected void visitMsgPluralNode(MsgPluralNode node) {
    ExprRootNode<?> pluralExpr = node.getExpr();
    int pluralValue;
    try {
      pluralValue = eval(pluralExpr).integerValue();
    } catch (SoyDataException e) {
      throw new RenderException(
            String.format("Plural expression \"%s\" doesn't evaluate to integer.",
                pluralExpr.toSourceString()));
    }

    currPluralRemainderValue = pluralValue - node.getOffset();

    // Check each case.
    for (CaseOrDefaultNode child : node.getChildren()) {
      if (child instanceof MsgPluralDefaultNode) {
        // This means it didn't match any other case.
        visitChildren(child);
        break;

      } else {
        if (((MsgPluralCaseNode) child).getCaseNumber() == pluralValue) {
          visitChildren(child);
          break;

        }
      }
    }

    currPluralRemainderValue = -1;
  }


  @Override protected void visitMsgPluralCaseNode(MsgPluralCaseNode node) {
    throw new AssertionError();
  }


  @Override protected void visitMsgPluralDefaultNode(MsgPluralDefaultNode node) {
    throw new AssertionError();
  }


  @Override protected void visitMsgPluralRemainderNode(MsgPluralRemainderNode node) {
    outputSb.append(currPluralRemainderValue);
  }


  @Override protected void visitMsgSelectNode(MsgSelectNode node) {
    ExprRootNode<?> selectExpr = node.getExpr();
    String selectValue;
    try {
      selectValue = eval(selectExpr).stringValue();
    } catch (SoyDataException e) {
      throw new RenderException(
          String.format("Select expression \"%s\" doesn't evaluate to string.",
                        selectExpr.toSourceString()));
    }

    // Check each case.
    for (CaseOrDefaultNode child : node.getChildren()) {
      if (child instanceof MsgSelectDefaultNode) {
        // This means it didn't match any other case.
        visitChildren(child);

      } else {
        if (((MsgSelectCaseNode) child).getCaseValue().equals(selectValue)) {
          visitChildren(child);
          return;

        }
      }
    }
  }


  @Override protected void visitMsgSelectCaseNode(MsgSelectCaseNode node) {
    throw new AssertionError();
  }


  @Override protected void visitMsgSelectDefaultNode(MsgSelectDefaultNode node) {
    throw new AssertionError();
  }


  @Override protected void visitGoogMsgNode(GoogMsgNode node) {
    throw new AssertionError();
  }


  @Override protected void visitGoogMsgRefNode(GoogMsgRefNode node) {
    throw new AssertionError();
  }


  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    // Note: We don't default to the fallback implementation because we don't need to add
    // another frame to the environment.
    visitChildren(node);
  }


  @Override protected void visitPrintNode(PrintNode node) {

    SoyData result = eval(node.getExprUnion().getExpr());
    if (result instanceof UndefinedData) {
      throw new RenderException(
          "In 'print' tag, expression \"" + node.getExprText() + "\" evaluates to undefined.");
    }

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Evaluate directive args.
      List<ExprRootNode<?>> argsExprs = directiveNode.getArgs();
      List<SoyData> argsSoyDatas = Lists.newArrayListWithCapacity(argsExprs.size());
      for (ExprRootNode<?> argExpr : argsExprs) {
        argsSoyDatas.add(evalVisitor.exec(argExpr));
      }

      // Apply directive.
      String directiveResult = applyDirective(directiveNode.getName(), result, argsSoyDatas, node);
      result = StringData.forValue(directiveResult);
    }

    outputSb.append(result);
  }


  /**
   * Protected helper for visitPrintNode() to apply a directive.
   *
   * <p> This default implementation can be overridden by subclasses (such as TofuRenderVisitor)
   * that have access to a potentially larger set of print directives.
   *
   * @param directiveName The name of the directive.
   * @param value The value to apply the directive on.
   * @param args The arguments to the directive.
   * @param printNode The containing PrintNode. Only used for error reporting.
   * @return The result of applying the directive with the given arguments to the given value.
   */
  protected String applyDirective(
      String directiveName, SoyData value, List<SoyData> args, PrintNode printNode) {

    // Get directive.
    SoyJavaRuntimePrintDirective directive = soyJavaRuntimeDirectivesMap.get(directiveName);
    if (directive == null) {
      throw new RenderException(
          "Failed to find Soy print directive with name '" + directiveName + "'" +
          " (tag " + printNode.toSourceString() + ")");
    }

    // TODO: Add a pass to check num args at compile time.
    if (! directive.getValidArgsSizes().contains(args.size())) {
      throw new RenderException(
          "Print directive '" + directiveName + "' used with the wrong number of" +
          " arguments (tag " + printNode.toSourceString() + ").");
    }

    return directive.apply(value, args);
  }


  @Override protected void visitCssNode(CssNode node) {
    ExprRootNode<?> componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      outputSb.append(eval(componentNameExpr).toString()).append("-");
    }
    //
    // CSS statements are of the form {css selector} or {css $component, selector}.
    // We only rename the selector text. The component must derive from a previous
    // css expression and thus is already renamed.
    //
    // For example, in Javascript calling Soy:
    //   Js: var base = goog.getCssName('goog-custom-button');
    //   Soy: {css $base, hover}
    //
    // In a Soy template:
    //   {call .helper}
    //     {param base}{css goog-custom-button}{/param}
    //   {/call}
    //
    //   {template .helper}
    //     {css $base, hover}
    //   {/template}
    //
    String selectorText = node.getSelectorText();
    if (cssRenamingMap != null) {
      String mappedText = cssRenamingMap.get(selectorText);
      if (mappedText != null) {
        selectorText = mappedText;
      }
    }
    outputSb.append(selectorText);
  }


  @Override protected void visitLetValueNode(LetValueNode node) {
    env.peek().put(node.getVarName(), eval(node.getValueExpr()));
  }


  @Override protected void visitLetContentNode(LetContentNode node) {
    env.peek().put(node.getVarName(), renderChildren(node));
  }


  @Override protected void visitIfNode(IfNode node) {

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        if (eval(icn.getExprUnion().getExpr()).toBoolean()) {
          visit(icn);
          return;
        }

      } else if (child instanceof IfElseNode) {
        visit(child);
        return;

      } else {
        throw new AssertionError();
      }
    }
  }


  @Override protected void visitSwitchNode(SwitchNode node) {

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
        visit(child);
        return;

      } else {
        throw new AssertionError();
      }
    }
  }


  @Override protected void visitForeachNode(ForeachNode node) {

    SoyData dataRefValue = eval(node.getExpr());
    if (!(dataRefValue instanceof SoyListData)) {
      throw new RenderException(
          "In 'foreach' command " + node.toSourceString() +
          ", the data reference does not resolve to a SoyListData.");
    }
    foreachList = (SoyListData) dataRefValue;

    if (foreachList.length() > 0) {
      // Case 1: Nonempty list.
      visit(node.getChild(0));

    } else {
      // Case 2: Empty list. If the 'ifempty' node exists, visit it.
      if (node.numChildren() == 2) {
        visit(node.getChild(1));
      }
    }
  }


  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {

    // Important: Save the value of foreachList to a local variable because the field might be
    // reused when this node's subtree is visited (i.e. if there are other 'foreach' loops nested
    // within this one).
    SoyListData foreachList = this.foreachList;
    this.foreachList = null;

    String varName = node.getVarName();
    Map<String, SoyData> newEnvFrame = Maps.newHashMap();
    // Note: No need to save firstIndex as it's always 0.
    newEnvFrame.put(varName + "__lastIndex", IntegerData.forValue(foreachList.length() - 1));
    env.push(newEnvFrame);

    for (int i = 0; i < foreachList.length(); ++i) {
      newEnvFrame.put(varName + "__index", IntegerData.forValue(i));
      newEnvFrame.put(varName, foreachList.get(i));
      visitChildren(node);
    }

    env.pop();
  }


  @Override protected void visitForNode(ForNode node) {

    List<Integer> rangeArgValues = Lists.newArrayList();

    for (ExprNode rangeArg : node.getRangeArgs()) {
      SoyData rangeArgValue = eval(rangeArg);
      if (!(rangeArgValue instanceof IntegerData)) {
        throw new RenderException(
            "In 'for' command " + node.toSourceString() + ", the expression \"" +
            rangeArg.toSourceString() + "\" does not resolve to an integer.");
      }
      rangeArgValues.add(((IntegerData) rangeArgValue).getValue());
    }

    int increment = (rangeArgValues.size() == 3) ? rangeArgValues.remove(2) : 1 /* default */;
    int init = (rangeArgValues.size() == 2) ? rangeArgValues.remove(0) : 0 /* default */;
    int limit = rangeArgValues.get(0);

    String localVarName = node.getVarName();
    Map<String, SoyData> newEnvFrame = Maps.newHashMap();
    env.push(newEnvFrame);

    for (int i = init; i < limit; i += increment) {
      newEnvFrame.put(localVarName, IntegerData.forValue(i));
      visitChildren(node);
    }

    env.pop();
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    TemplateNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());
    if (callee == null) {
      throw new RenderException(
          "Attempting to render undefined template '" + node.getCalleeName() + "'.");
    }

    visitCallNodeHelper(node, callee);
  }


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {

    TemplateNode callee;
    try {
      callee =
          templateRegistry.selectDelegateTemplate(node.getDelCalleeName(), activeDelPackageNames);
    } catch (DelegateTemplateConflictException e) {
      throw new RenderException(e.getMessage());
    }

    if (callee == null) {
      return;  // no active delegate implementation, so the call output is empty string
    }

    visitCallNodeHelper(node, callee);
  }


  @SuppressWarnings("ConstantConditions")  // for IntelliJ
  private void visitCallNodeHelper(CallNode node, TemplateNode callee) {

    // ------ Build the call data. ------
    SoyMapData dataToPass;
    if (node.isPassingAllData()) {
      dataToPass = data;
    } else if (node.isPassingData()) {
      SoyData dataRefValue = eval(node.getExpr());
      if (!(dataRefValue instanceof SoyMapData)) {
        throw new RenderException(
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
        callData.putSingle(
            child.getKey(), eval(((CallParamValueNode) child).getValueExprUnion().getExpr()));

      } else if (child instanceof CallParamContentNode) {
        callData.putSingle(child.getKey(), renderChildren((CallParamContentNode) child));

      } else {
        throw new AssertionError();
      }
    }

    // ------ Render the callee template with the callData built above. ------
    RenderVisitor rv = renderVisitorFactory.create(
        outputSb, templateRegistry, callData, ijData, null, activeDelPackageNames, msgBundle,
        cssRenamingMap);
    rv.exec(callee);
  }


  @Override protected void visitCallParamNode(CallParamNode node) {
    // In this visitor, we never directly visit a CallParamNode.
    throw new AssertionError();
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ParentSoyNode<?>) {

      if (node instanceof BlockNode) {
        visitBlockHelper((BlockNode) node);

      } else {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  /**
   * Helper for recursing on a block.
   * @param node The BlockNode to recurse on.
   */
  private void visitBlockHelper(BlockNode node) {

    if (node.needsEnvFrameDuringInterp() != Boolean.FALSE /*true or unknown*/) {
      env.push(Maps.<String, SoyData>newHashMap());
      visitChildren(node);
      env.pop();

    } else {
      visitChildren(node);
    }
  }


  /**
   * Private helper to evaluate an expression. Always use this helper instead of using evalVisitor
   * directly, because this helper creates and throws a RenderException if there's an error.
   */
  private SoyData eval(ExprNode expr) {

    if (expr == null) {
      throw new RenderException("Cannot evaluate expression in V1 syntax.");
    }

    // Lazily initialize evalVisitor.
    if (evalVisitor == null) {
      evalVisitor = evalVisitorFactory.create(data, ijData, env);
    }

    try {
      return evalVisitor.exec(expr);
    } catch (Exception e) {
      throw new RenderException(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage());
    }
  }


  /**
   * Private helper to render the children of a block.
   * @param block The block whose children are to be rendered.
   * @return The result of rendering the block's children, as StringData.
   */
  private StringData renderChildren(BlockNode block) {

    StringBuilder output = new StringBuilder();
    RenderVisitor rv = renderVisitorFactory.create(
        output, templateRegistry, data, ijData, env, activeDelPackageNames, msgBundle,
        cssRenamingMap);
    rv.visitChildren(block);  // note: using visitChildren(), not exec()
    return StringData.forValue(output.toString());
  }


  // -----------------------------------------------------------------------------------------------
  // Helper class for traversing a translated plural/select message.


  /**
   * Visitor for processing {@code SoyMsgPluralPart} and {@code SoyMsgSelectPart} objects.
   *
   * Visits the parts hierarchy, evaluates each part and appends the result into the
   * parent class' StringBuffer object.
   *
   * In addition to writing to outputSb, this inner class uses the outer class' eval() method to
   * evaluate the expressions associated with the nodes.
   */
  private class PluralSelectMsgPartsVisitor {


    /** The parent message node for the parts dealt here. */
    private final MsgNode msgNode;

    /** The locale for the translated message considered. */
    private final ULocale locale;

    /** Holds the value of the remainder for the current enclosing plural part. */
    private int currentPluralRemainderValue;


    /**
     * Constructor.
     * @param msgNode The parent message node for the parts dealt here.
     * @param locale The locale of the Soy message.
     */
    public PluralSelectMsgPartsVisitor(MsgNode msgNode, ULocale locale) {
      this.msgNode = msgNode;
      this.locale = locale;
    }


    /**
     * Processes a {@code SoyMsgSelectPart} and appends the rendered output to
     * the {@code StringBuilder} object in {@code RenderVisitor}.
     * @param selectPart The Select part.
     */
    private void visitPart(SoyMsgSelectPart selectPart) {

      String selectVarName = selectPart.getSelectVarName();
      MsgSelectNode repSelectNode = msgNode.getRepSelectNode(selectVarName);

      // Associate the select variable with the value.
      String correctSelectValue;
      ExprRootNode<?> selectExpr = repSelectNode.getExpr();
      try {
        correctSelectValue = eval(selectExpr).stringValue();
      } catch (SoyDataException e) {
        throw new RenderException(
            String.format("Select expression \"%s\" doesn't evaluate to string.",
                selectExpr.toSourceString()));
      }

      List<SoyMsgPart> caseParts = null;
      List<SoyMsgPart> defaultParts = null;

      // Handle cases.
      for (Pair<String, List<SoyMsgPart>> case0 : selectPart.getCases()) {
        if (case0.first == null) {
          defaultParts = case0.second;
        } else if (case0.first.equals(correctSelectValue)) {
          caseParts = case0.second;
          break;
        }
      }

      if (caseParts == null) {
        caseParts = defaultParts;
      }

      if (caseParts != null) {

        for (SoyMsgPart casePart : caseParts) {

          if (casePart instanceof SoyMsgSelectPart) {
            visitPart((SoyMsgSelectPart) casePart);

          } else if (casePart instanceof SoyMsgPluralPart) {
            visitPart((SoyMsgPluralPart) casePart);

          } else if (casePart instanceof SoyMsgPlaceholderPart) {
            visitPart((SoyMsgPlaceholderPart) casePart);

          } else if (casePart instanceof SoyMsgRawTextPart) {
            visitPart((SoyMsgRawTextPart) casePart);

          } else {
            throw new RenderException("Unsupported part of type " + casePart.getClass().getName() +
                " under a select case.");

          }
        }
      }
    }


    /**
     * Processes a {@code SoyMsgPluralPart} and appends the rendered output to
     * the {@code StringBuilder} object in {@code RenderVisitor}.
     * It uses the message node cached in this object to get the corresponding
     * Plural node, gets its variable value and offset, and computes the remainder value to
     * be used to render the {@code SoyMsgPluralRemainderPart} later.
     * @param pluralPart The Plural part.
     */
    private void visitPart(SoyMsgPluralPart pluralPart) {

      MsgPluralNode repPluralNode = msgNode.getRepPluralNode(pluralPart.getPluralVarName());
      int correctPluralValue;
      ExprRootNode<?> pluralExpr = repPluralNode.getExpr();
      try {
        correctPluralValue = eval(pluralExpr).integerValue();
      } catch (SoyDataException e) {
        throw new RenderException(
            String.format("Plural expression \"%s\" doesn't evaluate to integer.",
                pluralExpr.toSourceString()));
      }

      currentPluralRemainderValue = correctPluralValue - repPluralNode.getOffset();

      // Handle cases.
      List<SoyMsgPart> caseParts = null;

      // Check whether the plural value matches any explicit numeric value.
      boolean hasNonExplicitCases = false;
      List<SoyMsgPart> otherCaseParts = null;
      for (Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>> case0 : pluralPart.getCases()) {

        SoyMsgPluralCaseSpec pluralCaseSpec = case0.first;
        SoyMsgPluralCaseSpec.Type caseType = pluralCaseSpec.getType();
        if (caseType == SoyMsgPluralCaseSpec.Type.EXPLICIT) {
          if (pluralCaseSpec.getExplicitValue() == correctPluralValue) {
            caseParts = case0.second;
            break;
          }

        } else if (caseType == SoyMsgPluralCaseSpec.Type.OTHER) {
          otherCaseParts = case0.second;

        } else {
          hasNonExplicitCases = true;

        }
      }

      if (caseParts == null && !hasNonExplicitCases) {
        caseParts = otherCaseParts;
      }

      if (caseParts == null) {
        // Didn't match any numeric value.  Check which plural rule it matches.
        String pluralKeyword = PluralRules.forLocale(locale).select(currentPluralRemainderValue);
        SoyMsgPluralCaseSpec.Type correctCaseType =
            new SoyMsgPluralCaseSpec(pluralKeyword).getType();


        // Iterate the cases once again for non-numeric keywords.
        for (Pair<SoyMsgPluralCaseSpec, List<SoyMsgPart>> case0 : pluralPart.getCases()) {

          if (case0.first.getType() == correctCaseType) {
            caseParts = case0.second;
            break;
          }
        }
      }

      if (caseParts != null) {
        for (SoyMsgPart casePart : caseParts) {

          if (casePart instanceof SoyMsgPlaceholderPart) {
            visitPart((SoyMsgPlaceholderPart) casePart);

          } else if (casePart instanceof SoyMsgRawTextPart) {
            visitPart((SoyMsgRawTextPart) casePart);

          } else if (casePart instanceof SoyMsgPluralRemainderPart) {
            visitPart((SoyMsgPluralRemainderPart) casePart);

          } else {
            // Plural parts will not have nested plural/select parts.  So, this is an error.
            throw new RenderException("Unsupported part of type " + casePart.getClass().getName() +
                " under a plural case.");

          }
        }
      }
    }


    /**
     * Processes a {@code SoyMsgPluralRemainderPart} and appends the rendered output to
     * the {@code StringBuilder} object in {@code RenderVisitor}.  Since this is precomputed
     * when visiting the {@code SoyMsgPluralPart} object, it is directly used here.
     * @param remainderPart The {@code SoyMsgPluralRemainderPart} object.
     */
    @SuppressWarnings("UnusedDeclaration")  // for IntelliJ
    private void visitPart(SoyMsgPluralRemainderPart remainderPart) {
      outputSb.append(currentPluralRemainderValue);
    }


    /**
     * Process a {@code SoyMsgPlaceholderPart} and updates the internal data structures.
     * @param msgPlaceholderPart the Placeholder part.
     */
    private void visitPart(SoyMsgPlaceholderPart msgPlaceholderPart) {

      // Since the content of a placeholder is not altered by translation, just render
      // the corresponding placeholder node.
      visit(msgNode.getRepPlaceholderNode(msgPlaceholderPart.getPlaceholderName()));
    }


    /**
     * Processes a {@code SoyMsgRawTextPart} and appends the contained text to
     * the {@code StringBuilder} object in {@code RenderVisitor}.
     * @param rawTextPart The raw text part.
     */
    private void visitPart(SoyMsgRawTextPart rawTextPart) {
      outputSb.append(rawTextPart.getRawText());
    }

  }

}
