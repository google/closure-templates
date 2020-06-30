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
import com.google.template.soy.exprtree.ExprNode.AccessChainComponentNode;

/**
 * Container of nodes representing operators.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class OperatorNodes {

  private OperatorNodes() {}

  /** Node representing the unary '-' (negative) operator. */
  public static final class NegativeOpNode extends AbstractOperatorNode {

    public NegativeOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.NEGATIVE, operatorLocation);
    }

    private NegativeOpNode(NegativeOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.NEGATIVE_OP_NODE;
    }

    @Override
    public NegativeOpNode copy(CopyState copyState) {
      return new NegativeOpNode(this, copyState);
    }
  }

  /** Node representing the 'not' operator. */
  public static final class NotOpNode extends AbstractOperatorNode {

    public NotOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.NOT, operatorLocation);
    }

    private NotOpNode(NotOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.NOT_OP_NODE;
    }

    @Override
    public NotOpNode copy(CopyState copyState) {
      return new NotOpNode(this, copyState);
    }
  }

  /** Node representing the '*' (times) operator. */
  public static final class TimesOpNode extends AbstractOperatorNode {

    public TimesOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.TIMES, operatorLocation);
    }

    private TimesOpNode(TimesOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.TIMES_OP_NODE;
    }

    @Override
    public TimesOpNode copy(CopyState copyState) {
      return new TimesOpNode(this, copyState);
    }
  }

  /** Node representing the '/' (divde by) operator. */
  public static final class DivideByOpNode extends AbstractOperatorNode {

    public DivideByOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.DIVIDE_BY, operatorLocation);
    }

    private DivideByOpNode(DivideByOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.DIVIDE_BY_OP_NODE;
    }

    @Override
    public DivideByOpNode copy(CopyState copyState) {
      return new DivideByOpNode(this, copyState);
    }
  }

  /** Node representing the '%' (mod) operator. */
  public static final class ModOpNode extends AbstractOperatorNode {

    public ModOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.MOD, operatorLocation);
    }

    private ModOpNode(ModOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.MOD_OP_NODE;
    }

    @Override
    public ModOpNode copy(CopyState copyState) {
      return new ModOpNode(this, copyState);
    }
  }

  /** Node representing the '+' (plus) operator. */
  public static final class PlusOpNode extends AbstractOperatorNode {

    public PlusOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.PLUS, operatorLocation);
    }

    private PlusOpNode(PlusOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.PLUS_OP_NODE;
    }

    @Override
    public PlusOpNode copy(CopyState copyState) {
      return new PlusOpNode(this, copyState);
    }
  }

  /** Node representing the binary '-' (minus) operator. */
  public static final class MinusOpNode extends AbstractOperatorNode {

    public MinusOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.MINUS, operatorLocation);
    }

    private MinusOpNode(MinusOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.MINUS_OP_NODE;
    }

    @Override
    public MinusOpNode copy(CopyState copyState) {
      return new MinusOpNode(this, copyState);
    }
  }

  /** Node representing the '&lt;' (less than) operator. */
  public static final class LessThanOpNode extends AbstractOperatorNode {

    public LessThanOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.LESS_THAN, operatorLocation);
    }

    private LessThanOpNode(LessThanOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.LESS_THAN_OP_NODE;
    }

    @Override
    public LessThanOpNode copy(CopyState copyState) {
      return new LessThanOpNode(this, copyState);
    }
  }

  /** Node representing the '&gt;' (greater than) operator. */
  public static final class GreaterThanOpNode extends AbstractOperatorNode {

    public GreaterThanOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.GREATER_THAN, operatorLocation);
    }

    private GreaterThanOpNode(GreaterThanOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.GREATER_THAN_OP_NODE;
    }

    @Override
    public GreaterThanOpNode copy(CopyState copyState) {
      return new GreaterThanOpNode(this, copyState);
    }
  }

  /** Node representing the '&lt;=' (less than or equal) operator. */
  public static final class LessThanOrEqualOpNode extends AbstractOperatorNode {

    public LessThanOrEqualOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.LESS_THAN_OR_EQUAL, operatorLocation);
    }

    private LessThanOrEqualOpNode(LessThanOrEqualOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.LESS_THAN_OR_EQUAL_OP_NODE;
    }

    @Override
    public LessThanOrEqualOpNode copy(CopyState copyState) {
      return new LessThanOrEqualOpNode(this, copyState);
    }
  }

  /** Node representing the '&gt;=' (greater than or equal) operator. */
  public static final class GreaterThanOrEqualOpNode extends AbstractOperatorNode {

    public GreaterThanOrEqualOpNode(
        SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.GREATER_THAN_OR_EQUAL, operatorLocation);
    }

    private GreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.GREATER_THAN_OR_EQUAL_OP_NODE;
    }

    @Override
    public GreaterThanOrEqualOpNode copy(CopyState copyState) {
      return new GreaterThanOrEqualOpNode(this, copyState);
    }
  }

  /** Node representing the '==' (equal) operator. */
  public static final class EqualOpNode extends AbstractOperatorNode {

    public EqualOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.EQUAL, operatorLocation);
    }

    private EqualOpNode(EqualOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.EQUAL_OP_NODE;
    }

    @Override
    public EqualOpNode copy(CopyState copyState) {
      return new EqualOpNode(this, copyState);
    }
  }

  /** Node representing the '!=' (not equal) operator. */
  public static final class NotEqualOpNode extends AbstractOperatorNode {

    public NotEqualOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.NOT_EQUAL, operatorLocation);
    }

    private NotEqualOpNode(NotEqualOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.NOT_EQUAL_OP_NODE;
    }

    @Override
    public NotEqualOpNode copy(CopyState copyState) {
      return new NotEqualOpNode(this, copyState);
    }
  }

  /** Node representing the 'and' operator. */
  public static final class AndOpNode extends AbstractOperatorNode {

    public AndOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.AND, operatorLocation);
    }

    private AndOpNode(AndOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.AND_OP_NODE;
    }

    @Override
    public AndOpNode copy(CopyState copyState) {
      return new AndOpNode(this, copyState);
    }
  }

  /** Node representing the 'or' operator. */
  public static final class OrOpNode extends AbstractOperatorNode {

    public OrOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.OR, operatorLocation);
    }

    private OrOpNode(OrOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.OR_OP_NODE;
    }

    @Override
    public OrOpNode copy(CopyState copyState) {
      return new OrOpNode(this, copyState);
    }
  }

  /** Node representing the '?:' (null-coalescing) operator. */
  public static final class NullCoalescingOpNode extends AbstractOperatorNode {

    public NullCoalescingOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.NULL_COALESCING, operatorLocation);
    }

    private NullCoalescingOpNode(NullCoalescingOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    public ExprNode getLeftChild() {
      return getChild(0);
    }

    public ExprNode getRightChild() {
      return getChild(1);
    }

    @Override
    public Kind getKind() {
      return Kind.NULL_COALESCING_OP_NODE;
    }

    @Override
    public NullCoalescingOpNode copy(CopyState copyState) {
      return new NullCoalescingOpNode(this, copyState);
    }
  }

  /** Node representing the ternary '? :' (conditional) operator. */
  public static final class ConditionalOpNode extends AbstractOperatorNode {

    public ConditionalOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.CONDITIONAL, operatorLocation);
    }

    private ConditionalOpNode(ConditionalOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.CONDITIONAL_OP_NODE;
    }

    @Override
    public ConditionalOpNode copy(CopyState copyState) {
      return new ConditionalOpNode(this, copyState);
    }
  }

  /** Node representing the non-null assertion '!' operator. */
  public static final class AssertNonNullOpNode extends AbstractOperatorNode
      implements AccessChainComponentNode {

    public AssertNonNullOpNode(SourceLocation sourceLocation, SourceLocation operatorLocation) {
      super(sourceLocation, Operator.ASSERT_NON_NULL, operatorLocation);
    }

    private AssertNonNullOpNode(AssertNonNullOpNode orig, CopyState copyState) {
      super(orig, copyState);
    }

    @Override
    public Kind getKind() {
      return Kind.ASSERT_NON_NULL_OP_NODE;
    }

    @Override
    public AssertNonNullOpNode copy(CopyState copyState) {
      return new AssertNonNullOpNode(this, copyState);
    }
  }
}
