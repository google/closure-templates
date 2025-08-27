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
import com.google.template.soy.base.SourceLocationMapper;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

/**
 * Implementation of ErrorFormatter that can add code snippets and incorporate input sourcemaps.
 *
 * <p>Calling either of the {@link #withSources} methods will cause snippets to be included in the
 * formatter errors. And if a <a href="https://sourcemaps.info/spec.html">source map</a> can be
 * found in the source, the original source location will be included too.
 */
public final class ErrorFormatterImpl implements ErrorFormatter {

  public static ErrorFormatterImpl create() {
    return new ErrorFormatterImpl(null);
  }

  @Nullable private final ImmutableMap<SourceLogicalPath, SoyFileSupplier> sourceFileIndex;
  private final Map<SoyFileSupplier, SourceSnippetPrinter> snippetCache = new HashMap<>();
  private final Map<SoyFileSupplier, SourceLocationMapper> sourceMapsCache = new HashMap<>();

  private ErrorFormatterImpl(
      @Nullable ImmutableMap<SourceLogicalPath, SoyFileSupplier> sourceFileIndex) {
    this.sourceFileIndex = sourceFileIndex;
  }

  public ErrorFormatterImpl withSources(Map<SourceLogicalPath, SoyFileSupplier> sourceFiles) {
    return new ErrorFormatterImpl(ImmutableMap.copyOf(sourceFiles));
  }

  public ErrorFormatterImpl withSources(Iterable<SoyFileSupplier> sourceFiles) {
    return new ErrorFormatterImpl(
        StreamSupport.stream(sourceFiles.spliterator(), false)
            .collect(toImmutableMap(f -> f.getFilePath().asLogicalPath(), f -> f)));
  }

  @Override
  public String format(SoyError report) {
    if (sourceFileIndex == null) {
      return SIMPLE.format(report);
    }
    SourceLocation location = report.location();
    SoyFileSupplier sourceFile = sourceFileIndex.get(location.getFilePath().asLogicalPath());
    if (sourceFile == null) {
      return SIMPLE.format(report);
    }

    SourceSnippetPrinter snippetPrinter =
        snippetCache.computeIfAbsent(sourceFile, SourceSnippetPrinter::new);
    Optional<String> snippet = snippetPrinter.getSnippet(location);

    SourceLocationMapper sourceMap =
        sourceMapsCache.computeIfAbsent(sourceFile, ErrorFormatterImpl::buildSourceMap);
    SourceLocation originalLocation = sourceMap.map(location);
    String sourceMapExtraInfo = "";
    if (!location.equals(originalLocation) && originalLocation.getBeginPoint().isKnown()) {
      report = report.withLocation(originalLocation);
      sourceMapExtraInfo =
          "\n[generated location: " + ErrorFormatter.formatLocation(location) + "]";
    }

    String formatted = SIMPLE.format(report) + sourceMapExtraInfo;
    if (snippet.isPresent()) {
      formatted += '\n' + snippet.get().stripTrailing();
    }
    return formatted;
  }

  private static SourceLocationMapper buildSourceMap(SoyFileSupplier sourceFile) {
    return SourceLocationMapper.NO_OP;
  }
}
