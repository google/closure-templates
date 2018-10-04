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
package com.google.template.soy.bidifunctions;

import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.i18n.BidiFormatter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.BidiUtils;

/** Contains static functions that implement the java versions of the bidifunctions. */
public final class BidiFunctionsRuntime {

  public static SanitizedContent bidiDirAttrSanitized(
      BidiGlobalDir dir, SoyValue value, boolean isHtml) {
    Dir valueDir = null;
    boolean isHtmlForValueDirEstimation = false;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueDir = sanitizedContent.getContentDirection();
      if (valueDir == null) {
        isHtmlForValueDirEstimation = sanitizedContent.getContentKind() == ContentKind.HTML;
      }
    }

    if (valueDir == null) {
      isHtmlForValueDirEstimation = isHtmlForValueDirEstimation || isHtml;
      valueDir = BidiUtils.estimateDirection(value.coerceToString(), isHtmlForValueDirEstimation);
    }

    BidiFormatter bidiFormatter = BidiFormatter.getInstance(dir.toDir());
    return bidiFormatter.knownDirAttrSanitized(valueDir);
  }

  public static String bidiEndEdge(BidiGlobalDir bidiGlobalDir) {
    return bidiGlobalDir.getStaticValue() < 0 ? "left" : "right";
  }

  public static String bidiStartEdge(BidiGlobalDir bidiGlobalDir) {
    return bidiGlobalDir.getStaticValue() < 0 ? "right" : "left";
  }

  public static String bidiMarkAfter(BidiGlobalDir bidiGlobalDir, SoyValue value, boolean isHtml) {
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueDir = sanitizedContent.getContentDirection();
      isHtml = isHtml || sanitizedContent.getContentKind() == ContentKind.HTML;
    }

    String markAfterKnownDir =
        BidiFormatter.getInstance(bidiGlobalDir.toDir())
            .markAfter(valueDir, value.coerceToString(), isHtml);
    return markAfterKnownDir;
  }

  public static String bidiMark(BidiGlobalDir bidiGlobalDir) {
    return (bidiGlobalDir.getStaticValue() < 0) ? "\u200F" /*RLM*/ : "\u200E" /*LRM*/;
  }

  public static int bidiTextDir(SoyValue value, boolean isHtml) {
    Dir valueDir = null;
    boolean isHtmlForValueDirEstimation = false;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueDir = sanitizedContent.getContentDirection();
      if (valueDir == null) {
        isHtmlForValueDirEstimation = sanitizedContent.getContentKind() == ContentKind.HTML;
      }
    }
    if (valueDir == null) {
      isHtmlForValueDirEstimation = isHtmlForValueDirEstimation || isHtml;
      valueDir = BidiUtils.estimateDirection(value.coerceToString(), isHtmlForValueDirEstimation);
    }
    return valueDir.ord;
  }

  public static int bidiGlobalDir(BidiGlobalDir dir) {
    return dir.getStaticValue();
  }
}
