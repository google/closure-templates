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

package com.google.template.soy.types.proto;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.CaseFormat;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.primitive.SanitizedType;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * A {@link SoyType} subclass which describes a protocol buffer type.
 *
 * <p>TODO(lukes): introduce a dedicate Kind for protos instead of using SoyObjectType and the
 * Object kind
 *
 */
public final class SoyProtoTypeImpl implements SoyObjectType, SoyProtoType {

  private static final Logger logger = Logger.getLogger(
      SoyProtoTypeImpl.class.getName());

  /** Interface used to indicate that a value is a proto. */
  public interface Value extends SoyRecord {
    /** Returns the underlying message. */
    public Message getProto();
  }

  /** A member field of this proto type. */
  interface Field {

    /** Return the name of this member field. */
    String getName();

    /** Return the type of this member field. */
    SoyType getType();

    /** Return the field descriptor. */
    boolean hasField(Message proto);

    /**
     * An expression that reads this field from a protobuf in protoExpr.
     *
     * @param protoExpr an expression in the output language of backendKind
     *    that binds as tightly or more for the basic field lookup operators
     *    for that language.
     * @return an expression in the output language of backendKind that accesses the field.
     */
    String getAccessExpr(String protoExpr, SoyBackendKind backendKind);

    /**
     * Symbols required by {@link #getAccessExpr Access expression} that may not be in scope.
     *
     * @return Zero or more symbols in backend's output language.
     */
    ImmutableSet<String> getImportsForAccessExpr(SoyBackendKind backend);

    /**
     * Returns an appropriately typed {@link SoyValueProvider} for this field in the given message.
     */
    SoyValueProvider intepretField(SoyValueConverter converter, Message owningMessage);

    FieldDescriptor getDescriptor();
  }

  /** A member field of this proto type. */
  private class NormalField implements Field {
    protected final FieldDescriptor fieldDescriptor;
    protected final String name;
    // Lazily construct our impl to delay type lookups.  This enables us to resolve the types of
    // recursive proto definitions.
    @GuardedBy("this") private volatile FieldInterpreter interpreter;

    private NormalField(FieldDescriptor desc) {
      this.fieldDescriptor = desc;
      this.name = computeSoyName(desc);
    }

    private NormalField(String name) {
      this.fieldDescriptor = null;
      this.name = name;
    }

    /** Return the name of this member field. */
    @Override
    public final String getName() {
      return name;
    }

    /** Return the type of this member field. */
    @Override
    public SoyType getType() {
      return impl().type();
    }

    private FieldInterpreter impl() {
      @SuppressWarnings("GuardedBy")  // the checker can't prove Double checked locking safe
      FieldInterpreter local = interpreter;
      // Double checked locking
      if (local == null) {
        synchronized (this) {
          local = interpreter;
          if (local == null) {
            local = FieldInterpreter.create(typeRegistry, fieldDescriptor);
            this.interpreter = local;
          }
        }
      }
      return local;
    }

    /** Return the field descriptor. */
    @Override public boolean hasField(Message proto) {
      // TODO(lukes):  Currently we assume that a field is present if it is repeated, has an
      // explicit default value or is set.  However, the type of fields is not generally nullable,
      // so we can return null for a non-nullable field type.  Given the current ToFu implementation
      // this is not a problem, but modifying the field types to be nullable for non-repeated fields
      // with non explicit defaults should probably happen.
      return fieldDescriptor.isRepeated()
          || fieldDescriptor.hasDefaultValue()
          || proto.hasField(fieldDescriptor);
    }

    @Override public String getAccessExpr(String protoExpr, SoyBackendKind backendKind) {
      switch (backendKind) {
        case JS_SRC:
          String accessExpr =
              protoExpr + ".get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, name) + "()";

          // See whether we need to convert the value to a backend representation that is
          // compatible with Soy library code for that backend.
          String valueConverterFnName = getValueConverterFnName(backendKind);
          if (valueConverterFnName != null) {
            accessExpr = valueConverterFnName + "(" + accessExpr + ")";
          }

          return accessExpr;
        default:
          throw new UnsupportedOperationException();
      }
    }

    @Override public ImmutableSet<String> getImportsForAccessExpr(SoyBackendKind backend) {
      String valueConverterFnName = getValueConverterFnName(backend);
      if (valueConverterFnName != null) {
        return ImmutableSet.of(valueConverterFnName);
      }
      return ImmutableSet.<String>of();
    }

