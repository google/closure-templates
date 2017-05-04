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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.template.soy.data.LazySanitizedContents;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyAbstractCachingValueProvider;
import com.google.template.soy.data.SoyAbstractCachingValueProvider.ValueAssertion;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyFutureValueProvider;
import com.google.template.soy.data.SoyFutureValueProvider.FutureBlockCallback;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.AugmentedParamStore;
import com.google.template.soy.data.internal.BasicParamStore;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.internal.RenderableThunk;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.SharedRuntime;
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
import com.google.template.soy.soytree.ForNode.RangeArgs;
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
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.XidNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.soytree.defn.LoopVar;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType.Kind;
import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Visitor for rendering the template subtree rooted at a given SoyNode.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>The rendered output will be appended to the Appendable provided to the constructor.
 *
 */
public class RenderVisitor extends AbstractSoyNodeVisitor<Void> {

  /** Map of all SoyJavaPrintDirectives (name to directive). */
  protected final ImmutableMap<String, ? extends SoyJavaPrintDirective> soyJavaDirectivesMap;

  /** Factory for creating an instance of EvalVisitor. */
  protected final EvalVisitorFactory evalVisitorFactory;

  /** The bundle containing all the templates that may be rendered. */
  protected final TemplateRegistry templateRegistry;

  /** The current template data. */
  protected final SoyRecord data;

  /** The current injected data. */
  protected final SoyRecord ijData;

  /** The current environment. */
  protected Environment env;

  /** The predicate for testing whether a given delpackage is active. */
  protected final Predicate<String> activeDelPackageSelector;

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
   * Render visitors have a stack of output buffers and RenderVisitors a nested (to render blocks)
   * with independent output buffers. Of those, if any, the first one that is passed in can be
   * flushed – all the others are StringBuilders. This instance variable holds the flushable root
   * output buffer.
   */
  private CountingFlushableAppendable flushable;

  /**
   * @param soyJavaDirectivesMap Map of all SoyJavaPrintDirectives (name to directive).
   * @param evalVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates. Should never be null (except in some unit
   *     tests).
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param activeDelPackageSelector The predicate for testing whether a given delpackage is active.
   *     Allowed to be null when known to be irrelevant.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   * @param xidRenamingMap The 'xid' renaming map, or null if not applicable.
   */
  protected RenderVisitor(
      ImmutableMap<String, ? extends SoyJavaPrintDirective> soyJavaDirectivesMap,
      EvalVisitorFactory evalVisitorFactory,
      Appendable outputBuf,
      @Nullable TemplateRegistry templateRegistry,
      SoyRecord data,
      @Nullable SoyRecord ijData,
      @Nullable Predicate<String> activeDelPackageSelector,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap) {
    Preconditions.checkNotNull(data);

    this.soyJavaDirectivesMap = soyJavaDirectivesMap;
    this.evalVisitorFactory = evalVisitorFactory;
    this.templateRegistry = templateRegistry;
    this.data = data;
    this.ijData = ijData;
    this.activeDelPackageSelector = activeDelPackageSelector;
    this.msgBundle = msgBundle;
    this.xidRenamingMap = (xidRenamingMap == null) ? SoyCssRenamingMap.EMPTY : xidRenamingMap;
    this.cssRenamingMap = (cssRenamingMap == null) ? SoyCssRenamingMap.EMPTY : cssRenamingMap;

    this.evalVisitor = null; // lazily initialized
    this.assistantForMsgs = null; // lazily initialized

    this.outputBufStack = new ArrayDeque<>();
    if (outputBuf instanceof Flushable) {
      if (outputBuf instanceof CountingFlushableAppendable) {
        flushable = (CountingFlushableAppendable) outputBuf;
      } else {
        flushable = new CountingFlushableAppendable(outputBuf);
      }
      outputBuf = flushable;
    }
    pushOutputBuf(outputBuf);
  }

