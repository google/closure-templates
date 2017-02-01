/*
 * Copyright 2013 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.types.SoyTypeRegistry;
import javax.inject.Inject;

/**
 * Custom data converter for protocol buffer message types.
 *
 */
public final class SoyProtoValueConverter implements SoyCustomValueConverter {
  private final SoyTypeRegistry registry;
  private final SoyProtoTypeProvider protoTypeProvider;

  @VisibleForTesting
  public SoyProtoValueConverter() {
    this(new SoyTypeRegistry(), SoyProtoTypeProvider.empty());
  }

  @Inject
  SoyProtoValueConverter(SoyTypeRegistry registry, SoyProtoTypeProvider protoTypeProvider) {
    this.registry = registry;
    this.protoTypeProvider = protoTypeProvider;
  }

  @Override
  public SoyValueProvider convert(SoyValueConverter valueConverter, Object obj) {
    if (obj instanceof Message.Builder) {
      // Eagerly convert MessageBuilders into Messages.
      // This requires eagerly copying the entire proto at the moment, but allowing Builders
      // directly in SoyProtoValueImpl slightly increases the risk of threading issues.
      obj = ((Message.Builder) obj).build();
    }

    // Special case for protos that encode typed strings, instead of treating them as records.
    SoyValueProvider safeStringProvider = SafeStringTypes.convertToSoyValue(obj);
    if (safeStringProvider != null) {
      return safeStringProvider;
    }

    if (obj instanceof Message) {
      Message message = (Message) obj;
      // We can't just fetch the type from the type registry because it is possible that this type
      // was not part of the statically registered set.  So instead we use this internal helper to
      // fetch a type given a descriptor which will definitely work.
      SoyProtoType type = protoTypeProvider.getType(message.getDescriptorForType(), registry);
      return new SoyProtoValueImpl(valueConverter, type, message);
    }
    if (obj instanceof ByteString) {
      // Encode proto byte fields as base-64 since that is a safe and consistent way to send them
      // to Javascript.  Note that we must check this here without using custom value converter,
      // because ByteString implements Iterable<Byte>, which SoyValueConverter tries and fails to
      // convert to a list.
      return StringData.forValue(BaseEncoding.base64().encode(((ByteString) obj).toByteArray()));
    }
    // NOTE: Enum values can come in different flavors, depending on whether it was obtained
    // via reflection (as it is in SoyProtoValue) or obtained by just getting the enum value
    // if passed directly to Java.
    if (obj instanceof EnumValueDescriptor) {
      return IntegerData.forValue(((EnumValueDescriptor) obj).getNumber());
    }
    if (obj instanceof ProtocolMessageEnum) {
      return IntegerData.forValue(((ProtocolMessageEnum) obj).getNumber());
    }
    return null;
  }
}
