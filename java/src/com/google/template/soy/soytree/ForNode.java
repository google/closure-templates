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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyNode.ConditionalBlockNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.LocalVarBlockNode;
import com.google.template.soy.soytree.SoyNode.LoopNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.StatementNode;
import com.google.template.soy.soytree.defn.LocalVar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node representing a 'for' statement.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ForNode extends AbstractBlockCommandNode
    implements StandaloneNode,
        StatementNode,
        ConditionalBlockNode,
        LoopNode,
        ExprHolderNode,
        LocalVarBlockNode {

  /** The arguments to a {@code range(...)} expression in a {@code {for ...}} loop statement. */
  @AutoValue
  public abstract static class RangeArgs {
    private static final RangeArgs ERROR =
        create(VarRefNode.ERROR, VarRefNode.ERROR, VarRefNode.ERROR);

    static RangeArgs create(ExprNode start, ExprNode limit, ExprNode increment) {
      return new AutoValue_ForNode_RangeArgs(
          new ExprRootNode(start), new ExprRootNode(limit), new ExprRootNode(increment));
    }

    RangeArgs() {}

    /** The expression for the iteration start point. Default is {@code 0}. */
    public abstract ExprRootNode start();

    /** The expression for the iteration end point. This is interpreted as an exclusive limit. */
    public abstract ExprRootNode limit();

    /** The expression for the iteration increment. Default is {@code 1}. */
    public abstract ExprRootNode increment();

    /**
     * Returns true if it is statically known that the range is not empty.
     *
     * <p>Currently this is only possible if we have a range that contains constant values.
     */
    public final boolean definitelyNotEmpty() {
      long start;
      if (start().getRoot() instanceof IntegerNode) {
        start = ((IntegerNode) start().getRoot()).getValue();
      } else {
        return false; // if the start is not a constant then it might be empty
      }

      long limit;
      if (limit().getRoot() instanceof IntegerNode) {
        limit = ((IntegerNode) limit().getRoot()).getValue();
      } else {
        return false;
      }

      // NOTE: we don't need to consider the increment, since as long as start < limit, the start
      // will always be produced by the range
      return start < limit;
    }

    private RangeArgs copy(CopyState copyState) {
      return create(
          start().getRoot().copy(copyState),
          limit().getRoot().copy(copyState),
          increment().getRoot().copy(copyState));
    }
  }

  private static final SoyErrorKind INVALID_COMMAND_TEXT =
      SoyErrorKind.of("Invalid ''for'' command text");
  private static final SoyErrorKind INVALID_RANGE_SPECIFICATION =
      SoyErrorKind.of("Invalid range specification");
  private static final SoyErrorKind RANGE_OUT_OF_RANGE =
      SoyErrorKind.of("Range specification is too large: {0}");

  /** Regex pattern for the command text. */
  // 2 capturing groups: local var name, arguments to range()
  private static final Pattern COMMAND_TEXT_PATTERN =
      Pattern.compile(
          "( [$] \\w+ ) \\s+ in \\s+ range[(] \\s* (.*) \\s* [)]",
          Pattern.COMMENTS | Pattern.DOTALL);

  /** The Local variable for this loop. */
  private final LocalVar var;

  /** The parsed range args. */
  private final RangeArgs rangeArgs;

  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @param sourceLocation The source location for the {@code for }node.
   */
  public ForNode(
      int id, String commandText, SourceLocation sourceLocation, SoyParsingContext context) {
    super(id, sourceLocation, "for", commandText);

    Matcher matcher = COMMAND_TEXT_PATTERN.matcher(commandText);
    if (!matcher.matches()) {
      context.report(sourceLocation, INVALID_COMMAND_TEXT);
      this.rangeArgs = RangeArgs.ERROR;
      this.var = new LocalVar("error", this, null);
      // Return early to avoid IllegalStateException below
      return;
    }

    String varName = parseVarName(matcher.group(1), sourceLocation, context);
    List<ExprNode> rangeArgs = parseRangeArgs(matcher.group(2), sourceLocation, context);

    if (rangeArgs.size() > 3 || rangeArgs.isEmpty()) {
      context.report(sourceLocation, INVALID_RANGE_SPECIFICATION);
      this.rangeArgs = RangeArgs.ERROR;
    } else {
      // OK, now interpret the args

      // If there are 2 or more args, then the first is the 'start' value; default is 0
      ExprNode start =
          rangeArgs.size() >= 2 ? rangeArgs.get(0) : new IntegerNode(0, sourceLocation);

      // If there are 3 args, then the last one is the increment; default is 1
      ExprNode increment =
          rangeArgs.size() == 3 ? rangeArgs.get(2) : new IntegerNode(1, sourceLocation);

      // the limit is the first item if there is only one arg, otherwise it is the second arg
      ExprNode limit = rangeArgs.get(rangeArgs.size() == 1 ? 0 : 1);

      this.rangeArgs = RangeArgs.create(start, limit, increment);

      // Range args cannot be larger than 32-bit ints
      if (isOutOfRange(start)) {
        context.report(sourceLocation, RANGE_OUT_OF_RANGE, ((IntegerNode) start).getValue());
      }
      if (isOutOfRange(increment)) {
        context.report(sourceLocation, RANGE_OUT_OF_RANGE, ((IntegerNode) increment).getValue());
      }
      if (isOutOfRange(limit)) {
        context.report(sourceLocation, RANGE_OUT_OF_RANGE, ((IntegerNode) limit).getValue());
      }
    }

    var = new LocalVar(varName, this, null);
  }

  private static String parseVarName(
      String input, SourceLocation sourceLocation, SoyParsingContext context) {
    return new ExpressionParser(input, sourceLocation, context).parseVariable().getName();
  }

  private static List<ExprNode> parseRangeArgs(
      String input, SourceLocation sourceLocation, SoyParsingContext context) {
    return new ExpressionParser(input, sourceLocation, context).parseExpressionList();
  }

  private static boolean isOutOfRange(ExprNode node) {
    if (node instanceof IntegerNode) {
      long n = ((IntegerNode) node).getValue();
      return n > Integer.MAX_VALUE || n < Integer.MIN_VALUE;
    }
    return false;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ForNode(ForNode orig, CopyState copyState) {
    super(orig, copyState);
    this.var = new LocalVar(orig.var, this);
    this.rangeArgs = orig.rangeArgs.copy(copyState);
  }

  @Override
  public Kind getKind() {
    return Kind.FOR_NODE;
  }

  @Override
  public final LocalVar getVar() {
    return var;
  }

  @Override
  public final String getVarName() {
    return var.name();
  }

  /** Returns the parsed range args. */
  public RangeArgs getRangeArgs() {
    return rangeArgs;
  }

  @Override
  public List<ExprUnion> getAllExprUnions() {
    return ExprUnion.createList(
        ImmutableList.of(rangeArgs.start(), rangeArgs.limit(), rangeArgs.increment()));
  }

  @Override
  public BlockNode getParent() {
    return (BlockNode) super.getParent();
  }

  @Override
  public ForNode copy(CopyState copyState) {
    return new ForNode(this, copyState);
  }
}
