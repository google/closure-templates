/*
 * Copyright 2017 Google Inc.
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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Represents an ES6 class. Currently only supports anonymous class expressions.
 *
 * <p>Example:
 *
 * <p><code>{@literal
 * class[ extends BaseClass] {
 *    ...
 * };
 * }</code>
 */
@AutoValue
public abstract class ClassExpression extends Expression
    implements Expression.InitialStatementsScope {

  @Nullable
  abstract Id name();

  @Nullable
  abstract Expression baseClass();

  abstract ImmutableList<MethodDeclaration> methods();

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.concat(
        Stream.of(name(), baseClass()).filter(Objects::nonNull), methods().stream());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    ctx.append("class");
    if (name() != null) {
      ctx.append(" ");
      ctx.appendOutputExpression(name());
    }
    if (baseClass() != null) {
      ctx.append(" extends ");
      ctx.appendOutputExpression(baseClass());
    }
    ctx.append(" ");
    ctx.enterBlock();
    for (int i = 0; i < methods().size(); i++) {
      if (i > 0) {
        ctx.appendBlankLine();
      }
      ctx.appendOutputExpression(methods().get(i));
    }
    ctx.closeBlock();
  }

  public static Builder builder() {
    return new AutoValue_ClassExpression.Builder();
  }

  /** Builder. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setName(Id value);

    public abstract Builder setBaseClass(Expression value);

    abstract ImmutableList.Builder<MethodDeclaration> methodsBuilder();

    @CanIgnoreReturnValue
    public final Builder addMethod(MethodDeclaration value) {
      methodsBuilder().add(value);
      return this;
    }

    public abstract ClassExpression build();
  }

  /**
   * Represents a method declaration within an ES6 class (anonymous or named).
   *
   * <p>Example:
   *
   * <p><code>{@literal
   * foo(param1, param2) { < function body > }
   * }</code>
   */
  @AutoValue
  public abstract static class MethodDeclaration extends Expression
      implements Expression.InitialStatementsScope {
    abstract Id name();

    abstract JsDoc jsDoc();

    abstract Statement body();

    @Override
    Stream<? extends CodeChunk> childrenStream() {
      return Stream.of(jsDoc(), name(), body());
    }

    @Override
    void doFormatOutputExpr(FormattingContext ctx) {
      ctx.appendAll(jsDoc());
      ctx.endLine();
      ctx.appendOutputExpression(name());
      ctx.append("(");
      ctx.append(
          FunctionDeclaration.generateParamList(jsDoc(), /* addInlineTypeAnnotations= */ false));
      ctx.append(") ");
      ctx.appendAllIntoBlock(body());
    }

    public static Builder builder(Id name, Statement body) {
      return new AutoValue_ClassExpression_MethodDeclaration.Builder()
          .setName(name)
          .setBody(body)
          .setJsDoc(JsDoc.builder().build());
    }

    /** Builder. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setName(Id value);

      public abstract Builder setJsDoc(JsDoc value);

      public abstract Builder setBody(Statement value);

      public abstract MethodDeclaration build();
    }
  }
}
