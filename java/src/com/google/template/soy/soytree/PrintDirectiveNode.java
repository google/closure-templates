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
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
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

  /** The text of all the args. */
  private final String argsText;

  /** The parsed args. */
  private final ImmutableList<ExprRootNode> args;

  private PrintDirectiveNode(
      int id,
      String name,
      ImmutableList<ExprRootNode> args,
      String argsText,
      SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.name = name;
    this.args = args;
    this.argsText = argsText;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private PrintDirectiveNode(PrintDirectiveNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.argsText = orig.argsText;
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
    return name + ((argsText.length() > 0) ? ":" + argsText : "");
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return ExprUnion.createList(args);
  }

  @Override
  public PrintDirectiveNode copy(CopyState copyState) {
    return new PrintDirectiveNode(this, copyState);
  }

  /** Builder for {@link PrintDirectiveNode}. */
  public static final class Builder {
    private final int id;
    private final String name;
    private final String argsText;
    private final SourceLocation sourceLocation;

    /**
     * @param id The node's id.
     * @param name The directive name in source code (including vertical bar).
     * @param argsText The text of all the args, or empty string if none (usually empty string).
     * @param sourceLocation The node's source location.
     */
    public Builder(int id, String name, String argsText, SourceLocation sourceLocation) {
      this.id = id;
      this.name = name;
      this.argsText = argsText;
      this.sourceLocation = sourceLocation;
    }

    /**
     * Returns a new {@link PrintDirectiveNode} from the state of this builder, reporting syntax
     * errors to the given {@link ErrorReporter}.
     */
    public PrintDirectiveNode build(SoyParsingContext context) {
      ImmutableList<ExprRootNode> args = parseArgs(context);
      return new PrintDirectiveNode(id, name, args, argsText, sourceLocation);
    }

    private ImmutableList<ExprRootNode> parseArgs(SoyParsingContext context) {
      if (this.argsText.isEmpty()) {
        return ImmutableList.of();
      }
      ImmutableList.Builder<ExprRootNode> args = ImmutableList.builder();
      for (ExprNode expr :
          new ExpressionParser(argsText, sourceLocation, context).parseExpressionList()) {
        args.add(new ExprRootNode(expr));
      }
      return args.build();
    }
  }
}
