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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.NumberNode;
import com.google.template.soy.exprtree.StringNode;
import com.ibm.icu.text.MessagePattern;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers for interpreting parse errors as soy errors. */
final class ParseErrors {
  private static final SoyErrorKind PLAIN_ERROR = SoyErrorKind.of("{0}", StyleAllowance.values());

  private static final Pattern EXTRACT_LOCATION = Pattern.compile("at line (\\d+), column (\\d+).");

  private static final SoyErrorKind FOUND_DOUBLE_BRACE =
      SoyErrorKind.of("Soy '{{command}}' syntax is no longer supported. Use single braces.");
  ;
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

  /** Reports a generic parsing exception (such as: "Error at '}': expected number, string..."). */
  static void reportSoyFileParseException(
      ErrorReporter reporter, SourceFilePath filePath, ParseException e, int currentLexicalState) {
    reportSoyFileParseException(reporter, filePath, e, currentLexicalState, "");
  }

  /**
   * Reports a parsing exception.
   *
   * <p>Takes in an optional "advice" message to give the user context-specific suggestions for
   * common syntax errors (e.g. if the exception occurred while parsing a for-loop, the message
   * might show the user correct for-loop syntax). This will be displayed after the generic "Error
   * at '}': expected ..." message.
   */
  static void reportSoyFileParseException(
      ErrorReporter reporter,
      SourceFilePath filePath,
      ParseException e,
      int currentLexicalState,
      String optionalAdvice) {

    if (e instanceof SoyFileParser.ParseExceptionWithAdvice) {
      optionalAdvice = ((SoyFileParser.ParseExceptionWithAdvice) e).advice;
      e = ((SoyFileParser.ParseExceptionWithAdvice) e).original;
    }

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
      case SoyFileParserConstants.FOR:
      case SoyFileParserConstants.IN:
        if (optionalAdvice.isEmpty()) {
          // If we don't already have a context-specific suggestion, warn the user the "in" and
          // "for" are reserved words.
          optionalAdvice +=
              ".\nNote: "
                  + Tokens.getTokenDisplayName(errorToken.kind)
                  + " is a reserved word in soy.";
        }
        break;
      case SoyFileParserConstants.UNEXPECTED_DOUBLE_BRACE:
        reporter.report(location, FOUND_DOUBLE_BRACE);
        return;
      case SoyFileParserConstants.UNEXPECTED_CLOSE_TAG:
        reporter.report(location, UNEXPECTED_CLOSE_TAG);
        return;
      case SoyFileParserConstants.UNEXPECTED_NEWLINE:
        reporter.report(location, UNEXPECTED_NEWLINE);
        return;
      case SoyFileParserConstants.EOF:
        // The image for this token is usually pointing at some whitespace, which is confusing
        errorToken.image = "eof";
        if (currentLexicalState == SoyFileParserConstants.IN_DQ_ATTRIBUTE_VALUE
            || currentLexicalState == SoyFileParserConstants.IN_SQ_ATTRIBUTE_VALUE) {
          optionalAdvice += ". Did you forget to close an attribute?";
        } else if (currentLexicalState == SoyFileParserConstants.IN_MULTILINE_COMMENT
            || currentLexicalState == SoyFileParserConstants.IN_SOYDOC) {
          optionalAdvice += ". Did you forget to close a comment?";
        }
        // fall-through
      default:
        // fall-through
    }

    ImmutableSet.Builder<String> expectedTokenImages = ImmutableSet.builder();
    for (int[] expected : e.expectedTokenSequences) {
      // We only display the first token of any expected sequence
      String displayName = Tokens.getTokenDisplayName(expected[0]);
      if (displayName != null) {
        expectedTokenImages.add(displayName);
      }
    }

    reporter.report(
        location,
        PLAIN_ERROR,
        formatParseExceptionDetails(errorToken.image, expectedTokenImages.build().asList())
            + optionalAdvice);
  }

  static void reportSoyFileParseException(
      ErrorReporter reporter, SourceFilePath filePath, Token currentToken, String optionalAdvice) {
    // currentToken is the 'last successfully consumed token', but the error is usually due to the
    // first unsuccessful token.  use that for the source location
    Token errorToken = (currentToken.next != null) ? currentToken.next : currentToken;
    SourceLocation location = Tokens.createSrcLoc(filePath, errorToken);

    reporter.report(
        location,
        PLAIN_ERROR,
        formatParseExceptionDetails(errorToken.image, ImmutableList.of()) + optionalAdvice);
  }

  static void reportTokenMgrError(
      ErrorReporter reporter, SourceFilePath filePath, TokenMgrError exception) {
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
   * A helper method for formatting javacc ParseExceptions.
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
        "parse error at '%s'%s", escapeWhitespaceForErrorPrinting(errorToken), details);
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

  private static final SoyErrorKind SELECT_CASE_INVALID_VALUE =
      SoyErrorKind.of(
          "Invalid value for select ''case'', "
              + "expected an identifier (most commonly, a gender).{0}",
          StyleAllowance.NO_PUNCTUATION);

  /** Validates an expression being used as a {@code case} label in a {@code select}. */
  static String validateSelectCaseLabel(ExprNode caseValue, ErrorReporter reporter) {
    boolean isNumeric;
    boolean isError;
    String value;
    if (caseValue instanceof StringNode) {
      value = ((StringNode) caseValue).getValue();
      // Validate that our select cases are argument names as required by the ICU MessageFormat
      // library. We can offer a much better user experience by doing this validation eagerly.
      // NOTE: in theory we could allow numeric select cases, but this is almost always an error
      // (they should have used plural), if there turns out to be some good reason for this (e.g.
      // the numbers are reflect ordinals instead of cardinals), then we can revisit this error.
      int argNumber = MessagePattern.validateArgumentName(value);
      if (argNumber != MessagePattern.ARG_NAME_NOT_NUMBER) {
        try {
          // there are more efficient ways to do this, but we are already in an error case so who
          // cares
          Long.parseLong(value);
          isNumeric = true;
        } catch (NumberFormatException nfe) {
          isNumeric = false;
        }
        isError = true;
      } else {
        isNumeric = false;
        isError = false;
      }
    } else {
      isNumeric = caseValue instanceof NumberNode;
      isError = true;
      value = "";
    }
    if (isError) {
      reporter.report(
          caseValue.getSourceLocation(),
          SELECT_CASE_INVALID_VALUE,
          isNumeric ? "  Did you mean to use {plural} instead of {select}?" : "");
    }
    return value;
  }

  private static String escapeWhitespaceForErrorPrinting(String s) {
    s = s.replace("\r", "\\r");
    s = s.replace("\n", "\\n");
    s = s.replace("\t", "\\t");
    return s;
  }
}
