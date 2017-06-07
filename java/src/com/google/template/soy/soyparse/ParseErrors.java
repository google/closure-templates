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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.LegacyInternalSyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers for interpreting parse errors as soy errors. */
final class ParseErrors {
  static final SoyErrorKind PLAIN_ERROR = SoyErrorKind.of("{0}", StyleAllowance.values());

  private static final Pattern EXTRACT_LOCATION = Pattern.compile("at line (\\d+), column (\\d+).");

  private static final SoyErrorKind FOUND_DOUBLE_BRACE =
      SoyErrorKind.of("Soy '{{command}}' syntax is no longer supported. Use single braces.");
  private static final SoyErrorKind INVALID_TEMPLATE_COMMAND =
      SoyErrorKind.of("Command ''{0}'' cannot appear in templates.");
  private static final SoyErrorKind LEGACY_AND_ERROR =
      SoyErrorKind.of("Found use of ''&&'' instead of the ''and'' operator.");
  private static final SoyErrorKind LEGACY_OR_ERROR =
      SoyErrorKind.of("Found use of ''||'' instead of the ''or'' operator.");
  private static final SoyErrorKind LEGACY_NOT_ERROR =
      SoyErrorKind.of("Found use of ''!'' instead of the ''not'' operator.");
  private static final SoyErrorKind LEGACY_DOUBLE_QUOTED_STRING =
      SoyErrorKind.of("Found use of double quotes, Soy strings use single quotes.");
  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG =
      SoyErrorKind.of("Unexpected closing tag.");
  private static final SoyErrorKind UNEXPECTED_EOF =
      SoyErrorKind.of(
          "Unexpected end of file.  Did you forget to close an attribute value or a comment?");
  private static final SoyErrorKind UNEXPECTED_NEWLINE =
      SoyErrorKind.of("Unexpected newline in Soy string.");
  private static final SoyErrorKind UNEXPECTED_PARAM_DECL =
      SoyErrorKind.of(
          "Unexpected parameter declaration. Param declarations must come before any code in "
              + "your template.");
  private static final SoyErrorKind UNEXPECTED_PIPE =
      SoyErrorKind.of("Unexpected ''|''. Print directives should not have whitespace after ''|''.");
  private static final SoyErrorKind UNEXPECTED_RIGHT_BRACE =
      SoyErrorKind.of("Unexpected ''}''; did you mean '''{'rb'}'''?");
  private static final SoyErrorKind UNEXPECTED_TOKEN_MGR_ERROR =
      SoyErrorKind.of(
          "Unexpected fatal Soy error. Please file a bug with your Soy file and "
              + "we''ll take a look. (error code {0})\n{1}",
          StyleAllowance.NO_PUNCTUATION);

  private static final CharMatcher TOKENS_TO_QUOTE =
      CharMatcher.whitespace().or(CharMatcher.is(',')).or(CharMatcher.is(':')).precomputed();

  private ParseErrors() {}

