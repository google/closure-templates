/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.types.SoyTypes.NUMBER_TYPE;

import com.google.template.soy.basicfunctions.AugmentMapFunction;
import com.google.template.soy.basicfunctions.CeilingFunction;
import com.google.template.soy.basicfunctions.DebugSoyTemplateInfoFunction;
import com.google.template.soy.basicfunctions.FloatFunction;
import com.google.template.soy.basicfunctions.FloorFunction;
import com.google.template.soy.basicfunctions.KeysFunction;
import com.google.template.soy.basicfunctions.MaxFunction;
import com.google.template.soy.basicfunctions.MinFunction;
import com.google.template.soy.basicfunctions.RandomIntFunction;
import com.google.template.soy.basicfunctions.RoundFunction;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;
import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * Implements intrinsics for built in functions.
 *
 * <p>Many soy functions are very simple but the Tofu calling convention that is used requires us to
 *
 * <ul>
 *   <li>Box all arguments
 *   <li>Pass the arguments as a {@code List}
 *   <li>Map {@code null} -> {@code NullData} and back
 * </ul>
 *
 * <p>A lot of this cost could be avoided by swapping out an intrinsic implementation. For example,
 * {@code isNonnull($foo)} requires code that looks like:
 *
 * <pre>{@code
 * List<SoyValue> args = new ArrayList<1>();
 * args.put(<$foo>.box());
 * SoyValue ret = Runtime.callSoyFunction(
 *     renderContext.getFunction("isNonnull"),
 *     args);
 *
 * }</pre>
 *
 * <p>But that could be easily replaced with a simpler implementation of just {@code <$foo> ==
 * null}. This class provides intrinsic implementations of all functions that are built into {@code
 * soy}.
 */
final class PluginFunctionCompiler {

  private static final MethodRef AUGMENT_MAP_FN =
      MethodRef.create(AugmentMapFunction.class, "augmentMap", SoyDict.class, SoyDict.class)
          .asNonNullable();
  private static final MethodRef CEIL_FN =
      MethodRef.create(CeilingFunction.class, "ceil", SoyValue.class).asNonNullable();
  private static final MethodRef FLOOR_FN =
      MethodRef.create(FloorFunction.class, "floor", SoyValue.class).asNonNullable();
  private static final MethodRef KEYS_FN =
      MethodRef.create(KeysFunction.class, "keys", SoyMap.class);
  private static final MethodRef LIST_SIZE = MethodRef.create(List.class, "size");
  private static final MethodRef MATH_CEIL =
      MethodRef.create(Math.class, "ceil", double.class).asCheap();
  private static final MethodRef MATH_FLOOR =
      MethodRef.create(Math.class, "floor", double.class).asCheap();
  private static final MethodRef MATH_MAX_DOUBLE =
      MethodRef.create(Math.class, "max", double.class, double.class).asCheap();
  private static final MethodRef MATH_MAX_LONG =
      MethodRef.create(Math.class, "max", long.class, long.class).asCheap();
  private static final MethodRef MATH_MIN_DOUBLE =
      MethodRef.create(Math.class, "min", double.class, double.class).asCheap();
  private static final MethodRef MATH_MIN_LONG =
      MethodRef.create(Math.class, "min", long.class, long.class).asCheap();
  private static final MethodRef MATH_ROUND =
      MethodRef.create(Math.class, "round", double.class).asCheap();
  private static final MethodRef MAX_FN =
      MethodRef.create(MaxFunction.class, "max", SoyValue.class, SoyValue.class).asNonNullable();
  private static final MethodRef MIN_FN =
      MethodRef.create(MinFunction.class, "min", SoyValue.class, SoyValue.class).asNonNullable();
  private static final MethodRef RANDOM_INT_FN =
      MethodRef.create(RandomIntFunction.class, "randomInt", long.class).asCheap();
  private static final MethodRef ROUND_FN =
      MethodRef.create(RoundFunction.class, "round", SoyValue.class).asNonNullable();
  private static final MethodRef ROUND_WITH_NUM_DIGITS_AFTER_POINT_FN =
      MethodRef.create(RoundFunction.class, "round", SoyValue.class, int.class).asNonNullable();
  private static final MethodRef SOYLIST_LENGTH = MethodRef.create(SoyList.class, "length");
  private static final MethodRef STRING_CONTAINS =
      MethodRef.create(String.class, "contains", CharSequence.class);
  private static final MethodRef STRING_INDEX_OF =
      MethodRef.create(String.class, "indexOf", String.class);
  private static final MethodRef STRING_LENGTH = MethodRef.create(String.class, "length");
  private static final MethodRef STRING_SUBSTR_START =
      MethodRef.create(String.class, "substring", int.class).asNonNullable();
  private static final MethodRef STRING_SUBSTR_START_END =
      MethodRef.create(String.class, "substring", int.class, int.class);

