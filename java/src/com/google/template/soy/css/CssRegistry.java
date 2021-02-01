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
import java.util.Map;
import java.util.Optional;

/** Registry of known css symbols provided by the --cssSummaries flag. */
@Immutable
@AutoValue
public abstract class CssRegistry {
  public abstract ImmutableSet<String> providedSymbols();

  abstract ImmutableMap<String, String> filePathToSymbol();

  abstract Optional<ImmutableListMultimap<String, String>> classMap();

  abstract ImmutableMap<String, String> classToFilePathMap();

  abstract ImmutableMap<String, String> classToNamespaceMap();

  public abstract ImmutableList<String> checkCssList();

  public abstract boolean skipCssReferenceCheck();

  @Memoized
  public ImmutableMap<String, String> symbolToFilePath() {
    return filePathToSymbol().entrySet().stream()
        .collect(toImmutableMap(Map.Entry::getValue, Map.Entry::getKey));
  }

  public boolean isInRegistry(String symbol) {
    return providedSymbols().contains(symbol);
  }

  public String getSymbolFromFilepath(String filePath) {
    return filePathToSymbol().get(filePath);
  }

  public ImmutableList<String> allowedSymbolsToUse(String nsOrPath) {
    return classMap().get().get(nsOrPath);
  }

  public Optional<String> maybeGetRequireCss(String className) {
    return Optional.ofNullable(classToNamespaceMap().getOrDefault(className, null));
  }

  public Optional<String> maybeGetRequireCssPath(String className) {
    return Optional.ofNullable(classToFilePathMap().getOrDefault(className, null));
  }

  public boolean containsClassMap() {
    return classMap().isPresent();
  }

  public static CssRegistry create(
      ImmutableSet<String> providedSymbols, ImmutableMap<String, String> filePathToSymbol) {
    return new AutoValue_CssRegistry(
        providedSymbols,
        filePathToSymbol,
        Optional.empty(),
        ImmutableMap.of(),
        ImmutableMap.of(),
        ImmutableList.of(),
        false /* skipCssReferenceCheck */);
  }
}
