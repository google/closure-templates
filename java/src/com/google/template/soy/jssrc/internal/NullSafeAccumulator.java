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

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.Expression.ifExpression;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_ARRAY_MAP;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_NEWMAPS_TRANSFORM_VALUES;
import static com.google.template.soy.jssrc.internal.JsRuntime.extensionField;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoToSanitizedContentConverterFunction;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.ForOverride;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents a chain of (possibly null-safe) dot or bracket accesses. Used by {@link
 * TranslateExprNodeVisitor#visitNullSafeNode}.
 */
final class NullSafeAccumulator {

  /** The chain's base value. */
  private final Expression base;

  /**
   * Represents each "link" in the chain. For example, for the chain {@code a?.b?.c.d},
   * there are three links, {@code ?.b}, {@code ?.c}, and {@code .d}.
   */
  private final List<ChainAccess> chain;

  /**
   * A chain of dot accesses can end in a {@link com.google.common.html.types.SafeHtmlProto}
   * (SafeStyleProto, etc.). Such a chain needs to be {@link
   * com.google.template.soy.data.internalutils.NodeContentKinds#toJsUnpackFunction unpacked} to a
   * SanitizedContent object before it can be used in the JS runtime.
   */
  @Nullable private Expression unpackFunction;

  /**
   * A chain of dot accesses can end in a reference to a repeated or map {@link
   * com.google.common.html.types.SafeHtmlProto} field (SafeStyleProto field, etc.). The array/map
   * representing the field needs to be unpacked by running it through the appropriate {@link
   * com.google.template.soy.data.internalutils.NodeContentKinds#toJsUnpackFunction unpack} function
   * to produce SanitizedContent objects before it can be used in the JS runtime. This tracks the
   * type of the field so we know if/how to unpack it.
   */
  @Nullable private AccessType accessType;

  /** Creates a NullSafeAccumulator with the given base chunk. */
  NullSafeAccumulator(Expression base) {
    this.base = base;
    this.chain = new ArrayList<>();
  }

  /**
   * Extends the access chain with a dot access to the given value.
   *
   * @param nullSafe If true, code will be generated to ensure the chain is non-null before
   *     dereferencing {@code access}.
   */
  NullSafeAccumulator dotAccess(FieldAccess access, boolean nullSafe) {
    if (access instanceof ProtoCall) {
      ProtoCall protoCall = (ProtoCall) access;
      Expression maybeUnpack = protoCall.unpackFunction();
      if (maybeUnpack != null) {
        Preconditions.checkState(
            unpackFunction == null, "this chain will already unpack with %s", unpackFunction);
        unpackFunction = maybeUnpack;
        accessType = protoCall.accessType();
      }
    }
    chain.add(access.toChainAccess(nullSafe));
    return this;
  }

  NullSafeAccumulator mapGetAccess(Expression mapKeyCode, boolean nullSafe) {
    chain.add(FieldAccess.call("get", mapKeyCode).toChainAccess(nullSafe));
    // With a .get call we no longer need to unpack the entire map, just a singular object.
    accessType = AccessType.SINGULAR;
    return this;
  }

  /**
   * Extends the access chain with a bracket access to the given value.
   *
   * @param nullSafe If true, code will be generated to ensure the chain is non-null before
   *     dereferencing {@code arg}.
   */
  NullSafeAccumulator bracketAccess(Expression arg, boolean nullSafe) {
    chain.add(new Bracket(arg, nullSafe));
    // With a bracket access we no longer need to unpack the entire list, just a singular object.
    accessType = AccessType.SINGULAR;
    return this;
  }

  /**
   * Returns a code chunk representing the entire access chain. Null-safe accesses in the chain
   * generate code to make sure the chain is non-null before performing the access.
   */
  Expression result(CodeChunk.Generator codeGenerator) {
    Expression accessChain = buildAccessChain(base, codeGenerator, chain.iterator());

    if (unpackFunction == null) {
      return accessChain;
    } else {
      return accessType.unpackResult(accessChain, unpackFunction);
    }
  }

  /**
   * Builds the access chain.
   *
   * <p>For chains with no null-safe accessses this is a simple and direct approach. However, for
   * null safe accesses we will stash base expressions into a temporary variable so we can generate
   * multiple references to it.
   *
   * <p>For example:
   *
   * <ol>
   *   <li>{@code $a.b} -> {@code a.b}
   *   <li>{@code $a?.b} -> {@code var t = a; t == null ? null : a.b}
   *   <li>{@code a?.b?.c} -> {@code var t = a; var r;if (t== null) {var t2 = a.b; r= t2 == null ?
   *       null : t2.c}}
   *   <li>{@code a?.b?.c.d} {@code var t = a; var r;if (t== null) {var t2 = a.b; r= t2 == null ?
   *       null : t2.c.d}}
   * </ol>
   */
  private static Expression buildAccessChain(
      Expression base, CodeChunk.Generator generator, Iterator<ChainAccess> chain) {
    if (!chain.hasNext()) {
      return base; // base case
    }
    ChainAccess link = chain.next();
    if (link.nullSafe) {
      if (!base.isCheap()) {
        base = generator.declarationBuilder().setRhs(base).build().ref();
      }
      return ifExpression(base.doubleEqualsNull(), LITERAL_NULL)
          .setElse(buildAccessChain(link.extend(base), generator, chain))
          .build(generator);
    }
    return buildAccessChain(link.extend(base), generator, chain);
  }

  /**
   * Abstract base class for extending the access chain with {@link Dot dot accesses},
   * {@link Bracket bracket accesses}, and {@link DotCall dot accesses followed by a function call}.
   */
  private abstract static class ChainAccess {
    /** How to extend the tip of the chain. */
    abstract Expression extend(Expression prevTip);

    final boolean nullSafe;

    ChainAccess(boolean nullSafe) {
      this.nullSafe = nullSafe;
    }
  }

  /** Extends the chain with a (null-safe or not) bracket access. */
  private static final class Bracket extends ChainAccess {
    final Expression value;

    Bracket(Expression value, boolean nullSafe) {
      super(nullSafe);
      this.value = value;
    }

    @Override
    Expression extend(Expression prevTip) {
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
    Expression extend(Expression prevTip) {
      return prevTip.dotAccess(id);
    }
  }

  /**
   * Extends the chain with a (null-safe or not) dot access followed by a function call.
   * See {@link FieldAccess} for rationale.
   */
  private static final class DotCall extends ChainAccess {
    final String getter;
    @Nullable final Expression arg;

    DotCall(String getter, @Nullable Expression arg, boolean nullSafe) {
      super(nullSafe);
      this.getter = getter;
      this.arg = arg;
    }

    @Override
    Expression extend(Expression prevTip) {
      return arg == null
          ? prevTip.dotAccess(getter).call()
          : prevTip.dotAccess(getter).call(arg);
    }
  }

  /**
   * {@link NullSafeAccumulator} works by extending the tip of a chain of accesses. In some
   * situations (e.g. {@link ProtoCall}), the tip is "extended" by a dot access followed by a
   * function call. Because dot accesses have higher precedence in JS than function calls, the
   * extension cannot be represented by a single {@link Expression} that is attached to the previous
   * tip; that would generate an incorrect pair of parens around the function call, e.g. {@code
   * proto.(getFoo())} instead of {@code proto.getFoo()}. This tuple is a workaround for that
   * precedence issue.
   */
  abstract static class FieldAccess {

    @ForOverride
    abstract ChainAccess toChainAccess(boolean nullSafe);

    static FieldAccess id(String fieldName) {
      return new AutoValue_NullSafeAccumulator_Id(fieldName);
    }

    static FieldAccess call(String getter, Expression arg) {
      return new AutoValue_NullSafeAccumulator_Call(getter, arg);
    }

    static FieldAccess protoCall(String fieldName, FieldDescriptor desc) {
      return ProtoCall.create(fieldName, desc);
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

    @Nullable
    abstract Expression arg();

    @Override
    ChainAccess toChainAccess(boolean nullSafe) {
      return new DotCall(getter(), arg(), nullSafe);
    }
  }

  @AutoValue
  abstract static class ProtoCall extends FieldAccess {

    abstract String getter();

    @Nullable
    abstract Expression getterArg();

    @Nullable
    abstract AccessType accessType();

    @Nullable
    abstract Expression unpackFunction();

    static ProtoCall create(String fieldName, FieldDescriptor desc) {
      String getter;
      Expression arg;
      if (desc.isExtension()) {
        getter = "getExtension";
        arg = extensionField(desc);
      } else {
        getter = "get" + LOWER_CAMEL.to(UPPER_CAMEL, fieldName);
        arg = null;
      }

      Expression unpackFunction = getUnpackFunction(desc);

      return new AutoValue_NullSafeAccumulator_ProtoCall(
          getter, arg, unpackFunction == null ? null : AccessType.get(desc), unpackFunction);
    }

    @Override
    ChainAccess toChainAccess(boolean nullSafe) {
      return new DotCall(getter(), getterArg(), nullSafe);
    }

    @Nullable
    private static Expression getUnpackFunction(FieldDescriptor desc) {
      if (ProtoUtils.isSanitizedContentField(desc)) {
        return protoToSanitizedContentConverterFunction(desc.getMessageType());
      } else if (ProtoUtils.isSanitizedContentMap(desc)) {
        return protoToSanitizedContentConverterFunction(ProtoUtils.getMapValueMessageType(desc));
      } else {
        return null;
      }
    }
  }

  /** The underlying data structure this chain is accessing. */
  enum AccessType {
    /** This isn't accessing a data structure, just a singular value. */
    SINGULAR {
      @Override
      Expression unpackResult(Expression accessChain, Expression unpackFunction) {
        // It's okay if the whole chain evals to null. The unpack functions accept null.
        return unpackFunction.call(accessChain);
      }
    },
    /** This is accessing a repeated value. */
    REPEATED {
      @Override
      Expression unpackResult(Expression accessChain, Expression unpackFunction) {
        return GOOG_ARRAY_MAP.call(accessChain, unpackFunction);
      }
    },
    /** This is access a map value. */
    MAP {
      @Override
      Expression unpackResult(Expression accessChain, Expression unpackFunction) {
        return SOY_NEWMAPS_TRANSFORM_VALUES.call(accessChain, unpackFunction);
      }
    };

    abstract Expression unpackResult(Expression accessChain, Expression unpackFunction);

    private static AccessType get(FieldDescriptor desc) {
      if (desc.isMapField()) {
        // Check map first because proto map fields are represented as repeated in the descriptor
        // even though map fields cannot have a "repeated" qualifier in the proto language.
        return MAP;
      } else if (desc.isRepeated()) {
        return REPEATED;
      } else {
        return SINGULAR;
      }
    }
  }
}
