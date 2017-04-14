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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.primitive.UnknownType;

/**
 * Node representing a global.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class GlobalNode extends AbstractExprNode {

  public static GlobalNode error(SourceLocation location) {
    return new GlobalNode("error", location);
  }

  /** The name of the global. Not final in order to handle aliases in {@link RewriteGlobalsPass.} */
  private String name;

  private boolean suppressUnknownGlobalErrors;

  private PrimitiveNode value = null;
  private SoyType soyType = UnknownType.getInstance();

  /**
   * @param name The name of the global.
   * @param sourceLocation The node's source location.
   */
  public GlobalNode(String name, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.name = name;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private GlobalNode(GlobalNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.soyType = orig.soyType;
    this.value = orig.value;
  }

  @Override
  public Kind getKind() {
    return Kind.GLOBAL_NODE;
  }

  @Override
  public SoyType getType() {
    return soyType;
  }

  public void resolve(SoyType soyType, PrimitiveNode value) {
    this.soyType = checkNotNull(soyType);
    this.value = checkNotNull(value);
  }

  public boolean isResolved() {
    return value != null;
  }

  public PrimitiveNode getValue() {
    return value;
  }

  /** Returns the name of the global. */
  public String getName() {
    return name;
  }

  /** Only to be used by {@link RewriteGlobalsPass}. */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Call this method to suppress unknown global errors for this node. This is appropriate if other
   * errors have already been reported. TODO(lukes): consider upstreaming to Node. It may be useful
   * for other kinds of errors.
   */
  public void suppressUnknownGlobalErrors() {
    this.suppressUnknownGlobalErrors = true;
  }

  /** Returns true if 'unknown global' errors should not be reported for this node. */
  public boolean shouldSuppressUnknownGlobalErrors() {
    return suppressUnknownGlobalErrors;
  }

  @Override
  public String toSourceString() {
    return name;
  }

  @Override
  public GlobalNode copy(CopyState copyState) {
    return new GlobalNode(this, copyState);
  }
}
