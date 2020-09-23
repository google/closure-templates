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

package com.google.template.soy.types;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyInternalCompilerException;
import com.google.template.soy.internal.proto.ProtoUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Helper class that assists in the construction of {@link SoyTypeRegistry}. */
public final class SoyTypeRegistryBuilder {

  private static final SoyErrorKind PROTO_FQN_COLLISION =
      SoyErrorKind.of(
          "Identical protobuf message FQN ''{0}'' found in multiple dependencies: {1} and {2}.");

  /** Creates a type registry with only the built-in types. Mostly used for testing. */
  public static SoyTypeRegistry create() {
    return TypeRegistries.newComposite(
        TypeRegistries.builtinTypeRegistry(), TypeRegistries.newTypeInterner());
  }

  private final ImmutableList.Builder<GenericDescriptor> descriptors = ImmutableList.builder();

  public SoyTypeRegistryBuilder() {}

  /** Registers a collection of descriptors of any type. */
  public SoyTypeRegistryBuilder addDescriptors(
      Iterable<? extends GenericDescriptor> descriptorsToAdd) {
    descriptors.addAll(descriptorsToAdd);
    return this;
  }

  public SoyTypeRegistry build() {
    ImmutableList<GenericDescriptor> tmp = descriptors.build();
    ProtoFqnRegistryBuilder builder = new ProtoFqnRegistryBuilder(tmp);
    SoyTypeRegistry base = create();
    ProtoFqnTypeRegistry registry = (ProtoFqnTypeRegistry) builder.build(base);
    return new SoyTypeRegistryImpl(base, ImmutableSet.copyOf(builder.files.values()), registry);
  }

  @AutoValue
  abstract static class DescriptorKey {
    public static DescriptorKey of(GenericDescriptor d) {
      if (d instanceof FileDescriptor) {
        return new AutoValue_SoyTypeRegistryBuilder_DescriptorKey(d.getName(), "");
      } else {
        return new AutoValue_SoyTypeRegistryBuilder_DescriptorKey(
            d.getFile().getName(), d.getFullName());
      }
    }

    abstract String filePath();

    abstract String fullName();
  }

  /** Builder for {@link ProtoTypeRegistry}. */
  public static class ProtoFqnRegistryBuilder {
    private final ErrorReporter errorReporter = ErrorReporter.create(ImmutableMap.of());
    private final ImmutableSet<GenericDescriptor> inputs;
    private final Predicate<GenericDescriptor> alreadyVisitedKey;
    private final Predicate<GenericDescriptor> alreadyVisitedIdentity;
    private final Map<String, GenericDescriptor> msgAndEnumFqnToDesc = new HashMap<>();
    private final SetMultimap<String, FieldDescriptor> msgFqnToExts = HashMultimap.create();
    private final Map<String, FileDescriptor> files = new LinkedHashMap<>();

    public ProtoFqnRegistryBuilder(Iterable<GenericDescriptor> inputs) {
      this.inputs = ImmutableSet.copyOf(inputs); // maintain order
      Set<DescriptorKey> visitedKeys = new HashSet<>();
      alreadyVisitedKey = d -> !visitedKeys.add(DescriptorKey.of(d));
      Set<GenericDescriptor> visitedDescriptors = new HashSet<>();
      alreadyVisitedIdentity = d -> !visitedDescriptors.add(d);
    }

    public ProtoTypeRegistry build(SoyTypeRegistry interner) {
      Set<FileDescriptor> fileInputs =
          inputs.stream()
              .filter(d -> d instanceof FileDescriptor)
              .map(FileDescriptor.class::cast)
              .collect(toImmutableSet()); // maintain order

      // Visit all the file descriptors explicitly passed.
      fileInputs.forEach(this::visitFile);
      // Visit all descriptors explicitly passed not descending from any of the file inputs.
      inputs.stream().filter(d -> !fileInputs.contains(d.getFile())).forEach(this::visitGeneric);
      // Visit to collect extensions any file of any input not already visited.
      inputs.stream()
          .map(GenericDescriptor::getFile)
          .distinct()
          .filter(d -> !fileInputs.contains(d))
          .forEach(this::visitFileForExtensions);

      if (errorReporter.hasErrors()) {
        throw new SoyInternalCompilerException(errorReporter.getErrors(), null);
      }

      return new ProtoFqnTypeRegistry(
          interner,
          ImmutableMap.copyOf(msgAndEnumFqnToDesc),
          ImmutableSetMultimap.copyOf(msgFqnToExts));
    }

    private void visitGeneric(GenericDescriptor descriptor) {
      if (descriptor instanceof Descriptor) {
        visitMessage((Descriptor) descriptor);
      } else if (descriptor instanceof FieldDescriptor) {
        FieldDescriptor fd = (FieldDescriptor) descriptor;
        if (fd.isExtension()) {
          visitExtension(fd);
        }
        visitField(fd);
      } else if (descriptor instanceof EnumDescriptor) {
        visitEnum((EnumDescriptor) descriptor);
      } else if (descriptor instanceof FileDescriptor) {
        throw new IllegalArgumentException();
      } // services, etc. not needed thus far so neither gathered nor dispatched
    }

