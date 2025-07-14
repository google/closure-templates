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

package com.google.template.soy.exprtree;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.NumberType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnknownType;

/** Node representing a global. */
public final class GlobalNode extends AbstractExprNode {

  /** Known Soy globals. */
  public enum KnownGlobal {
    PI("Math_PI", NumberType.getInstance()),
    E("Math_E", NumberType.getInstance()),
    NAN("Number_NaN", NumberType.getInstance()),
    NEGATIVE_INFINITY("Number_NEGATIVE_INFINITY", NumberType.getInstance()),
    POSITIVE_INFINITY("Number_POSITIVE_INFINITY", NumberType.getInstance()),
    ;

    private final String id;
    private final NumberType type;

    KnownGlobal(String id, NumberType type) {
      this.id = id;
      this.type = type;
    }

    public String getId() {
      return id;
    }

    public NumberType getType() {
      return type;
    }
  }

  private static final ImmutableMap<String, KnownGlobal> KNOWN_IDS =
      stream(KnownGlobal.values()).collect(toImmutableMap(KnownGlobal::getId, g -> g));

  private static GlobalNode error(SourceLocation location) {
    return new GlobalNode(Identifier.create("error", "error", location));
  }

  public static void replaceExprWithError(ExprNode expr) {
    GlobalNode errorNode = error(expr.getSourceLocation());
    errorNode.isErrorPlaceholder = true;
    expr.getParent().replaceChild(expr, errorNode);
  }

  private Identifier identifier;
  private boolean isErrorPlaceholder;

  public GlobalNode(Identifier identifier) {
    super(identifier.location());
    this.identifier = identifier;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private GlobalNode(GlobalNode orig, CopyState copyState) {
    super(orig, copyState);
    this.identifier = orig.identifier;
  }

  @Override
  public Kind getKind() {
    return Kind.GLOBAL_NODE;
  }

  public boolean isKnown() {
    return KNOWN_IDS.containsKey(getName());
  }

  public KnownGlobal getKnownGlobal() {
    return Preconditions.checkNotNull(KNOWN_IDS.get(getName()));
  }

  @Override
  public SoyType getType() {
    KnownGlobal known = KNOWN_IDS.get(getName());
    return known != null ? known.getType() : UnknownType.getInstance();
  }

  /** Returns the name of the global. */
  public String getName() {
    return identifier.identifier();
  }

  public Identifier getIdentifier() {
    return identifier;
  }

  public void resolve(String name) {
    this.identifier = Identifier.create(name, identifier.originalName(), identifier.location());
  }

  /**
   * Returns true if this node is the product of calling {@link #replaceExprWithError} and therefore
   * an error has already been reported to the compiler. This might be better implemented as a new
   * node type.
   */
  public boolean alreadyReportedError() {
    return isErrorPlaceholder;
  }

  @Override
  public String toSourceString() {
    return identifier.originalName();
  }

  @Override
  public GlobalNode copy(CopyState copyState) {
    return new GlobalNode(this, copyState);
  }
}
