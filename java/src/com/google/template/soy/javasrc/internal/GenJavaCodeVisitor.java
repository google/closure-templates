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

package com.google.template.soy.javasrc.internal;

import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genCoerceBoolean;
import static com.google.template.soy.javasrc.restricted.JavaCodeUtils.genIntegerValue;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.SoyJavaSrcOptions.CodeStyle;
import com.google.template.soy.javasrc.internal.GenJavaExprsVisitor.GenJavaExprsVisitorFactory;
import com.google.template.soy.javasrc.internal.TranslateToJavaExprVisitor.TranslateToJavaExprVisitorFactory;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.JavaExprUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Visitor for generating full Java code (i.e. statements) for parse tree nodes.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * <p> {@link #exec} should be called on a full parse tree. Java source code will be generated for
 * all the Soy files. The return value is a list of strings, each string being the content of one
 * generated Java file (corresponding to one Soy file).
 *
 * @author Kai Huang
 */
class GenJavaCodeVisitor extends AbstractSoyNodeVisitor<String> {


  /** Regex pattern for an integer. */
  private static final Pattern INTEGER = Pattern.compile("-?\\d+");


  /** The options for generating Java source code. */
  private final SoyJavaSrcOptions javaSrcOptions;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsJavaExprsVisitor used by this instance. */
  private final IsComputableAsJavaExprsVisitor isComputableAsJavaExprsVisitor;

  /** The CanInitOutputVarVisitor used by this instance. */
  private final CanInitOutputVarVisitor canInitOutputVarVisitor;

  /** Factory for creating an instance of GenJavaExprsVisitor. */
  private final GenJavaExprsVisitorFactory genJavaExprsVisitorFactory;

  /** Factory for creating an instance of TranslateToJavaExprVisitor. */
  private final TranslateToJavaExprVisitorFactory translateToJavaExprVisitorFactory;

  /** The GenJavaExprsVisitor used by this instance. */
  @VisibleForTesting protected GenJavaExprsVisitor genJavaExprsVisitor;

  /** The JavaCodeBuilder to build the Java code. */
  @VisibleForTesting protected JavaCodeBuilder javaCodeBuilder;

  /** The current stack of replacement Java expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  @VisibleForTesting protected Deque<Map<String, JavaExpr>> localVarTranslations;


  /**
   * @param javaSrcOptions The options for generating Java source code.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJavaExprsVisitor The IsComputableAsJavaExprsVisitor to be used.
   * @param canInitOutputVarVisitor The CanInitOutputVarVisitor to be used.
   * @param genJavaExprsVisitorFactory Factory for creating an instance of GenJavaExprsVisitor.
   * @param translateToJavaExprVisitorFactory Factory for creating an instance of
   *     TranslateToJavaExprVisitor.
   */
  @Inject
  GenJavaCodeVisitor(
      SoyJavaSrcOptions javaSrcOptions, GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJavaExprsVisitor isComputableAsJavaExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenJavaExprsVisitorFactory genJavaExprsVisitorFactory,
      TranslateToJavaExprVisitorFactory translateToJavaExprVisitorFactory) {
    this.javaSrcOptions = javaSrcOptions;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJavaExprsVisitor = isComputableAsJavaExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.genJavaExprsVisitorFactory = genJavaExprsVisitorFactory;
    this.translateToJavaExprVisitorFactory = translateToJavaExprVisitorFactory;
  }


  @Override public String exec(SoyNode node) {
    javaCodeBuilder = new JavaCodeBuilder(javaSrcOptions.getCodeStyle());
    localVarTranslations = null;
    visit(node);
    return javaCodeBuilder.getCode();
  }


  @VisibleForTesting
  @Override protected void visit(SoyNode node) {
    super.visit(node);
  }


  @Override protected void visitChildren(ParentSoyNode<?> node) {

    // If the block is empty or if the first child cannot initilize the output var, we must
    // initialize the output var.
    if (node.numChildren() == 0 || !canInitOutputVarVisitor.exec(node.getChild(0))) {
      javaCodeBuilder.initOutputVarIfNecessary();
    }

    List<JavaExpr> consecChildrenJavaExprs = Lists.newArrayList();

    for (SoyNode child : node.getChildren()) {

      if (isComputableAsJavaExprsVisitor.exec(child)) {
        consecChildrenJavaExprs.addAll(genJavaExprsVisitor.exec(child));

      } else {
        // We've reached a child that is not computable as Java expressions.

        // First add the JavaExprs from preceding consecutive siblings that are computable as Java
        // expressions (if any).
        if (consecChildrenJavaExprs.size() > 0) {
          javaCodeBuilder.addToOutputVar(consecChildrenJavaExprs);
          consecChildrenJavaExprs.clear();
        }

        // Now append the code for this child.
        visit(child);
      }
    }

    // Add the JavaExprs from the last few children (if any).
    if (consecChildrenJavaExprs.size() > 0) {
      javaCodeBuilder.addToOutputVar(consecChildrenJavaExprs);
      consecChildrenJavaExprs.clear();
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {

    boolean isFirst = true;
    for (SoyFileNode soyFile : node.getChildren()) {
      if (isFirst) {
        isFirst = false;
      } else {
        javaCodeBuilder.appendLine().appendLine();
      }
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.setFilePath(soyFile.getFilePath());
      }
    }
  }


  /**
   * Example:
   * <pre>
   * // -----------------------------------------------------------------------------
   * // The functions below were generated from my_soy_file.soy.
   *
   * ...
   * </pre>
   */
  @Override protected void visitSoyFileNode(SoyFileNode node) {

    javaCodeBuilder.appendLine(
        "// ----------------------------------------------------------------------------- ");
    javaCodeBuilder.appendLine(
        "// The functions below were generated from ", node.getFileName(), ".");

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      javaCodeBuilder.appendLine().appendLine();
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.setTemplateName(template.getTemplateNameForUserMsgs());
      }
    }
  }


  /**
   * Example:
   * <pre>
   * my.func = function(opt_data, opt_sb) {
   *   var output = opt_sb || new soy.StringBuilder();
   *   ...
   *   ...
   *   if (!opt_sb) return output.toString();
   * };
   * </pre>
   */
  @Override protected void visitTemplateNode(TemplateNode node) {

    boolean isCodeStyleStringbuilder = javaSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER;

    localVarTranslations = new ArrayDeque<Map<String, JavaExpr>>();
    genJavaExprsVisitor = genJavaExprsVisitorFactory.create(localVarTranslations);

    boolean isPrivate = node.isPrivate();
    String modifiers;
    boolean shouldReturn;
    String params;

    if (isPrivate) {
      modifiers = "private";
    } else {
      modifiers = "public";
    }

    if (isCodeStyleStringbuilder && isPrivate) {
      params = "com.google.template.soy.data.SoyMapData data, StringBuilder output";
      shouldReturn = false;
    } else {
      if (isCodeStyleStringbuilder) {
        params = "com.google.template.soy.data.SoyMapData data, StringBuilder sb";
      } else {
        params = "com.google.template.soy.data.SoyMapData data";
      }
      shouldReturn = true;
    }

    javaCodeBuilder.appendLine(
        modifiers,
        shouldReturn ? " String " : " void ",
        node.getTemplateName().replace('.', '$'),
        "(" + params + ") {");
    javaCodeBuilder.increaseIndent();
    localVarTranslations.push(Maps.<String, JavaExpr>newHashMap());

    if (!isCodeStyleStringbuilder && isComputableAsJavaExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as Java
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the Java expressions and return the result.

      List<JavaExpr> templateBodyJavaExprs = genJavaExprsVisitor.exec(node);
      JavaExpr templateBodyJavaExpr = JavaExprUtils.concatJavaExprs(templateBodyJavaExprs);
      javaCodeBuilder.appendLine("return ", templateBodyJavaExpr.getText(), ";");

    } else {
      // Case 2: Normal case.

      javaCodeBuilder.pushOutputVar("output");
      if (isCodeStyleStringbuilder) {
        if (!isPrivate) {
          javaCodeBuilder.appendLine(
              "StringBuilder output = (sb != null) ? sb : new StringBuilder();");
        }
        javaCodeBuilder.setOutputVarInited();
      }

      visitChildren(node);

      if (shouldReturn) {
        if (isCodeStyleStringbuilder) {
          javaCodeBuilder.appendLine("return (sb != null) ? null : output.toString();");
        } else {
          javaCodeBuilder.appendLine("return output;");
        }
      }
      javaCodeBuilder.popOutputVar();
    }

    localVarTranslations.pop();
    javaCodeBuilder.decreaseIndent();
    javaCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {let $boo = ...}
   * </pre>
   * might generate
   * <pre>
   *   final com.google.template.soy.data.SoyData boo35 = ...;
   * </pre>
   */
  @Override protected void visitLetValueNode(LetValueNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    JavaExpr valueJavaExpr =
        translateToJavaExprVisitorFactory.create(localVarTranslations).exec(node.getValueExpr());
    javaCodeBuilder.appendLine(
        "final com.google.template.soy.data.SoyData ", generatedVarName, " = ",
        valueJavaExpr.getText(), ";");

    // Add a mapping for generating future references to this local var.
    localVarTranslations.peek().put(
        node.getVarName(), new JavaExpr(generatedVarName, SoyData.class, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {let $boo}
   *     Hello {$name}
   *   {/let}
   * </pre>
   * might generate
   * <pre>
   *   final com.google.template.soy.data.SoyData boo35 = ...;
   * </pre>
   */
  @Override protected void visitLetContentNode(LetContentNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    localVarTranslations.push(Maps.<String, JavaExpr>newHashMap());
    javaCodeBuilder.pushOutputVar(generatedVarName);

    visitChildren(node);

    javaCodeBuilder.popOutputVar();
    localVarTranslations.pop();

    // Add a mapping for generating future references to this local var.
    localVarTranslations.peek().put(
        node.getVarName(), new JavaExpr(generatedVarName, SoyData.class, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {if $boo.foo &gt; 0}
   *     ...
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   if (opt_data.boo.foo &gt; 0) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitIfNode(IfNode node) {

    if (isComputableAsJavaExprsVisitor.exec(node)) {
      javaCodeBuilder.addToOutputVar(genJavaExprsVisitor.exec(node));
      return;
    }

    // ------ Not computable as Java expressions, so generate full code. ------

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        JavaExpr condJavaExpr =
            translateToJavaExprVisitorFactory.create(localVarTranslations)
                .exec(icn.getExprUnion().getExpr());
        if (icn.getCommandName().equals("if")) {
          javaCodeBuilder.appendLine("if (", genCoerceBoolean(condJavaExpr), ") {");
        } else {  // "elseif" block
          javaCodeBuilder.appendLine("} else if (", genCoerceBoolean(condJavaExpr), ") {");
        }

        javaCodeBuilder.increaseIndent();
        visit(icn);
        javaCodeBuilder.decreaseIndent();

      } else if (child instanceof IfElseNode) {
        IfElseNode ien = (IfElseNode) child;

        javaCodeBuilder.appendLine("} else {");

        javaCodeBuilder.increaseIndent();
        visit(ien);
        javaCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    javaCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {switch $boo}
   *     {case 0}
   *       ...
   *     {case 1, 2}
   *       ...
   *     {default}
   *       ...
   *   {/switch}
   * </pre>
   * might generate
   * <pre>
   *   switch (opt_data.boo) {
   *     case 0:
   *       ...
   *       break;
   *     case 1:
   *     case 2:
   *       ...
   *       break;
   *     default:
   *       ...
   *   }
   * </pre>
   */
  @Override protected void visitSwitchNode(SwitchNode node) {

    TranslateToJavaExprVisitor ttjev =
        translateToJavaExprVisitorFactory.create(localVarTranslations);

    JavaExpr switchValueJavaExpr = ttjev.exec(node.getExpr());
    String switchValueVarName = "switchValue" + node.getId();
    javaCodeBuilder.appendLine(
        "com.google.template.soy.data.SoyData ", switchValueVarName, " = ",
        switchValueJavaExpr.getText(), ";");

    boolean isFirstCase = true;
    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;

        StringBuilder conditionExprText = new StringBuilder();
        boolean isFirstCaseValue = true;
        for (ExprNode caseExpr : scn.getExprList()) {
          JavaExpr caseJavaExpr = ttjev.exec(caseExpr);
          if (isFirstCaseValue) {
            isFirstCaseValue = false;
          } else {
            conditionExprText.append(" || ");
          }
          conditionExprText.append(switchValueVarName).append(".equals(")
              .append(caseJavaExpr.getText()).append(")");
        }

        if (isFirstCase) {
          isFirstCase = false;
          javaCodeBuilder.appendLine("if (", conditionExprText.toString(), ") {");
        } else {
          javaCodeBuilder.appendLine("} else if (", conditionExprText.toString(), ") {");
        }

        javaCodeBuilder.increaseIndent();
        visit(scn);
        javaCodeBuilder.decreaseIndent();

      } else if (child instanceof SwitchDefaultNode) {
        SwitchDefaultNode sdn = (SwitchDefaultNode) child;

        javaCodeBuilder.appendLine("} else {");

        javaCodeBuilder.increaseIndent();
        visit(sdn);
        javaCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    javaCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {ifempty}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   var fooList2 = opt_data.boo.foos;
   *   var fooListLen2 = fooList2.length;
   *   if (fooListLen2 > 0) {
   *     ...
   *   } else {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNode(ForeachNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String nodeId = Integer.toString(node.getId());
    String listVarName = baseVarName + "List" + nodeId;
    String listLenVarName = baseVarName + "ListLen" + nodeId;

    // Define list var and list-len var.
    JavaExpr dataRefJavaExpr =
        translateToJavaExprVisitorFactory.create(localVarTranslations).exec(node.getExpr());
    javaCodeBuilder.appendLine(
        "com.google.template.soy.data.SoyListData ", listVarName,
        " = (com.google.template.soy.data.SoyListData) ", dataRefJavaExpr.getText(), ";");
    javaCodeBuilder.appendLine("int ", listLenVarName, " = ", listVarName, ".length();");

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    if (hasIfemptyNode) {
      javaCodeBuilder.appendLine("if (", listLenVarName, " > 0) {");
      javaCodeBuilder.increaseIndent();
    }

    // Generate code for nonempty case.
    visit(node.getChild(0));

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      javaCodeBuilder.decreaseIndent();
      javaCodeBuilder.appendLine("} else {");
      javaCodeBuilder.increaseIndent();

      // Generate code for empty case.
      visit(node.getChild(1));

      javaCodeBuilder.decreaseIndent();
      javaCodeBuilder.appendLine("}");
    }
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   for (var fooIndex2 = 0; fooIndex2 &lt; fooListLen2; fooIndex2++) {
   *     var fooData2 = fooList2[fooIndex2];
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String foreachNodeId = Integer.toString(node.getForeachNodeId());
    String listVarName = baseVarName + "List" + foreachNodeId;
    String listLenVarName = baseVarName + "ListLen" + foreachNodeId;
    String indexVarName = baseVarName + "Index" + foreachNodeId;
    String dataVarName = baseVarName + "Data" + foreachNodeId;

    // The start of the Java 'for' loop.
    javaCodeBuilder.appendLine("for (int ", indexVarName, " = 0; ",
                             indexVarName, " < ", listLenVarName, "; ",
                             indexVarName, "++) {");
    javaCodeBuilder.increaseIndent();
    javaCodeBuilder.appendLine(
        "com.google.template.soy.data.SoyData ", dataVarName, " = ",
        listVarName, ".get(", indexVarName, ");");

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, JavaExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        baseVarName,
        new JavaExpr(dataVarName,
                     SoyData.class, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isFirst",
        new JavaExpr("com.google.template.soy.data.restricted.BooleanData.forValue(" +
                     indexVarName + " == 0)",
                     BooleanData.class, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isLast",
        new JavaExpr("com.google.template.soy.data.restricted.BooleanData.forValue(" +
                     indexVarName + " == " + listLenVarName + " - 1)",
                     BooleanData.class, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__index",
        new JavaExpr("com.google.template.soy.data.restricted.IntegerData.forValue(" +
                     indexVarName + ")",
                     IntegerData.class, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the Java 'for' loop.
    javaCodeBuilder.decreaseIndent();
    javaCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {for $i in range(1, $boo)}
   *     ...
   *   {/for}
   * </pre>
   * might generate
   * <pre>
   *   var iLimit4 = opt_data.boo;
   *   for (var i4 = 1; i4 &lt; iLimit4; i4++) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForNode(ForNode node) {

    String varName = node.getVarName();
    String nodeId = Integer.toString(node.getId());

    TranslateToJavaExprVisitor ttjev =
        translateToJavaExprVisitorFactory.create(localVarTranslations);

    // Get the Java expression text for the init/limit/increment values.
    List<ExprRootNode<?>> rangeArgs = Lists.newArrayList(node.getRangeArgs());
    String incrementJavaExprText = (rangeArgs.size() == 3) ?
        genIntegerValue(ttjev.exec(rangeArgs.remove(2))) : "1" /* default */;
    String initJavaExprText = (rangeArgs.size() == 2) ?
        genIntegerValue(ttjev.exec(rangeArgs.remove(0))) : "0" /* default */;
    String limitJavaExprText = genIntegerValue(ttjev.exec(rangeArgs.get(0)));

    // If any of the Java exprs for init/limit/increment isn't an integer, precompute its value.
    String initCode;
    if (INTEGER.matcher(initJavaExprText).matches()) {
      initCode = initJavaExprText;
    } else {
      initCode = varName + "Init" + nodeId;
      javaCodeBuilder.appendLine("int ", initCode, " = ", initJavaExprText, ";");
    }

    String limitCode;
    if (INTEGER.matcher(limitJavaExprText).matches()) {
      limitCode = limitJavaExprText;
    } else {
      limitCode = varName + "Limit" + nodeId;
      javaCodeBuilder.appendLine("int ", limitCode, " = ", limitJavaExprText, ";");
    }

    String incrementCode;
    if (INTEGER.matcher(incrementJavaExprText).matches()) {
      incrementCode = incrementJavaExprText;
    } else {
      incrementCode = varName + "Increment" + nodeId;
      javaCodeBuilder.appendLine("int ", incrementCode, " = ", incrementJavaExprText, ";");
    }

    // The start of the Java 'for' loop.
    String incrementStmt =
        (incrementCode.equals("1")) ? varName + nodeId + "++"
                                    : varName + nodeId + " += " + incrementCode;
    javaCodeBuilder.appendLine("for (int ",
                             varName, nodeId, " = ", initCode, "; ",
                             varName, nodeId, " < ", limitCode, "; ",
                             incrementStmt,
                             ") {");
    javaCodeBuilder.increaseIndent();

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, JavaExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        varName,
        new JavaExpr("com.google.template.soy.data.restricted.IntegerData.forValue(" +
                     varName + nodeId + ")",
                     IntegerData.class, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the Java 'for' loop.
    javaCodeBuilder.decreaseIndent();
    javaCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {call name="some.func" data="all" /}
   *   {call name="some.func" data="$boo.foo" /}
   *   {call name="some.func"}
   *     {param key="goo" value="88" /}
   *   {/call}
   *   {call name="some.func" data="$boo"}
   *     {param key="goo"}
   *       Hello {$name}
   *     {/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   output += some.func(opt_data);
   *   output += some.func(opt_data.boo.foo);
   *   output += some.func({goo: 88});
   *   output += some.func(soy.$$augmentData(opt_data.boo, {goo: 'Hello ' + opt_data.name});
   * </pre>
   */
  @Override protected void visitCallNode(CallNode node) {

    // If this node has any CallParamContentNode children those contents are not computable as Java
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJavaExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    if (javaSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      // For 'stringbuilder' code style, pass the current output var to collect the call's output.
      if (! (node instanceof CallBasicNode)) {
        throw new UnsupportedOperationException("Delegates are not supported in JavaSrc backend.");
      }
      JavaExpr objToPass = genCallCodeUtils.genObjToPass(node, localVarTranslations);
      javaCodeBuilder.indent()
          .append(((CallBasicNode) node).getCalleeName().replace('.', '$'))
          .append("(", objToPass.getText(), ", ").appendOutputVarName().append(");\n");

    } else {
      // For 'concat' code style, we simply add the call's result to the current output var.
      JavaExpr callExpr = genCallCodeUtils.genCallExpr(node, localVarTranslations);
      javaCodeBuilder.addToOutputVar(ImmutableList.of(callExpr));
    }
  }


  @Override protected void visitCallParamContentNode(CallParamContentNode node) {

    // This node should only be visited when it's not computable as Java expressions, because this
    // method just generates the code to define the temporary 'param<n>' variable.
    if (isComputableAsJavaExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'param<n>' when not computable as Java expressions.");
    }

    localVarTranslations.push(Maps.<String, JavaExpr>newHashMap());
    javaCodeBuilder.pushOutputVar("param" + node.getId());

    visitChildren(node);

    javaCodeBuilder.popOutputVar();
    localVarTranslations.pop();
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ParentSoyNode<?>) {

      if (node instanceof BlockNode) {
        localVarTranslations.push(Maps.<String, JavaExpr>newHashMap());
        visitChildren((BlockNode) node);
        localVarTranslations.pop();

      } else {
        visitChildren((ParentSoyNode<?>) node);
      }

      return;
    }

    if (isComputableAsJavaExprsVisitor.exec(node)) {
      // Simply generate Java expressions for this node and add them to the current output var.
      javaCodeBuilder.addToOutputVar(genJavaExprsVisitor.exec(node));

    } else {
      // Need to implement visit*Node() for the specific case.
      throw new UnsupportedOperationException();
    }
  }

}
