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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.internal.TranslateToPyExprVisitor.TranslateToPyExprVisitorFactory;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.shared.internal.FindCalleesNotInFileVisitor;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PyBidiIsRtlFn;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PyRuntimePath;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.PyTranslationClass;
import com.google.template.soy.sharedpasses.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Visitor for generating full Python code (i.e. statements) for parse tree nodes.
 *
 * <p> {@link #exec} should be called on a full parse tree. Python source code will be generated
 * for all the Soy files. The return value is a list of strings, each string being the content of
 * one generated Python file (corresponding to one Soy file).
 *
 */
final class GenPyCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {

  /** The module path for the runtime libraries. */
  private final String runtimePath;

  /** The module and function name for the bidi isRtl function. */
  private final String bidiIsRtlFn;

  /** The module and class name for the translation class used at runtime. */
  private final String translationClass;

  /** The contents of the generated Python files. */
  private List<String> pyFilesContents;

  @VisibleForTesting protected PyCodeBuilder pyCodeBuilder;

  private final IsComputableAsPyExprVisitor isComputableAsPyExprVisitor;

  private final GenPyExprsVisitorFactory genPyExprsVisitorFactory;

  @VisibleForTesting protected GenPyExprsVisitor genPyExprsVisitor;

  private final TranslateToPyExprVisitorFactory translateToPyExprVisitorFactory;

  private final GenPyCallExprVisitor genPyCallExprVisitor;

  /**
   * @see LocalVariableStack
   */
  @VisibleForTesting protected LocalVariableStack localVarExprs;

  /**
   * @param runtimePath The module path for the runtime libraries.
   * @param translationClass Python class path used in python runtime to execute translation.
   */
  @Inject
  GenPyCodeVisitor(@PyRuntimePath String runtimePath,
      @PyBidiIsRtlFn String bidiIsRtlFn,
      @PyTranslationClass String translationClass,
      IsComputableAsPyExprVisitor isComputableAsPyExprVisitor,
      GenPyExprsVisitorFactory genPyExprsVisitorFactory,
      TranslateToPyExprVisitorFactory translateToPyExprVisitorFactory,
      GenPyCallExprVisitor genPyCallExprVisitor,
      ErrorReporter errorReporter) {
    super(errorReporter);
    this.runtimePath = runtimePath;
    this.bidiIsRtlFn = bidiIsRtlFn;
    this.translationClass = translationClass;
    this.isComputableAsPyExprVisitor = isComputableAsPyExprVisitor;
    this.genPyExprsVisitorFactory = genPyExprsVisitorFactory;
    this.translateToPyExprVisitorFactory = translateToPyExprVisitorFactory;
    this.genPyCallExprVisitor = genPyCallExprVisitor;
  }


  @Override public List<String> exec(SoyNode node) {
    pyFilesContents = new ArrayList<>();
    pyCodeBuilder = null;
    genPyExprsVisitor = null;
    localVarExprs = null;
    visit(node);
    return pyFilesContents;
  }

  @VisibleForTesting void visitForTesting(SoyNode node) {
    visit(node);
  }

  /**
   * Visit all the children of a provided node and combine the results into one expression where
   * possible. This will let us avoid some {@code output.append} calls and save a bit of time.
   */
  @Override protected void visitChildren(ParentSoyNode<?> node) {
    // If the first child cannot be written as an expression, we need to init the output variable
    // first or face potential scoping issues with the output variable being initialized too late.
    if (node.numChildren() > 0 && !isComputableAsPyExprVisitor.exec(node.getChild(0))) {
      pyCodeBuilder.initOutputVarIfNecessary();
    }

    List<PyExpr> childPyExprs = new ArrayList<>();

    for (SoyNode child : node.getChildren()) {
      if (isComputableAsPyExprVisitor.exec(child)) {
        childPyExprs.addAll(genPyExprsVisitor.exec(child));
      } else {
        // We've reached a child that is not computable as a Python expression.
        // First add the PyExprs from preceding consecutive siblings that are computable as Python
        // expressions (if any).
        if (!childPyExprs.isEmpty()) {
          pyCodeBuilder.addToOutputVar(childPyExprs);
          childPyExprs.clear();
        }
        // Now append the code for this child.
        visit(child);
      }
    }

    // Add the PyExprs from the last few children (if any).
    if (!childPyExprs.isEmpty()) {
      pyCodeBuilder.addToOutputVar(childPyExprs);
      childPyExprs.clear();
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.associateMetaInfo(null, soyFile.getFilePath(), null);
      }
    }
  }

  /**
   * Visit a SoyFileNode and generate it's Python output.
   *
   * <p>This visitor generates the necessary imports and configuration needed for all Python output
   * files. This includes imports of runtime libraries, external templates called from within this
   * file, and namespacing configuration.
   *
   * <p>Template generation is deferred to other visitors.
   *
   * <p>Example Output:
   * <pre>
   * # coding=utf-8
   * """ This file was automatically generated from my-templates.soy.
   * Please don't edit this file by hand.
   * """
   *
   * ...
   * </pre>
   */
  @Override protected void visitSoyFileNode(SoyFileNode node) {

    if (node.getSoyFileKind() != SoyFileKind.SRC) {
      return;  // don't generate code for deps
    }

    pyCodeBuilder = new PyCodeBuilder();

    // Encode all source files in utf-8 to allow for special unicode characters in the generated
    // literals.
    pyCodeBuilder.appendLine("# coding=utf-8");

    pyCodeBuilder.appendLine("\"\"\" This file was automatically generated from ",
        node.getFileName(), ".");
    pyCodeBuilder.appendLine("Please don't edit this file by hand.");

    // Output a section containing optionally-parsed compiler directives in comments.
    pyCodeBuilder.appendLine();
    if (node.getNamespace() != null) {
      pyCodeBuilder.appendLine(
          "Templates in namespace ", node.getNamespace(), ".");
    }
    pyCodeBuilder.appendLine("\"\"\"");

    // Add code to define Python namespaces and add import calls for libraries.
    pyCodeBuilder.appendLine();
    addCodeToRequireGeneralDeps();
    addCodeToRequireSoyNamespaces(node);
    addCodeToRegisterCurrentSoyNamespace(node);
    addCodeToFixUnicodeStrings();

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      pyCodeBuilder.appendLine().appendLine();
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.associateMetaInfo(null, null, template.getTemplateNameForUserMsgs());
      }
    }

    pyFilesContents.add(pyCodeBuilder.getCode());
    pyCodeBuilder = null;
  }

  /**
   * Visit a TemplateNode and generate a corresponding function.
   *
   * <p>Example:
   * <pre>
   * def myfunc(opt_data=None, opt_ijData=None):
   *   output = ''
   *   ...
   *   ...
   *   return output
   * </pre>
   */
  @Override protected void visitTemplateNode(TemplateNode node) {
    localVarExprs = new LocalVariableStack();
    genPyExprsVisitor = genPyExprsVisitorFactory.create(localVarExprs);

    // Generate function definition up to colon.
    pyCodeBuilder.appendLine(
        "def ",
        node.getPartialTemplateName().substring(1),
        "(opt_data=None, opt_ijData=None):");
    pyCodeBuilder.increaseIndent();

    generateFunctionBody(node);

    // Dedent to end the function.
    pyCodeBuilder.decreaseIndent();
  }

  /**
   * Visit a TemplateDelegateNode and generate the corresponding function along with the delegate
   * registration.
   *
   * <p>Example:
   * <pre>
   * def myfunc(opt_data=None, opt_ijData=None):
   *   ...
   * runtime.register_delegate_fn('delname', 'delvariant', 0, myfunc, 'myfunc')
   * </pre>
   */
  @Override protected void visitTemplateDelegateNode(TemplateDelegateNode node) {
    // Generate the template first, before registering the delegate function.
    visitTemplateNode(node);

    // Register the function as a delegate function.
    String delTemplateIdExprText = "'" + node.getDelTemplateName() + "'";
    String delTemplateVariantExprText = "'" + node.getDelTemplateVariant() + "'";
    pyCodeBuilder.appendLine(
        "runtime.register_delegate_fn(",
        delTemplateIdExprText, ", ", delTemplateVariantExprText, ", ",
        Integer.toString(node.getDelPriority()), ", ",
        node.getPartialTemplateName().substring(1), ", '",
        node.getPartialTemplateName().substring(1), "')");
  }

  @Override protected void visitPrintNode(PrintNode node) {
    pyCodeBuilder.addToOutputVar(genPyExprsVisitor.exec(node));
  }

  /**
   * Visit an IfNode and generate a full conditional statement, or an inline ternary conditional
   * expression if all the children are computable as expressions.
   *
   * <p>Example:
   * <pre>
   *   {if $boo > 0}
   *     ...
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   if opt_data.get('boo') > 0:
   *     ...
   * </pre>
   */
  @Override protected void visitIfNode(IfNode node) {
    if (isComputableAsPyExprVisitor.exec(node)) {
      pyCodeBuilder.addToOutputVar(genPyExprsVisitor.exec(node));
      return;
    }

    // Not computable as Python expressions, so generate full code.
    TranslateToPyExprVisitor translator = translateToPyExprVisitorFactory.create(localVarExprs);
    for (SoyNode child : node.getChildren()) {
      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;
        PyExpr condPyExpr = translator.exec(icn.getExprUnion().getExpr());

        if (icn.getCommandName().equals("if")) {
          pyCodeBuilder.appendLine("if ", condPyExpr.getText(), ":");
        } else {
          pyCodeBuilder.appendLine("elif ", condPyExpr.getText(), ":");
        }

        pyCodeBuilder.increaseIndent();
        visitChildren(icn);
        pyCodeBuilder.decreaseIndent();

      } else if (child instanceof IfElseNode) {
        pyCodeBuilder.appendLine("else:");
        pyCodeBuilder.increaseIndent();
        visitChildren((IfElseNode) child);
        pyCodeBuilder.decreaseIndent();
      } else {
        throw new AssertionError("Unexpected if child node type. Child: " + child);
      }
    }
  }

  /**
   * Python does not support switch statements, so just replace with if: ... elif: ... else: ...
   * As some expressions may generate different results each time, the expression is stored before
   * conditionals (which prevents expression inlining).
   *
   * <p>Example:
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
   *   switchValue = opt_data.get('boo')
   *   if switchValue == 0:
   *     ...
   *   elif switchValue == 1:
   *     ...
   *   elif switchValue == 2:
   *     ...
   *   else:
   *     ...
   * </pre>
   */
  @Override protected void visitSwitchNode(SwitchNode node) {
    // Run the switch value creation first to ensure side effects always occur.
    TranslateToPyExprVisitor translator = translateToPyExprVisitorFactory.create(localVarExprs);
    String switchValueVarName = "switchValue";
    PyExpr switchValuePyExpr = translator.exec(node.getExpr());
    pyCodeBuilder.appendLine(switchValueVarName, " = ", switchValuePyExpr.getText());

    // If a Switch with only a default is provided (no case statements), just execute the inner
    // code directly.
    if (node.getChildren().size() == 1 && node.getChild(0) instanceof SwitchDefaultNode) {
      visitChildren((SwitchDefaultNode) node.getChild(0));
      return;
    }

    boolean isFirstCase = true;
    for (SoyNode child : node.getChildren()) {
      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;

        for (ExprNode caseExpr : scn.getExprList()) {
          PyExpr casePyExpr = translator.exec(caseExpr);
          PyExpr conditionFn = new PyFunctionExprBuilder("runtime.type_safe_eq")
              .addArg(new PyExpr(switchValueVarName, Integer.MAX_VALUE))
              .addArg(casePyExpr).asPyExpr();

          if (isFirstCase) {
            pyCodeBuilder.appendLineStart("if ").append(conditionFn.getText()).appendLineEnd(":");
            isFirstCase = false;
          } else {
            pyCodeBuilder.appendLineStart("elif ").append(conditionFn.getText()).appendLineEnd(":");
          }

          pyCodeBuilder.increaseIndent();
          visitChildren(scn);
          pyCodeBuilder.decreaseIndent();
        }
      } else if (child instanceof SwitchDefaultNode) {
        SwitchDefaultNode sdn = (SwitchDefaultNode) child;

        pyCodeBuilder.appendLine("else:");
        pyCodeBuilder.increaseIndent();
        visitChildren(sdn);
        pyCodeBuilder.decreaseIndent();
      } else {
        throw new AssertionError("Unexpected switch child node type. Child: " + child);
      }
    }
  }

  /**
   * Visits a ForNode and generates a for loop over a given range.
   *
   * <p>Example:
   * <pre>
   *   {for $i in range(1, $boo)}
   *     ...
   *   {/for}
   * </pre>
   * might generate
   * <pre>
   *   for i4 in xrange(1, opt_data.get('boo')):
   *     ...
   * </pre>
   */
  @Override protected void visitForNode(ForNode node) {
    TranslateToPyExprVisitor translator = translateToPyExprVisitorFactory.create(localVarExprs);

    String varName = node.getVarName();
    String nodeId = Integer.toString(node.getId());

    // The start of the Python 'for' loop.
    pyCodeBuilder.appendLineStart("for ", varName,  nodeId, " in ");

    // Build the xrange call. Since the Python param syntax matches Soy range syntax, params can be
    // directly dropped in.
    PyFunctionExprBuilder funcBuilder = new PyFunctionExprBuilder("xrange");
    for (ExprUnion arg : node.getAllExprUnions()) {
      funcBuilder.addArg(translator.exec(arg.getExpr()));
    }

    pyCodeBuilder.appendLineEnd(funcBuilder.asPyExpr().getText(), ":");

    // Add a new localVarExprs frame and populate it with the translations from this node.
    localVarExprs.pushFrame();
    localVarExprs.addVariable(varName, new PyExpr(varName + nodeId, Integer.MAX_VALUE));

    // Generate the code for the loop body.
    pyCodeBuilder.increaseIndent();
    visitChildren(node);
    pyCodeBuilder.decreaseIndent();

    // Remove the localVarTranslations frame that we added above.
    localVarExprs.popFrame();
  }

  /**
   * The top level ForeachNode primarily serves to test for the ifempty case. If present, the loop
   * is wrapped in an if statement which checks for data in the list before iterating.
   *
   * <p>Example:
   * <pre>
   *   {foreach $foo in $boo}
   *     ...
   *   {ifempty}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   fooList2 = opt_data.get('boo')
   *   if fooList2:
   *     ...loop...
   *   else:
   *     ...
   * </pre>
   */
  @Override protected void visitForeachNode(ForeachNode node) {
    // Build the local variable names.
    ForeachNonemptyNode nonEmptyNode = (ForeachNonemptyNode) node.getChild(0);
    String baseVarName = nonEmptyNode.getVarName();
    String listVarName = String.format("%sList%d", baseVarName, node.getId());

    // Define list variable
    TranslateToPyExprVisitor translator = translateToPyExprVisitorFactory.create(localVarExprs);
    PyExpr dataRefPyExpr = translator.exec(node.getExpr());
    pyCodeBuilder.appendLine(listVarName, " = ", dataRefPyExpr.getText());

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    if (hasIfemptyNode) {
      // Empty lists are falsy in Python.
      pyCodeBuilder.appendLine("if ", listVarName, ":");
      pyCodeBuilder.increaseIndent();
    }

    // Generate code for nonempty case.
    visit(nonEmptyNode);

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      pyCodeBuilder.decreaseIndent();
      pyCodeBuilder.appendLine("else:");
      pyCodeBuilder.increaseIndent();

      // Generate code for empty case.
      visit(node.getChild(1));

      pyCodeBuilder.decreaseIndent();
    }
  }


  /**
   * The ForeachNonemptyNode performs the actual looping. We use a standard {@code for} loop, except
   * that instead of looping directly over the list, we loop over an enumeration to have easy access
   * to the index along with the data.
   *
   * <p>Example:
   * <pre>
   *   {foreach $foo in $boo}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   fooList2 = opt_data.get('boo')
   *   for fooIndex2, fooData2 in enumerate(fooList2):
   *     ...
   * </pre>
   */
  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {
    // Build the local variable names.
    String baseVarName = node.getVarName();
    String foreachNodeId = Integer.toString(node.getForeachNodeId());
    String listVarName = baseVarName + "List" + foreachNodeId;
    String indexVarName = baseVarName + "Index" + foreachNodeId;
    String dataVarName = baseVarName + "Data" + foreachNodeId;

    // Create the loop with an enumeration.
    pyCodeBuilder.appendLine("for ", indexVarName, ", ", dataVarName,
        " in enumerate(", listVarName, "):");
    pyCodeBuilder.increaseIndent();

    // Add a new localVarExprs frame and populate it with the translations from this loop.
    int eqPrecedence = PyExprUtils.pyPrecedenceForOperator(Operator.EQUAL);
    localVarExprs.pushFrame();
    localVarExprs.addVariable(baseVarName, new PyExpr(dataVarName, Integer.MAX_VALUE))
        .addVariable(baseVarName + "__isFirst", new PyExpr(indexVarName + " == 0", eqPrecedence))
        .addVariable(baseVarName + "__isLast",
            new PyExpr(indexVarName + " == len(" + listVarName + ") - 1", eqPrecedence))
        .addVariable(baseVarName + "__index", new PyExpr(indexVarName, Integer.MAX_VALUE));

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarExprs frame that we added above.
    localVarExprs.popFrame();

    // The end of the Python 'for' loop.
    pyCodeBuilder.decreaseIndent();
  }

  @Override protected void visitForeachIfemptyNode(ForeachIfemptyNode node) {
    visitChildren(node);
  }

  /**
   * Visits a let node which accepts a value and stores it as a unique variable. The unique variable
   * name is stored in the LocalVariableStack for use by any subsequent code.
   *
   * <p>Example:
   * <pre>
   *   {let $boo: $foo[$moo] /}
   * </pre>
   * might generate
   * <pre>
   *   boo3 = opt_data.get('foo')['moo']
   * </pre>
   */
  @Override protected void visitLetValueNode(LetValueNode node) {
    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    TranslateToPyExprVisitor translator = translateToPyExprVisitorFactory.create(localVarExprs);
    PyExpr valuePyExpr = translator.exec(node.getValueExpr());
    pyCodeBuilder.appendLine(generatedVarName, " = ", valuePyExpr.getText());

    // Add a mapping for generating future references to this local var.
    localVarExprs.addVariable(node.getVarName(), new PyExpr(generatedVarName, Integer.MAX_VALUE));
  }


  /**
   * Visits a let node which contains a content section and stores it as a unique variable. The
   * unique variable name is stored in the LocalVariableStack for use by any subsequent code.
   *
   * <p>Note, this is one of the location where Strict mode is enforced in Python templates. As
   * such, all LetContentNodes must have a contentKind specified.
   *
   * <p>Example:
   * <pre>
   *   {let $boo kind="html"}
   *     Hello {$name}
   *   {/let}
   * </pre>
   * might generate
   * <pre>
   *   boo3 = sanitize.SanitizedHtml(''.join(['Hello ', sanitize.escape_html(opt_data.get('name'))])
   * </pre>
   */
  @Override protected void visitLetContentNode(LetContentNode node) {
    if (node.getContentKind() == null) {
      throw SoySyntaxExceptionUtils.createWithNode(
          "Let content node is missing a content kind. This may be due to using a non-strict "
              + "template, which is unsupported in the Python compiler.", node);
    }

    String generatedVarName = node.getUniqueVarName();

    // Traverse the children and push them onto the generated variable.
    localVarExprs.pushFrame();
    pyCodeBuilder.pushOutputVar(generatedVarName);

    visitChildren(node);

    PyExpr generatedContent = pyCodeBuilder.getOutputAsString();
    pyCodeBuilder.popOutputVar();
    localVarExprs.popFrame();

    // Mark the result as being escaped to the appropriate kind (e.g., "sanitize.SanitizedHtml").
    pyCodeBuilder.appendLine(generatedVarName, " = ",
        PyExprUtils.wrapAsSanitizedContent(node.getContentKind(), generatedContent).getText());

    // Add a mapping for generating future references to this local var.
    localVarExprs.addVariable(node.getVarName(), new PyExpr(generatedVarName, Integer.MAX_VALUE));
  }

  /**
   * Visits a call node and generates the syntax needed to call another template. If all of the
   * children can be represented as expressions, this is built as an expression itself. If not, the
   * non-expression params are saved as {@code param<n>} variables before the function call.
   */
  @Override protected void visitCallNode(CallNode node) {
    // If this node has any param children whose contents are not computable as Python expressions,
    // visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsPyExprVisitor.exec(child)) {
        visit(child);
      }
    }

    pyCodeBuilder.addToOutputVar(genPyCallExprVisitor.exec(node, localVarExprs).toPyString());
  }

  /**
   * Visits a call param content node which isn't computable as a PyExpr and stores its content in
   * a variable with the name {@code param<n>} where n is the node's id.
   */
  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    // This node should only be visited when it's not computable as Python expressions.
    Preconditions.checkArgument(!isComputableAsPyExprVisitor.exec(node),
        "Should only define 'param<n>' when not computable as Python expressions.");

    pyCodeBuilder.pushOutputVar("param" + node.getId());
    pyCodeBuilder.initOutputVarIfNecessary();
    visitChildren(node);
    pyCodeBuilder.popOutputVar();
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (isComputableAsPyExprVisitor.exec(node)) {
      // Generate Python expressions for this node and add them to the current output var.
      pyCodeBuilder.addToOutputVar(genPyExprsVisitor.exec(node));
    } else {
      // Need to implement visit*Node() for the specific case.
      throw new UnsupportedOperationException();
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Utility methods.


  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require general dependencies.
   */
  private void addCodeToRequireGeneralDeps() {
    pyCodeBuilder.appendLine("from __future__ import unicode_literals");

    pyCodeBuilder.appendLine("import math");
    pyCodeBuilder.appendLine("import random");

    // TODO(dcphillips): limit this based on usage?
    pyCodeBuilder.appendLine("from ", runtimePath, " import bidi");
    pyCodeBuilder.appendLine("from ", runtimePath, " import directives");
    pyCodeBuilder.appendLine("from ", runtimePath, " import runtime");
    pyCodeBuilder.appendLine("from ", runtimePath, " import sanitize");
    pyCodeBuilder.appendLine();

    if (!bidiIsRtlFn.isEmpty()) {
      int dotIndex = bidiIsRtlFn.lastIndexOf('.');
      // When importing the module, we'll use the constant name to avoid potential conflicts.
      String bidiModulePath = bidiIsRtlFn.substring(0, dotIndex);
      Pair<String, String> nameSpaceAndName = namespaceAndNameFromModule(bidiModulePath);
      String bidiNamespace = nameSpaceAndName.first;
      String bidiModuleName = nameSpaceAndName.second;
      pyCodeBuilder.appendLine("from ", bidiNamespace, " import ", bidiModuleName, " as ",
          SoyBidiUtils.IS_RTL_MODULE_ALIAS);
    }

    // Add import and instantiate statements for translator module
    // TODO(steveyang): remember the check when implementing MsgNode
    if (!translationClass.isEmpty()) {
      Pair<String, String> nameSpaceAndName = namespaceAndNameFromModule(translationClass);
      String translationNamespace = nameSpaceAndName.first;
      String translationName = nameSpaceAndName.second;
      pyCodeBuilder.appendLine("from ", translationNamespace, " import ", translationName);
      pyCodeBuilder.appendLine(PyExprUtils.TRANSLATOR_NAME, " = ", translationName, "()");
    }
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireSoyNamespaces(SoyFileNode soyFile) {
    for (String calleeNotInFile : new FindCalleesNotInFileVisitor(errorReporter).exec(soyFile)) {
      int lastDotIndex = calleeNotInFile.lastIndexOf('.');
      if (lastDotIndex == -1) {
        throw SoySyntaxExceptionUtils.createWithNode(
            "Called template \"" + calleeNotInFile + "\" does not reside in a namespace.",
            soyFile);
      }
      String calleeModule = calleeNotInFile.substring(0, lastDotIndex);
      if (!calleeModule.isEmpty()) {
        Pair<String, String> nameSpaceAndName = namespaceAndNameFromModule(calleeModule);
        String calleeNamespace = nameSpaceAndName.first;
        String calleeName = nameSpaceAndName.second;
        pyCodeBuilder.appendLine(calleeName, " = runtime.namespaced_import('", calleeName,
             "', namespace='", calleeNamespace, "')");
      }
    }
    pyCodeBuilder.appendLine();
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add module constant to register this module's
   * Soy namespace.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRegisterCurrentSoyNamespace(SoyFileNode soyFile) {
    String namespace = soyFile.getNamespace();
    pyCodeBuilder.appendLine("SOY_NAMESPACE = '" + namespace + "'");
  }

  /**
   * Helper to retrieve the namespace and name from a module name.
   * @param moduleName Python module name in dot notation format.
   */
  private static Pair<String, String> namespaceAndNameFromModule(String moduleName) {
    String namespace = moduleName;
    String name = moduleName;
    int lastDotIndex = moduleName.lastIndexOf('.');
    if (lastDotIndex != -1) {
      namespace = moduleName.substring(0, lastDotIndex);
      name = moduleName.substring(lastDotIndex + 1);
    }
    return Pair.of(namespace, name);
  }

  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to turn byte strings into unicode strings
   * for Python 2.
   */
  private void addCodeToFixUnicodeStrings() {
    pyCodeBuilder.appendLine("try:");
    pyCodeBuilder.increaseIndent();
    pyCodeBuilder.appendLine("str = unicode");
    pyCodeBuilder.decreaseIndent();
    pyCodeBuilder.appendLine("except NameError:");
    pyCodeBuilder.increaseIndent();
    pyCodeBuilder.appendLine("pass");
    pyCodeBuilder.decreaseIndent();
    pyCodeBuilder.appendLine();
  }

  /**
   * Helper for visitTemplateNode which generates the function body.
   */
  private void generateFunctionBody(TemplateNode node) {
    // Add a new frame for local variable translations.
    localVarExprs.pushFrame();

    // Generate statement to ensure data exists as an object, if ever used.
    if (new ShouldEnsureDataIsDefinedVisitor(errorReporter).exec(node)) {
      pyCodeBuilder.appendLine("opt_data = opt_data or {}");
    }

    pyCodeBuilder.pushOutputVar("output");

    visitChildren(node);

    PyExpr resultPyExpr = pyCodeBuilder.getOutputAsString();
    pyCodeBuilder.popOutputVar();

    // Templates with autoescape="strict" return the SanitizedContent wrapper for its kind:
    // - Call sites are wrapped in an escaper. Returning SanitizedContent prevents re-escaping.
    // - The topmost call into Soy returns a SanitizedContent. This will make it easy to take
    // the result of one template and feed it to another, and also to confidently assign sanitized
    // HTML content to innerHTML. This does not use the internal-blocks variant.
    resultPyExpr = PyExprUtils.wrapAsSanitizedContent(node.getContentKind(), resultPyExpr);

    pyCodeBuilder.appendLine("return ", resultPyExpr.getText());

    localVarExprs.popFrame();
  }
}
