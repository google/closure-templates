/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.error;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.protobuf.Descriptors.Descriptor;
import javax.annotation.Nullable;

/** Utility methods for constructing Soy error messages. */
public final class SoyErrors {

  /**
   * Given a collection of strings and a name that isn't contained in it. Return a message that
   * suggests one of the names.
   *
   * <p>Returns the empty string if {@code allNames} is empty or there is no close match.
   */
  public static String getDidYouMeanMessage(Iterable<String> allNames, String wrongName) {
    String closestName = getClosest(allNames, wrongName);
    if (closestName != null) {
      return String.format(" Did you mean '%s'?", closestName);
    }
    return "";
  }

  /**
   * Same as {@link #getDidYouMeanMessage(Iterable, String)} but with some additional heuristics for
   * proto fields.
   */
  public static String getDidYouMeanMessageForProtoFields(
      ImmutableSet<String> fields, Descriptor descriptor, String fieldName) {
    // TODO(b/27616446): when we have case enum support add a case here.
    if (fields.contains(fieldName + "List")) {
      return String.format(" Did you mean '%sList'?", fieldName);
    } else if (fields.contains(fieldName + "Map")) {
      return String.format(" Did you mean '%sMap'?", fieldName);
    } else {
      return getDidYouMeanMessage(fields, fieldName);
    }
  }

  /**
   * Returns the member of {@code allNames} that is closest to {@code wrongName}, or {@code null} if
   * {@code allNames} is empty.
   *
   * <p>The distance metric is a case insensitive Levenshtein distance.
   *
   * @throws IllegalArgumentException if {@code wrongName} is a member of {@code allNames}
   */
  @Nullable
  @VisibleForTesting
  static String getClosest(Iterable<String> allNames, String wrongName) {
    // only suggest matches that are closer than this.  This magic heuristic is based on what llvm
    // and javac do
    int shortest = (wrongName.length() + 2) / 3 + 1;
    String closestName = null;
    for (String otherName : allNames) {
      if (otherName.equals(wrongName)) {
        throw new IllegalArgumentException("'" + wrongName + "' is contained in " + allNames);
      }
      int distance = distance(otherName, wrongName, shortest);
      if (distance < shortest) {
        shortest = distance;
        closestName = otherName;
        if (distance == 0) {
          return closestName;
        }
      }
    }
    return closestName;
  }

  /**
   * Performs a case insensitive Levenshtein edit distance based on the 2 rows implementation.
   *
   * @param s The first string
   * @param t The second string
   * @param maxDistance The distance to beat, if we can't do better, stop trying
   * @return an integer describing the number of edits needed to transform s into t
   * @see "https://en.wikipedia.org/wiki/Levenshtein_distance#Iterative_with_two_matrix_rows"
   */
  private static int distance(String s, String t, int maxDistance) {
    // create two work vectors of integer distances
    // it is possible to reduce this to only one array, but performance isn't that important here.
    // We could also avoid calculating a lot of the entries by taking maxDistance into account in
    // the inner loop.  This would only be worth optimizing if it showed up in a profile.
    int[] v0 = new int[t.length() + 1];
    int[] v1 = new int[t.length() + 1];

    // initialize v0 (the previous row of distances)
    // this row is A[0][i]: edit distance for an empty s
    // the distance is just the number of characters to delete from t
    for (int i = 0; i < v0.length; i++) {
      v0[i] = i;
    }

    for (int i = 0; i < s.length(); i++) {
      // calculate v1 (current row distances) from the previous row v0
      // first element of v1 is A[i+1][0]
      //   edit distance is delete (i+1) chars from s to match empty t
      v1[0] = i + 1;
      int bestThisRow = v1[0];

      char sChar = Ascii.toLowerCase(s.charAt(i));
      // use formula to fill in the rest of the row
      for (int j = 0; j < t.length(); j++) {
        char tChar = Ascii.toLowerCase(t.charAt(j));
        v1[j + 1] =
            Math.min(
                v1[j] + 1, // deletion
                Math.min(
                    v0[j + 1] + 1, // insertion
                    v0[j] + ((sChar == tChar) ? 0 : 1))); // substitution
        bestThisRow = Math.min(bestThisRow, v1[j + 1]);
      }
      if (bestThisRow > maxDistance) {
        // if we couldn't possibly do better than maxDistance, stop trying.
        return maxDistance + 1;
      }

      // swap v1 (current row) to v0 (previous row) for next iteration. no need to clear previous
      // row since we always update all of v1 on each iteration.
      int[] tmp = v0;
      v0 = v1;
      v1 = tmp;
    }
    // The best answer is the last slot in v0 (due to the swap on the last iteration)
    return v0[t.length()];
  }

  /** Formats the errors in a standard way for displaying to a user. */
  public static String formatErrors(Iterable<SoyError> errors) {
    return formatErrorsInternal(errors, false);
  }

  /** Formats the errors, including only the error message (not its location or snippet). */
  public static String formatErrorsMessageOnly(Iterable<SoyError> errors) {
    return formatErrorsInternal(errors, true);
  }

  private static String formatErrorsInternal(Iterable<SoyError> errors, boolean messageOnly) {
    int numErrors = 0;
    int numWarnings = 0;
    for (SoyError error : errors) {
      if (error.isWarning()) {
        numWarnings++;
      } else {
        numErrors++;
      }
    }
    if (numErrors + numWarnings == 0) {
      throw new IllegalArgumentException("cannot format 0 errors");
    }
    StringBuilder sb =
        new StringBuilder(numErrors == 0 ? "warnings" : "errors")
            .append(" during Soy compilation\n");
    if (messageOnly) {
      Joiner.on("\n\n")
          .appendTo(
              sb,
              Streams.stream(errors)
                  .map(SoyError::message)
                  .collect(ImmutableList.toImmutableList()))
          .append('\n');
    } else {
      Joiner.on('\n').appendTo(sb, errors);
    }
    if (numErrors > 0) {
      formatNumber(numErrors, "error", sb);
    }
    if (numWarnings > 0) {
      if (numErrors > 0) {
        sb.append(' ');
      }
      formatNumber(numWarnings, "warning", sb);
    }
    return sb.append('\n').toString();
  }

  // hacky localization
  private static void formatNumber(int n, String type, StringBuilder to) {
    checkArgument(n > 0);
    to.append(n).append(' ').append(type).append(n == 1 ? "" : "s");
  }
}
