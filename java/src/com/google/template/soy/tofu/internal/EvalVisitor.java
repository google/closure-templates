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

package com.google.template.soy.tofu.internal;

import com.google.common.base.Join;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
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
import com.google.template.soy.shared.internal.ImpureFunction;
import com.google.template.soy.tofu.SoyTofuException;
import com.google.template.soy.tofu.restricted.SoyTofuFunction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * Visitor for evaluating the expression rooted at a given ExprNode.
 *
 * <p> {@link #exec} may be called on any expression. The result of evaluating the expression (in
 * the context of the {@code data} and {@code env} passed into the constructor) is returned as a
 * {@code SoyData} object.
 *
 * @author Kai Huang
 */
class EvalVisitor extends AbstractExprNodeVisitor<SoyData> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  static interface EvalVisitorFactory {

    /**
     * @param data The current template data.
     * @param env The current environment.
     */
    public EvalVisitor create(@Nullable SoyMapData data, Deque<Map<String, SoyData>> env);
  }


  /** Map of all SoyTofuFunctions (name to function). */
  private final Map<String, SoyTofuFunction> soyTofuFunctionsMap;

  /** The current template data. */
  private final SoyMapData data;

  /** The current environment. */
  private final Deque<Map<String, SoyData>> env;

  /** Stack of partial results of subtrees. */
  private Deque<SoyData> resultStack;


  /**
   * @param soyTofuFunctionsMap Map of all SoyTofuFunctions (name to function).
   * @param data The current template data.
   * @param env The current environment.
   */
  @Inject
  EvalVisitor(Map<String, SoyTofuFunction> soyTofuFunctionsMap, @Assisted @Nullable SoyMapData data,
              @Assisted Deque<Map<String, SoyData>> env) {
    this.soyTofuFunctionsMap = soyTofuFunctionsMap;
    this.data = data;
    this.env = env;
  }


  @Override protected void setup() {
    resultStack = new ArrayDeque<SoyData>();
  }


  /**
   * Returns the result of the expression evaluation. Only valid after calling {@code visit()}.
   * @return The expression result.
   */
  @Override protected SoyData getResult() {
    return resultStack.peek();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected void visitInternal(ExprRootNode<? extends ExprNode> node) {
    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives and data references (concrete classes).


  @Override protected void visitInternal(NullNode node) {
    resultStack.push(NullData.INSTANCE);
  }


  @Override protected void visitInternal(BooleanNode node) {
    pushResult(node.getValue());
  }


  @Override protected void visitInternal(IntegerNode node) {
    pushResult(node.getValue());
  }


  @Override protected void visitInternal(FloatNode node) {
    pushResult(node.getValue());
  }


  @Override protected void visitInternal(StringNode node) {
    pushResult(node.getValue());
  }


  @Override protected void visitInternal(DataRefNode node) {

    // First we resolve the first key, which may be a variable or a data key.
    String key0 = ((DataRefKeyNode) node.getChild(0)).getKey();
    SoyData value0 = getVarOrDataKey(key0);

    // Case 1: There is only one key. We already have the final value of the data reference.
    if (node.numChildren() == 1) {
      resultStack.push(value0);
      return;
    }

    // Case 2: There are more keys. We put the rest of the keys into a string list (along the way,
    // evaluate expressions that compute to keys, as necessary).
    List<String> keys = Lists.newArrayList();
    for (int i = 1; i < node.numChildren(); ++i) {
      ExprNode child = node.getChild(i);
      visit(child);
      keys.add(resultStack.pop().toString());
    }
    // Resolve the key string built from the rest of the keys.
    SoyData value = (value0 instanceof CollectionData) ?
                    ((CollectionData) value0).get(Join.join(".", keys)) /*may be null*/ : null;
    if (value != null) {
      resultStack.push(value);
    } else {
      resultStack.push(new UndefinedData());
    }
  }


  @Override protected void visitInternal(DataRefKeyNode node) {
    pushResult(node.getKey());
  }


  @Override protected void visitInternal(DataRefIndexNode node) {
    pushResult(node.getIndex());
  }


  @Override protected void visitInternal(GlobalNode node) {
    throw new UnsupportedOperationException();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators (concrete classes).


  @Override protected void visitInternal(NegativeOpNode node) {

    visitChildren(node);
    SoyData operand = resultStack.pop();
    if (operand instanceof IntegerData) {
      pushResult( - operand.integerValue() );
    } else {
      pushResult( - operand.floatValue() );
    }
  }


  @Override protected void visitInternal(NotOpNode node) {

    visitChildren(node);
    SoyData operand = resultStack.pop();
    pushResult( ! operand.toBoolean() );
  }


  @Override protected void visitInternal(TimesOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      pushResult(operand0.integerValue() * operand1.integerValue());
    } else {
      pushResult(operand0.numberValue() * operand1.numberValue());
    }
  }


  @Override protected void visitInternal(DivideByOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    // Note: Soy always performs floating-point division, even on two integers (like JavaScript).
    pushResult(operand0.numberValue() / operand1.numberValue());
  }


  @Override protected void visitInternal(ModOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    pushResult(operand0.integerValue() % operand1.integerValue());
  }


  @Override protected void visitInternal(PlusOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      pushResult(operand0.integerValue() + operand1.integerValue());
    } else if (operand0 instanceof StringData || operand1 instanceof StringData) {
      // String concatenation. Note we're calling toString() instead of stringValue() in case one
      // of the operands needs to be coerced to a string.
      pushResult(operand0.toString() + operand1.toString());
    } else {
      pushResult(operand0.numberValue() + operand1.numberValue());
    }
  }


  @Override protected void visitInternal(MinusOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      pushResult(operand0.integerValue() - operand1.integerValue());
    } else {
      pushResult(operand0.numberValue() - operand1.numberValue());
    }
  }


  @Override protected void visitInternal(LessThanOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      pushResult(operand0.integerValue() < operand1.integerValue());
    } else {
      pushResult(operand0.numberValue() < operand1.numberValue());
    }
  }


  @Override protected void visitInternal(GreaterThanOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      pushResult(operand0.integerValue() > operand1.integerValue());
    } else {
      pushResult(operand0.numberValue() > operand1.numberValue());
    }
  }


  @Override protected void visitInternal(LessThanOrEqualOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      pushResult(operand0.integerValue() <= operand1.integerValue());
    } else {
      pushResult(operand0.numberValue() <= operand1.numberValue());
    }
  }


  @Override protected void visitInternal(GreaterThanOrEqualOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      pushResult(operand0.integerValue() >= operand1.integerValue());
    } else {
      pushResult(operand0.numberValue() >= operand1.numberValue());
    }
  }


  @Override protected void visitInternal(EqualOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    pushResult(operand0.equals(operand1));
  }


  @Override protected void visitInternal(NotEqualOpNode node) {

    visitChildren(node);
    SoyData operand1 = resultStack.pop();
    SoyData operand0 = resultStack.pop();
    pushResult(!operand0.equals(operand1));
  }


  @Override protected void visitInternal(AndOpNode node) {

    // Note: Short-circuit evaluation.
    visit(node.getChild(0));
    SoyData operand0 = resultStack.pop();
    if (!operand0.toBoolean()) {
      pushResult(false);
    } else {
      visit(node.getChild(1));
      SoyData operand1 = resultStack.pop();
      pushResult(operand1.toBoolean());
    }
  }


  @Override protected void visitInternal(OrOpNode node) {

    // Note: Short-circuit evaluation.
    visit(node.getChild(0));
    SoyData operand0 = resultStack.pop();
    if (operand0.toBoolean()) {
      pushResult(true);
    } else {
      visit(node.getChild(1));
      SoyData operand1 = resultStack.pop();
      pushResult(operand1.toBoolean());
    }
  }


  @Override protected void visitInternal(ConditionalOpNode node) {

    // Note: We only evaluate the part that we need.
    visit(node.getChild(0));
    SoyData operand0 = resultStack.pop();
    if (operand0.toBoolean()) {
      visit(node.getChild(1));  // leave value on stack as the result
    } else {
      visit(node.getChild(2));  // leave value on stack as the result
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for functions.


  @Override protected void visitInternal(FunctionNode node) {

    String fnName = node.getFunctionName();
    int numArgs = node.numChildren();

    // Handle impure functions.
    ImpureFunction impureFn = ImpureFunction.forFunctionName(fnName);
    if (impureFn != null) {
      // TODO: Pass to check num args at compile time.
      if (numArgs != impureFn.getNumArgs()) {
        throw new SoyTofuException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      switch (impureFn) {
        case IS_FIRST:
          visitIsFirstFunction(node);
          return;
        case IS_LAST:
          visitIsLastFunction(node);
          return;
        case INDEX:
          visitIndexFunction(node);
          return;
        case HAS_DATA:
          visitHasDataFunction();
          return;
        default:
          throw new AssertionError();
      }
    }

    // Handle pure functions.
    SoyTofuFunction fn = soyTofuFunctionsMap.get(fnName);
    if (fn != null) {
      // TODO: Pass to check num args at compile time.
      if (! fn.getValidArgsSizes().contains(numArgs)) {
        throw new SoyTofuException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      List<SoyData> args = Lists.newArrayList();
      for (ExprNode child : node.getChildren()) {
        visit(child);
        args.add(resultStack.pop());
      }
      try {
        resultStack.push(fn.computeForTofu(args));
      } catch (Exception e) {
        throw new SoyTofuException(
            "Error while evaluating function \"" + node.toSourceString() + "\": " + e.getMessage());
      }
      return;
    }

    // Function not found.
    throw new SoyTofuException(
        "Failed to find SoyTofuFunction with name '" + fnName + "'" +
        " (function call \"" + node.toSourceString() + "\").");
  }


  private void visitIsFirstFunction(FunctionNode node) {

    int localVarIndex;
    try {
      DataRefNode dataRef = (DataRefNode) node.getChild(0);
      String localVarName = ((DataRefKeyNode) dataRef.getChild(0)).getKey();
      localVarIndex = getVarOrDataKey(localVarName + "__index").integerValue();
    } catch (Exception e) {
      throw new SoyTofuException("Failed to evaluate function call " + node.toSourceString() + ".");
    }
    pushResult(localVarIndex == 0);
  }


  private void visitIsLastFunction(FunctionNode node) {

    int localVarIndex, localVarLastIndex;
    try {
      DataRefNode dataRef = (DataRefNode) node.getChild(0);
      String localVarName = ((DataRefKeyNode) dataRef.getChild(0)).getKey();
      localVarIndex = getVarOrDataKey(localVarName + "__index").integerValue();
      localVarLastIndex = getVarOrDataKey(localVarName + "__lastIndex").integerValue();
    } catch (Exception e) {
      throw new SoyTofuException("Failed to evaluate function call " + node.toSourceString() + ".");
    }
    pushResult(localVarIndex == localVarLastIndex);
  }


  private void visitIndexFunction(FunctionNode node) {

    int localVarIndex;
    try {
      DataRefNode dataRef = (DataRefNode) node.getChild(0);
      String localVarName = ((DataRefKeyNode) dataRef.getChild(0)).getKey();
      localVarIndex = getVarOrDataKey(localVarName + "__index").integerValue();
    } catch (Exception e) {
      throw new SoyTofuException("Failed to evaluate function call " + node.toSourceString() + ".");
    }
    pushResult(localVarIndex);
  }


  private void visitHasDataFunction() {
    pushResult(data != null);
  }


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to push a boolean result onto the result stack.
   * @param b The boolean to push.
   */
  private void pushResult(boolean b) {
    resultStack.push(new BooleanData(b));
  }

  /**
   * Private helper to push an integer result onto the result stack.
   * @param i The integer to push.
   */
  private void pushResult(int i) {
    resultStack.push(new IntegerData(i));
  }

  /**
   * Private helper to push a float result onto the result stack.
   * @param f The float to push.
   */
  private void pushResult(double f) {
    resultStack.push(new FloatData(f));
  }

  /**
   * Private helper to push a string result onto the result stack.
   * @param s The string to push.
   */
  private void pushResult(String s) {
    resultStack.push(new StringData(s));
  }


  /**
   * Private helper to get the value of an identifier that is either a variable (from the
   * environment) or a top-level data key (from the template data).
   * @param ident The identifier to get the value of.
   * @return The value of the identifier, or null if it is not defined in the environment nor the
   *     template data.
   */
  private SoyData getVarOrDataKey(String ident) {

    SoyData value = null;

    // First try the environment.
    for (Map<String, SoyData> envFrame : env) {
      value = envFrame.get(ident);
      if (value != null) {
        return value;
      }
    }

    // Then try the data.
    value = data.get(ident);
    if (value != null) {
      return value;
    } else {
      return new UndefinedData();
    }
  }

}
