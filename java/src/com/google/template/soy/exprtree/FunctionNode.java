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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.shared.restricted.SoyFunction;
import javax.annotation.Nullable;

/**
 * A node representing a function (with args as children).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class FunctionNode extends AbstractParentExprNode {

  /** The function name. */
  private final String functionName;

  @Nullable private SoyFunction soyFunction;

  /**
   * @param functionName The function name.
   * @param sourceLocation The node's source location.
   */
  public FunctionNode(String functionName, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.functionName = functionName;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private FunctionNode(FunctionNode orig, CopyState copyState) {
    super(orig, copyState);
    this.functionName = orig.functionName;
    this.soyFunction = orig.soyFunction;
  }

  @Override
  public Kind getKind() {
    return Kind.FUNCTION_NODE;
  }

  /** Returns the function name. */
  public String getFunctionName() {
    return functionName;
  }

  @Nullable
  public SoyFunction getSoyFunction() {
    return soyFunction;
  }

  public void setSoyFunction(SoyFunction soyFunction) {
    this.soyFunction = soyFunction;
  }

  @Override
  public String toSourceString() {

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

  @Override
  public FunctionNode copy(CopyState copyState) {
    return new FunctionNode(this, copyState);
  }
}
