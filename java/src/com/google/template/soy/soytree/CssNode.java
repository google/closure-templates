/*
 * Copyright 2009 Google Inc.
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
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;


/**
 * Node representing a 'css' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
// TODO: Figure out why the CSS @component syntax doesn't support
// injected data ($ij.foo).  It looks like Soy is not checking CssNodes for
// injected data.
public class CssNode extends AbstractCommandNode
    implements StandaloneNode, StatementNode, ExprHolderNode {


  /**
   * Component name expression of a CSS command. Null if CSS command has no expression.
   * In the example <code>{css $componentName, SUFFIX}</code>, this would be
   * $componentName.
   */
  @Nullable private final ExprRootNode<?> componentNameExpr;

  /**
   * The selector text.  Either the entire command text of the CSS command, or
   * the suffix if you are using @component soy syntax:
   * <code>{css $componentName, SUFFIX}</code>
   */
  private final String selectorText;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   */
  public CssNode(int id, String commandText) throws SoySyntaxException {
    super(id, "css", commandText);

    int delimPos = commandText.lastIndexOf(',');
    if (delimPos != -1) {
      String componentNameText = commandText.substring(0, delimPos).trim();
      componentNameExpr = ExprParseUtils.parseExprElseThrowSoySyntaxException(
          componentNameText,
          "Invalid component name expression in 'css' command text \"" +
              componentNameText + "\".");
      selectorText = commandText.substring(delimPos + 1).trim();
    } else {
      componentNameExpr = null;
      selectorText = commandText;
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CssNode(CssNode orig) {
    super(orig);
    this.componentNameExpr =
        (orig.componentNameExpr != null) ? orig.componentNameExpr.clone() : null;
    this.selectorText = orig.selectorText;
  }


  @Override public Kind getKind() {
    return Kind.CSS_NODE;
  }


  /** Returns the parsed component name expression, or null if this node has no expression. */
  public ExprRootNode<?> getComponentNameExpr() {
    return componentNameExpr;
  }


  /** Returns the component name text, or null if this node has no component expression. */
  public String getComponentNameText() {
    return (componentNameExpr != null) ? componentNameExpr.toSourceString() : null;
  }


  /** Returns the selector text from this command. */
  public String getSelectorText() {
    return selectorText;
  }


  @Override public List<ExprUnion> getAllExprUnions() {
    return (componentNameExpr != null) ?
        ImmutableList.of(new ExprUnion(componentNameExpr)) : Collections.<ExprUnion>emptyList();
  }


  @Override public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }


  @Override public CssNode clone() {
    return new CssNode(this);
  }

}