  private final TemplateParameterLookup parameterLookup;

  PluginFunctionCompiler(TemplateParameterLookup parameterLookup) {
    this.parameterLookup = parameterLookup;
  }

  /** Returns an expression that evaluates the given soy function. */
  SoyExpression callPluginFunction(FunctionNode node, List<SoyExpression> args) {
    switch (node.getFunctionName()) {
      case "augmentMap":
        return invokeAugmentMapFunction(args.get(0), args.get(1));
      case "ceiling":
        return invokeCeilingFunction(args.get(0));
      case "floor":
        return invokeFloorFunction(args.get(0));
      case "isNonnull":
        return invokeIsNonnullFunction(args.get(0));
      case "keys":
        return invokeKeysFunction(args.get(0));
      case "length":
        return invokeLengthFunction(args.get(0));
      case "max":
        return invokeMaxFunction(args.get(0), args.get(1));
      case "min":
        return invokeMinFunction(args.get(0), args.get(1));
      case "randomInt":
        return invokeRandomIntFunction(args.get(0));
      case "round":
        if (args.size() == 1) {
          return invokeRoundFunction(args.get(0));
        }
        return invokeRoundFunction(args.get(0), args.get(1));
      case "strContains":
        return invokeStrContainsFunction(args.get(0), args.get(1));
      case "strIndexOf":
        return invokeStrIndexOfFunction(args.get(0), args.get(1));
      case "strLen":
        return invokeStrLenFunction(args.get(0));
      case "strSub":
        if (args.size() == 2) {
          return invokeStrSubFunction(args.get(0), args.get(1));
        }
        return invokeStrSubFunction(args.get(0), args.get(1), args.get(2));
      case FloatFunction.NAME:
        return invokeFloatFunction(args.get(0));
      case DebugSoyTemplateInfoFunction.NAME:
        return invokeDebugSoyTemplateInfoFunction();
      default:
        // TODO(lukes): add support for the BidiFunctions
        return invokeSoyFunction(node, args);
    }
  }

  private SoyExpression invokeDebugSoyTemplateInfoFunction() {
    return SoyExpression.forBool(
        parameterLookup
            .getRenderContext()
            .invoke(MethodRef.RENDER_CONTEXT_GET_DEBUG_SOY_TEMPLATE_INFO));
  }

  /** @see com.google.template.soy.basicfunctions.AugmentMapFunction */
  private SoyExpression invokeAugmentMapFunction(SoyExpression arg0, SoyExpression arg1) {
    Expression first = arg0.checkedCast(SoyDict.class);
    Expression second = arg1.checkedCast(SoyDict.class);
    // TODO(lukes): this logic should move into the ResolveExpressionTypesVisitor
    MapType mapType =
        MapType.of(
            StringType.getInstance(),
            UnionType.of(getMapValueType(arg0.soyType()), getMapValueType(arg1.soyType())));
    return SoyExpression.forSoyValue(mapType, AUGMENT_MAP_FN.invoke(first, second));
  }

  private SoyType getMapValueType(SoyType type) {
    if (type.getKind() == SoyType.Kind.MAP) {
      return ((MapType) type).getValueType();
    }
    return UnknownType.getInstance();
  }

  /** @see com.google.template.soy.basicfunctions.CeilingFunction */
  private SoyExpression invokeCeilingFunction(SoyExpression argument) {
    switch (argument.resultType().getSort()) {
      case Type.LONG:
        return argument;
      case Type.DOUBLE:
        return SoyExpression.forInt(
            BytecodeUtils.numericConversion(MATH_CEIL.invoke(argument), Type.LONG_TYPE));
      default:
        return SoyExpression.forSoyValue(IntType.getInstance(), CEIL_FN.invoke(argument.box()));
    }
  }

