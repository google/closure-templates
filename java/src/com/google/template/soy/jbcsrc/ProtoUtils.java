/*
 * Copyright 2016 Google Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.internal.proto.JavaQualifiedNames.getFieldName;
import static com.google.template.soy.internal.proto.JavaQualifiedNames.underscoresToCamelCase;
import static com.google.template.soy.internal.proto.ProtoUtils.getJsType;
import static com.google.template.soy.internal.proto.ProtoUtils.hasJsType;
import static com.google.template.soy.internal.proto.ProtoUtils.isUnsigned;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.MAP_ENTRY_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isPrimitive;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.numericConversion;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.unboxUnchecked;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FieldOptions.JSType;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.Descriptors.FileDescriptor.Syntax;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Extension;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableBuilder;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.protobuf.GeneratedMessage.GeneratedExtension;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.ProtoFieldInterpreter;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.internal.proto.FieldVisitor;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.restricted.BytecodeProducer;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyRuntimeType;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnionType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Utilities for dealing with protocol buffers.
 *
 * <p>TODO(user): Consider moving this back into ExpressionCompiler.
 */
final class ProtoUtils {

  private static final Type BYTE_STRING_TYPE = Type.getType(ByteString.class);
  private static final Type EXTENSION_TYPE = Type.getType(GeneratedExtension.class);

  private static final Type[] ONE_INT_ARG = {Type.INT_TYPE};

  private static final MethodRef BASE_ENCODING_BASE_64 =
      MethodRef.create(BaseEncoding.class, "base64").asNonNullable().asCheap();

  private static final MethodRef BASE_ENCODING_DECODE =
      MethodRef.create(BaseEncoding.class, "decode", CharSequence.class).asNonNullable().asCheap();

  private static final MethodRef BASE_ENCODING_ENCODE =
      MethodRef.create(BaseEncoding.class, "encode", byte[].class).asNonNullable().asCheap();

  private static final MethodRef BYTE_STRING_COPY_FROM =
      MethodRef.create(ByteString.class, "copyFrom", byte[].class).asNonNullable();

  private static final MethodRef BYTE_STRING_TO_BYTE_ARRAY =
      MethodRef.create(ByteString.class, "toByteArray").asNonNullable();

  private static final MethodRef EXTENDABLE_BUILDER_ADD_EXTENSION =
      MethodRef.create(ExtendableBuilder.class, "addExtension", ExtensionLite.class, Object.class)
          .asNonNullable();

  private static final MethodRef EXTENDABLE_BUILDER_SET_EXTENSION =
      MethodRef.create(ExtendableBuilder.class, "setExtension", ExtensionLite.class, Object.class)
          .asNonNullable();

  private static final MethodRef EXTENDABLE_MESSAGE_GET_EXTENSION =
      MethodRef.create(ExtendableMessage.class, "getExtension", ExtensionLite.class)
          .asNonNullable()
          .asCheap();

  private static final MethodRef EXTENDABLE_MESSAGE_HAS_EXTENSION =
      MethodRef.create(ExtendableMessage.class, "hasExtension", ExtensionLite.class)
          .asNonNullable()
          .asCheap();

