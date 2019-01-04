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

package com.google.template.soy.types;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.internal.proto.Field;
import com.google.template.soy.internal.proto.FieldVisitor;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * A {@link SoyType} subclass which describes a protocol buffer type.
 *
 */
public final class SoyProtoType extends SoyType {
  private static final class TypeVisitor extends FieldVisitor<SoyType> {
    private final SoyTypeRegistry registry;

    TypeVisitor(SoyTypeRegistry registry) {
      this.registry = registry;
    }

    @Override
    protected SoyType visitMap(FieldDescriptor mapField, SoyType keyType, SoyType valueType) {
      return registry.getOrCreateMapType(keyType, valueType);
    }

    @Override
    protected SoyType visitRepeated(SoyType value) {
      return registry.getOrCreateListType(value);
    }

    // For these we could directly invoke the constructor, but by recursing back through the
    // registry we can ensure that we return the cached instance
    @Override
    protected SoyType visitMessage(Descriptor messageType) {
      return registry.getType(messageType.getFullName());
    }

    @Override
    protected SoyType visitEnum(EnumDescriptor enumType) {
      return registry.getType(enumType.getFullName());
    }

    @Override
    protected SoyType visitLongAsInt() {
      return IntType.getInstance();
    }

    @Override
    protected SoyType visitUnsignedInt() {
      return IntType.getInstance();
    }

    @Override
    protected SoyType visitUnsignedLongAsString() {
      return StringType.getInstance();
    }

    @Override
    protected SoyType visitLongAsString() {
      return StringType.getInstance();
    }

    @Override
    protected SoyType visitBool() {
      return BoolType.getInstance();
    }

    @Override
    protected SoyType visitInt() {
      return IntType.getInstance();
    }

    @Override
    protected SoyType visitBytes() {
      return StringType.getInstance();
    }

    @Override
    protected SoyType visitString() {
      return StringType.getInstance();
    }

    @Override
    protected SoyType visitDoubleAsFloat() {
      return FloatType.getInstance();
    }

    @Override
    protected SoyType visitFloat() {
      return FloatType.getInstance();
    }

    @Override
    protected SoyType visitSafeHtml() {
      return SanitizedType.HtmlType.getInstance();
    }

    @Override
    protected SoyType visitSafeScript() {
      return SanitizedType.JsType.getInstance();
    }

    @Override
    protected SoyType visitSafeStyle() {
      return SanitizedType.StyleType.getInstance();
    }

    @Override
    protected SoyType visitSafeStyleSheet() {
      return SanitizedType.StyleType.getInstance();
    }

    @Override
    protected SoyType visitSafeUrl() {
      return SanitizedType.UriType.getInstance();
    }

    @Override
    protected SoyType visitTrustedResourceUrl() {
      return SanitizedType.TrustedResourceUriType.getInstance();
    }
  }

  private abstract static class FieldWithType extends Field {
    FieldWithType(FieldDescriptor fieldDesc) {
      super(fieldDesc);
    }

    abstract SoyType getType();
  }

  private static final class NormalFieldWithType extends FieldWithType {
    // NOTE: we lazily resolve types so we can handle recursive messages
    @GuardedBy("this")
    SoyType type;

    @GuardedBy("this")
    TypeVisitor visitor;

    NormalFieldWithType(FieldDescriptor fieldDesc, TypeVisitor visitor) {
      super(fieldDesc);
      this.visitor = visitor;
    }

    @Override
    synchronized SoyType getType() {
      if (type == null) {
        type = FieldVisitor.visitField(getDescriptor(), visitor);
        checkNotNull(type, "Couldn't find a type for: %s", getDescriptor());
        visitor = null;
      }
      return type;
    }
  }

  private static final class AmbiguousFieldWithType extends FieldWithType {
    final Set<FieldWithType> fields;

    AmbiguousFieldWithType(Set<FieldWithType> fields) {
      super(fields.iterator().next().getDescriptor());
      this.fields = fields;
    }

    @Override
    SoyType getType() {
      throw ambiguousFieldsError(getName(), fields);
    }
  }

  private final Descriptor typeDescriptor;
  private final ImmutableMap<String, FieldWithType> fields;

  public SoyProtoType(
      final SoyTypeRegistry typeRegistry, Descriptor descriptor, Set<FieldDescriptor> extensions) {
    this.typeDescriptor = descriptor;
    this.fields =
        Field.getFieldsForType(
            descriptor,
            extensions,
            new Field.Factory<FieldWithType>() {
              TypeVisitor visitor = new TypeVisitor(typeRegistry);

              @Override
              public FieldWithType create(FieldDescriptor fieldDescriptor) {
                return new NormalFieldWithType(fieldDescriptor, visitor);
              }

              @Override
              public FieldWithType createAmbiguousFieldSet(Set<FieldWithType> fields) {
                return new AmbiguousFieldWithType(fields);
              }
            });
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType fromType) {
    return fromType == this;
  }

  public Descriptor getDescriptor() {
    return typeDescriptor;
  }

  /**
   * For ParseInfo generation, return a string that represents the Java source expression for the
   * static descriptor constant.
   *
   * @return The Java source expression for this type's descriptor.
   */
  public String getDescriptorExpression() {
    // We only need to import the outermost descriptor.
    Descriptor descriptor = typeDescriptor;
    while (descriptor.getContainingType() != null) {
      descriptor = descriptor.getContainingType();
    }
    return JavaQualifiedNames.getQualifiedName(descriptor) + ".getDescriptor()";
  }

  /** Returns the {@link FieldDescriptor} of the given field. */
  public FieldDescriptor getFieldDescriptor(String fieldName) {
    return fields.get(fieldName).getDescriptor();
  }

  /** Returns the {@link SoyType} of the given field, or null if the field does not exist. */
  @Nullable
  public SoyType getFieldType(String fieldName) {
    FieldWithType field = fields.get(fieldName);
    return field != null ? field.getType() : null;
  }

  /** Returns all the field names of this proto. */
  public ImmutableSet<String> getFieldNames() {
    return fields.keySet();
  }

  /** Returns this proto's type name for the given backend. */
  public String getNameForBackend(SoyBackendKind backend) {
    switch (backend) {
      case JS_SRC:
        // The 'proto' prefix is JSPB-specific. If we ever support some other
        // JavaScript proto implementation, we'll need some way to determine which
        // proto implementation the user wants to use at this point.
        return ProtoUtils.calculateQualifiedJsName(typeDescriptor);
      case TOFU:
      case JBC_SRC:
        return JavaQualifiedNames.getClassName(typeDescriptor);
      case PYTHON_SRC:
        throw new UnsupportedOperationException();
    }
    throw new AssertionError(backend);
  }

  @Override
  public String toString() {
    return typeDescriptor.getFullName();
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setProto(typeDescriptor.getFullName());
  }

  /**
   * Whether or not server side emuluation of jspb semantics needs to check for field presence and
   * return null for absent fields.
   *
   * <p>This isn't necessary in the JS backends because we can rely on the proto->JS compiler to
   * create these semantics.
   */
  public boolean shouldCheckFieldPresenceToEmulateJspbNullability(String fieldName) {
    return fields.get(fieldName).shouldCheckFieldPresenceToEmulateJspbNullability();
  }
}
