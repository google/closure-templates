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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Node representing a 'print' directive.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PrintDirectiveNode extends AbstractSoyNode implements ExprHolderNode {

  public static PrintDirectiveNode createSyntheticNode(
      int id, Identifier name, SourceLocation location, SoyPrintDirective printDirective) {
    PrintDirectiveNode node =
        new PrintDirectiveNode(id, name, location, ImmutableList.of(), /* isSynthetic=*/ true);
    node.setPrintDirective(printDirective);
    return node;
  }

  private final Identifier name;
  private SoyPrintDirective printDirective;
  private SoySourceFunction printDirectiveFunction;

  /** The parsed args. */
  private final ImmutableList<ExprRootNode> args;

  /**
   * This means that the directive was inserted by the compiler, typically the autoescaper.
   * Otherwise it means that a user wrote it.
   */
  private final boolean isSynthetic;

  public PrintDirectiveNode(
      int id, Identifier name, SourceLocation location, ImmutableList<ExprNode> args) {
    this(id, name, location, args, /* isSynthetic=*/ false);
  }

  private PrintDirectiveNode(
      int id,
      Identifier name,
      SourceLocation location,
      ImmutableList<ExprNode> args,
      boolean isSynthetic) {
    super(id, location);
    this.name = name;
    this.args = ExprRootNode.wrap(args);
    this.isSynthetic = isSynthetic;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private PrintDirectiveNode(PrintDirectiveNode orig, CopyState copyState) {
    super(orig, copyState);
    List<ExprRootNode> tempArgs = Lists.newArrayListWithCapacity(orig.args.size());
    for (ExprRootNode origArg : orig.args) {
      tempArgs.add(origArg.copy(copyState));
    }
    this.name = orig.name;
    this.args = ImmutableList.copyOf(tempArgs);
    this.printDirective = orig.printDirective;
    this.printDirectiveFunction = orig.printDirectiveFunction;
    this.isSynthetic = orig.isSynthetic;
  }

  @Override
  public Kind getKind() {
    return Kind.PRINT_DIRECTIVE_NODE;
  }

  /** Returns the directive name (including vertical bar). */
  public String getName() {
    return name.identifier();
  }

  public SourceLocation getNameLocation() {
    return name.location();
  }

  /** Returns true if this node was inserted by the autoescaper. */
  public boolean isSynthetic() {
    return isSynthetic;
  }

  /** The parsed args. */
  public ImmutableList<ExprRootNode> getArgs() {
    return getExprList();
  }

  @Override
  public String toSourceString() {
    return args.isEmpty() ? getName() : getName() + ":" + SoyTreeUtils.toSourceString(args);
  }

  @Override
  public ImmutableList<ExprRootNode> getExprList() {
    return args;
  }

  @Override
  public PrintDirectiveNode copy(CopyState copyState) {
    return new PrintDirectiveNode(this, copyState);
  }

  /** Returns the print directive for this node. */
  public SoyPrintDirective getPrintDirective() {
    checkState(printDirective != null, "setPrintDirective hasn't been called yet");
    return printDirective;
  }

  /** Returns the soy source function that can be used to implement this directive. */
  @Nullable
  public SoySourceFunction getPrintDirectiveFunction() {
    return printDirectiveFunction;
  }

  /** Sets the print directive. */
  public void setPrintDirective(SoyPrintDirective printDirective) {
    checkState(this.printDirective == null, "setPrintDirective has already been called");
    checkArgument(name.identifier().equals(printDirective.getName()));
    this.printDirective = checkNotNull(printDirective);
  }

  /** Sets the print directive. A later compiler pass will rewrite the node to use this function. */
  public void setPrintDirectiveFunction(SoySourceFunction sourceFunction) {
    checkState(
        this.printDirectiveFunction == null, "setPrintDirectiveFunction has already been called");
    this.printDirectiveFunction = checkNotNull(sourceFunction);
  }
}
