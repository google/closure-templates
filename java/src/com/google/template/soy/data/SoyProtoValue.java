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

package com.google.template.soy.data;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.LegacyUnredactedTextFormat;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.internal.proto.Field;
import com.google.template.soy.internal.proto.Int64ConversionMode;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.jbcsrc.shared.Names;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Soy value that wraps a protocol buffer message object.
 *
 * <p>TODO(b/70906867): This implements SoyMap/SoyRecord for backwards compatibility. When Soy
 * initially added support for protos we implemented these interfaces to support using protos in
 * legacy untyped templates. This made it easier for teams to start passing protos to their
 * templates but has turned out to be a bad idea because it means your templates work differently in
 * javascript vs java. So now we continue to support these usecases but issue warnings when it
 * occurs. In the long run we will switch to either throwing exception or always returning null from
 * these methods.
 */
public final class SoyProtoValue extends SoyRecord {
  private static final Logger logger = Logger.getLogger(SoyProtoValue.class.getName());

  private static final class ProtoClass {
    final ImmutableMap<String, FieldWithInterpreter> fields;
    final Message defaultInstance;
    final String topLevelName;
    final String fullName;
    final String importPath;

    ProtoClass(Message defaultInstance, ImmutableMap<String, FieldWithInterpreter> fields) {
      this.fullName = defaultInstance.getDescriptorForType().getFullName();
      Descriptor d = defaultInstance.getDescriptorForType();
      while (d.getContainingType() != null) {
        d = d.getContainingType();
      }
      this.topLevelName = d.getName();
      this.importPath = defaultInstance.getDescriptorForType().getFile().getFullName();
      this.defaultInstance = checkNotNull(defaultInstance);
      this.fields = checkNotNull(fields);
    }
  }

  private static final class FieldWithInterpreter extends Field {
    @LazyInit ProtoFieldInterpreter interpreter;
    @LazyInit ProtoFieldInterpreter asGbigintInterpreter;
    @LazyInit ProtoFieldInterpreter asStringInterpreter;

    FieldWithInterpreter(FieldDescriptor fieldDesc) {
      super(fieldDesc);
    }

    private ProtoFieldInterpreter impl(Int64ConversionMode int64Mode) {
      ProtoFieldInterpreter local;
      switch (int64Mode) {
        case FOLLOW_JS_TYPE:
          local = interpreter;
          if (local == null) {
            local = ProtoFieldInterpreter.create(getDescriptor(), int64Mode);
            interpreter = local;
          }
          return local;
        case FORCE_GBIGINT:
          local = asGbigintInterpreter;
          if (local == null) {
            local = ProtoFieldInterpreter.create(getDescriptor(), int64Mode);
          }
          return local;
        case FORCE_STRING:
          local = asStringInterpreter;
          if (local == null) {
            local = ProtoFieldInterpreter.create(getDescriptor(), int64Mode);
          }
          return local;
      }

      throw new AssertionError();
    }

    private SoyValue interpretField(Message message, Int64ConversionMode int64Mode) {
      return impl(int64Mode).soyFromProto(message.getField(getDescriptor()));
    }

    public void assignField(Message.Builder builder, SoyValue value) {
      Int64ConversionMode int64Mode =
          getDescriptor().isMapField()
              ? Int64ConversionMode.FORCE_GBIGINT
              : Int64ConversionMode.FOLLOW_JS_TYPE;
      builder.setField(getDescriptor(), impl(int64Mode).protoFromSoy(value));
    }
  }

  private static final LoadingCache<Descriptor, ProtoClass> classCache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .build(
              new CacheLoader<>() {
                final Field.Factory<FieldWithInterpreter> factory = FieldWithInterpreter::new;

                @Override
                public ProtoClass load(Descriptor descriptor) throws Exception {
                  Set<FieldDescriptor> extensions = new LinkedHashSet<>();
                  return new ProtoClass(
                      getDefaultInstance(descriptor),
                      Field.getFieldsForType(descriptor, extensions, factory));
                }
              });

  private static Message getDefaultInstance(Descriptor key)
      throws ClassNotFoundException,
          IllegalAccessException,
          InvocationTargetException,
          NoSuchMethodException {
    Class<?> messageClass = Class.forName(JavaQualifiedNames.getClassName(key));
    return (Message) messageClass.getMethod("getDefaultInstance").invoke(null);
  }

  @Nonnull
  public static SoyProtoValue create(Message proto) {
    return new SoyProtoValue(proto);
  }

