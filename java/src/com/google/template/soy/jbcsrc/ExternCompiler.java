/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.ConstantsCompiler.ConstantVariables;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef.MethodPureness;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.runtime.JbcSrcExternRuntime;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** Compiles byte code for {@link ExternNode}s. */
public final class ExternCompiler {

  private final ExternNode extern;
  private final SoyClassWriter writer;

  ExternCompiler(ExternNode extern, SoyClassWriter writer) {
    this.extern = extern;
    this.writer = writer;
  }

  public void compile() {
    // We define a local static method that simply delegates to the extern method. However, the
    // local method parameter types match the soy types from the extern definition while the extern
    // delegate parameter types match the java types from the javaimpl definition.

    if (!extern.getJavaImpl().isPresent()) {
      Statement.throwExpression(JbcSrcExternRuntime.NO_EXTERN_JAVA_IMPL.invoke())
          .writeMethod(
              methodAccess(),
              buildMemberMethod(
                  extern.getIdentifier().identifier(),
                  extern.getType(),
                  /* requiresRenderContext= */ false,
                  extern.isJavaImplAsync()),
              writer);
      return;
    }

    JavaImplNode javaImpl = extern.getJavaImpl().get();
    Method memberMethod =
        buildMemberMethod(
            extern.getIdentifier().identifier(),
            extern.getType(),
            javaImpl.requiresRenderContext(),
            extern.isJavaImplAsync());
    int declaredMethodArgs = extern.getType().getParameters().size();

    ImmutableList.Builder<String> paramNamesBuilder = ImmutableList.builder();
    int paramNamesOffset = 0;
    if (javaImpl.requiresRenderContext()) {
      paramNamesBuilder.add(StandardNames.RENDER_CONTEXT);
      paramNamesOffset = 1;
    }
    for (int i = 0; i < declaredMethodArgs; i++) {
      paramNamesBuilder.add("p" + (i + 1 /* start with p1 */));
    }
    ImmutableList<String> paramNames = paramNamesBuilder.build();

    TypeInfo externClass = TypeInfo.create(javaImpl.className(), javaImpl.isInterface());
    TypeInfo returnType = getTypeInfoLoadedIfPossible(javaImpl.returnType().className());
    TypeInfo[] paramTypesInfos =
        javaImpl.paramTypes().stream()
            .map(d -> getTypeInfoLoadedIfPossible(d.className()))
            .toArray(TypeInfo[]::new);
    Type[] paramTypes = stream(paramTypesInfos).map(TypeInfo::type).toArray(Type[]::new);

    Label start = new Label();
    Label end = new Label();
    TemplateVariableManager paramSet =
        new TemplateVariableManager(
            TypeInfo.createClass(
                    Names.javaClassNameFromSoyNamespace(
                        extern.getNearestAncestor(SoyFileNode.class).getNamespace()))
                .type(),
            memberMethod.getArgumentTypes(),
            paramNames,
            start,
            end,
            /* isStatic= */ true);
    var renderContext =
        javaImpl.requiresRenderContext()
            ? Optional.of(
                new RenderContextExpression(paramSet.getVariable(StandardNames.RENDER_CONTEXT)))
            : Optional.<RenderContextExpression>empty();
    ConstantVariables vars = new ConstantVariables(paramSet, renderContext);

    Method externMethod = new Method(javaImpl.methodName(), returnType.type(), paramTypes);

    List<Expression> adaptedParams = new ArrayList<>();
    if (!javaImpl.isStatic()) {
      adaptedParams.add(
          vars.getRenderContext()
              .getPluginInstance(javaImpl.className())
              .checkedCast(externClass.type()));
    }
    for (int i = 0; i < declaredMethodArgs; i++) {
      adaptedParams.add(
          adaptParameter(
              paramSet.getVariable(paramNames.get(i + paramNamesOffset)),
              paramTypesInfos[i],
              extern.getType().getParameters().get(i).getType()));
    }
    // Add implicit params.
    for (int i = declaredMethodArgs; i < javaImpl.paramTypes().size(); i++) {
      adaptedParams.add(adaptImplicitParameter(vars, paramTypesInfos[i]));
    }

    MethodRef extMethodRef;
    if (javaImpl.isStatic()) {
      extMethodRef =
          MethodRef.createStaticMethod(externClass, externMethod, MethodPureness.NON_PURE);
    } else if (javaImpl.isInterface()) {
      extMethodRef =
          MethodRef.createInterfaceMethod(externClass, externMethod, MethodPureness.NON_PURE);
    } else {
      extMethodRef =
          MethodRef.createInstanceMethod(externClass, externMethod, MethodPureness.NON_PURE);
    }

    Expression body =
        adaptReturnType(
            returnType.type(),
            extern.getType().getReturnType(),
            extMethodRef.invoke(adaptedParams));

    new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        body.gen(adapter);
        adapter.mark(end);
        adapter.returnValue();
        paramSet.generateTableEntries(adapter);
      }
    }.writeMethod(methodAccess(), memberMethod, writer);
  }

  private int methodAccess() {
    // Same issue as TemplateCompiler#methodAccess
    return (extern.isExported() ? Opcodes.ACC_PUBLIC : 0) | Opcodes.ACC_STATIC;
  }

  /**
   * The type representation of the return type and parameters of the generated static extern
   * method. These are the declared types of this method and therefore the types that Soy template
   * callers of this extern need to adapt to. These are not the types of the user-provided Java
   * implementation of the extern.
   *
   * <p>Extern implementations generally want unboxed and nullable values. The templates may have
   * unboxed or boxed values. So we make the boundary here unboxed and nullable to avoid boxing
   * between the template and generated method since we would then just unbox before passing to the
   * implementation.
   */
  static SoyRuntimeType getRuntimeType(SoyType type) {
    SoyType nonNullable = SoyTypes.tryRemoveNullish(type.getEffectiveType());
    SoyRuntimeType runtimeType =
        SoyRuntimeType.getUnboxedType(nonNullable)
            .orElseGet(() -> SoyRuntimeType.getBoxedType(nonNullable));
    if (!nonNullable.equals(type) && BytecodeUtils.isPrimitive(runtimeType.runtimeType())) {
      // int|null -> SoyValue
      runtimeType = SoyRuntimeType.getBoxedType(type);
    }
    return runtimeType;
  }

  static Method buildMemberMethod(
      String symbol, FunctionType type, boolean requiresRenderContext, boolean async) {
    Type[] args =
        Streams.concat(
                requiresRenderContext
                    ? Stream.of(BytecodeUtils.RENDER_CONTEXT_TYPE)
                    : Stream.empty(),
                type.getParameters().stream().map(p -> getRuntimeType(p.getType()).runtimeType()))
            .toArray(Type[]::new);
    Type returnType =
        async
            ? BytecodeUtils.SOY_VALUE_PROVIDER_TYPE
            : getRuntimeType(type.getReturnType()).runtimeType();
    return new Method(symbol, returnType, args);
  }

  private static TypeInfo getTypeInfoLoadedIfPossible(String s) {
    try {
      return TypeInfo.create(MethodSignature.forName(s));
    } catch (ClassNotFoundException e) {
      return TypeInfo.create(s, false);
    }
  }

  /**
   * Adapts a soy value to a Java value to be passed to the Java implementation of the extern.
   *
   * <p>Extern implementations expect Java null for Soy null values.
   */
  private static Expression adaptParameter(
      Expression param, TypeInfo javaTypeInfo, SoyType soyType) {
    Type javaType = javaTypeInfo.type();
    SoyExpression actualParam =
        param instanceof SoyExpression
            ? (SoyExpression) param
            : SoyExpression.forRuntimeType(getRuntimeType(soyType), param);
    boolean soyTypeBoxed = actualParam.soyRuntimeType().isBoxed();
    boolean isObject = javaType.equals(BytecodeUtils.OBJECT.type());

    // If expecting a bland 'SoyValue', just box the expr.
    if (javaType.equals(BytecodeUtils.SOY_VALUE_TYPE)
        || javaType.equals(BytecodeUtils.PRIMITIVE_DATA_TYPE)) {
      // NullData -> null, UndefinedData -> UndefinedData
      return actualParam.boxWithSoyNullAsJavaNull().checkedCast(javaType);
    }
    // If we expect a specific SoyValue subclass, then box + cast.
    if (javaTypeInfo.classOptional().isPresent()
        && SoyValue.class.isAssignableFrom(javaTypeInfo.classOptional().get())) {
      // NullData -> null, UndefinedData -> null
      return actualParam.boxWithSoyNullishAsJavaNull().checkedCast(javaType);
    }

    // Otherwise, we're an unboxed type (non-SoyValue).

    // int needs special-casing for overflow, and because we can't unbox as int
    if (javaType.equals(Type.INT_TYPE)) {
      return JbcSrcExternRuntime.LONG_TO_INT.invoke(actualParam);
    } else if (javaType.equals(BytecodeUtils.BOXED_INTEGER_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_INTEGER.invoke(actualParam);
      }
      return MethodRefs.BOX_INTEGER.invoke(JbcSrcExternRuntime.LONG_TO_INT.invoke(actualParam));
    } else if (javaType.equals(Type.DOUBLE_TYPE)) {
      return actualParam.coerceToDouble();
    } else if (javaType.equals(BytecodeUtils.BOXED_DOUBLE_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_DOUBLE.invoke(actualParam);
      }
      return MethodRefs.BOX_DOUBLE.invoke(actualParam.coerceToDouble());
    } else if (javaType.equals(Type.FLOAT_TYPE)) {
      return BytecodeUtils.numericConversion(actualParam.coerceToDouble(), Type.FLOAT_TYPE);
    } else if (javaType.equals(BytecodeUtils.BOXED_FLOAT_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_FLOAT.invoke(actualParam);
      }
      return MethodRefs.BOX_FLOAT.invoke(
          BytecodeUtils.numericConversion(actualParam.coerceToDouble(), Type.FLOAT_TYPE));
    } else if (javaType.equals(BytecodeUtils.NUMBER_TYPE)) {
      return actualParam.unboxAsNumberOrJavaNull();
    } else if (javaType.equals(Type.getType(BigInteger.class))) {
      return JbcSrcExternRuntime.CONVERT_SOY_VALUE_TO_BIG_INTEGER.invoke(actualParam);
    }

    SoyType nonNullableSoyType = SoyTypes.tryRemoveNullish(soyType);

    // For protos, we need to unbox as Message & then cast.
    if (nonNullableSoyType.getKind() == Kind.MESSAGE) {
      return actualParam;
    } else if (nonNullableSoyType.getKind() == Kind.PROTO) {
      return actualParam.checkedCast(
          ProtoUtils.messageRuntimeType(((SoyProtoType) nonNullableSoyType).getDescriptor())
              .type());
    }
    // For protocol enums, we need to call forNumber on the type w/ the param (as casted to an int).
    // This is because Soy internally stores enums as ints. We know this is safe because we
    // already validated that the enum type matches the signature.
    if (nonNullableSoyType.getKind() == Kind.PROTO_ENUM) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_ENUM
            .invoke(
                actualParam,
                BytecodeUtils.constant(BytecodeUtils.getTypeForClassName(javaType.getClassName())))
            .checkedCast(javaType);
      }
      return MethodRef.createStaticMethod(
              javaTypeInfo,
              new Method("forNumber", javaType, new Type[] {Type.INT_TYPE}),
              MethodPureness.PURE)
          .invoke(BytecodeUtils.numericConversion(actualParam.unboxAsLong(), Type.INT_TYPE));
    }

    if (javaType.equals(BytecodeUtils.SAFE_URL_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_SAFE_URL.invoke(actualParam);
    } else if (javaType.equals(BytecodeUtils.SAFE_HTML_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_SAFE_HTML.invoke(actualParam);
    } else if (javaType.equals(BytecodeUtils.TRUSTED_RESOURCE_URL_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_TRUSTED_RESOURCE_URL.invoke(actualParam);
    } else if (javaType.equals(BytecodeUtils.SAFE_URL_PROTO_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_SAFE_URL_PROTO.invoke(actualParam);
    } else if (javaType.equals(BytecodeUtils.TRUSTED_RESOURCE_PROTO_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_TRUSTED_RESOURCE_URL_PROTO.invoke(actualParam);
    } else if (javaType.equals(BytecodeUtils.SAFE_HTML_PROTO_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_SAFE_HTML_PROTO.invoke(actualParam);
    }

    if (javaType.equals(Type.BOOLEAN_TYPE)) {
      return actualParam.unboxAsBoolean();
    } else if (javaType.equals(BytecodeUtils.BOXED_BOOLEAN_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_BOOLEAN.invoke(actualParam);
      }
      return MethodRefs.BOX_BOOLEAN.invoke(actualParam.unboxAsBoolean());
    } else if (javaType.equals(Type.LONG_TYPE)) {
      return actualParam.unboxAsLong();
    } else if (javaType.equals(BytecodeUtils.BOXED_LONG_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_LONG.invoke(actualParam);
      }
      return MethodRefs.BOX_LONG.invoke(actualParam.unboxAsLong());
    } else if (javaType.equals(BytecodeUtils.STRING_TYPE)) {
      return actualParam.unboxAsStringOrJavaNull();
    } else if (!isObject
        && BytecodeUtils.isDefinitelyAssignableFrom(javaType, BytecodeUtils.IMMUTABLE_LIST_TYPE)) {
      SoyType elmType = SoyTypes.getIterableElementType(nonNullableSoyType);
      SoyExpression unboxedList =
          actualParam.isBoxed() ? actualParam.unboxAsListOrJavaNull() : actualParam;
      switch (elmType.getKind()) {
        case INT:
          return JbcSrcExternRuntime.LIST_UNBOX_INTS.invoke(unboxedList);
        case FLOAT:
          return JbcSrcExternRuntime.LIST_UNBOX_FLOATS.invoke(unboxedList);
        case STRING:
          return JbcSrcExternRuntime.LIST_UNBOX_STRINGS.invoke(unboxedList);
        case BOOL:
          return JbcSrcExternRuntime.LIST_UNBOX_BOOLS.invoke(unboxedList);
        case MESSAGE:
        case PROTO:
          return JbcSrcExternRuntime.LIST_UNBOX_PROTOS.invoke(unboxedList);
        case PROTO_ENUM:
          String javaClass =
              JavaQualifiedNames.getClassName(((SoyProtoEnumType) elmType).getDescriptor());
          return JbcSrcExternRuntime.LIST_UNBOX_ENUMS.invoke(
              unboxedList, BytecodeUtils.constant(BytecodeUtils.getTypeForClassName(javaClass)));
        case UNION:
          if (SoyTypes.NUMBER_TYPE.equals(elmType)) {
            return JbcSrcExternRuntime.LIST_UNBOX_NUMBERS.invoke(unboxedList);
          }
        // fall through
        default:
          return actualParam.isBoxed()
              ? JbcSrcExternRuntime.UNBOX_OBJECT.invoke(actualParam)
              : JbcSrcExternRuntime.DEEP_UNBOX_LIST.invoke(actualParam);
      }
    } else if (javaType.equals(BytecodeUtils.MAP_TYPE)
        || javaType.equals(BytecodeUtils.IMMUTABLE_MAP_TYPE)) {
      if (nonNullableSoyType.getKind() == Kind.RECORD) {
        if (javaType.equals(BytecodeUtils.MAP_TYPE)) {
          return JbcSrcExternRuntime.RECORD_TO_MAP.invoke(actualParam);
        } else {
          return JbcSrcExternRuntime.RECORD_TO_IMMUTABLE_MAP.invoke(actualParam);
        }
      }
      SoyType keyType = SoyTypes.getMapKeysType(nonNullableSoyType);
      SoyType valueType = SoyTypes.getMapValuesType(nonNullableSoyType);
      return JbcSrcExternRuntime.UNBOX_MAP.invoke(
          actualParam,
          BytecodeUtils.constant(BytecodeUtils.getTypeForSoyType(keyType)),
          BytecodeUtils.constant(BytecodeUtils.getTypeForSoyType(valueType)));
    } else if (javaType.equals(BytecodeUtils.OBJECT.type())) {
      if (BytecodeUtils.isPrimitive(actualParam.soyRuntimeType().runtimeType())) {
        return BytecodeUtils.boxJavaPrimitive(actualParam);
      }
      return actualParam.isBoxed()
          ? JbcSrcExternRuntime.UNBOX_OBJECT.invoke(actualParam)
          : actualParam;
    }

    throw new AssertionError(
        String.format(
            "Unable to convert parameter of Soy type %s to java type %s.", soyType, javaType));
  }

  private static Expression adaptImplicitParameter(ConstantVariables vars, TypeInfo javaTypeInfo) {
    switch (javaTypeInfo.className()) {
      case "com.google.template.soy.data.Dir":
        return vars.getRenderContext().getBidiGlobalDirDir();
      case "com.google.template.soy.plugin.java.RenderCssHelper":
        return vars.getRenderContext().getRenderCssHelper();
      case "com.ibm.icu.util.ULocale":
        return vars.getRenderContext().getULocale();
      default:
        throw new IllegalArgumentException(javaTypeInfo.className());
    }
  }

  /**
   * Adapts the return value of the extern Java implementation to the expected Soy value.
   *
   * <p>In some cases (e.g. List) we happen to tolerate the extern returning null.
   */
  static Expression adaptReturnType(Type returnType, SoyType soyReturnType, Expression externCall) {
    boolean nullish = SoyTypes.isNullish(soyReturnType);
    Type externType = externCall.resultType();

    if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.FUTURE_TYPE, returnType)) {
      return JbcSrcExternRuntime.CONVERT_OBJECT_TO_SOY_VALUE_PROVIDER.invoke(externCall);
    }

    if (!nullish && externType.equals(BytecodeUtils.BOXED_INTEGER_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_INTEGER.invoke(externCall);
    } else if (externType.equals(Type.INT_TYPE)) {
      return BytecodeUtils.numericConversion(externCall, Type.LONG_TYPE);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_LONG_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_LONG.invoke(externCall);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_DOUBLE_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_DOUBLE.invoke(externCall);
    } else if (externType.equals(Type.FLOAT_TYPE)) {
      return BytecodeUtils.numericConversion(externCall, Type.DOUBLE_TYPE);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_FLOAT_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_FLOAT.invoke(externCall);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_BOOLEAN_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_BOOLEAN.invoke(externCall);
    } else if (!nullish && externType.equals(BytecodeUtils.BIG_INTEGER_TYPE)) {
      return JbcSrcExternRuntime.GBIGINT_FOR_VALUE.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.OBJECT.type())
        || externType.equals(BytecodeUtils.NUMBER_TYPE)) {
      checkState(soyReturnType.getKind() != SoyType.Kind.MAP);
      return JbcSrcExternRuntime.CONVERT_OBJECT_TO_SOY_VALUE.invoke(externCall);
    } else if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.MAP_TYPE, externType)) {
      // When Soy sees a map, it defaults to thinking it's a legacy_object_map, which only allow
      // string keys. We know that's not the case here (because the Soy return type of the extern
      // is "map") so mark this as a "map" and not a "legacy_object_map".
      if (soyReturnType.getKind() == SoyType.Kind.MAP) {
        externCall = JbcSrcExternRuntime.MARK_AS_SOY_MAP.invoke(externCall);
      }
      return JbcSrcExternRuntime.CONVERT_OBJECT_TO_SOY_VALUE.invoke(externCall);
    } else if (BytecodeUtils.isDefinitelyAssignableFrom(
        BytecodeUtils.COLLECTION_TYPE, externType)) {
      return JbcSrcExternRuntime.LIST_BOX_VALUES.invoke(externCall);
    } else if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.ITERABLE_TYPE, externType)) {
      return JbcSrcExternRuntime.ITERABLE_BOX_VALUES.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.SAFE_URL_TYPE)) {
      return JbcSrcExternRuntime.CONVERT_SAFE_URL_TO_SOY_VALUE_PROVIDER.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.SAFE_URL_PROTO_TYPE)) {
      return JbcSrcExternRuntime.CONVERT_SAFE_URL_PROTO_TO_SOY_VALUE_PROVIDER.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.TRUSTED_RESOURCE_PROTO_TYPE)) {
      return JbcSrcExternRuntime.CONVERT_TRUSTED_RESOURCE_URL_PROTO_TO_SOY_VALUE_PROVIDER.invoke(
          externCall);
    } else if (externType.equals(BytecodeUtils.SAFE_HTML_TYPE)) {
      return JbcSrcExternRuntime.CONVERT_SAFE_HTML_TO_SOY_VALUE_PROVIDER.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.SAFE_HTML_PROTO_TYPE)) {
      return JbcSrcExternRuntime.CONVERT_SAFE_HTML_PROTO_TO_SOY_VALUE_PROVIDER.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.TRUSTED_RESOURCE_URL_TYPE)) {
      return JbcSrcExternRuntime.CONVERT_TRUSTED_RESOURCE_URL_TO_SOY_VALUE_PROVIDER.invoke(
          externCall);
    } else if (soyReturnType.getKind() == SoyType.Kind.PROTO_ENUM) {
      return BytecodeUtils.numericConversion(
          MethodRefs.PROTOCOL_ENUM_GET_NUMBER.invoke(externCall), Type.LONG_TYPE);
    } else if (BytecodeUtils.SOY_VALUE_TYPE.equals(getRuntimeType(soyReturnType).runtimeType())) {
      // If the Soy return type of the extern is SoyValue, then we need to make sure the value
      // returned from the implementation is boxed.
      if (BytecodeUtils.isPrimitive(externCall.resultType())) {
        // convertObjectToSoyValueProvider requires values to be Java-boxed (i.e. int to Integer) so
        // do that first if needed.
        externCall = BytecodeUtils.boxJavaPrimitive(externCall.resultType(), externCall);
      }
      return JbcSrcExternRuntime.CONVERT_OBJECT_TO_SOY_VALUE.invoke(externCall);
    }
    return externCall;
  }
}
