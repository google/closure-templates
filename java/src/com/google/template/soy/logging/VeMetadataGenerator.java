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
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Bytes;
import com.google.escapevelocity.Template;
import com.google.protobuf.ExtensionRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TypeRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Generates VE metadata files.
 *
 * <p>These files contain a constant for each VE that has metadata so that generated code can
 * reference these constants and access the VE metadata.
 */
public final class VeMetadataGenerator {

  private static final Joiner COMMA = Joiner.on(", ");

  private final ByteSource loggingConfigBytes;
  private final String generator;
  private final TypeRegistry.ProtoRegistry typeRegistry;

  public VeMetadataGenerator(
      ByteSource loggingConfigBytes, String generator, SoyTypeRegistry typeRegistry) {
    this.loggingConfigBytes = loggingConfigBytes;
    this.generator = generator;
    checkArgument(typeRegistry instanceof TypeRegistry.ProtoRegistry);
    this.typeRegistry = (TypeRegistry.ProtoRegistry) typeRegistry;
  }

  public String generate() throws IOException {
    ExtensionRegistry registry = new VeMetadataExtensionRegistry(typeRegistry).createRegistry();
    AnnotatedLoggingConfig loggingConfig = parseLoggingConfig(registry);

    // All elements in this file should have the same package/class, so just grab it off the first
    // one, but verify below that they all match.
    String javaPackage = loggingConfig.getElement(0).getJavaPackage();
    String className = loggingConfig.getElement(0).getClassName();

    ImmutableList<VeMetadata> veMetadatas = getVeMetadatas(loggingConfig, javaPackage, className);

    return generateMetadataFile(javaPackage, className, generator, veMetadatas);
  }

  private AnnotatedLoggingConfig parseLoggingConfig(ExtensionRegistry registry) throws IOException {
    try (InputStream input = loggingConfigBytes.openStream()) {
      return AnnotatedLoggingConfig.parseFrom(input, registry);
    }
  }

  private static ImmutableList<VeMetadata> getVeMetadatas(
      AnnotatedLoggingConfig loggingConfig, String javaPackage, String className) {
    ImmutableList.Builder<VeMetadata> veMetadatas = ImmutableList.builder();

    for (AnnotatedLoggableElement element : loggingConfig.getElementList()) {
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
      if (element.getHasMetadata()) {
        veMetadatas.add(VeMetadata.create(element));
      }
    }

    return veMetadatas.build();
  }

  private String generateMetadataFile(
      String javaPackage, String className, String generator, ImmutableList<VeMetadata> veMetadatas)
      throws IOException {
    Template template =
        Template.parseFrom(
            new BufferedReader(
                new InputStreamReader(
                    getClass().getResourceAsStream("server_ve_metadata.vm"), UTF_8)));

    ImmutableMap<String, Object> vars =
        ImmutableMap.of(
            "package",
            javaPackage,
            "className",
            className,
            "generator",
            generator,
            "veMetadatas",
            veMetadatas);
    return template.evaluate(vars);
  }

  /** The information needed to generate a metadata constant for a VE. */
  @AutoValue
  public abstract static class VeMetadata {
    private static VeMetadata create(AnnotatedLoggableElement element) {
      return new AutoValue_VeMetadataGenerator_VeMetadata(
          element.getElement().getId(),
          COMMA.join(Bytes.asList(element.getElement().getMetadata().toByteArray())));
    }

    public abstract long id();

    public abstract String encodedMetadata();
  }
}
