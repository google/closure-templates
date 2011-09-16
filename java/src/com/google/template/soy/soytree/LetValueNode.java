/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

import java.util.List;


/**
 * Node representing a 'let' statement with a value expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class LetValueNode extends LetNode implements ExprHolderNode {


  /** The local variable name (without preceding '$'). */
  private final String varName;

  /** The value expression that the variable is set to. */
  private final ExprRootNode<?> valueExpr;


  /**
   * @param id The id for this node.
   * @param isLocalVarNameUniquified Whether the local var name is already uniquified (e.g. by
   *     appending node id).
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public LetValueNode(int id, boolean isLocalVarNameUniquified, String commandText) {
    super(id, isLocalVarNameUniquified, commandText);

    Pair<String, ExprRootNode<?>> parseResult = parseCommandTextHelper(commandText);
    varName = parseResult.first;
    valueExpr = parseResult.second;

    if (valueExpr == null) {
      throw new SoySyntaxException(
          "A 'let' tag should be self-ending (with a trailing '/') if and only if it also" + 
          " contains a value (invalid tag is {let " + commandText + " /}).");
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected LetValueNode(LetValueNode orig) {
    super(orig);
    this.varName = orig.varName;
    this.valueExpr = orig.valueExpr.clone();
  }


  @Override public Kind getKind() {
    return Kind.LET_VALUE_NODE;
  }


  @Override public String getVarName() {
    return varName;
  }


  /**
   * Returns the value expression that the variable is set to.
   */
  public ExprRootNode<?> getValueExpr() {
    return valueExpr;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(new ExprUnion(valueExpr));
  }


  @Override public SoyNode clone() {
    return new LetValueNode(this);
  }

}
