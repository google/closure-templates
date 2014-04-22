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


/**
 * A node representing a function (with args as children).
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class FunctionNode extends AbstractParentExprNode {


  /** The function name. */
  private final String functionName;


  /**
   * @param functionName The function name.
   */
  public FunctionNode(String functionName) {
    this.functionName = functionName;
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected FunctionNode(FunctionNode orig) {
    super(orig);
    this.functionName = orig.functionName;
  }


  @Override public Kind getKind() {
    return Kind.FUNCTION_NODE;
  }


  /** Returns the function name. */
  public String getFunctionName() {
    return functionName;
  }


  @Override public String toSourceString() {

    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append(functionName).append('(');

    boolean isFirst = true;
    for (ExprNode child : getChildren()) {
      if (isFirst) {
        isFirst = false;
      } else {
        sourceSb.append(", ");
      }
      sourceSb.append(child.toSourceString());
    }

    sourceSb.append(')');
    return sourceSb.toString();
  }


  @Override public FunctionNode clone() {
    return new FunctionNode(this);
  }

}
