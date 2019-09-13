/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.shared.internal.gencode;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utils for writing generated Java classes. */
public final class JavaGenerationUtils {

  /** Pattern for an all-upper-case word in a file name or identifier. */
  private static final Pattern ALL_UPPER_WORD =
      Pattern.compile("(?<= [^A-Za-z] | ^)  [A-Z]+  (?= [^A-Za-z] | $)", Pattern.COMMENTS);

  /** Pattern for an all-lower-case word in a file name or identifier. */
  // Note: Char after an all-lower word can be an upper letter (e.g. first word of camel case).
  private static final Pattern ALL_LOWER_WORD =
      Pattern.compile("(?<= [^A-Za-z] | ^)  [a-z]+  (?= [^a-z] | $)", Pattern.COMMENTS);

  /** Pattern for a character that's not a letter nor a digit. */
  private static final Pattern NON_LETTER_DIGIT = Pattern.compile("[^A-Za-z0-9]");

  private JavaGenerationUtils() {}

  /**
   * Formats and appends a Javadoc comment to the code being built.
   *
   * @param ilb The builder for the code.
   * @param doc The doc string to append as the content of a Javadoc comment. The Javadoc format
   *     will follow the usual conventions. Important: If the doc string is multiple lines, the line
   *     separator must be '\n'.
   * @param forceMultiline If true, we always generate a multiline Javadoc comment even if the doc
   *     string only has one line. If false, we generate either a single line or multiline Javadoc
   *     comment, depending on the doc string.
   * @param wrapAt100Chars If true, wrap at 100 chars.
   */
  public static void appendJavadoc(
      IndentedLinesBuilder ilb, String doc, boolean forceMultiline, boolean wrapAt100Chars) {
    if (wrapAt100Chars) {
      // Actual wrap length is less because of indent and because of space used by Javadoc chars.
      int wrapLen = 100 - ilb.getCurrIndentLen() - 7;
      List<String> wrappedLines = Lists.newArrayList();
      for (String line : Splitter.on('\n').split(doc)) {
        while (line.length() > wrapLen) {
          int spaceIndex = line.lastIndexOf(' ', wrapLen);
          if (spaceIndex >= 0) {
            wrappedLines.add(line.substring(0, spaceIndex));
            line = line.substring(spaceIndex + 1); // add 1 to skip the space
          } else {
            // No spaces. Just wrap at wrapLen.
            wrappedLines.add(line.substring(0, wrapLen));
            line = line.substring(wrapLen);
          }
        }
        wrappedLines.add(line);
      }
      doc = Joiner.on("\n").join(wrappedLines);
    }

    if (doc.contains("\n") || forceMultiline) {
      // Multiline.
      ilb.appendLine("/**");
      for (String line : Splitter.on('\n').split(doc)) {
        ilb.appendLine(" * ", line);
      }
      ilb.appendLine(" */");

    } else {
      // One line.
      ilb.appendLine("/** ", doc, " */");
    }
  }

  /**
   * Creates the upper camel case version of the given string, stripping non-alphanumberic
   * characters.
   *
   * @param str The string to turn into upper camel case.
   * @return The upper camel case version of the string.
   */
  public static String makeUpperCamelCase(String str) {
    str = makeWordsCapitalized(str, ALL_UPPER_WORD);
    str = makeWordsCapitalized(str, ALL_LOWER_WORD);
    str = NON_LETTER_DIGIT.matcher(str).replaceAll("");
    return str;
  }

