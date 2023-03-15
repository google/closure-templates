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
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.jssrc.dsl.FormattingContext.LexicalState;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Expresses JSDoc comment blocks and how to print them out. */
@AutoValue
@Immutable
public abstract class JsDoc extends SpecialToken implements CodeChunk.HasRequires {

  public static JsDoc getDefaultInstance() {
    return builder().build();
  }

  public static Builder builder() {
    return new AutoValue_JsDoc.Builder().setOverviewComment("");
  }

  /** Comment before the {@code @param} decls. */
  abstract String overviewComment();

  @Override
  public abstract ImmutableSet<GoogRequire> googRequires();

  public abstract ImmutableList<Param> params();

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return Stream.empty();
  }

  /** Builder for JsDoc. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract ImmutableList.Builder<Param> paramsBuilder();

    abstract ImmutableSet.Builder<GoogRequire> googRequiresBuilder();

    public abstract JsDoc build();

    @CanIgnoreReturnValue
    public Builder addGoogRequire(GoogRequire require) {
      googRequiresBuilder().add(require);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addGoogRequires(Collection<? extends GoogRequire> requires) {
      googRequiresBuilder().addAll(requires);
      return this;
    }

    @CanIgnoreReturnValue
    public abstract Builder setOverviewComment(String string);

    @CanIgnoreReturnValue
    public Builder addParameterizedAnnotation(String name, String value) {
      paramsBuilder().add(Param.create(name, value));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAnnotation(String type) {
      paramsBuilder().add(Param.createAnnotation(type, null));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addAnnotation(String type, String value) {
      paramsBuilder().add(Param.createAnnotation(type, value));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addTsParam(String name, String description) {
      String value = name;
      if (description != null && !description.isEmpty()) {
        value += " " + description;
      }
      paramsBuilder().add(Param.createAnnotation("param", value));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addParam(String name, String type) {
      paramsBuilder().add(Param.create("param", type, name));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addParam(String name, ImmutableMap<String, String> recordLiteralType) {
      paramsBuilder().add(Param.create("param", name, recordLiteralType));
      return this;
    }
  }

  /** Builder for JsDoc Param */
  @AutoValue
  @Immutable
  public abstract static class Param {
    public abstract String annotationType();

    @Nullable
    abstract String field();

    @Nullable
    public abstract String type();

    @Nullable
    public abstract String paramTypeName();

    /**
     * Non-null if the param type is a record literal (e.g. `{foo: boolean}`, with key = `foo` and
     * value = `boolean`).
     */
    @Nullable
    abstract ImmutableMap<String, String> recordLiteralType();

    static Param createAnnotation(String annotationType, String field) {
      Preconditions.checkArgument(!annotationType.startsWith("@"));
      return new AutoValue_JsDoc_Param(annotationType, field, null, null, null);
    }

    static Param create(String annotationType, String type) {
      Preconditions.checkArgument(!annotationType.startsWith("@"));
      return new AutoValue_JsDoc_Param(annotationType, null, type, null, null);
    }

    static Param create(String annotationType, String type, String paramTypeName) {
      Preconditions.checkArgument(!annotationType.startsWith("@"));
      return new AutoValue_JsDoc_Param(annotationType, null, type, paramTypeName, null);
    }

    static Param create(
        String annotationType,
        String paramTypeName,
        ImmutableMap<String, String> recordLiteralType) {
      return new AutoValue_JsDoc_Param(
          annotationType, null, null, paramTypeName, recordLiteralType);
    }

    void format(FormattingContext ctx) {
      if (recordLiteralType() != null) {
        ctx.append(String.format("@%s {{", annotationType()));
        ctx.endLine();
        for (Map.Entry<String, String> entry : recordLiteralType().entrySet()) {
          ctx.append(" *   " + entry.getKey() + ": " + entry.getValue() + ",");
          ctx.endLine();
        }
        ctx.append(String.format(" * }} %s", paramTypeName()));
      } else {
        ctx.append(this.toString());
      }
    }

    @Override
    public final String toString() {
      if (type() == null && field() == null) {
        return String.format("@%s", annotationType());
      } else if (field() != null) {
        return String.format("@%s %s", annotationType(), field());
      } else if (paramTypeName() == null) {
        return String.format("@%s {%s}", annotationType(), type());
      } else {
        return String.format("@%s {%s} %s", annotationType(), type(), paramTypeName());
      }
    }
  }

  /** Should only be invoked from FormattingContext#appendJsDoc. */
  @Override
  void doFormatToken(FormattingContext ctx) {
    if (isEmpty()) {
      return;
    }
    if (this.isSingleLine()) {
      ctx.append(this.toString());
      return;
    }
    ctx.append("/**");
    ctx.pushLexicalState(LexicalState.RANGE_COMMENT);
    ctx.endLine();
    if (!overviewComment().isEmpty()) {
      for (String s : Splitter.on('\n').split(overviewComment())) {
        ctx.append(" *" + (s.isEmpty() ? "" : " " + s));
        ctx.endLine();
      }
      if (!params().isEmpty()) {
        ctx.append(" *");
        ctx.endLine();
      }
    }
    for (Param param : params()) {
      ctx.append(" * ");
      param.format(ctx);
      ctx.endLine();
    }
    ctx.append(" */");
    ctx.popLexicalState();
    ctx.endLine();
  }

  /**
   * For use when appending jsdoc outside of a FormattingContext (e.g, @fileoverview at file start).
   * Otherwise, use FormattingContext#format(JsDoc), as this does not respect the current indent.
   */
  @Override
  public final String toString() {
    if (isEmpty()) {
      return "";
    }
    if (this.isSingleLine()) {
      return String.format(
          "/** %s */", overviewComment() + (params().size() == 1 ? params().get(0) : ""));
    }
    StringBuilder sb = new StringBuilder();
    sb.append("/**\n");
    if (overviewComment().length() > 0) {
      for (String s : Splitter.on('\n').split(overviewComment())) {
        sb.append(" *").append(s.isEmpty() ? "" : " " + s).append("\n");
      }
      if (!params().isEmpty()) {
        sb.append(" *\n");
      }
    }
    for (Param param : params()) {
      sb.append(" * ").append(param).append("\n");
    }
    sb.append(" */");
    return sb.toString();
  }

  public boolean isEmpty() {
    return params().isEmpty() && overviewComment().isEmpty();
  }

  private boolean isSingleLine() {
    if (overviewComment().contains("\n")) {
      return false;
    }
    return params().size() == 0
        || (overviewComment().isEmpty()
            && params().size() == 1
            // Typedefs usually span more than one line.
            && !params().get(0).annotationType().equals("typedef")
            // Record literals always span more than one line.
            && params().get(0).recordLiteralType() == null);
  }
}
