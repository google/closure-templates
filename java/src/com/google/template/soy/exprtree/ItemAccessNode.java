/*
 * Copyright 2013 Google Inc.
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

import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.basetree.SyntaxVersionBound;

import java.util.Objects;

/**
 * Represents the bracket [] operation, which can either be used to access an
 * array element by index, or a map value by key. Following the naming convention
 * of Python, both array elements and map values are here referred to as "items".
 *
 * Note: This also includes the Soy syntax $foo.0, which is equivalent to
 * $foo[0]. (The old dot-index syntax is supported for legacy reasons, but its
 * use is discouraged in new templates.)
 *
 */
public final class ItemAccessNode extends DataAccessNode {


  /** Whether the source code uses dot access (i.e. $x.0 instead of $x[0]). */
  private final boolean isDotSyntax;


  /**
   * @param base The base expression, that is a reference to the object
   *     containing the item.
   * @param key An expression representing either an array index or a map
   *     key.
   * @param isNullSafe If true, checks during evaluation whether the base expression is null
   *     and returns null instead of causing an invalid dereference.
   * @param isDotSyntax If true, this node represents an expression of the form
   *     $foo.0, that is an integer after the dot.
   */
  public ItemAccessNode(ExprNode base, ExprNode key, boolean isNullSafe, boolean isDotSyntax) {
    super(base, isNullSafe);
    this.isDotSyntax = isDotSyntax;
    if (isDotSyntax) {
      maybeSetSyntaxVersionBound(new SyntaxVersionBound(
          SyntaxVersion.V2_2,
          "Dot access for list items is no longer allowed; use bracket access instead" +
              " (i.e. $x[0] instead of $x.0)."));
    }
    addChild(key); // Key is child 1, Base is child 0.
  }


  @Override public Kind getKind() {
    return Kind.ITEM_ACCESS_NODE;
  }


  /** Returns the key expression. */
  public ExprNode getKeyExprChild() {
    return this.getChild(1);
  }


  /**
   * Returns the source string for the part of the expression that accesses
   * the item - in other words, not including the base expression. This is
   * intended for use in reporting errors.
   */
  @Override public String getSourceStringSuffix() {
    if (isDotSyntax) {
      return (isNullSafe ? "?." : ".") + getChild(1).toSourceString();
    }
    return (isNullSafe ? "?[" : "[") + getChild(1).toSourceString() + "]";
  }


  @Override public ExprNode clone() {
    return new ItemAccessNode(getChild(0).clone(), getChild(1).clone(), isNullSafe, isDotSyntax);
  }


  @Override public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) { return false; }
    ItemAccessNode otherItemRef = (ItemAccessNode) other;
    return getChild(0).equals(otherItemRef.getChild(0)) &&
        getChild(1).equals(otherItemRef.getChild(1)) &&
        isNullSafe == otherItemRef.isNullSafe &&
        isDotSyntax == otherItemRef.isDotSyntax;
  }


  @Override public int hashCode() {
    return Objects.hash(this.getClass(), getChild(0), getChild(1));
  }
}
