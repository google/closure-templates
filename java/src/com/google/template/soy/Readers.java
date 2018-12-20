/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.soytree.CompilationUnit;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/** Implementations of {@link SoyInputCache.Reader} for common compiler inputs. */
final class Readers {
  static final SoyInputCache.Reader<LoggingConfig> LOGGING_CONFIG_READER =
      new SoyInputCache.Reader<LoggingConfig>() {
        @Override
        public LoggingConfig read(File file, SoyCompilerFileReader reader) throws IOException {
          try (InputStream stream = reader.read(file).openStream()) {
            return LoggingConfig.parseFrom(stream);
          }
        }
      };

  static final SoyInputCache.Reader<ImmutableMap<String, PrimitiveData>> GLOBALS_READER =
      new SoyInputCache.Reader<ImmutableMap<String, PrimitiveData>>() {
        @Override
        public ImmutableMap<String, PrimitiveData> read(File file, SoyCompilerFileReader reader)
            throws IOException {
          return SoyUtils.parseCompileTimeGlobals(reader.read(file).asCharSource(UTF_8));
        }
      };

  // TODO(lukes): this isn't ideal.  What we really want to cache are FileDescriptor objects
  // instead of FileDescriptorSet protos.  But since file descriptors depend on each other that
  // makes it tricky to reason about caching.  The best strategy might be a second layer cache.
  static final SoyInputCache.Reader<FileDescriptorSet> FILE_DESCRIPTOR_SET_READER =
      new SoyInputCache.Reader<FileDescriptorSet>() {
        @Override
        public FileDescriptorSet read(File file, SoyCompilerFileReader reader) throws IOException {
          try (InputStream stream = reader.read(file).openStream()) {
            return FileDescriptorSet.parseFrom(stream, ProtoUtils.REGISTRY);
          }
        }
      };

  // TODO(lukes): ideally this would be reading directly to a List<TemplateMetadata> objects by
  // invoking the TemplateMetadataSerializer.  Doing so will require changing how types are parsed.
  static final SoyInputCache.Reader<CompilationUnit> COMPILATION_UNIT_READER =
      new SoyInputCache.Reader<CompilationUnit>() {
        @Override
        public CompilationUnit read(File file, SoyCompilerFileReader reader) throws IOException {
          try (InputStream is =
              new GZIPInputStream(reader.read(file).openStream(), /* bufferSize */ 32 * 1024)) {
            return CompilationUnit.parseFrom(is);
          }
        }
      };
}
