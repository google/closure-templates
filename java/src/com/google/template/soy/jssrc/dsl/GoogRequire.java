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
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jssrc.dsl.Expressions.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expressions.dottedIdWithRequires;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import javax.annotation.Nullable;

/**
 * Represents a symbol that is imported via a {@code goog.require} statement or via a TypeScript
 * import statement.
 */
@AutoValue
@Immutable
public abstract class GoogRequire implements Comparable<GoogRequire> {

  enum Type {
    GOOG_REQUIRE {},
    GOOG_REQUIRE_WITH_ALIAS,
    GOOG_REQUIRE_TYPE,
    GOOG_REQUIRE_TYPE_WITH_ALIAS,
    GOOG_MAYBE_REQUIRE,
    IMPORT;

    CodeChunk getChunk(GoogRequire require) {
      String symbol = require.symbol();
      String alias = require.alias();
      String path = require.path();
      switch (this) {
        case GOOG_REQUIRE:
          checkArgument(symbol.equals(alias));
          checkArgument(path == null);
          return dottedIdNoRequire("goog.require").call(stringLiteral(symbol));
        case GOOG_REQUIRE_WITH_ALIAS:
          checkArgument(path == null);
          return VariableDeclaration.builder(alias)
              .setRhs(dottedIdNoRequire("goog.require").call(stringLiteral(symbol)))
              .build();
        case GOOG_REQUIRE_TYPE:
          checkArgument(symbol.equals(alias));
          checkArgument(path == null);
          return dottedIdNoRequire("goog.requireType").call(stringLiteral(symbol));
        case GOOG_REQUIRE_TYPE_WITH_ALIAS:
          checkArgument(path == null);
          return VariableDeclaration.builder(alias)
              .setRhs(dottedIdNoRequire("goog.requireType").call(stringLiteral(symbol)))
              .build();
        case GOOG_MAYBE_REQUIRE:
          checkArgument(symbol.equals(alias));
          checkArgument(path == null);
          return dottedIdNoRequire("goog.maybeRequireFrameworkInternalOnlyDoNotCallOrElse")
              .call(stringLiteral(symbol));
        case IMPORT:
          return Import.symbolImport(symbol, alias, path);
      }
      throw new AssertionError("Unreachable");
    }

    Type toRequireType() {
      switch (this) {
        case GOOG_REQUIRE:
        case GOOG_REQUIRE_TYPE:
          return GOOG_REQUIRE_TYPE;
        case GOOG_REQUIRE_WITH_ALIAS:
        case GOOG_REQUIRE_TYPE_WITH_ALIAS:
          return GOOG_REQUIRE_TYPE_WITH_ALIAS;
        case GOOG_MAYBE_REQUIRE:
          throw new IllegalStateException("GOOG_MAYBE_REQUIRE is not a normal require");
        case IMPORT:
          throw new IllegalStateException("IMPORT is not a normal require");
      }
      throw new AssertionError("Unreachable");
    }

    Type toRequireValue() {
      switch (this) {
        case GOOG_REQUIRE:
        case GOOG_REQUIRE_TYPE:
          return GOOG_REQUIRE;
        case GOOG_REQUIRE_WITH_ALIAS:
        case GOOG_REQUIRE_TYPE_WITH_ALIAS:
          return GOOG_REQUIRE_WITH_ALIAS;
        case GOOG_MAYBE_REQUIRE:
          throw new IllegalStateException("GOOG_MAYBE_REQUIRE is not a normal require");
        case IMPORT:
          throw new IllegalStateException("IMPORT is not a normal require");
      }
      throw new AssertionError("Unreachable");
    }
  }

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol: {@code
   * goog.require('symbol'); }
   */
  public static GoogRequire create(String symbol) {
    return new AutoValue_GoogRequire(symbol, symbol, null, Type.GOOG_REQUIRE);
  }

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol: {@code
   * goog.requireType('symbol'); }
   */
  public static GoogRequire createTypeRequire(String symbol) {
    return new AutoValue_GoogRequire(symbol, symbol, null, Type.GOOG_REQUIRE_TYPE);
  }

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol and aliases it to the given
   * name: {@code var alias = goog.require('symbol'); }
   */
  public static GoogRequire createWithAlias(String symbol, String alias) {
    CodeChunks.checkId(alias);
    return new AutoValue_GoogRequire(symbol, alias, null, Type.GOOG_REQUIRE_WITH_ALIAS);
  }

  /**
   * Creates a new {@code GoogRequire} that requires the given symbol and aliases it to the given
   * name: {@code var alias = goog.requireType('symbol'); }
   */
  public static GoogRequire createTypeRequireWithAlias(String symbol, String alias) {
    CodeChunks.checkId(alias);
    return new AutoValue_GoogRequire(symbol, alias, null, Type.GOOG_REQUIRE_TYPE_WITH_ALIAS);
  }

