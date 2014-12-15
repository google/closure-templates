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

import com.google.common.collect.Lists;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyEasyDict;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.soytree.defn.LoopVar;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Visitor for evaluating the expression rooted at a given ExprNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} may be called on any expression. The result of evaluating the expression (in
 * the context of the {@code data} and {@code env} passed into the constructor) is returned as a
 * {@code SoyValue} object.
 *
 */
public class EvalVisitor extends AbstractReturningExprNodeVisitor<SoyValue> {

  /**
   * Interface for a factory that creates an EvalVisitor.
   */
  public static interface EvalVisitorFactory {

    /**
     * Creates an EvalVisitor.
     *
     * @param ijData The current injected data.
     * @param env The current environment.
     * @return The newly created EvalVisitor instance.
     */
    public EvalVisitor create(@Nullable SoyRecord ijData, Environment env);
  }


  /** Instance of SoyValueHelper to use. */
  private final SoyValueHelper valueHelper;

  /** Map of all SoyJavaFunctions (name to function). */
  private final Map<String, SoyJavaFunction> soyJavaFunctionsMap;

  /** The current injected data. */
  private final SoyRecord ijData;

  /** The current environment. */
  private final Environment env;

  /**
   * @param soyJavaFunctionsMap Map of all SoyJavaFunctions (name to function). Can be
   *     null if the subclass that is calling this constructor plans to override the default
   *     implementation of {@code computeFunction()}.
   * @param ijData The current injected data.
   * @param env The current environment.
   */
  protected EvalVisitor(
      SoyValueHelper valueHelper, @Nullable Map<String, SoyJavaFunction> soyJavaFunctionsMap,
      @Nullable SoyRecord ijData, Environment env) {

    this.valueHelper = valueHelper;
    this.soyJavaFunctionsMap = soyJavaFunctionsMap;
    this.ijData = ijData;
    this.env = checkNotNull(env);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected SoyValue visitExprRootNode(ExprRootNode<?> node) {
    return visit(node.getChild(0));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.


  @Override protected SoyValue visitNullNode(NullNode node) {
    return NullData.INSTANCE;
  }


  @Override protected SoyValue visitBooleanNode(BooleanNode node) {
    return convertResult(node.getValue());
  }


  @Override protected SoyValue visitIntegerNode(IntegerNode node) {
    return convertResult(node.getValue());
  }


  @Override protected SoyValue visitFloatNode(FloatNode node) {
    return convertResult(node.getValue());
  }


  @Override protected SoyValue visitStringNode(StringNode node) {
    return convertResult(node.getValue());
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.


  @Override protected SoyValue visitListLiteralNode(ListLiteralNode node) {
    return valueHelper.newEasyListFromJavaIterable(this.visitChildren(node)).makeImmutable();
  }


  @Override protected SoyValue visitMapLiteralNode(MapLiteralNode node) {

    int numItems = node.numChildren() / 2;

    boolean isStringKeyed = true;
    ExprNode firstNonstringKeyNode = null;
    List<SoyValue> keys = Lists.newArrayListWithCapacity(numItems);
    List<SoyValue> values = Lists.newArrayListWithCapacity(numItems);

    for (int i = 0; i < numItems; i++) {
      SoyValue key = visit(node.getChild(2 * i));
      if (isStringKeyed && ! (key instanceof StringData)) {
        isStringKeyed = false;
        firstNonstringKeyNode = node.getChild(2 * i);  // temporary until we support nonstring key
      }
      keys.add(key);
      values.add(visit(node.getChild(2 * i + 1)));
    }

    if (isStringKeyed) {
      SoyEasyDict dict = valueHelper.newEasyDict();
      for (int i = 0; i < numItems; i++) {
        dict.setField(keys.get(i).stringValue(), values.get(i));
      }
      return dict.makeImmutable();

    } else {
      // TODO: Support map literals with nonstring keys.
      throw new RenderException(String.format(
          "Currently, map literals must have string keys (key \"%s\" in map %s does not evaluate" +
              " to a string). Support for nonstring keys is a todo.",
          firstNonstringKeyNode.toSourceString(), node.toSourceString()));
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.


  @Override protected SoyValue visitVarRefNode(VarRefNode node) {
    return visitNullSafeNode(node);
  }


  @Override protected SoyValue visitDataAccessNode(DataAccessNode node) {
    return visitNullSafeNode(node);
  }


  /**
   * Helper function which ensures that {@link NullSafetySentinel} instances don't
   * escape from this visitor.
   *
   * @param node The node to evaluate.
   * @return The result of evaluating the node.
   */
  private SoyValue visitNullSafeNode(ExprNode node) {
    SoyValue value = visitNullSafeNodeRecurse(node);
    // Transform null sentinel into a normal null value.
    if (value == NullSafetySentinel.INSTANCE) {
      return NullData.INSTANCE;
    }
    return value;
  }


  /**
   * Helper function which recursively evaluates data references. This bypasses
   * the normal visitor mechanism as follows: As soon as the EvalVisitor sees a node which
   * is a data reference, it calls this function which evaluates that data reference
   * and any descendant data references, returning either the result of the evaluation, or
   * a special sentinel value which indicates that a null-safety check failed. Internally
   * this sentinel value is used to short-circuit evaluations that would otherwise fail because
   * of the null value.
   *
   * If any descendant node is not a data reference, then this uses the normal visitor
   * mechanism to evaluate that node.
   *
   * The reason for bypassing the normal visitor mechanism is that we want to detect
   * the transition between data-reference nodes and non-data-reference nodes. So for
   * example, if a FieldAccessNode has a parent node which is a data reference, we want to
   * propagate the sentinel value upward, whereas if the parent is not a data reference,
   * then we want to convert the sentinel value into a regular null value.
   *
   * @param node The node to evaluate.
   * @return The result of evaluating the node.
   */
  private SoyValue visitNullSafeNodeRecurse(ExprNode node) {
    switch (node.getKind()) {
      case VAR_REF_NODE:
        return visitNullSafeVarRefNode((VarRefNode) node);

      case FIELD_ACCESS_NODE:
      case ITEM_ACCESS_NODE:
        return visitNullSafeDataAccessNode((DataAccessNode) node);

      default: {
        return visit(node);
      }
    }
  }

  private SoyValue visitNullSafeVarRefNode(VarRefNode varRef) {
    SoyValue result = null;
    if (varRef.isInjected()) {
      // TODO(lukes): it would be nice to move this logic into Environment or even eliminate the
      // ijData == null case.  It seems like this case is mostly for prerendering, though im not
      // sure.
      if (ijData != null) {
        result = ijData.getField(varRef.getName());
      } else {
        if (varRef.isNullSafeInjected()) {
          return NullSafetySentinel.INSTANCE;
        } else {
          throw new RenderException(
              "Injected data not provided, yet referenced (" + varRef.toSourceString() + ").");
        }
      }
    } else {
      return env.getVar(varRef.getDefnDecl());
    }

    return (result != null) ? result : UndefinedData.INSTANCE;
  }

  private SoyValue visitNullSafeDataAccessNode(DataAccessNode dataAccess) {
    SoyValue value = visitNullSafeNodeRecurse(dataAccess.getBaseExprChild());

    // We expect the base expr to be a SoyRecord for field access or SoyMap for item access.
    String expectedTypeNameForErrorMsg = null;  // will be nonnull if error
    if (dataAccess.getKind() == ExprNode.Kind.FIELD_ACCESS_NODE) {
      // Case 1: Field access. Expect base value to be SoyRecord.
      if (value instanceof SoyRecord) {
        value = ((SoyRecord) value).getField(((FieldAccessNode) dataAccess).getFieldName());
      } else {
        expectedTypeNameForErrorMsg = "record";
      }
    } else {
      // Case 2: Item access. Expect base value to be SoyMap (includes SoyList).
      if (value instanceof SoyMap) {
        SoyValue key = visit(((ItemAccessNode) dataAccess).getKeyExprChild());
        value = ((SoyMap) value).getItem(key);
      } else {
        expectedTypeNameForErrorMsg = "map/list";
      }
    }

    // Handle error cases (including null-safety check failure.
    if (expectedTypeNameForErrorMsg != null) {
      if (dataAccess.isNullSafe()) {
        if (value == null || value instanceof UndefinedData ||
            value instanceof NullData || value == NullSafetySentinel.INSTANCE) {
          // Return the sentinel value that indicates that a null-safety check failed.
          return NullSafetySentinel.INSTANCE;
        } else {
          throw new RenderException(String.format(
              "While evaluating \"%s\", encountered non-%s just before accessing \"%s\".",
              dataAccess.toSourceString(), expectedTypeNameForErrorMsg,
              dataAccess.getSourceStringSuffix()));
        }
      } else if (value == NullSafetySentinel.INSTANCE) {
        // Bail out if base expression failed a null-safety check.
        return value;
      } else {
        // This behavior is not ideal, but needed for compatibility with existing code.
        // TODO: If feasible, find and fix existing instances, then throw RenderException here.
        return UndefinedData.INSTANCE;
      }
    } else if (dataAccess.getType() != null &&
        value != null &&
        !dataAccess.getType().isInstance(value)) {
      throw new RenderException(String.format("Expected value of type '" +
          dataAccess.getType() + "', but actual types was '" +
          value.getClass().getSimpleName() + "'."));
    }

    return (value != null) ? value : UndefinedData.INSTANCE;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.


  @Override protected SoyValue visitNegativeOpNode(NegativeOpNode node) {

    SoyValue operand = visit(node.getChild(0));
    if (operand instanceof IntegerData) {
      return convertResult( - operand.longValue() );
    } else {
      return convertResult( - operand.floatValue() );
    }
  }


  @Override protected SoyValue visitNotOpNode(NotOpNode node) {

    SoyValue operand = visit(node.getChild(0));
    return convertResult( ! operand.coerceToBoolean() );
  }


  @Override protected SoyValue visitTimesOpNode(TimesOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.longValue() * operand1.longValue());
    } else {
      return convertResult(operand0.numberValue() * operand1.numberValue());
    }
  }


  @Override protected SoyValue visitDivideByOpNode(DivideByOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
    // Note that this *will* lose precision for longs.
    return convertResult(operand0.numberValue() / operand1.numberValue());
  }


  @Override protected SoyValue visitModOpNode(ModOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    return convertResult(operand0.longValue() % operand1.longValue());
  }


  @Override protected SoyValue visitPlusOpNode(PlusOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.longValue() + operand1.longValue());
    } else if (operand0 instanceof StringData || operand1 instanceof StringData) {
      // String concatenation. Note we're calling toString() instead of stringValue() in case one
      // of the operands needs to be coerced to a string.
      return convertResult(operand0.toString() + operand1.toString());
    } else {
      return convertResult(operand0.numberValue() + operand1.numberValue());
    }
  }


  @Override protected SoyValue visitMinusOpNode(MinusOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.longValue() - operand1.longValue());
    } else {
      return convertResult(operand0.numberValue() - operand1.numberValue());
    }
  }


