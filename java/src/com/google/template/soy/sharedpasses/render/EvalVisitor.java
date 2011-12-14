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

import com.google.common.collect.Maps;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataRefIndexNode;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
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
import com.google.template.soy.shared.internal.NonpluginFunction;
import com.google.template.soy.shared.restricted.SoyJavaRuntimeFunction;

import java.util.Deque;
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
 * {@code SoyData} object.
 *
 */
public class EvalVisitor extends AbstractReturningExprNodeVisitor<SoyData> {


  /**
   * Interface for a factory that creates an EvalVisitor.
   */
  public static interface EvalVisitorFactory {

    /**
     * Creates an EvalVisitor.
     * @param data The current template data.
     * @param ijData The current injected data.
     * @param env The current environment.
     * @return The newly created EvalVisitor instance.
     */
    public EvalVisitor create(
        @Nullable SoyMapData data, @Nullable SoyMapData ijData, Deque<Map<String, SoyData>> env);
  }


  /** Map of all SoyJavaRuntimeFunctions (name to function). */
  private final Map<String, SoyJavaRuntimeFunction> soyJavaRuntimeFunctionsMap;

  /** The current template data. */
  private final SoyMapData data;

  /** The current injected data. */
  private final SoyMapData ijData;

  /** The current environment. */
  private final Deque<Map<String, SoyData>> env;


  /**
   * @param soyJavaRuntimeFunctionsMap Map of all SoyJavaRuntimeFunctions (name to function). Can be
   *     null if the subclass that is calling this constructor plans to override the default
   *     implementation of {@code computeFunction()}.
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param env The current environment.
   */
  protected EvalVisitor(
      @Nullable Map<String, SoyJavaRuntimeFunction> soyJavaRuntimeFunctionsMap,
      @Nullable SoyMapData data, @Nullable SoyMapData ijData, Deque<Map<String, SoyData>> env) {

    this.soyJavaRuntimeFunctionsMap = soyJavaRuntimeFunctionsMap;
    this.data = data;
    this.ijData = ijData;
    this.env = env;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected SoyData visitExprRootNode(ExprRootNode<?> node) {
    return visit(node.getChild(0));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives.


  @Override protected SoyData visitNullNode(NullNode node) {
    return NullData.INSTANCE;
  }


  @Override protected SoyData visitBooleanNode(BooleanNode node) {
    return convertResult(node.getValue());
  }


  @Override protected SoyData visitIntegerNode(IntegerNode node) {
    return convertResult(node.getValue());
  }


  @Override protected SoyData visitFloatNode(FloatNode node) {
    return convertResult(node.getValue());
  }


  @Override protected SoyData visitStringNode(StringNode node) {
    return convertResult(node.getValue());
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.


  @Override protected SoyData visitListLiteralNode(ListLiteralNode node) {
    return new SoyListData(visitChildren(node));
  }


  @Override protected SoyData visitMapLiteralNode(MapLiteralNode node) {

    Map<String, SoyData> map = Maps.newHashMap();

    for (int i = 0, n = node.numChildren(); i < n; i += 2) {
      SoyData key = visit(node.getChild(i));
      if (! (key instanceof StringData)) {
        throw new RenderException(
            "Maps must have string keys (key \"" + node.getChild(i).toSourceString() + "\"" +
            " in map " + node.toSourceString() + " does not evaluate to a string).");
      }
      SoyData value = visit(node.getChild(i + 1));
      map.put(key.stringValue(), value);
    }

    return new SoyMapData(map);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.


  @Override protected SoyData visitDataRefNode(DataRefNode node) {

    // First resolve the first key, which may reference a variable, data, or injected data.
    SoyData value0 = resolveDataRefFirstKey(node);

    // Case 1: There is only one key. We already have the final value of the data reference.
    if (node.numChildren() == 1) {
      return value0;
    }

    // Case 2: There are more keys.
    SoyData value = value0;
    for (int i = 1; i < node.numChildren(); i++) {

      // We expect 'value' to be a CollectionData during every iteration.
      if (! (value instanceof CollectionData)) {
        return UndefinedData.INSTANCE;
      }

      ExprNode child = node.getChild(i);
      if (child instanceof DataRefKeyNode) {
        value = ((CollectionData) value).getSingle(((DataRefKeyNode) child).getKey());
      } else if (child instanceof DataRefIndexNode) {
        value = ((SoyListData) value).get(((DataRefIndexNode) child).getIndex());
      } else if (child instanceof GlobalNode) {
        throw new UnsupportedOperationException();
      } else {
        SoyData key = visit(child);
        if (key instanceof IntegerData) {
          value = ((SoyListData) value).get(((IntegerData) key).getValue());
        } else {
          value = ((CollectionData) value).getSingle(key.toString());
        }
      }
    }

    if (value != null) {
      return value;
    } else {
      return UndefinedData.INSTANCE;
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators.


  @Override protected SoyData visitNegativeOpNode(NegativeOpNode node) {

    SoyData operand = visit(node.getChild(0));
    if (operand instanceof IntegerData) {
      return convertResult( - operand.integerValue() );
    } else {
      return convertResult( - operand.floatValue() );
    }
  }


  @Override protected SoyData visitNotOpNode(NotOpNode node) {

    SoyData operand = visit(node.getChild(0));
    return convertResult( ! operand.toBoolean() );
  }


  @Override protected SoyData visitTimesOpNode(TimesOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.integerValue() * operand1.integerValue());
    } else {
      return convertResult(operand0.numberValue() * operand1.numberValue());
    }
  }


  @Override protected SoyData visitDivideByOpNode(DivideByOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
    return convertResult(operand0.numberValue() / operand1.numberValue());
  }


  @Override protected SoyData visitModOpNode(ModOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    return convertResult(operand0.integerValue() % operand1.integerValue());
  }


  @Override protected SoyData visitPlusOpNode(PlusOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.integerValue() + operand1.integerValue());
    } else if (operand0 instanceof StringData || operand1 instanceof StringData) {
      // String concatenation. Note we're calling toString() instead of stringValue() in case one
      // of the operands needs to be coerced to a string.
      return convertResult(operand0.toString() + operand1.toString());
    } else {
      return convertResult(operand0.numberValue() + operand1.numberValue());
    }
  }


  @Override protected SoyData visitMinusOpNode(MinusOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.integerValue() - operand1.integerValue());
    } else {
      return convertResult(operand0.numberValue() - operand1.numberValue());
    }
  }


