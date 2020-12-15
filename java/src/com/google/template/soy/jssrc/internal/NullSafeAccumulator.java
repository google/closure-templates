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
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_CHECK_NOT_NULL;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_NEWMAPS_TRANSFORM_VALUES;
import static com.google.template.soy.jssrc.internal.JsRuntime.extensionField;
import static com.google.template.soy.jssrc.internal.JsRuntime.protoToSanitizedContentConverterFunction;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.ForOverride;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Represents a chain of (possibly null-safe) dot or bracket accesses. Used by {@link
 * TranslateExprNodeVisitor#visitNullSafeAccessNode}.
 */
final class NullSafeAccumulator {

  /** The chain's base value. */
  private final Expression base;

  /**
   * Represents each "link" in the chain. For example, for the chain {@code a?.b?.c.d}, there are
   * three links, {@code ?.b}, {@code ?.c}, and {@code .d}.
   */
  private final List<ChainAccess> chain;

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
  NullSafeAccumulator dotAccess(FieldAccess access, boolean nullSafe, boolean assertNonNull) {
    chain.add(access.toChainAccess(nullSafe, assertNonNull));
    return this;
  }

  NullSafeAccumulator mapGetAccess(Expression mapKeyCode, boolean nullSafe, boolean assertNonNull) {
    chain.add(FieldAccess.call("get", mapKeyCode).toChainAccess(nullSafe, assertNonNull));
    return this;
  }

  /**
   * Extends the access chain with a bracket access to the given value.
   *
   * @param nullSafe If true, code will be generated to ensure the chain is non-null before
   *     dereferencing {@code arg}.
   */
  NullSafeAccumulator bracketAccess(Expression arg, boolean nullSafe, boolean assertNonNull) {
    chain.add(new Bracket(arg, nullSafe, assertNonNull));
    return this;
  }

  /**
   * Extends the access chain with an arbitrary transformation of the previous tip.
   *
   * <p>If the previous tip is null, execution with throw an error before {@code extender} is
   * invoked.
   */
  NullSafeAccumulator functionCall(
      boolean nullSafe, boolean assertNonNull, Function<Expression, Expression> extender) {
    chain.add(new FunctionCall(nullSafe, assertNonNull, extender));
    return this;
  }

  /**
   * Returns a code chunk representing the entire access chain. Null-safe accesses in the chain
   * generate code to make sure the chain is non-null before performing the access.
   */
  Expression result(CodeChunk.Generator codeGenerator) {
    return buildAccessChain(base, codeGenerator, chain.iterator(), new ArrayDeque<>());
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
      Expression base,
      CodeChunk.Generator generator,
      Iterator<ChainAccess> chain,
      Deque<ChainAccess> unpackBuffer) {

    if (!chain.hasNext()) {
      return flushUnpackBuffer(base, unpackBuffer); // base case
    }
    ChainAccess link = chain.next();
    Expression result;
    if (link.nullSafe && !base.isCheap()) {
      base = generator.declarationBuilder().setRhs(base).build().ref();
    }

    Expression newBase = base;
    if (link.getUnpacking() == Unpacking.STOP) {
      newBase = flushUnpackBuffer(newBase, unpackBuffer);
    }
    newBase = link.extend(newBase);

    unpackBuffer.addFirst(link);
    if (link.nullSafe) {
      result =
          ifExpression(base.doubleEqualsNull(), LITERAL_NULL)
              .setElse(buildAccessChain(newBase, generator, chain, unpackBuffer))
              .build(generator);
    } else {
      result = buildAccessChain(newBase, generator, chain, unpackBuffer);
    }
    if (link.assertNonNull) {
      result = SOY_CHECK_NOT_NULL.call(result);
    }
    return result;
  }

  private static Expression flushUnpackBuffer(Expression base, Deque<ChainAccess> unpackBuffer) {
    boolean tail = true;
    for (ChainAccess link : unpackBuffer) {
      Unpacking unpacking = link.getUnpacking();
      if (unpacking == Unpacking.UNPACK) {
        base = link.unpack(base, tail);
        break;
      }
      tail = false;
    }
    unpackBuffer.clear();
    return base;
  }

  private enum Unpacking {
    /** This link will unpack. */
    UNPACK,
    /** This link will pass to the previous link. */
    PASS,
    /** This link should force all previous links to be unpacked before being passed to this one. */
    STOP
  }

  /**
   * Abstract base class for extending the access chain with {@link Dot dot accesses}, {@link
   * Bracket bracket accesses}, and {@link DotCall dot accesses followed by a function call}.
   */
  private abstract static class ChainAccess {

    /**
     * How to extend the tip of the chain.
     *
     * <p>No implementation of this class should allow this method to succeed if {@code prevTip} is
     * null. This can be guaranteed by calling a method like {@link Expression#dotAccess} on {@code
     * prevTip} or by adding an explicit check with {@link JsRuntime#SOY_CHECK_NOT_NULL}.
     */
    abstract Expression extend(Expression prevTip);

    final boolean nullSafe;
    final boolean assertNonNull;

    ChainAccess(boolean nullSafe, boolean assertNonNull) {
      this.nullSafe = nullSafe;
      this.assertNonNull = assertNonNull;
    }

    /**
     * Unpacks an expression so that it can be used in JS. Typically this is for unpacking protos,
     * like SafeHtmlProto, that are not used in their raw forms in JS. This must be implemented if
     * {@link #getUnpacking} returns {@link Unpacking#UNPACK}.
     *
     * @param base the expression to unpack
     * @param tail whether this link is the last link in the chain before the unpacking buffer is
     *     flushed.
     */
    Expression unpack(Expression base, boolean tail) {
      throw new UnsupportedOperationException();
    }

    Unpacking getUnpacking() {
      return Unpacking.PASS;
    }
  }

  private static final class FunctionCall extends ChainAccess {
    private final Function<Expression, Expression> funct;

    public FunctionCall(
        boolean nullSafe, boolean assertNonNull, Function<Expression, Expression> funct) {
      super(nullSafe, assertNonNull);
      this.funct = funct;
    }

    @Override
    Expression extend(Expression prevTip) {
      // Never allow a null method receiver.
      prevTip = SOY_CHECK_NOT_NULL.call(prevTip);
      return funct.apply(prevTip);
    }

    @Override
    Unpacking getUnpacking() {
      return Unpacking.STOP;
    }
  }

  /** Extends the chain with a (null-safe or not) bracket access. */
  private static final class Bracket extends ChainAccess {
    final Expression value;

    Bracket(Expression value, boolean nullSafe, boolean assertNonNull) {
      super(nullSafe, assertNonNull);
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

    Dot(String id, boolean nullSafe, boolean assertNonNull) {
      super(nullSafe, assertNonNull);
      this.id = id;
    }

    @Override
    Expression extend(Expression prevTip) {
      return prevTip.dotAccess(id);
    }
  }

  /**
   * Extends the chain with a (null-safe or not) dot access followed by a function call. See {@link
   * FieldAccess} for rationale.
   */
  private static class DotCall extends ChainAccess {
    final String getter;
    @Nullable final Expression arg;

    DotCall(String getter, @Nullable Expression arg, boolean nullSafe, boolean assertNonNull) {
      super(nullSafe, assertNonNull);
      this.getter = getter;
      this.arg = arg;
    }

    @Override
    final Expression extend(Expression prevTip) {
      return arg == null ? prevTip.dotAccess(getter).call() : prevTip.dotAccess(getter).call(arg);
    }
  }

  private static final class ProtoDotCall extends ChainAccess {

    private final ProtoCall protoCall;

    ProtoDotCall(boolean nullSafe, boolean assertNonNull, ProtoCall protoCall) {
      super(nullSafe, assertNonNull);
      this.protoCall = protoCall;
    }

    @Override
    Expression extend(Expression prevTip) {
      Expression arg = protoCall.getterArg();
      String getter = protoCall.getter();
      Expression result =
          arg == null ? prevTip.dotAccess(getter).call() : prevTip.dotAccess(getter).call(arg);
      return result;
    }

    @Override
    Expression unpack(Expression base, boolean tail) {
      // If tail=true then this link is the last link on the chain that's part of the unpack
      // buffer. In that case we can use whatever access type the link was created with. But if
      // tail=false then this is not the last link and some subsequent link, like dot or map access,
      // means that we can do the less expensive SINGULAR access type.
      AccessType accessType = tail ? protoCall.accessType() : AccessType.SINGULAR;
      return accessType.unpackResult(base, protoCall.unpackFunction());
    }

    @Override
    Unpacking getUnpacking() {
      return protoCall.unpackFunction() != null ? Unpacking.UNPACK : Unpacking.PASS;
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
    abstract ChainAccess toChainAccess(boolean nullSafe, boolean assertNonNull);

    static FieldAccess id(String fieldName) {
      return new AutoValue_NullSafeAccumulator_Id(fieldName);
    }

    static FieldAccess call(String getter, Expression arg) {
      return new AutoValue_NullSafeAccumulator_Call(getter, arg);
    }

    static FieldAccess protoCall(String fieldName, FieldDescriptor desc) {
      return ProtoCall.getField(fieldName, desc);
    }
  }

  @AutoValue
  abstract static class Id extends FieldAccess {
    abstract String fieldName();

    @Override
    ChainAccess toChainAccess(boolean nullSafe, boolean assertNonNull) {
      return new Dot(fieldName(), nullSafe, assertNonNull);
    }
  }

  @AutoValue
  abstract static class Call extends FieldAccess {
    abstract String getter();

    @Nullable
    abstract Expression arg();

    @Override
    ChainAccess toChainAccess(boolean nullSafe, boolean assertNonNull) {
      return new DotCall(getter(), arg(), nullSafe, assertNonNull);
    }
  }

  @AutoValue
  abstract static class ProtoCall extends FieldAccess {

    private enum Type {
      GET("get"),
      HAS("has");

      private final String prefix;

      Type(String prefix) {
        this.prefix = prefix;
      }

      public String getPrefix() {
        return prefix;
      }
    }

    abstract String getter();

    @Nullable
    abstract Expression getterArg();

    /**
     * A chain of dot accesses can end in a reference to a repeated or map {@link
     * com.google.common.html.types.SafeHtmlProto} field (SafeStyleProto field, etc.). The array/map
     * representing the field needs to be unpacked by running it through the appropriate {@link
     * com.google.template.soy.data.internalutils.NodeContentKinds#toJsUnpackFunction unpack}
     * function to produce SanitizedContent objects before it can be used in the JS runtime. This
     * tracks the type of the field so we know if/how to unpack it.
     */
    @Nullable
    abstract AccessType accessType();

    /**
     * A chain of dot accesses can end in a {@link com.google.common.html.types.SafeHtmlProto}
     * (SafeStyleProto, etc.). Such a chain needs to be {@link
     * com.google.template.soy.data.internalutils.NodeContentKinds#toJsUnpackFunction unpacked} to a
     * SanitizedContent object before it can be used in the JS runtime.
     */
    @Nullable
    abstract Expression unpackFunction();

    static ProtoCall getField(String fieldName, FieldDescriptor desc) {
      return accessor(fieldName, desc, Type.GET);
    }

    static ProtoCall hasField(String fieldName, FieldDescriptor desc) {
      return accessor(fieldName, desc, Type.HAS);
    }

    private static ProtoCall accessor(String fieldName, FieldDescriptor desc, Type prefix) {
      String getter;
      Expression arg;
      Expression unpackFunction = null;

      if (desc.isExtension() && Type.HAS == prefix) {
        // JSPB doesn't have hasExtension().
        throw new IllegalArgumentException("hasExtension() not implemented");
      } else if (Type.HAS == prefix
          && desc.getType() == FieldDescriptor.Type.MESSAGE
          && ProtoUtils.getContainingOneof(desc) == null) {
        // JSPB doesn't have hassers for submessages.
        throw new IllegalArgumentException("Submessage hasser not implemented");
      } else if (Type.GET == prefix) {
        unpackFunction = getUnpackFunction(desc);
      }

      if (desc.isExtension()) {
        getter = prefix.getPrefix() + "Extension";
        arg = extensionField(desc);
      } else {
        getter = prefix.getPrefix() + LOWER_CAMEL.to(UPPER_CAMEL, fieldName);
        arg = null;
      }

      return new AutoValue_NullSafeAccumulator_ProtoCall(
          getter, arg, unpackFunction == null ? null : AccessType.get(desc), unpackFunction);
    }

    @Override
    ChainAccess toChainAccess(boolean nullSafe, boolean assertNonNull) {
      return new ProtoDotCall(nullSafe, assertNonNull, this);
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
