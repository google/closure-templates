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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.AugmentedParamStore;
import com.google.template.soy.data.internal.BasicParamStore;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
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
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
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
import com.google.template.soy.soytree.XidNode;
import com.google.template.soy.soytree.defn.TemplateParam;

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


  /** Map of all SoyJavaPrintDirectives (name to directive). */
  protected final Map<String, SoyJavaPrintDirective> soyJavaDirectivesMap;

  /** Factory for creating an instance of EvalVisitor. */
  protected final EvalVisitorFactory evalVisitorFactory;

  /** The bundle containing all the templates that may be rendered. */
  protected final TemplateRegistry templateRegistry;

  /** The current template data. */
  protected final SoyRecord data;

  /** The current injected data. */
  protected final SoyRecord ijData;

  /** The current environment. */
  protected final Deque<Map<String, SoyValue>> env;

  /** The set of active delegate package names. */
  protected final Set<String> activeDelPackageNames;

  /** The bundle of translated messages, or null to use the messages from the Soy source. */
  protected final SoyMsgBundle msgBundle;

  /** xid renaming map. */
  protected final SoyIdRenamingMap xidRenamingMap;

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
   * @param soyJavaDirectivesMap Map of all SoyJavaPrintDirectives (name to directive).
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
   * @param xidRenamingMap The 'xid' renaming map, or null if not applicable.
   */
  protected RenderVisitor(
      Map<String, SoyJavaPrintDirective> soyJavaDirectivesMap,
      EvalVisitorFactory evalVisitorFactory, Appendable outputBuf,
      @Nullable TemplateRegistry templateRegistry, SoyRecord data,
      @Nullable SoyRecord ijData, @Nullable Deque<Map<String, SoyValue>> env,
      @Nullable Set<String> activeDelPackageNames, @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap xidRenamingMap, @Nullable SoyCssRenamingMap cssRenamingMap) {

    Preconditions.checkNotNull(data);

    this.soyJavaDirectivesMap = soyJavaDirectivesMap;
    this.evalVisitorFactory = evalVisitorFactory;
    this.templateRegistry = templateRegistry;
    this.data = data;
    this.ijData = ijData;
    this.env = (env != null) ? env : new ArrayDeque<Map<String, SoyValue>>();
    this.activeDelPackageNames = activeDelPackageNames;
    this.msgBundle = msgBundle;
    this.xidRenamingMap = xidRenamingMap;
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
  protected RenderVisitor createHelperInstance(Appendable outputBuf, SoyRecord data) {

    return new RenderVisitor(
        soyJavaDirectivesMap, evalVisitorFactory, outputBuf, templateRegistry,
        data, ijData, null, activeDelPackageNames, msgBundle, xidRenamingMap, cssRenamingMap);
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
      checkStrictParamTypes(node);
      visitBlockHelper(node);
    } catch (RenderException re) {
      // This will complete one single StackTraceElement, and rethrow.
      throw re.completeStackTraceElement(node);
    }
  }


  @Override protected void visitRawTextNode(RawTextNode node) {
    append(currOutputBuf, node.getRawText());
  }


  @Override protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    if (assistantForMsgs == null) {
      assistantForMsgs = new RenderVisitorAssistantForMsgs(this, env, msgBundle);
    }
    assistantForMsgs.visitForUseByMaster(node);
  }


  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    throw new AssertionError();
  }


  @Override protected void visitPrintNode(PrintNode node) {

    SoyValue result = eval(node.getExprUnion().getExpr(), node);
    if (result instanceof UndefinedData) {
      throw new RenderException(
          "In 'print' tag, expression \"" + node.getExprText() + "\" evaluates to undefined.")
          .addPartialStackTraceElement(node.getSourceLocation());
    }

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Evaluate directive args.
      List<ExprRootNode<?>> argsExprs = directiveNode.getArgs();
      List<SoyValue> argsSoyDatas = Lists.newArrayListWithCapacity(argsExprs.size());
      for (ExprRootNode<?> argExpr : argsExprs) {
        argsSoyDatas.add(eval(argExpr, directiveNode));
      }

      // Apply directive.
      result = applyDirective(directiveNode.getName(), result, argsSoyDatas, node);
    }

    // Important: Use coerceToString to make sure we are using the value's preferred way of being
    // converted to string.
    append(currOutputBuf, result.coerceToString());
  }


  @Override protected void visitXidNode(XidNode node) {
    String xid = node.getRenamedText(xidRenamingMap);
    append(currOutputBuf, xid);
  }


  @Override protected void visitCssNode(CssNode node) {
    ExprRootNode<?> componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      append(currOutputBuf, eval(componentNameExpr, node).toString());
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

    String className = node.getRenamedSelectorText(cssRenamingMap);
    append(currOutputBuf, className);
  }


  @Override protected void visitLetValueNode(LetValueNode node) {
    env.peek().put(node.getVarName(), eval(node.getValueExpr(), node));
  }


  @Override protected void visitLetContentNode(LetContentNode node) {
    SoyValue renderedBlock = renderBlock(node);

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
        if (eval(icn.getExprUnion().getExpr(), node).coerceToBoolean()) {
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

    SoyValue switchValue = eval(node.getExpr(), node);

    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;
        for (ExprNode caseExpr : scn.getExprList()) {
          if (switchValue.equals(eval(caseExpr, scn))) {
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

    SoyValue dataRefValue = eval(node.getExpr(), node);
    if (!(dataRefValue instanceof SoyList)) {
      throw new RenderException(
          "In 'foreach' command " + node.toSourceString() +
          ", the data reference does not resolve to a SoyList " +
          "(encountered type " + dataRefValue.getClass().getName() + ").");
    }
    SoyList foreachList = (SoyList) dataRefValue;

    if (foreachList.length() > 0) {
      // Case 1: Nonempty list.
      String varName = node.getVarName();

      Map<String, SoyValue> newEnvFrame = Maps.newHashMap();
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
      SoyValue rangeArgValue = eval(rangeArg, node);
      if (!(rangeArgValue instanceof IntegerData)) {
        throw new RenderException(
            "In 'for' command " + node.toSourceString() + ", the expression \"" +
            rangeArg.toSourceString() + "\" does not resolve to an integer.");
      }
      rangeArgValues.add(((IntegerData) rangeArgValue).integerValue());
    }

    int increment = (rangeArgValues.size() == 3) ? rangeArgValues.remove(2) : 1 /* default */;
    int init = (rangeArgValues.size() == 2) ? rangeArgValues.remove(0) : 0 /* default */;
    int limit = rangeArgValues.get(0);

    String localVarName = node.getVarName();
    Map<String, SoyValue> newEnvFrame = Maps.newHashMap();
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
          "Attempting to render undefined template '" + node.getCalleeName() + "'.")
          .addPartialStackTraceElement(node.getSourceLocation());
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
        SoyValue variantData = eval(variantExpr, node);
        if (variantData instanceof IntegerData) {
          // An integer constant is being used as variant. Use the value string representation as
          // variant.
          variant = String.valueOf(variantData.longValue());
        } else {
          // Variant is either a StringData or a SanitizedContent. Use the value as a string. If
          // the value is not a string, and exception will be thrown.
          variant = variantData.stringValue();
        }
      } catch (SoyDataException e) {
        throw new RenderException(String.format("Variant expression \"%s\" doesn't" +
            " evaluate to a valid type (Only string and integer are supported).",
            variantExpr.toSourceString()), e)
            .addPartialStackTraceElement(node.getSourceLocation());
      }
    }
    DelTemplateKey delegateKey = new DelTemplateKey(node.getDelCalleeName(), variant);

    TemplateDelegateNode callee;
    try {
      callee = templateRegistry.selectDelTemplate(delegateKey, activeDelPackageNames);
    } catch (DelegateTemplateConflictException e) {
      throw new RenderException(e.getMessage(), e)
          .addPartialStackTraceElement(node.getSourceLocation());
    }

    if (callee != null) {
      visitCallNodeHelper(node, callee);

    } else if (node.allowsEmptyDefault()) {
      return;  // no active delegate implementation, so the call output is empty string

    } else {
      throw new RenderException(
          "Found no active impl for delegate call to '" + node.getDelCalleeName() +
          "' (and no attribute allowemptydefault=\"true\").")
          .addPartialStackTraceElement(node.getSourceLocation());
    }
  }


  @SuppressWarnings("ConstantConditions")  // for IntelliJ
  private void visitCallNodeHelper(CallNode node, TemplateNode callee) {

    // ------ Build the call data. ------
    SoyRecord dataToPass;
    if (node.isPassingAllData()) {
      dataToPass = data;
    } else if (node.isPassingData()) {
      SoyValue dataRefValue = eval(node.getDataExpr(), node);
      if (!(dataRefValue instanceof SoyRecord)) {
        throw new RenderException(
            "In 'call' command " + node.toSourceString() +
            ", the data reference does not resolve to a SoyRecord.")
            .addPartialStackTraceElement(node.getSourceLocation());
      }
      dataToPass = (SoyRecord) dataRefValue;
    } else {
      dataToPass = null;
    }

    SoyRecord callData;

    if (node.numChildren() == 0) {
      // --- Cases 1 and 2: Not passing params. ---
      if (dataToPass == null) {
        // Case 1: Not passing data and not passing params.
        callData = ParamStore.EMPTY_INSTANCE;
      } else {
        // Case 2: Passing data and not passing params.
        callData = dataToPass;
      }

    } else {
      // --- Cases 3 and 4: Passing params. ---
      ParamStore mutableCallData;

      if (dataToPass == null) {
        // Case 3: Not passing data and passing params.
        mutableCallData = new BasicParamStore();
      } else {
        // Case 4: Passing data and passing params.
        mutableCallData = new AugmentedParamStore(dataToPass);
      }

      for (CallParamNode child : node.getChildren()) {

        if (child instanceof CallParamValueNode) {
          mutableCallData.setField(
              child.getKey(),
              eval(((CallParamValueNode) child).getValueExprUnion().getExpr(), child));

        } else if (child instanceof CallParamContentNode) {
          CallParamContentNode childCpcn = (CallParamContentNode) child;
          SoyValue renderedBlock = renderBlock(childCpcn);

          // If the param node has a content kind attribute, it will have been autoescaped in the
          // corresponding context by the strict contextual autoescaper. Hence, the result of
          // evaluating the param block is wrapped in SanitizedContent of the specified kind.
          if (childCpcn.getContentKind() != null) {
            renderedBlock = UnsafeSanitizedContentOrdainer.ordainAsSafe(
                renderedBlock.stringValue(), childCpcn.getContentKind());
          }

          mutableCallData.setField(child.getKey(), renderedBlock);

        } else {
          throw new AssertionError();
        }
      }

      callData = mutableCallData;
    }

    // ------ Render the callee template with the callData built above. ------

    if (node.getEscapingDirectiveNames().isEmpty()) {
      // No escaping at the call site -- render directly into the output buffer.
      RenderVisitor rv = createHelperInstance(currOutputBuf, callData);
      try {
        rv.exec(callee);
      } catch (RenderException re) {
        // The {call .XXX} failed to render - a new partial stack trace element is added to capture
        // this template call.
        throw re.addPartialStackTraceElement(node.getSourceLocation());
      }
    } else {
      // Escaping the call site's result, such as at a strict template boundary.
      // TODO: Some optimization is needed here before Strict Soy can be widely used:
      // - Only create this temporary buffer when contexts mismatch. We could run a pre-pass that
      // eliminates escaping directives when all callers are known.
      // - Instead of creating a temporary buffer and copying, wrap with an escaping StringBuilder.
      StringBuilder calleeBuilder = new StringBuilder();
      RenderVisitor rv = createHelperInstance(calleeBuilder, callData);
      try {
        rv.exec(callee);
      } catch (RenderException re) {
        // The {call .XXX} failed to render - a new partial stack trace element is added to capture
        // this template call.
        throw re.addPartialStackTraceElement(node.getSourceLocation());
      }
      SoyValue resultData = (callee.getContentKind() != null) ?
          UnsafeSanitizedContentOrdainer.ordainAsSafe(
              calleeBuilder.toString(), callee.getContentKind()) :
          StringData.forValue(calleeBuilder.toString());
      for (String directiveName : node.getEscapingDirectiveNames()) {
        resultData = applyDirective(directiveName, resultData, ImmutableList.<SoyValue>of(), node);
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
      env.push(Maps.<String, SoyValue>newHashMap());
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
  private SoyValue eval(ExprNode expr, SoyNode node) {

    if (expr == null) {
      throw new RenderException("Cannot evaluate expression in V1 syntax.")
          .addPartialStackTraceElement(node.getSourceLocation());
    }

    // Lazily initialize evalVisitor.
    if (evalVisitor == null) {
      evalVisitor = evalVisitorFactory.create(data, ijData, env);
    }

    try {
      return evalVisitor.exec(expr);
    } catch (Exception e) {
      throw new RenderException(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage(), e)
          .addPartialStackTraceElement(node.getSourceLocation());
    }
  }


  /**
   * This method must only be called by assistant visitors, in particular
   * RenderVisitorAssistantForMsgs.
   */
  SoyValue evalForUseByAssistants(ExprNode expr, SoyNode node) {
    return eval(expr, node);
  }


  /**
   * Helper to append text to the output, propagating any exceptions.
   */
  static void append(Appendable outputBuf, CharSequence cs) {
    try {
      outputBuf.append(cs);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Protected helper to apply a print directive.
   *
   * @param directiveName The name of the directive.
   * @param value The value to apply the directive on.
   * @param args The arguments to the directive.
   * @param node The node with the escaping. Only used for error reporting.
   * @return The result of applying the directive with the given arguments to the given value.
   */
  private SoyValue applyDirective(
      String directiveName, SoyValue value, List<SoyValue> args, SoyNode node) {

    // Get directive.
    SoyJavaPrintDirective directive = soyJavaDirectivesMap.get(directiveName);
    if (directive == null) {
      throw new RenderException(
          "Failed to find Soy print directive with name '" + directiveName + "'" +
          " (tag " + node.toSourceString() + ")")
          .addPartialStackTraceElement(node.getSourceLocation());
    }

    // TODO: Add a pass to check num args at compile time.
    if (! directive.getValidArgsSizes().contains(args.size())) {
      throw new RenderException(
          "Print directive '" + directiveName + "' used with the wrong number of" +
          " arguments (tag " + node.toSourceString() + ").")
          .addPartialStackTraceElement(node.getSourceLocation());
    }

    try {
      return directive.applyForJava(value, args);
    } catch (RuntimeException e) {
      throw new RenderException(String.format(
          "Failed in applying directive '%s' in tag \"%s\" due to exception: %s",
          directiveName, node.toSourceString(), e.getMessage()), e)
          .addPartialStackTraceElement(node.getSourceLocation());
    }
  }

  private void checkStrictParamTypes(TemplateNode node) {
    for (TemplateParam param : node.getParams()) {
      SoyValue paramValue = data.getField(param.name());
      if (paramValue == null) {
        paramValue = NullData.INSTANCE;
      }
      if (!param.type().isInstance(paramValue)) {
        throw new RenderException(
            "Parameter type mismatch: attempt to bind value '" +
            (paramValue instanceof UndefinedData ? "(undefined)" : paramValue) +
            "' to parameter '" + param.name() + "' which has declared type '" +
            param.type().toString() + "'.")
            .addPartialStackTraceElement(node.getSourceLocation());
      }
    }
  }
}
