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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;

/** Registry of known css symbols provided by the --cssSummaries flag. */
@Immutable
@AutoValue
public abstract class CssRegistry {
  public abstract ImmutableSet<String> providedSymbols();

  abstract ImmutableMap<String, String> filePathToSymbol();

  public boolean isInRegistry(String symbol) {
    return providedSymbols().contains(symbol);
  }

  public String getSymbolFromFilepath(String filePath) {
    return filePathToSymbol().get(filePath);
  }

  public static CssRegistry create(
      ImmutableSet<String> providedSymbols, ImmutableMap<String, String> filePathToSymbol) {
    return new AutoValue_CssRegistry(providedSymbols, filePathToSymbol);
  }
}
