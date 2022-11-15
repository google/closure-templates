/*
 * Copyright 2022 Google Inc.
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
import com.google.errorprone.annotations.Immutable;
import java.util.Optional;

/**
 * Represents a single "name : type" tsx function param.
 *
 * <p>TODO: make this support js too, so both backends can use it?
 */
@AutoValue
@Immutable
public abstract class ParamDecl {

  abstract String name();

  abstract Expression type();

  abstract boolean isOptional();

  abstract Optional<String> defaultValue();

  public static ParamDecl create(String name, Expression type) {
    return new AutoValue_ParamDecl(name, type, false, Optional.empty());
  }

  public static ParamDecl create(String name, Expression type, boolean isOptional) {
    return new AutoValue_ParamDecl(name, type, isOptional, Optional.empty());
  }

  public static ParamDecl create(String name, Expression type, String defaultValue) {
    return new AutoValue_ParamDecl(name, type, true, Optional.of(defaultValue));
  }

  public String nameDecl() {
    return name() + defaultValue().map(value -> " = " + value).orElse("");
  }

  public String typeDecl() {
    return name() + (isOptional() ? "?" : "") + ": " + type().singleExprOrName().getText();
  }
}