  // We use the full name as the key instead of the descriptor, since descriptors use identity
  // semantics for equality and we may load the descriptors for these protos from multiple sources
  // depending on our configuration.
  private static final ImmutableMap<String, MethodRef> SAFE_PROTO_TO_SANITIZED_CONTENT =
      ImmutableMap.<String, MethodRef>builder()
          .put(
              SafeHtmlProto.getDescriptor().getFullName(),
              createSafeProtoToSanitizedContentFactoryMethod(SafeHtmlProto.class))
          .put(
              SafeScriptProto.getDescriptor().getFullName(),
              createSafeProtoToSanitizedContentFactoryMethod(SafeScriptProto.class))
          .put(
              SafeStyleProto.getDescriptor().getFullName(),
              createSafeProtoToSanitizedContentFactoryMethod(SafeStyleProto.class))
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              createSafeProtoToSanitizedContentFactoryMethod(SafeStyleSheetProto.class))
          .put(
              SafeUrlProto.getDescriptor().getFullName(),
              createSafeProtoToSanitizedContentFactoryMethod(SafeUrlProto.class))
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              createSafeProtoToSanitizedContentFactoryMethod(TrustedResourceUrlProto.class))
          .build();

  private static MethodRef createSafeProtoToSanitizedContentFactoryMethod(Class<?> clazz) {
    return MethodRef.create(SanitizedContents.class, "from" + clazz.getSimpleName(), clazz)
        .asNonNullable();
  }

  private static final ImmutableMap<String, MethodRef> SANITIZED_CONTENT_TO_PROTO =
      ImmutableMap.<String, MethodRef>builder()
          .put(
              SafeHtmlProto.getDescriptor().getFullName(),
              MethodRef.create(SanitizedContent.class, "toSafeHtmlProto"))
          .put(
              SafeScriptProto.getDescriptor().getFullName(),
              MethodRef.create(SanitizedContent.class, "toSafeScriptProto"))
          .put(
              SafeStyleProto.getDescriptor().getFullName(),
              MethodRef.create(SanitizedContent.class, "toSafeStyleProto"))
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              MethodRef.create(SanitizedContent.class, "toSafeStyleSheetProto"))
          .put(
              SafeUrlProto.getDescriptor().getFullName(),
              MethodRef.create(SanitizedContent.class, "toSafeUrlProto"))
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              MethodRef.create(SanitizedContent.class, "toTrustedResourceUrlProto"))
          .build();

  private static final RepeatedFieldInterpreter REPEATED_FIELD_INTERPRETER =
      new RepeatedFieldInterpreter();

  /**
   * Returns a {@link SoyExpression} for accessing a field of a proto.
   *
   * @param protoType The type of the proto being accessed
   * @param baseExpr The proto being accessed
   * @param node The field access operation
   */
  static SoyExpression accessField(
      SoyProtoType protoType, SoyExpression baseExpr, String fieldName, SoyType fieldType) {
    return new AccessorGenerator(
            protoType, baseExpr, fieldName, fieldType, /* useBrokenSemantics= */ true)
        .generate();
  }

  static SoyExpression hasserField(SoyExpression baseExpr, String fieldName) {
    SoyProtoType protoType = (SoyProtoType) baseExpr.soyType();
    return new HasserGenerator(protoType, baseExpr, fieldName).generate();
  }

  /**
   * Returns a {@link SoyExpression} for accessing an extension field of a proto using the {@code
   * getExtension} method.
   *
   * @param baseExpr The proto being accessed.
   * @param node The method operation.
   */
  static SoyExpression accessExtensionField(
      SoyExpression baseExpr, MethodCallNode node, String fieldName, boolean useBrokenSemantics) {
    SoyProtoType protoType = (SoyProtoType) baseExpr.soyType();
    return new AccessorGenerator(protoType, baseExpr, fieldName, node.getType(), useBrokenSemantics)
        .generate();
  }

  static SoyExpression accessProtoUnionField(
      SoyExpression baseExpr, FieldAccessNode node, LocalVariableManager varManager) {
    return new ProtoUnionAccessorGenerator(baseExpr, node, varManager).generate();
  }

  private abstract static class BaseGenerator {

    final SoyRuntimeType unboxedRuntimeType;
    final SoyExpression baseExpr;

    public BaseGenerator(SoyRuntimeType unboxedRuntimeType, SoyExpression baseExpr) {
      this.unboxedRuntimeType = unboxedRuntimeType;
      this.baseExpr = baseExpr;
    }

    SoyExpression getTypedBaseExpression() {
      if (baseExpr.isBoxed()) {
        return SoyExpression.forProto(
            unboxedRuntimeType,
            baseExpr
                .invoke(MethodRef.SOY_PROTO_VALUE_GET_PROTO)
                // this cast is required because getProto() is generic, so it basically returns
                // 'Message'
                .checkedCast(unboxedRuntimeType.runtimeType()));
      } else if (baseExpr.soyRuntimeType().equals(unboxedRuntimeType)) {
        return baseExpr;
      } else {
        throw new AssertionError("should be impossible");
      }
    }
  }

  /**
   * A simple class to encapsulate all the parameters shared between our different accessor
   * generation strategies
   */
  private static final class AccessorGenerator extends BaseGenerator {
    final SoyType fieldType;
    final String fieldName;
    final FieldDescriptor descriptor;
    final boolean shouldCheckForFieldPresence;

    AccessorGenerator(
        SoyProtoType protoType,
        SoyExpression baseExpr,
        String fieldName,
        SoyType fieldType,
        boolean useBrokenSemantics) {
      super(SoyRuntimeType.getUnboxedType(protoType).get(), baseExpr);
      this.fieldName = fieldName;
      this.fieldType = fieldType;
      this.descriptor = protoType.getFieldDescriptor(fieldName);
      this.shouldCheckForFieldPresence =
          useBrokenSemantics
              && protoType.shouldCheckFieldPresenceToEmulateJspbNullability(fieldName);
    }

    SoyExpression generate() {
      SoyExpression typedBaseExpr = getTypedBaseExpression();
      if (descriptor.isExtension()) {
        return handleExtension(typedBaseExpr);
      } else {
        return handleNormalField(typedBaseExpr);
      }
    }

    private SoyExpression handleNormalField(final SoyExpression typedBaseExpr) {
      // TODO(lukes): consider adding a cache for the method lookups.
      final MethodRef getMethodRef = getGetterMethod(descriptor);

      if (descriptor.isMapField()) {
        return handleMapField(typedBaseExpr, getMethodRef);
      } else if (descriptor.isRepeated()) {
        return SoyExpression.forBoxedList(
            (ListType) fieldType,
            MethodRef.LAZY_PROTO_TO_SOY_VALUE_LIST_FOR_LIST.invoke(
                typedBaseExpr.invoke(getMethodRef),
                FieldVisitor.visitField(descriptor, REPEATED_FIELD_INTERPRETER)));
      }

      // To implement jspb semantics for proto nullability we need to call has<Field>() methods for
      // a subset of fields as specified in SoyProtoType. Though, we should probably actually be
      // testing against jspb semantics.  The best way forward is probably to first invest in
      // support for protos in our integration tests.
      if (!shouldCheckForFieldPresence) {
        // Simple case, just call .get and interpret the result
        return interpretField(typedBaseExpr.invoke(getMethodRef));
      } else {
        final Label hasFieldLabel = new Label();
        final BytecodeProducer hasCheck;

        // if oneof, check the value of getFooCase() enum
        OneofDescriptor containingOneof = descriptor.getContainingOneof();
        if (containingOneof != null) {
          final MethodRef getCaseRef = getOneOfCaseMethod(containingOneof);
          final Expression fieldNumber = constant(descriptor.getNumber());
          // this basically just calls getFooCase().getNumber() == field_number
          hasCheck =
              new BytecodeProducer() {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  getCaseRef.invokeUnchecked(adapter);
                  adapter.visitMethodInsn(
                      Opcodes.INVOKEVIRTUAL,
                      getCaseRef.returnType().getInternalName(),
                      "getNumber",
                      "()I",
                      false /* not an interface */);
                  fieldNumber.gen(adapter);
                  adapter.ifCmp(Type.INT_TYPE, GeneratorAdapter.EQ, hasFieldLabel);
                }
              };
        } else {
          // otherwise just call the has* method
          final MethodRef hasMethodRef = getHasserMethod(descriptor);
          hasCheck =
              new BytecodeProducer() {
                @Override
                protected void doGen(CodeBuilder adapter) {
                  hasMethodRef.invokeUnchecked(adapter);
                  adapter.ifZCmp(Opcodes.IFNE, hasFieldLabel);
                }
              };
        }

        // TODO(lukes): this violates the expression contract since we jump to a label outside the
        // scope of the expression
        final Label endLabel = new Label();
        // If the field doesn't have an explicit default then we need to call .has<Field> and return
        // null if it isn't present.
        SoyExpression interpretedField =
            interpretField(
                new Expression(
                    getMethodRef.returnType(),
                    getMethodRef.features().minus(Feature.NON_NULLABLE)) {
                  @Override
                  protected void doGen(CodeBuilder adapter) {
                    typedBaseExpr.gen(adapter);

                    // Call .has<Field>().
                    adapter.dup();
                    hasCheck.gen(adapter);

                    // The field is missing, substitute null.
                    adapter.pop();
                    adapter.visitInsn(Opcodes.ACONST_NULL);
                    adapter.goTo(endLabel);

                    // The field exists, call .get<Field>().
                    adapter.mark(hasFieldLabel);
                    getMethodRef.invokeUnchecked(adapter);
                  }
                });
        if (isPrimitive(interpretedField.resultType())) {
          interpretedField = interpretedField.box();
        }

        // TODO(b/22389927): This is another place where the soy type system lies to us, so make
        // sure to mark the type as nullable.
        return interpretedField.labelEnd(endLabel).asNullable();
      }
    }

    private SoyExpression handleMapField(SoyExpression typedBaseExpr, MethodRef getMethodRef) {
      List<FieldDescriptor> mapFields = descriptor.getMessageType().getFields();
      FieldDescriptor keyDescriptor = mapFields.get(0);
      FieldDescriptor valueDescriptor = mapFields.get(1);
      return SoyExpression.forMap(
          (MapType) fieldType,
          MethodRef.LAZY_PROTO_TO_SOY_VALUE_MAP_FOR_MAP.invoke(
              typedBaseExpr.invoke(getMethodRef),
              FieldVisitor.visitField(keyDescriptor, REPEATED_FIELD_INTERPRETER),
              FieldVisitor.visitField(valueDescriptor, REPEATED_FIELD_INTERPRETER),
              BytecodeUtils.constant(getKeyType(keyDescriptor))));
    }

    private static final Type getKeyType(FieldDescriptor keyDescriptor) {
      switch (keyDescriptor.getJavaType()) {
        case INT:
          return BytecodeUtils.INTEGER_TYPE;
        case LONG:
          return BytecodeUtils.BOXED_LONG_TYPE;
        case BOOLEAN:
          return BytecodeUtils.BOXED_BOOLEAN_TYPE;
        case STRING:
          return STRING_TYPE;
        default:
          throw new AssertionError("Invalid proto map key type: " + keyDescriptor);
      }
    }

    private SoyExpression interpretField(Expression field) {
      // Depending on types we may need to do a trivial conversion
      // (e.g. int->long, float->double, enum->int)
      switch (descriptor.getJavaType()) {
        case FLOAT:
          return SoyExpression.forFloat(numericConversion(field, Type.DOUBLE_TYPE));
        case DOUBLE:
          return SoyExpression.forFloat(field);
        case ENUM:
          if (isProto3EnumField(descriptor)) {
            // it already is an integer, cast to long
            return SoyExpression.forInt(numericConversion(field, Type.LONG_TYPE));
          }
          // otherwise it is proto2 and we need to extract the number.
          return SoyExpression.forInt(
              numericConversion(field.invoke(MethodRef.PROTOCOL_ENUM_GET_NUMBER), Type.LONG_TYPE));
        case INT:
          // Since soy 'int's are java longs, we can actually fully represent an unsigned 32bit
          // integer.
          if (isUnsigned(descriptor)) {
            return SoyExpression.forInt(MethodRef.UNSIGNED_INTS_TO_LONG.invoke(field));
          } else {
            return SoyExpression.forInt(numericConversion(field, Type.LONG_TYPE));
          }
        case LONG:
          if (shouldConvertBetweenStringAndLong(descriptor)) {
            if (isUnsigned(descriptor)) {
              return SoyExpression.forString(MethodRef.UNSIGNED_LONGS_TO_STRING.invoke(field));
            } else {
              return SoyExpression.forString(MethodRef.LONG_TO_STRING.invoke(field));
            }
          }
          // If the value is large and the field is unsigned we might corrupt it here (e.g. it will
          // appear negative).  We don't have a solution for this currently.  In theory we could add
          // the concept of unsigned integers to soy and then implement arithmetic on them... but
          // that is insane!
          return SoyExpression.forInt(field);
        case BOOLEAN:
          return SoyExpression.forBool(field);
        case STRING:
          return SoyExpression.forString(field);
        case MESSAGE:
          return messageToSoyExpression(field);
        case BYTE_STRING:
          return byteStringToBase64String(field);
      }
      throw new AssertionError("unsupported field type: " + descriptor);
    }

    private SoyExpression handleExtension(final SoyExpression typedBaseExpr) {
      // extensions are a little weird since we need to look up the extension object and then call
      // message.getExtension(Extension) and then cast and (maybe) unbox the result.
      // The reason we need to cast is because .getExtension is a generic api and that is just how
      // stupid java generics work.
      FieldRef extensionField = getExtensionField(descriptor);
      final Expression extensionFieldAccessor = extensionField.accessor();

      if (descriptor.isRepeated()) {
        return SoyExpression.forBoxedList(
            (ListType) fieldType,
            MethodRef.GET_EXTENSION_LIST.invoke(
                typedBaseExpr,
                extensionFieldAccessor,
                FieldVisitor.visitField(descriptor, REPEATED_FIELD_INTERPRETER)));
      }

      if (!descriptor.hasDefaultValue() && shouldCheckForFieldPresence) {
        final Label endLabel = new Label();
        SoyExpression interpretedField =
            interpretExtensionField(
                new Expression(
                    EXTENDABLE_MESSAGE_GET_EXTENSION.returnType(),
                    EXTENDABLE_MESSAGE_GET_EXTENSION.features().minus(Feature.NON_NULLABLE)) {
                  @Override
                  protected void doGen(CodeBuilder adapter) {
                    typedBaseExpr.gen(adapter);

                    // call hasExtension()
                    adapter.dup();
                    extensionFieldAccessor.gen(adapter);
                    EXTENDABLE_MESSAGE_HAS_EXTENSION.invokeUnchecked(adapter);
                    Label hasFieldLabel = new Label();
                    adapter.ifZCmp(Opcodes.IFNE, hasFieldLabel);

                    // The field is missing, substitute null.
                    adapter.pop();
                    adapter.visitInsn(Opcodes.ACONST_NULL);
                    adapter.goTo(endLabel);

                    // The field exists, call getExtension()
                    adapter.mark(hasFieldLabel);
                    extensionFieldAccessor.gen(adapter);
                    EXTENDABLE_MESSAGE_GET_EXTENSION.invokeUnchecked(adapter);
                  }
                });
        // Box primitives to allow it to be compatible with null.
        if (isPrimitive(interpretedField.resultType())) {
          interpretedField = interpretedField.box();
        }
        // TODO(b/22389927): This is another place where the soy type system lies to us, so make
        // sure to mark the type as nullable.
        // TODO(lukes): this violates the expression contract since we jump to a label outside the
        // scope of the expression
        return interpretedField.labelEnd(endLabel).asNullable();
      } else {
        // This branch handles extension with a default value, which are pretty rare, and non-
        // broken semantics, where we can delegate to the Java API without checking presence.
        return interpretExtensionField(
            typedBaseExpr.invoke(EXTENDABLE_MESSAGE_GET_EXTENSION, extensionFieldAccessor));
      }
    }

    private SoyExpression interpretExtensionField(Expression field) {
      switch (descriptor.getJavaType()) {
        case FLOAT:
        case DOUBLE:
          return SoyExpression.forFloat(
              field.checkedCast(Number.class).invoke(MethodRef.NUMBER_DOUBLE_VALUE));
        case ENUM:
          return SoyExpression.forInt(
              numericConversion(
                  field
                      .checkedCast(ProtocolMessageEnum.class)
                      .invoke(MethodRef.PROTOCOL_ENUM_GET_NUMBER),
                  Type.LONG_TYPE));
        case INT:
          if (isUnsigned(descriptor)) {
            return SoyExpression.forInt(
                MethodRef.UNSIGNED_INTS_TO_LONG.invoke(
                    field.checkedCast(Integer.class).invoke(MethodRef.NUMBER_INT_VALUE)));
          } else {
            return SoyExpression.forInt(
                field.checkedCast(Integer.class).invoke(MethodRef.NUMBER_LONG_VALUE));
          }
        case LONG:
          if (shouldConvertBetweenStringAndLong(descriptor)) {
            if (isUnsigned(descriptor)) {
              return SoyExpression.forString(
                  MethodRef.UNSIGNED_LONGS_TO_STRING.invoke(
                      field.checkedCast(Long.class).invoke(MethodRef.NUMBER_LONG_VALUE)));
            } else {
              return SoyExpression.forString(field.invoke(MethodRef.OBJECT_TO_STRING));
            }
          }
          return SoyExpression.forInt(
              field.checkedCast(Number.class).invoke(MethodRef.NUMBER_LONG_VALUE));
        case BOOLEAN:
          return SoyExpression.forBool(
              field.checkedCast(Boolean.class).invoke(MethodRef.BOOLEAN_VALUE));
        case STRING:
          return SoyExpression.forString(field.checkedCast(String.class));
        case MESSAGE:
          Type javaType = SoyRuntimeType.protoType(descriptor.getMessageType());
          return messageToSoyExpression(field.checkedCast(javaType));
        case BYTE_STRING:
          // Current tofu support for ByteString is to base64 encode it.
          return byteStringToBase64String(field.checkedCast(ByteString.class));
      }
      throw new AssertionError("unsupported field type: " + descriptor);
    }

    private SoyExpression byteStringToBase64String(Expression byteString) {
      byteString.checkAssignableTo(BYTE_STRING_TYPE);
      final Expression byteArray = byteString.invoke(BYTE_STRING_TO_BYTE_ARRAY);
      return SoyExpression.forString(
          new Expression(STRING_TYPE, Feature.NON_NULLABLE) {
            @Override
            protected void doGen(CodeBuilder adapter) {
              byteArray.gen(adapter);
              BASE_ENCODING_BASE_64.invokeUnchecked(adapter);
              // swap the two top items of the stack.
              // This ensures that the base expression is gen'd at a stack depth of zero, which is
              // important if it contains a conditional logic that branches out of the byteArray
              // Expression
              // TODO(lukes): fix this by changing the null checking logic in
              // handle{Extension|Normal}Field.
              adapter.swap();
              BASE_ENCODING_ENCODE.invokeUnchecked(adapter);
            }
          });
    }

    private SoyExpression messageToSoyExpression(Expression field) {
      if (fieldType.getKind() == SoyType.Kind.PROTO) {
        SoyProtoType fieldProtoType = (SoyProtoType) fieldType;
        SoyRuntimeType protoRuntimeType = SoyRuntimeType.getUnboxedType(fieldProtoType).get();
        return SoyExpression.forProto(protoRuntimeType, field);
      } else {
        // All other are special sanitized types
        Descriptor messageType = descriptor.getMessageType();
        MethodRef fromProtoMethod = SAFE_PROTO_TO_SANITIZED_CONTENT.get(messageType.getFullName());
        return SoyExpression.forSoyValue(fieldType, fromProtoMethod.invoke(field));
      }
    }
  }

  private static final class ProtoUnionAccessorGenerator {

    private final SoyExpression baseExpr;
    private final FieldAccessNode node;
    private final LocalVariableManager varManager;

    ProtoUnionAccessorGenerator(
        SoyExpression baseExpr, FieldAccessNode node, LocalVariableManager varManager) {
      checkArgument(baseExpr.soyType().getKind() == SoyType.Kind.UNION, baseExpr.soyType());
      this.baseExpr = baseExpr;
      this.node = node;
      this.varManager = varManager;
    }

    private Expression getUnboxedBaseExpression() {
      // This is a proto union type so must be boxed.
      checkState(baseExpr.isBoxed());
      return baseExpr.invoke(MethodRef.SOY_PROTO_VALUE_GET_PROTO);
    }

    SoyExpression generate() {
      // Keep the result unboxed if possible, but the result must be boxed if the Java types of the
      // possible results are different.
      SoyRuntimeType resultType =
          SoyRuntimeType.getUnboxedType(node.getType())
              .orElseGet(() -> SoyRuntimeType.getBoxedType(node.getType()));

      // Store the base value in a local variable so it's easy to repeatedly check the type and call
      // the correct getter method on it.
      LocalVariableManager.Scope scope = varManager.enterScope();
      Expression unboxedBaseExpr = getUnboxedBaseExpression();
      LocalVariable base =
          scope.createTemporary(node.getFieldName() + "__base", unboxedBaseExpr.resultType());
      Statement baseInit = base.store(unboxedBaseExpr, base.start());
      Statement scopeExit = scope.exitScope();

      boolean foundBoxed = false;
      Set<SoyType> members = ((UnionType) baseExpr.soyType()).getMembers();
      // Each key is a possible type of the (union) base expression. The value is the code to access
      // the field for the key's type.
      Map<SoyRuntimeType, SoyExpression> getters = new LinkedHashMap<>();
      for (SoyType member : members) {
        // The caller should check that all member types are protos, so this cast is safe.
        SoyProtoType protoType = (SoyProtoType) member;
        SoyRuntimeType unboxed = SoyRuntimeType.getUnboxedType(protoType).get();
        SoyExpression fieldAccess =
            accessField(
                protoType,
                SoyExpression.forProto(unboxed, base.checkedCast(unboxed.runtimeType())),
                node.getFieldName(),
                protoType.getFieldType(node.getFieldName()));
        if (fieldAccess.isBoxed()) {
          foundBoxed = true;
        }
        getters.put(unboxed, fieldAccess);
      }

      // Each accesses decides whether or not to box, but if the result type is boxed or at least
      // one of the accesses is boxed, then we need to box all of them to have a consistent result
      // type.
      if (resultType.isBoxed() || foundBoxed) {
        resultType = resultType.box();
        getters.replaceAll((key, value) -> value.box());
      }

      // Generates code to check the type of the base expression and call the correct getter method:
      // if (base instanceof Foo) {
      //   ((Foo) base).getMyField();
      // } else if (base instanceof Bar) {
      //   ((Bar) base).getMyField();
      // } else {
      //   ((Baz) base).getMyField();
      // }
      return SoyExpression.forRuntimeType(
          resultType,
          new Expression(resultType.runtimeType()) {

            @Override
            protected void doGen(CodeBuilder cb) {
              Label end = new Label();
              baseInit.gen(cb);

              for (Iterator<Map.Entry<SoyRuntimeType, SoyExpression>> i =
                      getters.entrySet().iterator();
                  i.hasNext(); ) {
                Map.Entry<SoyRuntimeType, SoyExpression> entry = i.next();
                SoyRuntimeType type = entry.getKey();
                SoyExpression getter = entry.getValue();
                boolean last = !i.hasNext();

                Label next = null;

                // Check the type of the base expression and jump to the next check if it doesn't
                // match. For the last iteration, skip the type check since it can only be the one
                // remaining type. A cast in the getter expression will verify this.
                if (!last) {
                  next = new Label();
                  base.gen(cb);
                  cb.instanceOf(type.runtimeType());
                  cb.ifZCmp(Opcodes.IFEQ, next);
                }

                getter.gen(cb);

                // If we made it here, then the type matched, so skip the remaining checks and go to
                // the end. This also marks the beginning of the next check (which is produced by
                // the next iteration) so it can be skipped to if the type check doesn't match. For
                // the last iteration, we're already at the end so can just fall out.
                if (!last) {
                  cb.goTo(end);
                  cb.mark(next);
                }
              }
              cb.mark(end);
              scopeExit.gen(cb);
            }
          });
    }
  }

  private static final class HasserGenerator extends BaseGenerator {

    final String fieldName;
    final FieldDescriptor descriptor;

    HasserGenerator(SoyProtoType protoType, SoyExpression baseExpr, String fieldName) {
      super(SoyRuntimeType.getUnboxedType(protoType).get(), baseExpr);
      this.fieldName = fieldName;
      this.descriptor = protoType.getFieldDescriptor(fieldName);
    }

    SoyExpression generate() {
      SoyExpression typedBaseExpr = getTypedBaseExpression();

      if (descriptor.isExtension()) {
        throw new AssertionError("extensions don't have hassers: " + descriptor);
      } else if (descriptor.isRepeated()) {
        throw new AssertionError("repeated fields don't have hassers: " + descriptor);
      } else {
        return handleNormalField(typedBaseExpr);
      }
    }

    private SoyExpression handleNormalField(SoyExpression typedBaseExpr) {
      MethodRef hasMethodRef = getHasserMethod(descriptor);
      return SoyExpression.forBool(typedBaseExpr.invoke(hasMethodRef));
    }
  }

  /**
   * Returns a {@link SoyExpression} for initializing a new proto.
   *
   * @param node The proto initialization node
   * @param varManager Local variables manager
   */
  static SoyExpression createProto(
      FunctionNode node,
      Function<ExprNode, SoyExpression> compilerFunction,
      ExpressionDetacher detacher,
      LocalVariableManager varManager) {
    return new ProtoInitGenerator(node, compilerFunction, detacher, varManager).generate();
  }

  private static final class ProtoInitGenerator {
    private final FunctionNode node;
    private final Function<ExprNode, SoyExpression> compilerFunction;
    private final ExpressionDetacher detacher;
    private final LocalVariableManager varManager;

    private final SoyProtoType protoType;
    private final Descriptor descriptor;

    ProtoInitGenerator(
        FunctionNode node,
        Function<ExprNode, SoyExpression> compilerFunction,
        ExpressionDetacher detacher,
        LocalVariableManager varManager) {
      this.node = node;
      this.compilerFunction = compilerFunction;
      this.detacher = detacher;
      this.varManager = varManager;

      this.protoType = (SoyProtoType) node.getType();
      this.descriptor = protoType.getDescriptor();
    }

    private SoyExpression compile(ExprNode expr) {
      return compilerFunction.apply(expr);
    }

    SoyExpression generate() {
      // For cases with no field assignments, return proto.defaultInstance().
      if (node.numChildren() == 0) {
        final Expression defaultInstance = getDefaultInstanceMethod(descriptor).invoke();
        return SoyExpression.forProto(
            SoyRuntimeType.getUnboxedType(protoType).get(), defaultInstance);
      }

      final Expression newBuilderCall = getBuilderMethod(descriptor).invoke();
      final ImmutableList<Statement> setters = getFieldSetters();
      final MethodRef buildCall = getBuildMethod(descriptor);

      Expression expression =
          new Expression(messageRuntimeType(descriptor).type()) {
            @Override
            protected void doGen(CodeBuilder cb) {
              newBuilderCall.gen(cb);

              for (Statement setter : setters) {
                setter.gen(cb);
              }

              // builder is already on the stack from newBuilder() / set<Field>()
              buildCall.invokeUnchecked(cb);
            }
          }.asNonNullable();

      return SoyExpression.forProto(SoyRuntimeType.getUnboxedType(protoType).get(), expression);
    }

    private ImmutableList<Statement> getFieldSetters() {
      ImmutableList.Builder<Statement> setters = ImmutableList.builder();
      for (int i = 0; i < node.numChildren(); i++) {
        FieldDescriptor field = protoType.getFieldDescriptor(node.getParamName(i).identifier());
        ExprNode baseArg = node.getChild(i);

        Statement setter;
        if (field.isRepeated()) {
          setter = handleRepeated(baseArg, field);
        } else {
          if (field.isExtension()) {
            setter = handleExtension(compile(baseArg), field);
          } else {
            setter = handleNormalSetter(compile(baseArg), field);
          }
        }
        setters.add(setter);
      }
      return setters.build();
    }

    private Statement handleMapSetter(
        final SoyExpression keyArg, final SoyExpression valueArg, final FieldDescriptor field) {
      final MethodRef putMethod = getPutMethod(field);
      List<FieldDescriptor> descriptors = field.getMessageType().getFields();
      final FieldDescriptor keyDescriptor = descriptors.get(0);
      final FieldDescriptor valueDescriptor = descriptors.get(1);
      return new Statement() {
        @Override
        protected void doGen(CodeBuilder cb) {
          keyArg.gen(cb);
          unboxAndCoerce(cb, keyArg, keyDescriptor);
          valueArg.gen(cb);
          unboxAndCoerce(cb, valueArg, valueDescriptor);
          putMethod.invokeUnchecked(cb);
        }
      };
    }

    /**
     * Returns a Statement that handles a single proto builder setFoo() call.
     *
     * <p>The Statement assumes that just before .gen(), there is an instance of the proto builder
     * at the top of the stack. After .gen() it is guaranteed to leave an instance of the builder at
     * the top of the stack, without changing stack heights.
     */
    private Statement handleNormalSetter(final SoyExpression baseArg, final FieldDescriptor field) {
      final MethodRef setterMethod = getSetOrAddMethod(field);
      final boolean isNullable = !baseArg.isNonNullable();
      return new Statement() {
        @Override
        protected void doGen(CodeBuilder cb) {
          baseArg.gen(cb);

          Label argIsNull = null;
          Label end = null;
          if (isNullable) {
            argIsNull = new Label();
            end = new Label();
            // perform null check
            cb.dup();
            cb.ifNull(argIsNull);
          }

          // arg is not null; unbox, coerce, set<Field>().

          unboxAndCoerce(cb, baseArg, field);
          setterMethod.invokeUnchecked(cb);
          if (isNullable) {
            cb.goTo(end);

            // arg is null; pop it off stack.
            cb.mark(argIsNull);
            cb.pop();

            cb.mark(end);
          }
        }
      };
    }

    private Statement handleMapSetterNotNull(final SoyExpression mapArg, FieldDescriptor field) {
      checkArgument(mapArg.isNonNullable());
      // Wait until all map values can be resolved. Since we don't box/unbox maps, directly call
      // mapArg.asJavaMap() that converts SoyMapImpl to a Map<String, SoyValueProvider>.
      // TODO(lukes): handle map literals specially
      Expression resolved =
          detacher.resolveSoyValueProviderMap(mapArg.invoke(MethodRef.SOY_MAP_IMPL_AS_JAVA_MAP));

      // Enter new scope
      LocalVariableManager.Scope scope = varManager.enterScope();

      // map.entrySet().iterator()
      Expression getMapIterator =
          resolved.invoke(MethodRef.MAP_ENTRY_SET).invoke(MethodRef.GET_ITERATOR);
      final LocalVariable iter =
          scope.createTemporary(field.getName() + "__iter", getMapIterator.resultType());
      Statement loopInitialization = iter.store(getMapIterator, iter.start());
      // (Map.Entry) iter.next()
      Expression iterNext = iter.invoke(MethodRef.ITERATOR_NEXT).checkedCast(MAP_ENTRY_TYPE);
      final LocalVariable mapEntry =
          scope.createTemporary(field.getName() + "__mapEntry", iterNext.resultType());
      Statement initMapEntry = mapEntry.store(iterNext, mapEntry.start());
      // exitScope must be called after creating all the variables
      final Statement scopeExit = scope.exitScope();

      // Get type info of the map key/value
      MapType mapType = (MapType) mapArg.soyType();
      SoyType keyType = mapType.getKeyType();
      SoyRuntimeType keyRuntimeType = SoyRuntimeType.getBoxedType(keyType);
      SoyType valueType = mapType.getValueType();
      SoyRuntimeType valueRuntimeType = SoyRuntimeType.getBoxedType(valueType);

      SoyExpression mapKey =
          SoyExpression.forSoyValue(
              keyType,
              mapEntry
                  .invoke(MethodRef.MAP_GET_KEY)
                  .checkedCast(keyRuntimeType.runtimeType())
                  // In ResolveExpressionTypesPass we already enforce that key is not nullable.  By
                  // asserting that it isn't we just get an NPE when we are wrong, which is what we
                  // want.
                  .asNonNullable());

      // Convert the soy value to java type

      // ResolveExpressionTypesPass already enforces that the value is not nullable. If the value is
      // null, it reports a type mismatch error.
      SoyExpression mapValue =
          SoyExpression.forSoyValue(
                  valueType,
                  mapEntry
                      .invoke(MethodRef.MAP_GET_VALUE)
                      .checkedCast(SOY_VALUE_PROVIDER_TYPE)
                      // resolve() is safe since we called ExpressionDetacher at the very beginning
                      // of this method
                      // (SomeType) ((SoyValueProvider) mapEntry.getValue()).resolve()
                      .invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE)
                      .checkedCast(valueRuntimeType.runtimeType()))
              .asNonNullable();

      // iter.hasNext()
      final Expression iterHasNext = iter.invoke(MethodRef.ITERATOR_HAS_NEXT);

      // Invokes the putFooFieldMap method in proto message builder
      final Statement putOne = handleMapSetter(mapKey, mapValue, field);

      // Put all expressions together into the while loop
      return new Statement() {
        @Override
        protected void doGen(CodeBuilder cb) {
          loopInitialization.gen(cb);

          // Loop start
          Label loopStart = cb.mark();
          // If hasNext is false, jumps to the end
          iterHasNext.gen(cb);
          Label end = new Label();
          cb.ifZCmp(Opcodes.IFEQ, end);

          // Loop body: load the current map entry and put it into proto
          initMapEntry.gen(cb);
          putOne.gen(cb);

          // Go back to loop start
          cb.goTo(loopStart);

          // Return
          cb.mark(end);
          scopeExit.gen(cb);
        }
      };
    }

    private Statement handleRepeated(ExprNode argNode, FieldDescriptor field) {
      if (argNode.getKind() == ExprNode.Kind.LIST_LITERAL_NODE) {
        checkState(!field.isMapField());
        List<Statement> additions = new ArrayList<>();
        ListLiteralNode list = (ListLiteralNode) argNode;
        for (ExprNode element : list.getChildren()) {
          // it is an error to assign a null list element, so just assert non-null so that it will
          // fail with an NPE if it happens to be null
          SoyExpression expression = compile(element).asNonNullable();
          additions.add(
              field.isExtension()
                  ? handleExtension(expression, field)
                  : handleNormalSetter(expression, field));
        }
        return Statement.concat(additions);
      }
      if (argNode.getKind() == ExprNode.Kind.MAP_LITERAL_NODE) {
        checkState(field.isMapField());
        List<Statement> puts = new ArrayList<>();
        MapLiteralNode map = (MapLiteralNode) argNode;
        for (int i = 0; i < map.numChildren(); i += 2) {
          SoyExpression key = compile(map.getChild(i));
          SoyExpression value = compile(map.getChild(i + 1));
          puts.add(handleMapSetter(key, value, field));
        }
        return Statement.concat(puts);
      }

      final SoyExpression baseArg = compile(argNode);
      // If the list arg is definitely an empty list/map, do nothing
      if (baseArg.soyType().equals(ListType.EMPTY_LIST)
          || baseArg.soyType().equals(MapType.EMPTY_MAP)) {
        return Statement.NULL_STATEMENT;
      }
      if (baseArg.isNonNullable()) {
        return field.isMapField()
            ? handleMapSetterNotNull(baseArg, field)
            : handleRepeatedNotNull(baseArg, field);
      }

      final Label isNonNull = new Label();
      final Label end = new Label();

      // perform null check
      SoyExpression nonNull =
          baseArg
              .withSource(
                  new Expression(baseArg.resultType(), baseArg.features()) {
                    @Override
                    protected void doGen(CodeBuilder cb) {
                      baseArg.gen(cb);

                      cb.dup();
                      cb.ifNonNull(isNonNull);

                      cb.pop(); // pop null off list, skip to end
                      // TODO(user): This violates Expression contract, as it jumps out of itself
                      cb.goTo(end);

                      cb.mark(isNonNull);
                    }
                  })
              .asNonNullable();

      final Statement handle =
          field.isMapField()
              ? handleMapSetterNotNull(nonNull, field)
              : handleRepeatedNotNull(nonNull, field);
      return new Statement() {
        @Override
        protected void doGen(CodeBuilder cb) {
          handle.gen(cb);
          cb.mark(end); // jump here if listArg is null
        }
      };
    }

    private Statement handleRepeatedNotNull(final SoyExpression listArg, FieldDescriptor field) {
      checkArgument(listArg.isNonNullable());

      // Unbox listArg as List<SoyValueProvider> and wait until all items are done
      SoyExpression unboxed = listArg.unboxAsList();
      Expression resolved = detacher.resolveSoyValueProviderList(unboxed);

      // Enter new scope
      LocalVariableManager.Scope scope = varManager.enterScope();

      // Create local variables: list, loop index, list size
      final LocalVariable list =
          scope.createTemporary(field.getName() + "__list", resolved.resultType());

      final LocalVariable listSize =
          scope.createTemporary(field.getName() + "__size", Type.INT_TYPE);
      final LocalVariable index = scope.createTemporary(field.getName() + "__index", Type.INT_TYPE);
      Statement indexInitialization = index.store(constant(0), index.start());
      Statement loopInitialization =
          Statement.concat(
              list.store(resolved, list.start()),
              listSize.store(list.invoke(MethodRef.LIST_SIZE), listSize.start()));

      // exitScope must be called after creating all the variables
      final Statement scopeExit = scope.exitScope();
      // Expected type info of the list element
      SoyType elementSoyType = ((ListType) unboxed.soyType()).getElementType();
      SoyRuntimeType elementType = SoyRuntimeType.getBoxedType(elementSoyType);

      // Call list.get(i).resolveSoyValueProvider(), then cast to the expected subtype of SoyValue
      Expression getAndResolve =
          list // list
              .invoke(MethodRef.LIST_GET, index) // .get(i)
              .checkedCast(SOY_VALUE_PROVIDER_TYPE) // cast Object to SoyValueProvider
              .invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE) // .resolve()
              .checkedCast(elementType.runtimeType()); // cast SoyValue to appropriate subtype

      SoyExpression soyValue =
          SoyExpression.forSoyValue(elementType.soyType(), getAndResolve)
              // Set soyValue as a non-nullable, even though it is possible for templates to receive
              // lists with null elements. Lists with null elements will result in a
              // NullPointerException thrown in .handleNormalSetter() / .handleExtension().
              //
              // Note: This is different from jspb implementation. Jspb will happily accept nulls as
              // part of a repeated field, however said proto will error out at server-side
              // deserialization time. Hence, here we throw an NPE rather than copying jspb.
              .asNonNullable();

      // Call into .handleNormalSetter() or .handleExtension(), which will call add<Field>()
      final Statement getAndAddOne =
          field.isExtension()
              ? handleExtension(soyValue, field)
              : handleNormalSetter(soyValue, field);

      // Put the entire for-loop together
      return new Statement() {
        @Override
        protected void doGen(CodeBuilder cb) {
          loopInitialization.gen(cb);

          // if list.size() == 0, skip loop
          listSize.gen(cb);
          Label listIsEmpty = new Label();
          cb.ifZCmp(Opcodes.IFEQ, listIsEmpty);

          indexInitialization.gen(cb);
          // Begin loop
          Label loopStart = cb.mark();

          // loop body
          getAndAddOne.gen(cb);

          // i++
          cb.iinc(index.index(), 1);

          // if i < list.size(), goto loopStart
          index.gen(cb);
          listSize.gen(cb);
          cb.ifICmp(Opcodes.IFLT, loopStart);

          // End loop

          cb.mark(listIsEmpty);
          scopeExit.gen(cb);
        }
      };
    }

    private Statement handleExtension(final SoyExpression baseArg, final FieldDescriptor field) {
      // .setExtension() requires an extension identifier object
      final Expression extensionIdentifier = getExtensionField(field).accessor();

      // Call .setExtension() for regular extensions, .addExtension() for repeated extensions
      final MethodRef setterMethod =
          field.isRepeated() ? EXTENDABLE_BUILDER_ADD_EXTENSION : EXTENDABLE_BUILDER_SET_EXTENSION;

      final boolean isNullable = !baseArg.isNonNullable();

      return new Statement() {
        @Override
        protected void doGen(CodeBuilder cb) {
          // Put baseArg on stack

          baseArg.gen(cb);
          Label argIsNull = null;
          Label end = null;
          if (isNullable) {
            argIsNull = new Label();
            end = new Label();
            // Null check
            cb.dup();
            cb.ifNull(argIsNull);
          }

          // Arg is not null; unbox, coerce, run .valueOf(), add extension id, call .setExtension()
          unboxAndCoerce(cb, baseArg, field);

          // Put extension identifier on stack, swap to the right order
          extensionIdentifier.gen(cb);
          cb.swap();

          // Call .setExtension() / .addExtension(), skip to end
          setterMethod.invokeUnchecked(cb);

          // Arg is null; pop it off stack
          if (isNullable) {
            cb.goTo(end);
            cb.mark(argIsNull);
            cb.pop();

            // Done; cast back to MyProto.Builder
            cb.mark(end);
          }
          cb.checkCast(builderRuntimeType(descriptor).type());
        }
      };
    }

    /**
     * Assuming that the value of {@code baseArg} is on the top of the stack. unbox and coerce it to
     * be compatible with the given field descriptor.
     */
    private static void unboxAndCoerce(
        CodeBuilder cb, SoyExpression baseArg, FieldDescriptor field) {
      Type currentType;
      if (!isSafeProto(field)) {
        if (baseArg.isBoxed()) {
          currentType = unboxUnchecked(cb, baseArg.soyRuntimeType(), classToUnboxTo(field));
        } else {
          currentType = baseArg.resultType();
        }
      } else {
        currentType = baseArg.resultType();
      }

      coerce(cb, currentType, field);
    }

    @Nullable
    private static Class<?> classToUnboxTo(FieldDescriptor field) {
      switch (field.getJavaType()) {
        case BOOLEAN:
          return boolean.class;
        case FLOAT:
        case DOUBLE:
          return double.class;
        case INT:
        case ENUM:
          return long.class;
        case LONG:
          return shouldConvertBetweenStringAndLong(field) ? String.class : long.class;
        case STRING:
        case BYTE_STRING:
          return String.class;
        case MESSAGE:
          if (isSafeProto(field)) {
            throw new IllegalStateException("SanitizedContent objects shouldn't be unboxed");
          }
          return Message.class;
      }
      throw new AssertionError("unsupported field type: " + field);
    }

    /**
     * Generate bytecode that coerces the top of stack to the correct type for the given field
     * setter.
     */
    private static void coerce(CodeBuilder cb, Type currentType, FieldDescriptor field) {
      // TODO(user): This might be a good place to do some extra type-checking, by
      // running comparisons between currentType to getRuntimeType(field).
      switch (field.getJavaType()) {
        case BOOLEAN:
        case DOUBLE:
        case STRING:
          break; // no coercion necessary
        case FLOAT:
          if (!currentType.equals(Type.FLOAT_TYPE)) {
            cb.cast(currentType, Type.FLOAT_TYPE);
          }
          break;
        case INT:
          checkState(currentType.equals(Type.LONG_TYPE));
          if (isUnsigned(field)) {
            MethodRef.UNSIGNED_INTS_SATURATED_CAST.invokeUnchecked(cb);
          } else {
            // lossy!
            cb.cast(currentType, Type.INT_TYPE);
          }
          break;
        case LONG:
          if (shouldConvertBetweenStringAndLong(field)) {
            if (isUnsigned(field)) {
              MethodRef.UNSIGNED_LONGS_PARSE_UNSIGNED_LONG.invokeUnchecked(cb);
            } else {
              MethodRef.LONG_PARSE_LONG.invokeUnchecked(cb);
            }
          }
          break;
        case BYTE_STRING:
          BASE_ENCODING_BASE_64.invokeUnchecked(cb);
          cb.swap();
          BASE_ENCODING_DECODE.invokeUnchecked(cb);
          BYTE_STRING_COPY_FROM.invokeUnchecked(cb);
          break;
        case MESSAGE:
          coerceToMessage(cb, currentType, field);
          break;
        case ENUM:
          if (!currentType.equals(Type.INT_TYPE)) {
            cb.cast(currentType, Type.INT_TYPE);
          }
          // for proto 3 enums we call the setValue function which accepts an int so we don't need
          // to grab the actual enum value.
          if (!isProto3EnumField(field)) {
            getForNumberMethod(field.getEnumType()).invokeUnchecked(cb);
          }
          return;
      }
      if (field.isExtension()) {
        // primitive extensions need to be boxed since the api is generic
        Type fieldType = getRuntimeType(field);
        if (isPrimitive(fieldType)) {
          cb.valueOf(fieldType);
        }
      }
    }

    private static void coerceToMessage(CodeBuilder cb, Type currentType, FieldDescriptor field) {
      Type runtimeFieldType = getRuntimeType(field);
      if (isSafeProto(field)) {
        MethodRef toProto = SANITIZED_CONTENT_TO_PROTO.get(field.getMessageType().getFullName());
        if (!currentType.equals(BytecodeUtils.SANITIZED_CONTENT_TYPE)) {
          cb.checkCast(BytecodeUtils.SANITIZED_CONTENT_TYPE);
        }
        toProto.invokeUnchecked(cb);
        currentType = toProto.returnType();
      }
      if (!currentType.equals(runtimeFieldType)) {
        cb.checkCast(runtimeFieldType);
      }
    }

    // TODO(user): Consider consolidating all the safe proto references to a single place.
    private static boolean isSafeProto(FieldDescriptor field) {
      return field.getJavaType() == JavaType.MESSAGE
          && SAFE_PROTO_TO_SANITIZED_CONTENT.containsKey(field.getMessageType().getFullName());
    }
  }

  private static boolean shouldConvertBetweenStringAndLong(FieldDescriptor descriptor) {
    if (hasJsType(descriptor)) {
      JSType jsType = getJsType(descriptor);
      if (jsType == JSType.JS_STRING) {
        return true;
      }
    }
    return false;
  }

  // TODO(lukes): Consider caching? in SoyRuntimeType?
  private static TypeInfo messageRuntimeType(Descriptor descriptor) {
    String className = JavaQualifiedNames.getClassName(descriptor);
    return TypeInfo.createClass(className);
  }

  private static TypeInfo enumRuntimeType(EnumDescriptor descriptor) {
    String className = JavaQualifiedNames.getClassName(descriptor);
    return TypeInfo.createClass(className);
  }

  private static TypeInfo builderRuntimeType(Descriptor descriptor) {
    String className = JavaQualifiedNames.getClassName(descriptor);
    return TypeInfo.createClass(className + "$Builder");
  }

  private static Type getRuntimeType(FieldDescriptor field) {
    switch (field.getJavaType()) {
      case BOOLEAN:
        return Type.BOOLEAN_TYPE;
      case BYTE_STRING:
        return BYTE_STRING_TYPE;
      case DOUBLE:
        return Type.DOUBLE_TYPE;
      case ENUM:
        return isProto3EnumField(field)
            ? Type.INT_TYPE
            : TypeInfo.createClass(JavaQualifiedNames.getClassName(field.getEnumType())).type();
      case FLOAT:
        return Type.FLOAT_TYPE;
      case INT:
        return Type.INT_TYPE;
      case LONG:
        return Type.LONG_TYPE;
      case MESSAGE:
        return TypeInfo.createClass(JavaQualifiedNames.getClassName(field.getMessageType())).type();
      case STRING:
        return STRING_TYPE;
    }
    throw new AssertionError("unexpected type");
  }

  /** Returns the {@link MethodRef} for the generated getter method. */
  private static MethodRef getGetterMethod(FieldDescriptor descriptor) {
    checkArgument(
        !descriptor.isExtension(), "extensions do not have getter methods. %s", descriptor);
    TypeInfo message = messageRuntimeType(descriptor.getContainingType());
    String repeatedType = "";
    Type runtimeType;
    boolean isProto3Enum = isProto3EnumField(descriptor);
    if (descriptor.isMapField()) {
      repeatedType = "Map";
      runtimeType = BytecodeUtils.MAP_TYPE;
      isProto3Enum = mapValueIsProto3Enum(descriptor);
    } else if (descriptor.isRepeated()) {
      repeatedType = "List";
      runtimeType = BytecodeUtils.LIST_TYPE;
    } else if (isProto3Enum) {
      runtimeType = Type.INT_TYPE;
    } else {
      runtimeType = getRuntimeType(descriptor);
    }
    return MethodRef.createInstanceMethod(
            message,
            new Method(
                "get"
                    + getFieldName(descriptor, true)
                    // For proto3 enums we access the Value field
                    + (isProto3Enum ? "Value" : "")
                    + repeatedType,
                runtimeType,
                MethodRef.NO_METHOD_ARGS))
        // All protos are guaranteed to never return null
        .asNonNullable()
        .asCheap();
  }

  private static boolean mapValueIsProto3Enum(FieldDescriptor descriptor) {
    FieldDescriptor valueDescriptor = descriptor.getMessageType().getFields().get(1);
    return isProto3EnumField(valueDescriptor);
  }

  /**
   * Proto3 enums fields can accept and return unknown values via the get<Field>Value() methods, we
   * use those methods instead of the methods that deal with the enum constants in order to support
   * unknown enum values. If we didn't, any field with an unknown enum value would throw an
   * exception when we call {@code getNumber()} on the enum.
   *
   * <p>For comparison, in proto2 unknown values always get mapped to 0, so this problem doesn't
   * exist. Also, in proto2, the 'Value' functions don't exist, so we can't use them.
   */
  private static boolean isProto3EnumField(FieldDescriptor descriptor) {
    return descriptor.getType() == Descriptors.FieldDescriptor.Type.ENUM
        && descriptor.getFile().getSyntax() == Syntax.PROTO3;
  }

  /** Returns the {@link MethodRef} for the generated hasser method. */
  private static MethodRef getHasserMethod(FieldDescriptor descriptor) {
    TypeInfo message = messageRuntimeType(descriptor.getContainingType());
    return MethodRef.createInstanceMethod(
            message,
            new Method(
                "has" + getFieldName(descriptor, true),
                Type.BOOLEAN_TYPE,
                MethodRef.NO_METHOD_ARGS))
        .asCheap();
  }

  /** Returns the {@link MethodRef} for the get*Case method for oneof fields. */
  private static MethodRef getOneOfCaseMethod(OneofDescriptor descriptor) {
    TypeInfo message = messageRuntimeType(descriptor.getContainingType());
    return MethodRef.createInstanceMethod(
            message,
            new Method(
                "get" + underscoresToCamelCase(descriptor.getName(), true) + "Case",
                TypeInfo.createClass(JavaQualifiedNames.getCaseEnumClassName(descriptor)).type(),
                MethodRef.NO_METHOD_ARGS))
        .asCheap();
  }

  /** Returns the {@link MethodRef} for the generated newBuilder method. */
  private static MethodRef getBuilderMethod(Descriptor descriptor) {
    TypeInfo message = messageRuntimeType(descriptor);
    TypeInfo builder = builderRuntimeType(descriptor);
    return MethodRef.createStaticMethod(
            message, new Method("newBuilder", builder.type(), MethodRef.NO_METHOD_ARGS))
        .asNonNullable();
  }

  /** Returns the {@link MethodRef} for the generated defaultInstance method. */
  private static MethodRef getDefaultInstanceMethod(Descriptor descriptor) {
    TypeInfo message = messageRuntimeType(descriptor);
    return MethodRef.createStaticMethod(
            message, new Method("getDefaultInstance", message.type(), MethodRef.NO_METHOD_ARGS))
        .asNonNullable();
  }

  /** Returns the {@link MethodRef} for the generated put method for proto map. */
  private static MethodRef getPutMethod(FieldDescriptor descriptor) {
    checkState(descriptor.isMapField());
    List<FieldDescriptor> mapFields = descriptor.getMessageType().getFields();
    TypeInfo builder = builderRuntimeType(descriptor.getContainingType());
    return MethodRef.createInstanceMethod(
        builder,
        new Method(
            "put" + getFieldName(descriptor, true),
            builder.type(),
            new Type[] {getRuntimeType(mapFields.get(0)), getRuntimeType(mapFields.get(1))}));
  }

  /** Returns the {@link MethodRef} for the generated setter/adder method. */
  private static MethodRef getSetOrAddMethod(FieldDescriptor descriptor) {
    TypeInfo builder = builderRuntimeType(descriptor.getContainingType());
    String prefix = descriptor.isRepeated() ? "add" : "set";
    boolean isProto3EnumField = isProto3EnumField(descriptor);
    String suffix = isProto3EnumField ? "Value" : "";
    return MethodRef.createInstanceMethod(
            builder,
            new Method(
                prefix + getFieldName(descriptor, true) + suffix,
                builder.type(),
                new Type[] {isProto3EnumField ? Type.INT_TYPE : getRuntimeType(descriptor)}))
        .asNonNullable();
  }

  /** Returns the {@link MethodRef} for the generated build method. */
  private static MethodRef getBuildMethod(Descriptor descriptor) {
    TypeInfo message = messageRuntimeType(descriptor);
    TypeInfo builder = builderRuntimeType(descriptor);
    return MethodRef.createInstanceMethod(
            builder, new Method("build", message.type(), MethodRef.NO_METHOD_ARGS))
        .asNonNullable();
  }

  /** Returns the {@link MethodRef} for the generated forNumber method. */
  private static MethodRef getForNumberMethod(EnumDescriptor descriptor) {
    TypeInfo enumType = enumRuntimeType(descriptor);
    return MethodRef.createStaticMethod(
            enumType, new Method("forNumber", enumType.type(), ONE_INT_ARG))
        // Note: Enum.forNumber() returns null if there is no corresponding enum. If a bad value is
        // passed in (via unknown types), the generated bytecode will NPE.
        .asNonNullable()
        .asCheap();
  }

  /** Returns the {@link FieldRef} for the generated {@link Extension} field. */
  private static FieldRef getExtensionField(FieldDescriptor descriptor) {
    checkArgument(descriptor.isExtension(), "%s is not an extension", descriptor);
    String extensionFieldName = getFieldName(descriptor, false);
    if (descriptor.getExtensionScope() != null) {
      TypeInfo owner = messageRuntimeType(descriptor.getExtensionScope());
      return FieldRef.createPublicStaticField(owner, extensionFieldName, EXTENSION_TYPE);
    }
    // else we have a 'top level extension'
    String containingClass =
        JavaQualifiedNames.getPackage(descriptor.getFile())
            + "."
            + JavaQualifiedNames.getOuterClassname(descriptor.getFile());
    return FieldRef.createPublicStaticField(
        TypeInfo.createClass(containingClass), extensionFieldName, EXTENSION_TYPE);
  }

  /**
   * Determines which ProtoFieldInterpreter to use to box values of repeated fields.
   *
   * <p>Generates a field access expression to the appropriate ProtoFieldInterpreter.
   */
  private static class RepeatedFieldInterpreter extends FieldVisitor<Expression> {
    private static final FieldRef LONG_AS_INT =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "LONG_AS_INT");

    private static final FieldRef UNSIGNED_INT =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "UNSIGNED_INT");

    private static final FieldRef UNSIGNEDLONG_AS_STRING =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "UNSIGNEDLONG_AS_STRING");

    private static final FieldRef LONG_AS_STRING =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "LONG_AS_STRING");

    private static final FieldRef BOOL =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "BOOL");

    private static final FieldRef BYTES =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "BYTES");

    private static final FieldRef STRING =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "STRING");

    private static final FieldRef DOUBLE_AS_FLOAT =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "DOUBLE_AS_FLOAT");

    private static final FieldRef FLOAT =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "FLOAT");

    private static final FieldRef INT =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "INT");

    private static final FieldRef SAFE_HTML_PROTO =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "SAFE_HTML_PROTO");

    private static final FieldRef SAFE_SCRIPT_PROTO =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "SAFE_SCRIPT_PROTO");

    private static final FieldRef SAFE_STYLE_PROTO =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "SAFE_STYLE_PROTO");

    private static final FieldRef SAFE_STYLE_SHEET_PROTO =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "SAFE_STYLE_SHEET_PROTO");

    private static final FieldRef SAFE_URL_PROTO =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "SAFE_URL_PROTO");

    private static final FieldRef TRUSTED_RESOURCE_URI_PROTO =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "TRUSTED_RESOURCE_URI_PROTO");

    private static final FieldRef PROTO_MESSAGE =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "PROTO_MESSAGE");

    private static final FieldRef ENUM_FROM_PROTO =
        FieldRef.staticFieldReference(ProtoFieldInterpreter.class, "ENUM_FROM_PROTO");

    @Override
    protected Expression visitMap(
        FieldDescriptor mapField, Expression keyInterpreter, Expression valueInterpreter) {
      throw new AssertionError("visit map key/value individually");
    }

    @Override
    protected Expression visitRepeated(Expression valueInterpreter) {
      return valueInterpreter;
    }

    @Override
    protected Expression visitLongAsInt() {
      return LONG_AS_INT.accessor();
    }

    @Override
    protected Expression visitUnsignedInt() {
      return UNSIGNED_INT.accessor();
    }

    @Override
    protected Expression visitUnsignedLongAsString() {
      return UNSIGNEDLONG_AS_STRING.accessor();
    }

    @Override
    protected Expression visitLongAsString() {
      return LONG_AS_STRING.accessor();
    }

    @Override
    protected Expression visitBool() {
      return BOOL.accessor();
    }

    @Override
    protected Expression visitInt() {
      return INT.accessor();
    }

    @Override
    protected Expression visitBytes() {
      return BYTES.accessor();
    }

    @Override
    protected Expression visitString() {
      return STRING.accessor();
    }

    @Override
    protected Expression visitDoubleAsFloat() {
      return DOUBLE_AS_FLOAT.accessor();
    }

    @Override
    protected Expression visitFloat() {
      return FLOAT.accessor();
    }

    @Override
    protected Expression visitSafeHtml() {
      return SAFE_HTML_PROTO.accessor();
    }

    @Override
    protected Expression visitSafeScript() {
      return SAFE_SCRIPT_PROTO.accessor();
    }

    @Override
    protected Expression visitSafeStyle() {
      return SAFE_STYLE_PROTO.accessor();
    }

    @Override
    protected Expression visitSafeStyleSheet() {
      return SAFE_STYLE_SHEET_PROTO.accessor();
    }

    @Override
    protected Expression visitSafeUrl() {
      return SAFE_URL_PROTO.accessor();
    }

    @Override
    protected Expression visitTrustedResourceUrl() {
      return TRUSTED_RESOURCE_URI_PROTO.accessor();
    }

    @Override
    protected Expression visitMessage(Descriptor message) {
      return PROTO_MESSAGE.accessor();
    }

    @Override
    protected Expression visitEnum(EnumDescriptor enumType, FieldDescriptor fieldType) {
      // ENUM_FROM_PROTO converts a proto enum to an int, since Soy represents proto enums as ints
      // internally. However, for a repeated enum field in a proto3 message we call
      // getEnumNameValueList (which doesn't exist in proto2 messages and it is what allows proto3
      // to retain unknown enum values), so the values are already ints.
      if (fieldType.getFile().getSyntax() == Syntax.PROTO3) {
        return INT.accessor();
      }
      return ENUM_FROM_PROTO.accessor();
    }
  }
}
