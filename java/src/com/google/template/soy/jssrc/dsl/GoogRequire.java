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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.template.soy.jssrc.dsl.Expression.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expression.dottedIdWithRequires;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;

/** Represents a symbol that is imported via a {@code goog.require} statement. */
@AutoValue
@Immutable
public abstract class GoogRequire implements Comparable<GoogRequire> {

  private static final Expression GOOG_REQUIRE = dottedIdNoRequire("goog.require");
  private static final Expression GOOG_REQUIRE_TYPE = dottedIdNoRequire("goog.requireType");

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol: {@code
   * goog.require('symbol'); }
   */
  public static GoogRequire create(String symbol) {
    return new AutoValue_GoogRequire(
        symbol, GOOG_REQUIRE.call(stringLiteral(symbol)), /*isTypeRequire=*/ false);
  }

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol: {@code
   * goog.requireType('symbol'); }
   */
  public static GoogRequire createTypeRequire(String symbol) {
    return new AutoValue_GoogRequire(
        symbol, GOOG_REQUIRE_TYPE.call(stringLiteral(symbol)), /*isTypeRequire=*/ true);
  }

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol and aliases it to the given
   * name: {@code var alias = goog.require('symbol'); }
   */
  public static GoogRequire createWithAlias(String symbol, String alias) {
    CodeChunkUtils.checkId(alias);
    return new AutoValue_GoogRequire(
        symbol,
        VariableDeclaration.builder(alias).setRhs(GOOG_REQUIRE.call(stringLiteral(symbol))).build(),
        /*isTypeRequire=*/ false);
  }

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol and aliases it to the given
   * name: {@code var alias = goog.requireType('symbol'); }
   */
  public static GoogRequire createTypeRequireWithAlias(String symbol, String alias) {
    CodeChunkUtils.checkId(alias);
    return new AutoValue_GoogRequire(
        symbol,
        VariableDeclaration.builder(alias)
            .setRhs(GOOG_REQUIRE_TYPE.call(stringLiteral(symbol)))
            .build(),
        /*isTypeRequire=*/ true);
  }

  /** The symbol to require. */
  public abstract String symbol();

  /** A code chunk that will generate the {@code goog.require()}. */
  abstract CodeChunk chunk();

  abstract boolean isTypeRequire();

  /** Returns a code chunk that can act as a reference to the required symbol. */
  public Expression reference() {
    if (chunk() instanceof VariableDeclaration) {
      return id(((VariableDeclaration) chunk()).varName(), ImmutableSet.of(this));
    } else {
      return dottedIdWithRequires(symbol(), ImmutableSet.of(this));
    }
  }

  /** Returns a reference to the module object using {@code goog.module.get} */
  public Expression googModuleGet() {
    if (chunk() instanceof VariableDeclaration) {
      throw new IllegalStateException("requires with aliases shouldn't use goog.module.get");
    }
    return dottedIdWithRequires("goog.module.get", ImmutableSet.of(this))
        .call(stringLiteral(symbol()));
  }

  /** Access a member of this required symbol. */
  public Expression dotAccess(String ident) {
    return reference().dotAccess(ident);
  }

  public void writeTo(StringBuilder sb) {
    sb.append(chunk().getStatementsForInsertingIntoForeignCodeAtIndent(0));
  }

  /** For 2 goog requires with the same symbol. Return the perfered one. */
  public GoogRequire merge(GoogRequire other) {
    checkArgument(other.symbol().equals(symbol()));
    if (other.equals(this)) {
      return this;
    }
    // if symbols are equal and the references are, then they must differ only by requireType or not
    // prefer the non requireType symbol
    if ((other.chunk() instanceof VariableDeclaration
            && chunk() instanceof VariableDeclaration
            && ((VariableDeclaration) chunk())
                .varName()
                .equals(((VariableDeclaration) other.chunk()).varName()))
        || (!(chunk() instanceof VariableReference)
            && !(other.chunk() instanceof VariableDeclaration))) {
      if (other.isTypeRequire()) {
        return this;
      }
      return other;
    }
    throw new IllegalArgumentException(
        "Found the same namespace added as a require in multiple incompatible ways: "
            + other
            + " vs. "
            + this);
  }

  @Override
  public final int compareTo(GoogRequire o) {
    return symbol().compareTo(o.symbol());
  }
}
