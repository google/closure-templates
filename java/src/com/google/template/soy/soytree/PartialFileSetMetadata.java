/*
 * Copyright 2021 Google Inc.
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

import com.google.template.soy.base.SourceFilePath;
import java.util.Collection;
import javax.annotation.Nullable;

/** Metadata about a soy file set that is available as soon as its AST is parsed. */
public interface PartialFileSetMetadata {

  @Nullable
  PartialFileMetadata getPartialFile(SourceFilePath path);

  Collection<? extends PartialFileMetadata> getAllPartialFiles();

  /**
   * Gets the Soy namespace of a source file indexed in this metadata.
   *
   * @throws NullPointerException if {@code path} is not registered in metadata.
   */
  default String getNamespaceForPath(SourceFilePath path) {
    return getPartialFile(path).getNamespace();
  }
}