  static void reportSoyFileParseException(
      ErrorReporter reporter, String filePath, ParseException e) {
    Token currentToken = e.currentToken;

    // currentToken is the 'last successfully consumed token', but the error is usually due to the
    // first unsuccessful token.  use that for the source location
    Token errorToken = (currentToken.next != null) ? currentToken.next : currentToken;
    SourceLocation location = Tokens.createSrcLoc(filePath, errorToken);

    // handle a few special cases.
    switch (errorToken.kind) {
      case SoyFileParserConstants.XXX_BRACE_INVALID:
        reporter.report(location, UNEXPECTED_RIGHT_BRACE);
        return;
      case SoyFileParserConstants.DECL_BEGIN_PARAM:
      case SoyFileParserConstants.DECL_BEGIN_OPT_PARAM:
      case SoyFileParserConstants.DECL_BEGIN_INJECT_PARAM:
      case SoyFileParserConstants.DECL_BEGIN_OPT_INJECT_PARAM:
        reporter.report(location, UNEXPECTED_PARAM_DECL);
        return;
      case SoyFileParserConstants.DOUBLE_QUOTE:
        reporter.report(location, LEGACY_DOUBLE_QUOTED_STRING);
        return;
      case SoyFileParserConstants.LEGACY_AND:
        reporter.report(location, LEGACY_AND_ERROR);
        return;
      case SoyFileParserConstants.LEGACY_OR:
        reporter.report(location, LEGACY_OR_ERROR);
        return;
      case SoyFileParserConstants.LEGACY_NOT:
        reporter.report(location, LEGACY_NOT_ERROR);
        return;
      case SoyFileParserConstants.UNEXPECTED_PIPE:
        reporter.report(location, UNEXPECTED_PIPE);
        return;
      case SoyFileParserConstants.UNEXPECTED_DOUBLE_BRACE:
        reporter.report(location, FOUND_DOUBLE_BRACE);
        return;
      case SoyFileParserConstants.UNEXPECTED_CLOSE_TAG:
        reporter.report(location, UNEXPECTED_CLOSE_TAG);
        return;
      case SoyFileParserConstants.UNEXPECTED_ALIAS:
      case SoyFileParserConstants.UNEXPECTED_NAMESPACE:
      case SoyFileParserConstants.UNEXPECTED_DELPACKAGE:
      case SoyFileParserConstants.UNEXPECTED_TEMPLATE:
      case SoyFileParserConstants.UNEXPECTED_DELTEMPLATE:
        reporter.report(location, INVALID_TEMPLATE_COMMAND, errorToken.image);
        return;
      case SoyFileParserConstants.UNEXPECTED_NEWLINE:
        reporter.report(location, UNEXPECTED_NEWLINE);
        return;
      case SoyFileParserConstants.EOF:
        reporter.report(location, UNEXPECTED_EOF);
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
        PLAIN_ERROR,
        formatParseExceptionDetails(errorToken.image, expectedTokenImages.build().asList()));
  }

  /**
   * Returns a human friendly display name for tokens. By default we use the generated token image
   * which is appropriate for literal tokens.
   */
  private static String getSoyFileParserTokenDisplayName(int tokenId) {
    switch (tokenId) {

        // File-level tokens:
      case SoyFileParserConstants.DELTEMPLATE_OPEN:
        return "{deltemplate";
      case SoyFileParserConstants.TEMPLATE_OPEN:
        return "{template";
      case SoyFileParserConstants.EOF:
        return "eof";

        // Template tokens:
      case SoyFileParserConstants.CMD_BEGIN_CALL:
        return "{call";
      case SoyFileParserConstants.CMD_BEGIN_DELCALL:
        return "{delcall";
      case SoyFileParserConstants.CMD_BEGIN_PARAM:
        return "{param";
      case SoyFileParserConstants.CMD_BEGIN_MSG:
        return "{msg";
      case SoyFileParserConstants.CMD_BEGIN_FALLBACK_MSG:
        return "{fallbackmsg";
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
      case SoyFileParserConstants.CMD_BEGIN_PRINT:
        return "{print";

      case SoyFileParserConstants.NAME:
      case SoyFileParserConstants.T_NAME:
      case SoyFileParserConstants.IDENT:
      case SoyFileParserConstants.CSS_SELECTOR:
        return "identifier";
      case SoyFileParserConstants.PRINT_DIRECTIVE:
        return "print directive";
      case SoyFileParserConstants.DATA_ATTR:
      case SoyFileParserConstants.VARIANT_ATTR:
      case SoyFileParserConstants.GENDERS_ATTR:
        return "attribute name";
      case SoyFileParserConstants.ATTRIBUTE_VALUE:
      case SoyFileParserConstants.EQ_QUOTE:
        return "attribute value";

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

        // Expression tokens:
      case SoyFileParserConstants.HEX_INTEGER:
      case SoyFileParserConstants.DEC_INTEGER:
      case SoyFileParserConstants.FLOAT:
        return "number";
      case SoyFileParserConstants.SINGLE_QUOTE:
        return "string";
      case SoyFileParserConstants.DOLLAR_IDENT:
        return "variable";

      case SoyFileParserConstants.UNEXPECTED_TOKEN:
        throw new AssertionError("we should never expect the unexpected token");

      default:
        return SoyFileParserConstants.tokenImage[tokenId];
    }
  }

