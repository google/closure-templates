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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import java.util.List;

/**
 * Node representing a 'print' directive.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PrintDirectiveNode extends AbstractSoyNode implements ExprHolderNode {

  /** The directive name (including vertical bar). */
  private final String name;

  /** The parsed args. */
  private final ImmutableList<ExprRootNode> args;

  public PrintDirectiveNode(
      int id, SourceLocation location, String name, ImmutableList<ExprNode> args) {
    super(id, location);
    Preconditions.checkArgument(name.charAt(0) == '|');
    this.name = name;
    this.args = ExprRootNode.wrap(args);
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private PrintDirectiveNode(PrintDirectiveNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    List<ExprRootNode> tempArgs = Lists.newArrayListWithCapacity(orig.args.size());
    for (ExprRootNode origArg : orig.args) {
      tempArgs.add(origArg.copy(copyState));
    }
    this.args = ImmutableList.copyOf(tempArgs);
  }

  @Override
  public Kind getKind() {
    return Kind.PRINT_DIRECTIVE_NODE;
  }

  /** Returns the directive name (including vertical bar). */
  public String getName() {
    return name;
  }

  /** The parsed args. */
  public List<ExprRootNode> getArgs() {
    return args;
  }

  @Override
  public String toSourceString() {
    return args.isEmpty() ? name : name + ":" + SoyTreeUtils.toSourceString(args);
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return args;
  }

  @Override
  public PrintDirectiveNode copy(CopyState copyState) {
    return new PrintDirectiveNode(this, copyState);
  }
}
