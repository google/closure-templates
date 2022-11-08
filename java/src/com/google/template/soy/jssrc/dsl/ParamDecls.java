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

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.List;

/**
 * Represent all of a function's params. Formats them as: "{amount, name = ‘Vesper’} : {amount:
 * number, name?: string} ".
 *
 * <p>TODO: make this support js too, so both backends can use it?
 */
@AutoValue
@Immutable
public abstract class ParamDecls {

  abstract ImmutableList<ParamDecl> params();

  public static ParamDecls create(List<ParamDecl> params) {
    return new AutoValue_ParamDecls(ImmutableList.copyOf(params));
  }

  public String getCode() {

    if (params().isEmpty()) {
      return "{}: {}";
    }

    // Generate the dict of param names (e.g. "{amount, name = ‘Vesper’}"). Default values are not
    // supported yet.
    String paramNamesDict =
        "{" + params().stream().map(ParamDecl::nameDecl).collect(joining(", ")) + "}";

    // Generate the dict of param types (e.g. "{amount: number, name?: string}").
    String paramTypesDict =
        "{" + params().stream().map(ParamDecl::typeDecl).collect(joining(", ")) + "}";

    return paramNamesDict + " : " + paramTypesDict;
  }
}
