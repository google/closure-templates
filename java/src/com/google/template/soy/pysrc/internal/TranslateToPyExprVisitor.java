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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListComprehensionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralFromListNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.Operator.Operand;
import com.google.template.soy.exprtree.Operator.SyntaxElement;
import com.google.template.soy.exprtree.OperatorNodes.AssertNonNullOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.logging.LoggingFunction;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Visitor for translating a Soy expression (in the form of an {@link ExprNode}) into an equivalent
 * Python expression.
 *
 */
public final class TranslateToPyExprVisitor extends AbstractReturningExprNodeVisitor<PyExpr> {

  private static class NotFoundBehavior {
    private static final NotFoundBehavior RETURN_NONE = new NotFoundBehavior(Type.RETURN_NONE);
    private static final NotFoundBehavior THROW = new NotFoundBehavior(Type.THROW);

    /** Return {@code None} if the key is not in the structure. */
    private static NotFoundBehavior returnNone() {
      return RETURN_NONE;
    }

    /** Throw an exception if the key is not in the structure. */
    private static NotFoundBehavior throwException() {
      return THROW;
    }

    /** Default to the given value if the key is not in the structure. */
    private static NotFoundBehavior defaultValue(PyExpr defaultValue) {
      return new NotFoundBehavior(defaultValue);
    }

    private enum Type {
      RETURN_NONE,
      THROW,
      DEFAULT_VALUE,
    }

    private final Type type;
    @Nullable private final PyExpr defaultValue;

    private NotFoundBehavior(Type type) {
      this.type = type;
      this.defaultValue = null;
    }

    private NotFoundBehavior(PyExpr defaultValue) {
      this.type = Type.DEFAULT_VALUE;
      this.defaultValue = checkNotNull(defaultValue);
    }

    private Type getType() {
      return type;
    }

    private PyExpr getDefaultValue() {
      return defaultValue;
    }
  }

  private static final SoyErrorKind PROTO_ACCESS_NOT_SUPPORTED =
      SoyErrorKind.of("Proto accessors are not supported in pysrc.");
  private static final SoyErrorKind PROTO_INIT_NOT_SUPPORTED =
      SoyErrorKind.of("Proto init is not supported in pysrc.");
  private static final SoyErrorKind SOY_PY_SRC_FUNCTION_NOT_FOUND =
      SoyErrorKind.of("Failed to find SoyPySrcFunction ''{0}''.");
  private static final SoyErrorKind SOY_PY_SRC_METHOD_NOT_FOUND =
      SoyErrorKind.of("Failed to find SoyPythonSourceFunction for method ''{0}''.");
  private static final SoyErrorKind UNTYPED_BRACKET_ACCESS_NOT_SUPPORTED =
      SoyErrorKind.of(
          "Bracket access on values of unknown type is not supported in pysrc. "
              + "The expression should be declared as a list or map.");

  /**
   * Errors in this visitor generate Python source that immediately explodes. Users of Soy are
   * expected to check the error reporter before using the gencode; if they don't, this should
   * apprise them. TODO(brndn): consider changing the visitor to return {@code Optional<PyExpr>} and
   * returning {@link Optional#absent()} on error.
   */
  private static final PyExpr ERROR =
      new PyExpr("raise Exception('Soy compilation failed')", Integer.MAX_VALUE);

  private static final PyExpr NONE = new PyExpr("None", Integer.MAX_VALUE);

  private static final PyExpr DATA = new PyExpr("data", Integer.MAX_VALUE);
  private static final PyExpr IJ_DATA = new PyExpr("ijData", Integer.MAX_VALUE);

  private final LocalVariableStack localVarExprs;

  private final ErrorReporter errorReporter;
  private final PythonValueFactoryImpl pluginValueFactory;
  private final SoyFileNode containingFile;

