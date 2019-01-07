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
import com.google.common.collect.SetMultimap;
import com.google.common.io.CharSource;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;

/** Implementations of {@link SoyInputCache.CacheLoader} for common compiler inputs. */
final class CacheLoaders {
  static final SoyInputCache.CacheLoader<LoggingConfig> LOGGING_CONFIG_LOADER =
      new SoyInputCache.CacheLoader<LoggingConfig>() {
        @Override
        public LoggingConfig read(File file, SoyCompilerFileReader reader, SoyInputCache cache)
            throws IOException {
          try (InputStream stream = reader.read(file).openStream()) {
            return LoggingConfig.parseFrom(stream);
          }
        }
      };

  static final SoyInputCache.CacheLoader<ImmutableMap<String, PrimitiveData>> GLOBALS_LOADER =
      new SoyInputCache.CacheLoader<ImmutableMap<String, PrimitiveData>>() {
        @Override
        public ImmutableMap<String, PrimitiveData> read(
            File file, SoyCompilerFileReader reader, SoyInputCache cache) throws IOException {
          return SoyUtils.parseCompileTimeGlobals(reader.read(file).asCharSource(UTF_8));
        }
      };

  /**
   * A cached descriptor set.
   *
   * <p>Reading descriptors is expensive and takes multiple steps:
   *
   * <ol>
   *   <li>First we need to parse the protos, this can be expensive due to size
   *   <li>Then we need to resolve all the symbols and construct FileDescriptor objects, this is
   *       complex because the files depend on each other but we don't know how until we read the
   *       protos.
   * </ol>
   *
   * <p>To make caching effective we essentially do these 2 steps at different times. The {@link
   * #CACHED_DESCRIPTOR_SET_LOADER} is responsible for step 1 and our caller is responsible for
   * assisting with step #2 (by calculating the filename->FileDescriptorSet map).
   */
  static final class CachedDescriptorSet {
    private final File file;
    private final Map<String, FileDescriptorProto> protosByFileName;
    private final Map<String, FileDescriptor> fileNameToDescriptors = new LinkedHashMap<>();;

    CachedDescriptorSet(File file, FileDescriptorSet proto) {
      this.file = checkNotNull(file);
      ImmutableMap.Builder<String, FileDescriptorProto> protosByFileNameBuilder =
          ImmutableMap.builder();
      for (FileDescriptorProto fileProto : proto.getFileList()) {
        protosByFileNameBuilder.put(fileProto.getName(), fileProto);
      }
      this.protosByFileName = protosByFileNameBuilder.build();
    }

    File getFile() {
      return file;
    }

    Set<String> getProtoFileNames() {
      return protosByFileName.keySet();
    }

    /**
     * Returns the descriptors for all enums, messages and extensions defined directly in this file.
     *
     * <p>If you want descriptors from dependencies you will need to call this method on those
     * objects (or invoke {@link FileDescriptor#getDependencies}).
     *
     * @param protoFileToDescriptor a map of proto file names to the CachedDescriptorSet that
     *     contains them.
     * @param cache The cache so file dependencies can be recorded.
     */
    Collection<FileDescriptor> getFileDescriptors(
        SetMultimap<String, CachedDescriptorSet> protoFileToDescriptor, SoyInputCache cache)
        throws DescriptorValidationException {
      if (fileNameToDescriptors.size() == protosByFileName.size()) {
        return fileNameToDescriptors.values();
      }
      // we are missing some descriptors, iterate over everything to make sure they are populated.
      for (FileDescriptorProto fileProto : protosByFileName.values()) {
        buildDescriptor(fileProto, protoFileToDescriptor, cache);
      }
      return fileNameToDescriptors.values();
    }

    private FileDescriptor buildDescriptor(
        FileDescriptorProto fileProto,
        SetMultimap<String, CachedDescriptorSet> protoFileToDescriptor,
        SoyInputCache cache)
        throws DescriptorValidationException {
      FileDescriptor descriptor = fileNameToDescriptors.get(fileProto.getName());
      if (descriptor != null) {
        return descriptor;
      }
      FileDescriptor[] deps = new FileDescriptor[fileProto.getDependencyCount()];
      for (int i = 0; i < fileProto.getDependencyCount(); i++) {
        String depName = fileProto.getDependency(i);
        Set<CachedDescriptorSet> depDescriptorSets = protoFileToDescriptor.get(depName);
        if (depDescriptorSets.isEmpty()) {
          throw new IllegalStateException(
              "Cannot find proto descriptor for "
                  + depName
                  + " which is a dependency of "
                  + fileProto.getName());
        }
        // add dependencies on all files that might provide the dependency, that way we will always
        // get evicted, even if not all potential dependencies are present.
        for (CachedDescriptorSet dep : depDescriptorSets) {
          if (dep != this) {
            cache.declareDependency(getFile(), dep.getFile());
          }
        }
        // look up the dep from the first depDescriptorSet.  It is arbitrary which one we use.
        // Often, this set has exactly one entry, and when it doesn't we should have already issued
        // a warning.
        CachedDescriptorSet dep = depDescriptorSets.iterator().next();
        FileDescriptorProto depProto = dep.protosByFileName.get(depName);
        if (depProto == null) {
          throw new IllegalStateException(
              "Cannot find proto for: "
                  + depName
                  + " which is a dependency of "
                  + fileProto.getName());
        }
        deps[i] = dep.buildDescriptor(depProto, protoFileToDescriptor, cache);
      }
      descriptor = FileDescriptor.buildFrom(fileProto, deps);
      fileNameToDescriptors.put(fileProto.getName(), descriptor);
      return descriptor;
    }
  }

  static final SoyInputCache.CacheLoader<CachedDescriptorSet> CACHED_DESCRIPTOR_SET_LOADER =
      new SoyInputCache.CacheLoader<CachedDescriptorSet>() {
        @Override
        public CachedDescriptorSet read(
            File file, SoyCompilerFileReader reader, SoyInputCache cache) throws IOException {
          try (InputStream stream = reader.read(file).openStream()) {
            return new CachedDescriptorSet(
                file, FileDescriptorSet.parseFrom(stream, ProtoUtils.REGISTRY));
          }
        }
      };

  // TODO(lukes): ideally this would be reading directly to a List<TemplateMetadata> objects by
  // invoking the TemplateMetadataSerializer.  Doing so will require changing how types are parsed.
  static final SoyInputCache.CacheLoader<CompilationUnit> COMPILATION_UNIT_LOADER =
      new SoyInputCache.CacheLoader<CompilationUnit>() {
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

  static final SoyInputCache.CacheLoader<CachedSoyFileSupplier> SOY_FILE_LOADER =
      new SoyInputCache.CacheLoader<CachedSoyFileSupplier>() {
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
