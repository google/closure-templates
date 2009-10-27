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

package com.google.template.soy.exprtree;

import com.google.template.soy.basetree.Node;
import com.google.template.soy.basetree.ParentNode;


/**
 * This class defines the base interface for a node in the Soy expression parse tree, as well as a
 * number of subinterfaces that extend the base interface in various aspects. Every concrete node
 * implements some subset of these interfaces.
 *
 * The top level definition is the base ExprNode interface.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public interface ExprNode extends Node {

  @Override public ParentExprNode getParent();


  // -----------------------------------------------------------------------------------------------


  /**
   * A node in an expression parse tree that may be a parent.
   */
  public static interface ParentExprNode extends ExprNode, ParentNode<ExprNode> {}


  // -----------------------------------------------------------------------------------------------


  /**
   * A node representing an operator (with operands as children).
   */
  public static interface OperatorNode extends ParentExprNode {

    public Operator getOperator();
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * A node representing a primitive literal.
   */
  public static interface PrimitiveNode extends ExprNode {}

}
