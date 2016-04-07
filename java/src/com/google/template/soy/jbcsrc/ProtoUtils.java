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
import static com.google.template.soy.jbcsrc.FieldRef.staticFieldReference;
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
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.GeneratedMessage.ExtendableMessage;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.jbcsrc.Expression.Feature;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.proto.JavaQualifiedNames;
import com.google.template.soy.types.proto.Protos;
import com.google.template.soy.types.proto.SoyProtoTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

  private static final ImmutableMap<Descriptor, MethodRef> SAFE_PROTO_TO_ACCESSOR =
      ImmutableMap.<Descriptor, MethodRef>builder()
          .put(SafeHtmlProto.getDescriptor(), createSafeAccessor(SafeHtmlProto.class))
          .put(SafeScriptProto.getDescriptor(), createSafeAccessor(SafeScriptProto.class))
          .put(SafeStyleProto.getDescriptor(), createSafeAccessor(SafeStyleProto.class))
          .put(SafeStyleSheetProto.getDescriptor(), createSafeAccessor(SafeStyleSheetProto.class))
          .put(SafeUrlProto.getDescriptor(), createSafeAccessor(SafeUrlProto.class))
          .put(
              TrustedResourceUrlProto.getDescriptor(),
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

  /** A helper to unbox a boxed proto value. */
  static SoyExpression unbox(SoyProtoTypeImpl protoType, SoyExpression baseExpr) {
    return baseExpr.unboxAs(messageClass(protoType));
  }

  /**
   * A simple class to encapsulate all the parameters shared between our different accessor
   * generation strategies
   */
  private static final class AccessorGenerator {
    final Class<?> protoClass;
    final SoyExpression baseExpr;
    final FieldAccessNode node;
    final Expression renderContext;
    final FieldDescriptor descriptor;
    final boolean isProto3;

    AccessorGenerator(SoyProtoTypeImpl protoType, SoyExpression baseExpr, FieldAccessNode node,
        Expression renderContext) {
      this.protoClass = messageClass(protoType);
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
      SoyExpression typedBaseExpr = baseExpr.withRenderContext(renderContext).unboxAs(protoClass);
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
      final MethodRef getMethodRef =
          MethodRef.create(getGetterMethod(descriptor)).asNonNullable().asCheap();
      if (shouldCheckForFieldPresence()) {
        final Label hasFieldLabel = new Label();
        final BytecodeProducer hasCheck;
        OneofDescriptor containingOneof = descriptor.getContainingOneof();
        if (containingOneof != null) {
          final MethodRef getCaseRef =
              MethodRef.create(getOneOfCaseMethod(containingOneof));
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
          final MethodRef hasMethodRef;
          hasMethodRef = MethodRef.create(getHasserMethod(descriptor)).asCheap();
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
      FieldRef extensionField = staticFieldReference(getExtensionField(descriptor));
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
        Class<? extends Message> messageClass = messageClass(fieldProtoType);
        return SoyExpression.forProto(
            fieldProtoType,
            messageClass,
            field.cast(messageClass), // this cast isn't redundant for extensions
            renderContext);
      } else {
        // All other are special sanitized types
        ContentKind kind = ((SanitizedType) node.getType()).getContentKind();
        Descriptor messageType = descriptor.getMessageType();
        MethodRef methodRef = SAFE_PROTO_TO_ACCESSOR.get(messageType);
        return SoyExpression.forSanitizedString(
            field
                .cast(methodRef.owner().type()) // this cast isn't redundant for extensions
                .invoke(methodRef),
            kind);
      }
    }

    private SoyExpression handleRepeated() {
      // For repeated fields we delegate to the tofu implementation.  This is because the proto
      // will return a List<Integer> which we will need to turn into a List<IntegerData> and so on
      // we could handle this by
      // 1. generating Runtime.java helpers to do this kind of collection boxing conversion
      // 2. enhancing SoyExpression to be able to understand a 'partially unboxed collection'
      // 3. fallback to tofu (which already supports this)
      // 4. Add new SoyList implementations that can do this kind of lazy resolving transparently
      //    (I think SoyEasyList is supposed to support this)
      // For now we will do #3.  #2 would be ideal (least overhead) but would be very complex. #1 or
      // $4 would both be reasonable compromises.
      return SoyExpression.forSoyValue(node.getType(),
          MethodRef.RUNTIME_GET_FIELD_PROVIDER.invoke(baseExpr.box(), constant(node.getFieldName()))
          // We can immediately resolve because we know proto fields don't need detach logic.
          // they are always immediately available.
          .invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE)
          .cast(node.getType().javaType()));
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

  private static Class<? extends Message> messageClass(SoyProtoTypeImpl protoType) {
    try {
      return Class.forName(protoType.getNameForBackend(SoyBackendKind.JBC_SRC))
          .asSubclass(Message.class);
    } catch (ClassNotFoundException e) {
      // TODO(lukes): report an error here?
      throw new AssertionError(e);
    }
  }

  private static Class<? extends Message> descriptorClass(Descriptor descriptor) {
    String className = JavaQualifiedNames.getClassName(descriptor);
    ClassLoader cl = descriptor.getClass().getClassLoader();
    if (cl == null) {
      cl = ClassLoader.getSystemClassLoader();
    }
    try {
      return cl.loadClass(className).asSubclass(Message.class);
    } catch (ClassNotFoundException ex) {
      throw new AssertionError(
          "Could not resolve java class for descriptor " + descriptor.getFullName());
    }
  }


  /** Returns the {@link Method} for the generated getter method. */
  public static Method getGetterMethod(FieldDescriptor descriptor) throws SecurityException {
    Preconditions.checkArgument(!descriptor.isExtension(),
        "extensions do not have getter methods. %s", descriptor);
    Class<? extends Message> owner = descriptorClass(descriptor.getContainingType());
    try {
      return owner.getMethod("get" + underscoresToCamelCase(descriptor.getName(), true));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find getter method for " + descriptor, e);
    }
  }

  /** Returns the {@link Method} for the generated hasser method. */
  private static Method getHasserMethod(FieldDescriptor descriptor) throws SecurityException {
    Class<? extends Message> owner = descriptorClass(descriptor.getContainingType());
    try {
      return owner.getMethod("has" + underscoresToCamelCase(descriptor.getName(), true));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find hasser method for " + descriptor, e);
    }
  }

  /** Returns the {@link Method} for the get*Case method for oneof fields. */
  public static Method getOneOfCaseMethod(OneofDescriptor descriptor) throws SecurityException  {
    Class<? extends Message> owner = descriptorClass(descriptor.getContainingType());
    try {
      return owner.getMethod("get" + underscoresToCamelCase(descriptor.getName(), true) + "Case");
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Could not find case method for " + descriptor, e);
    }
  }

  /** Returns the {@link Field} for the generated {@link Extension} field. */
  public static Field getExtensionField(FieldDescriptor descriptor) throws SecurityException {
    Preconditions.checkArgument(descriptor.isExtension(), "%s is not an extension", descriptor);
    String extensionFieldName = underscoresToCamelCase(descriptor.getName(), false);
    if (descriptor.getExtensionScope() != null) {
      Class<? extends Message> owner = descriptorClass(descriptor.getExtensionScope());
      try {
        return owner.getField(extensionFieldName);
      } catch (NoSuchFieldException e) {
        throw new IllegalStateException("Could not find extension field for " + descriptor, e);
      }
    }
    // else we have a 'top level extension'
    String containingClass =
        JavaQualifiedNames.getPackage(descriptor.getFile()) + "."
        + JavaQualifiedNames.getOuterClassname(descriptor.getFile());
    try {
      return Class.forName(containingClass).getField(extensionFieldName);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not find extension field for " + descriptor, e);
    }
  }
}