  static void reportLegacyInternalSyntaxException(
      ErrorReporter reporter, String filePath, LegacyInternalSyntaxException exception) {
    SourceLocation sourceLocation = exception.getSourceLocation();
    if (!sourceLocation.isKnown()) {
      sourceLocation = new SourceLocation(filePath);
    }
    reporter.report(sourceLocation, PLAIN_ERROR, exception.getOriginalMessage());
  }

  static void reportTokenMgrError(
      ErrorReporter reporter, String filePath, TokenMgrError exception) {
    int errorCode = exception.errorCode;
    String message = exception.getMessage();

    SourceLocation location;
    Matcher loc = EXTRACT_LOCATION.matcher(message);
    if (loc.find()) {
      int line = Integer.parseInt(loc.group(1));
      // javacc's column numbers are 0-based, while Soy's are 1-based
      int column = Integer.parseInt(loc.group(2)) + 1;
      location = new SourceLocation(filePath, line, column, line, column);
    } else {
      location = new SourceLocation(filePath);
    }

    // If the file is terminated in the middle of an attribute value or a multiline comment a
    // TokenMgrError will be thrown (due to our use of MORE productions).  The only way to tell is
    // to test the message for "<EOF>".  The suggested workaround for this is to submit the
    // generated TokenMgrError code into source control and rewrite the constructor.  This would
    // also allow us to avoid using a regex to extract line number information.
    if (exception.errorCode == TokenMgrError.LEXICAL_ERROR && message.contains("<EOF>")) {
      reporter.report(location, UNEXPECTED_EOF);
    } else {
      reporter.report(location, UNEXPECTED_TOKEN_MGR_ERROR, errorCode, message);
    }
  }

  /**
   * A helper method for formating javacc ParseExceptions.
   *
   * @param errorToken The piece of text that we were unable to parse.
   * @param expectedTokens The set of formatted tokens that we were expecting next.
   */
  private static String formatParseExceptionDetails(
      String errorToken, List<String> expectedTokens) {
    // quotes/normalize the expected tokens before rendering, just in case after normalization some
    // can be deduplicated.
    ImmutableSet.Builder<String> normalizedTokensBuilder = ImmutableSet.builder();
    for (String t : expectedTokens) {
      normalizedTokensBuilder.add(maybeQuoteForParseError(t));
    }
    expectedTokens = normalizedTokensBuilder.build().asList();

    StringBuilder details = new StringBuilder();
    int numExpectedTokens = expectedTokens.size();
    if (numExpectedTokens != 0) {
      details.append(": expected ");
      for (int i = 0; i < numExpectedTokens; i++) {
        details.append(expectedTokens.get(i));
        if (i < numExpectedTokens - 2) {
          details.append(", ");
        }
        if (i == numExpectedTokens - 2) {
          if (numExpectedTokens > 2) {
            details.append(',');
          }
          details.append(" or ");
        }
      }
    }

    return String.format(
        "parse error at '%s'%s", escapeWhitespaceForErrorPrinting(errorToken), details.toString());
  }

  private static String maybeQuoteForParseError(String token) {
    // the literal matches are surrounded in double quotes, so remove them
    if (token.length() > 1 && token.charAt(0) == '"' && token.charAt(token.length() - 1) == '"') {
      token = token.substring(1, token.length() - 1);
    }

    // if the token starts or ends with a whitespace character, a comma, or a colon, then put them
    // in single quotes to avoid ambiguity in the error messages.
    if (TOKENS_TO_QUOTE.matches(token.charAt(0))
        || TOKENS_TO_QUOTE.matches(token.charAt(token.length() - 1))) {
      token = "'" + token + "'";
    }

    return escapeWhitespaceForErrorPrinting(token);
  }

  private static String escapeWhitespaceForErrorPrinting(String s) {
    s = s.replace("\r", "\\r");
    s = s.replace("\n", "\\n");
    s = s.replace("\t", "\\t");
    return s;
  }
}
