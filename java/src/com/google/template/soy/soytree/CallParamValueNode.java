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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;

import java.util.List;


/**
 * Node representing a 'param' with a value expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CallParamValueNode extends CallParamNode implements ExprHolderNode {


  /** The param key. */
  private final String key;

  /** The parsed expression for the param value. */
  private final ExprUnion valueExprUnion;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public CallParamValueNode(int id, String commandText) throws SoySyntaxException {
    super(id, commandText);

    CommandTextParseResult parseResult = parseCommandTextHelper(commandText);
    key = parseResult.key;
    valueExprUnion = parseResult.valueExprUnion;

    if (valueExprUnion == null) {
      throw new SoySyntaxException(
          "A 'param' tag should be self-ending (with a trailing '/') if and only if it also" +
          " contains a value (invalid tag is {param " + commandText + " /}).");
    }
    if (valueExprUnion.getExpr() == null) {
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallParamValueNode(CallParamValueNode orig) {
    super(orig);
    this.key = orig.key;
    this.valueExprUnion = (orig.valueExprUnion != null) ? orig.valueExprUnion.clone() : null;
  }


  @Override public Kind getKind() {
    return Kind.CALL_PARAM_VALUE_NODE;
  }


  @Override public String getKey() {
    return key;
  }


  /** Returns the expression text for the param value. */
  public String getValueExprText() {
    return valueExprUnion.getExprText();
  }


  /** Returns the parsed expression for the param value. */
  public ExprUnion getValueExprUnion() {
    return valueExprUnion;
  }


  @Override public String getTagString() {
    return buildTagStringHelper(true);
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return ImmutableList.of(valueExprUnion);
  }


  @Override public CallParamValueNode clone() {
    return new CallParamValueNode(this);
  }

}
