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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Displays {@link SoySyntaxException}s in a useful way, with a snippet of Soy source code
 * containing the error and a caret pointing at the exact place where the error was found.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ErrorPrettyPrinter {

  private final SnippetFormatter snippetFormatter;

  public ErrorPrettyPrinter(SnippetFormatter snippetFormatter) {
    this.snippetFormatter = snippetFormatter;
  }

  /**
   * Displays {@code e} on the given {@link java.io.PrintStream} in a useful way, with a snippet
   * of Soy source code containing the error and a caret pointing at the exact place where the error
   * was found.
   */
  public void print(SoySyntaxException e, PrintStream err) {
    // We build the full error message into a buffer and then print so that we issue one write
    // operation to the print stream.  The most likely printstream is System.err which tends to be
    // used concurrently.  By issuing a single write we ensure that our message doesn't get
    // scrambled with concurrent writes.
    StringBuilder builder = new StringBuilder();

    // Start by printing the actual text of the exception.
    builder.append(e.getMessage()).append("\n");

    // Try to find a snippet of source code associated with the exception and print it.
    SourceLocation sourceLocation = e.getSourceLocation();
    Optional<String> snippet;
    try {
      snippet = snippetFormatter.getSnippet(sourceLocation);
    } catch (IOException exception) {
      snippet = Optional.absent();
    }
    // TODO(user): this is a result of calling SoySyntaxException#createWithoutMetaInfo,
    // which occurs almost 100 times. Clean them up.
    if (snippet.isPresent()) {
      builder.append(snippet.get()).append("\n");
      // Print a caret below the error.
      // TODO(brndn): SourceLocation.beginColumn is occasionally -1. Review all SoySyntaxException
      // instantiations and ensure the SourceLocation is well-formed.
      int beginColumn = Math.max(e.getSourceLocation().getBeginColumn(), 1);
      String caretLine = Strings.repeat(" ", beginColumn - 1) + "^";
      builder.append(caretLine).append("\n");
    }
    err.print(builder.toString());
  }
}
