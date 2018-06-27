/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.internal.i18n;

import com.google.common.annotations.VisibleForTesting;
import com.google.template.soy.data.Dir;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.util.ULocale;

public class BidiUtils {

  /** Not instantiable. */
  private BidiUtils() {}

  /**
   * A container class for Unicode formatting characters and for directionality string constants.
   */
  static final class Format {
    private Format() {} // Not instantiable.
    /** Unicode "Left-To-Right Embedding" (LRE) character. */
    public static final char LRE = '\u202A';
    /** Unicode "Right-To-Left Embedding" (RLE) character. */
    public static final char RLE = '\u202B';
    /** Unicode "Pop Directional Formatting" (PDF) character. */
    public static final char PDF = '\u202C';
    /** Unicode "Left-To-Right Mark" (LRM) character. */
    public static final char LRM = '\u200E';
    /** Unicode "Right-To-Left Mark" (RLM) character. */
    public static final char RLM = '\u200F';

    // Holding also the String representation of LRM and RLM is useful for
    // several applications.
    public static final String LRM_STRING = Character.toString(LRM);
    public static final String RLM_STRING = Character.toString(RLM);
  }

  /** Returns whether a locale, given as a string in the ICU syntax, is RTL. */
  public static boolean isRtlLanguage(String locale) {
    try {
      return UScript.isRightToLeft(
          UCharacter.getPropertyValueEnum(
              UProperty.SCRIPT, ULocale.addLikelySubtags(new ULocale(locale)).getScript()));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** An object that estimates the directionality of a given string by various methods. */
  @VisibleForTesting
  static class DirectionalityEstimator {

    // Internal static variables and constants.

    /**
     * The size of the bidi character class cache. The results of the UCharacter.getDirectionality()
     * calls on the lowest DIR_TYPE_CACHE_SIZE codepoints are kept in an array for speed. The 0x700
     * value is designed to leave all the European and Near Eastern languages in the cache. It can
     * be reduced to 0x180, restricting the cache to the Western European languages.
     */
    private static final int DIR_TYPE_CACHE_SIZE = 0x700;

    /** The bidi character class cache. */
    private static final byte[] DIR_TYPE_CACHE;

    static {
      DIR_TYPE_CACHE = new byte[DIR_TYPE_CACHE_SIZE];
      for (int i = 0; i < DIR_TYPE_CACHE_SIZE; i++) {
        DIR_TYPE_CACHE[i] = UCharacter.getDirectionality(i);
      }
    }

    /**
     * The current classification of a word, for the word count direction estimation algorithm. As
     * we progress our examination through a word, the type may increase in value e.g.: NEUTRAL ->
     * EN | AN -> STRONG or NEUTRAL -> PLUS -> SIGNED_EN | PLUS_AN -> STRONG. It will only decrease
     * when going back down to NEUTRAL at a word break, and when a neutral character (other than a
     * plus or minus sign) appears after a plus or minus sign. Please note that STRONG, URL, and
     * EMBEDDED are terminal, i.e. do not change into another word type until the end of the word is
     * reached.
     */
    private static class WordType {
      /** Word so far - if any - contains no LTR, RTL, or numeric characters. */
      public static final int NEUTRAL = 0;

      /** Word so far is a plus sign. */
      public static final int PLUS = 1;

      /** Word so far is a minus sign. */
      public static final int MINUS = 2;

      /**
       * Word so far started with a European numeral, and had no LTR or RTL or plus/minus before the
       * number; enWordCount has been incremented.
       */
      public static final int EN = 3;

      /**
       * Word so far started with an Arabic numeral, and had no LTR or RTL or plus/minus before the
       * number.
       */
      public static final int AN = 4;

      /**
       * Word so far has been a signed European number, which has to be displayed in LTR;
       * signedEnWordCount has been incremented.
       */
      public static final int SIGNED_EN = 5;

      /**
       * Word so far has been an Arabic number with a leading plus sign, which we may choose to
       * interpret as an international phone number, which has to be displayed in LTR;
       * plusAnWordCount has been incremented.
       */
      public static final int PLUS_AN = 6;

      /**
       * Word so far has been a negative Arabic number, which has to be displayed in RTL;
       * minusAnWordCount has been incremented.
       */
      public static final int MINUS_AN = 7;

      /** Word had an LTR or RTL character; ltrWordCount or rtlWordCount has been incremented. */
      public static final int STRONG = 8;

      /**
       * Word started with a URL prefix (http:// or https://); urlWordCount has been incremented.
       */
      public static final int URL = 9;

      /** A "word" between LRE/LRO/RLE/RLO and matching PDF. */
      public static final int EMBEDDED = 10;
    }

    /**
     * If at least RTL_THRESHOLD of the words containing strong LTR or RTL in the string start with
     * RTL, the word count direction estimation algorithm judges the string as a whole to be RTL.
     */
    private static final double RTL_THRESHOLD = 0.4;

    // Internal instance variables.

    /** The text to be scanned. */
    private final String text;

    /**
     * Whether the text to be scanned is to be treated as HTML, i.e. skipping over tags and entities
     * when looking for the next / preceding dir type.
     */
    private final boolean isHtml;

    /** The length of the text in chars. */
    private final int length;

    /** The current position in the text. */
    private int charIndex;

    /**
     * The char encountered by the last dirTypeForward or dirTypeBackward call. If it encountered a
     * supplementary codepoint, this contains a char that is not a valid codepoint. This is ok,
     * because this member is only used to detect some well-known ASCII syntax, e.g. "http://" and
     * the beginning of an HTML tag or entity.
     */
    private char lastChar;

    /** Number of LTR words found so far by the word count direction estimation algorithm. */
    private int ltrWordCount;

    /** Number of RTL words found so far by the word count direction estimation algorithm. */
    private int rtlWordCount;

    /** Number of URLs found so far by the word count direction estimation algorithm. */
    private int urlWordCount;

    /**
     * Number of unsigned EN numbers found so far by the word count direction estimation algorithm.
     */
    private int enWordCount;

    /**
     * Number of signed EN numbers found so far by the word count direction estimation algorithm.
     */
    private int signedEnWordCount;

    /**
     * Number of plus-signed AN numbers found so far by the word count direction estimation
     * algorithm.
     */
    private int plusAnWordCount;

    /**
     * Number of minus-signed AN numbers found so far by the word count direction estimation
     * algorithm.
     */
    private int minusAnWordCount;

    /**
     * Type (so far) of the word continuing at charIndex in the string, for the word count direction
     * estimation algorithm.
     */
    private int wordType;

    // Methods intended for use by BidiUtils.

    /**
     * Constructor.
     *
     * @param text The string to scan.
     * @param isHtml Whether the text to be scanned is to be treated as HTML, i.e. skipping over
     *     tags and entities.
     */
    DirectionalityEstimator(String text, boolean isHtml) {
      this.text = text;
      this.isHtml = isHtml;
      length = text.length();
    }

    /**
     * Returns the directionality of the last character with strong directionality in the string, or
     * Dir.NEUTRAL if none was encountered. For efficiency, actually scans backwards from the end of
     * the string. Treats a non-BN character between an LRE/RLE/LRO/RLO and its matching PDF as a
     * strong character, LTR after LRE/LRO, and RTL after RLE/RLO. The results are undefined for a
     * string containing unbalanced LRE/RLE/LRO/RLO/PDF characters.
     */
    Dir getExitDir() {
      // The reason for this method name, as opposed to getLastStrongDir(), is that "last strong"
      // sounds like the exact opposite of "first strong", which is a commonly used description of
      // Unicode's estimation algorithm (getUnicodeDir() above), but the two must treat formatting
      // characters quite differently. Thus, we are staying away from both "first" and "last" in
      // these method names to avoid confusion.
      charIndex = length;
      int embeddingLevel = 0;
      int lastNonEmptyEmbeddingLevel = 0;
      while (charIndex > 0) {
        switch (dirTypeBackward()) {
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT:
            if (embeddingLevel == 0) {
              return Dir.LTR;
            }
            if (lastNonEmptyEmbeddingLevel == 0) {
              lastNonEmptyEmbeddingLevel = embeddingLevel;
            }
            break;
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            if (lastNonEmptyEmbeddingLevel == embeddingLevel) {
              return Dir.LTR;
            }
            --embeddingLevel;
            break;
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT:
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            if (embeddingLevel == 0) {
              return Dir.RTL;
            }
            if (lastNonEmptyEmbeddingLevel == 0) {
              lastNonEmptyEmbeddingLevel = embeddingLevel;
            }
            break;
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            if (lastNonEmptyEmbeddingLevel == embeddingLevel) {
              return Dir.RTL;
            }
            --embeddingLevel;
            break;
          case UCharacter.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
            ++embeddingLevel;
            break;
          case UCharacter.BOUNDARY_NEUTRAL:
            break;
          default:
            if (lastNonEmptyEmbeddingLevel == 0) {
              lastNonEmptyEmbeddingLevel = embeddingLevel;
            }
            break;
        }
      }
      return Dir.NEUTRAL;
    }

    /**
     * Estimates the directionality of the (whole) string based on relative word counts. See {@link
     * #estimateDirection(String, boolean)} for full description.
     *
     * @return the string's directionality
     */
    @SuppressWarnings("fallthrough")
    Dir estimateDirectionByWordCount() {
      charIndex = 0;
      ltrWordCount = 0;
      rtlWordCount = 0;
      urlWordCount = 0;
      enWordCount = 0;
      signedEnWordCount = 0;
      plusAnWordCount = 0;
      minusAnWordCount = 0;
      int embedLevel = 0;
      wordType = WordType.NEUTRAL;
      while (charIndex < length) {
        byte dirType = dirTypeForward();
        // The DIRECTIONALITY_LEFT_TO_RIGHT case is taken out of the switch statement below to
        // improve the performance for LTR text (i.e. the vast majority of the content encountered
        // on the web).
        if (dirType == UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT) {
          // Strongly LTR. Convert numeric word to LTR, and a neutral word either to LTR or, if
          // the character just scanned and the characters following it are a URL, to a URL.
          processStrong(/* isRtl= */ false);
        } else {
          switch (dirType) {
            case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT:
            case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
              // Strongly RTL. Convert neutral or numeric word to RTL.
              processStrong(/* isRtl= */ true);
              break;

            case UCharacter.DIRECTIONALITY_EUROPEAN_NUMBER:
              // A European digit. Convert NEUTRAL to EN, and PLUS and MINUS to SIGNED_EN.
              processEuropeanDigit();
              break;

            case UCharacter.DIRECTIONALITY_ARABIC_NUMBER:
              // An Arabic digit. Convert NEUTRAL to AN, PLUS to PLUS_AN, and MINUS to MINUS_AN.
              processArabicDigit();
              break;

            case UCharacter.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR:
              // Plus or minus sign. Treat as end of a numeric word, and convert NEUTRAL to PLUS or
              // MINUS.
              if (wordType < WordType.STRONG) {
                if (wordType <= WordType.MINUS) {
                  switch (lastChar) {
                    case 0x002B: // PLUS SIGN
                    case 0x207A: // SUPERSCRIPT PLUS SIGN
                    case 0x208A: // SUBSCRIPT PLUS SIGN
                    case 0xFB29: // HEBREW LETTER ALTERNATIVE PLUS SIGN
                    case 0xFE62: // SMALL PLUS SIGN
                    case 0xFF0B: // FULLWIDTH PLUS SIGN
                      wordType = WordType.PLUS;
                      break;
                    default:
                      wordType = WordType.MINUS;
                      break;
                  }
                } else {
                  wordType = WordType.NEUTRAL;
                }
              }
              break;

            case UCharacter.COMMON_NUMBER_SEPARATOR:
              // Neutral used to format numbers that (with the exception of a slash, due to a
              // Microsoft bug) can be relied upon to keep the digits around it displayed LTR. Reset
              // PLUS and MINUS back to NEUTRAL, and treat a slash as the end of a numeric word.
              if (wordType < WordType.STRONG && (wordType <= WordType.MINUS || lastChar == '/')) {
                wordType = WordType.NEUTRAL;
              }
              break;

            case UCharacter.OTHER_NEUTRAL:
            case UCharacter.EUROPEAN_NUMBER_TERMINATOR:
              // Neutrals not used for formatting inside numbers. Treat as end of a numeric word.
              if (wordType < WordType.STRONG) {
                wordType = WordType.NEUTRAL;
              }
              break;

            case UCharacter.DIRECTIONALITY_WHITESPACE:
            case UCharacter.DIRECTIONALITY_SEGMENT_SEPARATOR:
              // Whitespace. Treat as end of word, unless embedded.
              if (wordType < WordType.EMBEDDED) {
                wordType = WordType.NEUTRAL;
              }
              break;

            case UCharacter.DIRECTIONALITY_PARAGRAPH_SEPARATOR:
              // Paragraph break. Treat as end of word, and reset embedding level.
              embedLevel = 0;
              wordType = WordType.NEUTRAL;
              break;

            case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
              // LRO overrides the directionality of the characters inside it, so treat them as
              // strongly LTR.
              processStrong(/* isRtl= */ false);
              // Fall through to LRE processing.
            case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
              // Start LTR embedded area.
              if (embedLevel++ == 0) {
                wordType = WordType.EMBEDDED;
              }
              break;

            case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
              // RLO overrides the directionality of the characters inside it, so treat them as
              // a strongly RTL word.
              processStrong(/* isRtl= */ true);
              // Fall through to RLE processing.
            case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
              // Start RTL embedded area.
              if (embedLevel++ == 0) {
                wordType = WordType.EMBEDDED;
              }
              break;

            case UCharacter.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
              // End embedded area.
              if (--embedLevel == 0) {
                wordType = WordType.NEUTRAL;
              }
              break;

            default:
              // Ignore control characters (DIRECTIONALITY_BOUNDARY_NEUTRAL) and non-spacing marks
              // (DIRECTIONALITY_NON_SPACING_MARKS).
              break;
          }
        }
      }

      return compareCounts();
    }

    // Internal methods

    /*
     * Make the final choice of estimated direction depending on the calculated word counts.
     */
    Dir compareCounts() {
      if (rtlWordCount > (ltrWordCount + rtlWordCount) * RTL_THRESHOLD) {
        return Dir.RTL;
      }
      // If ltrWordCount is greater than zero, the string is LTR. Otherwise, rtlWordCount must also
      // be zero, and the result depends only on the "weak" words - URLs and numbers.
      if (ltrWordCount + urlWordCount + signedEnWordCount > 0 || enWordCount > 1) {
        return Dir.LTR;
      }
      if (minusAnWordCount > 0) {
        return Dir.RTL;
      }
      if (plusAnWordCount > 0) {
        return Dir.LTR;
      }
      return Dir.NEUTRAL;
    }

    /**
     * Converts a neutral or numeric word to STRONG, or, if the word had been neutral, and the
     * character just scanned and the characters following are a URL, to a URL, and adjusts the word
     * counts appropriately.
     */
    private void processStrong(boolean isRtl) {
      if (wordType >= WordType.STRONG) {
        // Current word's type is final.
        return;
      }
      switch (wordType) {
        case WordType.NEUTRAL:
          if (!isRtl
              && lastChar == 'h'
              && (matchForward("ttp://", true) || matchForward("ttps://", true))) {
            // This is the start of a URL.
            wordType = WordType.URL;
            ++urlWordCount;
            return;
          }
          break;
        case WordType.SIGNED_EN:
          // signedEnWordCount was incremented earlier; revert it.
          --signedEnWordCount;
          break;
        case WordType.PLUS_AN:
          // plusAnWordCount was incremented earlier; revert it.
          --plusAnWordCount;
          break;
        case WordType.MINUS_AN:
          // minusAnWordCount was incremented earlier; revert it.
          --minusAnWordCount;
          break;
        case WordType.EN:
          // enWordCount was incremented earlier; revert it.
          --enWordCount;
          break;
        default:
          // No word count was incremented earlier.
          break;
      }
      wordType = WordType.STRONG;
      if (isRtl) {
        ++rtlWordCount;
      } else {
        ++ltrWordCount;
      }
    }

    /**
     * Converts a NEUTRAL to EN, and PLUS and MINUS to SIGNED_EN, and adjusts the word counts
     * appropriately.
     */
    private void processEuropeanDigit() {
      switch (wordType) {
        case WordType.NEUTRAL:
          // Convert a neutral word to an unsigned "European" number.
          ++enWordCount;
          wordType = WordType.EN;
          break;

        case WordType.PLUS:
        case WordType.MINUS:
          // Convert a sign to a signed "European" number.
          ++signedEnWordCount;
          wordType = WordType.SIGNED_EN;
          break;

        default:
          break;
      }
    }

    /**
     * Converts a NEUTRAL to AN, PLUS to PLUS_AN, and MINUS to MINUS_AN, and adjusts the word counts
     * appropriately.
     */
    private void processArabicDigit() {
      switch (wordType) {
        case WordType.NEUTRAL:
          // Convert a neutral word to an unsigned "Arabic" number. Currently, unsigned "Arabic"
          // numbers do not play a part in deciding the overall directionality. Nevertheless, we
          // do identify them here so we can easily change the policy on them if necessary.
          wordType = WordType.AN;
          break;

        case WordType.PLUS:
          // Convert a plus sign to a plus-signed "Arabic" number.
          ++plusAnWordCount;
          wordType = WordType.PLUS_AN;
          break;

        case WordType.MINUS:
          // Convert a minus sign to a minus-signed "Arabic" number.
          ++minusAnWordCount;
          wordType = WordType.MINUS_AN;
          break;

        default:
          break;
      }
    }

    /**
     * Returns whether the text at charIndex going forward is equal to a given string. Does NOT skip
     * over HTML mark-up.
     *
     * @param match The string to match.
     * @param advance Whether to advance charIndex to the end of a successful match.
     * @return Whether the text at charIndex going forward is equal to the given string.
     */
    @VisibleForTesting
    boolean matchForward(String match, boolean advance) {
      int matchLength = match.length();
      if (matchLength > length - charIndex) {
        return false;
      }
      for (int checkIndex = 0; checkIndex < matchLength; checkIndex++) {
        if (text.charAt(charIndex + checkIndex) != match.charAt(checkIndex)) {
          return false;
        }
      }
      if (advance) {
        charIndex += matchLength;
      }
      return true;
    }

    /**
     * Gets the bidi character class, i.e. UCharacter.getDirectionality(), of a given char, using a
     * cache for speed. Not designed for supplementary codepoints, whose results we do not cache.
     */
    private static byte getCachedDirectionality(char c) {
      return c < DIR_TYPE_CACHE_SIZE ? DIR_TYPE_CACHE[c] : UCharacter.getDirectionality(c);
    }

    /**
     * Returns the UCharacter.DIRECTIONALITY_... value of the next codepoint and advances charIndex.
     * If isHtml, and the codepoint is '<' or '&', advances through the tag/entity, and returns an
     * appropriate dirtype.
     *
     * @throws java.lang.IndexOutOfBoundsException if called when charIndex >= length or < 0.
     */
    @VisibleForTesting
    byte dirTypeForward() {
      lastChar = text.charAt(charIndex);
      if (UCharacter.isHighSurrogate(lastChar)) {
        int codePoint = UCharacter.codePointAt(text, charIndex);
        charIndex += UCharacter.charCount(codePoint);
        return UCharacter.getDirectionality(codePoint);
      }
      charIndex++;
      byte dirType = getCachedDirectionality(lastChar);
      if (isHtml) {
        // Process tags and entities.
        if (lastChar == '<') {
          dirType = skipTagForward();
        } else if (lastChar == '&') {
          dirType = skipEntityForward();
        }
      }
      return dirType;
    }

    /**
     * Returns the UCharacter.DIRECTIONALITY_... value of the preceding codepoint and advances
     * charIndex backwards. If isHtml, and the codepoint is the end of a complete HTML tag or
     * entity, advances over the whole tag/entity and returns an appropriate dirtype.
     *
     * @throws java.lang.IndexOutOfBoundsException if called when charIndex > length or <= 0.
     */
    @VisibleForTesting
    byte dirTypeBackward() {
      lastChar = text.charAt(charIndex - 1);
      if (UCharacter.isLowSurrogate(lastChar)) {
        int codePoint = UCharacter.codePointBefore(text, charIndex);
        charIndex -= UCharacter.charCount(codePoint);
        return UCharacter.getDirectionality(codePoint);
      }
      charIndex--;
      byte dirType = getCachedDirectionality(lastChar);
      if (isHtml) {
        // Process tags and entities.
        if (lastChar == '>') {
          dirType = skipTagBackward();
        } else if (lastChar == ';') {
          dirType = skipEntityBackward();
        }
      }
      return dirType;
    }

    /**
     * Advances charIndex forward through an HTML tag (after the opening &lt; has already been read)
     * and returns an appropriate dirtype for the tag. If there is no matching &gt;, does not change
     * charIndex and returns UCharacter.DIRECTIONALITY_OTHER_NEUTRALS (for the &lt; that hadn't been
     * part of a tag after all).
     */
    private byte skipTagForward() {
      int initialCharIndex = charIndex;
      while (charIndex < length) {
        lastChar = text.charAt(charIndex++);
        if (lastChar == '>') {
          // The end of the tag.
          // We return BN because the tags we really expect to encounter - and know how to handle
          // best - are inline ones like <span>, <b>, <i>, <a>, etc. These do not connote a word
          // break (as would WS) or punctuation (as would ON), but really are most similar to
          // control codes. Ideally, we should check the actual tag and return B for <br> and the
          // block element tags, but perfecting handling of multi-paragraph input isn't very
          // important since estimating one directionality over several paragraphs is futile anyway:
          // each one should be allowed its own. More importantly, we should check for the dir
          // attribute and return an appropriate embedding, override, or isolate initiator bidi
          // class, and its closing dirtype for the closing tag, but finding the closing tag is
          // not so easy. A poor man's approach that should be good enough without needing a stack
          // could ignore the dir attribute on elements nested in an element with a dir attribute,
          // and find its closing tag by counting the nesting only of its type. Still, this wouldn't
          // work in skipTagBackward() - see note there.
          // TODO(user): Consider checking the tag and returning BN, B, or one of the explicit
          // directional formatting dirtypes, as appropriate.
          return UCharacter.DIRECTIONALITY_BOUNDARY_NEUTRAL;
        }
        if (lastChar == '"' || lastChar == '\'') {
          // Skip over a quoted attribute value inside the tag.
          char quote = lastChar;
          while (charIndex < length && (lastChar = text.charAt(charIndex++)) != quote) {}
        }
      }
      // The original '<' wasn't the start of a tag after all.
      charIndex = initialCharIndex;
      lastChar = '<';
      return UCharacter.DIRECTIONALITY_OTHER_NEUTRALS;
    }

    /**
     * Advances charIndex backward through an HTML tag (after the closing &gt; has already been
     * read) and returns an appropriate dirtype for the tag. If there is no matching &lt;, does not
     * change charIndex and returns UCharacter.DIRECTIONALITY_OTHER_NEUTRALS (for the &gt; that
     * hadn't been part of a tag after all). Nevertheless, the running time for calling
     * skipTagBackward() in a loop remains linear in the size of the text, even for a text like
     * "&gt;&gt;&gt;&gt;", because skipTagBackward() also stops looking for a matching &lt; when it
     * encounters another &gt;.
     */
    private byte skipTagBackward() {
      int initialCharIndex = charIndex;
      while (charIndex > 0) {
        lastChar = text.charAt(--charIndex);
        if (lastChar == '<') {
          // The start of the tag. See note in skipTagForward() regarding the dirtype we return.
          // Note, however, that the "poor man's approach" described there for handling the dir
          // attribute wouldn't work here, since here we see the closing tag first - and do not
          // have any indication if its matching opening tag carries the dir attribute.
          return UCharacter.DIRECTIONALITY_BOUNDARY_NEUTRAL;
        }
        if (lastChar == '>') {
          break;
        }
        if (lastChar == '"' || lastChar == '\'') {
          // Skip over a quoted attribute value inside the tag.
          char quote = lastChar;
          while (charIndex > 0 && (lastChar = text.charAt(--charIndex)) != quote) {}
        }
      }
      // The original '>' wasn't the end of a tag after all.
      charIndex = initialCharIndex;
      lastChar = '>';
      return UCharacter.DIRECTIONALITY_OTHER_NEUTRALS;
    }

    /**
     * Advances charIndex forward through an HTML character entity tag (after the opening &amp; has
     * already been read) and returns UCharacter.DIRECTIONALITY_WHITESPACE. It would be best to
     * figure out the actual character and return its dirtype, but this is good enough.
     */
    private byte skipEntityForward() {
      while (charIndex < length && (lastChar = text.charAt(charIndex++)) != ';') {}
      return UCharacter.DIRECTIONALITY_WHITESPACE;
    }

    /**
     * Advances charIndex backward through an HTML character entity tag (after the closing ; has
     * already been read) and returns UCharacter.DIRECTIONALITY_WHITESPACE. It would be best to
     * figure out the actual character and return its dirtype, but this is good enough. If there is
     * no matching &amp;, does not change charIndex and returns
     * UCharacter.DIRECTIONALITY_OTHER_NEUTRALS (for the ';' that did not start an entity after
     * all). Nevertheless, the running time for calling skipEntityBackward() in a loop remains
     * linear in the size of the text, even for a text like ";;;;;;;", because skipTagBackward()
     * also stops looking for a matching &amp; when it encounters another ;.
     */
    private byte skipEntityBackward() {
      int initialCharIndex = charIndex;
      while (charIndex > 0) {
        lastChar = text.charAt(--charIndex);
        if (lastChar == '&') {
          return UCharacter.DIRECTIONALITY_WHITESPACE;
        }
        if (lastChar == ';') {
          break;
        }
      }
      charIndex = initialCharIndex;
      lastChar = ';';
      return UCharacter.DIRECTIONALITY_OTHER_NEUTRALS;
    }
  }

  /**
   * Returns the directionality of the last character with strong directionality in the string, or
   * Dir.NEUTRAL if none was encountered. For efficiency, actually scans backwards from the end of
   * the string. Treats a non-BN character between an LRE/RLE/LRO/RLO and its matching PDF as a
   * strong character, LTR after LRE/LRO, and RTL after RLE/RLO. The results are undefined for a
   * string containing unbalanced LRE/RLE/LRO/RLO/PDF characters. The intended use is to check
   * whether a logically separate item that starts with a number or a character of the string's exit
   * directionality and follows this string inline (not counting any neutral characters in between)
   * would "stick" to it in an opposite-directionality context, thus being displayed in an incorrect
   * position. An LRM or RLM character (the one of the context's directionality) between the two
   * will prevent such sticking.
   *
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   */
  public static Dir getExitDir(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).getExitDir();
  }

  /**
   * Estimates the directionality of a string based on relative word counts, as detailed below.
   *
   * <p>The parts of the text embedded between LRE/RLE and the matching PDF are ignored, since the
   * directionality in which the string as a whole is displayed will not affect their display
   * anyway, and we want to base it on the remainder.
   *
   * <p>The parts of the text embedded between LRO/RLO and the matching PDF are considered LTR/RTL
   * "words". This is primarily in order to treat "fake bidi" pseudolocalized text as RTL.
   *
   * <p>The remaining parts of the text are divided into "words" on whitespace and, inside numbers,
   * on neutral characters that break the LTR flow around them when used inside a number in an RTL
   * context. (This is most of them, the primary exceptions being period, comma, NBSP and colon,
   * i.e. bidi class CS not including slash, which a long-standing Microsoft bug treats as ES)).
   *
   * <p>Each word is assigned a type - LTR, RTL, URL, signed "European" number, unsigned "European"
   * number, negative "Arabic" number, "Arabic" number with leading plus sign, and unsigned "Arabic"
   * number - as follows:
   *
   * <p>- Words that start with "http[s]://" (possibly preceded by some neutrals) are URLs.
   *
   * <p>- Of the remaining words, those that contain any strongly directional characters are
   * classified as LTR or RTL based on their first strongly directional character.
   *
   * <p>- Of the remaining words, those that contain any digits are classified as an "European" or
   * "Arabic" number based on the type of its first digit, and signed or unsigned depending on
   * whether the first digit was immediately preceded by a plus or minus sign (bidi class ES).
   *
   * <p>- The remaining words are classified as "neutral" and ignored.
   *
   * <p>Once the words of each type have been counted, the directionality is decided as follows:
   *
   * <p>If the number of RTL words exceeds 40% of the total of LTR and RTL words, return Dir.RTL.
   * The threshold favors RTL because LTR words and phrases are used in RTL sentences more commonly
   * than RTL in LTR.
   *
   * <p>Otherwise, if there are any LTR words, return Dir.LTR.
   *
   * <p>Otherwise (i.e. if there are no LTR or RTL words), if there are any URLs, or any signed
   * "European" numbers, or an "Arabic" number with a leading plus sign, or more than one unsigned
   * "European" number, return Dir.LTR. This ensures that the text is displayed LTR even in an RTL
   * context, where things like "http://www.google.com/", "-5", "+١٢٣٤٢٣٤٦٧٨٩" (assuming it is
   * intended as an international phone number, not an explicitly signed positive number, which is a
   * very rare use case), "3 - 2 = 1", "(03) 123 4567", and, when preceded by an Arabic letter, even
   * "123-4567" and "400×300" are displayed incorrectly. (Most neutrals, including those in the last
   * two examples, are treated as ending a number in order to treat such expressions as containing
   * more than one "European" number, and thus to force their display in LTR.) Considering a string
   * containing more than "European" number to be LTR also makes sense because math expressions in
   * "European" digits need to be displayed LTR even in RTL languages. However, that probably isn't
   * a very important consideration, since math expressions would usually also contain strongly LTR
   * or RTL variable names that should set the overall directionality. Ranges like "$1 - $5" *are*
   * an important consideration, but their preferred direction unfortunately varies among the RTL
   * languages. Since LTR is preferred for ranges in Persian and Urdu, and is the more widespread
   * usage in Hebrew, it seems like an OK choice. Please note that native Persian digits are
   * included in the "European" class because the unary minus is preferred on the left in Persian,
   * and Persian math is written LTR.
   *
   * <p>Otherwise, if there are any negative "Arabic" numbers, return Dir.RTL. This is because the
   * unary minus is supposed to be displayed to the right of a number written in "Arabic" digits.
   *
   * <p>Otherwise, return Dir.NEUTRAL. This includes the common case of a single unsigned number,
   * which will display correctly in either "European" or "Arabic" digits in either directionality,
   * so it is best not to force it to either. It also includes an otherwise neutral string
   * containing two or more "Arabic" numbers. We do *not* consider it to be RTL because it is
   * unclear that it is important to display "Arabic"-digit math and ranges in RTL even in an LTR
   * context, and because we have no idea how to handle phone numbers spelled (or, more likely,
   * misspelled) in "Arabic" digits with non-CS separators. But it is quite clear that we do not
   * want to force it to LTR.
   *
   * <p>If {@code isHtml} is true, treats {@code str} as HTML, ignoring HTML tags and escapes that
   * would otherwise be mistaken for LTR text.
   *
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return the string's directionality
   */
  public static Dir estimateDirection(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).estimateDirectionByWordCount();
  }
}
