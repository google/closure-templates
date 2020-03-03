/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;

/**
 * Reprensents an expression that the user put in parentheses (e.g. "(Expr)"). This node allows the
 * formatter to know where the user put parentheses (even where not necessary). We remove these
 * nodes right after the HtmlRewriter in the first compiler pass, since we know from the AST
 * structure which expressions actually require parentheses.
 */
public final class GroupNode extends AbstractParentExprNode {

  /**
   * @param expr The base expression inside the parentheses
   * @param sourceLocation The node's source location.
   */
  public GroupNode(ExprNode expr, SourceLocation sourceLocation) {
    super(sourceLocation);
    checkArgument(expr != null);
    addChild(expr);
  }

  private GroupNode(GroupNode orig, CopyState copyState) {
    super(orig, copyState);
  }

  @Override
  public String toSourceString() {
    return "(" + this.getChild(0).toSourceString() + ")";
  }

  @Override
  public GroupNode copy(CopyState copyState) {
    return new GroupNode(this, copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.GROUP_NODE;
  }
}
