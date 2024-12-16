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
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Represent all of a function's params. Formats them as: "{amount, name = ‘Vesper’} : {amount:
 * number, name?: string} ".
 *
 * <p>TODO: make this support js too, so both backends can use it?
 */
@AutoValue
@Immutable
public abstract class ParamDecls extends Expression {

  public static final ParamDecls EMPTY = create(ImmutableList.of());

  abstract ImmutableList<ParamDecl> params();

  abstract boolean namedStyle();

  @Nullable
  abstract TsInterface tsInterface();

  public static ParamDecls createNamed(List<ParamDecl> params) {
    return new AutoValue_ParamDecls(ImmutableList.copyOf(params), true, null);
  }

  public static ParamDecls createFromInterface(TsInterface tsInterface) {
    return new AutoValue_ParamDecls(tsInterface.properties(), true, tsInterface);
  }

  public static ParamDecls create(List<ParamDecl> params) {
    return new AutoValue_ParamDecls(ImmutableList.copyOf(params), false, null);
  }

  public RecordType asRecordType() {
    return RecordType.create(params());
  }

  @Override
  void doFormatOutputExpr(FormattingContext ctx) {
    if (namedStyle()) {
      if (params().isEmpty()) {
        ctx.append("{}: {}");
        return;
      }

      // Generate the dict of param names (e.g. "{amount, name = ‘Vesper’}"). Default values are not
      // supported yet.
      ctx.append("{");
      boolean first = true;
      for (ParamDecl param : params()) {
        first = ctx.commaAfterFirst(first);

        ctx.append(param.name());
        if (param.alias() != null) {
          ctx.noBreak().append(": " + param.alias());
        }
        if (param.defaultValue() != null) {
          ctx.noBreak().append(" = ").appendOutputExpression(param.defaultValue());
        }
      }
      ctx.append("}: ");

      if (tsInterface() != null) {
        ctx.append(tsInterface().name());
      } else {
        ctx.append("{");
        // Generate the dict of param types (e.g. "{amount: number, name?: string}").
        first = true;
        for (ParamDecl param : params()) {
          first = ctx.commaAfterFirst(first);
          ctx.appendOutputExpression(param);
        }
        ctx.append("}");
      }
    } else {
      boolean first = true;
      for (ParamDecl param : params()) {
        first = ctx.commaAfterFirst(first);
        ctx.appendOutputExpression(param);
      }
    }
  }

  @Override
  Stream<? extends CodeChunk> childrenStream() {
    return params().stream();
  }
}