  public static GoogRequire createMaybeRequire(String symbol) {
    return new AutoValue_GoogRequire(symbol, symbol, null, Type.GOOG_MAYBE_REQUIRE);
  }

  public static GoogRequire createImport(String symbol, String path) {
    return createImport(symbol, symbol, path);
  }

  public static GoogRequire createImport(String symbol, String alias, String path) {
    return new AutoValue_GoogRequire(symbol, alias, path, Type.IMPORT);
  }

  /** The symbol to require. */
  public abstract String symbol();

  public abstract String alias();

  @Nullable
  abstract String path();

  /** A code chunk that will generate the {@code goog.require()}. */
  abstract Type type();

  public boolean isTypeRequire() {
    return type() == Type.GOOG_REQUIRE_TYPE || type() == Type.GOOG_REQUIRE_TYPE_WITH_ALIAS;
  }

  public GoogRequire toRequireType() {
    Type newType = type().toRequireType();
    if (newType == type()) {
      return this;
    }
    return new AutoValue_GoogRequire(symbol(), alias(), path(), newType);
  }

  public GoogRequire toRequireValue() {
    Type newType = type().toRequireValue();
    if (newType == type()) {
      return this;
    }
    GoogRequire require = new AutoValue_GoogRequire(symbol(), alias(), path(), newType);
    require.constructionLocation = null;
    return require;
  }

  boolean isMaybeRequire() {
    return type() == Type.GOOG_MAYBE_REQUIRE;
  }

  /** Returns a code chunk that can act as a reference to the required symbol. */
  public Expression reference() {
    checkState(type() == Type.GOOG_REQUIRE_WITH_ALIAS);
    return id(alias(), ImmutableSet.of(this));
  }

  /** Returns a reference to the module object using {@code goog.module.get} */
  public Expression googModuleGet() {
    return dottedIdWithRequires("goog.module.get", ImmutableSet.of(this))
        .call(stringLiteral(symbol()));
  }

  /** Access a member of this required symbol. */
  public Expression dotAccess(String ident) {
    return reference().dotAccess(ident);
  }

  // DO NOT SUBMIT
  @SuppressWarnings("Immutable")
  private Throwable constructionLocation = new Exception();

  public void writeTo(StringBuilder sb) {
    if (symbol().equals("goog.soy.data.SanitizedHtml")
        && !isTypeRequire()
        && constructionLocation != null) {
      throw new IllegalStateException(
          "goog.soy.data.SanitizedHtml is not a type require", constructionLocation);
    }
    sb.append(type().getChunk(this).getCode(FormatOptions.JSSRC)).append('\n');
  }

  private boolean isAliasedRequire() {
    return type() == Type.GOOG_REQUIRE_WITH_ALIAS || type() == Type.GOOG_REQUIRE_TYPE_WITH_ALIAS;
  }

  private boolean isBareRequire() {
    return type() == Type.GOOG_REQUIRE || type() == Type.GOOG_REQUIRE_TYPE;
  }

  /** For 2 goog requires with the same symbol. Return the preferred one. */
  public GoogRequire merge(GoogRequire other) {
    checkArgument(other.symbol().equals(symbol()));
    if (other.equals(this)) {
      return this;
    }
    // if symbols are equal and the references are, then they must differ only by requireType or not
    // prefer the non requireType symbol
    if ((other.isAliasedRequire() && this.isAliasedRequire() && other.alias().equals(this.alias()))
        || (other.isBareRequire() && this.isBareRequire())) {
      if (other.isTypeRequire()) {
        return this;
      }
      return other;
    }

    // If one is a type require without a variable declaration, then use the other one.
    // Types can be referenced in a fully qualified way even in a goog.module file with an alias
    // This is unstylish but tricky to solve given the way we currently model requires and only half
    // support goog.module.
    if (other.isTypeRequire() && other.isBareRequire()) {
      return this;
    }
    if (this.isTypeRequire() && this.isBareRequire()) {
      return other;
    }
    // If only one of the two is aliased, then use the one with the alias
    if (other.isAliasedRequire() && this.isBareRequire()) {
      if (other.isTypeRequire() && !this.isTypeRequire()) {
        return other.toRequireValue();
      }
      return other;
    }
    if (this.isAliasedRequire() && other.isBareRequire()) {
      if (!other.isTypeRequire() && this.isTypeRequire()) {
        return this.toRequireValue();
      }
      return this;
    }

    throw new IllegalArgumentException(
        "Found the same namespace added as a require in multiple incompatible ways:\n"
            + other
            + "\nvs.\n"
            + this);
  }

  @Override
  public final int compareTo(GoogRequire o) {
    return symbol().compareTo(o.symbol());
  }
}
