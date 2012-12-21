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
import com.ibm.icu.lang.UCharacter;

import java.util.regex.Pattern;

/**
 * Utility functions for performing common Bidi tests on strings.
 */
public class BidiUtils {

  /**
   * Not instantiable.
   */
  private BidiUtils() {
  }

  /**
   * Enum for directionality type.
   */
  public enum Dir {
    LTR     (1),
    UNKNOWN (0),
    RTL     (-1);

    public final int ord;

    Dir(int ord) {this.ord = ord; }

    /**
     * Interprets numeric representation of directionality: positive values are
     * interpreted as RTL, negative values as LTR, and zero as UNKNOWN.
     * These specific numeric values are standard for directionality
     * representation in Soy
     * {@link com.google.template.soy.jssrc.SoyJsSrcOptions#setBidiGlobalDir}.
     */
    public static Dir valueOf(int dir) {
      return dir > 0 ? LTR : dir < 0 ? RTL : UNKNOWN;
    }

    /**
     * Interprets boolean representation of directionality: false is interpreted
     * as LTR and true as RTL.
     */
    public static Dir valueOf(boolean dir) {
      return dir ? RTL : LTR;
    }

    /**
     * Returns whether this directionality is opposite to the given
     * directionality.
     */
    public boolean isOppositeTo(Dir dir) {
      return this.ord * dir.ord < 0;
    }
  }

  /**
   * A container class for Unicode formatting characters and for directionality
   * string constants.
   */
  public static final class Format {
    private Format () {}  // Not instantiable.
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

  /**
   * Returns the directionality of the input language / locale.
   * See {@link #isRtlLanguage} for more info.
   */
  public static Dir languageDir(String languageString) {
    return isRtlLanguage(languageString) ? Dir.RTL : Dir.LTR;
  }

  /**
   * A regular expression for matching right-to-left language codes.
   * See {@link #isRtlLanguage} for the design.
   */
  private static final Pattern RtlLocalesRe = Pattern.compile(
      "^(ar|dv|he|iw|fa|nqo|ps|sd|ug|ur|yi|.*[-_](Arab|Hebr|Thaa|Nkoo|Tfng))" +
      "(?!.*[-_](Latn|Cyrl)($|-|_))($|-|_)", Pattern.CASE_INSENSITIVE);

  /**
   * Check if a BCP 47 / III language code indicates an RTL language, i.e. either:
   * - a language code explicitly specifying one of the right-to-left scripts,
   *   e.g. "az-Arab", or<p>
   * - a language code specifying one of the languages normally written in a
   *   right-to-left script, e.g. "fa" (Farsi), except ones explicitly specifying
   *   Latin or Cyrillic script (which are the usual LTR alternatives).<p>
   * The list of right-to-left scripts appears in the 100-199 range in
   * http://www.unicode.org/iso15924/iso15924-num.html, of which Arabic and
   * Hebrew are by far the most widely used. We also recognize Thaana, N'Ko, and
   * Tifinagh, which also have significant modern usage. The rest (Syriac,
   * Samaritan, Mandaic, etc.) seem to have extremely limited or no modern usage
   * and are not recognized.
   * The languages usually written in a right-to-left script are taken as those
   * with Suppress-Script: Hebr|Arab|Thaa|Nkoo|Tfng  in
   * http://www.iana.org/assignments/language-subtag-registry,
   * as well as Sindhi (sd) and Uyghur (ug).
   * The presence of other subtags of the language code, e.g. regions like EG
   * (Egypt), is ignored.
   */
  public static boolean isRtlLanguage(String languageString) {
    return languageString != null &&
        RtlLocalesRe.matcher(languageString).find();
  }

  /**
   * "right" string constant.
   */
  public static final String RIGHT = "right";

  /**
   * "left" string constant.
   */
  public static final String LEFT = "left";

  /**
   * An object that estimates the directionality of a given string by various methods.
   */
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

    /**
     * The bidi character class cache.
     */
    private static final byte DIR_TYPE_CACHE[];

    static {
      DIR_TYPE_CACHE = new byte[DIR_TYPE_CACHE_SIZE];
      for (int i = 0; i < DIR_TYPE_CACHE_SIZE; i++) {
        DIR_TYPE_CACHE[i] = UCharacter.getDirectionality(i);
      }
    }

    /**
     * Word types, for the word count direction estimation algorithm. As we continue in a single
     * word, its type may rise from NEUTRAL to NUMERIC to STRONG, but will never descend. There are
     * also special word types for urls and the text between matching pairs of Unicode bidi
     * formatting chjaracters.
     */
    private static class WordType {
      /**
       * Word so far - if any - has consisted of neutral chars only.
       */
      public static final int NEUTRAL = 0;

