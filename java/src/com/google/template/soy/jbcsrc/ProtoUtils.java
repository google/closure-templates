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

import static com.google.template.soy.jbcsrc.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.BytecodeUtils.numericConversion;
import static com.google.template.soy.types.proto.JavaQualifiedNames.underscoresToCamelCase;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor.Syntax;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Extension;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.protobuf.GeneratedMessage.GeneratedExtension;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.jbcsrc.Expression.Feature;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.proto.JavaQualifiedNames;
import com.google.template.soy.types.proto.Protos;
import com.google.template.soy.types.proto.SoyProtoTypeImpl;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Utilities for dealing with protocol buffers, they are all moved here in order to make it easier
 * to exclude from the Soy open source build.
 */
final class ProtoUtils {
  private static final Type BYTE_STRING_TYPE = Type.getType(ByteString.class);
  private static final Type EXTENSION_TYPE = Type.getType(GeneratedExtension.class);

  static final MethodRef SOY_PROTO_VALUE_GET_PROTO =
      MethodRef.forMethod(SoyProtoTypeImpl.Value.class, "getProto").asNonNullable().asCheap();

  static final MethodRef RENDER_CONTEXT_BOX =
      MethodRef.create(RenderContext.class, "box", Message.class).asNonNullable();

  static final MethodRef PROTO_ENUM_GET_NUMBER =
      MethodRef.forMethod(ProtocolMessageEnum.class, "getNumber").asCheap();

  static final MethodRef BYTE_STRING_TO_BYTE_ARRAY =
      MethodRef.forMethod(ByteString.class, "toByteArray").asNonNullable();

  static final MethodRef BASE_64 =
      MethodRef.forMethod(BaseEncoding.class, "base64")
          .asNonNullable().asCheap();

  static final MethodRef BASE_ENCODING_ENCODE =
      MethodRef.forMethod(BaseEncoding.class, "encode", byte[].class)
          .asNonNullable().asCheap();

  static final MethodRef EXTENDABLE_MESSAGE_GET_EXTENSION =
      MethodRef.forMethod(ExtendableMessage.class, "getExtension", ExtensionLite.class)
          .asNonNullable().asCheap();

  static final MethodRef EXTENDABLE_MESSAGE_HAS_EXTENSION =
      MethodRef.forMethod(ExtendableMessage.class, "hasExtension", ExtensionLite.class)
          .asNonNullable()
          .asCheap();

