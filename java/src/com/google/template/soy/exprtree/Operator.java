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

import com.google.common.base.Pair;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import static com.google.template.soy.exprtree.Operator.Constants.OPERAND_0;
import static com.google.template.soy.exprtree.Operator.Constants.OPERAND_1;
import static com.google.template.soy.exprtree.Operator.Constants.OPERAND_2;
import static com.google.template.soy.exprtree.Operator.Constants.SP;
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
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * Enum of Soy expression operators.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public enum Operator {


  NEGATIVE(
      ImmutableList.of(new Token("-"), OPERAND_0),
      8, Associativity.RIGHT, "- (unary)", NegativeOpNode.class),
  NOT(
      ImmutableList.of(new Token("not"), SP, OPERAND_0),
      8, Associativity.RIGHT, NotOpNode.class),

  TIMES(
      ImmutableList.of(OPERAND_0, SP, new Token("*"), SP, OPERAND_1),
      7, Associativity.LEFT, TimesOpNode.class),
  DIVIDE_BY(
      ImmutableList.of(OPERAND_0, SP, new Token("/"), SP, OPERAND_1),
      7, Associativity.LEFT, DivideByOpNode.class),
  MOD(
      ImmutableList.of(OPERAND_0, SP, new Token("%"), SP, OPERAND_1),
      7, Associativity.LEFT, ModOpNode.class),

  PLUS(
      ImmutableList.of(OPERAND_0, SP, new Token("+"), SP, OPERAND_1),
      6, Associativity.LEFT, PlusOpNode.class),
  MINUS(
      ImmutableList.of(OPERAND_0, SP, new Token("-"), SP, OPERAND_1),
      6, Associativity.LEFT, "- (binary)", MinusOpNode.class),

  LESS_THAN(
      ImmutableList.of(OPERAND_0, SP, new Token("<"), SP, OPERAND_1),
      5, Associativity.LEFT, LessThanOpNode.class),
  GREATER_THAN(
      ImmutableList.of(OPERAND_0, SP, new Token(">"), SP, OPERAND_1),
      5, Associativity.LEFT, GreaterThanOpNode.class),
  LESS_THAN_OR_EQUAL(
      ImmutableList.of(OPERAND_0, SP, new Token("<="), SP, OPERAND_1),
      5, Associativity.LEFT, LessThanOrEqualOpNode.class),
  GREATER_THAN_OR_EQUAL(
      ImmutableList.of(OPERAND_0, SP, new Token(">="), SP, OPERAND_1),
      5, Associativity.LEFT, GreaterThanOrEqualOpNode.class),

  EQUAL(
      ImmutableList.of(OPERAND_0, SP, new Token("=="), SP, OPERAND_1),
      4, Associativity.LEFT, EqualOpNode.class),
  NOT_EQUAL(
      ImmutableList.of(OPERAND_0, SP, new Token("!="), SP, OPERAND_1),
      4, Associativity.LEFT, NotEqualOpNode.class),

  AND(
      ImmutableList.of(OPERAND_0, SP, new Token("and"), SP, OPERAND_1),
      3, Associativity.LEFT, AndOpNode.class),

  OR(
      ImmutableList.of(OPERAND_0, SP, new Token("or"), SP, OPERAND_1),
      2, Associativity.LEFT, OrOpNode.class),

  CONDITIONAL(
      ImmutableList.of(
          OPERAND_0, SP, new Token("?"), SP, OPERAND_1, SP, new Token(":"), SP, OPERAND_2),
      1, Associativity.RIGHT, ConditionalOpNode.class),
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
  private static final Map<Pair<String, Integer>, Operator> FETCH_MAP;
  static {
    Map<Pair<String, Integer>, Operator> fetchMap = Maps.newHashMap();
    for (Operator op : Operator.values()) {
      fetchMap.put(Pair.of(op.getTokenString(), op.getNumOperands()), op);
    }
    FETCH_MAP = Collections.unmodifiableMap(fetchMap);
  }


  /**
   * Fetches an Operator given the pair (tokenString, numOperands).
   *
   * @param tokenString A string listing the operator token. If multiple tokens (e.g. the ternary
   *     conditional operator), separate them using a space.
   * @param numOperands The number of operands this operator takes.
   * @return The matching Operator object.
   * @throws IllegalArgumentException If there is no Soy operator matching the given data.
   */
  public static Operator of(String tokenString, int numOperands) {
    Operator op = FETCH_MAP.get(Pair.of(tokenString, numOperands));
    if (op != null) {
      return op;
    } else {
      throw new IllegalArgumentException();
    }
  }


  // -----------------------------------------------------------------------------------------------


  /** The canonical syntax for this operator, including spacing. */
  private final List<SyntaxElement> syntax;

  /** This operator's token. Multiple tokens (e.g. the ternary conditional operator) are separated
   *  using a space. */
  private final String tokenString;

  /** The number of operands that this operator takes. */
  private final int numOperands;

  /** This operator's precedence level. */
  private final int precedence;

  /** This operator's associativity. */
  private final Associativity associativity;

  /** A short description of this operator (usually just the token string). */
  private final String description;

  /** The coresponding node class representing this operator. */
  private final Class<? extends OperatorNode> nodeClass;


  /**
   * Constructor that doesn't specify a description string (defaults to using the token string).
   * @param syntax The canonical syntax for this operator, including spacing.
   * @param precedence This operator's precedence level.
   * @param associativity This operator's associativity.
   * @param nodeClass The coresponding node class representing this operator.
   */
  private Operator(List<SyntaxElement> syntax, int precedence, Associativity associativity,
                   Class<? extends OperatorNode> nodeClass) {
    this(syntax, precedence, associativity, null, nodeClass);
  }


  /**
   * Constructor that specifies a description string.
   * @param syntax The canonical syntax for this operator, including spacing.
   * @param precedence This operator's precedence level.
   * @param associativity This operator's associativity.
   * @param description A short description of this operator.
   * @param nodeClass The coresponding node class representing this operator.
   */
  private Operator(List<SyntaxElement> syntax, int precedence, Associativity associativity,
                   String description, Class<? extends OperatorNode> nodeClass) {

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
    Preconditions.checkArgument(tokenString != null && numOperands > 0);
    this.tokenString = tokenString;
    this.numOperands = numOperands;

    this.precedence = precedence;
    this.associativity = associativity;
    this.description = (description != null) ? description : tokenString;
    this.nodeClass = nodeClass;
  }


  /** Returns the canonical syntax for this operator, including spacing. */
  public List<SyntaxElement> getSyntax() {
    return syntax;
  }

  /** Returns this operator's token. Multiple tokens (e.g. the ternary conditional operator) are
   *  separated using a space. */
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

  /** Returns the coresponding node class representing this operator. */
  public Class<? extends OperatorNode> getNodeClass() {
    return nodeClass;
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * Enum for an operator's associativity.
   */
  public static enum Associativity {
    /** Left-to-right. */
    LEFT,
    /** Right-to-left. */
    RIGHT
  }


  /**
   * Represents a syntax element (used in a syntax specification for an operator).
   */
  public static interface SyntaxElement {}


  /**
   * A syntax element for an operand.
   */
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


  /**
   * A syntax element for a token.
   */
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


  /**
   * A syntax element for a space character.
   */
  public static class Spacer implements SyntaxElement {

    private Spacer() {}
  }

}
