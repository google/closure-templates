/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.passes;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.soytree.FileMetadata;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.Metadata;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Abstract pass that implements {@link #getFileMetadata(SourceLogicalPath)}, which provides a
 * non-null value for any valid import (from soy deps or from other files in same compilation unit).
 */
abstract class AbstractTopologicallyOrderedPass
    implements CompilerFileSetPass, CompilerFileSetPass.TopologicallyOrdered {

  private final Supplier<FileSetMetadata> fileSetMetadataFromDeps;
  private final Map<SourceLogicalPath, FileMetadata> astMetadata = new HashMap<>();

  protected AbstractTopologicallyOrderedPass(Supplier<FileSetMetadata> fileSetMetadataFromDeps) {
    this.fileSetMetadataFromDeps = fileSetMetadataFromDeps;
  }

  abstract void run(SoyFileNode file, IdGenerator nodeIdGen);

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      run(file, idGenerator);
      astMetadata.put(file.getFilePath().asLogicalPath(), Metadata.forAst(file));
    }
    return Result.CONTINUE;
  }

  @Nullable
  protected FileMetadata getFileMetadata(SourceLogicalPath path) {
    FileMetadata metadata = astMetadata.get(path);
    if (metadata == null) {
      metadata = fileSetMetadataFromDeps.get().getFile(path);
    }
    return metadata;
  }
}