  @Override protected SoyValue visitLessThanOpNode(LessThanOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.longValue() < operand1.longValue());
    } else {
      return convertResult(operand0.numberValue() < operand1.numberValue());
    }
  }


  @Override protected SoyValue visitGreaterThanOpNode(GreaterThanOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.longValue() > operand1.longValue());
    } else {
      return convertResult(operand0.numberValue() > operand1.numberValue());
    }
  }


  @Override protected SoyValue visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.longValue() <= operand1.longValue());
    } else {
      return convertResult(operand0.numberValue() <= operand1.numberValue());
    }
  }


  @Override protected SoyValue visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.longValue() >= operand1.longValue());
    } else {
      return convertResult(operand0.numberValue() >= operand1.numberValue());
    }
  }


  /**
   * Determines if the operand's string form can be equality-compared with a string.
   */
  private boolean compareString(StringData stringData, SoyValue other) {
    // This follows similarly to the Javascript specification, to ensure similar operation
    // over Javascript and Java: http://www.ecma-international.org/ecma-262/5.1/#sec-11.9.3
    if (other instanceof StringData || other instanceof SanitizedContent) {
      return stringData.stringValue().equals(other.toString());
    }
    if (other instanceof NumberData) {
      try {
        // Parse the string as a number.
        return Double.parseDouble(stringData.stringValue()) == other.numberValue();
      } catch (NumberFormatException nfe) {
        // Didn't parse as a number.
        return false;
      }
    }
    return false;
  }


  /**
   * Custom equality operator that smooths out differences between different Soy runtimes.
   *
   * This approximates Javascript's behavior, but is much easier to understand.
   */
  private boolean equals(SoyValue operand0, SoyValue operand1) {
    // Treat the case where either is a string specially.
    if (operand0 instanceof StringData) {
      return compareString((StringData) operand0, operand1);
    }
    if (operand1 instanceof StringData) {
      return compareString((StringData) operand1, operand0);
    }
    return operand0.equals(operand1);
  }


  @Override protected SoyValue visitEqualOpNode(EqualOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    return convertResult(equals(operand0, operand1));
  }


  @Override protected SoyValue visitNotEqualOpNode(NotEqualOpNode node) {

    SoyValue operand0 = visit(node.getChild(0));
    SoyValue operand1 = visit(node.getChild(1));
    return convertResult(!equals(operand0, operand1));
  }


  @Override protected SoyValue visitAndOpNode(AndOpNode node) {

    // Note: Short-circuit evaluation.
    SoyValue operand0 = visit(node.getChild(0));
    if (!operand0.coerceToBoolean()) {
      return convertResult(false);
    } else {
      SoyValue operand1 = visit(node.getChild(1));
      return convertResult(operand1.coerceToBoolean());
    }
  }


  @Override protected SoyValue visitOrOpNode(OrOpNode node) {

    // Note: Short-circuit evaluation.
    SoyValue operand0 = visit(node.getChild(0));
    if (operand0.coerceToBoolean()) {
      return convertResult(true);
    } else {
      SoyValue operand1 = visit(node.getChild(1));
      return convertResult(operand1.coerceToBoolean());
    }
  }


  @Override protected SoyValue visitConditionalOpNode(ConditionalOpNode node) {

    // Note: We only evaluate the part that we need.
    SoyValue operand0 = visit(node.getChild(0));
    if (operand0.coerceToBoolean()) {
      return visit(node.getChild(1));
    } else {
      return visit(node.getChild(2));
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.


  @Override protected SoyValue visitFunctionNode(FunctionNode node) {

    String fnName = node.getFunctionName();

    // Handle nonplugin functions.
    NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
    if (nonpluginFn != null) {
      switch (nonpluginFn) {
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case QUOTE_KEYS_IF_JS:
          return visitMapLiteralNode((MapLiteralNode) node.getChild(0));
        default:
          throw new AssertionError();
      }
    }

    // Handle plugin functions.
    List<SoyValue> args = this.visitChildren(node);
    SoyJavaFunction fn = soyJavaFunctionsMap.get(fnName);
    if (fn == null) {
      throw new RenderException(
          "Failed to find Soy function with name '" + fnName + "'" +
          " (function call \"" + node.toSourceString() + "\").");
    }
    // Note: Arity has already been checked by CheckFunctionCallsVisitor.
    return computeFunctionHelper(fn, args, node);
  }


  /**
   * Protected helper for {@code computeFunction}. This helper exists so that subclasses can
   * override it.
   *
   * @param fn The function object.
   * @param args The arguments to the function.
   * @param fnNode The function node. Only used for error reporting.
   * @return The result of the function called on the given arguments.
   */
  protected SoyValue computeFunctionHelper(
      SoyJavaFunction fn, List<SoyValue> args, FunctionNode fnNode) {

    try {
      return fn.computeForJava(args);
    } catch (Exception e) {
      throw new RenderException(
          "While computing function \"" + fnNode.toSourceString() + "\": " + e.getMessage(), e);
    }
  }


  private SoyValue visitIsFirstFunction(FunctionNode node) {

    int localVarIndex;
    try {
      VarRefNode dataRef = (VarRefNode) node.getChild(0);
      localVarIndex = env.getIndex((LoopVar) dataRef.getDefnDecl());
    } catch (Exception e) {
      throw new RenderException(
          "Failed to evaluate function call " + node.toSourceString() + ".",
          e);
    }
    return convertResult(localVarIndex == 0);
  }


  private SoyValue visitIsLastFunction(FunctionNode node) {

    boolean isLast;
    try {
      VarRefNode dataRef = (VarRefNode) node.getChild(0);
      isLast = env.isLast((LoopVar) dataRef.getDefnDecl());
    } catch (Exception e) {
      throw new RenderException(
          "Failed to evaluate function call " + node.toSourceString() + ".",
          e);
    }
    return convertResult(isLast);
  }


  private SoyValue visitIndexFunction(FunctionNode node) {

    int localVarIndex;
    try {
      VarRefNode dataRef = (VarRefNode) node.getChild(0);
      localVarIndex = env.getIndex((LoopVar) dataRef.getDefnDecl());
    } catch (Exception e) {
      throw new RenderException(
          "Failed to evaluate function call " + node.toSourceString() + ".",
          e);
    }
    return convertResult(localVarIndex);
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to convert a boolean result.
   * @param b The boolean to convert.
   */
  private SoyValue convertResult(boolean b) {
    return BooleanData.forValue(b);
  }


  /**
   * Private helper to convert an integer result.
   * @param i The integer to convert.
   */
  private SoyValue convertResult(long i) {
    return IntegerData.forValue(i);
  }


  /**
   * Private helper to convert a float result.
   * @param f The float to convert.
   */
  private SoyValue convertResult(double f) {
    return FloatData.forValue(f);
  }


  /**
   * Private helper to convert a string result.
   * @param s The string to convert.
   */
  private SoyValue convertResult(String s) {
    return StringData.forValue(s);
  }


  /**
   * Class that represents a sentinel value indicating that a null-safety
   * check failed. This value should never "leak" outside this class, in other words,
   * no code outside of this class should ever see a value of this type.
   */
  private static final class NullSafetySentinel extends SoyAbstractValue {

    /** Static singleton instance of SafeNullData. */
    public static final NullSafetySentinel INSTANCE = new NullSafetySentinel();

    private NullSafetySentinel() {}

    @Override public boolean equals(SoyValue other) {
      return other == INSTANCE;
    }

    @Override public boolean coerceToBoolean() {
      return false;
    }

    @Override public String coerceToString() {
      return "null";
    }

    @Override public void render(Appendable appendable) throws IOException {
      appendable.append(coerceToString());
    }
  }

}
