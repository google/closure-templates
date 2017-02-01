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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers for interpreting parse errors as soy errors. */
final class ParseErrors {
  private static final Pattern EXTRACT_LOCATION = Pattern.compile("at line (\\d+), column (\\d+).");
  private static final SoyErrorKind UNEXPECTED_TOKEN_MGR_ERROR =
      SoyErrorKind.of(
          "Unexpected fatal Soy error. Please file a bug with your Soy file and "
              + "we''ll take a look.  {0}");
  private static final SoyErrorKind UNEXPECTED_EOF =
      SoyErrorKind.of(
          "Unexpected end of file.  Did you forget to close an attribute value or a comment?");

  private static final SoyErrorKind INVALID_STRING_LITERAL =
      SoyErrorKind.of("Invalid string literal found in Soy command.");
  private static final SoyErrorKind UNEXPECTED_RIGHT_BRACE =
      SoyErrorKind.of("Unexpected ''}''; did you mean '''{'rb'}'''?");
  private static final SoyErrorKind BAD_PHNAME_VALUE =
      SoyErrorKind.of("Found ''phname'' attribute that is not a valid identifier");
  private static final SoyErrorKind UNEXPECTED_PARAM_DECL =
      SoyErrorKind.of(
          "Unexpected parameter declaration. Param declarations must come before any code in "
              + "your template.");

  private ParseErrors() {}

  static void reportSoyFileParseException(
      ErrorReporter reporter, String filePath, ParseException e) {
    // currentToken is the 'last successfully consumed token', but the error is usually due to the
    // first unsuccessful token.  use that for the source location
    Token errorToken = e.currentToken;
    if (errorToken.next != null) {
      errorToken = errorToken.next;
    }
    SourceLocation location = Tokens.createSrcLoc(filePath, errorToken);
    // handle a few special cases.
    switch (errorToken.kind) {
      case SoyFileParserConstants.XXX_BRACE_INVALID:
        reporter.report(location, UNEXPECTED_RIGHT_BRACE);
        return;
      case SoyFileParserConstants.XXX_INVALID_STRING_LITERAL:
        reporter.report(location, INVALID_STRING_LITERAL);
        return;
      case SoyFileParserConstants.XXX_CMD_TEXT_PHNAME_NOT_IDENT:
        reporter.report(location, BAD_PHNAME_VALUE);
        return;
      case SoyFileParserConstants.DECL_BEGIN_PARAM:
      case SoyFileParserConstants.DECL_BEGIN_OPT_PARAM:
      case SoyFileParserConstants.DECL_BEGIN_INJECT_PARAM:
      case SoyFileParserConstants.DECL_BEGIN_OPT_INJECT_PARAM:
        reporter.report(location, UNEXPECTED_PARAM_DECL);
        return;
      default:
        //fall-through
    }
    ImmutableSet.Builder<String> expectedTokenImages = ImmutableSet.builder();
    for (int[] expected : e.expectedTokenSequences) {
      // We only display the first token of any expected sequence
      expectedTokenImages.add(getSoyFileParserTokenDisplayName(expected[0]));
    }

    reporter.report(
        location,
        SoyErrorKind.of("{0}"),
        formatParseExceptionDetails(errorToken.image, expectedTokenImages.build().asList()));
  }

  /**
   * Returns a human friendly display name for tokens. By default we use the generated token image
   * which is appropriate for literal tokens.
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
        return "{call";
      case SoyFileParserConstants.CMD_CLOSE_CALL:
        return "{/call}";

      case SoyFileParserConstants.CMD_BEGIN_DELCALL:
        return "{delcall";
      case SoyFileParserConstants.CMD_CLOSE_DELCALL:
        return "{/delcall}";

      case SoyFileParserConstants.NAME:
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
      case SoyFileParserConstants.CMD_OPEN_LITERAL:
        return "{literal";

      case SoyFileParserConstants.CMD_FULL_SP:
      case SoyFileParserConstants.CMD_FULL_NIL:
      case SoyFileParserConstants.CMD_FULL_CR:
      case SoyFileParserConstants.CMD_FULL_LF:
      case SoyFileParserConstants.CMD_FULL_TAB:
      case SoyFileParserConstants.CMD_FULL_LB:
      case SoyFileParserConstants.CMD_FULL_RB:
      case SoyFileParserConstants.TOKEN_NOT_WS:
        return "text";
      case SoyFileParserConstants.TOKEN_WS:
        return "whitespace";

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

  static void reportTokenMgrError(
      ErrorReporter reporter, String filePath, TokenMgrError exception) {
    // If the file is terminated in the middle of an attribute value or a multiline comment a
    // TokenMgrError will be thrown (due to our use of MORE productions).  The only way to tell is
    // to test the message for "<EOF>".  The suggested workaround for this is to submit the
    // generated TokenMgrError code into source control and rewrite the constructor.  This would
    // also allow us to avoid using a regex to extract line number information.
    String message = exception.getMessage();
    if (exception.errorCode == TokenMgrError.LEXICAL_ERROR && message.contains("<EOF>")) {
      Matcher line = EXTRACT_LOCATION.matcher(message);
      if (line.find()) {
        int startLine = Integer.parseInt(line.group(1));
        // javacc's column numbers are 0-based, while Soy's are 1-based
        int column = Integer.parseInt(line.group(2)) + 1;
        reporter.report(
            new SourceLocation(filePath, startLine, column, startLine, column), UNEXPECTED_EOF);
        return;
      }
    }
    reporter.report(new SourceLocation(filePath), UNEXPECTED_TOKEN_MGR_ERROR, message);
  }
}