  /** The underlying proto message object. */
  private final Message proto;

  // lazily initialized
  private ProtoClass clazz;

  // This field is used by the Tofu renderer to tell the logging code where the access is, so that
  // the log lines have sufficient information.  For jbcsrc we can just log an exception since the
  // source location can be inferred from the stack trace.
  private Object locationKey;

  private SoyProtoValue(Message proto) {
    this.proto = checkNotNull(proto);
  }

  // lazy accessor for clazz, in jbcsrc we often will not need this metadata. so avoid calculating
  // it if we can
  private ProtoClass clazz() {
    ProtoClass localClazz = clazz;
    if (localClazz == null) {
      localClazz = classCache.getUnchecked(proto.getDescriptorForType());
      clazz = localClazz;
    }
    return localClazz;
  }

  /** Returns the underlying message. */
  @Nonnull
  @Override
  public Message getProto() {
    return proto;
  }

  public SoyValue getProtoField(String name) {
    return getProtoField(name, Int64ConversionMode.FOLLOW_JS_TYPE);
  }

  public SoyValue getProtoField(String name, Int64ConversionMode int64Mode) {
    FieldWithInterpreter field = clazz().fields.get(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    FieldDescriptor fd = field.getDescriptor();
    if (!fd.isRepeated() && fd.getJavaType() == JavaType.MESSAGE && !proto.hasField(fd)) {
      // Unset singular message fields are always null to match JSPB semantics.
      return UndefinedData.INSTANCE;
    }
    return field.interpretField(proto, int64Mode);
  }

  public SoyValue getReadonlyProtoField(String name) {
    FieldWithInterpreter field = clazz().fields.get(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    FieldDescriptor fd = field.getDescriptor();
    if (fd.isRepeated() || fd.getJavaType() != JavaType.MESSAGE) {
      throw new AssertionError("impossible");
    }
    return field.interpretField(proto, Int64ConversionMode.FOLLOW_JS_TYPE);
  }

  /**
   * Returns the value of the field, or null only if the field has presence semantics and is unset.
   * For fields with no presence semantics (i.e., there's no hasser method), the value is never
   * null.
   */
  public SoyValue getProtoFieldOrNull(String name) {
    return getProtoFieldOrNull(name, Int64ConversionMode.FOLLOW_JS_TYPE);
  }

  /**
   * Returns the value of the field, or null only if the field has presence semantics and is unset.
   * For fields with no presence semantics (i.e., there's no hasser method), the value is never
   * null.
   */
  public SoyValue getProtoFieldOrNull(String name, Int64ConversionMode int64Mode) {
    FieldWithInterpreter field = clazz().fields.get(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    FieldDescriptor fd = field.getDescriptor();
    if (fd.hasPresence() && !proto.hasField(fd)) {
      return UndefinedData.INSTANCE;
    }
    return field.interpretField(proto, int64Mode);
  }

  public boolean hasProtoField(String name) {
    FieldWithInterpreter field = clazz().fields.get(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    if (field.getDescriptor().isRepeated()) {
      // Compiler should prevent this from happening.
      throw new IllegalArgumentException("Cannot check for presence on repeated field " + name);
    } else {
      return proto.hasField(field.getDescriptor());
    }
  }

  public void setAccessLocationKey(Object location) {
    this.locationKey = location;
  }

  // -----------------------------------------------------------------------------------------------
  // SoyRecord.

  @Deprecated
  @Override
  public boolean hasField(RecordProperty name) {
    asRecord();
    return false;
  }

  @Deprecated
  @Override
  public SoyValue getField(RecordProperty name) {
    asRecord();
    return null;
  }

  @Deprecated
  @Override
  public SoyValueProvider getFieldProvider(RecordProperty name) {
    asRecord();
    return null;
  }

  @Override
  public ImmutableMap<String, SoyValueProvider> recordAsMap() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void forEach(BiConsumer<RecordProperty, ? super SoyValueProvider> action) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int recordSize() {
    throw new UnsupportedOperationException();
  }

  // -----------------------------------------------------------------------------------------------
  // SoyLegacyObjectMap.

  @Deprecated
  @Override
  public int getItemCnt() {
    asMap();
    return super.getItemCnt();
  }

  @Deprecated
  @Override
  public Iterable<? extends SoyValue> getItemKeys() {
    asMap();
    return super.getItemKeys();
  }

  @Deprecated
  @Override
  public boolean hasItem(SoyValue key) {
    asMap();
    return super.hasItem(key);
  }

  @Deprecated
  @Override
  public SoyValue getItem(SoyValue key) {
    asMap();
    return super.getItem(key);
  }

  @Deprecated
  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    asMap();
    return super.getItemProvider(key);
  }

  private void asMap() {
    asDeprecatedType("map");
  }

  private void asRecord() {
    asDeprecatedType("record");
  }

  private void asDeprecatedType(String type) {
    Object locationKey = getAndClearLocationKey();
    ProtoClass clazz = clazz();
    // TODO(lukes): consider throwing an exception here, this would be inconsistent withh JS but
    // would be more useful.
    if (locationKey == null) {
      // if there is no locationKey (i.e. this is jbcsrc), then we will use a stack trace
      Exception e = new Exception("bad proto access");
      Names.rewriteStackTrace(e);
      logger.log(
          Level.SEVERE,
          String.format(
              "Accessing a proto of type %s (import {%s} from '%s';) as a %s is deprecated. Add"
                  + " static types to fix."
              ,
              clazz.fullName, clazz.topLevelName, clazz.importPath, type),
          e);
    } else {
      // if there is a locationKey (i.e. this is tofu), then we will use the location key
      logger.log(
          Level.SEVERE,
          String.format(
              "Accessing a proto of type %s (import {%s} from '%s';) as a %s is deprecated. Add"
                  + " static types to fix."
                  + "\n\t%s",
              clazz.fullName, clazz.topLevelName, clazz.importPath, type, locationKey),
          new Exception("bad proto access @" + locationKey));
    }
  }

  private Object getAndClearLocationKey() {
    Object key = locationKey;
    if (key != null) {
      locationKey = null;
    }
    return key;
  }

  // -----------------------------------------------------------------------------------------------
  // SoyValue.

  @Override
  public boolean equals(Object other) {
    return other != null
        && this.getClass() == other.getClass()
        // Use identity for js compatibility
        && this.proto == ((SoyProtoValue) other).proto;
  }

  @Override
  public int hashCode() {
    return this.proto.hashCode();
  }

  @Override
  public String coerceToString() {
    // TODO(gboyer): Make this consistent with JavaScript.
    // TODO(gboyer): Respect ProtoUtils.shouldJsIgnoreField(...)?
    return LegacyUnredactedTextFormat.legacyUnredactedToString(proto);
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    TextFormat.printer().print(proto, appendable);
  }

  @SuppressWarnings("ReturnValueIgnored")
  @Override
  public SoyValue checkNullishProto(Class<? extends Message> messageType) {
    messageType.cast(proto);
    return this;
  }

  @Override
  public boolean isProtoInstance(Class<? extends Message> messageType) {
    return messageType.isInstance(proto);
  }

  // -----------------------------------------------------------------------------------------------
  // Object.

  /**
   * Returns a string that indicates the type of proto held, to assist in debugging Soy type errors.
   */
  @Override
  public String toString() {
    return String.format(
        "SoyProtoValue<%s, %s>",
        proto.getDescriptorForType().getFullName(),
        proto.equals(proto.getDefaultInstanceForType())
            ? (proto == proto.getDefaultInstanceForType() ? "empty(default)" : "empty")
            : "not-empty");
  }

  /**
   * Provides an interface for constructing a SoyProtoValue. Used by the tofu renderer only.
   *
   * <p>Not for use by Soy users.
   */
  public static final class Builder {
    private final ProtoClass clazz;
    private Message.Builder builder;

    public Builder(Descriptor soyProto) {
      this.clazz = classCache.getUnchecked(soyProto);
    }

    private Message.Builder builder() {
      Message.Builder localBuilder = builder;
      if (localBuilder == null) {
        localBuilder = this.builder = clazz.defaultInstance.newBuilderForType();
      }
      return localBuilder;
    }

    @CanIgnoreReturnValue
    public Builder setField(String field, SoyValue value) {
      clazz.fields.get(field).assignField(builder(), value);
      return this;
    }

    public SoyProtoValue build() {
      SoyProtoValue soyProtoValue =
          new SoyProtoValue(builder == null ? clazz.defaultInstance : builder.build());
      soyProtoValue.clazz = clazz;
      return soyProtoValue;
    }

    public boolean hasField(String field) {
      return clazz.fields.containsKey(field);
    }
  }

  @Override
  public String getSoyTypeName() {
    return ProtoUtils.getProtoName(proto);
  }
}
