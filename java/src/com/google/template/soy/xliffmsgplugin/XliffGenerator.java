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

package com.google.template.soy.xliffmsgplugin;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.IndentedLinesBuilder;
import com.google.template.soy.internal.base.CharEscaper;
import com.google.template.soy.internal.base.CharEscapers;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;

import java.util.Map;

import javax.annotation.Nullable;


/**
 * Static function for generating the output XLIFF file content from a SoyMsgBundle of extracted
 * messages.
 *
 * <p> XLIFF specification: http://docs.oasis-open.org/xliff/xliff-core/xliff-core.html
 *
 */
class XliffGenerator {

  private XliffGenerator() {}


  /** Make some effort to use correct XLIFF datatype values. */
  private static final Map<String, String> CONTENT_TYPE_TO_XLIFF_DATATYPE_MAP =
      ImmutableMap.<String, String>builder()
          .put("text/plain", "plaintext")
          .put("text/html", "html")
          .put("application/xhtml+xml", "xhtml")
          .put("application/javascript", "javascript")
          .put("text/css", "css")
          .put("text/xml", "xml")
          .build();


  /**
   * Generates the output XLIFF file content for a given SoyMsgBundle.
   *
   * @param msgBundle The SoyMsgBundle to process.
   * @param sourceLocaleString The source language/locale string of the messages.
   * @param targetLocaleString The target language/locale string of the messages (optional). If
   *     specified, the resulting XLIFF file will specify this target language and will contain
   *     empty 'target' tags. If not specified, the resulting XLIFF file will not contain target
   *     language and will not contain 'target' tags.
   * @return The generated XLIFF file content.
   */
  static CharSequence generateXliff(
      SoyMsgBundle msgBundle, String sourceLocaleString, @Nullable String targetLocaleString) {

    CharEscaper attributeEscaper = CharEscapers.xmlEscaper();
    CharEscaper contentEscaper = CharEscapers.xmlContentEscaper();

    boolean hasTarget = targetLocaleString != null && targetLocaleString.length() > 0;

    IndentedLinesBuilder ilb = new IndentedLinesBuilder(2);
    ilb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    ilb.appendLine("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\">");
    ilb.increaseIndent();
    ilb.appendIndent().appendParts(
        "<file original=\"SoyMsgBundle\" datatype=\"x-soy-msg-bundle\"", " xml:space=\"preserve\"",
        " source-language=\"", attributeEscaper.escape(sourceLocaleString), "\"");
    if (hasTarget) {
      ilb.appendParts(" target-language=\"", attributeEscaper.escape(targetLocaleString), "\"");
    }
    ilb.append(">").appendLineEnd();
    ilb.increaseIndent();
    ilb.appendLine("<body>");
    ilb.increaseIndent();

    for (SoyMsg msg : msgBundle) {

      // Begin 'trans-unit'.
      ilb.appendIndent().appendParts("<trans-unit id=\"", Long.toString(msg.getId()), "\"");
      String contentType = msg.getContentType();
      if (contentType != null && contentType.length() > 0) {
        String xliffDatatype = CONTENT_TYPE_TO_XLIFF_DATATYPE_MAP.get(contentType);
        if (xliffDatatype == null) {
          xliffDatatype = contentType;  // just use the contentType string
        }
        ilb.appendParts(" datatype=\"", attributeEscaper.escape(xliffDatatype), "\"");
      }
      ilb.append(">").appendLineEnd();
      ilb.increaseIndent();

      // Source.
      ilb.appendIndent().append("<source>");
      for (SoyMsgPart msgPart : msg.getParts()) {
        if (msgPart instanceof SoyMsgRawTextPart) {
          String rawText = ((SoyMsgRawTextPart) msgPart).getRawText();
          ilb.append(contentEscaper.escape(rawText));
        } else {
          String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
          ilb.appendParts("<x id=\"", attributeEscaper.escape(placeholderName), "\"/>");
        }
      }
      ilb.append("</source>").appendLineEnd();

      // Target.
      if (hasTarget) {
        ilb.appendLine("<target/>");
      }

      // Description and meaning.
      String desc = msg.getDesc();
      if (desc != null && desc.length() > 0) {
        ilb.appendLine(
            "<note priority=\"1\" from=\"description\">", contentEscaper.escape(desc), "</note>");
      }
      String meaning = msg.getMeaning();
      if (meaning != null && meaning.length() > 0) {
        ilb.appendLine(
            "<note priority=\"1\" from=\"meaning\">", contentEscaper.escape(meaning), "</note>");
      }

      // End 'trans-unit'.
      ilb.decreaseIndent();
      ilb.appendLine("</trans-unit>");
    }

    ilb.decreaseIndent();
    ilb.appendLine("</body>");
    ilb.decreaseIndent();
    ilb.appendLine("</file>");
    ilb.decreaseIndent();
    ilb.appendLine("</xliff>");

    return ilb;
  }

}