      /**
       * Word so far had numerals, but no LTR or RTL; weakLtrWordCount has been incremented.
       */
      public static final int NUMERIC = 1;

      /**
       * Word had an LTR or RTL codepoint; ltrWordCount or rtlWordCount has been incremented.
       */
      public static final int STRONG = 2;

      /**
       * Word started with a URL prefix (http://); weakLtrWordCount has been incremented.
       */
      public static final int URL = 3;

      /**
       * A "word" between LRE/LRO/RLE/RLO and matching PDF.
       */
      public static final int EMBEDDED = 4;
    }

    /**
     * If at least RTL_THRESHOLD of the words containing strong LTR or RTL in the string start with
     * RTL, the word count direction estimation algorithm judges the string as a whole to be RTL.
     */
    private static final double RTL_THRESHOLD = 0.4;


    // Internal instance variables.

    /**
     * The text to be scanned.
     */
    private final String text;

    /**
     * Whether the text to be scanned is to be treated as HTML, i.e. skipping over tags and
     * entities when looking for the next / preceding dir type.
     */
    private final boolean isHtml;

    /**
     * The length of the text in chars.
     */
    private final int length;

    /**
     * The current position in the text.
     */
    private int charIndex;

    /**
     * The char encountered by the last dirTypeForward or dirTypeBackward call. If it encountered a
     * supplementary codepoint, this contains a char that is not a valid codepoint. This is ok,
     * because this member is only used to detect some well-known ASCII syntax, e.g. "http://" and
     * the beginning of an HTML tag or entity.
     */
    private char lastChar;

    /**
     * Number of LTR words found so far by the word count direction estimation algorithm.
     */
    private int ltrWordCount;

    /**
     * Number of RTL words found so far by the word count direction estimation algorithm.
     */
    private int rtlWordCount;

    /**
     * Number of "weak" LTR words (numbers and URLs) found so far by the word count direction
     * estimation algorithm.
     */
    private int weakLtrWordCount;

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
     * Checks if the (whole) string has any LTR characters in it.
     *
     * @param countEmbedding Whether LRE/RLE/LRO/RLO/PDF characters should be taken into account.
     * @return Whether any LTR characters were encountered.
     */
    boolean hasAnyLtr(boolean countEmbedding) {
      charIndex = 0;
      int embeddingLevel = 0;
      while (charIndex < length) {
        switch (dirTypeForward()) {
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT:
            if (embeddingLevel == 0) {
              return true;
            }
            break;
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            if (countEmbedding && embeddingLevel++ == 0) {
              return true;
            }
            break;
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            if (countEmbedding) {
              ++embeddingLevel;
            }
            break;
          case UCharacter.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
            if (countEmbedding) {
              --embeddingLevel;
            }
            break;
        }
      }
      return false;
    }

    /**
     * Checks if the (whole) string has any RTL characters in it.
     *
     * @param countEmbedding Whether LRE/RLE/LRO/RLO/PDF characters should be taken into account.
     * @return Whether any RTL characters were encountered.
     */
    boolean hasAnyRtl(boolean countEmbedding) {
      charIndex = 0;
      int embeddingLevel = 0;
      while (charIndex < length) {
        switch (dirTypeForward()) {
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT:
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            if (embeddingLevel == 0) {
              return true;
            }
            break;
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            if (countEmbedding && embeddingLevel++ == 0) {
              return true;
            }
            break;
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            if (countEmbedding) {
              ++embeddingLevel;
            }
            break;
          case UCharacter.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
            if (countEmbedding) {
              --embeddingLevel;
            }
            break;
        }
      }
      return false;
    }

    /**
     * Returns the directionality of the first character with strong directionality (going forward
     * from the start of the string), or Dir.UNKNOWN if none was encountered. Ignores
     * LRE/RLE/LRO/RLO/PDF characters.
     */
    Dir getUnicodeDir() {
      charIndex = 0;
      while (charIndex < length) {
        switch (dirTypeForward()) {
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT:
            return Dir.LTR;
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT:
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            return Dir.RTL;
        }
      }
      return Dir.UNKNOWN;
    }

    /**
     * Returns the directionality of the last character with strong directionality in the string, or
     * Dir.UNKNOWN if none was encountered. For efficiency, actually scans backwards from the end of
     * the string. Treats a (non-BN) character between an LRE/RLE/LRO/RLO and its matching PDF as a
     * strong character, LTR after LRE/LRO, and RTL after RLE/RLO. The results are undefined for a
     * string containing unbalanced LRE/RLE/LRO/RLO/PDF characters.
     */
    Dir getExitDir() {
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
      return Dir.UNKNOWN;
    }

