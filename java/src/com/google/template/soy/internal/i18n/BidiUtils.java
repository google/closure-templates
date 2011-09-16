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
      "(?!.*[-_](Latn|Cyrl)($|-|_))($|-|_)");

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
   * An object for iterating through the bidi character classes in a given string, forwards or
   * backwards. Based on Character.getDirectionality(), but with a bidi character class cache for
   * speed, optimized support for iterating through a UTF-16 string, and a simplistic capability for
   * skipping over HTML mark-up when iterating through the bidi character classes.
   */
  static class DirTypeIterator {

    // Statics.

    /**
     * The size of the bidi character class cache. The results of the Character.getDirectionality()
     * calls on the lowest DIR_TYPE_CACHE_SIZE codepoints are kept in an array for speed. The 0x700
     * value is designed to leave all the European and Near Eastern languages in the cache. It can
     * be reduced to 0x180, restricting the cache to the Western European languages.
     */
    private static final int DIR_TYPE_CACHE_SIZE = 0x700;

    /**
     * The initial value for bidi character class cache values.
     */
    private static final byte UNKNOWN_DIR_TYPE = 0x7F;

    /**
     * The bidi character class cache. It is not thread-safe, but the worst that can happen is that
     * a particular value will be computed more than once.
     */
    private static byte dirTypeCache[];

    static {
      dirTypeCache = new byte[DIR_TYPE_CACHE_SIZE];
      for (int i = 0; i < DIR_TYPE_CACHE_SIZE; i++) {
        dirTypeCache[i] = UNKNOWN_DIR_TYPE;
      }
    }

    /**
     * Gets the bidi character class, i.e. Character.getDirectionality(), of a given char, using a
     * cache for speed. Not designed for supplementary codepoints, whose results we do not cache.
     */
    private static byte getCachedDirectionality(char c) {
      if (c >= DIR_TYPE_CACHE_SIZE) {
        return Character.getDirectionality(c);
      }
      byte dirType = dirTypeCache[c];
      if (dirType == UNKNOWN_DIR_TYPE) {
        dirTypeCache[c] = dirType = Character.getDirectionality(c);
      }
      return dirType;
    }


    // Private data members.

    /**
     * The text to be scanned.
     */
    private CharSequence text;

    /**
     * The length of the text in chars.
     */
    private int length;

    /**
     * Whether the text to be scanned is to be treated as HTML, i.e. skipping over tags and
     * entities when looking for the next / preceding dir type.
     */
    private boolean isHtml;

    /**
     * The current position in the text.
     */
    private int charIndex;

    /**
     * The char encountered by the last dirTypeForward or dirTypeBackward call. If it encountered a
     * supplementary codepoint, this contains a char that is not a valid codepoint.
     */
    private char lastChar;


    // Constructors

    /**
     * Creates a DirTypeIterator, given a string and whether it is to be treated as HTML, skipping
     * over mark-up. The initial position is at the start of the string.
     * @param text The string to scan.
     * @param isHtml Whether the text to be scanned is to be treated as HTML, i.e. skipping over
     * tags and entities.
     */
    public DirTypeIterator(CharSequence text, boolean isHtml) {
      this.text = text;
      this.isHtml = isHtml;
      length = text.length();
      rewind(false);
    }


    // Public methods

    /**
     * Returns whether the iteration has reached the end of the text.
     */
    public boolean atEnd() {
      return charIndex == length;
    }

    /**
     * Returns whether the iteration has reached the start of the text.
     */
    public boolean atStart() {
      return charIndex == 0;
    }

    /**
     * Returns the char encountered by the last operation to advance the iteration forward or
     * backward, e.g. {@code dirTypeForward()} or {@code dirTypeBackward()}. If it encountered a
     * supplementary codepoint, returns a character that is not a valid codepoint.
     */
    public char getLastChar() {
      return lastChar;
    }

    /**
     * Re-starts the iteration from the start (or the end) of the string.
     * @param toEnd Whether to start at the end of the string.
     */
    public void rewind(boolean toEnd) {
      charIndex = toEnd ? length : 0;
      lastChar = '\uD800';  // An invalid codepoint.
    }

    /**
     * Returns the next char and advances the iteration. If it encounters a supplementary codepoint,
     * returns a char that is not a valid codepoint, but advances through the whole codepoint. Meant
     * for parsing over a known syntax that does not use supplementary codepoints. Does NOT skip
     * over HTML mark-up. Will throw IndexOutOfBoundsException if called when atEnd() is true.
     * @throws java.lang.IndexOutOfBoundsException
     */
    public char charForward() {
      lastChar = text.charAt(charIndex);
      if (Character.isHighSurrogate(lastChar)) {
        charIndex += Character.charCount(Character.codePointAt(text, charIndex));
      } else {
        charIndex++;
      }
      return lastChar;
    }

    /**
     * Returns the preceding char and advances the iteration backwards. If it encounters a
     * supplementary codepoint, returns a char that is not a valid codepoint, but advances through
     * the whole codepoint. Meant for parsing over a known syntax that does not use supplementary
     * codepoints. Does NOT skip over HTML mark-up. Will throw IndexOutOfBoundsException if called
     * when atStart() is true.
     * @throws java.lang.IndexOutOfBoundsException
     */
    public char charBackward() {
      lastChar = text.charAt(charIndex - 1);
      if (Character.isLowSurrogate(lastChar)) {
        charIndex -= Character.charCount(Character.codePointBefore(text, charIndex));
      } else {
        charIndex--;
      }
      return lastChar;
    }

    /**
     * Returns whether the text at the current position going forward is equal to a given string.
     * Does NOT skip over HTML mark-up.
     * @param match The string to match.
     * @param advance Whether to advance the iteration to the end of a successful match.
     * @return Whether the text at the current position going forward is equal to the given string.
     */
    public boolean matchForward(CharSequence match, boolean advance) {
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
     * Returns the Character.DIRECTIONALITY_... value of the next codepoint and advances the
     * iteration. If isHtml, and the codepoint is '<' or '&', advances through the tag/entity, and
     * returns Character.DIRECTIONALITY_WHITESPACE. For an entity, it would be best to figure out
     * the actual character, and return its dirtype, but this is good enough for our purposes. Will
     * throw IndexOutOfBoundsException if called when atEnd() is true.
     * @throws java.lang.IndexOutOfBoundsException
     */
    public byte dirTypeForward() {
      lastChar = text.charAt(charIndex);
      if (Character.isHighSurrogate(lastChar)) {
        int codePoint = Character.codePointAt(text, charIndex);
        charIndex += Character.charCount(codePoint);
        return Character.getDirectionality(codePoint);
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
     * Returns the Character.DIRECTIONALITY_... value of the preceding codepoint and advances the
     * iteration backwards. If isHtml, and the codepoint is the end of a complete HTML tag or
     * entity, advances over the whole tag/entity and returns Character.DIRECTIONALITY_WHITESPACE.
     * For an entity, it would be best to figure out the actual character, and return its dirtype,
     * but this is good enough for our purposes. Will throw IndexOutOfBoundsException if called
     * when atStart() is true.
     * @throws java.lang.IndexOutOfBoundsException
     */
    public byte dirTypeBackward() {
      lastChar = text.charAt(charIndex - 1);
      if (Character.isLowSurrogate(lastChar)) {
        int codePoint = Character.codePointBefore(text, charIndex);
        charIndex -= Character.charCount(codePoint);
        return Character.getDirectionality(codePoint);
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


    // Private methods

    /**
     * Advances current position forward through an HTML tag and returns
     * Character.DIRECTIONALITY_WHITESPACE.
     */
    private byte skipTagForward() {
      while (!atEnd()) {
        char c = charForward();
        if (c == '>') {
          break;
        }
        if (c == '"' || c == '\'') {
          while (!atEnd()) {
            if (charForward() == c) {
              break;
            }
          }
        }
      }
      return Character.DIRECTIONALITY_WHITESPACE;
    }

    /**
     * Advances current position backward through an HTML tag and returns
     * Character.DIRECTIONALITY_WHITESPACE. If the tag is not closed, does not advance the position
     * and returns Character.DIRECTIONALITY_OTHER_NEUTRALS (for the '>' that did not start a tag
     * after all).
     */
    private byte skipTagBackward() {
      int initialCharIndex = charIndex;
      while (!atStart()) {
        char c = charBackward();
        if (c == '<') {
          return Character.DIRECTIONALITY_WHITESPACE;
        }
        if (c == '>') {
          break;
        }
        if (c == '"' || c == '\'') {
          for (;;) {
            if (atStart() || charBackward() == c) {
              break;
            }
          }
        }
      }
      charIndex = initialCharIndex;
      lastChar = '>';
      return Character.DIRECTIONALITY_OTHER_NEUTRALS;
    }

    /**
     * Advances current position forward through an HTML character entity tag and returns
     * Character.DIRECTIONALITY_WHITESPACE. It would be best to figure out the actual character and
     * return its dirtype, but this is good enough.
     */
    private byte skipEntityForward() {
      do {} while (!atEnd() && charForward() != ';');
      return Character.DIRECTIONALITY_WHITESPACE;
    }

    /**
     * Advances current position backward through an HTML character entity tag and returns
     * Character.DIRECTIONALITY_WHITESPACE. It would be best to figure out the actual character and
     * return its dirtype, but this is good enough. If the entity is not closed, does not advance
     * the position and returns Character.DIRECTIONALITY_OTHER_NEUTRALS (for the ';' that did not
     * start an entity after all).
     */
    private byte skipEntityBackward() {
      int initialCharIndex = charIndex;
      while (!atStart()) {
        char c = charBackward();
        if (c == '&') {
          return Character.DIRECTIONALITY_WHITESPACE;
        }
        if (c == ';') {
          break;
        }
      }
      charIndex = initialCharIndex;
      lastChar = ';';
      return Character.DIRECTIONALITY_OTHER_NEUTRALS;
    }
  }

  /**
   * An object that estimates the directionality of a given string by various methods. The word
   * count method is a port of the DirectionalityEstimator in i18n/bidi/bidi_classifier.cc.
   * Although thic class extends DirTypeIterator, this is just for efficiency. All of its methods
   * ignore the initial position in the iteration, and end with the iteration at some arbitrary
   * point of their choosing.
   */
  static class DirectionalityEstimator extends DirTypeIterator {

    // Statics

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
       * A "word" between LRE/LRO/RLE/RLO and matching PDF; embedLevel has been incremented.
       */
      public static final int EMBEDDED = 4;
    }

    /**
     * If at least RTL_THRESHOLD of the words containing strong LTR or RTL in the string start with
     * RTL, the string as a whole is judged to be RTL.
     */
    private static final double RTL_THRESHOLD = 0.4;


    // Private data members

    /**
     * Number of LTR words found so far.
     */
    private int ltrWordCount;

    /**
     * Number of RTL words found so far.
     */
    private int rtlWordCount;

    /**
     * Number of "weak" LTR words (numbers and URLs) found so far.
     */
    private int weakLtrWordCount;

    /**
     * Number of unmatched LRE/LRO/RLE/RLO characters before current position.
     */
    private int embedLevel;

    /**
     * Type (so far) of the word continuing at the current position in the string.
     */
    private int wordType;


    // Constructors

    /**
     * Creates a DirectionalityEstimator, given a string and whether it is to be treated as HTML,
     * skipping over mark-up.
     * @param text The string to scan.
     * @param isHtml Whether the text to be scanned is to be treated as HTML, i.e. skipping over
     *     tags and entities.
     */
    public DirectionalityEstimator(String text, boolean isHtml) {
      super(text, isHtml);
    }


    // Public methods

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
    public Dir estimateDirectionByWordCount() {
      rewind(false);
      ltrWordCount = 0;
      rtlWordCount = 0;
      weakLtrWordCount = 0;
      embedLevel = 0;
      wordType = WordType.NEUTRAL;
      while (!atEnd()) {
        switch (dirTypeForward()) {
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            // Strong RTL codepoint.
            // Convert WordType.NEUTRAL or WordType.NUMERIC word to WordType.STRONG.
            strongWord(true /* isRightToLeft */);
            break;
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
            // Strong LTR codepoint.
            // If it is the beginning of a URL, convert WordType.NEUTRAL to WordType.URL.
            // Otherwise, convert WordType.NEUTRAL or WordType.NUMERIC word to WordType.STRONG.
            if (wordType == WordType.NEUTRAL && getLastChar() == 'h' &&
                (matchForward("ttp://", true) || matchForward("ttps://", true))) {
              wordType = WordType.URL;
              ++weakLtrWordCount;
            } else {
              strongWord(false /* isRightToLeft */);
            }
            break;
          case Character.DIRECTIONALITY_EUROPEAN_NUMBER:
          case Character.DIRECTIONALITY_ARABIC_NUMBER:
            // Convert WordType.NEUTRAL word to WordType.NUMERIC.
            if (wordType < WordType.NUMERIC) {
              ++weakLtrWordCount;
              wordType = WordType.NUMERIC;
            }
            break;
          case Character.DIRECTIONALITY_WHITESPACE:
          case Character.DIRECTIONALITY_SEGMENT_SEPARATOR:
            // End of word, unless embedded.
            if (wordType < WordType.EMBEDDED) {
              wordType = WordType.NEUTRAL;
            }
            break;
          case Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR:
            // End of word, and reset embedding level.
            embedLevel = 0;
            wordType = WordType.NEUTRAL;
            break;
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            // LRO overrides the directionality of the characters inside it, so treat them as
            // a strongly LTR word.
            strongWord(false /* isRightToLeft */);
            // Fall through to LRE processing.
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
            // Start embedded area.
            if (embedLevel++ == 0) {
              wordType = WordType.EMBEDDED;
            }
            break;
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            // RLO overrides the directionality of the characters inside it, so treat them as
            // a strongly RTL word.
            strongWord(true /* isRightToLeft */);
            // Fall through to RLE processing.
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
            // Start embedded area.
            if (embedLevel++ == 0) {
              wordType = WordType.EMBEDDED;
            }
            break;
          case Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
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

    /**
     * Upgrades WordType.NEUTRAL or WordType.NUMERIC word to WordType.STRONG and adjusts the word
     * counts appropriately for the given direction.
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
     * Checks if the (whole) string has any LTR characters in it.
     * @param countEmbedding Whether LRE/RLE/LRO/RLO/PDF characters should be taken into account.
     * @return Whether any LTR characters were encountered.
     */
    public boolean hasAnyLtr(boolean countEmbedding) {
      rewind(false);
      int embeddingLevel = 0;
      while (!atEnd()) {
        switch (dirTypeForward()) {
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
            if (embeddingLevel == 0) {
              return true;
            }
            break;
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            if (countEmbedding && embeddingLevel++ == 0) {
              return true;
            }
            break;
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            if (countEmbedding) {
              ++embeddingLevel;
            }
            break;
          case Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
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
     * @param countEmbedding Whether LRE/RLE/LRO/RLO/PDF characters should be taken into account.
     * @return Whether any RTL characters were encountered.
     */
    public boolean hasAnyRtl(boolean countEmbedding) {
      rewind(false);
      int embeddingLevel = 0;
      while (!atEnd()) {
        switch (dirTypeForward()) {
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            if (embeddingLevel == 0) {
              return true;
            }
            break;
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            if (countEmbedding && embeddingLevel++ == 0) {
              return true;
            }
            break;
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            if (countEmbedding) {
              ++embeddingLevel;
            }
            break;
          case Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
            if (countEmbedding) {
              --embeddingLevel;
            }
            break;
        }
      }
      return false;
    }

    /**
     * Returns the direction of the first character with strong directionality (going forward from
     * the start of the string).
     * @param countEmbedding Whether LRE/RLE/LRO/RLO/PDF characters should be taken into account.
     * @return The direction of the first character (going forwards!) with strong directionality,
     *     or Dir.UNKNOWN if none was encountered.
     */
    public Dir firstStrong(boolean countEmbedding) {
      rewind(false);
      while (!atEnd()) {
        switch (dirTypeForward()) {
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
            return Dir.LTR;
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            if (countEmbedding) {
              return Dir.LTR;
            }
            break;
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            return Dir.RTL;
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            if (countEmbedding) {
              return Dir.RTL;
            }
            break;
        }
      }
      return Dir.UNKNOWN;
    }

    /**
     * Returns the direction of the first character with strong directionality going backward from
     * the end of the string.
     * @param countEmbedding Whether LRE/RLE/LRO/RLO/PDF characters should be taken into account.
     * @return The direction of the first character (going backwards!) with strong directionality,
     *     or Dir.UNKNOWN if none was encountered.
     */
    public Dir lastStrong(boolean countEmbedding) {
      rewind(true);
      int embeddingLevel = 0;
      while (!atStart()) {
        switch (dirTypeBackward()) {
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
            if (embeddingLevel == 0) {
              return Dir.LTR;
            }
            break;
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING:
          case Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE:
            if (countEmbedding && --embeddingLevel == 0) {
              return Dir.LTR;
            }
            break;
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            if (embeddingLevel == 0) {
              return Dir.RTL;
            }
            break;
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
          case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
            if (countEmbedding && --embeddingLevel == 0) {
              return Dir.RTL;
            }
            break;
          case Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT:
            if (countEmbedding) {
              ++embeddingLevel;
            }
            break;
        }
      }
      return Dir.UNKNOWN;
    }
  }

  /**
   * Checks if the given string has any LTR characters in it. Note that LRE/RLE/LRO/RLO/PDF
   * characters are ignored.
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
   * @param str the string to be tested
   * @return whether the string contains any LTR characters
   */
  public static boolean hasAnyLtr(String str) {
    return hasAnyLtr(str, false /* isHtml */);
  }

  /**
   * Checks if the given string has any RTL characters in it. Note that LRE/RLE/LRO/RLO/PDF
   * characters are ignored.
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
   * @param str the string to be tested
   * @return whether the string contains any RTL characters
   */
  public static boolean hasAnyRtl(String str) {
    return hasAnyRtl(str, false /* isHtml */);
  }

  /**
   * Returns true if the first character with strong directionality is an LTR character.
   * LRE/RLE/LRO/RLO are considered to have strong directionality.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return true if LTR directionality is detected
   */
  public static boolean startsWithLtr(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).firstStrong(true /* countEmbedding */)
        == Dir.LTR;
  }

  /**
   * Like {@link #startsWithLtr(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   * @param str the string to check
   * @return true if LTR directionality is detected
   */
  public static boolean startsWithLtr(String str) {
    return startsWithLtr(str, false /* isHtml */);
  }

  /**
   * Returns true if the first character with strong directionality is an RTL character.
   * LRE/RLE/LRO/RLO are considered to have strong directionality.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return true if rtl directionality is detected
   */
  public static boolean startsWithRtl(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).firstStrong(true /* countEmbedding */)
        == Dir.RTL;
  }

  /**
   * Like {@link #startsWithRtl(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   * @param str the string to check
   * @return true if rtl directionality is detected
   */
  public static boolean startsWithRtl(String str) {
    return startsWithRtl(str, false /* isHtml */);
  }

  /**
   * Check whether the exit directionality of a piece of text is LTR, i.e. if the last
   * strongly-directional character in the string is LTR. If the text ends with a balanced
   * LRE|RLE|LRO|RLO...PDF sequence, the opening character of that sequence determines the exit
   * directionality, e.g. LRE...PDF is considered LTR regardless of what's inside it. The intended
   * use is to check whether a logically separate item that starts with a number or an LTR character
   * and follows this text inline (not counting any neutral characters in between) would "stick" to
   * it in an RTL context, thus being displayed in an incorrect position. An RLM character between
   * the two would prevent the sticking in such a case.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether LTR exit directionality was detected
   */
  public static boolean endsWithLtr(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).lastStrong(true /* countEmbedding */)
        == Dir.LTR;
  }

  /**
   * Like {@link #endsWithLtr(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   */
  public static boolean endsWithLtr(String str) {
    return endsWithLtr(str, false /* isHtml */);
  }

  /**
   * Check whether the exit directionality of a piece of text is RTL, i.e. if the last
   * strongly-directional character in the string is RTL. If the text ends with a balanced
   * LRE|RLE|LRO|RLO...PDF sequence, the opening character of that sequence determines the exit
   * directionality, e.g. LRE...PDF is considered LTR regardless of what's inside it. The intended
   * use is to check whether a logically separate item that starts with a number or an RTL character
   * and follows this text inline (not counting any neutral characters in between) would "stick" to
   * it in an LTR context, thus being displayed in an incorrect position. An LRM character between
   * the two would prevent the sticking in such a case.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether RTL exit directionality was detected
   */
  public static boolean endsWithRtl(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).lastStrong(true /* countEmbedding */)
        == Dir.RTL;
  }

  /**
   * Like {@link #endsWithRtl(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   */
  public static boolean endsWithRtl(String str) {
    return endsWithRtl(str, false /* isHtml */);
  }

  /**
   * Estimates the directionality of a string based on relative word counts.
   * If the number of RTL words is above a certain percentage of the total number of strongly
   * directional words, returns RTL.
   * Otherwise, if any words are strongly or weakly LTR, returns LTR.
   * Otherwise, returns UNKNOWN, which is used to mean "neutral".
   * Numbers are counted as weakly LTR.
   * @param str the string to check
   * @return the string's directionality
   */
  public static Dir estimateDirection(String str) {
    return estimateDirection(str, false /* isHtml */);
  }

  /**
   * Like {@link #estimateDirection(String)}, but can treat {@code str} as HTML,
   * ignoring HTML tags and escapes that would otherwise be mistaken for LTR text.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   */
  public static Dir estimateDirection(String str, boolean isHtml) {
    return new DirectionalityEstimator(str, isHtml).estimateDirectionByWordCount();
  }
}
