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
        .setIsMutable(false);
  }

  abstract String varName();

  @Nullable
  abstract Expression rhs();

  @Nullable
  abstract JsDoc jsDoc();

  abstract ImmutableSet<GoogRequire> googRequires();

  abstract boolean isMutable();

  /** Returns an {@link Expression} representing a reference to this declared variable. */
  public Expression ref() {
    return VariableReference.of(this);
  }

  /**
   * {@link CodeChunk#getCode} serializes both the chunk's initial statements and its output
   * expression. When a declaration is the only chunk being serialized, this leads to a redundant
   * trailing expression: <code>
   *   var $$tmp = blah;
   *   $$tmp
   * </code> Override the superclass implementation to omit the trailing expression.
   */
  @Override
  String getCode(int startingIndent) {
    FormattingContext ctx = new FormattingContext(startingIndent);
    ctx.appendInitialStatements(this);
    return ctx.toString();
  }

  @Override
  void doFormatInitialStatements(FormattingContext ctx) {
    if (rhs() != null) {
      ctx.appendInitialStatements(rhs());
    }
    if (jsDoc() != null) {
      ctx.append(jsDoc()).endLine();
    }
    // variables without initializing expressions cannot be const
    ctx.append((isMutable() || rhs() == null) ? "let " : "const ").append(varName());
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
  GoogRequire[] allRequires() {
    ImmutableSet.Builder<GoogRequire> requiresBuilder =
        ImmutableSet.<GoogRequire>builder().addAll(googRequires());
    if (rhs() != null) {
      rhs().collectRequires(requiresBuilder::add);
    }
    if (jsDoc() != null) {
      jsDoc().collectRequires(requiresBuilder::add);
    }
    return requiresBuilder.build().toArray(new GoogRequire[0]);
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

    public abstract VariableDeclaration build();
  }
}
