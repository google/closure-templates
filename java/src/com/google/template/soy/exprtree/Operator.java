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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.template.soy.exprtree.Operator.Associativity.LEFT;
import static com.google.template.soy.exprtree.Operator.Associativity.RIGHT;
import static com.google.template.soy.exprtree.Operator.Constants.OPERAND_0;
import static com.google.template.soy.exprtree.Operator.Constants.OPERAND_1;
import static com.google.template.soy.exprtree.Operator.Constants.OPERAND_2;
import static com.google.template.soy.exprtree.Operator.Constants.SP;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Enum of Soy expression operators.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public enum Operator {
  NEGATIVE(ImmutableList.of(new Token("-"), OPERAND_0), 8, RIGHT, "- (unary)") {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new NegativeOpNode(location);
    }
  },
  NOT(ImmutableList.of(new Token("not"), SP, OPERAND_0), 8, RIGHT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new NotOpNode(location);
    }
  },
  TIMES(ImmutableList.of(OPERAND_0, SP, new Token("*"), SP, OPERAND_1), 7, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new TimesOpNode(location);
    }
  },
  DIVIDE_BY(ImmutableList.of(OPERAND_0, SP, new Token("/"), SP, OPERAND_1), 7, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new DivideByOpNode(location);
    }
  },
  MOD(ImmutableList.of(OPERAND_0, SP, new Token("%"), SP, OPERAND_1), 7, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new ModOpNode(location);
    }
  },

  PLUS(ImmutableList.of(OPERAND_0, SP, new Token("+"), SP, OPERAND_1), 6, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new PlusOpNode(location);
    }
  },
  MINUS(ImmutableList.of(OPERAND_0, SP, new Token("-"), SP, OPERAND_1), 6, LEFT, "- (binary)") {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new MinusOpNode(location);
    }
  },

  LESS_THAN(ImmutableList.of(OPERAND_0, SP, new Token("<"), SP, OPERAND_1), 5, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new LessThanOpNode(location);
    }
  },
  GREATER_THAN(ImmutableList.of(OPERAND_0, SP, new Token(">"), SP, OPERAND_1), 5, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new GreaterThanOpNode(location);
    }
  },
  LESS_THAN_OR_EQUAL(ImmutableList.of(OPERAND_0, SP, new Token("<="), SP, OPERAND_1), 5, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new LessThanOrEqualOpNode(location);
    }
  },
  GREATER_THAN_OR_EQUAL(ImmutableList.of(OPERAND_0, SP, new Token(">="), SP, OPERAND_1), 5, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new GreaterThanOrEqualOpNode(location);
    }
  },

  EQUAL(ImmutableList.of(OPERAND_0, SP, new Token("=="), SP, OPERAND_1), 4, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new EqualOpNode(location);
    }
  },
  NOT_EQUAL(ImmutableList.of(OPERAND_0, SP, new Token("!="), SP, OPERAND_1), 4, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new NotEqualOpNode(location);
    }
  },

  AND(ImmutableList.of(OPERAND_0, SP, new Token("and"), SP, OPERAND_1), 3, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new AndOpNode(location);
    }
  },

  OR(ImmutableList.of(OPERAND_0, SP, new Token("or"), SP, OPERAND_1), 2, LEFT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new OrOpNode(location);
    }
  },

  NULL_COALESCING(ImmutableList.of(OPERAND_0, SP, new Token("?:"), SP, OPERAND_1), 1, RIGHT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new NullCoalescingOpNode(location);
    }
  },
  CONDITIONAL(
      ImmutableList.of(
          OPERAND_0, SP, new Token("?"), SP, OPERAND_1, SP, new Token(":"), SP, OPERAND_2),
      1,
      RIGHT) {
    @Override
    public OperatorNode createNode(SourceLocation location) {
      return new ConditionalOpNode(location);
    }
  },
  ;

  /** Constants used in the enum definitions above. */
  static class Constants {
    static final Spacer SP = new Spacer();
    static final Operand OPERAND_0 = new Operand(0);
    static final Operand OPERAND_1 = new Operand(1);
    static final Operand OPERAND_2 = new Operand(2);
  }

  // -----------------------------------------------------------------------------------------------

  /** Map used for fetching an Operator from the pair (tokenString, numOperands). */
  private static final ImmutableTable<String, Integer, Operator> OPERATOR_TABLE;

  static {
    ImmutableTable.Builder<String, Integer, Operator> builder = ImmutableTable.builder();
    for (Operator op : Operator.values()) {
      builder.put(op.getTokenString(), op.getNumOperands(), op);
    }
    OPERATOR_TABLE = builder.build();
  }

  /**
   * Create an operator node, given the token, precedence, and list of children (arguments).
   *
   * @param op A string listing the operator token. If multiple tokens (e.g. the ternary conditional
   *     operator), separate them using a space.
   * @param prec The precedence of an operator. Must match the precedence specified by {@code op}.
   * @param children The list of children (arguments) for the operator.
   * @return The matching OperatorNode.
   * @throws IllegalArgumentException If there is no Soy operator matching the given data.
   */
  public static final OperatorNode createOperatorNode(
      SourceLocation location, String op, int prec, ExprNode... children) {
    checkArgument(OPERATOR_TABLE.containsRow(op));

    Operator operator = OPERATOR_TABLE.get(op, children.length);
    if (operator.getPrecedence() != prec) {
      throw new IllegalArgumentException("invalid precedence " + prec + " for operator " + op);
    }
    return operator.createNode(location, children);
  }

  // -----------------------------------------------------------------------------------------------

  /** The canonical syntax for this operator, including spacing. */
  private final ImmutableList<SyntaxElement> syntax;

  /**
   * This operator's token. Multiple tokens (e.g. the ternary conditional operator) are separated
   * using a space.
   */
  private final String tokenString;

  /** The number of operands that this operator takes. */
  private final int numOperands;

  /** This operator's precedence level. */
  private final int precedence;

  /** This operator's associativity. */
  private final Associativity associativity;

  /** A short description of this operator (usually just the token string). */
  private final String description;

  /**
   * Constructor that doesn't specify a description string (defaults to using the token string).
   *
   * @param syntax The canonical syntax for this operator, including spacing.
   * @param precedence This operator's precedence level.
   * @param associativity This operator's associativity.
   */
  private Operator(
      ImmutableList<SyntaxElement> syntax, int precedence, Associativity associativity) {
    this(syntax, precedence, associativity, /* description= */ null);
  }

  /**
   * Constructor that specifies a description string.
   *
   * @param syntax The canonical syntax for this operator, including spacing.
   * @param precedence This operator's precedence level.
   * @param associativity This operator's associativity.
   * @param description A short description of this operator.
   */
  private Operator(
      ImmutableList<SyntaxElement> syntax,
      int precedence,
      Associativity associativity,
      @Nullable String description) {

    this.syntax = syntax;

    String tokenString = null;
    int numOperands = 0;
    for (SyntaxElement syntaxEl : syntax) {
      if (syntaxEl instanceof Operand) {
        numOperands += 1;
      } else if (syntaxEl instanceof Token) {
        if (tokenString == null) {
          tokenString = ((Token) syntaxEl).getValue();
        } else {
          tokenString += " " + ((Token) syntaxEl).getValue();
        }
      }
    }
    checkArgument(tokenString != null && numOperands > 0);
    this.tokenString = tokenString;
    this.numOperands = numOperands;

    this.precedence = precedence;
    this.associativity = associativity;
    this.description = (description != null) ? description : tokenString;
  }

  /** Returns the canonical syntax for this operator, including spacing. */
  public List<SyntaxElement> getSyntax() {
    return syntax;
  }

  /**
   * Returns this operator's token. Multiple tokens (e.g. the ternary conditional operator) are
   * separated using a space.
   */
  public String getTokenString() {
    return tokenString;
  }

  /** Returns the number of operands that this operator takes. */
  public int getNumOperands() {
    return numOperands;
  }

  /** Returns this operator's precedence level. */
  public int getPrecedence() {
    return precedence;
  }

  /** Returns this operator's associativity. */
  public Associativity getAssociativity() {
    return associativity;
  }

  /** Returns a short description of this operator (usually just the token string). */
  public String getDescription() {
    return description;
  }

  /** Creates a node representing this operator. */
  public abstract OperatorNode createNode(SourceLocation location);

  /** Creates a node representing this operator, with the given children. */
  public final OperatorNode createNode(SourceLocation location, ExprNode... children) {
    checkArgument(children.length == getNumOperands());
    OperatorNode node = createNode(location);
    for (ExprNode child : children) {
      node.addChild(child);
    }
    return node;
  }

  // -----------------------------------------------------------------------------------------------

  /** Enum for an operator's associativity. */
  public static enum Associativity {
    /** Left-to-right. */
    LEFT,
    /** Right-to-left. */
    RIGHT
  }

  /** Represents a syntax element (used in a syntax specification for an operator). */
  @Immutable
  public static interface SyntaxElement {}

  /** A syntax element for an operand. */
  @Immutable
  public static class Operand implements SyntaxElement {

    private final int index;

    private Operand(int index) {
      this.index = index;
    }

    /** Returns the index of this operand. */
    public int getIndex() {
      return index;
    }
  }

  /** A syntax element for a token. */
  @Immutable
  public static class Token implements SyntaxElement {

    private final String value;

    private Token(String value) {
      this.value = value;
    }

    /** Returns this token's string literal. */
    public String getValue() {
      return value;
    }
  }

  /** A syntax element for a space character. */
  @Immutable
  public static class Spacer implements SyntaxElement {

    private Spacer() {}
  }
}
