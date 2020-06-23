/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.logging;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.io.CharSource;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TypeRegistry;
import java.io.IOException;
import java.io.Reader;

/** Produces a Soy internal logging config with additional Soy-only details. */
public final class AnnotatedLoggingConfigGenerator {

  private final CharSource rawLoggingConfig;
  private final String javaPackage;
  private final String className;
  private final TypeRegistry.ProtoRegistry typeRegistry;

  public AnnotatedLoggingConfigGenerator(
      CharSource rawLoggingConfig,
      String javaPackage,
      String className,
      SoyTypeRegistry typeRegistry) {
    this.rawLoggingConfig = rawLoggingConfig;
    this.javaPackage = javaPackage;
    this.className = className;
    checkArgument(typeRegistry instanceof TypeRegistry.ProtoRegistry);
    this.typeRegistry = (TypeRegistry.ProtoRegistry) typeRegistry;
  }

  private LoggingConfig parseLoggingConfig() throws IOException {
    ExtensionRegistry extensions = new VeMetadataExtensionRegistry(typeRegistry).createRegistry();
    LoggingConfig.Builder loggingConfig = LoggingConfig.newBuilder();
    try (Reader input = rawLoggingConfig.openStream()) {
      TextFormat.merge(input, extensions, loggingConfig);
    }
    return loggingConfig.build();
  }

  public AnnotatedLoggingConfig generate() throws IOException {
    LoggingConfig loggingConfig = parseLoggingConfig();

    // Add the UndefinedVe to the annotated LoggingConfig. This ensures the annotated LoggingConfig
    // always has at least one element, so will always contain the necessary fields (package, class
    // name, etc.).
    AnnotatedLoggingConfig.Builder builder =
        AnnotatedLoggingConfig.newBuilder()
            .addElement(
                ValidatedLoggingConfig.UNDEFINED_VE.toBuilder()
                    .setHasMetadata(false)
                    .setJavaPackage(javaPackage)
                    .setClassName(className)
                    .build());
    for (LoggableElement element : loggingConfig.getElementList()) {
      builder.addElement(
          AnnotatedLoggableElement.newBuilder()
              .setHasMetadata(element.getMetadata().getSerializedSize() > 0)
              .setElement(element)
              .setJavaPackage(javaPackage)
              .setClassName(className));
    }

    return builder.build();
  }
}
