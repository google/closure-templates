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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoOneOf;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
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

  /** How parameters are passed to the function. */
  public enum ParamsStyle {
    NONE,
    POSITIONAL,
    NAMED
  }

  /**
   * Either a {@link SoyFunction} or a {@link SoySourceFunction}. TODO(b/19252021): use
   * SoySourceFunction everywhere.
   */
  @AutoOneOf(FunctionRef.Type.class)
  public abstract static class FunctionRef {
    enum Type {
      SOY_FUNCTION,
      SOY_SOURCE_FUNCTION
    }

    public static FunctionRef of(Object soyFunction) {
      if (soyFunction instanceof SoyFunction) {
        return of((SoyFunction) soyFunction);
      } else if (soyFunction instanceof SoySourceFunction) {
        return of((SoySourceFunction) soyFunction);
      } else {
        throw new ClassCastException(String.valueOf(soyFunction));
      }
    }

    public static FunctionRef of(SoyFunction soyFunction) {
      return AutoOneOf_FunctionNode_FunctionRef.soyFunction(soyFunction);
    }

    public static FunctionRef of(SoySourceFunction soySourceFunction) {
      return AutoOneOf_FunctionNode_FunctionRef.soySourceFunction(soySourceFunction);
    }

    abstract Type type();

    abstract SoyFunction soyFunction();

    abstract SoySourceFunction soySourceFunction();

    public Object either() {
      return type() == Type.SOY_FUNCTION ? soyFunction() : soySourceFunction();
    }
  }

  private static final class FunctionState {
    @Nullable private FunctionRef function;
    @Nullable private ImmutableList<SoyType> allowedParamTypes;
  }

  public static FunctionNode newNone(Identifier name, SourceLocation sourceLocation) {
    return new FunctionNode(
        sourceLocation, name, ParamsStyle.NONE, ImmutableList.of(), ImmutableList.of());
  }

  public static FunctionNode newPositional(
      Identifier name, BuiltinFunction soyFunction, SourceLocation sourceLocation) {
    FunctionNode fn =
        new FunctionNode(sourceLocation, name, ParamsStyle.POSITIONAL, ImmutableList.of(), null);
    fn.setSoyFunction(soyFunction);
    return fn;
  }

  public static FunctionNode newPositional(
      Identifier name, SoySourceFunction soyFunction, SourceLocation sourceLocation) {
    FunctionNode fn =
        new FunctionNode(sourceLocation, name, ParamsStyle.POSITIONAL, ImmutableList.of(), null);
    fn.setSoyFunction(soyFunction);
    return fn;
  }

  public static FunctionNode newPositional(
      Identifier name, SourceLocation sourceLocation, @Nullable List<Point> commaLocations) {
    return new FunctionNode(
        sourceLocation,
        name,
        ParamsStyle.POSITIONAL,
        ImmutableList.of(),
        commaLocations == null ? null : ImmutableList.copyOf(commaLocations));
  }

  public static FunctionNode newNamed(
      Identifier name, Iterable<Identifier> paramNames, SourceLocation sourceLocation) {
    return new FunctionNode(
        sourceLocation, name, ParamsStyle.NAMED, ImmutableList.copyOf(paramNames), null);
  }

  private final Identifier name;
  private final ParamsStyle paramsStyle;
  /** When paramsStyle is NAMED this contains the list of named parameters. Otherwise empty. */
  private final ImmutableList<Identifier> paramNames;

  @Nullable private final ImmutableList<SourceLocation.Point> commaLocations;

  // Mutable state stored in this AST node from various passes.
  private final FunctionState state = new FunctionState();

  private FunctionNode(
      SourceLocation sourceLocation,
      Identifier name,
      ParamsStyle paramsStyle,
      ImmutableList<Identifier> paramNames,
      @Nullable ImmutableList<Point> commaLocations) {
    super(sourceLocation);
    this.name = name;
    this.paramsStyle = paramsStyle;
    this.paramNames = paramNames;
    this.commaLocations = commaLocations;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private FunctionNode(FunctionNode orig, CopyState copyState) {
    super(orig, copyState);
    this.name = orig.name;
    this.paramsStyle = orig.paramsStyle;
    this.paramNames = orig.paramNames;
    this.state.function = orig.state.function;
    this.state.allowedParamTypes = orig.state.allowedParamTypes;
    this.commaLocations = orig.commaLocations;
  }

  public Optional<ImmutableList<SourceLocation.Point>> getCommaLocations() {
    return Optional.ofNullable(commaLocations);
  }

  @Override
  public Kind getKind() {
    return Kind.FUNCTION_NODE;
  }

  /** Returns the function name. */
  public String getFunctionName() {
    return name.identifier();
  }

  public ParamsStyle getParamsStyle() {
    return paramsStyle;
  }

  public Identifier getIdentifier() {
    return name;
  }

  /** Returns the location of the function name. */
  public SourceLocation getFunctionNameLocation() {
    return name.location();
  }

  public boolean isResolved() {
    return state.function != null;
  }

  public Object getSoyFunction() {
    checkState(
        this.state.function != null,
        "setSoyFunction() hasn't been called yet %s %s",
        name,
        getSourceLocation());
    return state.function.either();
  }

  public void setSoyFunction(Object soyFunction) {
    checkNotNull(soyFunction);
    checkState(this.state.function == null, "setSoyFunction() was already called");
    this.state.function = FunctionRef.of(soyFunction);
  }

  public void setAllowedParamTypes(List<SoyType> allowedParamTypes) {
    checkState(paramsStyle == ParamsStyle.POSITIONAL || paramsStyle == ParamsStyle.NONE);
    checkState(
        allowedParamTypes.size() == numChildren(),
        "allowedParamTypes.size (%s) != numChildren (%s)",
        allowedParamTypes.size(),
        numChildren());
    this.state.allowedParamTypes = ImmutableList.copyOf(allowedParamTypes);
  }

  /** Returns null if ResolveExpressionTypesPass has not run yet. */
  @Nullable
  public ImmutableList<SoyType> getAllowedParamTypes() {
    checkState(paramsStyle == ParamsStyle.POSITIONAL || paramsStyle == ParamsStyle.NONE);
    return state.allowedParamTypes;
  }

  /**
   * Returns the list of proto initialization call param names.
   *
   * <p>Each param name corresponds to each of this node's children, which are the param values.
   */
  public ImmutableList<Identifier> getParamNames() {
    Preconditions.checkState(paramsStyle == ParamsStyle.NAMED || paramsStyle == ParamsStyle.NONE);
    return paramNames;
  }

  public Identifier getParamName(int i) {
    Preconditions.checkState(paramsStyle == ParamsStyle.NAMED || paramsStyle == ParamsStyle.NONE);
    return paramNames.get(i);
  }

  @Override
  public String toSourceString() {
    StringBuilder sourceSb = new StringBuilder();
    sourceSb.append(getFunctionName()).append('(');

    if (paramsStyle == ParamsStyle.POSITIONAL) {
      boolean isFirst = true;
      for (ExprNode child : getChildren()) {
        if (isFirst) {
          isFirst = false;
        } else {
          sourceSb.append(", ");
        }
        sourceSb.append(child.toSourceString());
      }
    } else if (paramsStyle == ParamsStyle.NAMED) {
      for (int i = 0; i < numChildren(); i++) {
        if (i > 0) {
          sourceSb.append(", ");
        }
        sourceSb.append(paramNames.get(i)).append(": ");
        sourceSb.append(getChild(i).toSourceString());
      }
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
    return SoyFunctions.isPure(state.function.either());
  }
}
