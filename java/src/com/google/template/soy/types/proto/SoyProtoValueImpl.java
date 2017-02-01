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
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SoyAbstractValue;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.Collection;

/**
 * Soy value that wraps a protocol buffer message object.
 *
 * <p>This implements SoyMap to deal with some uses that require "reflecting" over Soy proto fields.
 * However, this usage is deprecated and will be unsupported when static type checking is used,
 * since it doesn't work with Javascript, which does not support reflection.
 *
 */
public final class SoyProtoValueImpl extends SoyAbstractValue implements SoyProtoValue, SoyMap {

  /** The value converter to use for internal conversions. */
  private final SoyValueConverter valueConverter;

  /** The underlying proto message object. */
  private final Message proto;

  /** SoyType of the proto message object. */
  private final SoyProtoType type;

  /**
   * @param valueConverter The value converter to use for internal conversions.
   * @param type The SoyType of the proto.
   * @param proto The proto message object.
   */
  SoyProtoValueImpl(SoyValueConverter valueConverter, SoyProtoType type, Message proto) {
    this.valueConverter = checkNotNull(valueConverter);
    this.type = checkNotNull(type);
    this.proto = checkNotNull(proto);
  }

  // -----------------------------------------------------------------------------------------------
  // SoyProtoValue.

  /** Returns the underlying message. */
  @Override
  public Message getProto() {
    return proto;
  }

  @Override
  public boolean hasProtoField(String name) {
    Field field = type.getField(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    return field.hasField(proto);
  }

  @Override
  public SoyValue getProtoField(String name) {
    Field field = type.getField(name);
    if (field == null) {
      throw new IllegalArgumentException(
          "Proto " + proto.getClass().getName() + " does not have a field of name " + name);
    }
    return field.interpretField(valueConverter, proto).resolve();
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
    Field field = type.getField(name);
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

    return type.getField(name).interpretField(valueConverter, proto).resolve();
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
    for (String key : type.getFieldNames()) {
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
    // TODO(gboyer): Respect Protos.shouldJsIgnoreField(...)?
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
    private static final LoadingCache<SoyProtoType, Message> defaultInstanceForType =
        CacheBuilder.newBuilder()
            .weakKeys()
            .build(
                new CacheLoader<SoyProtoType, Message>() {
                  @Override
                  public Message load(SoyProtoType key) throws Exception {
                    // every proto has a public static method getDefaultInstance() which returns
                    // the default empty instance
                    String nameForBackend = key.getNameForBackend(SoyBackendKind.TOFU);
                    Class<?> messageClass = Class.forName(nameForBackend);
                    return (Message) messageClass.getMethod("getDefaultInstance").invoke(null);
                  }
                });

    private final SoyProtoType soyProto;
    private final Message.Builder builder;
    private final SoyValueConverter valueConverter;

    public Builder(SoyValueConverter valueConverter, SoyProtoType soyProto) {
      this.valueConverter = valueConverter;
      this.soyProto = soyProto;
      this.builder = defaultInstanceForType.getUnchecked(soyProto).newBuilderForType();
    }

    public Builder setField(String field, SoyValue value) {
      soyProto.getField(field).assignField(builder, value);
      return this;
    }

    public SoyProtoValueImpl build() {
      return new SoyProtoValueImpl(valueConverter, soyProto, builder.build());
    }
  }
}
