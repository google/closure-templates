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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.template.soy.data.SoyAbstractCachingRecord;
import com.google.template.soy.data.SoyAbstractCachingValueProvider;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;

import java.io.IOException;
import java.util.Collection;

/**
 * Soy value that wraps a protocol buffer message object.
 *
 * <p>This also implements SoyMap to deal with some uses that require "reflecting" over Soy proto
 * fields. However, this usage is deprecated and will be unsupported when static type checking is
 * used, since it doesn't work with Javascript, which does not support reflection.
 *
 */
public final class SoyProtoValue extends SoyAbstractCachingRecord
    implements SoyProtoTypeImpl.Value, SoyMap {

  /** The value converter to use for internal conversions. */
  private final SoyValueConverter valueConverter;

  /** The underlying proto message object. */
  private final Message proto;

  private final SoyProtoTypeImpl type;

  /**
   * @param valueConverter The value converter to use for internal conversions.
   * @param proto The proto message object.
   */
  SoyProtoValue(SoyValueConverter valueConverter, SoyProtoTypeImpl type, Message proto) {
    this.valueConverter = checkNotNull(valueConverter);
    this.proto = checkNotNull(proto);
    this.type = checkNotNull(type);
  }

  @Override public boolean hasField(String name) {
    SoyProtoTypeImpl.Field fieldDesc = type.getField(name);
    if (fieldDesc == null) {
      return false;
    }
    return fieldDesc.hasField(proto);
  }

  @Override public SoyValueProvider getFieldProviderInternal(final String name) {
    if (!hasField(name)) {
      return null;
    }
    return new SoyAbstractCachingValueProvider() {
      @Override protected SoyValue compute() {
        return type.getField(name).intepretField(valueConverter, proto).resolve();
      }

      @Override public RenderResult status() {
        return RenderResult.done();
      }
    };
  }

  @Override public boolean hasItem(SoyValue key) {
    return hasField(key.stringValue());
  }

  @Override public SoyValueProvider getItemProvider(SoyValue key) {
    return getFieldProvider(key.stringValue());
  }

  @Override public SoyValue getItem(SoyValue key) {
    return getField(key.stringValue());
  }

  @Override public Collection<SoyValue> getItemKeys() {
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

  @Override public int getItemCnt() {
    return getItemKeys().size();
  }

  @Override public boolean equals(Object other) {
    return other != null && this.getClass() == other.getClass()
        // Use identity for js compatibility
        && this.proto == ((SoyProtoValue) other).proto;
  }

  @Override public int hashCode() {
    return this.proto.hashCode();
  }

  @Override public boolean coerceToBoolean() {
    return true;  // matches JS behavior
  }

  @Override public void render(Appendable appendable) throws IOException {
    TextFormat.print(proto, appendable);
  }

  @Override public String coerceToString() {
    // TODO(gboyer): Make this consistent with Javascript or AbstractMap.
    // TODO(gboyer): Respect Protos.shouldJsIgnoreField(...)?
    return proto.toString();
  }

  /**
   * Returns the underlying message.
   */
  @Override public Message getProto() {
    return proto;
  }

  /**
   * Returns a string that indicates the type of proto held, to assist in debugging Soy type errors.
   */
  @Override
  public String toString() {
    return String.format("SoyProtoValue<%s>", proto.getDescriptorForType().getFullName());
  }
}