    private void visitFile(FileDescriptor fd) {
      if (alreadyVisitedIdentity.test(fd)) {
        return;
      }
      files.putIfAbsent(fd.getName(), fd);
      fd.getDependencies().forEach(this::visitFile);
      fd.getExtensions().forEach(this::visitExtension);
      fd.getMessageTypes().forEach(this::visitMessage);
      fd.getEnumTypes().forEach(this::visitEnum);
    }

    private void visitFileForExtensions(FileDescriptor fd) {
      files.putIfAbsent(fd.getName(), fd);
      fd.getDependencies().forEach(this::visitFileForExtensions);
      fd.getExtensions().forEach(this::visitExtension);
      fd.getMessageTypes().forEach(this::visitMessageForExtensions);
    }

    private void visitMessageForExtensions(Descriptor d) {
      d.getExtensions().forEach(this::visitExtension);
      d.getNestedTypes().forEach(this::visitMessageForExtensions);
    }

    private void visitMessage(Descriptor m) {
      if (alreadyVisitedIdentity.test(m)) {
        return;
      }
      if (!alreadyVisitedKey.test(m)) {
        putAndWarnCollision(m);
      }

      m.getEnumTypes().forEach(this::visitEnum);
      m.getExtensions().forEach(this::visitExtension);
      m.getNestedTypes().forEach(this::visitMessage);
      m.getFields().forEach(this::visitField);
    }

    private void visitField(FieldDescriptor f) {
      if (f.getType() == FieldDescriptor.Type.MESSAGE) {
        visitMessage(f.getMessageType());
      }
      if (f.getType() == FieldDescriptor.Type.ENUM) {
        visitEnum(f.getEnumType());
      }
    }

    private void visitEnum(EnumDescriptor e) {
      if (alreadyVisitedKey.test(e)) {
        return;
      }
      putAndWarnCollision(e);
    }

    private void visitExtension(FieldDescriptor f) {
      Preconditions.checkArgument(f.isExtension());
      if (!ProtoUtils.shouldJsIgnoreField(f)) {
        msgFqnToExts.put(f.getContainingType().getFullName(), f);
      }
    }

    private void putAndWarnCollision(GenericDescriptor d) {
      // Since we use FQN as a primary key in several data structures, collisions are errors.
      GenericDescriptor previous = msgAndEnumFqnToDesc.put(d.getFullName(), d);
      if (previous != null) {
        errorReporter.report(
            SourceLocation.UNKNOWN,
            PROTO_FQN_COLLISION,
            d.getFullName(),
            previous.getFile().getName(),
            d.getFile().getName());
      }
    }
  }

  /**
   * The standard implementation of SoyTypeRegistry, which indexes protobuf types by FQN and exposes
   * those types via {@link #getProtoRegistry()}.
   */
  static class SoyTypeRegistryImpl extends DelegatingSoyTypeRegistry {

    private final ImmutableSet<FileDescriptor> fileDescriptors;
    private final ProtoFqnTypeRegistry protoFqnRegistry;

    public SoyTypeRegistryImpl(
        SoyTypeRegistry delegate,
        ImmutableSet<FileDescriptor> fileDescriptors,
        ProtoFqnTypeRegistry protoFqnRegistry) {
      super(delegate);
      this.protoFqnRegistry = protoFqnRegistry;
      this.fileDescriptors = fileDescriptors;
    }

    @Override
    public ImmutableSet<FileDescriptor> getProtoDescriptors() {
      return fileDescriptors;
    }

    @Override
    public ProtoTypeRegistry getProtoRegistry() {
      return protoFqnRegistry;
    }
  }

  private static class ProtoFqnTypeRegistry implements ProtoTypeRegistry {

    private final TypeInterner interner;
    /** Map of FQN to descriptor for all message and enum descendants of imported symbols. */
    private final ImmutableMap<String, GenericDescriptor> msgAndEnumFqnToDesc;
    /** Multimap of FQN to extensions descriptor for all message descendants of imported symbols. */
    private final ImmutableSetMultimap<String, FieldDescriptor> msgFqnToExts;

    public ProtoFqnTypeRegistry(
        TypeInterner interner,
        ImmutableMap<String, GenericDescriptor> msgAndEnumFqnToDesc,
        ImmutableSetMultimap<String, FieldDescriptor> msgFqnToExts) {
      this.interner = interner;
      this.msgAndEnumFqnToDesc = msgAndEnumFqnToDesc;
      this.msgFqnToExts = msgFqnToExts;
    }

    @Nullable
    @Override
    public SoyType getProtoType(String protoFqn) {
      GenericDescriptor descriptor = msgAndEnumFqnToDesc.get(protoFqn);
      if (descriptor instanceof EnumDescriptor) {
        return interner.getOrCreateProtoEnumType((EnumDescriptor) descriptor);
      } else if (descriptor instanceof Descriptor) {
        return interner.getOrComputeProtoType(
            (Descriptor) descriptor,
            name ->
                new SoyProtoType(
                    interner, this, (Descriptor) descriptor, msgFqnToExts.get(protoFqn)));
      }
      return null;
    }
  }
}
