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


/**
 * Container of nodes representing operators.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class OperatorNodes {

  private OperatorNodes() {}


  /**
   * Node representing the unary '-' (negative) operator.
   */
  public static class NegativeOpNode extends AbstractOperatorNode {

    public NegativeOpNode() { super(Operator.NEGATIVE); }

    protected NegativeOpNode(NegativeOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.NEGATIVE_OP_NODE; }

    @Override public NegativeOpNode clone() { return new NegativeOpNode(this); }
  }


  /**
   * Node representing the 'not' operator.
   */
  public static class NotOpNode extends AbstractOperatorNode {

    public NotOpNode() { super(Operator.NOT); }

    protected NotOpNode(NotOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.NOT_OP_NODE; }

    @Override public NotOpNode clone() { return new NotOpNode(this); }
  }


  /**
   * Node representing the '*' (times) operator.
   */
  public static class TimesOpNode extends AbstractOperatorNode {

    public TimesOpNode() { super(Operator.TIMES); }

    protected TimesOpNode(TimesOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.TIMES_OP_NODE; }

    @Override public TimesOpNode clone() { return new TimesOpNode(this); }
  }


  /**
   * Node representing the '/' (divde by) operator.
   */
  public static class DivideByOpNode extends AbstractOperatorNode {

    public DivideByOpNode() { super(Operator.DIVIDE_BY); }

    protected DivideByOpNode(DivideByOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.DIVIDE_BY_OP_NODE; }

    @Override public DivideByOpNode clone() { return new DivideByOpNode(this); }
  }


  /**
   * Node representing the '%' (mod) operator.
   */
  public static class ModOpNode extends AbstractOperatorNode {

    public ModOpNode() { super(Operator.MOD); }

    protected ModOpNode(ModOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.MOD_OP_NODE; }

    @Override public ModOpNode clone() { return new ModOpNode(this); }
  }


  /**
   * Node representing the '+' (plus) operator.
   */
  public static class PlusOpNode extends AbstractOperatorNode {

    public PlusOpNode() { super(Operator.PLUS); }

    protected PlusOpNode(PlusOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.PLUS_OP_NODE; }

    @Override public PlusOpNode clone() { return new PlusOpNode(this); }
  }


  /**
   * Node representing the binary '-' (minus) operator.
   */
  public static class MinusOpNode extends AbstractOperatorNode {

    public MinusOpNode() { super(Operator.MINUS); }

    protected MinusOpNode(MinusOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.MINUS_OP_NODE; }

    @Override public MinusOpNode clone() { return new MinusOpNode(this); }
  }


  /**
   * Node representing the '&lt;' (less than) operator.
   */
  public static class LessThanOpNode extends AbstractOperatorNode {

    public LessThanOpNode() { super(Operator.LESS_THAN); }

    protected LessThanOpNode(LessThanOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.LESS_THAN_OP_NODE; }

    @Override public LessThanOpNode clone() { return new LessThanOpNode(this); }
  }


  /**
   * Node representing the '&gt;' (greater than) operator.
   */
  public static class GreaterThanOpNode extends AbstractOperatorNode {

    public GreaterThanOpNode() { super(Operator.GREATER_THAN); }

    protected GreaterThanOpNode(GreaterThanOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.GREATER_THAN_OP_NODE; }

    @Override public GreaterThanOpNode clone() { return new GreaterThanOpNode(this); }
  }


  /**
   * Node representing the '&lt;=' (less than or equal) operator.
   */
  public static class LessThanOrEqualOpNode extends AbstractOperatorNode {

    public LessThanOrEqualOpNode() { super(Operator.LESS_THAN_OR_EQUAL); }

    protected LessThanOrEqualOpNode(LessThanOrEqualOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.LESS_THAN_OR_EQUAL_OP_NODE; }

    @Override public LessThanOrEqualOpNode clone() { return new LessThanOrEqualOpNode(this); }
  }


  /**
   * Node representing the '&gt;=' (greater than or equal) operator.
   */
  public static class GreaterThanOrEqualOpNode extends AbstractOperatorNode {

    public GreaterThanOrEqualOpNode() { super(Operator.GREATER_THAN_OR_EQUAL); }

    protected GreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.GREATER_THAN_OR_EQUAL_OP_NODE; }

    @Override public GreaterThanOrEqualOpNode clone() { return new GreaterThanOrEqualOpNode(this); }
  }


  /**
   * Node representing the '==' (equal) operator.
   */
  public static class EqualOpNode extends AbstractOperatorNode {

    public EqualOpNode() { super(Operator.EQUAL); }

    protected EqualOpNode(EqualOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.EQUAL_OP_NODE; }

    @Override public EqualOpNode clone() { return new EqualOpNode(this); }
  }


  /**
   * Node representing the '!=' (not equal) operator.
   */
  public static class NotEqualOpNode extends AbstractOperatorNode {

    public NotEqualOpNode() { super(Operator.NOT_EQUAL); }

    protected NotEqualOpNode(NotEqualOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.NOT_EQUAL_OP_NODE; }

    @Override public NotEqualOpNode clone() { return new NotEqualOpNode(this); }
  }


  /**
   * Node representing the 'and' operator.
   */
  public static class AndOpNode extends AbstractOperatorNode {

    public AndOpNode() { super(Operator.AND); }

    protected AndOpNode(AndOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.AND_OP_NODE; }

    @Override public AndOpNode clone() { return new AndOpNode(this); }
  }


  /**
   * Node representing the 'or' operator.
   */
  public static class OrOpNode extends AbstractOperatorNode {

    public OrOpNode() { super(Operator.OR); }

    protected OrOpNode(OrOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.OR_OP_NODE; }

    @Override public OrOpNode clone() { return new OrOpNode(this); }
  }


  /**
   * Node representing the ternary '? :' (conditional) operator.
   */
  public static class ConditionalOpNode extends AbstractOperatorNode {

    public ConditionalOpNode() { super(Operator.CONDITIONAL); }

    protected ConditionalOpNode(ConditionalOpNode orig) { super(orig); }

    @Override public Kind getKind() { return Kind.CONDITIONAL_OP_NODE; }

    @Override public ConditionalOpNode clone() { return new ConditionalOpNode(this); }
  }

}