  @Override protected SoyData visitLessThanOpNode(LessThanOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.integerValue() < operand1.integerValue());
    } else {
      return convertResult(operand0.numberValue() < operand1.numberValue());
    }
  }


  @Override protected SoyData visitGreaterThanOpNode(GreaterThanOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.integerValue() > operand1.integerValue());
    } else {
      return convertResult(operand0.numberValue() > operand1.numberValue());
    }
  }


  @Override protected SoyData visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.integerValue() <= operand1.integerValue());
    } else {
      return convertResult(operand0.numberValue() <= operand1.numberValue());
    }
  }


  @Override protected SoyData visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return convertResult(operand0.integerValue() >= operand1.integerValue());
    } else {
      return convertResult(operand0.numberValue() >= operand1.numberValue());
    }
  }


  @Override protected SoyData visitEqualOpNode(EqualOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    return convertResult(operand0.equals(operand1));
  }


  @Override protected SoyData visitNotEqualOpNode(NotEqualOpNode node) {

    SoyData operand0 = visit(node.getChild(0));
    SoyData operand1 = visit(node.getChild(1));
    return convertResult(!operand0.equals(operand1));
  }


  @Override protected SoyData visitAndOpNode(AndOpNode node) {

    // Note: Short-circuit evaluation.
    SoyData operand0 = visit(node.getChild(0));
    if (!operand0.toBoolean()) {
      return convertResult(false);
    } else {
      SoyData operand1 = visit(node.getChild(1));
      return convertResult(operand1.toBoolean());
    }
  }


  @Override protected SoyData visitOrOpNode(OrOpNode node) {

    // Note: Short-circuit evaluation.
    SoyData operand0 = visit(node.getChild(0));
    if (operand0.toBoolean()) {
      return convertResult(true);
    } else {
      SoyData operand1 = visit(node.getChild(1));
      return convertResult(operand1.toBoolean());
    }
  }


  @Override protected SoyData visitConditionalOpNode(ConditionalOpNode node) {

    // Note: We only evaluate the part that we need.
    SoyData operand0 = visit(node.getChild(0));
    if (operand0.toBoolean()) {
      return visit(node.getChild(1));
    } else {
      return visit(node.getChild(2));
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.


  @Override protected SoyData visitFunctionNode(FunctionNode node) {

    String fnName = node.getFunctionName();
    int numArgs = node.numChildren();

    // Handle nonplugin functions.
    NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
    if (nonpluginFn != null) {
      // TODO: Pass to check num args at compile time.
      if (numArgs != nonpluginFn.getNumArgs()) {
        throw new RenderException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      switch (nonpluginFn) {
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case HAS_DATA:
          return visitHasDataFunction();
        default:
          throw new AssertionError();
      }
    }

    // Handle plugin functions.
    List<SoyData> args = visitChildren(node);
    return computeFunction(fnName, args, node);
  }


  /**
   * Protected helper for visitFunctionNode() to compute a function.
   *
   * <p> This default implementation can be overridden by subclasses (such as TofuEvalVisitor) that
   * have access to a potentially larger set of functions.
   *
   * @param fnName The name of the function.
   * @param args The arguments to the function.
   * @param fnNode The function node. Only used for error reporting.
   * @return The result of the function called on the given arguments.
   */
  protected SoyData computeFunction(String fnName, List<SoyData> args, FunctionNode fnNode) {

    SoyJavaRuntimeFunction fn = soyJavaRuntimeFunctionsMap.get(fnName);
    if (fn == null) {
      throw new RenderException(
          "Failed to find Soy function with name '" + fnName + "'" +
          " (function call \"" + fnNode.toSourceString() + "\").");
    }

    // Arity has already been checked by CheckFunctionCallsVisitor.

    return computeFunctionHelper(fn, args, fnNode);
  }


  /**
   * Protected helper for {@code computeFunction}. This helper exists so that subclasses can
   * override it.
   * @param fn The function object.
   * @param args The arguments to the function.
   * @param fnNode The function node. Only used for error reporting.
   * @return The result of the function called on the given arguments.
   */
  protected SoyData computeFunctionHelper(
      SoyJavaRuntimeFunction fn, List<SoyData> args, FunctionNode fnNode) {

    try {
      return fn.compute(args);
    } catch (Exception e) {
      throw new RenderException(
          "Error while computing function \"" + fnNode.toSourceString() + "\": " + e.getMessage());
    }
  }


  private SoyData visitIsFirstFunction(FunctionNode node) {

    int localVarIndex;
    try {
      DataRefNode dataRef = (DataRefNode) node.getChild(0);
      String localVarName = dataRef.getFirstKey();
      localVarIndex = getLocalVar(localVarName + "__index").integerValue();
    } catch (Exception e) {
      throw new RenderException("Failed to evaluate function call " + node.toSourceString() + ".");
    }
    return convertResult(localVarIndex == 0);
  }


  private SoyData visitIsLastFunction(FunctionNode node) {

    int localVarIndex, localVarLastIndex;
    try {
      DataRefNode dataRef = (DataRefNode) node.getChild(0);
      String localVarName = dataRef.getFirstKey();
      localVarIndex = getLocalVar(localVarName + "__index").integerValue();
      localVarLastIndex = getLocalVar(localVarName + "__lastIndex").integerValue();
    } catch (Exception e) {
      throw new RenderException("Failed to evaluate function call " + node.toSourceString() + ".");
    }
    return convertResult(localVarIndex == localVarLastIndex);
  }


  private SoyData visitIndexFunction(FunctionNode node) {

    int localVarIndex;
    try {
      DataRefNode dataRef = (DataRefNode) node.getChild(0);
      String localVarName = dataRef.getFirstKey();
      localVarIndex = getLocalVar(localVarName + "__index").integerValue();
    } catch (Exception e) {
      throw new RenderException("Failed to evaluate function call " + node.toSourceString() + ".");
    }
    return convertResult(localVarIndex);
  }


  private SoyData visitHasDataFunction() {
    return convertResult(data != null);
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to convert a boolean result.
   * @param b The boolean to convert.
   */
  private SoyData convertResult(boolean b) {
    return BooleanData.forValue(b);
  }

  /**
   * Private helper to convert an integer result.
   * @param i The integer to convert.
   */
  private SoyData convertResult(int i) {
    return IntegerData.forValue(i);
  }

  /**
   * Private helper to convert a float result.
   * @param f The float to convert.
   */
  private SoyData convertResult(double f) {
    return FloatData.forValue(f);
  }

  /**
   * Private helper to convert a string result.
   * @param s The string to convert.
   */
  private SoyData convertResult(String s) {
    return StringData.forValue(s);
  }


  /**
   * Private helper to get the value of a local variable (from the environment).
   * Note: Throws an AssertionError if the given name is not defined in the environment.
   * @param localVarName The name of the local var to retrieve.
   * @return The value of the local var.
   */
  private SoyData getLocalVar(String localVarName) {

    for (Map<String, SoyData> envFrame : env) {
      SoyData value = envFrame.get(localVarName);
      if (value != null) {
        return value;
      }
    }

    throw new AssertionError();
  }


  /**
   * Private helper to get the value of the first part of a data ref.
   * @param dataRefNode The data ref whose first key we want to retrieve.
   * @return The value of the first key, or UndefinedData if it is not defined in the environment
   *     nor the template data.
   */
  protected SoyData resolveDataRefFirstKey(DataRefNode dataRefNode) {

    String firstKey = dataRefNode.getFirstKey();
    SoyData value = null;

    if (dataRefNode.isIjDataRef()) {
      value = ijData.getSingle(firstKey);

    } else {
      Boolean isLocalVarDataRef = dataRefNode.isLocalVarDataRef();  // null if unknown

      // Retrieve from the environment. Do this when (a) we know it's a local var data ref or (b) we
      // don't know either way.
      if (isLocalVarDataRef == Boolean.TRUE || isLocalVarDataRef == null) {
        if (env != null) {
          for (Map<String, SoyData> envFrame : env) {
            value = envFrame.get(firstKey);
            if (value != null) {
              break;
            }
          }
        }
      }

      // Retrieve from the data. Do this when (a) we know it's not a local var data ref or (b) we
      // don't know either way, but we failed to retrieve a nonnull value from the environment.
      if (isLocalVarDataRef == Boolean.FALSE || (isLocalVarDataRef == null && value == null)) {
        value = data.getSingle(firstKey);
      }
    }

    return (value != null) ? value : UndefinedData.INSTANCE;
  }

}
