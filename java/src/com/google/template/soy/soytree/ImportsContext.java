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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Streams.stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.types.DelegatingSoyTypeRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * File-specific imports context. Holds info about the symbols that have been imported in a given
 * file.
 */
public final class ImportsContext {

  private SoyTypeRegistry typeRegistry;
  private final Set<String> allImportedSymbols;

  public ImportsContext() {
    this.typeRegistry = null;
    this.allImportedSymbols = new LinkedHashSet<>();
  }

  public boolean addImportedSymbol(String symbol) {
    return allImportedSymbols.add(symbol);
  }

  public void setTypeRegistry(SoyTypeRegistry typeRegistry) {
    checkState(this.typeRegistry == null, "Type registry is already set; cannot be overwritten.");
    this.typeRegistry = typeRegistry;
  }

  public SoyTypeRegistry getTypeRegistry() {
    return checkNotNull(typeRegistry, "Type registry has not been set yet.");
  }

  /**
   * A {@link SoyTypeRegistry} that includes imported symbols (possibly aliased) in a given file.
   */
  public static final class ImportsTypeRegistry extends DelegatingSoyTypeRegistry {

    /** Map of all imported names to FQN (messages and enums). */
    private final ImmutableMap<String, String> msgAndEnumLocalToFqn;
    /** Map of all imported names to FQN (messages, enums, and extensions). */
    private final ImmutableMap<String, String> allLocalToFqn;

    public ImportsTypeRegistry(
        SoyTypeRegistry delegate,
        ImmutableMap<String, String> msgAndEnumLocalToFqn,
        ImmutableMap<String, String> allLocalToFqn) {
      super(delegate);
      this.msgAndEnumLocalToFqn = msgAndEnumLocalToFqn;
      this.allLocalToFqn = allLocalToFqn;
    }

    @Nullable
    @Override
    public SoyType getType(String typeName) {
      String fullType = SoyTypes.localToFqn(typeName, msgAndEnumLocalToFqn);
      return fullType != null ? getProtoRegistry().getProtoType(fullType) : super.getType(typeName);
    }

    @Override
    public Identifier resolve(Identifier id) {
      String resolved = SoyTypes.localToFqn(id.identifier(), allLocalToFqn);
      if (resolved != null) {
        return Identifier.create(resolved, id.originalName(), id.location());
      }
      return super.resolve(id);
    }

    @Override
    public Iterable<String> getAllSortedTypeNames() {
      return () ->
          Streams.concat(
                  msgAndEnumLocalToFqn.keySet().stream(), stream(super.getAllSortedTypeNames()))
              .sorted()
              .iterator();
    }
  }
}
