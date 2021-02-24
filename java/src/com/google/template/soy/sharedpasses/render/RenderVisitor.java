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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyAbstractCachingValueProvider;
import com.google.template.soy.data.SoyAbstractCachingValueProvider.ValueAssertion;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyFutureValueProvider;
import com.google.template.soy.data.SoyFutureValueProvider.FutureBlockCallback;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyRecords;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.TofuTemplateValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.RangeArgs;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.shared.internal.SharedRuntime;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
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
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType.Kind;
import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
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

  /** Factory for creating an instance of EvalVisitor. */
  protected final EvalVisitorFactory evalVisitorFactory;

  protected final ImmutableMap<String, TemplateNode> basicTemplates;
  protected final DelTemplateSelector<TemplateDelegateNode> deltemplates;
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

  /** Configures if we should render additional HTML comments for runtime insepction. */
  protected boolean debugSoyTemplateInfo;

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

  /** The runtime instances for functions. */
  private final ImmutableMap<String, Supplier<Object>> pluginInstances;

  /**
   * @param evalVisitorFactory Factory for creating an instance of EvalVisitor.
   * @param outputBuf The Appendable to append the output to.
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param activeDelPackageSelector The predicate for testing whether a given delpackage is active.
   *     Allowed to be null when known to be irrelevant.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   * @param xidRenamingMap The 'xid' renaming map, or null if not applicable.
   * @param pluginInstances The instances used for evaluating functions that call instance methods.
   */
  public RenderVisitor(
      EvalVisitorFactory evalVisitorFactory,
      Appendable outputBuf,
      ImmutableMap<String, TemplateNode> basicTemplates,
      DelTemplateSelector<TemplateDelegateNode> deltemplates,
      SoyRecord data,
      @Nullable SoyRecord ijData,
      @Nullable Predicate<String> activeDelPackageSelector,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      boolean debugSoyTemplateInfo,
      ImmutableMap<String, Supplier<Object>> pluginInstances) {
    checkNotNull(data);

    this.evalVisitorFactory = evalVisitorFactory;
    this.basicTemplates = checkNotNull(basicTemplates);
    this.deltemplates = checkNotNull(deltemplates);
    this.data = data;
    this.ijData = ijData;
    this.activeDelPackageSelector = activeDelPackageSelector;
    this.msgBundle = msgBundle;
    this.xidRenamingMap = (xidRenamingMap == null) ? SoyCssRenamingMap.EMPTY : xidRenamingMap;
    this.cssRenamingMap = (cssRenamingMap == null) ? SoyCssRenamingMap.EMPTY : cssRenamingMap;
    this.debugSoyTemplateInfo = debugSoyTemplateInfo;
    this.pluginInstances = checkNotNull(pluginInstances);

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
        evalVisitorFactory,
        outputBuf,
        basicTemplates,
        deltemplates,
        data,
        ijData,
        activeDelPackageSelector,
        msgBundle,
        xidRenamingMap,
        cssRenamingMap,
        debugSoyTemplateInfo,
        pluginInstances);
  }

  /**
   * This method must only be called by assistant visitors, in particular
   * RenderVisitorAssistantForMsgs.
   */
  void visitForUseByAssistants(SoyNode node) {
    visit(node);
  }

  /** A private helper to render templates with optimized type checking. */
  private void renderTemplate(TemplateNode template) {
    env = Environment.create(template, data, ijData);
    checkStrictParamTypes(template);
    visitChildren(template);
    env = null; // unpin for gc
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.

  @Override
  protected void visitTemplateNode(TemplateNode node) {
    renderTemplate(node);
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
    if (!node.getEscapingDirectives().isEmpty()) {
      // The entire message needs to be escaped, so we need to render to a temporary buffer.
      // Fortunately, for most messages (in HTML context) this is unnecessary.
      pushOutputBuf(new StringBuilder());
    }
    assistantForMsgs.visitForUseByMaster(node);
    if (!node.getEscapingDirectives().isEmpty()) {
      // Escape the entire message with the required directives.
      SoyValue wholeMsg = StringData.forValue(popOutputBuf().toString());
      for (SoyPrintDirective directive : node.getEscapingDirectives()) {
        wholeMsg = applyDirective(directive, wholeMsg, ImmutableList.of(), node);
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
      result = applyDirective(directiveNode.getPrintDirective(), result, argsSoyDatas, node);
    }

    append(currOutputBuf, result, node);
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
  protected void visitForNode(ForNode node) {
    Optional<RangeArgs> exprAsRangeArgs = RangeArgs.createFromNode(node);
    if (exprAsRangeArgs.isPresent()) {
      RangeArgs args = exprAsRangeArgs.get();
      int step = args.increment().isPresent() ? evalRangeArg(node, args.increment().get()) : 1;
      int start = args.start().isPresent() ? evalRangeArg(node, args.start().get()) : 0;
      int end = evalRangeArg(node, args.limit());
      int length = end - start;
      if ((length ^ step) < 0) {
        // sign mismatch, step will never cause start to reach end.
        // handle ifempty, if present
        if (node.numChildren() == 2) {
          visit(node.getChild(1));
        }
      } else {
        ForNonemptyNode child = (ForNonemptyNode) node.getChild(0);
        int size = length / step + (length % step == 0 ? 0 : 1);
        for (int i = 0; i < size; ++i) {
          executeForeachBody(child, i, IntegerData.forValue(start + step * i), size);
        }
      }
    } else {
      SoyValue dataRefValue = eval(node.getExpr(), node);
      if (!(dataRefValue instanceof SoyList)) {
        throw RenderException.createWithSource(
            "In 'for' command "
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
        ForNonemptyNode child = (ForNonemptyNode) node.getChild(0);
        for (int i = 0; i < listLength; ++i) {
          executeForeachBody(child, i, foreachList.getProvider(i), listLength);
        }
      } else {
        // Case 2: Empty list. If the 'ifempty' node exists, visit it.
        if (node.numChildren() == 2) {
          visit(node.getChild(1));
        }
      }
    }
  }

  private void executeForeachBody(ForNonemptyNode child, int i, SoyValueProvider value, int size) {
    env.bindLoopPosition(child.getVar(), value, i, size - 1 == i);
    if (child.getIndexVar() != null) {
      env.bind(child.getIndexVar(), SoyValueConverter.INSTANCE.convert(i));
    }
    visitChildren(child);
  }

  private int evalRangeArg(SoyNode node, ExprNode rangeArg) {
    SoyValue rangeArgValue = eval(rangeArg, node);
    if (!(rangeArgValue instanceof IntegerData)) {
      throw RenderException.create(
              "In 'range' expression \""
                  + rangeArg.toSourceString()
                  + "\" does not resolve to an integer.")
          .addStackTraceElement(
              node.getNearestAncestor(TemplateNode.class), rangeArg.getSourceLocation());
    }
    return rangeArgValue.integerValue();
  }

  @Override
  protected void visitCallBasicNode(CallBasicNode node) {
    TofuTemplateValue calleeExpr = (TofuTemplateValue) eval(node.getCalleeExpr(), node);
    TemplateNode callee = basicTemplates.get(calleeExpr.getTemplateName());
    if (callee == null) {
      throw RenderException.createWithSource(
          "Attempting to render undefined template '" + node.getCalleeName() + "'.", node);
    }

    visitCallNodeHelper(node, callee, calleeExpr.getBoundParameters());
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
    TemplateDelegateNode callee;
    try {
      callee =
          deltemplates.selectTemplate(node.getDelCalleeName(), variant, activeDelPackageSelector);
    } catch (IllegalArgumentException e) {
      throw RenderException.createWithSource(e.getMessage(), e, node);
    }

    if (callee != null) {
      visitCallNodeHelper(node, callee, Optional.empty());

    } else if (node.allowEmptyDefault()) {
      return; // no active delegate implementation, so the call output is empty string

    } else {
      throw RenderException.createWithSource(
          "Found no active impl for delegate call to \""
              + node.getDelCalleeName()
              + (variant.isEmpty() ? "" : ":" + variant)
              + "\" (and delcall does not set allowemptydefault=\"true\").",
          node);
    }
  }

  private void visitCallNodeHelper(
      CallNode node, TemplateNode callee, Optional<SoyRecord> boundParams) {

    // ------ Build the call data. ------
    SoyRecord callData = createCallParams(node);
    if (boundParams.isPresent()) {
      callData = SoyRecords.merge(boundParams.get(), callData);
    }

    // ------ Render the callee template with the callData built above. ------

    if (node.getEscapingDirectives().isEmpty()) {
      // No escaping at the call site -- render directly into the output buffer.
      RenderVisitor rv = this.createHelperInstance(currOutputBuf, callData);
      try {
        rv.renderTemplate(callee);
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
        rv.renderTemplate(callee);
      } catch (RenderException re) {
        // The {call .XXX} failed to render - a new partial stack trace element is added to capture
        // this template call.
        throw re.addStackTraceElement(node);
      }
      ContentKind calleeKind = fromSanitizedContentKind(callee.getContentKind());
      SoyValue resultData =
          calleeKind != ContentKind.TEXT
              ? UnsafeSanitizedContentOrdainer.ordainAsSafe(calleeBuilder.toString(), calleeKind)
              : StringData.forValue(calleeBuilder.toString());
      for (SoyPrintDirective directive : node.getEscapingDirectives()) {
        resultData = applyDirective(directive, resultData, ImmutableList.of(), node);
      }
      append(currOutputBuf, resultData, node);
    }
  }

  private SoyRecord createCallParams(CallNode node) {
    if (node.numChildren() == 0) {
      if (!node.isPassingData()) {
        // Case 1: Not passing data and not passing params.
        return ParamStore.EMPTY_INSTANCE;
      }

      // Case 2: Passing data and not passing params.
      if (!node.isPassingAllData()) {
        return getDataRecord(node);
      }

      ImmutableList<TemplateParam> params = node.getNearestAncestor(TemplateNode.class).getParams();
      ParamStore dataWithDefaults = null;
      // If this is a data="all" call and the caller has default parameters we need to augment the
      // data record to make sure any default parameters are set to the default in the data record.
      for (TemplateParam param : params) {
        if (param.hasDefault() && data.getField(param.name()) == null) {
          if (dataWithDefaults == null) {
            dataWithDefaults = new ParamStore(data, params.size());
          }
          // This could be made more performant by precalculating the default value, but Tofu is
          // legacy so don't worry about.
          dataWithDefaults.setField(param.name(), lazyEval(param.defaultValue(), node));
        }
      }
      return dataWithDefaults == null ? data : dataWithDefaults;
    }

    ParamStore params;
    if (node.isPassingData()) {
      // Case 3: Passing data and passing params.
      SoyRecord dataRecord;
      if (node.isPassingAllData()) {
        dataRecord = data;
      } else {
        dataRecord = getDataRecord(node);
      }
      params = new ParamStore(dataRecord, node.numChildren());
      if (node.isPassingAllData()) {
        for (TemplateParam param : node.getNearestAncestor(TemplateNode.class).getParams()) {
          // If this is a data="all" call and the caller has default parameters we need to augment
          // the params record to make sure any unset default parameters are set to the default in
          // the params record.
          if (param.hasDefault() && params.getField(param.name()) == null) {
            params.setField(param.name(), lazyEval(param.defaultValue(), node));
          }
        }
      }
    } else {
      // Case 4: Not passing data and passing params.
      params = new ParamStore(node.numChildren());
    }

    // --- Cases 3 and 4: Passing params. ---
    for (CallParamNode child : node.getChildren()) {

      if (child instanceof CallParamValueNode) {
        params.setField(
            child.getKey().identifier(), lazyEval(((CallParamValueNode) child).getExpr(), child));

      } else if (child instanceof CallParamContentNode) {
        params.setField(
            child.getKey().identifier(), renderRenderUnitNode((CallParamContentNode) child));

      } else {
        throw new AssertionError();
      }
    }

    return params;
  }

  private SoyRecord getDataRecord(CallNode node) {
    SoyValue dataRefValue = eval(node.getDataExpr(), node);
    if (!(dataRefValue instanceof SoyRecord)) {
      throw RenderException.create(
              "In 'call' command "
                  + node.toSourceString()
                  + ", the data reference does not resolve to a SoyRecord.")
          .addStackTraceElement(node);
    }
    return (SoyRecord) dataRefValue;
  }

  @Override
  protected void visitCallParamNode(CallParamNode node) {
    // In this visitor, we never directly visit a CallParamNode.
    throw new AssertionError();
  }

  @Override
  protected void visitVeLogNode(VeLogNode node) {
    ExprRootNode logonlyExpression = node.getLogonlyExpression();
    if (logonlyExpression != null) {
      if (eval(logonlyExpression, node).booleanValue()) {
        throw RenderException.createWithSource(
            "Cannot set logonly=\"true\" unless there is a logger configured, but tofu doesn't "
                + "support loggers",
            node);
      }
    }
    visitChildren(node);
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

  private SoyValueProvider renderRenderUnitNode(final RenderUnitNode renderUnitNode) {
    return new RenderableThunk(fromSanitizedContentKind(renderUnitNode.getContentKind())) {
      @Override
      protected void doRender(Appendable appendable) throws IOException {
        renderBlock(renderUnitNode, appendable);
      }
    };
  }

  private static ContentKind fromSanitizedContentKind(SanitizedContentKind kind) {
    return ContentKind.valueOf(kind.name());
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
      evalVisitor =
          evalVisitorFactory.create(
              env,
              cssRenamingMap,
              xidRenamingMap,
              msgBundle,
              debugSoyTemplateInfo,
              pluginInstances);
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
   * @param directive The directive to apply
   * @param value The value to apply the directive on.
   * @param args The arguments to the directive.
   * @param node The node with the escaping. Only used for error reporting.
   * @return The result of applying the directive with the given arguments to the given value.
   */
  private SoyValue applyDirective(
      SoyPrintDirective directive, SoyValue value, List<SoyValue> args, SoyNode node) {

    // Get directive.
    if (!(directive instanceof SoyJavaPrintDirective)) {
      throw RenderException.createWithSource(
          "Failed to find Soy print directive with name '"
              + directive
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
              + directive
              + "' used with the wrong number of arguments (tag "
              + node.toSourceString()
              + ").",
          node);
    }

    try {
      return ((SoyJavaPrintDirective) directive).applyForJava(value, args);
    } catch (RuntimeException e) {
      throw RenderException.createWithSource(
          String.format(
              "Failed in applying directive '%s' in tag \"%s\" due to exception: %s",
              directive, node.toSourceString(), e.getMessage()),
          e,
          node);
    }
  }

  private void checkStrictParamTypes(TemplateNode node) {
    for (TemplateParam param : node.getParams()) {
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
      // Nothing to check.  ANY and UNKNOWN match all types.
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
    } else if (param.hasDefault() && paramValue.resolve() instanceof UndefinedData) {
      // Default parameters are undefined if they're unset.
      return;
    }

    SoyValue value;
    try {
      value = paramValue.resolve();
    } catch (Exception e) {
      throw RenderException.createWithSource("failed to evaluate param: " + param.name(), e, node);
    }
    checkValueType(param, value, node);
  }

  /** Check that the value matches the given param type. */
  private void checkValueType(TemplateParam param, SoyValue value, TemplateNode node) {
    if (!TofuTypeChecks.isInstance(param.type(), value, param.nameLocation())) {
      // should this be a soydataexception?
      throw RenderException.createWithSource(
          "Parameter type mismatch: attempt to bind value '"
              + (value instanceof UndefinedData ? "(undefined)" : value)
              + "' (a "
              + value.getClass().getSimpleName()
              + ") to parameter '"
              + param.name()
              + "' which has a declared type of '"
              + param.type()
              + "'.",
          node);
    }
  }
}
