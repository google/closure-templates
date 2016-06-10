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
import com.google.template.soy.base.internal.LegacyInternalSyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;

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
        SoyErrorKind.of("{0}"),
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

      // File-level tokens:
      case SoyFileParserConstants.DELTEMPLATE_OPEN:
        return "{deltemplate";
      case SoyFileParserConstants.TEMPLATE_OPEN:
        return "{template";

      // Template tokens:
      case SoyFileParserConstants.CMD_BEGIN_CALL:
        return "{(del)call";
      case SoyFileParserConstants.CMD_CLOSE_CALL:
        return "{/(del)call}";

      case SoyFileParserConstants.DOTTED_IDENT:
        return "identifier";
      case SoyFileParserConstants.EOF:
        return "eof";
      // TODO(slaks): Gather all CMD_BEGIN* constants using Reflection & string manipulation?
      case SoyFileParserConstants.CMD_BEGIN_PARAM:
        return "{param";
      case SoyFileParserConstants.CMD_BEGIN_MSG:
        return "{msg";
      case SoyFileParserConstants.CMD_BEGIN_FALLBACK_MSG:
        return "{fallbackmsg";
      case SoyFileParserConstants.CMD_BEGIN_PRINT:
        return "{print";
      case SoyFileParserConstants.CMD_BEGIN_XID:
        return "{xid";
      case SoyFileParserConstants.CMD_BEGIN_CSS:
        return "{css";
      case SoyFileParserConstants.CMD_BEGIN_IF:
        return "{if";
      case SoyFileParserConstants.CMD_BEGIN_ELSEIF:
        return "{elseif";
      case SoyFileParserConstants.CMD_BEGIN_LET:
        return "{let";
      case SoyFileParserConstants.CMD_BEGIN_FOR:
        return "{for";
      case SoyFileParserConstants.CMD_BEGIN_PLURAL:
        return "{plural";
      case SoyFileParserConstants.CMD_BEGIN_SELECT:
        return "{select";
      case SoyFileParserConstants.CMD_BEGIN_SWITCH:
        return "{switch";
      case SoyFileParserConstants.CMD_BEGIN_CASE:
        return "{case";
      case SoyFileParserConstants.CMD_BEGIN_FOREACH:
        return "{foreach";

      case SoyFileParserConstants.UNEXPECTED_TOKEN:
        throw new AssertionError("we should never expect the unexpected token");
      default:
        return SoyFileParserConstants.tokenImage[tokenId];
    }
  }

  static void report(
      ErrorReporter reporter, String filePath, LegacyInternalSyntaxException exception) {
    SourceLocation sourceLocation = exception.getSourceLocation();
    if (!sourceLocation.isKnown()) {
      sourceLocation = new SourceLocation(filePath);
    }
    reporter.report(sourceLocation, SoyErrorKind.of("{0}"), exception.getOriginalMessage());
  }

  static void reportUnexpected(ErrorReporter reporter, String filePath, TokenMgrError exception) {
    reporter.report(new SourceLocation(filePath), SoyErrorKind.of("Unexpected fatal Soy error.  "
        + "Please file a bug with your Soy file and we''ll take a look.  {0}"),
        exception.getMessage());
  }
}