    /**
     * Estimates the directionality of the (whole) string based on relative word counts.
     * <p>
     * If the number of RTL words is above a certain percentage of the total number of strongly
     * directional words, returns RTL.
     * <p>
     * Otherwise, if any words are strongly or weakly LTR, returns LTR.
     * <p>
     * Otherwise, returns UNKNOWN, which is used to mean "neutral".
     * <p>
     * Numbers and URLs are counted as weakly LTR.
     *
     * @return the string's directionality
     */
    @SuppressWarnings("fallthrough")
    Dir estimateDirectionByWordCount() {
      charIndex = 0;
      ltrWordCount = 0;
      rtlWordCount = 0;
      weakLtrWordCount = 0;
      int embedLevel = 0;
      wordType = WordType.NEUTRAL;
      while (charIndex < length) {
        switch (dirTypeForward()) {
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT:
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            // Strong RTL codepoint.
            // Convert WordType.NEUTRAL or WordType.NUMERIC word to WordType.STRONG.
            strongWord(true /* isRightToLeft */);
            break;
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT:
            // Strong LTR codepoint.
            // If it is the beginning of a URL, convert WordType.NEUTRAL to WordType.URL.
            // Otherwise, convert WordType.NEUTRAL or WordType.NUMERIC word to WordType.STRONG.
            if (wordType == WordType.NEUTRAL && lastChar == 'h' &&
                (matchForward("ttp://", true) || matchForward("ttps://", true))) {
              wordType = WordType.URL;
              ++weakLtrWordCount;
            } else {
              strongWord(false /* isRightToLeft */);
            }
            break;
          case UCharacter.DIRECTIONALITY_EUROPEAN_NUMBER:
          case UCharacter.DIRECTIONALITY_ARABIC_NUMBER:
            // Convert WordType.NEUTRAL word to WordType.NUMERIC.
            if (wordType < WordType.NUMERIC) {
              ++weakLtrWordCount;
              wordType = WordType.NUMERIC;
            }
            break;
          case UCharacter.DIRECTIONALITY_WHITESPACE:
          case UCharacter.DIRECTIONALITY_SEGMENT_SEPARATOR:
            // End of word, unless embedded.
            if (wordType < WordType.EMBEDDED) {
              wordType = WordType.NEUTRAL;
            }
            break;
          case UCharacter.DIRECTIONALITY_PARAGRAPH_SEPARATOR:
            // End of word, and reset embedding level.
            embedLevel = 0;
            wordType = WordType.NEUTRAL;
            break;
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            // LRO overrides the directionality of the characters inside it, so treat them as
            // a strongly LTR word.
            strongWord(false /* isRightToLeft */);
            // Fall through to LRE processing.
          case UCharacter.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
            // Start embedded area.
            if (embedLevel++ == 0) {
              wordType = WordType.EMBEDDED;
            }
            break;
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            // RLO overrides the directionality of the characters inside it, so treat them as
            // a strongly RTL word.
            strongWord(true /* isRightToLeft */);
            // Fall through to RLE processing.
          case UCharacter.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
            // Start embedded area.
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
            // There are lots of dir types that don't need any special processing.
            // Just go on to the next codepoint.
            break;
        }
      }

      // Make the final decision depending on the calculated word counts.
      if (rtlWordCount > (ltrWordCount + rtlWordCount) * RTL_THRESHOLD) {
        return Dir.RTL;
      }
      if (ltrWordCount + weakLtrWordCount > 0) {
        return Dir.LTR;
      }
      return Dir.UNKNOWN;
    }


    // Internal methods

    /**
     * Upgrades WordType.NEUTRAL or WordType.NUMERIC word to WordType.STRONG and adjusts the word
     * counts appropriately for the given direction.
     *
     * @param isRightToLeft Whether the strong word we are starting is RTL.
     */
    private void strongWord(boolean isRightToLeft) {
      if (wordType < WordType.STRONG) {
        if (isRightToLeft) {
          ++rtlWordCount;
        } else {
          ++ltrWordCount;
        }
        if (wordType == WordType.NUMERIC) {
          // weakLtrWordCount has already been incremented, so fix it.
          --weakLtrWordCount;
        }
        wordType = WordType.STRONG;
      }
    }

