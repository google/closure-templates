/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.dsl;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.ForOverride;
import com.google.errorprone.annotations.Immutable;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Represents a variable declaration. */
@AutoValue
@Immutable
public abstract class VariableDeclaration extends Statement {

  public static Builder builder(String name) {
    return new AutoValue_VariableDeclaration.Builder()
        .setVarName(name)
        .setGoogRequires(ImmutableSet.of())
        // All variables should be const by default
        .setIsMutable(false)
        .setIsExported(false)
        .setIsDeclaration(false);
  }

  public abstract String varName();

  @Nullable
  abstract Expression rhs();

  @Nullable
  abstract JsDoc jsDoc();

  abstract ImmutableSet<GoogRequire> googRequires();

  abstract boolean isMutable();

  abstract boolean isExported();

  abstract boolean isDeclaration();

  @Nullable
  abstract Expression type();

  /** Returns an {@link Expression} representing a reference to this declared variable. */
  public Expression ref() {
    return VariableReference.of(this);
  }

  @Override
  void doFormatStatement(FormattingContext ctx) {
    if (rhs() != null) {
      ctx.appendInitialStatements(rhs());
    }
    if (jsDoc() != null) {
      ctx.append(jsDoc()).endLine();
    }

    if (isExported()) {
      ctx.append("export ");
    }
    if (isDeclaration()) {
      ctx.append("declare ");
    }

    // variables without initializing expressions cannot be const
    if (!varName().contains(".")) {
      ctx.append((isMutable() || (rhs() == null && !isDeclaration())) ? "let " : "const ");
    }
    ctx.append(varName());
    if (type() != null) {
      ctx.append(": ").appendOutputExpression(type());
    }
    if (rhs() != null) {
      ctx.append(" = ").appendOutputExpression(rhs());
    }
    ctx.append(";").endLine();
  }

  @Override
  public void collectRequires(Consumer<GoogRequire> collector) {
    for (GoogRequire require : allRequires()) {
      collector.accept(require);
    }
  }

  // A cache of all the transitive requires.  Necessary because every time we traverse a variable
  // reference we collect requires from the declaration.  This means collectRequires will be called
  // once per reference instead of once per declaration which can lead to exponential behavior.
  // Use an array so our caller doesn't have to deal with allocating epic amounts of iterators.
  @ForOverride
  @Memoized
  ImmutableSet<GoogRequire> allRequires() {
    ImmutableSet.Builder<GoogRequire> requiresBuilder =
        ImmutableSet.<GoogRequire>builder().addAll(googRequires());
    if (rhs() != null) {
      rhs().collectRequires(requiresBuilder::add);
    }
    if (jsDoc() != null) {
      jsDoc().collectRequires(requiresBuilder::add);
    }
    if (type() != null) {
      type().collectRequires(requiresBuilder::add);
    }
    return requiresBuilder.build();
  }

  /** A builder for a {@link VariableDeclaration}. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract Builder setVarName(String name);

    public abstract Builder setJsDoc(JsDoc jsDoc);

    public abstract Builder setRhs(Expression value);

    public Builder setGoogRequires(Iterable<GoogRequire> requires) {
      return setGoogRequires(ImmutableSet.copyOf(requires));
    }

    abstract Builder setGoogRequires(ImmutableSet<GoogRequire> requires);

    public final Builder setMutable() {
      return setIsMutable(true);
    }

    abstract Builder setIsMutable(boolean isConst);

    public abstract Builder setIsExported(boolean isExported);

    public abstract Builder setIsDeclaration(boolean isDeclaration);

    public abstract Builder setType(Expression type);

    public abstract VariableDeclaration build();
  }
}
