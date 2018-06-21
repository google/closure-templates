/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.msgs.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.msgs.restricted.MsgPartUtils;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for building msg parts with ICU syntax.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class IcuSyntaxUtils {

  private IcuSyntaxUtils() {}

  /**
   * Given a list of msg parts: (a) if it contains any plural/select parts, then builds a new list
   * of msg parts where plural/select parts in the original msg parts are all embedded as raw text
   * in ICU format, (b) if it doesn't contain any plural/select parts, then simply returns the
   * original msg parts instead of creating a new list of identical msg parts.
   *
   * @param origMsgParts The msg parts to convert.
   * @return A new list of msg parts with embedded ICU syntax if the original msg parts contain
   *     plural/select parts, otherwise the original msg parts.
   */
  public static ImmutableList<SoyMsgPart> convertMsgPartsToEmbeddedIcuSyntax(
      ImmutableList<SoyMsgPart> origMsgParts) {

    // If origMsgParts doesn't have plural/select parts, simply return it.
    if (!MsgPartUtils.hasPlrselPart(origMsgParts)) {
      return origMsgParts;
    }

    // Build the new msg parts.
    ImmutableList.Builder<SoyMsgPart> newMsgPartsBuilder = ImmutableList.builder();
    StringBuilder currRawTextSb = new StringBuilder();

    convertMsgPartsHelper(
        newMsgPartsBuilder, currRawTextSb, origMsgParts, /* isInPlrselPart= */ false);
    if (currRawTextSb.length() > 0) {
      newMsgPartsBuilder.add(SoyMsgRawTextPart.of(currRawTextSb.toString()));
    }

    return newMsgPartsBuilder.build();
  }

  /**
   * Private helper for {@code convertMsgPartsToEmbeddedIcuSyntax()} to convert msg parts.
   *
   * @param newMsgPartsBuilder The new msg parts being built.
   * @param currRawTextSb The collector for the current raw text, which hasn't yet been turned into
   *     a SoyMsgRawTextPart and added to newMsgPartsBuilder because it might not be complete.
   * @param origMsgParts The msg parts to convert.
   * @param isInPlrselPart Whether we're currently within a plural/select part's subtree.
   */
  private static void convertMsgPartsHelper(
      ImmutableList.Builder<SoyMsgPart> newMsgPartsBuilder,
      StringBuilder currRawTextSb,
      List<SoyMsgPart> origMsgParts,
      boolean isInPlrselPart) {

    for (SoyMsgPart origMsgPart : origMsgParts) {

      if (origMsgPart instanceof SoyMsgRawTextPart) {
        String rawText = ((SoyMsgRawTextPart) origMsgPart).getRawText();
        if (isInPlrselPart) {
          rawText = icuEscape(rawText);
        }
        currRawTextSb.append(rawText);

      } else if (origMsgPart instanceof SoyMsgPlaceholderPart) {
        // A placeholder ends the curr raw text, so if the collected curr raw text is nonempty, add
        // a msg part for it and clear the collector.
        if (currRawTextSb.length() > 0) {
          newMsgPartsBuilder.add(SoyMsgRawTextPart.of(currRawTextSb.toString()));
          currRawTextSb.setLength(0);
        }
        // Reuse the msg part for the placeholder since it's immutable.
        newMsgPartsBuilder.add(origMsgPart);

      } else if (origMsgPart instanceof SoyMsgPluralRemainderPart) {
        currRawTextSb.append(getPluralRemainderString());

      } else if (origMsgPart instanceof SoyMsgPluralPart) {
        convertPluralPartHelper(newMsgPartsBuilder, currRawTextSb, (SoyMsgPluralPart) origMsgPart);

      } else if (origMsgPart instanceof SoyMsgSelectPart) {
        convertSelectPartHelper(newMsgPartsBuilder, currRawTextSb, (SoyMsgSelectPart) origMsgPart);
      }
    }
  }

  /**
   * Private helper for {@code convertMsgPartsToEmbeddedIcuSyntax()} to convert a plural part.
   *
   * @param newMsgPartsBuilder The new msg parts being built.
   * @param currRawTextSb The collector for the current raw text, which hasn't yet been turned into
   *     a SoyMsgRawTextPart and added to newMsgPartsBuilder because it might not be complete.
   * @param origPluralPart The plural part to convert.
   */
  private static void convertPluralPartHelper(
      Builder<SoyMsgPart> newMsgPartsBuilder,
      StringBuilder currRawTextSb,
      SoyMsgPluralPart origPluralPart) {

    currRawTextSb.append(
        getPluralOpenString(origPluralPart.getPluralVarName(), origPluralPart.getOffset()));

    for (Case<SoyMsgPluralCaseSpec> pluralCase : origPluralPart.getCases()) {
      currRawTextSb.append(getPluralCaseOpenString(pluralCase.spec()));
      convertMsgPartsHelper(
          newMsgPartsBuilder, currRawTextSb, pluralCase.parts(), /* isInPlrselPart= */ true);
      currRawTextSb.append(getPluralCaseCloseString());
    }

    currRawTextSb.append(getPluralCloseString());
  }

  /**
   * Private helper for {@code convertMsgPartsToEmbeddedIcuSyntax()} to convert a select part.
   *
   * @param newMsgPartsBuilder The new msg parts being built.
   * @param currRawTextSb The collector for the current raw text, which hasn't yet been turned into
   *     a SoyMsgRawTextPart and added to newMsgPartsBuilder because it might not be complete.
   * @param origSelectPart The select part to convert.
   */
  private static void convertSelectPartHelper(
      Builder<SoyMsgPart> newMsgPartsBuilder,
      StringBuilder currRawTextSb,
      SoyMsgSelectPart origSelectPart) {

    currRawTextSb.append(getSelectOpenString(origSelectPart.getSelectVarName()));

    for (Case<String> selectCase : origSelectPart.getCases()) {
      currRawTextSb.append(getSelectCaseOpenString(selectCase.spec()));
      convertMsgPartsHelper(
          newMsgPartsBuilder, currRawTextSb, selectCase.parts(), /* isInPlrselPart= */ true);
      currRawTextSb.append(getSelectCaseCloseString());
    }

    currRawTextSb.append(getSelectCloseString());
  }

  // -----------------------------------------------------------------------------------------------
  // Private low-level helpers.

  // A typical Plural command is as follows:
  // {plural $num_people offset="1"}
  //   {case 0}Case 0 statement.
  //   {case 1}Case 1 statement.
  //   {default}Default statement for {remainder{$num_people}} out of {$num_people}.
  // {/plural}
  //
  // The corresponding ICU syntax string is:
  // {numPeople,plural,offset=1
  //   =0{Case 0 statement.}
  //   =1{Case 1 statement}
  //   other{Default statement for # out of {$numPeople}.}
  // }
  //
  // (The variable name "numPeople" may be different depending on what purpose the
  // string is generated.)
  //
  // Similarly, a typical select case:
  //
  // {select $gender}
  //   {case 'female'}{$person} added you to her circle.
  //   {default}{$person} added you to his circle.
  // {/select}
  //
  // The corresponding ICU syntax string is:
  // {gender,select,
  //   female{{$person} added you to her circle.}
  //   other{{$person} added you to his circle.}
  // }
  //
  // (The variable names "gender" and "person" may be different depending on what purpose the
  // string is generated.)

  /**
   * Regex pattern for ICU syntax chars needing escaping. Reference:
   * http://userguide.icu-project.org/formatparse/messages
   *
   * <p>Syntax chars are single quote, braces, and hash. Single quotes not followed by another
   * syntax char do not need escaping. We match for: (a) a single quote that precedes another syntax
   * char, (b) a single quote at the end of the raw text part (presumably the raw text is followed
   * by some ICU syntax, such as a placeholder or the end of a plural/select case), or (c) any brace
   * char but not the hash char (see important note below).
   *
   * <p>Important: In case (c), we do not match for the hash char '#' because we specifically turn
   * off ICU special handling of '#' in both (1) generating JS code for goog.getMsg
   * (GenJsCodeVisitorAssistantForMsgs.genI18nMessageFormatExprHelper) and (2) reading translated
   * msgs files (XtbIcuMsgParser.processIcuMessage),
   */
  // Note: Need to escape hash char in regex due to Pattern.COMMENTS.
  private static final Pattern ICU_SYNTAX_CHAR_NEEDING_ESCAPE_PATTERN =
      Pattern.compile(" ' (?= ['{}\\#] ) | ' $ | [{}] ", Pattern.COMMENTS);

  /** Map from ICU syntax char to its escape sequence. */
  private static final ImmutableMap<String, String> ICU_SYNTAX_CHAR_ESCAPE_MAP =
      ImmutableMap.of("'", "''", "{", "'{'", "}", "'}'");

  /**
   * Escapes ICU syntax characters in raw text.
   *
   * @param rawText The raw text to escaped.
   * @return The escaped raw text. If the given raw text doesn't need escaping, then the same string
   *     object is returned.
   */
  @VisibleForTesting
  static String icuEscape(String rawText) {

    Matcher matcher = ICU_SYNTAX_CHAR_NEEDING_ESCAPE_PATTERN.matcher(rawText);
    if (!matcher.find()) {
      return rawText;
    }

    StringBuffer escapedTextSb = new StringBuffer();
    do {
      String repl = ICU_SYNTAX_CHAR_ESCAPE_MAP.get(matcher.group());
      matcher.appendReplacement(escapedTextSb, repl);
    } while (matcher.find());
    matcher.appendTail(escapedTextSb);
    return escapedTextSb.toString();
  }


  // ------ Plural related strings. ------

  /**
   * Gets the opening (left) string for a plural statement.
   *
   * @param varName The plural var name.
   * @param offset The offset.
   * @return the ICU syntax string for the plural opening string.
   */
  private static String getPluralOpenString(String varName, int offset) {
    StringBuilder openingPartSb = new StringBuilder();
    openingPartSb.append('{').append(varName).append(",plural,");
    if (offset != 0) {
      openingPartSb.append("offset:").append(offset).append(' ');
    }
    return openingPartSb.toString();
  }

  /**
   * Gets the closing (right) string for a plural statement.
   *
   * @return the ICU syntax string for the plural closing string.
   */
  private static String getPluralCloseString() {
    return "}";
  }

  /**
   * Gets the opening (left) string for a plural case statement.
   *
   * @param pluralCaseSpec The plural case spec object.
   * @return the ICU syntax string for the plural case opening string.
   */
  private static String getPluralCaseOpenString(SoyMsgPluralCaseSpec pluralCaseSpec) {
    String icuCaseName =
        (pluralCaseSpec.getType() == SoyMsgPluralCaseSpec.Type.EXPLICIT)
            ? "=" + pluralCaseSpec.getExplicitValue()
            : pluralCaseSpec.getType().name().toLowerCase();
    return icuCaseName + "{";
  }

  /**
   * Gets the closing (right) string for a plural case statement.
   *
   * @return the ICU syntax string for the plural case closing string.
   */
  private static String getPluralCaseCloseString() {
    return "}";
  }

  /**
   * Gets the closing string for a plural remainder statement.
   *
   * @return the ICU syntax string for the plural remainder string.
   */
  private static String getPluralRemainderString() {
    return "#";
  }

  // ------ Select related strings. ------

  /**
   * Gets the opening (left) string for a select statement.
   *
   * @param varName The select var name.
   * @return the ICU syntax string for the select opening string.
   */
  private static String getSelectOpenString(String varName) {
    return "{" + varName + ",select,";
  }

  /**
   * Gets the closing (right) string for a select statement.
   *
   * @return the ICU syntax string for the select closing string.
   */
  private static String getSelectCloseString() {
    return "}";
  }

  /**
   * Gets the opening (left) string for a select case statement.
   *
   * @param caseValue The case value, or {@code null} is it is the default statement.
   * @return the ICU syntax string for the select case opening string.
   */
  private static String getSelectCaseOpenString(String caseValue) {
    return ((caseValue != null) ? caseValue : "other") + "{";
  }

  /**
   * Gets the closing string for a plural remainder statement.
   *
   * @return the ICU syntax string for the plural remainder string.
   */
  private static String getSelectCaseCloseString() {
    return "}";
  }
}
