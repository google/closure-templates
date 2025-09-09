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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.CallableExprBuilder;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.OperatorNodes.SpreadOpNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Parser helper class. */
class CallArgs {

  static final SoyErrorKind PARAM_MIX = SoyErrorKind.of("Mix of named and positional arguments.");

  private static final Identifier RECORD_SPREAD_KEY =
      Identifier.create("unused", SourceLocation.UNKNOWN);

  static final class Builder {
    final List<ExprNode> keys = new ArrayList<>();
    final List<ExprNode> values = new ArrayList<>();
    final List<SourceLocation.Point> commas = new ArrayList<>();

    void addComma(SourceLocation.Point comma) {
      commas.add(comma);
    }

    void addPositional(ExprNode value) {
      keys.add(null);
      values.add(value);
    }

    void addNamed(ExprNode key, ExprNode value) {
      keys.add(key);
      values.add(value);
    }

    CallArgs build(ErrorReporter errorReporter, Token close, Token identToken) {
      return create(
          errorReporter,
          keys.stream().map(Optional::ofNullable).collect(toImmutableList()),
          ImmutableList.copyOf(values),
          ImmutableList.copyOf(commas),
          close,
          identToken);
    }
  }

  private static CallArgs create(
      ErrorReporter errorReporter,
      ImmutableList<Optional<ExprNode>> keys,
      ImmutableList<ExprNode> values,
      ImmutableList<SourceLocation.Point> commaPos,
      Token close,
      Token identToken) {

    Preconditions.checkArgument(keys.size() == values.size());

    SourceLocation fullLocForError = null;
    if (!values.isEmpty()) {
      fullLocForError =
          values
              .get(0)
              .getSourceLocation()
              .createSuperRangeWith(Iterables.getLast(values).getSourceLocation());
      ExprNode firstName = keys.get(0).orElse(null);
      if (firstName != null) {
        fullLocForError = fullLocForError.createSuperRangeWith(firstName.getSourceLocation());
      }
    }
    boolean hasPositional = false;
    boolean hasNamed = false;

    for (int i = 0; i < keys.size(); i++) {
      Optional<ExprNode> key = keys.get(i);
      ExprNode value = values.get(i);

      if (key.isEmpty()) {
        if (value instanceof SpreadOpNode) {
          // hasSpread
        } else {
          hasPositional = true;
        }
      } else {
        hasNamed = true;
      }
    }

    if (hasNamed && hasPositional) {
      errorReporter.report(fullLocForError, PARAM_MIX);
    }

    return new CallArgs(keys, values, commaPos, close, identToken);
  }

  final ImmutableList<Optional<ExprNode>> keys;
  final ImmutableList<ExprNode> values;
  final ImmutableList<SourceLocation.Point> commas;
  private ImmutableList<Identifier> names;
  final Token close;
  final Token identToken;

  private CallArgs(
      ImmutableList<Optional<ExprNode>> keys,
      ImmutableList<ExprNode> values,
      ImmutableList<SourceLocation.Point> commas,
      Token close,
      Token identToken) {
    this.keys = keys;
    this.values = values;
    this.commas = commas;
    this.close = close;
    this.identToken = identToken;
  }

  public boolean isRecordLiteral() {
    return identToken.image.equals("record");
  }

  public boolean isMapLiteral() {
    return identToken.image.equals("map");
  }

  public boolean isNamed() {
    return keys.stream().allMatch(Optional::isPresent);
  }

  public boolean hasSpread() {
    return values.stream().anyMatch(value -> value instanceof SpreadOpNode);
  }

  public ImmutableMap<ExprNode, ExprNode> toMapLiteral() {
    ImmutableMap.Builder<ExprNode, ExprNode> map = ImmutableMap.builder();
    for (int i = 0; i < keys.size(); i++) {
      Optional<ExprNode> key = keys.get(i);
      ExprNode value = values.get(i);
      map.put(key.get(), value);
    }
    return map.buildOrThrow();
  }

  ImmutableList<Identifier> getNames(ErrorReporter errorReporter) {
    if (names == null) {
      Set<String> uniqueNames = new HashSet<>();
      ImmutableList.Builder<Identifier> builder = ImmutableList.builder();
      for (int i = 0; i < keys.size(); i++) {
        if (keys.get(i).isPresent()) {
          ExprNode key = keys.get(i).get();
          // Parsed this as an ExprNode but it must actually be an identifier.
          String firstId = key.toSourceString();
          if (BaseUtils.isDottedIdentifier(firstId)) {
            if (!uniqueNames.add(firstId)) {
              errorReporter.report(
                  key.getSourceLocation(), SoyFileParser.DUPLICATE_KEY_NAME, firstId);
            }
            builder.add(Identifier.create(firstId, key.getSourceLocation()));
          } else {
            errorReporter.report(
                key.getSourceLocation(), SoyFileParser.INVALID_PARAM_NAME, firstId);
            builder.add(Identifier.create("error", key.getSourceLocation()));
          }
        } else if (values.get(i) instanceof SpreadOpNode) {
          builder.add(RECORD_SPREAD_KEY);
        } else {
          throw new IllegalArgumentException();
        }
      }
      names = builder.build();
    }
    return names;
  }

  public CallableExprBuilder toBuilder(ErrorReporter errorReporter) {
    CallableExprBuilder cb =
        CallableExprBuilder.builder().setParamValues(values).setCommaLocations(commas);
    // `record` functions are treated as having named args.
    if (isNamed() || isRecordLiteral()) {
      cb.setParamNames(getNames(errorReporter));
    }
    return cb;
  }
}
