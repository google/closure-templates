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

package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.CodeChunk.ifExpression;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_ARRAY_MAP;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunk.WithValue;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents a chain of (possibly null-safe) dot or bracket accesses. Used by {@link
 * TranslateExprNodeVisitor#visitNullSafeNode}. TODO(user): remove; simply emit {@link
 * CodeChunk#ifStatement conditional statements}. We can't do this yet though, because we can't
 * generate non-expression outside of test code.
 */
final class NullSafeAccumulator {

  /** The chain's base value. */
  private final CodeChunk.WithValue base;

  /**
   * Represents each "link" in the chain. For example, for the chain {@code a?.b?.c.d},
   * there are three links, {@code ?.b}, {@code ?.c}, and {@code .d}.
   */
  private final List<ChainAccess> chain;

  /**
   * A chain of dot accesses can end in a {@link com.google.common.html.types.SafeHtmlProto}
   * (SafeStyleProto, etc.). Such a chain needs to be
   * {@link com.google.template.soy.data.internalutils.NodeContentKinds#toJsUnpackFunction unpacked}
   * to a SanitizedContent object before it can be used in the JS runtime.
   */
  @Nullable private CodeChunk.WithValue unpackFunction;

  /**
   * A chain of dot accesses can end in a reference to a repeated {@link
   * com.google.common.html.types.SafeHtmlProto} field (SafeStyleProto field, etc.). The array
   * representing the repeated field needs to be unpacked by mapping it through the appropriate
   * {@link com.google.template.soy.data.internalutils.NodeContentKinds#toJsUnpackFunction } unpack
   * function to produce an array of SanitizedContent objects before it can be used in the JS
   * runtime.
   */
  private boolean isRepeated = false;

  /** Creates a NullSafeAccumulator with the given base chunk. */
  NullSafeAccumulator(CodeChunk.WithValue base) {
    this.base = base;
    this.chain = new ArrayList<>();
  }


  /**
   * Extends the access chain with a dot access to the given value.
   * @param nullSafe If true, code will be generated to ensure the chain is non-null before
   * dereferencing {@code arg}.
   */
  NullSafeAccumulator dotAccess(FieldAccess arg, boolean nullSafe) {
    if (arg instanceof CallAndUnpack) {
      Preconditions.checkState(
          unpackFunction == null, "this chain will already unpack with", unpackFunction);
      unpackFunction = ((CallAndUnpack) arg).unpackFunctionName();
      isRepeated = ((CallAndUnpack) arg).isRepeated();
    }
    chain.add(arg.toChainAccess(nullSafe));
    return this;
  }

  /**
   * Extends the access chain with a bracket access to the given value.
   * @param nullSafe If true, code will be generated to ensure the chain is non-null before
   * dereferencing {@code arg}.
   */
  NullSafeAccumulator bracketAccess(CodeChunk.WithValue arg, boolean nullSafe) {
    chain.add(new Bracket(arg, nullSafe));
    return this;
  }

  /**
   * Returns a code chunk representing the entire access chain. Null-safe accesses in the chain
   * generate code to make sure the chain is non-null before performing the access.
   */
  CodeChunk.WithValue result(CodeChunk.Generator codeGenerator) {
    // First generate a list of every partial evaluation of the chain.
    ImmutableList<CodeChunk.WithValue> intermediateValues = buildIntermediateValues();
    Preconditions.checkState(intermediateValues.size() == chain.size() + 1);

    // Walk backwards through the intermediate values. For any null-safe link in the chain,
    // test the intermediate value against null before dereferencing it.
    // For example, to translate a?.b.c, the rightmost link is not null-safe, so it translates to
    // a.b.c. The next link is null-safe, so it translates to a == null ? null : a.b.c.
    CodeChunk.WithValue cur = intermediateValues.get(intermediateValues.size() - 1);
    for (int i = intermediateValues.size() - 2; i >= 0; --i) {
      CodeChunk.WithValue chunk = intermediateValues.get(i);
      boolean nullSafe = chain.get(i).nullSafe;
      if (nullSafe) {
        cur = ifExpression(chunk.doubleEqualsNull(), LITERAL_NULL).else_(cur).build(codeGenerator);
      }
    }

    if (unpackFunction == null) {
      return cur;
    } else if (!isRepeated) {
      // It's okay if the whole chain evals to null. The unpack functions accept null.
      return unpackFunction.call(cur);
    } else {
      return GOOG_ARRAY_MAP.call(cur, unpackFunction);
    }
  }

  /**
   * Builds a list of intermediate values representing partial evaluation of the chain.
   * For example, the chain {@code a?.b?.c.d} has four intermediate values:
   * <ol>
   *   <li>{@code a}
   *   <li>{@code a?.b}
   *   <li>{@code a?.b?.c}
   *   <li>{@code a?.b?.c.d}
   * </ol>
   */
  private ImmutableList<CodeChunk.WithValue> buildIntermediateValues() {
    ImmutableList.Builder<CodeChunk.WithValue> builder = ImmutableList.builder();
    CodeChunk.WithValue prev = base;
    builder.add(prev);
    for (ChainAccess link : chain) {
      prev = link.extend(prev);
      builder.add(prev);
    }
    return builder.build();
  }

  /**
   * Abstract base class for extending the access chain with {@link Dot dot accesses},
   * {@link Bracket bracket accesses}, and {@link DotCall dot accesses followed by a function call}.
   */
  private abstract static class ChainAccess {
    /** How to extend the tip of the chain. */
    abstract CodeChunk.WithValue extend(CodeChunk.WithValue prevTip);
    final boolean nullSafe;

    ChainAccess(boolean nullSafe) {
      this.nullSafe = nullSafe;
    }
  }

  /** Extends the chain with a (null-safe or not) bracket access. */
  private static final class Bracket extends ChainAccess {
    final CodeChunk.WithValue value;

    Bracket(CodeChunk.WithValue value, boolean nullSafe) {
      super(nullSafe);
      this.value = value;
    }

    @Override
    CodeChunk.WithValue extend(CodeChunk.WithValue prevTip) {
      return prevTip.bracketAccess(value);
    }
  }

  /** Extends the chain with a (null-safe or not) dot access. */
  private static final class Dot extends ChainAccess {
    final String id;
    Dot(String id, boolean nullSafe) {
      super(nullSafe);
      this.id = id;
    }

    @Override
    CodeChunk.WithValue extend(CodeChunk.WithValue prevTip) {
      return prevTip.dotAccess(id);
    }
  }

  /**
   * Extends the chain with a (null-safe or not) dot access followed by a function call.
   * See {@link FieldAccess} for rationale.
   */
  private static final class DotCall extends ChainAccess {
    final String getter;
    @Nullable final CodeChunk.WithValue arg;

    DotCall(String getter, @Nullable CodeChunk.WithValue arg, boolean nullSafe) {
      super(nullSafe);
      this.getter = getter;
      this.arg = arg;
    }

    @Override
    WithValue extend(WithValue prevTip) {
      return arg == null
          ? prevTip.dotAccess(getter).call()
          : prevTip.dotAccess(getter).call(arg);
    }
  }

  /**
   * {@link NullSafeAccumulator} works by extending the tip of a chain of accesses.
   * In some situations (e.g. {@link TranslateExprNodeVisitor#genCodeForProtoAccess}),
   * the tip is "extended" by a dot access followed by a function call. Because dot accesses
   * have higher precedence in JS than function calls, the extension cannot be represented by a
   * single {@link CodeChunk.WithValue} that is attached to the previous tip; that would generate
   * an incorrect pair of parens around the function call, e.g. {@code proto.(getFoo())} instead of
   * {@code proto.getFoo()}. This tuple is a workaround for that precedence issue.
   */
  abstract static class FieldAccess {

    @ForOverride
    abstract ChainAccess toChainAccess(boolean nullSafe);

    static FieldAccess id(String fieldName) {
      return new AutoValue_NullSafeAccumulator_Id(fieldName);
    }

    static FieldAccess call(String getter, CodeChunk.WithValue arg) {
      return new AutoValue_NullSafeAccumulator_Call(getter, arg);
    }

    static FieldAccess call(String getter) {
      return new AutoValue_NullSafeAccumulator_Call(getter, null /* arg */);
    }

    static CallAndUnpack.Builder callAndUnpack() {
      return new AutoValue_NullSafeAccumulator_CallAndUnpack.Builder();
    }
  }

  @AutoValue
  abstract static class Id extends FieldAccess {
    abstract String fieldName();

    @Override
    ChainAccess toChainAccess(boolean nullSafe) {
      return new Dot(fieldName(), nullSafe);
    }
  }

  @AutoValue
  abstract static class Call extends FieldAccess {
    abstract String getter();
    @Nullable abstract CodeChunk.WithValue arg();

    @Override
    ChainAccess toChainAccess(boolean nullSafe) {
      return new DotCall(getter(), arg(), nullSafe);
    }
  }

  @AutoValue
  abstract static class CallAndUnpack extends FieldAccess {
    abstract String getter();
    @Nullable abstract CodeChunk.WithValue arg();
    abstract CodeChunk.WithValue unpackFunctionName();
    abstract boolean isRepeated();

    @Override
    ChainAccess toChainAccess(boolean nullSafe) {
      return new DotCall(getter(), arg(), nullSafe);
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder getter(String getter);

      abstract Builder arg(CodeChunk.WithValue arg);

      abstract Builder unpackFunctionName(CodeChunk.WithValue unpackFunctionName);

      abstract Builder isRepeated(boolean isRepeated);

      abstract CallAndUnpack build();
    }
  }
}