    /**
     * The name of a function that converts field values to soy-compatible values.
     *
     * @param backend The backend for which the caller is generating code.
     * @return A symbol in backend's output language or null if the identity
     *     conversion is appropriate.
     */
    @Nullable private String getValueConverterFnName(SoyBackendKind backend) {
      switch (backend) {
        case JS_SRC:
          switch (fieldDescriptor.getJavaType()) {
            case MESSAGE:
              String fieldTypeName = fieldDescriptor.getMessageType().getFullName();
              SanitizedType valueConverterOutputType =
                  SafeStringTypes.SAFE_STRING_PROTO_NAME_TO_SANITIZED_TYPE.get(fieldTypeName);
              if (valueConverterOutputType != null) {
                // E.g. soydata.unpackProtoToSanitizedHtml
                return "soydata.unpackProtoToSanitized"
                    + CaseFormat.UPPER_UNDERSCORE.to(
                        CaseFormat.UPPER_CAMEL,
                        valueConverterOutputType.getKind().name());
              }
              break;
          }
          break;
      }
      return null;
    }

    /**
     * Returns an appropriately typed {@link SoyValueProvider} for this field in the given message.
     */
    @Override public SoyValueProvider intepretField(SoyValueConverter converter, Message message) {
      return impl().intepretField(converter, message.getField(fieldDescriptor));
    }

    @Override public FieldDescriptor getDescriptor() {
      return fieldDescriptor;
    }
  }

  /** An extension field for this proto type. */
  private final class ExtensionField extends NormalField {
    ExtensionField(FieldDescriptor desc) {
      super(desc);
    }

    @Override public String getAccessExpr(String protoExpr, SoyBackendKind backendKind) {
      switch (backendKind) {
        case JS_SRC:
          return protoExpr + ".getExtension(" + getExtensionJsName() + ")";
        default:
          throw new UnsupportedOperationException();
      }
    }

    @Override public ImmutableSet<String> getImportsForAccessExpr(SoyBackendKind backend) {
      ImmutableSet.Builder<String> imports = ImmutableSet.<String>builder()
          .addAll(super.getImportsForAccessExpr(backend));
      switch (backend) {
        case JS_SRC:
          imports.add(JsQualifiedNameHelper.getImportForExtension(fieldDescriptor));
          break;

        case TOFU: {
          // Ideally we would just call MessageUtils.getExtensionField(descriptor) to get a Field
          // object. But we can't do that because this is run by the ParseInfoGenerator and that
          // doesn't necessarily have a classpath dependency on the protos, just a data dependency
          // on the descriptors.  So the classes may not exist.
          String extensionFieldName =
              JavaQualifiedNames.underscoresToCamelCase(fieldDescriptor.getName(), false);
          String extensionFieldHolderClassName;
          if (fieldDescriptor.getExtensionScope() != null) {
            extensionFieldHolderClassName =
                JavaQualifiedNames.getQualifiedName(fieldDescriptor.getExtensionScope());
          } else {
          // else we have a 'top level extension'
          extensionFieldHolderClassName =
            JavaQualifiedNames.getPackage(fieldDescriptor.getFile()) + "."
                + JavaQualifiedNames.getOuterClassname(fieldDescriptor.getFile());
          }
          imports.add(
              extensionFieldHolderClassName + "." + extensionFieldName + ".getDescriptor()");
          break;
        }
      }
      return imports.build();
    }

    private String getExtensionJsName() {
      return JsQualifiedNameHelper.getQualifiedExtensionName(fieldDescriptor);
    }
  }

  /**
   * It is possible for extensions to have conflicting names (since protos only care about tag
   * numbers).  However, for soy we need to identify fields by names not numbers.  This allows us to
   * represent such fields in the type but make it an error to access them.  In the future we may
   * want to add alternate mechanisms to access extensions that don't rely on field names solely.
   */
  private static final class AmbiguousFieldSet implements Field {
    private final ImmutableSet<String> fullFieldNames;
    private final ImmutableSet<ExtensionField> extensions;
    private final String name;

    AmbiguousFieldSet(String name, Set<ExtensionField> fields) {
      this.name = name;
      checkArgument(fields.size() > 1);
      this.extensions = ImmutableSet.copyOf(fields);

      ImmutableSet.Builder<String> builder = ImmutableSet.builder();
      for (ExtensionField field : fields) {
        builder.add(field.fieldDescriptor.getFullName());
      }
      fullFieldNames = builder.build();
    }

    @Override public boolean hasField(Message proto) {
      // We need to return true so that users observe failures instead of null.
      for (ExtensionField field : extensions) {
        if (field.hasField(proto)) {
          return true;
        }
      }
      return false;
    }

    @Override public SoyType getType() {
      throw failure();
    }

    @Override public FieldDescriptor getDescriptor() {
      throw failure();
    }

    @Override public SoyValueProvider intepretField(
        SoyValueConverter converter, Message owningMessage) {
      throw failure();
    }

    private RuntimeException failure() {
      return new IllegalStateException(
          String.format("Cannot access $%s. It may refer to any one of the following extensions, "
              + "and Soy doesn't have enough information to decide which.\n%s\nTo resolve ensure "
              + "that all extension fields accessed from soy have unique names.",
              name, fullFieldNames));
    }

    @Override public String getName() {
      return name;
    }

    @Override public String getAccessExpr(String protoExpr, SoyBackendKind backendKind) {
      throw failure();
    }

