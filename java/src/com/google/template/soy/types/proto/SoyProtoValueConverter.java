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

import com.google.protobuf.Message;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import javax.inject.Inject;

/**
 * Custom data converter for protocol buffer message types.
 *
 */
// TODO(user): Will be removed shortly. Please use SoyFileSet.Builder#addProtoDescriptors to add
// descriptors directly to SoyFileSet.Builder.
public final class SoyProtoValueConverter implements SoyCustomValueConverter {

  @Inject
  public SoyProtoValueConverter() {}

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
      return SoyProtoValueImpl.create(message);
    }
    return null;
  }
}