  // We use the full name as the key instead of the descriptor, since descriptors use identity
  // semantics for equality and we may load the descriptors for these protos from multiple sources
  // depending on our configuration.
  private static final ImmutableMap<String, MethodRef> SAFE_PROTO_TO_ACCESSOR =
      ImmutableMap.<String, MethodRef>builder()
          .put(SafeHtmlProto.getDescriptor().getFullName(), createSafeAccessor(SafeHtmlProto.class))
          .put(
              SafeScriptProto.getDescriptor().getFullName(),
              createSafeAccessor(SafeScriptProto.class))
          .put(
              SafeStyleProto.getDescriptor().getFullName(),
              createSafeAccessor(SafeStyleProto.class))
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              createSafeAccessor(SafeStyleSheetProto.class))
          .put(SafeUrlProto.getDescriptor().getFullName(), createSafeAccessor(SafeUrlProto.class))
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              createSafeAccessor(TrustedResourceUrlProto.class))
          .build();

  private static MethodRef createSafeAccessor(Class<?> clazz) {
    // All the safe web types have the same format for their access method names:
    // getPrivateDoNotAccessOrElse + name + WrappedValue  where name is the prefix of the message
    // type.
    String simpleName = clazz.getSimpleName();
    simpleName = simpleName.substring(0, simpleName.length() - "Proto".length());
    return MethodRef.forMethod(clazz, "getPrivateDoNotAccessOrElse" + simpleName + "WrappedValue")
        .asNonNullable().asCheap();
  }

  /**
   * Returns a {@link SoyExpression} for accessing a field of a proto.
   *
   * @param protoType The type of the proto being accessed
   * @param baseExpr The proto being accessed
   * @param node The field access operation
   * @param renderContext The render context
   */
  static SoyExpression accessField(SoyProtoTypeImpl protoType, SoyExpression baseExpr,
      FieldAccessNode node, Expression renderContext) {
    return new AccessorGenerator(protoType, baseExpr, node, renderContext).generateAccessor();
  }

  /**
   * A simple class to encapsulate all the parameters shared between our different accessor
   * generation strategies
   */
  private static final class AccessorGenerator {
    final SoyRuntimeType unboxedRuntimeType;
    final SoyExpression baseExpr;
    final FieldAccessNode node;
    final Expression renderContext;
    final FieldDescriptor descriptor;
    final boolean isProto3;

    AccessorGenerator(SoyProtoTypeImpl protoType, SoyExpression baseExpr, FieldAccessNode node,
        Expression renderContext) {
      this.unboxedRuntimeType = SoyRuntimeType.getUnboxedType(protoType).get();
      this.baseExpr = baseExpr;
      this.node = node;
      this.renderContext = renderContext;
      this.descriptor = protoType.getFieldDescriptor(node.getFieldName());
      this.isProto3 = descriptor.getFile().getSyntax() == Syntax.PROTO3;
    }

    SoyExpression generateAccessor() {
      if (descriptor.isRepeated()) {
        return handleRepeated();
      }
      SoyExpression typedBaseExpr;
      if (baseExpr.isBoxed()) {
        typedBaseExpr =
            SoyExpression.forProto(
                unboxedRuntimeType,
                baseExpr
                    .invoke(ProtoUtils.SOY_PROTO_VALUE_GET_PROTO)
                    // this cast is required because getProto() is generic, so it basically returns
                    // 'Message'
                    .cast(unboxedRuntimeType.runtimeType()),
                renderContext);
      } else if (baseExpr.soyRuntimeType().equals(unboxedRuntimeType)) {
        typedBaseExpr = baseExpr;
      } else {
        throw new AssertionError("should be impossible");
      }
      if (descriptor.isExtension()) {
        return handleExtension(typedBaseExpr);
      } else {
        return handleNormalField(typedBaseExpr);
      }
    }

    private SoyExpression handleNormalField(final SoyExpression typedBaseExpr) {
      // TODO(lukes): consider adding a cache for the method lookups.
      // To implement jspb semantics for proto nullability we need to call has<Field>() methods for
      // a subset of fields as specified in SoyProtoTypeImpl. Though, we should probably actually be
      // testing against jspb semantics.  The best way forward is probably to first invest in
      // support for protos in our integration tests.
      final MethodRef getMethodRef = getGetterMethod(descriptor);
      if (shouldCheckForFieldPresence()) {
        final Label hasFieldLabel = new Label();
        final BytecodeProducer hasCheck;
        OneofDescriptor containingOneof = descriptor.getContainingOneof();
        if (containingOneof != null) {
          final MethodRef getCaseRef = getOneOfCaseMethod(containingOneof);
          final Expression fieldNumber = BytecodeUtils.constant(descriptor.getNumber());
          // this basically just calls getFooCase().getNumber() == field_number
          hasCheck = new BytecodeProducer(){
            @Override
            void doGen(CodeBuilder adapter) {
              getCaseRef.invokeUnchecked(adapter);
              adapter.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                  getCaseRef.returnType().getInternalName(),
                  "getNumber",
                  "()I",
                  false);
              fieldNumber.gen(adapter);
              adapter.ifCmp(Type.INT_TYPE, GeneratorAdapter.EQ, hasFieldLabel);
            }
          };
        } else {
          // otherwise just call the has* method
          final MethodRef hasMethodRef = getHasserMethod(descriptor);
          hasCheck = new BytecodeProducer() {
            @Override
            void doGen(CodeBuilder adapter) {
              hasMethodRef.invokeUnchecked(adapter);
              adapter.ifZCmp(Opcodes.IFNE, hasFieldLabel);
            }
          };
        }
        final Label endLabel = new Label();
        // If the field doesn't have an explicit default then we need to call .has<Field> and return
        // null if it isn't present.
        SoyExpression interpretedField =
            interpretField(
                new Expression(
                    getMethodRef.returnType(),
                    getMethodRef.features().minus(Feature.NON_NULLABLE)) {
                  @Override
                  void doGen(CodeBuilder adapter) {
                    typedBaseExpr.gen(adapter);
                    adapter.dup();
                    hasCheck.gen(adapter);
                    adapter.pop();
                    // The field is missing, substitute null.
                    adapter.visitInsn(Opcodes.ACONST_NULL);
                    adapter.goTo(endLabel);
                    adapter.mark(hasFieldLabel);
                    getMethodRef.invokeUnchecked(adapter);
                  }
                });
        if (BytecodeUtils.isPrimitive(interpretedField.resultType())) {
          interpretedField = interpretedField.box();
        }
        // TODO(b/22389927): This is another place where the soy type system lies to us, so make
        // sure to mark the type as nullable.
        return interpretedField.labelEnd(endLabel).asNullable();
      } else {
        // Simple case, just call .get and interpret the result
        return interpretField(typedBaseExpr.invoke(getMethodRef));
      }
    }

    /**
     * TODO(lukes): when jspb nullability semantics get fixed, we should be able to simplify this
     * as well.
     */
    private boolean shouldCheckForFieldPresence() {
      if (descriptor.hasDefaultValue()) {
        return false; // No need to check for presence if the field has a explicit default value.
      } else if (!isProto3) {
        return true; // Always check for presence in proto2.
      } else {
        // For proto3, only check for field presence for message subtypes or oneof members.
        // TODO(b/27726705): non message types in oneof fields should likely not be nullable in soy
        // though for that to be reasonable we will likely need to expose direct access to the
        // 'getCase' method in soy.
        return descriptor.getContainingOneof() != null
            || descriptor.getType() == FieldDescriptor.Type.MESSAGE;
      }
    }

    private SoyExpression interpretField(Expression field) throws AssertionError {
      // Depending on types we may need to do a trivial conversion
      // (e.g. int->long, float->double, enum-> int)
      switch (descriptor.getJavaType()) {
        case FLOAT:
          return SoyExpression.forFloat(numericConversion(field, Type.DOUBLE_TYPE));
        case DOUBLE:
          return SoyExpression.forFloat(field);
        case ENUM:
          return SoyExpression.forInt(
              numericConversion(field.invoke(PROTO_ENUM_GET_NUMBER), Type.LONG_TYPE));
        case INT:
          if (shouldReinterpretAsString(descriptor)) {
            return SoyExpression.forString(MethodRef.INTEGER_TO_STRING.invoke(field));
          }
          return SoyExpression.forInt(numericConversion(field, Type.LONG_TYPE));
        case LONG:
          if (shouldReinterpretAsString(descriptor)) {
            return SoyExpression.forString(MethodRef.LONG_TO_STRING.invoke(field));
          }
          return SoyExpression.forInt(field);
        case BOOLEAN:
          return SoyExpression.forBool(field);
        case STRING:
          return SoyExpression.forString(field);
        case MESSAGE:
          return messageToSoyExpression(field);
        case BYTE_STRING:
          return byteStringToBase64String(field);
        default:
          throw new AssertionError("unsupported field type: " + descriptor);
      }
    }

    private SoyExpression handleExtension(final SoyExpression typedBaseExpr) {
      // extensions are a little weird since we need to look up the extension object and then call
      // message.getExtension(Extension) and then cast and (maybe) unbox the result.
      // The reason we need to cast is because .getExtension is a generic api and that is just how
      // stupid java generics work.
      FieldRef extensionField = getExtensionField(descriptor);
      final Expression extensionFieldAccessor = extensionField.accessor();
      // An extension with a default value is pretty rare, but we still need to support it.
      if (!descriptor.hasDefaultValue()) {
        final Label endLabel = new Label();
        SoyExpression interpretedField =
            intepretExtensionField(
                new Expression(
                    EXTENDABLE_MESSAGE_GET_EXTENSION.returnType(),
                    EXTENDABLE_MESSAGE_GET_EXTENSION.features().minus(Feature.NON_NULLABLE)) {
                  @Override
                  void doGen(CodeBuilder adapter) {
                    typedBaseExpr.gen(adapter);
                    adapter.dup();
                    extensionFieldAccessor.gen(adapter);
                    EXTENDABLE_MESSAGE_HAS_EXTENSION.invokeUnchecked(adapter);
                    Label hasFieldLabel = new Label();
                    adapter.ifZCmp(Opcodes.IFNE, hasFieldLabel);
                    adapter.pop();
                    // The field is missing, substitute null.
                    adapter.visitInsn(Opcodes.ACONST_NULL);
                    adapter.goTo(endLabel);
                    adapter.mark(hasFieldLabel);
                    extensionFieldAccessor.gen(adapter);
                    EXTENDABLE_MESSAGE_GET_EXTENSION.invokeUnchecked(adapter);
                  }
                });
        // Box primitives to allow it to be compatible with null.
        if (BytecodeUtils.isPrimitive(interpretedField.resultType())) {
          interpretedField = interpretedField.box();
        }
        // TODO(b/22389927): This is another place where the soy type system lies to us, so make
        // sure to mark the type as nullable.
        return interpretedField.labelEnd(endLabel).asNullable();
      } else {
        return intepretExtensionField(
            typedBaseExpr.invoke(EXTENDABLE_MESSAGE_GET_EXTENSION, extensionFieldAccessor));
      }
    }

    private SoyExpression intepretExtensionField(Expression field) throws AssertionError {
      switch (descriptor.getJavaType()) {
        case FLOAT:
        case DOUBLE:
          return SoyExpression.forFloat(field.cast(Number.class).invoke(MethodRef.DOUBLE_VALUE));
        case ENUM:
          return SoyExpression.forInt(
              numericConversion(
                  field.cast(ProtocolMessageEnum.class).invoke(PROTO_ENUM_GET_NUMBER),
                  Type.LONG_TYPE));
        case INT:
        case LONG:
          if (shouldReinterpretAsString(descriptor)) {
            return SoyExpression.forString(field.invoke(MethodRef.OBJECT_TO_STRING));
          }
          return SoyExpression.forInt(field.cast(Number.class).invoke(MethodRef.LONG_VALUE));
        case BOOLEAN:
          return SoyExpression.forBool(field.cast(Boolean.class).invoke(MethodRef.BOOLEAN_VALUE));
        case STRING:
          return SoyExpression.forString(field.cast(String.class));
        case MESSAGE:
          return messageToSoyExpression(field);
        case BYTE_STRING:
          // Current tofu support for ByteString is to base64 encode it.
          return byteStringToBase64String(field.cast(ByteString.class));
        default:
          throw new AssertionError("unsupported field type: " + descriptor);
      }
    }

    private SoyExpression byteStringToBase64String(Expression byteString) {
      byteString.checkAssignableTo(BYTE_STRING_TYPE);
      final Expression byteArray = byteString.invoke(BYTE_STRING_TO_BYTE_ARRAY);
      return SoyExpression.forString(
          new Expression(STRING_TYPE, Feature.NON_NULLABLE) {
            @Override
            void doGen(CodeBuilder adapter) {
              byteArray.gen(adapter);
              BASE_64.invokeUnchecked(adapter);
              // swap the two top items of the stack.
              // This ensures that the base expression is gen'd at a stack depth of zero, which is
              // important if it contains a label for a reattach point.
              adapter.swap();
              BASE_ENCODING_ENCODE.invokeUnchecked(adapter);
            }
          });
    }

    private SoyExpression messageToSoyExpression(Expression field) {
      if (node.getType() instanceof SoyProtoTypeImpl) {
        SoyProtoTypeImpl fieldProtoType = (SoyProtoTypeImpl) node.getType();
        SoyRuntimeType protoRuntimeType = SoyRuntimeType.getUnboxedType(fieldProtoType).get();
        return SoyExpression.forProto(
            protoRuntimeType,
            field.cast(protoRuntimeType.runtimeType()), // this cast isn't redundant for extensions
            renderContext);
      } else {
        // All other are special sanitized types
        ContentKind kind = ((SanitizedType) node.getType()).getContentKind();
        Descriptor messageType = descriptor.getMessageType();
        MethodRef methodRef = SAFE_PROTO_TO_ACCESSOR.get(messageType.getFullName());
        return SoyExpression.forSanitizedString(
            field
                .cast(methodRef.owner().type()) // this cast isn't redundant for extensions
                .invoke(methodRef),
            kind);
      }
    }

    private SoyExpression handleRepeated() {
      // For repeated fields we delegate to the tofu implementation.  This is because the proto
      // will return a List<Integer> which we will need to turn into a List<IntegerData> and so on.
      // we could handle this by
      // 1. generating Runtime.java helpers to do this kind of collection boxing conversion
      // 2. enhancing SoyExpression to be able to understand a 'partially unboxed collection'
      // 3. fallback to tofu (which already supports this)
      // 4. Add new SoyList implementations that can do this kind of lazy resolving transparently
      //    (I think SoyEasyList is supposed to support this)
      // For now we will do #3.  #2 would be ideal (least overhead) but would be very complex. #1 or
      // #4 would both be reasonable compromises.
      SoyRuntimeType boxedType = SoyRuntimeType.getBoxedType(node.getType());
      return SoyExpression.forSoyValue(
          node.getType(),
          MethodRef.RUNTIME_GET_FIELD_PROVIDER
              .invoke(baseExpr.box(), constant(node.getFieldName()))
              // We can immediately resolve because we know proto fields don't need detach logic.
              // they are always immediately available.
              .invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE)
              .cast(boxedType.runtimeType()));
    }
  }

  private static boolean shouldReinterpretAsString(FieldDescriptor descriptor) {
    boolean reinterpretAsString = false;
    if (Protos.hasJsType(descriptor)) {
      Protos.JsType jsType = Protos.getJsType(descriptor);
      if (jsType == Protos.JsType.STRING) {
        reinterpretAsString = true;
      }
    }
    return reinterpretAsString;
  }

  // Consider caching? in SoyRuntimeType?
  private static TypeInfo descriptorRuntimeType(Descriptor descriptor) {
    String className = JavaQualifiedNames.getClassName(descriptor);
    return TypeInfo.create(className);
  }

  private static Type getRuntimeReturnType(FieldDescriptor field) {
    switch (field.getJavaType()) {
      case BOOLEAN:
        return Type.BOOLEAN_TYPE;
      case BYTE_STRING:
        return BYTE_STRING_TYPE;
      case DOUBLE:
        return Type.DOUBLE_TYPE;
      case ENUM:
        return TypeInfo.create(JavaQualifiedNames.getClassName(field.getEnumType())).type();
      case FLOAT:
        return Type.FLOAT_TYPE;
      case INT:
        return Type.INT_TYPE;
      case LONG:
        return Type.LONG_TYPE;
      case MESSAGE:
        return TypeInfo.create(JavaQualifiedNames.getClassName(field.getMessageType())).type();
      case STRING:
        return BytecodeUtils.STRING_TYPE;
      default:
        throw new AssertionError("unexpected type");
    }
  }

  /** Returns the {@link MethodRef} for the generated getter method. */
  private static MethodRef getGetterMethod(FieldDescriptor descriptor) throws SecurityException {
    Preconditions.checkArgument(!descriptor.isExtension(),
        "extensions do not have getter methods. %s", descriptor);
    TypeInfo owner = descriptorRuntimeType(descriptor.getContainingType());
    return MethodRef.createInstanceMethod(
            owner,
            new org.objectweb.asm.commons.Method(
                "get" + underscoresToCamelCase(descriptor.getName(), true),
                Type.getMethodDescriptor(getRuntimeReturnType(descriptor))))
        // All protos are guaranteed to never return null
        .asNonNullable()
        .asCheap();
  }

  /** Returns the {@link MethodRef} for the generated hasser method. */
  private static MethodRef getHasserMethod(FieldDescriptor descriptor) throws SecurityException {
    TypeInfo owner = descriptorRuntimeType(descriptor.getContainingType());
    return MethodRef.createInstanceMethod(
            owner,
            new org.objectweb.asm.commons.Method(
                "has" + underscoresToCamelCase(descriptor.getName(), true),
                Type.getMethodDescriptor(Type.BOOLEAN_TYPE)))
        .asCheap();
  }

  /** Returns the {@link MethodRef} for the get*Case method for oneof fields. */
  public static MethodRef getOneOfCaseMethod(OneofDescriptor descriptor) throws SecurityException {
    TypeInfo owner = descriptorRuntimeType(descriptor.getContainingType());
    return MethodRef.createInstanceMethod(
            owner,
            new org.objectweb.asm.commons.Method(
                "get" + underscoresToCamelCase(descriptor.getName(), true) + "Case",
                Type.getMethodDescriptor(
                    TypeInfo.create(JavaQualifiedNames.getCaseEnumClassName(descriptor)).type())))
        .asCheap();
  }

  /** Returns the {@link FieldRef} for the generated {@link Extension} field. */
  public static FieldRef getExtensionField(FieldDescriptor descriptor) throws SecurityException {
    Preconditions.checkArgument(descriptor.isExtension(), "%s is not an extension", descriptor);
    String extensionFieldName = underscoresToCamelCase(descriptor.getName(), false);
    if (descriptor.getExtensionScope() != null) {
      TypeInfo owner = descriptorRuntimeType(descriptor.getExtensionScope());
      return FieldRef.createPublicStaticField(owner, extensionFieldName, EXTENSION_TYPE);
    }
    // else we have a 'top level extension'
    String containingClass =
        JavaQualifiedNames.getPackage(descriptor.getFile()) + "."
        + JavaQualifiedNames.getOuterClassname(descriptor.getFile());
    return FieldRef.createPublicStaticField(
        TypeInfo.create(containingClass), extensionFieldName, EXTENSION_TYPE);
  }
}
