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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.template.soy.base.internal.FixedIdGenerator;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.StableSoyFileSupplier;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;

/** Implementations of {@link SoyInputCache.Reader} for common compiler inputs. */
final class Readers {
  static final SoyInputCache.Reader<LoggingConfig> LOGGING_CONFIG_READER =
      new SoyInputCache.Reader<LoggingConfig>() {
        @Override
        public LoggingConfig read(File file, SoyCompilerFileReader reader, SoyInputCache cache)
            throws IOException {
          try (InputStream stream = reader.read(file).openStream()) {
            return LoggingConfig.parseFrom(stream);
          }
        }
      };

  static final SoyInputCache.Reader<ImmutableMap<String, PrimitiveData>> GLOBALS_READER =
      new SoyInputCache.Reader<ImmutableMap<String, PrimitiveData>>() {
        @Override
        public ImmutableMap<String, PrimitiveData> read(
            File file, SoyCompilerFileReader reader, SoyInputCache cache) throws IOException {
          return SoyUtils.parseCompileTimeGlobals(reader.read(file).asCharSource(UTF_8));
        }
      };

  // TODO(lukes): this isn't ideal.  What we really want to cache are FileDescriptor objects
  // instead of FileDescriptorSet protos.  But since file descriptors depend on each other that
  // makes it tricky to reason about caching.  The best strategy might be a second layer cache.
  static final SoyInputCache.Reader<FileDescriptorSet> FILE_DESCRIPTOR_SET_READER =
      new SoyInputCache.Reader<FileDescriptorSet>() {
        @Override
        public FileDescriptorSet read(File file, SoyCompilerFileReader reader, SoyInputCache cache)
            throws IOException {
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
        public CompilationUnit read(File file, SoyCompilerFileReader reader, SoyInputCache cache)
            throws IOException {
          try (InputStream is =
              new GZIPInputStream(reader.read(file).openStream(), /* bufferSize */ 32 * 1024)) {
            return CompilationUnit.parseFrom(is);
          }
        }
      };

  /**
   * A special SoyFileSupplier that implements {@link HasAstOrErrors} to support caching.
   *
   * <p>This stores an ErrorReporter and a nullable SoyFileNode so that the results of a parse can
   * always be replayed in the context of any compile that gets a cache hit.
   */
  private static final class CachedSoyFileSupplier
      implements SoyFileSupplier, SoyFileSetParser.HasAstOrErrors {
    private final SoyFileSupplier delegate;
    private final ErrorReporter errors;
    @Nullable private final SoyFileNode file;

    CachedSoyFileSupplier(SoyFileSupplier delegate, ErrorReporter errors, SoyFileNode file) {
      this.delegate = checkNotNull(delegate);
      this.errors = checkNotNull(errors);
      this.file = file;
      if (file == null) {
        checkArgument(errors.hasErrors()); // sanity check
      }
    }

    @Override
    public SoyFileNode getAst(IdGenerator nodeIdGen, ErrorReporter other) {
      errors.copyTo(other);
      if (file != null) {
        // we need to make a copy, since the AST is mutable
        // we need to assign new ids using the id generator for the current compile to ensure that
        // all ids are unique across a compile.
        return SoyTreeUtils.cloneWithNewIds(file, nodeIdGen);
      }
      return null;
    }

    // boring delegate methods

    @Override
    public boolean hasChangedSince(Version version) {
      return delegate.hasChangedSince(version);
    }

    @Override
    public String getFilePath() {
      return delegate.getFilePath();
    }

    @Override
    public CharSource asCharSource() {
      return delegate.asCharSource();
    }

    @Override
    public java.io.Reader open() throws IOException {
      return delegate.open();
    }

    @Override
    public Version getVersion() {
      return delegate.getVersion();
    }
  }

  static final SoyInputCache.Reader<CachedSoyFileSupplier> SOY_FILE_READER =
      new SoyInputCache.Reader<CachedSoyFileSupplier>() {
        private final FixedIdGenerator idGenerator = new FixedIdGenerator(-1);

        @Override
        public CachedSoyFileSupplier read(
            File file, SoyCompilerFileReader reader, SoyInputCache cache) throws IOException {
          CharSource source = reader.read(file).asCharSource(UTF_8);
          SoyFileSupplier delegate = new StableSoyFileSupplier(source, file.getPath());
          ErrorReporter errors = ErrorReporter.create(/*fileSuppliers*/ ImmutableMap.of());
          SoyFileNode fileNode;
          try (java.io.Reader charReader = source.openStream()) {
            fileNode =
                new SoyFileParser(idGenerator, charReader, file.getPath(), errors).parseSoyFile();
          }
          return new CachedSoyFileSupplier(delegate, errors, fileNode);
        }
      };
}
