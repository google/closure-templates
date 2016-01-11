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

/**
 * Helpers for interpreting parse errors as soy errors.
 */
final class ParseErrors {

  private static final SoyErrorKind LEGACY_AND_ERROR =
      SoyErrorKind.of("Found use of ''&&'' instead of the ''and'' operator");
  private static final SoyErrorKind LEGACY_OR_ERROR =
      SoyErrorKind.of("Found use of ''||'' instead of the ''or'' operator");
  private static final SoyErrorKind LEGACY_NOT_ERROR =
      SoyErrorKind.of("Found use of ''!'' instead of the ''not'' operator");
  private static final SoyErrorKind LEGACY_DOUBLE_QUOTED_STRING =
      SoyErrorKind.of("Found use of double quotes, Soy strings use single quotes");

  private ParseErrors() {}

  static void reportExprParseException(
      ErrorReporter reporter, SourceLocation location, ParseException e) {
    reportExprParseException(reporter, "", location, e);
  }

  static void reportExprParseException(
      ErrorReporter reporter, String expectation, SourceLocation location, ParseException e) {
    // currentToken is the 'last successfully consumed token', but the error is usually due to the
    // first unsuccessful token.  use that for the source location
    Token errorToken = e.currentToken;
    if (errorToken.next != null) {
      errorToken = errorToken.next;
    }

    // handle a few special cases that come up in v1->v2 migrations.  We have no production rules
    // that reference these tokens so they will always be unexpected.  Another option we could take
    // is add production rules/matchers for these in the expected places (e.q. allow '&&' in the
    // same place we match 'and'), then we could report errors and keep going in the parser.
    switch (errorToken.kind) {
      case ExpressionParserConstants.LEGACY_AND:
        reporter.report(location, LEGACY_AND_ERROR);
        return;
      case ExpressionParserConstants.LEGACY_OR:
        reporter.report(location, LEGACY_OR_ERROR);
        return;
      case ExpressionParserConstants.LEGACY_NOT:
        reporter.report(location, LEGACY_NOT_ERROR);
        return;
      case ExpressionParserConstants.DOUBLE_QUOTE:
        reporter.report(location, LEGACY_DOUBLE_QUOTED_STRING);
        return;
    }
    // otherwise log a generic unexpected token error message
    ImmutableSet.Builder<String> expectedTokenImages = ImmutableSet.builder();
    for (int[] expected : e.expectedTokenSequences) {
      // We only display the first token
      expectedTokenImages.add(getSoyFileParserTokenDisplayName(expected[0]));
    }
    reporter.report(
        // TODO(lukes): we should offset the location by the location of the errorToken however
        // expr source locations are already iffy, so doing random math on them is unlikely to help
        // anything.  Revisit when we stop extracting expressions from SoyNode command texts with
        // regular expressions.
        location,
        SoyErrorKind.of(expectation + "{0}"),
        formatParseExceptionDetails(errorToken.image, expectedTokenImages.build().asList()));
  }

  /**
   * Returns a human friendly display name for tokens.  By default we use the generated token
   * image which is appropriate for literal tokens.
   */
  private static String getSoyFileParserTokenDisplayName(int tokenId) {
    switch (tokenId) {
      case ExpressionParserConstants.EOF:
        return "eof";
      case ExpressionParserConstants.PRECEDENCE_2_OP:
      case ExpressionParserConstants.PRECEDENCE_3_OP:
      case ExpressionParserConstants.PRECEDENCE_4_OP:
      case ExpressionParserConstants.PRECEDENCE_5_OP:
      case ExpressionParserConstants.PRECEDENCE_6_OP:
      case ExpressionParserConstants.PRECEDENCE_7_OP:
        return "an operator";
      case ExpressionParserConstants.IDENT:
        return "an identifier";
      case ExpressionParserConstants.DOT_IDENT:
      case ExpressionParserConstants.QUESTION_DOT_IDENT:
        return "field access";
      case ExpressionParserConstants.DOLLAR_IDENT:
        return "variable";
      case ExpressionParserConstants.UNEXPECTED_TOKEN:
        throw new AssertionError("we should never expect the unexpected token");
      default:
        return ExpressionParserConstants.tokenImage[tokenId];
    }
  }
}
