/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;

/** Shared utilities for the 'soyparse' package. */
final class SoyParseUtils {

  private static final SoyErrorKind CALL_COLLIDES_WITH_NAMESPACE_ALIAS =
      SoyErrorKind.of("Call collides with namespace alias ''{0}''.");
  private static final SoyErrorKind END_OF_STRING =
      SoyErrorKind.of("End-of-string after escape character.");
  private static final SoyErrorKind INVALID_UNICODE_SEQUENCE =
      SoyErrorKind.of("Invalid unicode sequence ''{0}''.");
  private static final SoyErrorKind UNKNOWN_ESCAPE_CODE =
      SoyErrorKind.of("Unknown escape code ''{0}''.");

  /** Given a template call and file header info, return the expanded callee name if possible. */
  @SuppressWarnings("unused") // called in SoyFileParser.jj
  public static final String calculateFullCalleeName(
      Identifier ident, SoyFileHeaderInfo header, ErrorReporter errorReporter) {

    String name = ident.identifier();
    switch (ident.type()) {
      case DOT_IDENT:
        // Case 1: Source callee name is partial.
        return header.namespace + name;
      case DOTTED_IDENT:
        // Case 2: Source callee name is a proper dotted ident.
        int firstDot = name.indexOf('.');
        String alias = header.aliasToNamespaceMap.get(name.substring(0, firstDot));

        // Case 2a: Source callee name's first part is an alias.
        if (alias != null) {
          return alias + name.substring(firstDot);
        }

        // Case 2b: Source callee name's first part is not an alias.
        return name;
      case SINGLE_IDENT:
        // Case 3: Source callee name is a single ident (not dotted).
        if (header.aliasToNamespaceMap.containsKey(name)) {
          errorReporter.report(ident.location(), CALL_COLLIDES_WITH_NAMESPACE_ALIAS, name);
        }
        return name;
      default:
        throw new AssertionError();
    }
  }

  /** Unescapes a Soy string, according to JavaScript rules. */
  @SuppressWarnings("unused") // called in SoyFileParser.jj
  public static String unescapeString(String s, ErrorReporter errorReporter, SourceLocation loc) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); ) {
      char c = s.charAt(i);
      if (c == '\\') {
        i = doUnescape(s, i + 1, sb, errorReporter, loc);
      } else {
        sb.append(c);
        i++;
      }
    }
    return sb.toString();
  }

  /**
   * Looks for an escape code starting at index i of s, and appends it to sb.
   *
   * @return the index of the first character in s after the escape code.
   */
  private static int doUnescape(
      String s, int i, StringBuilder sb, ErrorReporter errorReporter, SourceLocation loc) {
    if (i >= s.length()) {
      errorReporter.report(loc.offsetStartCol(i), END_OF_STRING);
      return i;
    }

    char c = s.charAt(i++);
    switch (c) {
      case 'n':
        sb.append('\n');
        break;
      case 'r':
        sb.append('\r');
        break;
      case 't':
        sb.append('\t');
        break;
      case 'b':
        sb.append('\b');
        break;
      case 'f':
        sb.append('\f');
        break;
      case '\\':
      case '\"':
      case '\'':
      case '>':
        sb.append(c);
        break;
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
        --i; // backup to first octal digit
        int nOctalDigits = 1;
        int digitLimit = c < '4' ? 3 : 2;
        while (nOctalDigits < digitLimit
            && i + nOctalDigits < s.length()
            && isOctal(s.charAt(i + nOctalDigits))) {
          ++nOctalDigits;
        }
        sb.append((char) Integer.parseInt(s.substring(i, i + nOctalDigits), 8));
        i += nOctalDigits;
        break;
      case 'x':
      case 'u':
        String hexCode;
        int nHexDigits = (c == 'u' ? 4 : 2);
        try {
          hexCode = s.substring(i, i + nHexDigits);
        } catch (IndexOutOfBoundsException ioobe) {
          errorReporter.report(loc.offsetStartCol(i + 1), INVALID_UNICODE_SEQUENCE, s.substring(i));
          return i + nHexDigits;
        }

        int unicodeValue;
        try {
          unicodeValue = Integer.parseInt(hexCode, 16);
        } catch (NumberFormatException nfe) {
          errorReporter.report(loc.offsetStartCol(i + 1), INVALID_UNICODE_SEQUENCE, hexCode);
          return i + nHexDigits;
        }

        sb.append((char) unicodeValue);
        i += nHexDigits;
        break;
      default:
        errorReporter.report(loc.offsetStartCol(i), UNKNOWN_ESCAPE_CODE, c);
        return i;
    }

    return i;
  }

  private static boolean isOctal(char c) {
    return (c >= '0') && (c <= '7');
  }

  /** Unescapes backslash-double quote sequences in an attribute value to just double quote. */
  static String unescapeCommandAttributeValue(String s) {
    // NOTE: we don't just use String.replace since it internally allocates/compiles a regular
    // expression.  Instead we have a handrolled loop.
    int index = s.indexOf("\\\"");
    if (index == -1) {
      return s;
    }
    StringBuilder buf = new StringBuilder(s.length());
    buf.append(s);
    boolean nextIsDQ = buf.charAt(s.length() - 1) == '"';
    for (int i = s.length() - 2; i >= index; i--) {
      char c = buf.charAt(i);
      if (c == '\\' && nextIsDQ) {
        buf.deleteCharAt(i);
      }
      nextIsDQ = c == '"';
    }
    return buf.toString();
  }
}
