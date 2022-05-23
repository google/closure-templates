/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.soyparse;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.CallableExprBuilder;
import com.google.template.soy.exprtree.ExprNode;
import java.util.HashSet;
import java.util.Set;

/** Parser helper class. */
class CallArgs {

  static CallArgs positional(
      ImmutableList<ExprNode> args, ImmutableList<SourceLocation.Point> commaPos, Token close) {
    return new CallArgs(null, args, commaPos, close);
  }

  static CallArgs named(
      ErrorReporter errorReporter,
      ImmutableList<Identifier> names,
      ImmutableList<ExprNode> values,
      Token close) {
    Set<String> paramNames = new HashSet<>();
    for (Identifier name : names) {
      if (!paramNames.add(name.identifier())) {
        errorReporter.report(name.location(), SoyFileParser.DUPLICATE_KEY_NAME, name.identifier());
      }
    }

    return new CallArgs(names, values, null, close);
  }

  static CallArgs empty(Token close) {
    return new CallArgs(null, ImmutableList.of(), ImmutableList.of(), close);
  }

  final ImmutableList<Identifier> names;
  final ImmutableList<ExprNode> values;
  final ImmutableList<SourceLocation.Point> commas;
  final Token close;

  private CallArgs(
      ImmutableList<Identifier> names,
      ImmutableList<ExprNode> values,
      ImmutableList<SourceLocation.Point> commas,
      Token close) {
    this.names = names;
    this.values = values;
    this.commas = commas;
    this.close = close;
  }

  public boolean isNamed() {
    return names != null;
  }

  public CallableExprBuilder toBuilder() {
    CallableExprBuilder cb =
        CallableExprBuilder.builder().setParamValues(values).setCommaLocations(commas);
    if (isNamed()) {
      cb.setParamNames(names);
    }
    return cb;
  }
}
