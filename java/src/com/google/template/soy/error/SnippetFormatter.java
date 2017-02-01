/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.error;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * Fetches a snippet of source code surrounding a given {@link SourceLocation}. The snippet is just
 * for human consumption and doesn't necessarily correspond to any unit of Soy syntax.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class SnippetFormatter {

  private final ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers;

  public SnippetFormatter(ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers) {
    this.filePathsToSuppliers = checkNotNull(filePathsToSuppliers);
  }

  /**
   * Returns a snippet of source code surrounding the given {@link SourceLocation}, or {@link
   * Optional#absent()} if source code is unavailable. (This happens, for example, when anyone uses
   * {@link SourceLocation#UNKNOWN}, which is why no one should use it.)
   */
  Optional<String> getSnippet(SourceLocation sourceLocation) throws IOException {
    // Try to find a snippet of source code associated with the exception and print it.
    SoyFileSupplier supplier = filePathsToSuppliers.get(sourceLocation.getFilePath());
    if (supplier == null) {
      return Optional.absent();
    }
    String result;
    try (BufferedReader reader = new BufferedReader(supplier.open())) {
      // Line numbers are 1-indexed
      for (int linenum = 1; linenum < sourceLocation.getBeginLine(); ++linenum) {
        // Skip preceding lines
        reader.readLine();
      }
      result = reader.readLine(); // returns null on EOF
    }
    return Optional.fromNullable(result);
  }
}
