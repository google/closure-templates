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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.Names;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.soytree.ConstNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import java.util.Arrays;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** Compiles byte code for {@link ConstNode}s. */
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
    Method memberMethod = buildMemberMethod(extern.getIdentifier().identifier(), extern.getType());

    if (!extern.getJavaImpl().isPresent()) {
      Statement.throwExpression(MethodRef.NO_EXTERN_JAVA_IMPL.invoke())
          .writeMethod(methodAccess(), memberMethod, writer);
      return;
    }

    JavaImplNode javaImpl = extern.getJavaImpl().get();

    ImmutableList.Builder<String> paramNamesBuilder = ImmutableList.builder();
    for (int i = 0; i < javaImpl.params().size(); i++) {
      paramNamesBuilder.add("p" + (i + 1));
    }
    ImmutableList<String> paramNames = paramNamesBuilder.build();

    TypeInfo externClass = TypeInfo.create(javaImpl.className(), false);
    TypeInfo returnType = getTypeInfoLoadedIfPossible(javaImpl.returnType());
    TypeInfo[] paramTypesInfos =
        javaImpl.params().stream()
            .map(ExternCompiler::getTypeInfoLoadedIfPossible)
            .toArray(TypeInfo[]::new);
    Type[] paramTypes = Arrays.stream(paramTypesInfos).map(TypeInfo::type).toArray(Type[]::new);

    Label start = new Label();
    Label end = new Label();
    TemplateVariableManager paramSet =
        new TemplateVariableManager(
            TypeInfo.createClass(
                    Names.javaClassNameFromSoyNamespace(
                        extern.getNearestAncestor(SoyFileNode.class).getNamespace()))
                .type(),
            memberMethod,
            paramNames,
            start,
            end,
            /*isStatic=*/ true);

    Method externMethod = new Method(javaImpl.methodName(), returnType.type(), paramTypes);
    Expression[] adaptedParams = new Expression[paramNames.size()];
    for (int i = 0; i < paramTypesInfos.length; i++) {
      adaptedParams[i] =
          adaptParameter(
              paramSet.getVariable(paramNames.get(i)),
              paramTypesInfos[i],
              extern.getType().getParameters().get(i).getType());
    }

    Expression body =
        adaptReturnType(
            memberMethod.getReturnType(),
            MethodRef.createStaticMethod(externClass, externMethod).invoke(adaptedParams));

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

  static SoyRuntimeType getRuntimeType(SoyType type) {
    return SoyRuntimeType.getUnboxedType(type).orElseGet(() -> SoyRuntimeType.getBoxedType(type));
  }

  static Method buildMemberMethod(String symbol, FunctionType type) {
    Type[] args =
        type.getParameters().stream()
            .map(p -> getRuntimeType(p.getType()).runtimeType())
            .toArray(Type[]::new);
    return new Method(symbol, getRuntimeType(type.getReturnType()).runtimeType(), args);
  }

  private static TypeInfo getTypeInfoLoadedIfPossible(String s) {
    try {
      return TypeInfo.create(MethodSignature.forName(s));
    } catch (ClassNotFoundException e) {
      return TypeInfo.create(s, false);
    }
  }

  private static Expression adaptParameter(
      Expression paramAsSoyType, TypeInfo javaTypeInfo, SoyType soyType) {
    Type javaType = javaTypeInfo.type();
    SoyExpression actualParam =
        paramAsSoyType instanceof SoyExpression
            ? (SoyExpression) paramAsSoyType
            : SoyExpression.forRuntimeType(getRuntimeType(soyType), paramAsSoyType);

    // If expecting a bland 'SoyData', just box the expr.
    if (javaType.equals(BytecodeUtils.SOY_DATA_TYPE)) {
      return actualParam.box().checkedCast(javaType);
    }
    // If we expect a specific SoyValue subclass, then box + cast.
    if (javaTypeInfo.classOptional().isPresent()
        && SoyValue.class.isAssignableFrom(javaTypeInfo.classOptional().get())) {
      return actualParam.box().checkedCast(javaType);
    }

    // Otherwise, we're an unboxed type (non-SoyValue).

    // int needs special-casing for overflow, and because we can't unbox as int
    if (javaType.equals(Type.INT_TYPE)) {
      return MethodRef.LONG_TO_INT.invoke(actualParam);
    } else if (javaType.equals(BytecodeUtils.INTEGER_TYPE)) {
      return MethodRef.BOX_INTEGER.invoke(MethodRef.LONG_TO_INT.invoke(actualParam));
    } else if (javaType.equals(Type.DOUBLE_TYPE)) {
      return actualParam.coerceToDouble();
    } else if (javaType.equals(BytecodeUtils.BOXED_DOUBLE_TYPE)) {
      return MethodRef.BOX_DOUBLE.invoke(actualParam.coerceToDouble());
    }
    // For protos, we need to unbox as Message & then cast.
    if (soyType.getKind() == Kind.MESSAGE) {
      return actualParam;
    } else if (soyType.getKind() == Kind.PROTO) {
      return actualParam.checkedCast(
          ProtoUtils.messageRuntimeType(((SoyProtoType) soyType).getDescriptor()).type());
    }
    // For protocol enums, we need to call forNumber on the type w/ the param (as casted to an int).
    // This is because Soy internally stores enums as ints. We know this is safe because we
    // already validated that the enum type matches the signature.
    if (soyType.getKind() == Kind.PROTO_ENUM) {
      return MethodRef.createStaticMethod(
              javaTypeInfo, new Method("forNumber", javaType, new Type[] {Type.INT_TYPE}))
          .invoke(BytecodeUtils.numericConversion(actualParam.unboxAsLong(), Type.INT_TYPE));
    }

    if (javaType.equals(Type.BOOLEAN_TYPE)) {
      return actualParam.unboxAsBoolean();
    } else if (javaType.equals(BytecodeUtils.BOXED_BOOLEAN_TYPE)) {
      return MethodRef.BOX_BOOLEAN.invoke(actualParam.unboxAsBoolean());
    } else if (javaType.equals(Type.LONG_TYPE)) {
      return actualParam.unboxAsLong();
    } else if (javaType.equals(BytecodeUtils.BOXED_LONG_TYPE)) {
      return MethodRef.BOX_LONG.invoke(actualParam.unboxAsLong());
    } else if (javaType.equals(BytecodeUtils.STRING_TYPE)) {
      return actualParam.unboxAsString();
    } else if (javaType.equals(BytecodeUtils.LIST_TYPE)
        || javaType.equals(BytecodeUtils.IMMUTIBLE_LIST_TYPE)) {
      SoyType elmType = ((ListType) soyType).getElementType();
      SoyExpression unboxedList = actualParam.isBoxed() ? actualParam.unboxAsList() : actualParam;
      switch (elmType.getKind()) {
        case INT:
          return MethodRef.LIST_UNBOX_INTS.invoke(unboxedList);
        case FLOAT:
          return MethodRef.LIST_UNBOX_FLOATS.invoke(unboxedList);
        case STRING:
          return MethodRef.LIST_UNBOX_STRINGS.invoke(unboxedList);
        case BOOL:
          return MethodRef.LIST_UNBOX_BOOLS.invoke(unboxedList);
        case PROTO:
          return MethodRef.LIST_UNBOX_PROTOS.invoke(unboxedList);
        case PROTO_ENUM:
          String javaClass =
              JavaQualifiedNames.getClassName(((SoyProtoEnumType) elmType).getDescriptor());
          return MethodRef.LIST_UNBOX_ENUMS.invoke(
              unboxedList, BytecodeUtils.constant(BytecodeUtils.getTypeForClassName(javaClass)));
        default:
          throw new AssertionError("ValidateExternsPass should prevent this.");
      }
    } else if (javaType.equals(BytecodeUtils.MAP_TYPE)
        || javaType.equals(BytecodeUtils.IMMUTIBLE_MAP_TYPE)) {
      SoyType keyType = ((MapType) soyType).getKeyType();
      SoyType valueType = ((MapType) soyType).getValueType();
      return MethodRef.UNBOX_MAP.invoke(
          actualParam,
          BytecodeUtils.constant(BytecodeUtils.getTypeForSoyType(keyType)),
          BytecodeUtils.constant(BytecodeUtils.getTypeForSoyType(valueType)));
    } else if (javaType.equals(BytecodeUtils.OBJECT.type())) {
      return actualParam.isBoxed() ? MethodRef.UNBOX_OBJECT.invoke(actualParam) : actualParam;
    }

    throw new AssertionError(
        String.format(
            "Unable to convert parameter of Soy type %s to java type %s.", soyType, javaType));
  }

  static Expression adaptReturnType(Type returnType, Expression externCall) {
    Type externType = externCall.resultType();

    if (externType.equals(BytecodeUtils.INTEGER_TYPE)) {
      return MethodRef.UNBOX_INTEGER.invoke(externCall);
    } else if (externType.equals(Type.INT_TYPE)) {
      return MethodRef.INT_TO_LONG.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.BOXED_LONG_TYPE)) {
      return MethodRef.UNBOX_LONG.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.INTEGER_DATA_TYPE)) {
      return MethodRef.SOY_VALUE_LONG_VALUE.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.BOXED_DOUBLE_TYPE)) {
      return MethodRef.UNBOX_DOUBLE.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.FLOAT_DATA_TYPE)) {
      return MethodRef.SOY_VALUE_FLOAT_VALUE.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.BOXED_BOOLEAN_TYPE)) {
      return MethodRef.UNBOX_BOOLEAN.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.BOOLEAN_DATA_TYPE)) {
      return MethodRef.SOY_VALUE_BOOLEAN_VALUE.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.STRING_DATA_TYPE)) {
      return MethodRef.SOY_VALUE_STRING_VALUE.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.OBJECT.type())) {
      return MethodRef.CONVERT_OBJECT_TO_SOY_VALUE_PROVIDER.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.LIST_TYPE)
        || externType.equals(BytecodeUtils.IMMUTIBLE_LIST_TYPE)) {
      return MethodRef.LIST_BOX_VALUES.invoke(externCall);
    }

    return externCall;
  }
}
