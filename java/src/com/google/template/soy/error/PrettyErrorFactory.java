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
import java.io.IOException;

/**
 * Displays {@link SoyErrorKind}s in a useful way, with a snippet of Soy source code containing the
 * error and a caret pointing at the exact place where the error was found.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class PrettyErrorFactory implements SoyError.Factory {

  private final SnippetFormatter snippetFormatter;

  public PrettyErrorFactory(SnippetFormatter snippetFormatter) {
    this.snippetFormatter = snippetFormatter;
  }

  @Override
  public SoyError create(SourceLocation location, SoyErrorKind kind, Object... args) {
    String message = kind.format(args);
    return SoyError.createError(location, kind, message, getFormattedError(location, message));
  }

  private String getFormattedError(SourceLocation sourceLocation, String message) {
    StringBuilder builder = new StringBuilder();

    // Start by printing the actual text of the exception.
    // NOTE: we don't put the column offset in the error message because it is redundant with the
    // caret location.
    builder
        .append(sourceLocation.getFilePath())
        .append(':')
        .append(sourceLocation.getBeginLine())
        .append(": error: ")
        .append(message)
        .append("\n");

    // Try to find a snippet of source code associated with the exception and print it.
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
      int beginColumn = Math.max(sourceLocation.getBeginColumn(), 1);
      String caretLine = Strings.repeat(" ", beginColumn - 1) + "^";
      builder.append(caretLine).append("\n");
    }
    return builder.toString();
  }
}
