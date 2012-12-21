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

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.AugmentedSoyMapData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.msgs.SoyMsgBundle;
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
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateConflictException;

import java.io.IOException;
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
 * <p> The rendered output will be appended to the Appendable provided to the constructor.
 *
 * @author Kai Huang
 */
public class RenderVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Map of all SoyJavaRuntimePrintDirectives (name to directive). */
  protected final Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap;

  /** Factory for creating an instance of EvalVisitor. */
  protected final EvalVisitorFactory evalVisitorFactory;

  /** The bundle containing all the templates that may be rendered. */
  protected final TemplateRegistry templateRegistry;

  /** The current template data. */
  protected final SoyMapData data;

  /** The current injected data. */
  protected final SoyMapData ijData;

  /** The current environment. */
  protected final Deque<Map<String, SoyData>> env;

  /** The set of active delegate package names. */
  protected final Set<String> activeDelPackageNames;

  /** The bundle of translated messages, or null to use the messages from the Soy source. */
  protected final SoyMsgBundle msgBundle;

  /** CSS renaming map. */
  protected final SoyCssRenamingMap cssRenamingMap;

  /** The EvalVisitor for this instance (can reuse since 'data' and 'env' references stay same). */
  // Note: Don't use directly. Call eval() instead.
  private EvalVisitor evalVisitor;

  /** The assistant visitor for msgs (lazily initialized). */
  private RenderVisitorAssistantForMsgs assistantForMsgs;

  /** The stack of output Appendables (current output buffer is top of stack). */
  protected Deque<Appendable> outputBufStack;

  /** The current Appendable to append the output to. Equals the top element of outputStack. */
  private Appendable currOutputBuf;


  /**
   * @param soyJavaRuntimeDirectivesMap Map of all SoyJavaRuntimePrintDirectives (name to
   *     directive). Can be null if the subclass that is calling this constructor plans to override
   *     the default implementation of {@code applyDirective()}.
   * @param evalVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates. Should never be null (except in some unit
   *     tests).
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param env The current environment, or null if this is the initial call.
   * @param activeDelPackageNames The set of active delegate package names. Allowed to be null when
   *     known to be irrelevant.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   */
  protected RenderVisitor(
      @Nullable Map<String, SoyJavaRuntimePrintDirective> soyJavaRuntimeDirectivesMap,
      EvalVisitorFactory evalVisitorFactory, Appendable outputBuf,
      @Nullable TemplateRegistry templateRegistry, SoyMapData data, @Nullable SoyMapData ijData,
      @Nullable Deque<Map<String, SoyData>> env, @Nullable Set<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap) {

    Preconditions.checkNotNull(data);

    this.soyJavaRuntimeDirectivesMap = soyJavaRuntimeDirectivesMap;
    this.evalVisitorFactory = evalVisitorFactory;
    this.templateRegistry = templateRegistry;
    this.data = data;
    this.ijData = ijData;
    this.env = (env != null) ? env : new ArrayDeque<Map<String, SoyData>>();
    this.activeDelPackageNames = activeDelPackageNames;
    this.msgBundle = msgBundle;
    this.cssRenamingMap = cssRenamingMap;

    this.evalVisitor = null;  // lazily initialized
    this.assistantForMsgs = null;  // lazily initialized

    this.outputBufStack = new ArrayDeque<Appendable>();
    pushOutputBuf(outputBuf);
  }


  /**
   * Creates a helper instance for rendering a subtemplate.
   *
   * @param outputBuf The Appendable to append the output to.
   * @param data The template data.
   * @return The newly created RenderVisitor instance.
   */
  protected RenderVisitor createHelperInstance(Appendable outputBuf, SoyMapData data) {

    return new RenderVisitor(
        soyJavaRuntimeDirectivesMap, evalVisitorFactory, outputBuf, templateRegistry,
        data, ijData, null, activeDelPackageNames, msgBundle, cssRenamingMap);
  }


  /**
   * This method must only be called by assistant visitors, in particular
   * RenderVisitorAssistantForMsgs.
   */
  void visitForUseByAssistants(SoyNode node) {
    visit(node);
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
    append(currOutputBuf, node.getRawText());
  }


  @Override protected void visitMsgNode(MsgNode node) {
    if (assistantForMsgs == null) {
      assistantForMsgs = new RenderVisitorAssistantForMsgs(this, env, msgBundle);
    }
    assistantForMsgs.visitForUseByMaster(node);
  }


  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    throw new AssertionError();
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
      result = applyDirective(directiveNode.getName(), result, argsSoyDatas, node);
    }

    append(currOutputBuf, result.toString());
  }


  @Override protected void visitCssNode(CssNode node) {

    ExprRootNode<?> componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      append(currOutputBuf, eval(componentNameExpr).toString());
      append(currOutputBuf, "-");
    }

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

    String selectorText = node.getSelectorText();
    if (cssRenamingMap != null) {
      String mappedText = cssRenamingMap.get(selectorText);
      if (mappedText != null) {
        selectorText = mappedText;
      }
    }
    append(currOutputBuf, selectorText);
  }


  @Override protected void visitLetValueNode(LetValueNode node) {
    env.peek().put(node.getVarName(), eval(node.getValueExpr()));
  }


  @Override protected void visitLetContentNode(LetContentNode node) {
    SoyData renderedBlock = renderBlock(node);

    // If the let node has a content kind attribute, it will have been autoescaped in the
    // corresponding context by the strict contextual autoescaper. Hence, the result of evaluating
    // the let block is wrapped in SanitizedContent of the specified kind.
    // TODO: Consider adding mutable state to nodes that allows the contextual escaper to tag
    // nodes it has processed, and assert presence of this tag here.
    if (node.getContentKind() != null) {
      renderedBlock = UnsafeSanitizedContentOrdainer.ordainAsSafe(
          renderedBlock.stringValue(), node.getContentKind());
    }

    env.peek().put(node.getVarName(), renderedBlock);
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
    SoyListData foreachList = (SoyListData) dataRefValue;

    if (foreachList.length() > 0) {
      // Case 1: Nonempty list.
      String varName = node.getVarName();

      Map<String, SoyData> newEnvFrame = Maps.newHashMap();
      // Note: No need to save firstIndex as it's always 0.
      newEnvFrame.put(varName + "__lastIndex", IntegerData.forValue(foreachList.length() - 1));
      env.push(newEnvFrame);

      for (int i = 0; i < foreachList.length(); ++i) {
        newEnvFrame.put(varName + "__index", IntegerData.forValue(i));
        newEnvFrame.put(varName, foreachList.get(i));
        visitChildren((ForeachNonemptyNode) node.getChild(0));
      }

      env.pop();

    } else {
      // Case 2: Empty list. If the 'ifempty' node exists, visit it.
      if (node.numChildren() == 2) {
        visit(node.getChild(1));
      }
    }
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

    ExprRootNode<?> variantExpr = node.getDelCalleeVariantExpr();
    String variant;
    if (variantExpr == null) {
      variant = "";
    } else {
      try {
        variant = eval(variantExpr).stringValue();
      } catch (SoyDataException e) {
        throw new RenderException(String.format(
            "Variant expression \"%s\" doesn't evaluate to a string.",
            variantExpr.toSourceString()));
      }
    }
    DelTemplateKey delegateKey = new DelTemplateKey(node.getDelCalleeName(), variant);

    TemplateDelegateNode callee;
    try {
      callee = templateRegistry.selectDelTemplate(delegateKey, activeDelPackageNames);
    } catch (DelegateTemplateConflictException e) {
      throw new RenderException(e.getMessage());
    }

    if (callee != null) {
      visitCallNodeHelper(node, callee);

    } else if (node.allowsEmptyDefault()) {
      return;  // no active delegate implementation, so the call output is empty string

    } else {
      throw new RenderException(
          "Found no active impl for delegate call to '" + node.getDelCalleeName() +
          "' (and no attribute allowemptydefault=\"true\").");
    }
  }


  @SuppressWarnings("ConstantConditions")  // for IntelliJ
  private void visitCallNodeHelper(CallNode node, TemplateNode callee) {

    // ------ Build the call data. ------
    SoyMapData dataToPass;
    if (node.isPassingAllData()) {
      dataToPass = data;
    } else if (node.isPassingData()) {
      SoyData dataRefValue = eval(node.getDataExpr());
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
        CallParamContentNode childCpcn = (CallParamContentNode) child;
        SoyData renderedBlock = renderBlock(childCpcn);

        // If the param node has a content kind attribute, it will have been autoescaped in the
        // corresponding context by the strict contextual autoescaper. Hence, the result of
        // evaluating the param block is wrapped in SanitizedContent of the specified kind.
        if (childCpcn.getContentKind() != null) {
          renderedBlock = UnsafeSanitizedContentOrdainer.ordainAsSafe(
              renderedBlock.stringValue(), childCpcn.getContentKind());
        }

        callData.putSingle(child.getKey(), renderedBlock);

      } else {
        throw new AssertionError();
      }
    }

    // ------ Render the callee template with the callData built above. ------

    if (node.getEscapingDirectiveNames().isEmpty()) {
      // No escaping at the call site -- render directly into the output buffer.
      RenderVisitor rv = createHelperInstance(currOutputBuf, callData);
      rv.exec(callee);
    } else {
      // Escaping the call site's result, such as at a strict template boundary.
      // TODO: Some optimization is needed here before Strict Soy can be widely used:
      // - Only create this temporary buffer when contexts mismatch. We could run a pre-pass that
      // eliminates escaping directives when all callers are known.
      // - Instead of creating a temporary buffer and copying, wrap with an escaping StringBuilder.
      StringBuilder calleeBuilder = new StringBuilder();
      RenderVisitor rv = createHelperInstance(calleeBuilder, callData);
      rv.exec(callee);
      SoyData resultData = (callee.getContentKind() != null) ?
          UnsafeSanitizedContentOrdainer.ordainAsSafe(
              calleeBuilder.toString(), callee.getContentKind()) :
          StringData.forValue(calleeBuilder.toString());
      for (String directiveName : node.getEscapingDirectiveNames()) {
        resultData = applyDirective(directiveName, resultData, ImmutableList.<SoyData>of(), node);
      }
      append(currOutputBuf, resultData.toString());
    }
  }


  @Override protected void visitCallParamNode(CallParamNode node) {
    // In this visitor, we never directly visit a CallParamNode.
    throw new AssertionError();
  }


  @Override protected void visitLogNode(LogNode node) {
    System.out.println(renderBlock(node));
  }


  @Override protected void visitDebuggerNode(DebuggerNode node) {
    // The 'debugger' statement does nothing in Java rendering, but the user could theoretically
    // place a breakpoint at this method.
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
   * Pushes the given output buffer onto the stack (it becomes the current output buffer).
   */
  private void pushOutputBuf(Appendable outputBuf) {
    outputBufStack.push(outputBuf);
    currOutputBuf = outputBuf;
  }


  /**
   * Pops the top output buffer off the stack and returns it (changes the current output buffer).
   */
  private Appendable popOutputBuf() {
    Appendable poppedOutputBuf = outputBufStack.pop();
    currOutputBuf = outputBufStack.peek();
    return poppedOutputBuf;
  }


  /**
   * This method must only be called by assistant visitors, in particular
   * RenderVisitorAssistantForMsgs.
   */
  Appendable getCurrOutputBufForUseByAssistants() {
    return currOutputBuf;
  }


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
   * Private helper to render the children of a block into a separate string (not directly appended
   * to the current output buffer).
   * @param block The block whose children are to be rendered.
   * @return The result of rendering the block's children, as StringData.
   */
  private StringData renderBlock(BlockNode block) {

    pushOutputBuf(new StringBuilder());
    visitBlockHelper(block);
    Appendable outputBuf = popOutputBuf();
    return StringData.forValue(outputBuf.toString());
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
      Throwable cause = (e instanceof RenderException) ? e.getCause() : e;
      throw new RenderException(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage(), cause);
    }
  }


  /**
   * This method must only be called by assistant visitors, in particular
   * RenderVisitorAssistantForMsgs.
   */
  SoyData evalForUseByAssistants(ExprNode expr) {
    return eval(expr);
  }


  /**
   * Helper to append text to the output, propagating any exceptions.
   */
  static void append(Appendable outputBuf, CharSequence cs) {
    try {
      outputBuf.append(cs);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }


  /**
   * Protected helper to apply a print directive.
   *
   * <p> This default implementation can be overridden by subclasses (such as TofuRenderVisitor)
   * that have access to a potentially larger set of print directives.
   *
   * @param directiveName The name of the directive.
   * @param value The value to apply the directive on.
   * @param args The arguments to the directive.
   * @param node The node with the escaping. Only used for error reporting.
   * @return The result of applying the directive with the given arguments to the given value.
   */
  protected SoyData applyDirective(
      String directiveName, SoyData value, List<SoyData> args, SoyNode node) {

    // Get directive.
    SoyJavaRuntimePrintDirective directive = soyJavaRuntimeDirectivesMap.get(directiveName);
    if (directive == null) {
      throw new RenderException(
          "Failed to find Soy print directive with name '" + directiveName + "'" +
          " (tag " + node.toSourceString() + ")");
    }

    // TODO: Add a pass to check num args at compile time.
    if (! directive.getValidArgsSizes().contains(args.size())) {
      throw new RenderException(
          "Print directive '" + directiveName + "' used with the wrong number of" +
          " arguments (tag " + node.toSourceString() + ").");
    }

    try {
      return directive.apply(value, args);

    } catch (RuntimeException e) {
      throw new RenderException(String.format(
          "Failed in applying directive '%s' in tag \"%s\" due to exception: %s",
          directiveName, node.toSourceString(), e.getMessage()));
    }
  }

}
