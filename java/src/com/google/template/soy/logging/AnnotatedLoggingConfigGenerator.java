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

import com.google.common.io.CharSource;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Produces a Soy internal logging config with additional Soy-only details. */
public final class AnnotatedLoggingConfigGenerator {

  private static final SoyErrorKind TEXT_PROTO_PARSE_ERROR =
      SoyErrorKind.of("Error parsing logging config textproto: {0}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind DUPLICATE_VE =
      SoyErrorKind.of(
          "Logging config contains different VEs with the same ID:\n\n{0}\nand\n\n{1}",
          StyleAllowance.NO_PUNCTUATION);

  private final CharSource rawLoggingConfig;
  private final String javaPackage;
  private final String jsPackage;
  private final String className;
  private final String javaResourceFilename;
  private final SoyTypeRegistry typeRegistry;
  private final ErrorReporter errorReporter;

  public AnnotatedLoggingConfigGenerator(
      CharSource rawLoggingConfig,
      String javaPackage,
      String jsPackage,
      String className,
      String javaResourceFilename,
      SoyTypeRegistry typeRegistry,
      ErrorReporter errorReporter) {
    this.rawLoggingConfig = rawLoggingConfig;
    this.javaPackage = javaPackage;
    this.jsPackage = jsPackage;
    this.className = className;
    this.javaResourceFilename = javaResourceFilename;
    this.typeRegistry = typeRegistry;
    this.errorReporter = errorReporter;
  }

  private LoggingConfig parseLoggingConfig() throws IOException {
    ExtensionRegistry extensions = new VeMetadataExtensionRegistry(typeRegistry).createRegistry();
    LoggingConfig.Builder loggingConfig = LoggingConfig.newBuilder();
    try (Reader input = rawLoggingConfig.openStream()) {
      TextFormat.merge(input, extensions, loggingConfig);
    } catch (ParseException e) {
      errorReporter.report(SourceLocation.UNKNOWN, TEXT_PROTO_PARSE_ERROR, e.getMessage());
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
                    .setJsPackage(jsPackage)
                    .setClassName(className)
                    .setJavaResourceFilename(javaResourceFilename)
                    .build());
    Map<Long, LoggableElement> elements = new HashMap<>();
    for (LoggableElement element : loggingConfig.getElementList()) {
      AnnotatedLoggableElement annotatedElement =
          AnnotatedLoggableElement.newBuilder()
              .setHasMetadata(element.getMetadata().getSerializedSize() > 0)
              .setElement(element)
              .setJavaPackage(javaPackage)
              .setJsPackage(jsPackage)
              .setClassName(className)
              .setJavaResourceFilename(javaResourceFilename)
              .build();

      if (elements.containsKey(element.getId())) {
        if (!element.equals(elements.get(element.getId()))) {
          errorReporter.report(
              SourceLocation.UNKNOWN, DUPLICATE_VE, element, elements.get(element.getId()));
        }
        // Exact duplicates are allowed, but only include them once in the annotated logging config.
        continue;
      }
      builder.addElement(annotatedElement);
      elements.put(element.getId(), element);
    }

    return builder.build();
  }
}