  public static String makeLowerCamelCase(String str) {
    str = makeUpperCamelCase(str);
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * Makes all the words in the given string into capitalized format (first letter capital, rest
   * lower case). Words are defined by the given regex pattern.
   *
   * @param str The string to process.
   * @param wordPattern The regex pattern for matching a word.
   * @return The resulting string with all words in capitalized format.
   */
  private static String makeWordsCapitalized(String str, Pattern wordPattern) {
    StringBuffer sb = new StringBuffer();

    Matcher wordMatcher = wordPattern.matcher(str);
    while (wordMatcher.find()) {
      String oldWord = wordMatcher.group();
      StringBuilder newWord = new StringBuilder();
      for (int i = 0, n = oldWord.length(); i < n; i++) {
        if (i == 0) {
          newWord.append(Character.toUpperCase(oldWord.charAt(i)));
        } else {
          newWord.append(Character.toLowerCase(oldWord.charAt(i)));
        }
      }
      wordMatcher.appendReplacement(sb, Matcher.quoteReplacement(newWord.toString()));
    }
    wordMatcher.appendTail(sb);

    return sb.toString();
  }

  /**
   * Private helper to append an ImmutableList to the code, on a new line.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableList.
   * @param itemSnippets Code snippets for the items to put into the ImmutableList.
   */
  public static void appendImmutableList(
      IndentedLinesBuilder ilb, String typeParamSnippet, Collection<String> itemSnippets) {
    ilb.appendLineStart("");
    appendImmutableListInline(ilb, typeParamSnippet, itemSnippets);
  }

  /**
   * Private helper to append an ImmutableList to the code, inline.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableList.
   * @param itemSnippets Code snippets for the items to put into the ImmutableList.
   */
  public static void appendImmutableListInline(
      IndentedLinesBuilder ilb, String typeParamSnippet, Collection<String> itemSnippets) {
    appendFunctionCallWithParamsOnNewLines(
        ilb, "ImmutableList." + typeParamSnippet + "of", itemSnippets);
  }

  /**
   * Private helper to append an ImmutableSet to the code, on a new line.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableList.
   * @param itemSnippets Code snippets for the items to put into the ImmutableList.
   */
  public static void appendImmutableSet(
      IndentedLinesBuilder ilb, String typeParamSnippet, Collection<String> itemSnippets) {
    ilb.appendLineStart("");
    appendImmutableSetInline(ilb, typeParamSnippet, itemSnippets);
  }

  /**
   * Private helper to append an ImmutableSet to the code, inline.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableList.
   * @param itemSnippets Code snippets for the items to put into the ImmutableList.
   */
  public static void appendImmutableSetInline(
      IndentedLinesBuilder ilb, String typeParamSnippet, Collection<String> itemSnippets) {
    appendFunctionCallWithParamsOnNewLines(
        ilb, "ImmutableSet." + typeParamSnippet + "of", itemSnippets);
  }

  /**
   * Private helper to append an ImmutableMap to the code.
   *
   * @param ilb The builder for the code.
   * @param typeParamSnippet The type parameter for the ImmutableMap.
   * @param entrySnippetPairs Pairs of (key, value) code snippets for the entries to put into the
   *     ImmutableMap.
   */
  public static void appendImmutableMap(
      IndentedLinesBuilder ilb, String typeParamSnippet, Map<String, String> entrySnippetPairs) {
    if (entrySnippetPairs.isEmpty()) {
      ilb.appendLineStart("ImmutableMap.", typeParamSnippet, "of()");

    } else {
      ilb.appendLine("ImmutableMap.", typeParamSnippet, "builder()");
      ilb.increaseIndent(2);
      for (Map.Entry<String, String> entrySnippetPair : entrySnippetPairs.entrySet()) {
        ilb.appendLine(".put(", entrySnippetPair.getKey(), ", ", entrySnippetPair.getValue(), ")");
      }
      ilb.appendLineStart(".build()");
      ilb.decreaseIndent(2);
    }
  }

  /**
   * Private helper for appendImmutableList() and appendImmutableSet().
   *
   * @param ilb The builder for the code.
   * @param functionCallSnippet Code snippet for the function call (without parenthesis or params).
   * @param params Params to put in parenthesis for the function call.
   */
  private static void appendFunctionCallWithParamsOnNewLines(
      IndentedLinesBuilder ilb, String functionCallSnippet, Collection<String> params) {

    if (params.isEmpty()) {
      ilb.appendParts(functionCallSnippet, "()");
      return;
    }

    ilb.appendLineEnd(functionCallSnippet, "(");
    ilb.increaseIndent(2); // Double indent each param.
    boolean isFirst = true;
    for (String param : params) {
      if (isFirst) {
        isFirst = false;
      } else {
        ilb.appendLineEnd(",");
      }
      ilb.appendLineStart(param);
    }
    ilb.append(")");
    ilb.decreaseIndent(2);
  }
}
