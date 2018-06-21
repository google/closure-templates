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
import com.google.errorprone.annotations.Immutable;
import javax.annotation.Nullable;

/** Expresses JSDoc comment blocks and how to print them out. */
@AutoValue
@Immutable
public abstract class JsDoc {

  public static Builder builder() {
    return new AutoValue_JsDoc.Builder();
  }

  abstract ImmutableList<Param> params();

  @Override
  public String toString() {
    // Typedefs usually span more than one line.
    if (params().size() == 1 && !"typedef".equals(params().get(0).annotationType())) {
      return String.format("/** %s */", params().get(0));
    }
    StringBuilder builder = new StringBuilder();
    builder.append("/**\n");
    for (Param param : params()) {
      builder.append(" * ").append(param).append("\n");
    }
    builder.append(" */");
    return builder.toString();
  }

  /** Builder for JsDoc. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract ImmutableList.Builder<Param> paramsBuilder();

    public abstract JsDoc build();

    public Builder addParameterizedTag(String name, String value) {
      paramsBuilder().add(Param.create(name, value));
      return this;
    }

    public Builder addTag(String type) {
      paramsBuilder().add(Param.createTag(type, null));
      return this;
    }

    public Builder addTag(String type, String value) {
      paramsBuilder().add(Param.createTag(type, value));
      return this;
    }

    public Builder addParam(String name, String type) {
      paramsBuilder().add(Param.create("param", type, name));
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

    static Param createTag(String annotationType, String field) {
      return new AutoValue_JsDoc_Param(annotationType, field, null, null);
    }

    static Param create(String annotationType, String type) {
      return new AutoValue_JsDoc_Param(annotationType, null, type, null);
    }

    static Param create(String annotationType, String type, String paramTypeName) {
      return new AutoValue_JsDoc_Param(annotationType, null, type, paramTypeName);
    }

    @Override
    public String toString() {
      if (type() == null && field() == null) {
        return String.format("@%s", annotationType());
      }
      if (field() != null) {
        return String.format("@%s %s", annotationType(), field());
      }
      if (paramTypeName() == null) {
        return String.format("@%s {%s}", annotationType(), type());
      }
      return String.format("@%s {%s} %s", annotationType(), type(), paramTypeName());
    }
  }
}
