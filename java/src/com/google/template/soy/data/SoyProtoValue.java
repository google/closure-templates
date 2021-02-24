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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.proto.Field;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.jbcsrc.shared.Names;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 *
 */
public final class SoyProtoValue extends SoyAbstractValue implements SoyLegacyObjectMap, SoyRecord {
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

    FieldWithInterpreter(FieldDescriptor fieldDesc) {
      super(fieldDesc);
    }

    private ProtoFieldInterpreter impl() {
      ProtoFieldInterpreter local = interpreter;
      if (local == null) {
        local = ProtoFieldInterpreter.create(getDescriptor());
      }
      return local;
    }

    public SoyValue interpretField(Message message) {
      return impl().soyFromProto(message.getField(getDescriptor()));
    }

    public void assignField(Message.Builder builder, SoyValue value) {
      builder.setField(getDescriptor(), impl().protoFromSoy(value));
    }

    boolean hasField(Message proto) {
      // TODO(lukes):  Currently we assume that a field is present if it is repeated, has an
      // explicit default value or is set.  However, the type of fields is not generally nullable,
      // so we can return null for a non-nullable field type.  Given the current ToFu implementation
      // this is not a problem, but modifying the field types to be nullable for non-repeated fields
      // with non explicit defaults should probably happen.
      return !shouldCheckFieldPresenceToEmulateJspbNullability() || proto.hasField(getDescriptor());
    }
  }

  private static final LoadingCache<Descriptor, ProtoClass> classCache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .build(
              new CacheLoader<Descriptor, ProtoClass>() {
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
      throws ClassNotFoundException, IllegalAccessException, InvocationTargetException,
          NoSuchMethodException {
    Class<?> messageClass = Class.forName(JavaQualifiedNames.getClassName(key));
    return (Message) messageClass.getMethod("getDefaultInstance").invoke(null);
  }

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
  public Message getProto() {
    return proto;
  }

  /**
   * Gets a value for the field for the underlying proto object. Not intended for general use.
   *
   * @param name The proto field name.
   * @return The value of the given field for the underlying proto object, or NullData if either the
   *     field does not exist or the value is not set in the underlying proto (according to the jspb
   *     semantics)
   */
  public SoyValue getProtoField(String name) {
    return getProtoField(name, /* useBrokenProtoSemantics= */ true);
  }

  public SoyValue getProtoField(String name, boolean useBrokenProtoSemantics) {
    FieldWithInterpreter field = clazz().fields.get(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    if (useBrokenProtoSemantics
        && field.shouldCheckFieldPresenceToEmulateJspbNullability()
        && !proto.hasField(field.getDescriptor())) {
      return NullData.INSTANCE;
    }
    return field.interpretField(proto);
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
  public boolean hasField(String name) {
    if (asRecord()) {
      return doHasField(name);
    }
    return false;
  }

  private boolean doHasField(String name) {
    // TODO(user): hasField(name) should really be two separate checks:
    // if (type.getField(name) == null) { throw new IllegalArgumentException(); }
    // if (!type.getField(name).hasField(proto)) { return null; }
    FieldWithInterpreter field = clazz().fields.get(name);
    if (field == null) {
      return false;
    }
    return field.hasField(proto);
  }

  @Deprecated
  @Override
  public SoyValue getField(String name) {
    if (asRecord()) {
      return doGetField(name);
    }
    return null;
  }

  private SoyValue doGetField(String name) {
    SoyValueProvider valueProvider = doGetFieldProvider(name);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }

  @Deprecated
  @Override
  public SoyValueProvider getFieldProvider(String name) {
    if (asRecord()) {
      return doGetFieldProvider(name);
    }
    return null;
  }

  private SoyValueProvider doGetFieldProvider(final String name) {
    if (!doHasField(name)) {
      // jspb implements proto.getUnsetField() incorrectly. It should return default value for the
      // type (0, "", etc.), but jspb returns null instead. We follow jspb semantics, so return null
      // here, and the value will be converted to NullData higher up the chain.
      return null;
    }

    return clazz().fields.get(name).interpretField(proto).resolve();
  }

  @Override
  public final ImmutableMap<String, SoyValueProvider> recordAsMap() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void forEach(BiConsumer<String, ? super SoyValueProvider> action) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int recordSize() {
    throw new UnsupportedOperationException();
  }

  // -----------------------------------------------------------------------------------------------
  // SoyMap.

  @Deprecated
  @Override
  public int getItemCnt() {
    return getItemKeys().size();
  }

  @Deprecated
  @Override
  public Collection<SoyValue> getItemKeys() {
    if (asMap()) {
      // We allow iteration over keys for reflection, to support existing templates that require
      // this. We don't guarantee that this will be particularly fast (e.g. by caching) to avoid
      // slowing down the common case of field access. This basically goes over all possible keys,
      // but filters ones that need to be ignored or lack a suitable value.
      ImmutableList.Builder<SoyValue> builder = ImmutableList.builder();
      for (String key : clazz().fields.keySet()) {
        if (doHasField(key)) {
          builder.add(StringData.forValue(key));
        }
      }
      return builder.build();
    }
    return ImmutableList.of();
  }

  @Deprecated
  @Override
  public boolean hasItem(SoyValue key) {
    if (asMap()) {
      return doHasField(key.stringValue());
    }
    return false;
  }

  @Deprecated
  @Override
  public SoyValue getItem(SoyValue key) {
    if (asMap()) {
      return doGetField(key.stringValue());
    }
    return null;
  }

  @Deprecated
  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    if (asMap()) {
      return doGetFieldProvider(key.stringValue());
    }
    return null;
  }

  @CheckReturnValue
  private boolean asMap() {
    return asDeprecatedType("map");
  }

  @CheckReturnValue
  private boolean asRecord() {
    return asDeprecatedType("record");
  }

  @CheckReturnValue
  private boolean asDeprecatedType(String type) {
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
    return Flags.allowReflectiveProtoAccess();
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
  public boolean coerceToBoolean() {
    return true; // matches JS behavior
  }

  @Override
  public String coerceToString() {
    // TODO(gboyer): Make this consistent with JavaScript.
    // TODO(gboyer): Respect ProtoUtils.shouldJsIgnoreField(...)?
    return proto.toString();
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    TextFormat.print(proto, appendable);
  }

  // -----------------------------------------------------------------------------------------------
  // Object.

  /**
   * Returns a string that indicates the type of proto held, to assist in debugging Soy type errors.
   */
  @Override
  public String toString() {
    return String.format("SoyProtoValue<%s>", proto.getDescriptorForType().getFullName());
  }

  @Override
  public int hashCode() {
    return this.proto.hashCode();
  }

  /**
   * Provides an interface for constructing a SoyProtoValue. Used by the tofu renderer only.
   *
   * <p>Not for use by Soy users.
   */
  public static final class Builder {
    private final ProtoClass clazz;
    private final Message.Builder builder;

    public Builder(Descriptor soyProto) {
      this.clazz = classCache.getUnchecked(soyProto);
      this.builder = clazz.defaultInstance.newBuilderForType();
    }

    public Builder setField(String field, SoyValue value) {
      clazz.fields.get(field).assignField(builder, value);
      return this;
    }

    public SoyProtoValue build() {
      SoyProtoValue soyProtoValue = new SoyProtoValue(builder.build());
      soyProtoValue.clazz = clazz;
      return soyProtoValue;
    }
  }
}