  /** @see com.google.template.soy.basicfunctions.FloatFunction */
  private SoyExpression invokeFloatFunction(SoyExpression arg) {
    SoyExpression unboxed = arg.isBoxed() ? arg.unboxAs(long.class) : arg;
    SoyExpression result =
        SoyExpression.forFloat(BytecodeUtils.numericConversion(unboxed, Type.DOUBLE_TYPE));
    return arg.isCheap() ? result.asCheap() : result;
  }

  /** @see com.google.template.soy.basicfunctions.FloorFunction */
  private SoyExpression invokeFloorFunction(SoyExpression argument) {
    switch (argument.resultType().getSort()) {
      case Type.LONG:
        return argument;
      case Type.DOUBLE:
        return SoyExpression.forInt(
            BytecodeUtils.numericConversion(MATH_FLOOR.invoke(argument), Type.LONG_TYPE));
      default:
        return SoyExpression.forSoyValue(IntType.getInstance(), FLOOR_FN.invoke(argument.box()));
    }
  }

  /** @see com.google.template.soy.basicfunctions.IsNonnullFunction */
  private SoyExpression invokeIsNonnullFunction(final SoyExpression soyExpression) {
    if (BytecodeUtils.isPrimitive(soyExpression.resultType())) {
      return SoyExpression.TRUE;
    }
    return SoyExpression.forBool(
        new Expression(Type.BOOLEAN_TYPE, soyExpression.features()) {
          @Override
          void doGen(CodeBuilder adapter) {
            soyExpression.gen(adapter);
            Label isNull = new Label();
            adapter.ifNull(isNull);
            // non-null
            adapter.pushBoolean(true);
            Label end = new Label();
            adapter.goTo(end);
            adapter.mark(isNull);
            adapter.pushBoolean(false);
            adapter.mark(end);
          }
        });
  }

  /** @see com.google.template.soy.basicfunctions.KeysFunction */
  private SoyExpression invokeKeysFunction(SoyExpression soyExpression) {
    SoyType argType = soyExpression.soyType();
    // TODO(lukes): this logic should live in ResolveExpressionTypesVisitor
    SoyType listElementType;
    if (argType.getKind() == SoyType.Kind.MAP) {
      listElementType = ((MapType) argType).getKeyType(); // pretty much just string
    } else if (argType.getKind() == SoyType.Kind.LIST) {
      listElementType = IntType.getInstance();
    } else {
      listElementType = UnknownType.getInstance();
    }
    return SoyExpression.forList(
        ListType.of(listElementType),
        KEYS_FN.invoke(soyExpression.box().checkedCast(SoyMap.class)));
  }