  @Override
  public Void exec(SoyNode node) {
    if (flushable != null) {
      // only do this in exec() so that all recursively called templates flush the correct top-level
      // output stream
      FutureBlockCallback old = SoyFutureValueProvider.futureBlockCallback.get();
      SoyFutureValueProvider.futureBlockCallback.set(flushable);
      super.exec(node);
      SoyFutureValueProvider.futureBlockCallback.set(old);
    } else {
      super.exec(node);
    }
    return null;
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
        soyJavaDirectivesMap,
        evalVisitorFactory,
        outputBuf,
        templateRegistry,
        data,
        ijData,
        activeDelPackageSelector,
        msgBundle,
        xidRenamingMap,
        cssRenamingMap);
  }

  /**
   * This method must only be called by assistant visitors, in particular
   * RenderVisitorAssistantForMsgs.
   */
  void visitForUseByAssistants(SoyNode node) {
    visit(node);
  }

  /** A private helper to render templates with optimized type checking. */
  private void renderTemplate(
      TemplateNode template, ImmutableList<TemplateParam> paramsToTypeCheck) {
    env = Environment.create(template, data, ijData);
    checkStrictParamTypes(template, paramsToTypeCheck);
    visitChildren(template);
    env = null; // unpin for gc
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    // check all params of the node. This callpath should only be called in the case of external
    // calls into soy (e.g. RenderVisitor.exec(node)).  For calls to templates from soy, the
    // renderTemplate() method is called directly.
    renderTemplate(node, node.getParams());
  }

  @Override
  protected void visitRawTextNode(RawTextNode node) {
    append(currOutputBuf, node.getRawText());
  }

  @Override
  protected void visitMsgFallbackGroupNode(MsgFallbackGroupNode node) {
    if (assistantForMsgs == null) {
      assistantForMsgs = new RenderVisitorAssistantForMsgs(this, msgBundle);
    }
    if (!node.getEscapingDirectiveNames().isEmpty()) {
      // The entire message needs to be escaped, so we need to render to a temporary buffer.
      // Fortunately, for most messages (in HTML context) this is unnecessary.
      pushOutputBuf(new StringBuilder());
    }
    assistantForMsgs.visitForUseByMaster(node);
    if (!node.getEscapingDirectiveNames().isEmpty()) {
      // Escape the entire message with the required directives.
      SoyValue wholeMsg = StringData.forValue(popOutputBuf().toString());
      for (String directiveName : node.getEscapingDirectiveNames()) {
        wholeMsg = applyDirective(directiveName, wholeMsg, ImmutableList.<SoyValue>of(), node);
      }
      append(currOutputBuf, wholeMsg.stringValue());
    }
  }

  @Override
  protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    throw new AssertionError();
  }

  @Override
  protected void visitPrintNode(PrintNode node) {

    SoyValue result = eval(node.getExpr(), node);
    if (result instanceof UndefinedData) {
      throw RenderException.createWithSource(
          "In 'print' tag, expression \""
              + node.getExpr().toSourceString()
              + "\" evaluates to undefined.",
          node);
    }

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Evaluate directive args.
      List<ExprRootNode> argsExprs = directiveNode.getArgs();
      List<SoyValue> argsSoyDatas = Lists.newArrayListWithCapacity(argsExprs.size());
      for (ExprRootNode argExpr : argsExprs) {
        argsSoyDatas.add(eval(argExpr, directiveNode));
      }

      // Apply directive.
      result = applyDirective(directiveNode.getName(), result, argsSoyDatas, node);
    }

    append(currOutputBuf, result, node);
  }

  @Override
  protected void visitXidNode(XidNode node) {
    String xid = node.getRenamedText(xidRenamingMap);
    append(currOutputBuf, xid);
  }

  @Override
  protected void visitCssNode(CssNode node) {
    ExprRootNode componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      append(currOutputBuf, eval(componentNameExpr, node), node);
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

  @Override
  protected void visitLetValueNode(LetValueNode node) {
    env.bind(node.getVar(), lazyEval(node.getExpr(), node));
  }

  @Override
  protected void visitLetContentNode(LetContentNode node) {
    env.bind(node.getVar(), renderRenderUnitNode(node));
  }

  @Override
  protected void visitIfNode(IfNode node) {

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        if (eval(icn.getExpr(), node).coerceToBoolean()) {
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

  @Override
  protected void visitSwitchNode(SwitchNode node) {

    SoyValue switchValue = eval(node.getExpr(), node);

    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;
        for (ExprNode caseExpr : scn.getExprList()) {
          if (SharedRuntime.equal(switchValue, eval(caseExpr, scn))) {
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

  @Override
  protected void visitForeachNode(ForeachNode node) {

    SoyValue dataRefValue = eval(node.getExpr(), node);
    if (!(dataRefValue instanceof SoyList)) {
      throw RenderException.createWithSource(
          "In 'foreach' command "
              + node.toSourceString()
              + ", the data reference does not "
              + "resolve to a SoyList "
              + "(encountered type "
              + dataRefValue.getClass().getName()
              + ").",
          node);
    }
    SoyList foreachList = (SoyList) dataRefValue;
    int listLength = foreachList.length();
    if (listLength > 0) {
      // Case 1: Nonempty list.
      ForeachNonemptyNode child = (ForeachNonemptyNode) node.getChild(0);
      LoopVar var = child.getVar();
      for (int i = 0; i < listLength; ++i) {
        SoyValueProvider value = foreachList.getProvider(i);
        env.bind(var, value);
        env.bindCurrentIndex(var, i);
        env.bindIsLast(var, listLength - 1 == i);
        visitChildren(child);
      }
    } else {
      // Case 2: Empty list. If the 'ifempty' node exists, visit it.
      if (node.numChildren() == 2) {
        visit(node.getChild(1));
      }
    }
  }

  @Override
  protected void visitForNode(ForNode node) {

    RangeArgs rangeArgs = node.getRangeArgs();

    int increment = evalRangeArg(node, rangeArgs.increment());
    int init = evalRangeArg(node, rangeArgs.start());
    int limit = evalRangeArg(node, rangeArgs.limit());

    LocalVar localVarName = node.getVar();
    for (int i = init; i < limit; i += increment) {
      env.bind(localVarName, IntegerData.forValue(i));
      visitChildren(node);
    }
  }

  private int evalRangeArg(ForNode node, ExprRootNode rangeArg) {
    SoyValue rangeArgValue = eval(rangeArg, node);
    if (!(rangeArgValue instanceof IntegerData)) {
      throw RenderException.createWithSource(
          "In 'for' command "
              + node.toSourceString()
              + ", the expression \""
              + rangeArg.toSourceString()
              + "\" does not resolve to an integer.",
          node);
    }
    return rangeArgValue.integerValue();
  }

  @Override
  protected void visitCallBasicNode(CallBasicNode node) {

    TemplateNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());
    if (callee == null) {
      throw RenderException.createWithSource(
          "Attempting to render undefined template '" + node.getCalleeName() + "'.", node);
    }

    visitCallNodeHelper(node, callee);
  }

  @Override
  protected void visitCallDelegateNode(CallDelegateNode node) {

    ExprRootNode variantExpr = node.getDelCalleeVariantExpr();
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
        throw RenderException.createWithSource(
            String.format(
                "Variant expression \"%s\" doesn't evaluate to a valid type "
                    + "(Only string and integer are supported).",
                variantExpr.toSourceString()),
            e,
            node);
      }
    }
    DelTemplateKey delegateKey = DelTemplateKey.create(node.getDelCalleeName(), variant);

    TemplateDelegateNode callee;
    try {
      callee = templateRegistry.selectDelTemplate(delegateKey, activeDelPackageSelector);
    } catch (IllegalArgumentException e) {
      throw RenderException.createWithSource(e.getMessage(), e, node);
    }

    if (callee != null) {
      visitCallNodeHelper(node, callee);

    } else if (node.allowEmptyDefault()) {
      return; // no active delegate implementation, so the call output is empty string

    } else {
      throw RenderException.createWithSource(
          "Found no active impl for delegate call to '"
              + node.getDelCalleeName()
              + "' (and no attribute allowemptydefault=\"true\").",
          node);
    }
  }

  @SuppressWarnings("ConstantConditions") // for IntelliJ
  private void visitCallNodeHelper(CallNode node, TemplateNode callee) {

    // ------ Build the call data. ------
    SoyRecord dataToPass;
    if (node.isPassingAllData()) {
      dataToPass = data;
    } else if (node.isPassingData()) {
      SoyValue dataRefValue = eval(node.getDataExpr(), node);
      if (!(dataRefValue instanceof SoyRecord)) {
        throw RenderException.create(
                "In 'call' command "
                    + node.toSourceString()
                    + ", the data reference does not resolve to a SoyRecord.")
            .addStackTraceElement(node);
      }
      dataToPass = (SoyRecord) dataRefValue;
    } else {
      dataToPass = null;
    }

    SoyRecord callData;

    int numChildren = node.numChildren();
    if (numChildren == 0) {
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
        mutableCallData = new BasicParamStore(numChildren);
      } else {
        // Case 4: Passing data and passing params.
        mutableCallData = new AugmentedParamStore(dataToPass, numChildren);
      }

      for (CallParamNode child : node.getChildren()) {

        if (child instanceof CallParamValueNode) {
          mutableCallData.setField(
              child.getKey().identifier(), lazyEval(((CallParamValueNode) child).getExpr(), child));

        } else if (child instanceof CallParamContentNode) {
          mutableCallData.setField(
              child.getKey().identifier(), renderRenderUnitNode((CallParamContentNode) child));

        } else {
          throw new AssertionError();
        }
      }

      callData = mutableCallData;
    }

    // ------ Render the callee template with the callData built above. ------

    if (node.getEscapingDirectiveNames().isEmpty()) {
      // No escaping at the call site -- render directly into the output buffer.
      RenderVisitor rv = this.createHelperInstance(currOutputBuf, callData);
      try {
        rv.renderTemplate(callee, node.getParamsToRuntimeCheck(callee));
      } catch (RenderException re) {
        // The {call .XXX} failed to render - a new partial stack trace element is added to capture
        // this template call.
        throw re.addStackTraceElement(node);
      }
    } else {
      // Escaping the call site's result, such as at a strict template boundary.
      // TODO: Some optimization is needed here before Strict Soy can be widely used:
      // - Only create this temporary buffer when contexts mismatch. We could run a pre-pass that
      // eliminates escaping directives when all callers are known.
      // - Instead of creating a temporary buffer and copying, wrap with an escaping StringBuilder.
      StringBuilder calleeBuilder = new StringBuilder();
      RenderVisitor rv = this.createHelperInstance(calleeBuilder, callData);
      try {
        rv.renderTemplate(callee, node.getParamsToRuntimeCheck(callee));
      } catch (RenderException re) {
        // The {call .XXX} failed to render - a new partial stack trace element is added to capture
        // this template call.
        throw re.addStackTraceElement(node);
      }
      SoyValue resultData =
          (callee.getContentKind() != null)
              ? UnsafeSanitizedContentOrdainer.ordainAsSafe(
                  calleeBuilder.toString(), callee.getContentKind())
              : StringData.forValue(calleeBuilder.toString());
      for (String directiveName : node.getEscapingDirectiveNames()) {
        resultData = applyDirective(directiveName, resultData, ImmutableList.<SoyValue>of(), node);
      }
      append(currOutputBuf, resultData, node);
    }
  }

  @Override
  protected void visitCallParamNode(CallParamNode node) {
    // In this visitor, we never directly visit a CallParamNode.
    throw new AssertionError();
  }

  @Override
  protected void visitLogNode(LogNode node) {
    renderBlock(node, System.out);
    System.out.println(); // add a newline
  }

  @Override
  protected void visitDebuggerNode(DebuggerNode node) {
    // The 'debugger' statement does nothing in Java rendering, but the user could theoretically
    // place a breakpoint at this method.
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {

    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /** Pushes the given output buffer onto the stack (it becomes the current output buffer). */
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
   * Private helper to render the children of a block into a separate string (not directly appended
   * to the current output buffer).
   *
   * @param block The block whose children are to be rendered.
   */
  private void renderBlock(BlockNode block, Appendable to) {
    pushOutputBuf(to);
    visitChildren(block);
    popOutputBuf();
  }

  private SoyValue renderRenderUnitNode(final RenderUnitNode renderUnitNode) {
    RenderableThunk thunk =
        new RenderableThunk() {
          @Override
          protected void doRender(Appendable appendable) throws IOException {
            renderBlock(renderUnitNode, appendable);
          }
        };
    ContentKind contentKind = renderUnitNode.getContentKind();
    if (contentKind != null) {
      return LazySanitizedContents.forThunk(thunk, contentKind);
    } else {
      return StringData.forThunk(thunk);
    }
  }

  /**
   * Private helper to evaluate an expression. Always use this helper instead of using evalVisitor
   * directly, because this helper creates and throws a RenderException if there's an error.
   */
  private SoyValue eval(ExprNode expr, SoyNode node) {

    if (expr == null) {
      throw RenderException.create("Cannot evaluate expression in V1 syntax.")
          .addStackTraceElement(node);
    }

    // Lazily initialize evalVisitor.
    if (evalVisitor == null) {
      evalVisitor = evalVisitorFactory.create(env, ijData, cssRenamingMap, xidRenamingMap);
    }

    try {
      return evalVisitor.exec(expr);
    } catch (RenderException e) {
      // RenderExceptions can be thrown when evaluating lazy transclusions.
      throw RenderException.createFromRenderException(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage(), e, node);
    } catch (Exception e) {
      throw RenderException.createWithSource(
          "When evaluating \"" + expr.toSourceString() + "\": " + e.getMessage(), e, node);
    }
  }

  /**
   * A lazy wrapper around {@link #eval}.
   *
   * <p>Useful for {@code {let ...}} and {@code {param ...}} commands where the expression may be
   * defined before being used.
   */
  private SoyValueProvider lazyEval(final ExprNode expr, final SoyNode node) {
    return new SoyAbstractCachingValueProvider() {
      @Override
      protected SoyValue compute() {
        return eval(expr, node);
      }

      @Override
      public RenderResult status() {
        return RenderResult.done();
      }
    };
  }

  /**
   * This method must only be called by assistant visitors, in particular
   * RenderVisitorAssistantForMsgs.
   */
  SoyValue evalForUseByAssistants(ExprNode expr, SoyNode node) {
    return eval(expr, node);
  }

  /** Helper to append text to the output, propagating any exceptions. */
  static void append(Appendable outputBuf, CharSequence cs) {
    try {
      outputBuf.append(cs);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Helper to append a SoyValue to the output, propagating any exceptions. */
  static void append(Appendable outputBuf, SoyValue value, SoyNode node) {
    try {
      value.render(outputBuf);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (RenderException e) {
      throw e.addStackTraceElement(node);
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
      throw RenderException.createWithSource(
          "Failed to find Soy print directive with name '"
              + directiveName
              + "'"
              + " (tag "
              + node.toSourceString()
              + ")",
          node);
    }

    // TODO: Add a pass to check num args at compile time.
    if (!directive.getValidArgsSizes().contains(args.size())) {
      throw RenderException.createWithSource(
          "Print directive '"
              + directiveName
              + "' used with the wrong number of arguments (tag "
              + node.toSourceString()
              + ").",
          node);
    }

    try {
      return directive.applyForJava(value, args);
    } catch (RuntimeException e) {
      throw RenderException.createWithSource(
          String.format(
              "Failed in applying directive '%s' in tag \"%s\" due to exception: %s",
              directiveName, node.toSourceString(), e.getMessage()),
          e,
          node);
    }
  }

  private void checkStrictParamTypes(TemplateNode node, ImmutableList<TemplateParam> params) {
    for (TemplateParam param : params) {
      checkStrictParamType(node, param, env.getVarProvider(param));
    }
    for (TemplateParam param : node.getInjectedParams()) {
      checkStrictParamType(node, param, env.getVarProvider(param));
    }
  }

  /** Check that the given {@code paramValue} matches the static type of {@code param}. */
  private void checkStrictParamType(
      final TemplateNode node, final TemplateParam param, @Nullable SoyValueProvider paramValue) {
    Kind kind = param.type().getKind();
    if (kind == Kind.ANY || kind == Kind.UNKNOWN) {
      // Nothing to check.  ANY and UKNOWN match all types.
      return;
    }
    if (paramValue == null) {
      paramValue = NullData.INSTANCE;
    } else if (paramValue instanceof SoyAbstractCachingValueProvider) {
      SoyAbstractCachingValueProvider typedValue = (SoyAbstractCachingValueProvider) paramValue;
      if (!typedValue.isComputed()) {
        // in order to preserve laziness we tell the value provider to assert the type when
        // computation is triggered
        typedValue.addValueAssertion(
            new ValueAssertion() {
              @Override
              public void check(SoyValue value) {
                checkValueType(param, value, node);
              }
            });
        return;
      }
    }
    checkValueType(param, paramValue.resolve(), node);
  }

  /** Check that the value matches the given param type. */
  private void checkValueType(TemplateParam param, SoyValue value, TemplateNode node) {
    if (!param.type().isInstance(value)) {
      // should this be a soydataexception?
      throw RenderException.createWithSource(
          "Parameter type mismatch: attempt to bind value '"
              + (value instanceof UndefinedData ? "(undefined)" : value)
              + "' to parameter '"
              + param.name()
              + "' which has declared type '"
              + param.type()
              + "'.",
          node);
    }
  }
}
