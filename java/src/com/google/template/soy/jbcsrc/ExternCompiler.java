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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.BIG_INTEGER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.FUNCTION_VALUE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.ITERABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.MESSAGE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isNumericPrimitive;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.newLabel;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.numericConversion;
import static com.google.template.soy.jbcsrc.restricted.MethodRefs.IMMUTABLE_LIST_COPY_OF_ITERABLE;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.template.soy.base.internal.TypeReference;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.ConstantsCompiler.ConstantVariables;
import com.google.template.soy.jbcsrc.ExpressionCompiler.BasicExpressionCompiler;
import com.google.template.soy.jbcsrc.TemplateCompiler.TemplateVariables;
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
import com.google.template.soy.soytree.AutoImplNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.FileMetadata.Extern;
import com.google.template.soy.soytree.FileMetadata.Extern.JavaImpl;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.JavaImplNode;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.FunctionType.Parameter;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.MessageType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
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

  static final TemplateAnalysis EXTERN_CONTEXT =
      new TemplateAnalysis() {
        @Override
        public boolean isResolved(VarRefNode ref) {
          return true;
        }

        @Override
        public boolean isResolved(DataAccessNode ref) {
          return true;
        }
      };

  private final ExternNode extern;
  private final SoyClassWriter writer;
  private final JavaSourceFunctionCompiler javaSourceFunctionCompiler;
  private final FileSetMetadata fileSetMetadata;
  private final Extern externMetadata;

  ExternCompiler(
      ExternNode extern,
      SoyClassWriter writer,
      JavaSourceFunctionCompiler javaSourceFunctionCompiler,
      FileSetMetadata fileSetMetadata) {
    this.extern = extern;
    this.writer = writer;
    this.javaSourceFunctionCompiler = javaSourceFunctionCompiler;
    this.fileSetMetadata = fileSetMetadata;
    this.externMetadata = Metadata.forAst(extern);
  }

  public void compile() {
    // We define a local static method that simply delegates to the extern method. However, the
    // local method parameter types match the soy types from the extern definition while the extern
    // delegate parameter types match the java types from the javaimpl definition.

    boolean requiresRenderContext = false;
    Optional<JavaImplNode> javaOpt = extern.getJavaImpl();
    Optional<AutoImplNode> autoOpt = extern.getAutoImpl();
    if (javaOpt.isEmpty() && autoOpt.isEmpty()) {
      Statement.throwExpression(JbcSrcExternRuntime.NO_EXTERN_JAVA_IMPL.invoke())
          .writeMethod(
              methodAccess(),
              buildMemberMethod(
                  extern.getIdentifier().identifier(),
                  extern.getType(),
                  requiresRenderContext,
                  extern.isJavaImplAsync()),
              writer);
      return;
    }

    boolean autoJava = javaOpt.isEmpty() && autoOpt.isPresent();
    // TODO(b/408029720): Do not code gen if private and not auto java. Private externs from
    //    function pointers are still invoked via the dynamic path.
    requiresRenderContext = ExpressionCompiler.requiresRenderContext(externMetadata);
    Method memberMethod =
        buildMemberMethod(
            extern.getIdentifier().identifier(),
            extern.getType(),
            requiresRenderContext,
            extern.isJavaImplAsync());
    int declaredMethodArgs = extern.getType().getParameters().size();

    int paramNamesOffset = 0;
    ImmutableList.Builder<String> paramNamesBuilder = ImmutableList.builder();
    if (requiresRenderContext) {
      paramNamesBuilder.add(StandardNames.RENDER_CONTEXT);
      paramNamesOffset = 1;
    }

    if (autoJava) {
      extern.getType().getParameters().stream()
          .map(Parameter::getName)
          .forEach(paramNamesBuilder::add);
    } else {
      for (int i = 0; i < declaredMethodArgs; i++) {
        paramNamesBuilder.add("p" + (i + 1 /* start with p1 */));
      }
    }
    ImmutableList<String> paramNames = paramNamesBuilder.build();

    Label start = newLabel();
    Label end = newLabel();
    Statement body;

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
            /* isStatic= */ true,
            autoJava
                ? n -> {
                  SoyType soyType = extern.getType().getParameterMap().get(n);
                  return soyType != null ? getRuntimeType(soyType) : null;
                }
                : TemplateVariableManager.NO_RUNTIME_TYPE_KNOWN);
    var renderContext =
        requiresRenderContext
            ? Optional.of(
                new RenderContextExpression(paramSet.getVariable(StandardNames.RENDER_CONTEXT)))
            : Optional.<RenderContextExpression>empty();
    ConstantVariables vars = new ConstantVariables(paramSet, renderContext);

    if (autoJava) {
      AutoImplNode autoImpl = autoOpt.get();
      TemplateVariables variables =
          new TemplateVariables(
              paramSet,
              /* stackFrame= */ null,
              /* paramsRecord= */ Optional.empty(),
              renderContext.orElse(null));
      BasicExpressionCompiler basicCompiler =
          ExpressionCompiler.createBasicCompiler(
              autoImpl,
              EXTERN_CONTEXT,
              variables,
              paramSet,
              javaSourceFunctionCompiler,
              null,
              null);
      SoyNodeCompiler nodeCompiler =
          SoyNodeCompiler.createForExtern(
              autoImpl,
              paramSet,
              variables,
              basicCompiler,
              javaSourceFunctionCompiler,
              fileSetMetadata,
              e -> adaptReturnExpression(e, getRuntimeType(extern.getType().getReturnType())));
      List<Statement> statements = new ArrayList<>();
      SoyFileNode fileNode = extern.getNearestAncestor(SoyFileNode.class);
      if (fileNode != null) {
        statements.add(nodeCompiler.trackRequiredCssPathStatements(fileNode));
      }
      statements.add(nodeCompiler.compile(autoImpl));
      body = AppendableExpression.concat(statements);
    } else {
      JavaImplNode javaImpl = javaOpt.get();
      TypeInfo externClass = TypeInfo.create(javaImpl.className(), javaImpl.isInterface());
      Type returnType = getTypeInfoForJavaImpl(javaImpl.returnType().className()).type();
      TypeInfo[] paramTypesInfos =
          javaImpl.paramTypes().stream()
              .map(d -> getTypeInfoForJavaImpl(d.className()))
              .toArray(TypeInfo[]::new);
      Type[] paramTypes = stream(paramTypesInfos).map(TypeInfo::type).toArray(Type[]::new);

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
                extern.getType().getParameters().get(i).getType().getEffectiveType(),
                vars));
      }
      // Add implicit params.
      for (int i = declaredMethodArgs; i < javaImpl.paramTypes().size(); i++) {
        adaptedParams.add(adaptImplicitParameter(vars, paramTypesInfos[i]));
      }

      MethodRef extMethodRef =
          getMethodRef(javaImpl.type(), externClass, javaImpl.methodName(), returnType, paramTypes);
      body =
          Statement.returnExpression(
              adaptReturnType(
                  returnType,
                  extern.getType().getReturnType(),
                  extMethodRef.invoke(adaptedParams)));
    }

    checkState(body.isTerminal());
    new Statement(Statement.Kind.TERMINAL) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(start);
        body.gen(adapter);
        adapter.mark(end);
        paramSet.generateTableEntries(adapter);
      }
    }.writeMethod(methodAccess(), memberMethod, writer);
  }

  public static MethodRef getMethodRef(JavaImpl java) {
    return getMethodRef(
        java.type(),
        getTypeInfoForJavaImpl(java.className(), java.type().isInterface()),
        java.method(),
        getTypeInfoForJavaImpl(java.returnType().className()).type(),
        java.paramTypes().stream()
            .map(t -> getTypeInfoForJavaImpl(t.className()).type())
            .toArray(Type[]::new));
  }

  private static MethodRef getMethodRef(
      JavaImpl.MethodType methodType,
      TypeInfo externClass,
      String methodName,
      Type returnType,
      Type[] paramTypes) {
    Method externMethod = new Method(methodName, returnType, paramTypes);
    if (methodType.isStatic()) {
      return MethodRef.createStaticMethod(externClass, externMethod, MethodPureness.NON_PURE);
    } else if (methodType.isInterface()) {
      return MethodRef.createInterfaceMethod(externClass, externMethod, MethodPureness.NON_PURE);
    } else {
      return MethodRef.createInstanceMethod(externClass, externMethod, MethodPureness.NON_PURE);
    }
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
    SoyType nonNullable = SoyTypes.excludeNullish(type.getEffectiveType());
    SoyRuntimeType runtimeType =
        SoyRuntimeType.getUnboxedType(nonNullable)
            .orElseGet(() -> SoyRuntimeType.getBoxedType(nonNullable));
    if (!nonNullable.isEffectivelyEqual(type)
        && BytecodeUtils.isPrimitive(runtimeType.runtimeType())) {
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

  static TypeInfo getTypeInfoForJavaImpl(String s) {
    return getTypeInfoForJavaImpl(s, false);
  }

  static TypeInfo getTypeInfoForJavaImpl(String s, boolean isInterface) {
    try {
      return TypeInfo.create(MethodSignature.forName(s));
    } catch (ClassNotFoundException e) {
      return TypeInfo.create(s, isInterface);
    }
  }

  static Expression adaptParameter(
      Expression param, TypeReference javaType, SoyType soyType, TemplateParameterLookup vars) {
    return adaptParameter(param, getTypeInfoForJavaImpl(javaType.className()), soyType, vars);
  }

  /**
   * Adapts a soy value to a Java value to be passed to the Java implementation of the extern.
   *
   * <p>Extern implementations expect Java null for Soy null values.
   */
  private static Expression adaptParameter(
      Expression param, TypeInfo javaTypeInfo, SoyType soyType, TemplateParameterLookup vars) {
    Type javaType = javaTypeInfo.type();
    SoyExpression actualParam =
        param instanceof SoyExpression
            ? (SoyExpression) param
            : SoyExpression.forRuntimeType(getRuntimeType(soyType), param);
    boolean soyTypeBoxed = actualParam.soyRuntimeType().isBoxed();
    boolean isObject = javaType.equals(BytecodeUtils.OBJECT.type());

    // If expecting a bland 'SoyValue', just box the expr.
    // If we expect a specific SoyValue subclass, then box + cast.
    if (javaType.equals(BytecodeUtils.SOY_VALUE_TYPE)
        || javaType.equals(BytecodeUtils.PRIMITIVE_DATA_TYPE)
        || (javaTypeInfo.classOptional().isPresent()
            && SoyValue.class.isAssignableFrom(javaTypeInfo.classOptional().get()))) {
      // NullData -> null, UndefinedData -> UndefinedData
      return actualParam.boxWithSoyNullAsJavaNull().checkedCast(javaType);
    }

    // Otherwise, we're an unboxed type (non-SoyValue).

    // int needs special-casing for overflow, and because we can't unbox as int
    if (javaType.equals(Type.INT_TYPE)) {
      return JbcSrcExternRuntime.LONG_TO_INT.invoke(actualParam.coerceToLong());
    } else if (javaType.equals(BytecodeUtils.BOXED_INTEGER_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_INTEGER.invoke(actualParam);
      }
      return MethodRefs.BOX_INTEGER.invoke(
          JbcSrcExternRuntime.LONG_TO_INT.invoke(actualParam.coerceToLong()));
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
    } else if (javaType.equals(Type.BOOLEAN_TYPE)) {
      return actualParam.unboxAsBoolean();
    } else if (javaType.equals(BytecodeUtils.BOXED_BOOLEAN_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_BOOLEAN.invoke(actualParam);
      }
      return MethodRefs.BOX_BOOLEAN.invoke(actualParam.unboxAsBoolean());
    } else if (javaType.equals(Type.LONG_TYPE)) {
      return actualParam.coerceToLong();
    } else if (javaType.equals(BytecodeUtils.BOXED_LONG_TYPE)) {
      if (soyTypeBoxed) {
        return JbcSrcExternRuntime.SOY_VALUE_TO_BOXED_LONG.invoke(actualParam);
      }
      return MethodRefs.BOX_LONG.invoke(actualParam.coerceToLong());
    } else if (javaType.equals(BytecodeUtils.STRING_TYPE)) {
      return actualParam.unboxAsStringOrJavaNull();
    } else if (javaType.equals(BIG_INTEGER_TYPE)) {
      return JbcSrcExternRuntime.CONVERT_SOY_VALUE_TO_BIG_INTEGER.invoke(actualParam.box());
    }

    SoyType nonNullableSoyType = SoyTypes.excludeNullish(soyType.getEffectiveType());

    // For protos, we need to unbox as Message & then cast.
    if (nonNullableSoyType instanceof MessageType) {
      return actualParam.unboxAsMessageOrJavaNull(MESSAGE_TYPE);
    } else if (nonNullableSoyType instanceof SoyProtoType) {
      return actualParam.unboxAsMessageOrJavaNull(
          ProtoUtils.messageRuntimeType(((SoyProtoType) nonNullableSoyType).getDescriptor())
              .type());
    }
    // For protocol enums, we need to call forNumber on the type w/ the param (as casted to an int).
    // This is because Soy internally stores enums as ints. We know this is safe because we
    // already validated that the enum type matches the signature.
    if (nonNullableSoyType instanceof SoyProtoEnumType) {
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

    if (!isObject
        && BytecodeUtils.isDefinitelyAssignableFrom(javaType, BytecodeUtils.IMMUTABLE_LIST_TYPE)) {
      SoyType elmType = SoyTypes.getIterableElementType(null, nonNullableSoyType);
      Expression unboxedList =
          actualParam.isBoxed() ? actualParam.unboxAsListOrJavaNull() : actualParam;
      switch (elmType.getKind()) {
        case INT:
          return JbcSrcExternRuntime.LIST_UNBOX_INTS.invoke(unboxedList);
        case FLOAT:
        case NUMBER:
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
          if (SoyTypes.INT_OR_FLOAT.isEffectivelyEqual(elmType)) {
            return JbcSrcExternRuntime.LIST_UNBOX_NUMBERS.invoke(unboxedList);
          }
        // fall through
        default:
          Expression list =
              actualParam.isBoxed()
                  ? JbcSrcExternRuntime.UNBOX_OBJECT.invoke(actualParam)
                  : JbcSrcExternRuntime.DEEP_UNBOX_LIST.invoke(actualParam);
          if (javaType.equals(BytecodeUtils.IMMUTABLE_LIST_TYPE)) {
            list = IMMUTABLE_LIST_COPY_OF_ITERABLE.invoke(list.checkedCast(ITERABLE_TYPE));
          } else {
            list = list.checkedCast(javaType);
          }
          return list;
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
    } else if (isObject) {
      if (BytecodeUtils.isPrimitive(actualParam.soyRuntimeType().runtimeType())) {
        return BytecodeUtils.boxJavaPrimitive(actualParam);
      }
      return actualParam.isBoxed()
          ? JbcSrcExternRuntime.UNBOX_OBJECT.invoke(actualParam)
          // Unbox nested data structures in unboxed list/set.
          : JbcSrcExternRuntime.UNBOX_OBJECT_CONTENTS.invoke(actualParam);
    }

    if (SoyTypes.isKindOrUnionOfKind(soyType, Kind.FUNCTION)) {
      return actualParam
          .checkedCast(FUNCTION_VALUE_TYPE)
          .invoke(MethodRefs.FUNCTION_WITH_RENDER_CONTEXT, vars.getRenderContext())
          .invoke(MethodRefs.FUNCTION_AS_INSTANCE, constant(javaType))
          .checkedCast(javaType);
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

  private SoyExpression adaptReturnExpression(SoyExpression raw, SoyRuntimeType type) {
    return raw.coerceTo(type.runtimeType());
  }

  /**
   * Adapts the return value of the extern Java implementation to the expected Soy value.
   *
   * <p>In some cases (e.g. List) we happen to tolerate the extern returning null.
   */
  static Expression adaptReturnType(Type returnType, SoyType soyReturnType, Expression externCall) {
    boolean nullish = SoyTypes.isNullish(soyReturnType);
    Type externType = externCall.resultType();
    Type exprType = getRuntimeType(soyReturnType).runtimeType();

    if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.FUTURE_TYPE, returnType)) {
      return JbcSrcExternRuntime.CONVERT_OBJECT_TO_SOY_VALUE_PROVIDER.invoke(externCall);
    }

    if (!nullish && externType.equals(BytecodeUtils.BOXED_INTEGER_TYPE)) {
      return adaptIfNumeric(JbcSrcExternRuntime.UNBOX_INTEGER.invoke(externCall), exprType);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_LONG_TYPE)) {
      return adaptIfNumeric(JbcSrcExternRuntime.UNBOX_LONG.invoke(externCall), exprType);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_DOUBLE_TYPE)) {
      return adaptIfNumeric(JbcSrcExternRuntime.UNBOX_DOUBLE.invoke(externCall), exprType);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_FLOAT_TYPE)) {
      return adaptIfNumeric(JbcSrcExternRuntime.UNBOX_FLOAT.invoke(externCall), exprType);
    } else if (!nullish && externType.equals(BytecodeUtils.BOXED_BOOLEAN_TYPE)) {
      return JbcSrcExternRuntime.UNBOX_BOOLEAN.invoke(externCall);
    } else if (!nullish && externType.equals(BytecodeUtils.BIG_INTEGER_TYPE)) {
      return JbcSrcExternRuntime.GBIGINT_FOR_VALUE.invoke(externCall);
    } else if (externType.equals(BytecodeUtils.OBJECT.type())
        || externType.equals(BytecodeUtils.NUMBER_TYPE)) {
      checkState(!MapType.ANY_MAP.isAssignableFromStrict(soyReturnType));
      return JbcSrcExternRuntime.CONVERT_OBJECT_TO_SOY_VALUE.invoke(externCall);
    } else if (BytecodeUtils.isDefinitelyAssignableFrom(BytecodeUtils.MAP_TYPE, externType)) {
      // When Soy sees a map, it defaults to thinking it's a legacy_object_map, which only allow
      // string keys. We know that's not the case here (because the Soy return type of the extern
      // is "map") so mark this as a "map" and not a "legacy_object_map".
      if (MapType.ANY_MAP.isAssignableFromStrict(soyReturnType)) {
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
    } else if (SoyProtoEnumType.ANY_ENUM.isAssignableFromStrict(soyReturnType)) {
      return adaptIfNumeric(MethodRefs.PROTOCOL_ENUM_GET_NUMBER.invoke(externCall), exprType);
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
    return adaptIfNumeric(externCall, exprType);
  }

  private static Expression adaptIfNumeric(Expression base, Type exprType) {
    if (isNumericPrimitive(base.resultType()) && isNumericPrimitive(exprType)) {
      return numericConversion(base, exprType);
    }
    return base;
  }
}
