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

package com.google.template.soy.msgs.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import javax.annotation.Nullable;

/**
 * Static methods to compute the unique message id for a message.
 */
final class SoyMsgIdComputer {

  private SoyMsgIdComputer() {}

  /**
   * Computes the unique message id for a message, given the message parts, the meaning string (if
   * any), and the content type (if any). These are the only elements incorporated into the message
   * id.
   *
   * <p>In particular, note that the id of a message does not change when its desc changes.
   *
   * @param msgParts The parts of the message.
   * @param meaning The meaning string, or null if none (usually null).
   * @param contentType Content type of the document that this message will appear in (e.g. "{@code
   *     text/html}", or null if not used.
   * @return The computed message id.
   */
  static long computeMsgId(
      ImmutableList<SoyMsgPart> msgParts, @Nullable String meaning, @Nullable String contentType) {
    return computeMsgIdHelper(msgParts, false, meaning, contentType);
  }

  /**
   * Computes an alternate unique message id for a message, given the message parts, the meaning
   * string (if any), and the content type (if any). These are the only elements incorporated into
   * the message id.
   *
   * <p>In particular, note that the id of a message does not change when its desc changes.
   *
   * <p>Important: This is an alternate message id computation using braced placeholders. Only use
   * this function instead of {@link #computeMsgId} if you know that you need this alternate format.
   *
   * @param msgParts The parts of the message.
   * @param meaning The meaning string, or null if none (usually null).
   * @param contentType Content type of the document that this message will appear in (e.g. "{@code
   *     text/html}", or null if not used..
   * @return The computed message id.
   */
  static long computeMsgIdUsingBracedPhs(
      ImmutableList<SoyMsgPart> msgParts, @Nullable String meaning, @Nullable String contentType) {
    return computeMsgIdHelper(msgParts, true, meaning, contentType);
  }

  /**
   * Computes the unique message id for a message, given the message parts, the meaning string (if
   * any), and the content type (if any). These are the only elements incorporated into the message
   * id.
   *
   * <p>In particular, note that the id of a message does not change when its desc changes.
   *
   * @param msgParts The parts of the message.
   * @param doUseBracedPhs Whether to use braced placeholders. (Even though braced placeholders
   *     originated from ICU syntax, the choice of whether to use braced placeholders when computing
   *     msg id is still a separate decision, even if your message is plural/select and you do use
   *     ICU syntax to represent plural/select parts.)
   * @param meaning The meaning string, or null if none (usually null).
   * @param contentType Content type of the document that this message will appear in (e.g. "{@code
   *     text/html}", or null if not used..
   */
  private static long computeMsgIdHelper(
      ImmutableList<SoyMsgPart> msgParts,
      boolean doUseBracedPhs,
      @Nullable String meaning,
      @Nullable String contentType) {

    // Important: Do not change this algorithm. Doing so will break backwards compatibility.

    String msgContentStrForMsgIdComputation =
        buildMsgContentStrForMsgIdComputation(msgParts, doUseBracedPhs);

    long fp = fingerprint(msgContentStrForMsgIdComputation);

    // If there is a meaning, incorporate its fingerprint.
    if (meaning != null) {
      fp = (fp << 1) + (fp < 0 ? 1 : 0) + fingerprint(meaning);
    }

    // If there is a content type other than "text/html", incorporate its fingerprint.
    // TODO(nicholasyu): This is never executed. Look into removing.
    if (contentType != null && !contentType.equals("text/html")) {
      fp = (fp << 1) + (fp < 0 ? 1 : 0) + fingerprint(contentType);
    }

    // To avoid negative ids we strip the high-order bit.
    return fp & 0x7fffffffffffffffL;
  }

  /**
   * Private helper to build the canonical message content string that should be used for msg id
   * computation.
   *
   * <p>Note: For people who know what "presentation" means in this context, the result string
   * should be exactly the presentation string.
   *
   * @param msgParts The parts of the message.
   * @param doUseBracedPhs Whether to use braced placeholders.
   * @return The canonical message content string that should be used for msg id computation.
   */
  @VisibleForTesting
  static String buildMsgContentStrForMsgIdComputation(
      ImmutableList<SoyMsgPart> msgParts, boolean doUseBracedPhs) {

    msgParts = IcuSyntaxUtils.convertMsgPartsToEmbeddedIcuSyntax(msgParts);

    StringBuilder msgStrSb = new StringBuilder();

    for (SoyMsgPart msgPart : msgParts) {

      if (msgPart instanceof SoyMsgRawTextPart) {
        msgStrSb.append(((SoyMsgRawTextPart) msgPart).getRawText());

      } else if (msgPart instanceof SoyMsgPlaceholderPart) {
        if (doUseBracedPhs) {
          msgStrSb.append('{');
        }
        msgStrSb.append(((SoyMsgPlaceholderPart) msgPart).getPlaceholderName());
        if (doUseBracedPhs) {
          msgStrSb.append('}');
        }

      } else {
        throw new AssertionError("unexpected child: " + msgPart);
      }
    }

    return msgStrSb.toString();
  }

