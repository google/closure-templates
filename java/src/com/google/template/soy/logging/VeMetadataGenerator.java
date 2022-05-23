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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSink;
import com.google.escapevelocity.Template;
import com.google.protobuf.ExtensionRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Generates VE metadata files.
 *
 * <p>Gencode references these files to access VE metadata.
 */
public final class VeMetadataGenerator {

  /** The kind of rendering to produce VE metadata for. */
  public enum Mode {
    /**
     * Generates a Java source file and a binary encoded RuntimeVeMetadata proto resource. The Java
     * source file references the resource, which contains the actual VE metadata. These two files
     * should be put in the same package and jar, so the Java class can load the resource.
     */
    SERVER {
      @Override
      void generateAndWrite(
          AnnotatedLoggingConfig loggingConfig,
          Options options,
          ExtensionRegistry registry,
          CharSink output,
          Optional<ByteSink> resourceOutput)
          throws IOException {
        checkState(resourceOutput.isPresent());
        ImmutableMap<String, Object> templateVars =
            ImmutableMap.of(
                "package",
                options.javaPackage(),
                "javaResourceFilename",
                options.javaResourceFilename());

        output.write(generateMetadataFile("server_ve_metadata.vm", options, templateVars));

        RuntimeVeMetadata.Builder runtimeVeMetadata = RuntimeVeMetadata.newBuilder();
        for (AnnotatedLoggableElement element : loggingConfig.getElementList()) {
          if (element.getHasMetadata()) {
            runtimeVeMetadata.putMetadata(
                element.getElement().getId(), element.getElement().getMetadata());
          }
        }
        resourceOutput.get().write(runtimeVeMetadata.build().toByteArray());
      }
    },
    /** Generates a TypeScript source file containing the VE metadata. */
    CLIENT {
      @Override
      void generateAndWrite(
          AnnotatedLoggingConfig loggingConfig,
          Options options,
          ExtensionRegistry registry,
          CharSink output,
          Optional<ByteSink> resourceOutput)
          throws IOException {
        checkState(!resourceOutput.isPresent());
        Function<LoggableElementMetadata, String> serializeFunction =
            (metadata) -> java.util.Arrays.toString(metadata.toByteArray());
        ImmutableMap<Long, String> veMetadatas =
            loggingConfig.getElementList().stream()
                .filter(AnnotatedLoggableElement::getHasMetadata)
                .map(AnnotatedLoggableElement::getElement)
                .collect(
                    toImmutableMap(e -> e.getId(), e -> serializeFunction.apply(e.getMetadata())));

        output.write(
            generateMetadataFile(
                "client_ve_metadata.vm", options, ImmutableMap.of("veMetadatas", veMetadatas)));
      }
    };

    abstract void generateAndWrite(
        AnnotatedLoggingConfig loggingConfig,
        Options options,
        ExtensionRegistry registry,
        CharSink output,
        Optional<ByteSink> resourceOutput)
        throws IOException;

    private static String generateMetadataFile(
        String metadataTemplateFilename,
        Options options,
        ImmutableMap<String, Object> extraTemplateVars)
        throws IOException {
      Template template =
          Template.parseFrom(
              new BufferedReader(
                  new InputStreamReader(
                      VeMetadataGenerator.class.getResourceAsStream(metadataTemplateFilename),
                      UTF_8)));

      ImmutableMap<String, Object> vars =
          ImmutableMap.<String, Object>builder()
              .putAll(extraTemplateVars)
              .put("className", options.className())
              .put("generator", options.generator())
              .build();
      return template.evaluate(vars);
    }
  }

  private final Mode mode;
  private final ByteSource loggingConfigBytes;
  private final String generator;
  private final SoyTypeRegistry typeRegistry;
  private final CharSink output;
  private final Optional<ByteSink> resourceOutput;

  public VeMetadataGenerator(
      Mode mode,
      ByteSource loggingConfigBytes,
      String generator,
      SoyTypeRegistry typeRegistry,
      CharSink output,
      Optional<ByteSink> resourceOutput) {
    this.mode = mode;
    this.loggingConfigBytes = loggingConfigBytes;
    this.generator = generator;
    this.typeRegistry = typeRegistry;
    this.output = output;
    this.resourceOutput = resourceOutput;
  }

  public void generateAndWrite() throws IOException {
    ExtensionRegistry registry = new VeMetadataExtensionRegistry(typeRegistry).createRegistry();
    AnnotatedLoggingConfig loggingConfig = parseLoggingConfig(registry);
    Options options = Options.create(loggingConfig, generator);

    mode.generateAndWrite(loggingConfig, options, registry, output, resourceOutput);
  }

  private AnnotatedLoggingConfig parseLoggingConfig(ExtensionRegistry registry) throws IOException {
    try (InputStream input = loggingConfigBytes.openStream()) {
      return AnnotatedLoggingConfig.parseFrom(input, registry);
    }
  }

  @AutoValue
  abstract static class Options {

    private static Options create(AnnotatedLoggingConfig loggingConfig, String generator) {
      // All elements in this file should have the same package/class, so just grab it off the first
      // one, but verify below that they all match.
      String javaPackage = loggingConfig.getElement(0).getJavaPackage();
      String className = loggingConfig.getElement(0).getClassName();
      String javaResourceFilename = loggingConfig.getElement(0).getJavaResourceFilename();

      loggingConfig
          .getElementList()
          .forEach(e -> validateElement(e, javaPackage, className, javaResourceFilename));

      return new AutoValue_VeMetadataGenerator_Options(
          javaPackage, className, javaResourceFilename, generator);
    }

    private static void validateElement(
        AnnotatedLoggableElement element,
        String javaPackage,
        String className,
        String javaResourceFilename) {
      checkState(
          javaPackage.equals(element.getJavaPackage()),
          "expected %s but got %s",
          javaPackage,
          element.getJavaPackage());
      checkState(
          className.equals(element.getClassName()),
          "expected %s but got %s",
          className,
          element.getClassName());
      checkState(
          javaResourceFilename.equals(element.getJavaResourceFilename()),
          "expected %s but got %s",
          javaResourceFilename,
          element.getJavaResourceFilename());
    }

    abstract String javaPackage();

    abstract String className();

    abstract String javaResourceFilename();

    abstract String generator();
  }
}
