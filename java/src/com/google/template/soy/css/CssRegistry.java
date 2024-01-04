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

package com.google.template.soy.css;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLogicalPath;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Registry of known css symbols provided by the --cssSummaries flag. */
@Immutable
@AutoValue
public abstract class CssRegistry {
  public static final CssRegistry EMPTY = createForTest(ImmutableSet.of(), ImmutableMap.of());

  // LFPME = logical file path minus extension

  /** Set of all top-level symbols, i.e. all CSS namespaces and all LFPMEs. */
  public abstract ImmutableSet<String> providedSymbols();

  /** Maps LFPME to the CSS namespace. */
  abstract ImmutableMap<String, String> lfpmeToNamespace();

  /** Maps all keys in {@link #providedSymbols} to the list of classes contained therein. */
  abstract Optional<ImmutableListMultimap<String, String>> symbolToClasses();

  /** Maps the logic file path (not LFPME) to a map of {short class name -> full class name}. */
  abstract ImmutableMap<SourceLogicalPath, ImmutableMap<String, String>> filePathToShortClassMap();

  /** Maps logical file path to the path of the CSS metadata file passed to the compiler. */
  abstract ImmutableMap<SourceLogicalPath, SourceFilePath> logicalToRealMap();

  public abstract boolean skipCssReferenceCheck();

  @Memoized
  ImmutableMap<String, String> symbolToLfpme() {
    return lfpmeToNamespace().entrySet().stream()
        .collect(toImmutableMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  public String getLfpmeForSymbol(String symbol) {
    return symbolToLfpme().get(symbol);
  }

  public boolean isInRegistry(String symbol) {
    return providedSymbols().contains(symbol);
  }

  public String getSymbolFromFilepath(String filePath) {
    return lfpmeToNamespace().get(filePath);
  }

  public ImmutableList<String> allowedSymbolsToUse(String nsOrPath) {
    return symbolToClasses().get().get(nsOrPath);
  }

  public boolean containsClassMap() {
    return symbolToClasses().isPresent();
  }

  public boolean containsLogicalPath(SourceLogicalPath logicalPath) {
    return filePathToShortClassMap().containsKey(logicalPath);
  }

  public ImmutableSet<SourceLogicalPath> getAllLogicalPaths() {
    return filePathToShortClassMap().keySet();
  }

  public ImmutableMap<String, String> getShortClassNameMapForLogicalPath(
      SourceLogicalPath logicalPath) {
    return filePathToShortClassMap().get(logicalPath);
  }

  public static CssRegistry createWithFilePathToShortClassMap(
      ImmutableSet<String> providedSymbols,
      ImmutableMap<SourceLogicalPath, ImmutableMap<String, String>> filePathToShortClassMap) {
    return new AutoValue_CssRegistry(
        providedSymbols,
        ImmutableMap.of(),
        Optional.empty(),
        filePathToShortClassMap,
        ImmutableMap.of(),
        /* skipCssReferenceCheck= */ false);
  }

  public static CssRegistry createForTest(ImmutableSet<String> providedSymbols) {
    return createForTest(providedSymbols, ImmutableMap.of());
  }

  public static CssRegistry createForTest(
      ImmutableSet<String> providedSymbols, ImmutableMap<String, String> filePathToSymbol) {
    return new AutoValue_CssRegistry(
        providedSymbols,
        filePathToSymbol,
        Optional.empty(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        /* skipCssReferenceCheck= */ false);
  }

  @Nullable
  public SourceFilePath getFilePathForLogicalPath(SourceLogicalPath path) {
    return logicalToRealMap().get(path);
  }
}