  TranslateToPyExprVisitor(
      LocalVariableStack localVarExprs,
      PythonValueFactoryImpl pluginValueFactory,
      SoyNode containingNode,
      ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
    this.pluginValueFactory = pluginValueFactory;
    this.containingFile = containingNode.getNearestAncestor(SoyFileNode.class);
    this.localVarExprs = localVarExprs;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.

  @Override
  protected PyExpr visitExprRootNode(ExprRootNode node) {
    return visit(node.getRoot());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.

  @Override
  protected PyExpr visitPrimitiveNode(PrimitiveNode node) {
    // Note: ExprNode.toSourceString() technically returns a Soy expression. In the case of
    // primitives, the result is usually also the correct Python expression.
    return new PyExpr(node.toSourceString(), Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitStringNode(StringNode node) {
    return new PyStringExpr(node.toSourceString());
  }

  @Override
  protected PyExpr visitNullNode(NullNode node) {
    // Nulls are represented as 'None' in Python.
    return NONE;
  }

  @Override
  protected PyExpr visitBooleanNode(BooleanNode node) {
    // Specifically set booleans to 'True' and 'False' given python's strict naming for booleans.
    return new PyExpr(node.getValue() ? "True" : "False", Integer.MAX_VALUE);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.

  @Override
  protected PyExpr visitListLiteralNode(ListLiteralNode node) {
    return PyExprUtils.convertIterableToPyListExpr(
        node.getChildren().stream().map(n -> visit(n)).collect(Collectors.toList()));
  }

  @Override
  protected PyExpr visitListComprehensionNode(ListComprehensionNode node) {

    // Visit the originalListExpr in: [transformExpr for $foo in originalListExpr if filterExpr].
    PyExpr originalListExpr = visit(node.getListExpr());

    // Build a unique name for the iterator variable ($foo in this example), and push a local var
    // frame for its scope.
    String baseListIterVarName = node.getListIterVar().name();
    String uniqueListIterVarName =
        String.format("%sListComprehensions%d", baseListIterVarName, node.getNodeId());
    localVarExprs.pushFrame();
    localVarExprs.addVariable(
        baseListIterVarName, new PyExpr(uniqueListIterVarName, Integer.MAX_VALUE));
    String uniqueIndexVarName = null;
    if (node.getIndexVar() != null) {
      String baseIndexVarName = node.getIndexVar().name();
      uniqueIndexVarName =
          String.format("%sListComprehensions%d", baseIndexVarName, node.getNodeId());
      localVarExprs.addVariable(
          baseIndexVarName, new PyExpr(uniqueIndexVarName, Integer.MAX_VALUE));
    }

    // Now we can visit the transformExpr and filterExpr (if present).
    PyExpr itemTransformExpr = visit(node.getListItemTransformExpr());
    PyExpr filterExpr = node.getFilterExpr() == null ? null : visit(node.getFilterExpr());

    // Build the full list comprehension expr.
    PyExpr comprehensionExpr =
        PyExprUtils.genPyListComprehensionExpr(
            originalListExpr,
            itemTransformExpr,
            filterExpr,
            uniqueListIterVarName,
            uniqueIndexVarName);

    localVarExprs.popFrame();

    return comprehensionExpr;
  }

  @Override
  protected PyExpr visitRecordLiteralNode(RecordLiteralNode node) {
    Preconditions.checkArgument(node.numChildren() == node.getKeys().size());
    Map<PyExpr, PyExpr> dict = new LinkedHashMap<>();

    for (int i = 0; i < node.numChildren(); i++) {
      dict.put(new PyStringExpr("'" + node.getKey(i) + "'"), visit(node.getChild(i)));
    }

    // TODO(b/69064788): Switch records to use namedtuple so that if a record is accessed as a map
    // (or if a map is accessed as a record) it's a runtime error.
    return PyExprUtils.convertMapToOrderedDict(dict);
  }

  @Override
  protected PyExpr visitMapLiteralNode(MapLiteralNode node) {
    Preconditions.checkArgument(node.numChildren() % 2 == 0);
    Map<PyExpr, PyExpr> dict = new LinkedHashMap<>();

    for (int i = 0, n = node.numChildren(); i < n; i += 2) {
      ExprNode keyNode = node.getChild(i);
      PyExpr key = visit(keyNode);
      key = new PyFunctionExprBuilder("runtime.check_not_null").addArg(key).asPyExpr();
      ExprNode valueNode = node.getChild(i + 1);
      dict.put(key, visit(valueNode));
    }

    return PyExprUtils.convertMapToPyExpr(dict);
  }

  @Override
  protected PyExpr visitMapLiteralFromListNode(MapLiteralFromListNode node) {
    // Unimplemented. Return an empty map for now.
    Map<PyExpr, PyExpr> dict = new LinkedHashMap<>();
    return PyExprUtils.convertMapToPyExpr(dict);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.

  @Override
  protected PyExpr visitVarRefNode(VarRefNode node) {
    if (node.getDefnDecl().kind() == VarDefn.Kind.STATE) {
      throw new AssertionError(); // should have been desugared
    } else if (node.isInjected()) {
      // Case 1: Injected data reference.
      return new PyExpr(
          genCodeForLiteralKeyAccess(IJ_DATA, node.getNameWithoutLeadingDollar()),
          Integer.MAX_VALUE);
    } else {
      PyExpr translation = localVarExprs.getVariableExpression(node.getNameWithoutLeadingDollar());
      if (translation != null) {
        // Case 2: In-scope local var.
        return new PyExpr(translation.getText(), Integer.MAX_VALUE);
      } else {
        // Case 3: Data reference.
        NotFoundBehavior notFoundBehavior = NotFoundBehavior.throwException();
        if (node.getDefnDecl().kind() == VarDefn.Kind.PARAM
            && ((TemplateParam) node.getDefnDecl()).hasDefault()) {
          // This evaluates the default value at every access of a parameter with a default
          // value. This could be made more performant by only evaluating the default value
          // once at the beginning of the template. But the Python backend is minimally
          // supported so this is fine.
          PyExpr defaultValue = visit(((TemplateParam) node.getDefnDecl()).defaultValue());
          notFoundBehavior = NotFoundBehavior.defaultValue(defaultValue);
        }
        return new PyExpr(
            genCodeForLiteralKeyAccess(DATA, node.getNameWithoutLeadingDollar(), notFoundBehavior),
            Integer.MAX_VALUE);
      }
    }
  }

  @Override
  protected PyExpr visitDataAccessNode(DataAccessNode node) {
    // All null safe accesses should've already been converted to NullSafeAccessNodes.
    checkArgument(!node.isNullSafe());
    // First recursively visit base expression.
    PyExpr base = visit(node.getBaseExprChild());
    return new PyExpr(visitDataAccessNode(node, base), Integer.MAX_VALUE);
  }

  private PyExpr visitDataAccessNode(
      DataAccessNode dataAccess,
      StringBuilder nullSafetyPrefix,
      PyExpr base,
      boolean nullSafe,
      boolean hasAssertNonNull) {
    // Generate null safety check for base expression.
    if (nullSafe) {
      nullSafetyPrefix.append("None if ").append(base.getText()).append(" is None else ");
    }
    PyExpr result = new PyExpr(visitDataAccessNode(dataAccess, base), Integer.MAX_VALUE);
    if (hasAssertNonNull) {
      result = assertNotNull(result);
    }
    return result;
  }

  private String visitDataAccessNode(DataAccessNode dataAccess, PyExpr base) {
    // Generate access to field
    if (dataAccess.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
      FieldAccessNode fieldAccess = (FieldAccessNode) dataAccess;
      return genCodeForFieldAccess(
          fieldAccess, fieldAccess.getBaseExprChild().getType(), base, fieldAccess.getFieldName());
    } else if (dataAccess.getKind() == ExprNode.Kind.METHOD_CALL_NODE) {
      MethodCallNode methodCall = (MethodCallNode) dataAccess;
      return genCodeForMethodCall(methodCall, base);
    } else {
      ItemAccessNode itemAccess = (ItemAccessNode) dataAccess;
      Kind baseKind = itemAccess.getBaseExprChild().getType().getKind();
      PyExpr keyPyExpr = visit(itemAccess.getKeyExprChild());
      switch (baseKind) {
        case LIST:
          return genCodeForKeyAccess(base, keyPyExpr, NotFoundBehavior.returnNone());
        case UNKNOWN:
          errorReporter.report(
              itemAccess.getKeyExprChild().getSourceLocation(),
              UNTYPED_BRACKET_ACCESS_NOT_SUPPORTED);
          // fall through
        case MAP:
        case UNION:
          return genCodeForKeyAccess(base, keyPyExpr, NotFoundBehavior.returnNone());
        case LEGACY_OBJECT_MAP:
        case RECORD:
          return genCodeForKeyAccess(base, keyPyExpr, NotFoundBehavior.throwException());
        default:
          throw new AssertionError("illegal item access on " + baseKind);
      }
    }
  }

  @Override
  protected PyExpr visitNullSafeAccessNode(NullSafeAccessNode nullSafeAccessNode) {
    StringBuilder nullSafetyPrefix = new StringBuilder();

    PyExpr access = visit(nullSafeAccessNode.getBase());
    ExprNode dataAccess = nullSafeAccessNode.getDataAccess();
    while (dataAccess.getKind() == ExprNode.Kind.NULL_SAFE_ACCESS_NODE) {
      NullSafeAccessNode node = (NullSafeAccessNode) dataAccess;
      access =
          accumulateDataAccess(
              (DataAccessNode) node.getBase(),
              access,
              nullSafetyPrefix,
              /* hasAssertNonNull= */ false);
      dataAccess = node.getDataAccess();
    }
    access =
        accumulateDataAccessTail((AccessChainComponentNode) dataAccess, access, nullSafetyPrefix);

    if (nullSafetyPrefix.length() == 0) {
      return access;
    } else {
      return new PyExpr(
          nullSafetyPrefix + access.getText(),
          PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL));
    }
  }

  private PyExpr accumulateDataAccess(
      DataAccessNode dataAccessNode,
      PyExpr base,
      StringBuilder nullSafetyPrefix,
      boolean hasAssertNonNull) {
    boolean nullSafe = true;
    if (dataAccessNode.getBaseExprChild() instanceof DataAccessNode) {
      base =
          accumulateDataAccess(
              (DataAccessNode) dataAccessNode.getBaseExprChild(),
              base,
              nullSafetyPrefix,
              /* hasAssertNonNull= */ false);
      nullSafe = false;
    }
    return visitDataAccessNode(dataAccessNode, nullSafetyPrefix, base, nullSafe, hasAssertNonNull);
  }

  private PyExpr accumulateDataAccessTail(
      AccessChainComponentNode dataAccessNode, PyExpr base, StringBuilder nullSafetyPrefix) {
    boolean hasAssertNonNull = false;
    if (dataAccessNode.getKind() == ExprNode.Kind.ASSERT_NON_NULL_OP_NODE) {
      AssertNonNullOpNode assertNonNull = (AssertNonNullOpNode) dataAccessNode;
      dataAccessNode = (AccessChainComponentNode) assertNonNull.getChild(0);
      hasAssertNonNull = true;
    }
    return accumulateDataAccess(
        (DataAccessNode) dataAccessNode, base, nullSafetyPrefix, hasAssertNonNull);
  }

  @Override
  protected PyExpr visitGlobalNode(GlobalNode node) {
    return visit(node.getValue());
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.

  @Override
  protected PyExpr visitOperatorNode(OperatorNode node) {
    return genPyExprUsingSoySyntax(node);
  }

  @Override
  protected PyExpr visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    List<PyExpr> children = visitChildren(node);

    PyExpr conditionalExpr = PyExprUtils.genPyNotNullCheck(children.get(0));
    PyExpr trueExpr = children.get(0);
    PyExpr falseExpr = children.get(1);

    // TODO(dcphillips): unlike jssrc,Tofu and jbcsrc pysrc evaluates the condition twice.  It would
    // be nice to avoid that. Obvious solutions include.
    // 1. Introduce a local variable:
    // tmp = <left hand side>
    // if tmp is None:
    //   tmp = <right hand side>
    //
    // 2. Use a lambda to defer evaluation of the right hand side.
    // lambda x=<left hand side> : <right hand side> if x is None else x
    return genTernaryConditional(conditionalExpr, trueExpr, falseExpr);
  }

  @Override
  protected PyExpr visitEqualOpNode(EqualOpNode node) {
    // Python has stricter type casting rules during equality comparison. To get around this we
    // use our custom utility to emulate the behavior of Soy/JS.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr(
        "runtime.type_safe_eq("
            + operandPyExprs.get(0).getText()
            + ", "
            + operandPyExprs.get(1).getText()
            + ")",
        Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitNotEqualOpNode(NotEqualOpNode node) {
    // Invert type_safe_eq.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr(
        "not runtime.type_safe_eq("
            + operandPyExprs.get(0).getText()
            + ", "
            + operandPyExprs.get(1).getText()
            + ")",
        PyExprUtils.pyPrecedenceForOperator(Operator.NOT));
  }

  @Override
  protected PyExpr visitPlusOpNode(PlusOpNode node) {
    // Python has stricter type casting between strings and other primitives than Soy, so addition
    // must be sent through the type_safe_add utility to emulate that behavior.
    List<PyExpr> operandPyExprs = visitChildren(node);

    return new PyExpr(
        "runtime.type_safe_add("
            + operandPyExprs.get(0).getText()
            + ", "
            + operandPyExprs.get(1).getText()
            + ")",
        Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitConditionalOpNode(ConditionalOpNode node) {
    // Retrieve the operands.
    Operator op = Operator.CONDITIONAL;
    List<SyntaxElement> syntax = op.getSyntax();
    List<PyExpr> operandExprs = visitChildren(node);

    Operand conditionalOperand = ((Operand) syntax.get(0));
    PyExpr conditionalExpr = operandExprs.get(conditionalOperand.getIndex());
    Operand trueOperand = ((Operand) syntax.get(4));
    PyExpr trueExpr = operandExprs.get(trueOperand.getIndex());
    Operand falseOperand = ((Operand) syntax.get(8));
    PyExpr falseExpr = operandExprs.get(falseOperand.getIndex());

    return genTernaryConditional(conditionalExpr, trueExpr, falseExpr);
  }

  @Override
  protected PyExpr visitAssertNonNullOpNode(AssertNonNullOpNode node) {
    return assertNotNull(node.getChild(0));
  }

  /**
   * {@inheritDoc}
   *
   * <p>The source of available functions is a look-up map provided by Guice in {@link
   * SharedModule#provideSoyFunctionsMap}.
   *
   * @see BuiltinFunction
   * @see SoyPySrcFunction
   */
  @Override
  protected PyExpr visitFunctionNode(FunctionNode node) {
    Object soyFunction = node.getSoyFunction();
    if (soyFunction instanceof BuiltinFunction) {
      return visitNonPluginFunction(node, (BuiltinFunction) soyFunction);
    } else if (soyFunction instanceof SoyPythonSourceFunction) {
      return pluginValueFactory.applyFunction(
          node.getSourceLocation(),
          node.getStaticFunctionName(),
          (SoyPythonSourceFunction) soyFunction,
          visitChildren(node));
    } else if (soyFunction instanceof SoyPySrcFunction) {
      List<PyExpr> args = visitChildren(node);
      return ((SoyPySrcFunction) soyFunction).computeForPySrc(args);
    } else if (soyFunction instanceof LoggingFunction) {
      // trivial logging function support
      return new PyStringExpr("'" + ((LoggingFunction) soyFunction).getPlaceholder() + "'");
    } else {
      errorReporter.report(
          node.getSourceLocation(), SOY_PY_SRC_FUNCTION_NOT_FOUND, node.getStaticFunctionName());
      return ERROR;
    }
  }

  private PyExpr visitNonPluginFunction(FunctionNode node, BuiltinFunction nonpluginFn) {
    switch (nonpluginFn) {
      case IS_PARAM_SET:
        return visitIsSetFunction(node);
      case IS_FIRST:
        return visitForEachFunction(node, "__isFirst");
      case IS_LAST:
        return visitForEachFunction(node, "__isLast");
      case INDEX:
        return visitForEachFunction(node, "__index");
      case CHECK_NOT_NULL:
        return assertNotNull(node.getChild(0));
      case CSS:
        return visitCssFunction(node);
      case XID:
        return visitXidFunction(node);
      case SOY_SERVER_KEY:
        return visitSoyServerKeyFunction(node);
      case IS_PRIMARY_MSG_IN_USE:
        return visitIsPrimaryMsgInUseFunction(node);
      case TO_FLOAT:
        // this is a no-op in python
        return visit(node.getChild(0));
      case DEBUG_SOY_TEMPLATE_INFO:
        // 'debugSoyTemplateInfo' is used for inpsecting soy template info from rendered pages.
        // Always resolve to false since there is no plan to support this feature in PySrc.
        return new PyExpr("False", Integer.MAX_VALUE);
      case LEGACY_DYNAMIC_TAG:
      case UNKNOWN_JS_GLOBAL:
        throw new UnsupportedOperationException(
            "the "
                + nonpluginFn.getName()
                + " function can't be used in templates compiled to Python");
      case VE_DATA:
        return NONE;
      case MSG_WITH_ID:
      case REMAINDER:
        // should have been removed earlier in the compiler
        throw new AssertionError();
      case PROTO_INIT:
        errorReporter.report(node.getSourceLocation(), PROTO_INIT_NOT_SUPPORTED);
        return ERROR;
    }
    throw new AssertionError();
  }

  private PyExpr visitForEachFunction(FunctionNode node, String suffix) {
    String varName = ((VarRefNode) node.getChild(0)).getNameWithoutLeadingDollar();
    return localVarExprs.getVariableExpression(varName + suffix);
  }

  private PyExpr visitIsSetFunction(FunctionNode node) {
    String varName = ((VarRefNode) node.getChild(0)).getNameWithoutLeadingDollar();
    return new PyFunctionExprBuilder("runtime.is_set").addArg(varName).addArg(DATA).asPyExpr();
  }

  private PyExpr assertNotNull(ExprNode node) {
    return assertNotNull(visit(node));
  }

  private static PyExpr assertNotNull(PyExpr expr) {
    return new PyFunctionExprBuilder("runtime.check_not_null").addArg(expr).asPyExpr();
  }

  private PyExpr visitCssFunction(FunctionNode node) {
    return new PyFunctionExprBuilder("runtime.get_css_name")
        .addArgs(visitChildren(node))
        .asPyExpr();
  }

  private PyExpr visitXidFunction(FunctionNode node) {
    return new PyFunctionExprBuilder("runtime.get_xid_name")
        .addArg(visit(node.getChild(0)))
        .asPyExpr();
  }

  private PyExpr visitSoyServerKeyFunction(FunctionNode node) {
    return visit(node.getChild(0));
  }

  private PyExpr visitIsPrimaryMsgInUseFunction(FunctionNode node) {
    long primaryMsgId = ((IntegerNode) node.getChild(1)).getValue();
    long fallbackMsgId = ((IntegerNode) node.getChild(2)).getValue();
    return new PyExpr(
        PyExprUtils.TRANSLATOR_NAME
            + ".is_msg_available("
            + primaryMsgId
            + ") or not "
            + PyExprUtils.TRANSLATOR_NAME
            + ".is_msg_available("
            + fallbackMsgId
            + ")",
        PyExprUtils.pyPrecedenceForOperator(Operator.OR));
  }

  /**
   * Generates the code for key access given a key literal, e.g. {@code .get('key')}.
   *
   * @param key the String literal value to be used as a key
   */
  private static String genCodeForLiteralKeyAccess(PyExpr containerExpr, String key) {
    return genCodeForLiteralKeyAccess(containerExpr, key, NotFoundBehavior.throwException());
  }

  private static String genCodeForLiteralKeyAccess(
      PyExpr containerExpr, String key, NotFoundBehavior notFoundBehavior) {
    return genCodeForKeyAccess(containerExpr, new PyStringExpr("'" + key + "'"), notFoundBehavior);
  }

  /**
   * Generates the code for key access given the name of a variable to be used as a key, e.g. {@code
   * .get(key)}.
   *
   * @param key an expression to be used as a key
   * @param notFoundBehavior What should happen if the key is not in the structure.
   * @param coerceKeyToString Whether or not the key should be coerced to a string.
   */
  private static String genCodeForKeyAccess(
      PyExpr containerExpr, PyExpr key, NotFoundBehavior notFoundBehavior) {
    switch (notFoundBehavior.getType()) {
      case RETURN_NONE:
        return new PyFunctionExprBuilder("runtime.key_safe_data_access")
            .addArg(containerExpr)
            .addArg(key)
            .build();
      case THROW:
        return new PyFunctionExprBuilder(containerExpr.getText() + ".get").addArg(key).build();
      case DEFAULT_VALUE:
        return new PyFunctionExprBuilder(containerExpr.getText() + ".get")
            .addArg(key)
            .addArg(notFoundBehavior.getDefaultValue())
            .build();
    }
    throw new AssertionError(notFoundBehavior.getType());
  }

  /**
   * Generates the code for a field name access, e.g. ".foo" or "['bar']".
   *
   * @param node the field access source node
   * @param baseType the type of the object that contains the field
   * @param containerExpr an expression that evaluates to the container of the named field. This
   *     expression may have any operator precedence that binds more tightly than exponentiation.
   * @param fieldName the field name
   */
  private String genCodeForFieldAccess(
      ExprNode node, SoyType baseType, PyExpr containerExpr, String fieldName) {
    if (baseType != null && baseType.getKind() == SoyType.Kind.PROTO) {
      errorReporter.report(node.getSourceLocation(), PROTO_ACCESS_NOT_SUPPORTED);
      return ".ERROR";
    }
    return genCodeForLiteralKeyAccess(containerExpr, fieldName);
  }

  private String genCodeForMethodCall(MethodCallNode methodCallNode, PyExpr containerExpr) {
    Preconditions.checkArgument(methodCallNode.isMethodResolved());
    SoyMethod method = methodCallNode.getSoyMethod();

    // Never allow a null method receiver.
    containerExpr = assertNotNull(containerExpr);

    if (method instanceof BuiltinMethod) {
      switch ((BuiltinMethod) method) {
        case BIND:
          return new PyFunctionExprBuilder("runtime.bind_template_params")
              .addArg(containerExpr)
              .addArg(visit(methodCallNode.getChild(1)))
              .asPyExpr()
              .getText();
        case GET_EXTENSION:
        case HAS_PROTO_FIELD:
          errorReporter.report(
              methodCallNode.getAccessSourceLocation(),
              SOY_PY_SRC_METHOD_NOT_FOUND,
              methodCallNode.getMethodName());
          return ".ERROR";
      }
    } else if (method instanceof SoySourceFunctionMethod) {
      SoySourceFunction function = ((SoySourceFunctionMethod) method).getImpl();
      if (function instanceof SoyPythonSourceFunction) {
        List<PyExpr> args = new ArrayList<>();
        args.add(containerExpr);
        methodCallNode.getParams().forEach(n -> args.add(visit(n)));
        return pluginValueFactory
            .applyFunction(
                methodCallNode.getSourceLocation(),
                methodCallNode.getMethodName().identifier(),
                (SoyPythonSourceFunction) function,
                args)
            .getText();
      } else {
        errorReporter.report(
            methodCallNode.getAccessSourceLocation(),
            SOY_PY_SRC_METHOD_NOT_FOUND,
            methodCallNode.getMethodName());
        return ".ERROR";
      }
    }
    throw new AssertionError();
  }

  /**
   * Generates a Python expression for the given OperatorNode's subtree assuming that the Python
   * expression for the operator uses the same syntax format as the Soy operator.
   *
   * @param opNode the OperatorNode whose subtree to generate a Python expression for
   * @return the generated Python expression
   */
  private PyExpr genPyExprUsingSoySyntax(OperatorNode opNode) {
    List<PyExpr> operandPyExprs = visitChildren(opNode);
    String newExpr = PyExprUtils.genExprWithNewToken(opNode.getOperator(), operandPyExprs, null);

    return new PyExpr(newExpr, PyExprUtils.pyPrecedenceForOperator(opNode.getOperator()));
  }

  /**
   * Generates a ternary conditional Python expression given the conditional and true/false
   * expressions.
   *
   * @param conditionalExpr the conditional expression
   * @param trueExpr the expression to execute if the conditional executes to true
   * @param falseExpr the expression to execute if the conditional executes to false
   * @return a ternary conditional expression
   */
  private PyExpr genTernaryConditional(PyExpr conditionalExpr, PyExpr trueExpr, PyExpr falseExpr) {
    // Python's ternary operator switches the order from <conditional> ? <true> : <false> to
    // <true> if <conditional> else <false>.
    int conditionalPrecedence = PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL);
    StringBuilder exprSb =
        new StringBuilder()
            .append(PyExprUtils.maybeProtect(trueExpr, conditionalPrecedence).getText())
            .append(" if ")
            .append(PyExprUtils.maybeProtect(conditionalExpr, conditionalPrecedence).getText())
            .append(" else ")
            .append(PyExprUtils.maybeProtect(falseExpr, conditionalPrecedence).getText());

    return new PyExpr(exprSb.toString(), conditionalPrecedence);
  }

  @Override
  protected PyExpr visitVeLiteralNode(VeLiteralNode node) {
    return NONE;
  }

  PyExpr getCalleeExpr(TemplateLiteralNode node) {
    String name;
    TemplateNode template = getTemplateIfInSameFile(node);
    if (template != null) {
      name = GenPyCallExprVisitor.getLocalTemplateName(template);
    } else {
      String resolvedName = node.getResolvedName();
      int secondToLastDotIndex = resolvedName.lastIndexOf('.', resolvedName.lastIndexOf('.') - 1);
      name = resolvedName.substring(secondToLastDotIndex + 1);
    }
    return new PyExpr(name, Integer.MAX_VALUE);
  }

  @Override
  protected PyExpr visitTemplateLiteralNode(TemplateLiteralNode node) {
    PyExpr calleeExpr = getCalleeExpr(node);
    return new PyFunctionExprBuilder("runtime.create_template_type")
        .addArg(calleeExpr)
        .addArg(new PyStringExpr("'" + node.getResolvedName() + "'"))
        .asPyExpr();
  }

  @Nullable
  private TemplateNode getTemplateIfInSameFile(TemplateLiteralNode templateLiteralNode) {
    for (TemplateNode template : containingFile.getTemplates()) {
      if ((template instanceof TemplateBasicNode || template instanceof TemplateElementNode)
          && template.getTemplateName().equals(templateLiteralNode.getResolvedName())) {
        return template;
      }
    }
    return null;
  }
}
