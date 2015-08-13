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

package com.google.template.soy.soyparse;

import static com.google.template.soy.base.internal.BaseUtils.formatParseExceptionDetails;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;

/**
 * Helpers for interpreting parse errors as soy errors.
 */
final class ParseErrors {
  private ParseErrors() {}

  static void reportSoyFileParseException(
      ErrorReporter reporter, String filePath, ParseException e) {
    // currentToken is the 'last successfully consumed token', but the error is usually due to the
    // first unsuccessful token.  use that for the source location
    Token errorToken = e.currentToken;
    if (errorToken.next != null) {
      errorToken = errorToken.next;
    }
    ImmutableSet.Builder<String> expectedTokenImages = ImmutableSet.builder();
    for (int[] expected : e.expectedTokenSequences) {
      // We only display the first token
      expectedTokenImages.add(getSoyFileParserTokenDisplayName(expected[0]));
    }
    reporter.report(
        Tokens.createSrcLoc(filePath, errorToken), 
        SoyError.of("{0}"), 
        formatParseExceptionDetails(errorToken.image, expectedTokenImages.build().asList()));
  }

  /** 
   * Returns a human friendly display name for tokens.  By default we use the generated token 
   * image which is appropriate for literal tokens.
   */
  private static String getSoyFileParserTokenDisplayName(int tokenId) {
    switch (tokenId) {
      case SoyFileParserConstants.ATTRIBUTE_VALUE:
        return "attribute-value";
      case SoyFileParserConstants.DELTEMPLATE_OPEN:
        return "{deltemplate";
      case SoyFileParserConstants.TEMPLATE_OPEN:
        return "{template";
      case SoyFileParserConstants.DOTTED_IDENT:
        return "identifier";
      case SoyFileParserConstants.EOF:
        return "eof";
      case SoyFileParserConstants.UNEXPECTED_TOKEN:
        throw new AssertionError("we should never expect the unexpected token");
      default:
        return SoyFileParserConstants.tokenImage[tokenId];
    }
  }

  static void report(ErrorReporter reporter, String filePath, SoySyntaxException exception) {
    SourceLocation sourceLocation = exception.getSourceLocation();
    if (!sourceLocation.isKnown()) {
      sourceLocation = new SourceLocation(filePath);
    }
    reporter.report(sourceLocation, SoyError.of("{0}"), exception.getOriginalMessage());
  }

  static void report(ErrorReporter reporter, String filePath, TokenMgrError exception) {
    reporter.report(new SourceLocation(filePath), SoyError.of("{0}"), exception.getMessage());
  }
}
