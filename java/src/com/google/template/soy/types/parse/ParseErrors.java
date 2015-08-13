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

package com.google.template.soy.types.parse;

import static com.google.template.soy.base.internal.BaseUtils.formatParseExceptionDetails;

import com.google.common.collect.ImmutableSet;

/**
 * Helpers to interpreting parse exceptions
 */
final class ParseErrors {

  /** Pretty prints the parse exception message. */
  static String formatParseException(ParseException e) {
    Token errorToken = e.currentToken;
    if (errorToken.next != null) {
      errorToken = errorToken.next;
    }

    ImmutableSet.Builder<String> expectedTokenImages = ImmutableSet.builder();
    for (int[] expected : e.expectedTokenSequences) {
      // We only display the first token
      expectedTokenImages.add(getTokenDisplayName(expected[0]));
    }
    return formatParseExceptionDetails(errorToken.image, expectedTokenImages.build().asList());
  }

  /**
   * Returns a human friendly display name for tokens.  By default we use the generated token
   * image which is appropriate for literal tokens.
   */
  private static String getTokenDisplayName(int tokenId) {
    switch (tokenId) {
      case TypeParserConstants.IDENT:
        return "identifier";
      case TypeParserConstants.EOF:
        return "eof";
      case TypeParserConstants.UNEXPECTED_TOKEN:
        throw new AssertionError("we should never expect the unexpected token");
      default:
        return TypeParserConstants.tokenImage[tokenId];
    }
  }

}
