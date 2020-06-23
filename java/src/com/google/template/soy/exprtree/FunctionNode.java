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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyFunctions;
import com.google.template.soy.shared.restricted.SoyPureFunction;
import com.google.template.soy.types.SoyType;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A node representing a function (with args as children).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class FunctionNode extends AbstractParentExprNode {

  private final Identifier name;
  private final Optional<ImmutableList<SourceLocation.Point>> commaLocations;

  /**
   * Either a {@link SoyFunction} or a {@link SoySourceFunction}. TODO(b/19252021): use
   * SoySourceFunction everywhere.
   */
  private Object soyFunction;

  /** The parameter types this function allows. */
  @Nullable private ImmutableList<SoyType> allowedParamTypes;

  /** Convenience constructor for when the function is available. */
  public FunctionNode(
      Identifier name,
      Object soyFunction,
      SourceLocation sourceLocation,
      List<SourceLocation.Point> commaLocations) {
    this(name, sourceLocation, commaLocations);
    setSoyFunction(soyFunction);
  }

  public FunctionNode(Identifier name, Object soyFunction, SourceLocation sourceLocation) {
    super(sourceLocation);
    this.name = name;
    this.commaLocations = Optional.empty();
    setSoyFunction(soyFunction);
  }

  public FunctionNode(
      Identifier name, SourceLocation sourceLocation, List<SourceLocation.Point> commaLocations) {
    super(sourceLocation);
    this.name = name;
    this.commaLocations = Optional.of(ImmutableList.copyOf(commaLocations));
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private FunctionNode(FunctionNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.soyFunction = orig.soyFunction;
    this.allowedParamTypes = orig.allowedParamTypes;
    this.commaLocations = orig.commaLocations;
  }

  public Optional<ImmutableList<SourceLocation.Point>> getCommaLocations() {
    return commaLocations;
  }

  @Override
  public Kind getKind() {
    return Kind.FUNCTION_NODE;
  }

  /** Returns the function name. */
  public String getFunctionName() {
    return name.identifier();
  }

  public Identifier getIdentifier() {
    return name;
  }

  /** Returns the location of the function name. */
  public SourceLocation getFunctionNameLocation() {
    return name.location();
  }

  public Object getSoyFunction() {
    checkState(this.soyFunction != null, "setSoyFunction() hasn't been called yet");
    return soyFunction;
  }

  public void setSoyFunction(Object soyFunction) {
    checkNotNull(soyFunction);
    checkState(soyFunction instanceof SoyFunction || soyFunction instanceof SoySourceFunction);
    checkState(this.soyFunction == null, "setSoyFunction() was already called");
    if (soyFunction instanceof SoyFunction) {
      checkArgument(name.identifier().equals(((SoyFunction) soyFunction).getName()));
    }
    this.soyFunction = soyFunction;
  }

  public void setAllowedParamTypes(List<SoyType> allowedParamTypes) {
    checkState(
        allowedParamTypes.size() == numChildren(),
        "allowedParamTypes.size (%s) != numChildren (%s)",
        allowedParamTypes.size(),
        numChildren());
    this.allowedParamTypes = ImmutableList.copyOf(allowedParamTypes);
  }

  /** Returns null if ResolveExpressionTypesPass has not run yet. */
  @Nullable
  public ImmutableList<SoyType> getAllowedParamTypes() {
    return allowedParamTypes;
  }

  @Override
  public String toSourceString() {

    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append(getFunctionName()).append('(');

    boolean isFirst = true;
    for (ExprNode child : getChildren()) {
      if (isFirst) {
        isFirst = false;
      } else {
        sourceSb.append(", ");
      }
      sourceSb.append(child.toSourceString());
    }

    sourceSb.append(')');
    return sourceSb.toString();
  }

  @Override
  public FunctionNode copy(CopyState copyState) {
    return new FunctionNode(this, copyState);
  }

  /**
   * Whether or not this function is pure.
   *
   * <p>See {@link SoyPureFunction} for the definition of a pure function.
   */
  public boolean isPure() {
    return SoyFunctions.isPure(soyFunction);
  }
}
