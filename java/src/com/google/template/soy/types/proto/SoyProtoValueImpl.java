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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Soy value that wraps a protocol buffer message object.
 *
 * <p>This implements SoyMap to deal with some uses that require "reflecting" over Soy proto fields.
 * However, this usage is deprecated and will be unsupported when static type checking is used,
 * since it doesn't work with Javascript, which does not support reflection.
 *
 */
public final class SoyProtoValueImpl extends SoyAbstractValue implements SoyProtoValue, SoyMap {
  private static final class ProtoClass {
    final ImmutableMap<String, Field> fields;
    final Message defaultInstance;

    ProtoClass(Message defaultInstance, ImmutableMap<String, Field> fields) {
      this.defaultInstance = checkNotNull(defaultInstance);
      this.fields = checkNotNull(fields);
    }
  }

  private static final LoadingCache<Descriptor, ProtoClass> classCache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .build(
              new CacheLoader<Descriptor, ProtoClass>() {
                @Override
                public ProtoClass load(Descriptor descriptor) throws Exception {
                  Set<FieldDescriptor> extensions = new LinkedHashSet<>();
                  return new ProtoClass(
                      getDefaultInstance(descriptor),
                      Field.getFieldsForType(/* typeRegistry= */ null, descriptor, extensions));
                }
              });

  private static Message getDefaultInstance(Descriptor key)
      throws ClassNotFoundException, IllegalAccessException, InvocationTargetException,
          NoSuchMethodException {
    Class<?> messageClass = Class.forName(JavaQualifiedNames.getClassName(key));
    return (Message) messageClass.getMethod("getDefaultInstance").invoke(null);
  }

  public static SoyProtoValueImpl create(Message proto) {
    return new SoyProtoValueImpl(proto);
  }

  /** The underlying proto message object. */
  private final Message proto;

  // lazily initialized
  private ProtoClass clazz;

  private SoyProtoValueImpl(Message proto) {
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

  // -----------------------------------------------------------------------------------------------
  // SoyProtoValue.

  /** Returns the underlying message. */
  @Override
  public Message getProto() {
    return proto;
  }

  @Override
  public SoyValue getProtoField(String name) {
    Field field = clazz().fields.get(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    if (field.shouldCheckFieldPresenceToEmulateJspbNullability()
        && !proto.hasField(field.getDescriptor())) {
      return NullData.INSTANCE;
    }
    return field.interpretField(proto).resolve();
  }

  // -----------------------------------------------------------------------------------------------
  // SoyRecord.

  @Deprecated
  @Override
  // TODO(user): Issue warning for people who are running compilation that uses SoyRecord methods
  public boolean hasField(String name) {
    // TODO(user): hasField(name) should really be two separate checks:
    // if (type.getField(name) == null) { throw new IllegalArgumentException(); }
    // if (!type.getField(name).hasField(proto)) { return null; }
    Field field = clazz().fields.get(name);
    if (field == null) {
      return false;
    }
    return field.hasField(proto);
  }

  @Deprecated
  @Override
  // TODO(user): Issue warning for people who are running compilation that uses SoyRecord methods
  public SoyValue getField(String name) {
    SoyValueProvider valueProvider = getFieldProvider(name);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }

  @Deprecated
  @Override
  // TODO(user): Issue warning for people who are running compilation that uses SoyRecord methods
  public SoyValueProvider getFieldProvider(String name) {
    return getFieldProviderInternal(name);
  }

  private SoyValueProvider getFieldProviderInternal(final String name) {
    if (!hasField(name)) {
      // jspb implements proto.getUnsetField() incorrectly. It should return default value for the
      // type (0, "", etc.), but jspb returns null instead. We follow jspb semantics, so return null
      // here, and the value will be converted to NullData higher up the chain.
      return null;
    }

    return clazz().fields.get(name).interpretField(proto).resolve();
  }

  // -----------------------------------------------------------------------------------------------
  // SoyMap.

  @Override
  public int getItemCnt() {
    return getItemKeys().size();
  }

  @Override
  public Collection<SoyValue> getItemKeys() {
    // We allow iteration over keys for reflection, to support existing templates that require
    // this. We don't guarantee that this will be particularly fast (e.g. by caching) to avoid
    // slowing down the common case of field access. This basically goes over all possible keys,
    // but filters ones that need to be ignored or lack a suitable value.
    ImmutableList.Builder<SoyValue> builder = ImmutableList.builder();
    for (String key : clazz().fields.keySet()) {
      if (hasField(key)) {
        builder.add(StringData.forValue(key));
      }
    }
    return builder.build();
  }

  @Override
  public boolean hasItem(SoyValue key) {
    return hasField(key.stringValue());
  }

  @Override
  public SoyValue getItem(SoyValue key) {
    return getField(key.stringValue());
  }

  @Override
  public SoyValueProvider getItemProvider(SoyValue key) {
    return getFieldProvider(key.stringValue());
  }

  // -----------------------------------------------------------------------------------------------
  // SoyValue.

  @Override
  public boolean equals(Object other) {
    return other != null
        && this.getClass() == other.getClass()
        // Use identity for js compatibility
        && this.proto == ((SoyProtoValueImpl) other).proto;
  }

  @Override
  public boolean coerceToBoolean() {
    return true; // matches JS behavior
  }

  @Override
  public String coerceToString() {
    // TODO(gboyer): Make this consistent with Javascript or AbstractMap.
    // TODO(gboyer): Respect ProtoUtils.shouldJsIgnoreField(...)?
    return proto.toString();
  }

  @Override
  public void render(Appendable appendable) throws IOException {
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
   * Provides an interface for constructing a SoyProtoValueImpl. Used by the tofu renderer only.
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

    public SoyProtoValueImpl build() {
      SoyProtoValueImpl soyProtoValueImpl = new SoyProtoValueImpl(builder.build());
      soyProtoValueImpl.clazz = clazz;
      return soyProtoValueImpl;
    }
  }
}