    /**
     * Returns whether the text at charIndex going forward is equal to a given string.
     * Does NOT skip over HTML mark-up.
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
     * If isHtml, and the codepoint is '<' or '&', advances through the tag/entity, and returns
     * UCharacter.DIRECTIONALITY_WHITESPACE. For an entity, it would be best to figure out the
     * actual character, and return its dirtype, but treating it as whitespace is good enough for
     * our purposes.
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
     * entity, advances over the whole tag/entity and returns UCharacter.DIRECTIONALITY_WHITESPACE.
     * For an entity, it would be best to figure out the actual character, and return its dirtype,
     * but treating it as whitespace is good enough for our purposes.
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
     * and returns UCharacter.DIRECTIONALITY_WHITESPACE. If there is no matching &gt;, does not
     * change charIndex and returns UCharacter.DIRECTIONALITY_OTHER_NEUTRALS (for the &lt; that
     * hadn't been part of a tag after all).
     */
    private byte skipTagForward() {
      int initialCharIndex = charIndex;
      while (charIndex < length) {
        lastChar = text.charAt(charIndex++);
        if (lastChar == '>') {
          // The end of the tag.
          return UCharacter.DIRECTIONALITY_WHITESPACE;
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
     * read) and returns UCharacter.DIRECTIONALITY_WHITESPACE. If there is no matching &lt;, does
     * not change charIndex and returns UCharacter.DIRECTIONALITY_OTHER_NEUTRALS (for the &gt; that
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
          // The start of the tag.
          return UCharacter.DIRECTIONALITY_WHITESPACE;
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
     * Advances charIndex forward through an HTML character entity tag (after the opening
     * &amp; has already been read) and returns UCharacter.DIRECTIONALITY_WHITESPACE. It would be
     * best to figure out the actual character and return its dirtype, but this is good enough.
     */
    private byte skipEntityForward() {
      while (charIndex < length && (lastChar = text.charAt(charIndex++)) != ';') {}
      return UCharacter.DIRECTIONALITY_WHITESPACE;
    }

    /**
     * Advances charIndex backward through an HTML character entity tag (after the closing ;
     * has already been read) and returns UCharacter.DIRECTIONALITY_WHITESPACE. It would be best to
     * figure out the actual character and return its dirtype, but this is good enough. If there
     * is no matching &amp;, does not change charIndex and returns
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
   * Checks if the given string has any LTR characters in it. Note that LRE/RLE/LRO/RLO/PDF
   * characters are ignored.
   *
   * @param str the string to be tested
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether the string contains any LTR characters
   */
  public static boolean hasAnyLtr(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).hasAnyLtr(false /* countEmbedding */);
  }

  /**
   * Like {@link #hasAnyLtr(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   *
   * @param str the string to be tested
   * @return whether the string contains any LTR characters
   */
  public static boolean hasAnyLtr(String str) {
    return hasAnyLtr(str, false /* isHtml */);
  }

  /**
   * Checks if the given string has any RTL characters in it. Note that LRE/RLE/LRO/RLO/PDF
   * characters are ignored.
   *
   * @param str the string to be tested
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether the string contains any RTL characters
   */
  public static boolean hasAnyRtl(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).hasAnyRtl(false /* countEmbedding */);
  }

  /**
   * Like {@link #hasAnyRtl(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   *
   * @param str the string to be tested
   * @return whether the string contains any RTL characters
   */
  public static boolean hasAnyRtl(String str) {
    return hasAnyRtl(str, false /* isHtml */);
  }

  /**
   * Returns the directionality of a string as defined by the UBA's rules P2 and P3, i.e. the
   * directionality of its first strong (L, R, or AL) character (with LRE/RLE/LRO/RLO/PDF having no
   * effect). However returns Dir.UNKNOWN if no strong characters were encountered (which P3 says
   * should be treated as LTR).
   *
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   */
  public static Dir getUnicodeDir(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).getUnicodeDir();
  }

  /**
   * Like {@link #getUnicodeDir(String, boolean)}, but assumes {@code str} is not HTML or
   * HTML-escaped.
   */
  public static Dir getUnicodeDir(String str) {
    return getUnicodeDir(str, false /* isHtml */);
  }

  /**
   * Returns the directionality of the last character with strong directionality in the string, or
   * Dir.UNKNOWN if none was encountered. For efficiency, actually scans backwards from the end of
   * the string. Treats a (non-BN) character between an LRE/RLE/LRO/RLO and its matching PDF as a
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
   * Like {@link #getExitDir(String, boolean)}, but assumes {@code str} is not HTML or
   * HTML-escaped.
   */
  public static Dir getExitDir(String str) {
    return getExitDir(str, false /* isHtml */);
  }

  /**
   * Estimates the directionality of a string based on relative word counts.
   * If the number of RTL words is above a certain percentage of the total number of strongly
   * directional words, returns RTL.
   * Otherwise, if any words are strongly or weakly LTR, returns LTR.
   * Otherwise, returns UNKNOWN, which is used to mean "neutral".
   * Numbers are counted as weakly LTR.
   *
   * @param str the string to check
   * @return the string's directionality
   */
  public static Dir estimateDirection(String str) {
    return estimateDirection(str, false /* isHtml */);
  }

  /**
   * Like {@link #estimateDirection(String)}, but can treat {@code str} as HTML,
   * ignoring HTML tags and escapes that would otherwise be mistaken for LTR text.
   *
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   */
  public static Dir estimateDirection(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).estimateDirectionByWordCount();
  }
}
