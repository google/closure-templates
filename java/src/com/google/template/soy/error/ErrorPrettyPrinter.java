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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileSupplier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/**
 * Displays {@link SoySyntaxException}s in a useful way, with a snippet of Soy source code
 * containing the error and a caret pointing at the exact place where the error was found.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ErrorPrettyPrinter {

  private final ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers;

  public ErrorPrettyPrinter(List<SoyFileSupplier> suppliers) {
    ImmutableMap.Builder<String, SoyFileSupplier> builder = new Builder<>();
    for (SoyFileSupplier supplier : suppliers) {
      builder.put(supplier.getFilePath(), supplier);
    }
    this.filePathsToSuppliers = builder.build();
  }

  /**
   * Displays {@code e} on the given {@link java.io.PrintStream} in a useful way, with a snippet
   * of Soy source code containing the error and a caret pointing at the exact place where the error
   * was found.
   */
  public void print(SoySyntaxException e, PrintStream err) {
    // Start by printing the actual text of the exception.
    err.println(e.getMessage());

    // Try to find a snippet of source code associated with the exception and print it.
    SourceLocation sourceLocation = e.getSourceLocation();
    SoyFileSupplier supplier = filePathsToSuppliers.get(sourceLocation.getFilePath());
    if (supplier == null) {
      // TODO(user): this is a result of calling SoySyntaxException#createWithoutMetaInfo,
      // which occurs almost 100 times. Clean them up.
      return;
    }
    String snippet;
    try {
      snippet = getSnippet(supplier, sourceLocation);
    } catch (IOException ioe) {
      return;
    }

    err.println(snippet);
    // Print a caret below the error.
    // TODO(brndn): SourceLocation.beginColumn is occasionally -1. Review all SoySyntaxException
    // instantiations and ensure the SourceLocation is well-formed.
    int beginColumn = Math.max(e.getSourceLocation().getBeginColumn(), 1);
    String caretLine = Strings.repeat(" ", beginColumn - 1) + "^";
    err.println(caretLine);
  }

  private String getSnippet(SoyFileSupplier supplier, SourceLocation sourceLocation)
      throws IOException {
    try (BufferedReader reader = new BufferedReader(supplier.open())) {
      // Line numbers are 1-indexed
      for (int linenum = 1; linenum < sourceLocation.getLineNumber(); ++linenum) {
        // Skip preceding lines
        reader.readLine();
      }
      return reader.readLine();
    }
  }
}
