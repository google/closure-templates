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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.logging.AnnotatedLoggingConfig;
import com.google.template.soy.logging.VeMetadata;
import com.google.template.soy.soytree.CompilationUnit;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Implementations of {@link SoyInputCache.CacheLoader} for common compiler inputs. */
final class CacheLoaders {
  static final SoyInputCache.CacheLoader<AnnotatedLoggingConfig> LOGGING_CONFIG_LOADER =
      new SoyInputCache.CacheLoader<AnnotatedLoggingConfig>() {
        @Override
        public AnnotatedLoggingConfig read(
            File file, SoyCompilerFileReader reader, SoyInputCache cache) throws IOException {
          try (InputStream stream = reader.read(file).openStream()) {
            // This could include VE metadata with extensions, but those are processed separately
            // (via SoyAnnotatedLoggingConfigGenerator) and aren't needed here, so just pass an
            // empty extension registry.
            return AnnotatedLoggingConfig.parseFrom(stream, ExtensionRegistry.getEmptyRegistry());
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

  // A map of proto file name to the FileDescriptor from that proto's Java class. When matching
  // descriptors (like to see if an extension exists on a message) proto uses reference equality, so
  // this allows us to register the FileDescriptors for the compiled protos, rather than ones read
  // at runtime from descriptor files passed to the Soy compiler, and means that all references
  // (even from runtime protos) point to the same (compile time) descriptors.
  private static final ImmutableMap<String, FileDescriptor> WELL_KNOWN_PROTOS =
      ImmutableMap.of(VeMetadata.getDescriptor().getName(), VeMetadata.getDescriptor());

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
    private final Map<String, FileDescriptor> fileNameToDescriptors = new LinkedHashMap<>();

    CachedDescriptorSet(File file, FileDescriptorSet proto) {
      this.file = checkNotNull(file);
      ImmutableMap.Builder<String, FileDescriptorProto> protosByFileNameBuilder =
          ImmutableMap.builder();
      for (FileDescriptorProto fileProto : proto.getFileList()) {
        protosByFileNameBuilder.put(fileProto.getName(), fileProto);
        if (WELL_KNOWN_PROTOS.containsKey(fileProto.getName())) {
          fileNameToDescriptors.put(
              fileProto.getName(), WELL_KNOWN_PROTOS.get(fileProto.getName()));
        }
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

  static final SoyInputCache.CacheLoader<ImmutableList<String>> CSS_CHECK_EXEMPTIONS =
      new SoyInputCache.CacheLoader<ImmutableList<String>>() {
        @Override
        public ImmutableList<String> read(
            File file, SoyCompilerFileReader reader, SoyInputCache cache) throws IOException {
          return reader.read(file).asCharSource(UTF_8).readLines();
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
            return CompilationUnit.parseFrom(is, ExtensionRegistry.getEmptyRegistry());
          }
        }
      };

  private CacheLoaders() {}
}
