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
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.ProtocolMessageEnum;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.types.SoyTypeRegistry;

import java.lang.reflect.Modifier;
import java.util.Map;

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

  @Inject SoyProtoValueConverter(SoyTypeRegistry registry, SoyProtoTypeProvider protoTypeProvider) {
    this.registry = registry;
    this.protoTypeProvider = protoTypeProvider;
  }

  @Override public SoyValueProvider convert(SoyValueConverter valueConverter, Object obj) {
    if (obj instanceof Message.Builder) {
      // Eagerly convert MessageBuilders into Messages.
      // This requires eagerly copying the entire proto at the moment, but allowing Builders
      // directly in SoyProtoValue slightly increases the risk of threading issues.
      obj = ((Message.Builder) obj).build();
    }

    // Special case for protos that encode typed strings, instead of treating them as records.
    SoyValueProvider valueProvider = tryToConvertToValueType(obj);
    if (valueProvider != null) {
      return valueProvider;
    }

    if (obj instanceof Message) {
      Message message = (Message) obj;
      // We can't just fetch the type from the type registry because it is possible that this type
      // was not part of the statically registered set.  So instead we use this internal helper to
      // fetch a type given a descriptor which will definitely work.
      SoyProtoTypeImpl type = protoTypeProvider.getType(message.getDescriptorForType(), registry);
      return new SoyProtoValue(valueConverter, type, message);
    }
    if (obj instanceof ByteString) {
      // Encode proto byte fields as base-64 since that is a safe and consistent way to send them
      // to Javascript.  Note that we must check this here without using custom value converter,
      // because ByteString implements Iterable<Byte>, which SoyValueHelper tries and fails to
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


  // This class is named ProtoValueConverter, but the map below includes entries for non-protobuf
  // classes because splitting them up would lead to a brittle interface if clients included one
  // converter but not the other.
  // We also don't want to do Safe* to SanitizedContent conversion in a separate converters because
  // then clients would have to include converters in a specific order.
  // TODO(msamuel): Maybe rename this class so that readers do not conclude incorrectly that it
  // only converts protocol buffers.

  /** Map concrete classes to converters to value types to avoid long if (instanceof) chains. */
  private static final
  Map<Class<?>, Function<? super Object, ? extends SoyValueProvider>> CONVERTER_FOR_VALUE_CLASS =
      ImmutableMap.<Class<?>, Function<? super Object, ? extends SoyValueProvider>>builder()
      .put(
          SafeHtml.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeHtml((SafeHtml) soyInput);
            }
          })
      .put(
          SafeHtmlProto.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeHtmlProto((SafeHtmlProto) soyInput);
            }
          })
      .put(
          SafeScript.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeScript((SafeScript) soyInput);
            }
          })
      .put(
          SafeScriptProto.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeScriptProto((SafeScriptProto) soyInput);
            }
          })
      .put(
          SafeStyle.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeStyle((SafeStyle) soyInput);
            }
          })
      .put(
          SafeStyleProto.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeStyleProto((SafeStyleProto) soyInput);
            }
          })
      .put(
          SafeStyleSheet.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeStyleSheet((SafeStyleSheet) soyInput);
            }
          })
      .put(
          SafeStyleSheetProto.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeStyleSheetProto((SafeStyleSheetProto) soyInput);
            }
          })
      .put(
          SafeUrl.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeUrl((SafeUrl) soyInput);
            }
          })
      .put(
          SafeUrlProto.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromSafeUrlProto((SafeUrlProto) soyInput);
            }
          })
      .put(
          TrustedResourceUrl.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromTrustedResourceUrl((TrustedResourceUrl) soyInput);
            }
          })
      .put(
          TrustedResourceUrlProto.class,
          new Function<Object, SoyValueProvider>() {
            @Override public SoyValueProvider apply(Object soyInput) {
              return SanitizedContents.fromTrustedResourceUrlProto(
                  (TrustedResourceUrlProto) soyInput);
            }
          })
      .build();


  static {
    // If our class lookup table includes classes that can be extended,
    // then the .get(...) call below may spuriously return null.
    for (Class<?> cl : CONVERTER_FOR_VALUE_CLASS.keySet()) {
      if (!Modifier.isFinal(cl.getModifiers())) {
        throw new AssertionError(
            cl + " is not final so there might be subclasses that aren't keyed in this map");
      }
    }
  }


  private static SoyValueProvider tryToConvertToValueType(Object obj) {
    if (obj == null) {
      return null;
    }
    Function<? super Object, ? extends SoyValueProvider> converter;
    converter = CONVERTER_FOR_VALUE_CLASS.get(obj.getClass());
    if (converter != null) {
      return converter.apply(obj);
    }
    return null;
  }
}