    @Override public ImmutableSet<String> getImportsForAccessExpr(SoyBackendKind backend) {
      throw failure();
    }
  }

  private final SoyTypeRegistry typeRegistry;
  private final Descriptor typeDescriptor;
  private final ImmutableMap<String, Field> fields;

  public SoyProtoTypeImpl(
      SoyTypeRegistry typeRegistry,
      Descriptor descriptor,
      Set<FieldDescriptor> extensions) {
    this.typeRegistry = typeRegistry;
    this.typeDescriptor = descriptor;
    ImmutableMap.Builder<String, Field> builder = ImmutableMap.builder();
    for (FieldDescriptor fieldDescriptor : descriptor.getFields()) {
      if (Protos.shouldJsIgnoreField(fieldDescriptor)) {
        continue;
      }
      NormalField field = new NormalField(fieldDescriptor);
      builder.put(field.getName(), field);
    }
    SetMultimap<String, ExtensionField> extensionsBySoyName = HashMultimap.create();
    for (FieldDescriptor extension : extensions) {
      ExtensionField field = new ExtensionField(extension);
      extensionsBySoyName.put(field.getName(), field);
    }
    for (Map.Entry<String, Set<ExtensionField>> group :
        Multimaps.asMap(extensionsBySoyName).entrySet()) {
      String fieldName = group.getKey();
      Set<ExtensionField> ambiguousFields = group.getValue();
      if (ambiguousFields.size() == 1) {
        builder.put(fieldName, Iterables.getOnlyElement(ambiguousFields));
      } else {
        AmbiguousFieldSet value = new AmbiguousFieldSet(fieldName, ambiguousFields);

        logger.severe(
            "Proto " + descriptor.getFullName()
            + " has multiple extensions with the name \"" + fieldName + "\": "
            + value.fullFieldNames + " this field will not be accessible from soy");
        builder.put(fieldName, value);
      }
    }
    fields = builder.build();
  }

  @Override public Kind getKind() {
    return Kind.OBJECT;
  }

  @Override public boolean isAssignableFrom(SoyType fromType) {
    return fromType == this;
  }

  @Override public boolean isInstance(SoyValue value) {
    return value instanceof Value
        && ((Value) value).getProto().getDescriptorForType() == typeDescriptor;
  }

  @Override public String getName() {
    return typeDescriptor.getFullName();
  }

  @Override public SoyType getFieldType(String fieldName) {
    Field field = fields.get(fieldName);
    return field != null ? field.getType() : null;
  }

  @Override public String getNameForBackend(SoyBackendKind backend) {
    switch (backend) {
      case JS_SRC:
        // The 'proto' prefix is JSPB-specific. If we ever support some other
        // JavaScript proto implementation, we'll need some way to determine which
        // proto implementation the user wants to use at this point.
        return JsQualifiedNameHelper.getQualifiedName(typeDescriptor);
      case TOFU:
        return JavaQualifiedNames.getQualifiedName(typeDescriptor) + ".getDescriptor()";
      case JBC_SRC:
        return JavaQualifiedNames.getClassName(typeDescriptor);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override public String getFieldAccessExpr(
      String fieldContainerExpr, String fieldName, SoyBackendKind backend) {
    Field field = fields.get(fieldName);
    return field != null ? field.getAccessExpr(fieldContainerExpr, backend) : null;
  }

  public FieldDescriptor getFieldDescriptor(String fieldName) {
    return fields.get(fieldName).getDescriptor();
  }

  @Override public String toString() {
    return getName();
  }

  @Override public ImmutableSet<String> getFieldAccessImports(
      String fieldName, SoyBackendKind backend) {
    Field field = fields.get(fieldName);
    if (field != null) {
      return field.getImportsForAccessExpr(backend);
    }
    return ImmutableSet.<String>of();
  }

  @Override public String getDescriptorExpression() {
    // We only need to import the outermost descriptor.
    Descriptor descriptor = typeDescriptor;
    while (descriptor.getContainingType() != null) {
      descriptor = descriptor.getContainingType();
    }
    return JavaQualifiedNames.getQualifiedName(descriptor) + ".getDescriptor()";
  }

  /**
   * Returns the {@link NormalField field metadata} for the given field.
   */
  @Nullable Field getField(String name) {
    return fields.get(name);
  }

  /**
   * Returns all the field names of this proto.
   */
  @Override public ImmutableSet<String> getFieldNames() {
    return fields.keySet();
  }

  private static String computeSoyName(FieldDescriptor field) {
    return CaseFormat.LOWER_UNDERSCORE.to(
        CaseFormat.LOWER_CAMEL, field.getName()) + fieldSuffix(field);
  }

  private static String fieldSuffix(FieldDescriptor field) {
    if (field.isRepeated()) {
      if (Protos.hasJsMapKey(field)) {
        return "Map";
      }
      return "List";
    } else {
      return "";
    }
  }
}