  @VisibleForTesting
  static long fingerprint(String str) {

    byte[] strBytes = str.getBytes(UTF_8);
    int hi = hash32(strBytes, 0, strBytes.length, 0);
    int lo = hash32(strBytes, 0, strBytes.length, 102072);
    if ((hi == 0) && (lo == 0 || lo == 1)) {
      // Turn 0/1 into another fingerprint
      hi ^= 0x130f9bef;
      lo ^= 0x94a0a928;
    }
    return (((long) hi) << 32) | (lo & 0xffffffffL);
  }

  @SuppressWarnings({
    "PointlessBitwiseExpression",
    "PointlessArithmeticExpression",
    "FallThrough"
  }) // IntelliJ
  private static int hash32(byte[] str, int start, int limit, int c) {

    int a = 0x9e3779b9;
    int b = 0x9e3779b9;

    int i;
    for (i = start; i + 12 <= limit; i += 12) {

      a +=
          (((str[i + 0] & 0xff) << 0)
              | ((str[i + 1] & 0xff) << 8)
              | ((str[i + 2] & 0xff) << 16)
              | ((str[i + 3] & 0xff) << 24));
      b +=
          (((str[i + 4] & 0xff) << 0)
              | ((str[i + 5] & 0xff) << 8)
              | ((str[i + 6] & 0xff) << 16)
              | ((str[i + 7] & 0xff) << 24));
      c +=
          (((str[i + 8] & 0xff) << 0)
              | ((str[i + 9] & 0xff) << 8)
              | ((str[i + 10] & 0xff) << 16)
              | ((str[i + 11] & 0xff) << 24));

      // Mix.
      a -= b;
      a -= c;
      a ^= (c >>> 13);
      b -= c;
      b -= a;
      b ^= (a << 8);
      c -= a;
      c -= b;
      c ^= (b >>> 13);
      a -= b;
      a -= c;
      a ^= (c >>> 12);
      b -= c;
      b -= a;
      b ^= (a << 16);
      c -= a;
      c -= b;
      c ^= (b >>> 5);
      a -= b;
      a -= c;
      a ^= (c >>> 3);
      b -= c;
      b -= a;
      b ^= (a << 10);
      c -= a;
      c -= b;
      c ^= (b >>> 15);
    }

    c += limit - start;
    switch (limit - i) { // Deal with rest. Cases fall through.
      case 11:
        c += (str[i + 10] & 0xff) << 24;
      case 10:
        c += (str[i + 9] & 0xff) << 16;
      case 9:
        c += (str[i + 8] & 0xff) << 8;
        // the first byte of c is reserved for the length
      case 8:
        b += (str[i + 7] & 0xff) << 24;
      case 7:
        b += (str[i + 6] & 0xff) << 16;
      case 6:
        b += (str[i + 5] & 0xff) << 8;
      case 5:
        b += (str[i + 4] & 0xff);
      case 4:
        a += (str[i + 3] & 0xff) << 24;
      case 3:
        a += (str[i + 2] & 0xff) << 16;
      case 2:
        a += (str[i + 1] & 0xff) << 8;
      case 1:
        a += (str[i + 0] & 0xff);
        break;
      default: // fall out

        // case 0 : nothing left to add
    }

    // Mix.
    a -= b;
    a -= c;
    a ^= (c >>> 13);
    b -= c;
    b -= a;
    b ^= (a << 8);
    c -= a;
    c -= b;
    c ^= (b >>> 13);
    a -= b;
    a -= c;
    a ^= (c >>> 12);
    b -= c;
    b -= a;
    b ^= (a << 16);
    c -= a;
    c -= b;
    c ^= (b >>> 5);
    a -= b;
    a -= c;
    a ^= (c >>> 3);
    b -= c;
    b -= a;
    b ^= (a << 10);
    c -= a;
    c -= b;
    c ^= (b >>> 15);

    return c;
  }
}
