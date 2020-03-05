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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Expresses JSDoc comment blocks and how to print them out. */
@AutoValue
@Immutable
public abstract class JsDoc {

  public static Builder builder() {
    return new AutoValue_JsDoc.Builder();
  }

  abstract ImmutableList<GoogRequire> requires();

  abstract ImmutableList<Param> params();

  public void collectRequires(Consumer<GoogRequire> collector) {
    for (GoogRequire require : requires()) {
      collector.accept(require);
    }
  }

  /** Builder for JsDoc. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract ImmutableList.Builder<Param> paramsBuilder();

    abstract ImmutableList.Builder<GoogRequire> requiresBuilder();

    public abstract JsDoc build();

    public Builder addGoogRequire(GoogRequire require) {
      requiresBuilder().add(require);
      return this;
    }

    public Builder addParameterizedAnnotation(String name, String value) {
      paramsBuilder().add(Param.create(name, value));
      return this;
    }

    public Builder addAnnotation(String type) {
      paramsBuilder().add(Param.createAnnotation(type, null));
      return this;
    }

    public Builder addAnnotation(String type, String value) {
      paramsBuilder().add(Param.createAnnotation(type, value));
      return this;
    }

    public Builder addParam(String name, String type) {
      paramsBuilder().add(Param.create("param", type, name));
      return this;
    }

    public Builder addParam(String name, ImmutableMap<String, String> recordLiteralType) {
      paramsBuilder().add(Param.create("param", name, recordLiteralType));
      return this;
    }
  }

  @AutoValue
  @Immutable
  abstract static class Param {
    abstract String annotationType();

    @Nullable
    abstract String field();

    @Nullable
    abstract String type();

    @Nullable
    abstract String paramTypeName();

    /**
     * Non-null if the param type is a record literal (e.g. `{foo: boolean}`, with key = `foo` and
     * value = `boolean`).
     */
    @Nullable
    abstract ImmutableMap<String, String> recordLiteralType();

    static Param createAnnotation(String annotationType, String field) {
      return new AutoValue_JsDoc_Param(annotationType, field, null, null, null);
    }

    static Param create(String annotationType, String type) {
      return new AutoValue_JsDoc_Param(annotationType, null, type, null, null);
    }

    static Param create(String annotationType, String type, String paramTypeName) {
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
  public void doFormatJsDoc(FormattingContext ctx) {
    if (this.isSingleLine()) {
      ctx.append(this.toString());
      return;
    }
    ctx.append("/**");
    ctx.endLine();
    for (Param param : params()) {
      ctx.append(" * ");
      param.format(ctx);
      ctx.endLine();
    }
    ctx.append(" */");
  }

  /**
   * For use when appending jsdoc outside of a FormattingContext (e.g, @fileoverview at file start).
   * Otherwise, use FormattingContext#format(JsDoc), as this does not respect the current indent.
   */
  @Override
  public final String toString() {
    if (this.isSingleLine()) {
      return String.format("/** %s */", params().get(0));
    }
    StringBuilder sb = new StringBuilder();
    sb.append("/**\n");
    for (Param param : params()) {
      sb.append(" * ").append(param).append("\n");
    }
    sb.append(" */");
    return sb.toString();
  }

  private boolean isSingleLine() {
    return params().size() == 1
        // Typedefs usually span more than one line.
        && !"typedef".equals(params().get(0).annotationType())
        // Record literals always span more than one line.
        && params().get(0).recordLiteralType() == null;
  }
}
