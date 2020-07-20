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

import com.google.common.base.Objects;
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

/** A {@link SoyType} subclass which describes a protocol buffer type. */
public final class SoyProtoType extends SoyType {
  private static final class TypeVisitor extends FieldVisitor<SoyType> {
    private final TypeInterner interner;
    private final ProtoTypeRegistry registry;

    TypeVisitor(TypeInterner interner, ProtoTypeRegistry registry) {
      this.interner = interner;
      this.registry = registry;
    }

    @Override
    protected SoyType visitMap(FieldDescriptor mapField, SoyType keyType, SoyType valueType) {
      return interner.getOrCreateMapType(keyType, valueType);
    }

    @Override
    protected SoyType visitRepeated(SoyType value) {
      return interner.getOrCreateListType(value);
    }

    // For these we could directly invoke the constructor, but by recursing back through the
    // registry we can ensure that we return the cached instance
    @Override
    protected SoyType visitMessage(Descriptor messageType) {
      return registry.getProtoType(messageType.getFullName());
    }

    @Override
    protected SoyType visitEnum(EnumDescriptor enumType, FieldDescriptor fieldType) {
      return registry.getProtoType(enumType.getFullName());
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

  private static final class FieldWithType extends Field {
    // NOTE: we lazily resolve types so we can handle recursive messages
    @GuardedBy("this")
    SoyType type;

    @GuardedBy("this")
    TypeVisitor visitor;

    FieldWithType(FieldDescriptor fieldDesc, TypeVisitor visitor) {
      super(fieldDesc);
      this.visitor = visitor;
    }

    synchronized SoyType getType() {
      if (type == null) {
        type = FieldVisitor.visitField(getDescriptor(), visitor);
        checkNotNull(type, "Couldn't find a type for: %s", getDescriptor());
        visitor = null;
      }
      return type;
    }
  }

  public static SoyProtoType newForTest(Descriptor d) {
    return new SoyProtoType(TypeRegistries.newTypeInterner(), (fqn) -> null, d, ImmutableSet.of());
  }

  private final Descriptor typeDescriptor;
  private final ImmutableMap<String, FieldWithType> fields;
  private final ImmutableSet<String> extensionFieldNames;

  SoyProtoType(
      TypeInterner interner,
      ProtoTypeRegistry registry,
      Descriptor descriptor,
      Set<FieldDescriptor> extensions) {
    this(new TypeVisitor(interner, registry), descriptor, extensions);
  }

  private SoyProtoType(
      TypeVisitor visitor, Descriptor descriptor, Set<FieldDescriptor> extensions) {
    this.typeDescriptor = descriptor;
    this.fields =
        Field.getFieldsForType(
            descriptor, extensions, fieldDescriptor -> new FieldWithType(fieldDescriptor, visitor));
    this.extensionFieldNames =
        fields.keySet().stream()
            .filter(
                fieldName -> {
                  FieldWithType field = fields.get(fieldName);
                  // The fields map currently maps both the simple name and the fully qualified name
                  // of an extension field to the FieldWithType. The extensionFieldNames set
                  // should only have the fully qualified names of the fields.
                  return field.getDescriptor().isExtension()
                      && fieldName.equals(field.getFullyQualifiedName());
                })
            .collect(ImmutableSet.toImmutableSet());
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
    return ProtoUtils.getQualifiedOuterClassname(typeDescriptor);
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

  /** Returns all the fully qualified extension field names of this proto. */
  public ImmutableSet<String> getExtensionFieldNames() {
    return extensionFieldNames;
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

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SoyProtoType that = (SoyProtoType) o;
    return Objects.equal(typeDescriptor.getFullName(), that.typeDescriptor.getFullName());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hashCode(typeDescriptor.getFullName());
  }
}