  /** @see com.google.template.soy.basicfunctions.LengthFunction */
  private SoyExpression invokeLengthFunction(SoyExpression soyExpression) {
    Expression lengthExpressionAsInt;
    if (soyExpression.isBoxed()) {
      lengthExpressionAsInt = soyExpression.checkedCast(SoyList.class).invoke(SOYLIST_LENGTH);
    } else {
      lengthExpressionAsInt = soyExpression.checkedCast(List.class).invoke(LIST_SIZE);
    }
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(lengthExpressionAsInt, Type.LONG_TYPE));
  }

  /** @see com.google.template.soy.basicfunctions.MaxFunction */
  private SoyExpression invokeMaxFunction(SoyExpression left, SoyExpression right) {
    if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
      return SoyExpression.forInt(
          MATH_MAX_LONG.invoke(left.unboxAs(long.class), right.unboxAs(long.class)));
    } else if (left.assignableToNullableFloat() && right.assignableToNullableFloat()) {
      return SoyExpression.forFloat(
          MATH_MAX_DOUBLE.invoke(left.unboxAs(double.class), right.unboxAs(double.class)));
    } else {
      return SoyExpression.forSoyValue(NUMBER_TYPE, MAX_FN.invoke(left.box(), right.box()));
    }
  }

  /** @see com.google.template.soy.basicfunctions.MinFunction */
  private SoyExpression invokeMinFunction(SoyExpression left, SoyExpression right) {
    if (left.assignableToNullableInt() && right.assignableToNullableInt()) {
      return SoyExpression.forInt(
          MATH_MIN_LONG.invoke(left.unboxAs(long.class), right.unboxAs(long.class)));
    } else if (left.assignableToNullableFloat() && right.assignableToNullableFloat()) {
      return SoyExpression.forFloat(
          MATH_MIN_DOUBLE.invoke(left.unboxAs(double.class), right.unboxAs(double.class)));
    } else {
      return SoyExpression.forSoyValue(NUMBER_TYPE, MIN_FN.invoke(left.box(), right.box()));
    }
  }

  /** @see com.google.template.soy.basicfunctions.RandomIntFunction */
  private SoyExpression invokeRandomIntFunction(SoyExpression soyExpression) {
    return SoyExpression.forInt(RANDOM_INT_FN.invoke(soyExpression.unboxAs(long.class)));
  }

  /** @see com.google.template.soy.basicfunctions.RoundFunction */
  private SoyExpression invokeRoundFunction(SoyExpression soyExpression) {
    if (soyExpression.assignableToNullableInt()) {
      return soyExpression;
    }
    if (soyExpression.assignableToNullableFloat()) {
      return SoyExpression.forInt(MATH_ROUND.invoke(soyExpression.unboxAs(double.class)));
    }
    return SoyExpression.forInt(ROUND_FN.invoke(soyExpression.box()));
  }

  /** @see com.google.template.soy.basicfunctions.RoundFunction */
  private SoyExpression invokeRoundFunction(SoyExpression value, SoyExpression digitsAfterPoint) {
    return SoyExpression.forSoyValue(
        NUMBER_TYPE,
        ROUND_WITH_NUM_DIGITS_AFTER_POINT_FN.invoke(
            value.box(),
            BytecodeUtils.numericConversion(digitsAfterPoint.unboxAs(long.class), Type.INT_TYPE)));
  }

  private SoyExpression invokeSoyFunction(FunctionNode node, List<SoyExpression> args) {
    Expression soyJavaFunctionExpr =
        MethodRef.RENDER_CONTEXT_GET_FUNCTION.invoke(
            parameterLookup.getRenderContext(), constant(node.getFunctionName()));
    Expression list = SoyExpression.asBoxedList(args);
    // Most soy functions don't have return types, but if they do we should enforce it
    return SoyExpression.forSoyValue(
        node.getType(),
        MethodRef.RUNTIME_CALL_SOY_FUNCTION
            .invoke(soyJavaFunctionExpr, list)
            .checkedCast(SoyRuntimeType.getBoxedType(node.getType()).runtimeType()));
  }

  /** @see com.google.template.soy.basicfunctions.StrContainsFunction */
  private SoyExpression invokeStrContainsFunction(SoyExpression left, SoyExpression right) {
    return SoyExpression.forBool(
        left.unboxAs(String.class).invoke(STRING_CONTAINS, right.coerceToString()));
  }

  /** @see com.google.template.soy.basicfunctions.StrIndexOfFunction */
  private SoyExpression invokeStrIndexOfFunction(SoyExpression left, SoyExpression right) {
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(
            left.unboxAs(String.class).invoke(STRING_INDEX_OF, right.unboxAs(String.class)),
            Type.LONG_TYPE));
  }

  /** @see com.google.template.soy.basicfunctions.StrIndexOfFunction */
  private SoyExpression invokeStrLenFunction(SoyExpression str) {
    return SoyExpression.forInt(
        BytecodeUtils.numericConversion(
            str.unboxAs(String.class).invoke(STRING_LENGTH), Type.LONG_TYPE));
  }

  /** @see com.google.template.soy.basicfunctions.StrSubFunction */
  private SoyExpression invokeStrSubFunction(SoyExpression str, SoyExpression startIndex) {
    return SoyExpression.forString(
        str.unboxAs(String.class)
            .invoke(
                STRING_SUBSTR_START,
                BytecodeUtils.numericConversion(startIndex.unboxAs(long.class), Type.INT_TYPE)));
  }

  /** @see com.google.template.soy.basicfunctions.StrSubFunction */
  private SoyExpression invokeStrSubFunction(
      SoyExpression str, SoyExpression startIndex, SoyExpression endIndex) {
    return SoyExpression.forString(
        str.unboxAs(String.class)
            .invoke(
                STRING_SUBSTR_START_END,
                BytecodeUtils.numericConversion(startIndex.unboxAs(long.class), Type.INT_TYPE),
                BytecodeUtils.numericConversion(endIndex.unboxAs(long.class), Type.INT_TYPE)));
  }
}
