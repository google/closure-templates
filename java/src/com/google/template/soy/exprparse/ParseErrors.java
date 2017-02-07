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

package com.google.template.soy.exprparse;

import static com.google.template.soy.base.internal.BaseUtils.formatParseExceptionDetails;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;

/** Helpers for interpreting parse errors as soy errors. */
final class ParseErrors {

  private ParseErrors() {}

  static void reportExprParseException(
      ErrorReporter reporter, SourceLocation parentSourceLocation, ParseException e) {
    reportExprParseException(reporter, "", parentSourceLocation, e);
  }

  static void reportExprParseException(
      ErrorReporter reporter,
      String expectation,
      SourceLocation parentSourceLocation,
      ParseException e) {
    // currentToken is the 'last successfully consumed token', but the error is usually due to the
    // first unsuccessful token.  use that for the source location
    Token errorToken = e.currentToken;
    if (errorToken.next != null) {
      errorToken = errorToken.next;
    }
    SourceLocation errorLocation = Tokens.createSrcLoc(parentSourceLocation, errorToken);

    // handle a few special cases that come up in v1->v2 migrations.  We have no production rules
    // that reference these tokens so they will always be unexpected.  Another option we could take
    // is add production rules/matchers for these in the expected places (e.q. allow '&&' in the
    // same place we match 'and'), then we could report errors and keep going in the parser.
    switch (errorToken.kind) {
      case ExpressionParserConstants.LEGACY_AND:
        reporter.report(errorLocation, V1ExpressionErrors.LEGACY_AND_ERROR);
        return;
      case ExpressionParserConstants.LEGACY_OR:
        reporter.report(errorLocation, V1ExpressionErrors.LEGACY_OR_ERROR);
        return;
      case ExpressionParserConstants.LEGACY_NOT:
        reporter.report(errorLocation, V1ExpressionErrors.LEGACY_NOT_ERROR);
        return;
      case ExpressionParserConstants.DOUBLE_QUOTE:
        reporter.report(errorLocation, V1ExpressionErrors.LEGACY_DOUBLE_QUOTED_STRING);
        return;
    }
    // otherwise log a generic unexpected token error message
    ImmutableSet.Builder<String> expectedTokenImages = ImmutableSet.builder();
    for (int[] expected : e.expectedTokenSequences) {
      // We only display the first token
      expectedTokenImages.add(getSoyFileParserTokenDisplayName(expected[0]));
    }
    reporter.report(
        errorLocation,
        SoyErrorKind.of(expectation + "{0}"),
        formatParseExceptionDetails(errorToken.image, expectedTokenImages.build().asList()));
  }

  /**
   * Returns a human friendly display name for tokens. By default we use the generated token image
   * which is appropriate for literal tokens.
   */
  private static String getSoyFileParserTokenDisplayName(int tokenId) {
    switch (tokenId) {
      case ExpressionParserConstants.EOF:
        return "eof";
      case ExpressionParserConstants.HEX_INTEGER:
      case ExpressionParserConstants.DEC_INTEGER:
      case ExpressionParserConstants.FLOAT:
        return "number";
      case ExpressionParserConstants.STRING:
        return "string";
      case ExpressionParserConstants.IDENT:
        return "an identifier";
      case ExpressionParserConstants.DOLLAR_IDENT:
        return "variable";
      case ExpressionParserConstants.UNEXPECTED_TOKEN:
        throw new AssertionError("we should never expect the unexpected token");
      default:
        return ExpressionParserConstants.tokenImage[tokenId];
    }
  }
}
