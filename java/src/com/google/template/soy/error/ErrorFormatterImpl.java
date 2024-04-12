/*
 * Copyright 2024 Google Inc.
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

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/** Implementation of ErrorFormatter that can add code snippets. */
public final class ErrorFormatterImpl implements ErrorFormatter {

  private static final SourceSnippetPrinter snippetPrinter = new SourceSnippetPrinter();

  public static ErrorFormatterImpl create() {
    return new ErrorFormatterImpl(null);
  }

  @Nullable private final ImmutableMap<SourceLogicalPath, SoyFileSupplier> sourceFileIndex;

  private ErrorFormatterImpl(
      @Nullable ImmutableMap<SourceLogicalPath, SoyFileSupplier> sourceFileIndex) {
    this.sourceFileIndex = sourceFileIndex;
  }

  public ErrorFormatterImpl withSnippets(Map<SourceLogicalPath, SoyFileSupplier> sourceFiles) {
    return new ErrorFormatterImpl(ImmutableMap.copyOf(sourceFiles));
  }

  public ErrorFormatterImpl withSnippets(Iterable<SoyFileSupplier> sourceFiles) {
    return new ErrorFormatterImpl(
        StreamSupport.stream(sourceFiles.spliterator(), false)
            .collect(toImmutableMap(f -> f.getFilePath().asLogicalPath(), f -> f)));
  }

  @Override
  public String format(SoyError report) {
    SourceLocation location = report.location();
    Optional<String> snippet =
        Optional.ofNullable(sourceFileIndex)
            .map(index -> index.get(location.getFilePath().asLogicalPath()))
            .flatMap(supplier -> snippetPrinter.getSnippet(supplier, location));

    return snippet
        .map(s -> SIMPLE.format(report) + '\n' + s.stripTrailing())
        .orElseGet(() -> SIMPLE.format(report));
  }
}
