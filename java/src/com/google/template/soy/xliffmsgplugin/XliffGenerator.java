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
import com.google.common.collect.Iterables;
import com.google.common.escape.Escaper;
import com.google.common.xml.XmlEscapers;
import com.google.template.soy.base.internal.IndentedLinesBuilder;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;

import java.util.ArrayList;
import java.util.List;
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

  private final Escaper attributeEscaper = XmlEscapers.xmlAttributeEscaper();
  private final Escaper contentEscaper = XmlEscapers.xmlContentEscaper();
  private final String sourceLocaleString;
  private final String targetLocaleString;
  private final boolean hasTarget;

  private final IndentedLinesBuilder ilb = new IndentedLinesBuilder(2);

  private XliffGenerator(String sourceLocaleString, @Nullable String targetLocaleString) {
    this.sourceLocaleString = sourceLocaleString;
    this.targetLocaleString = targetLocaleString;
    this.hasTarget = targetLocaleString != null && targetLocaleString.length() > 0;
  }


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
    return new XliffGenerator(sourceLocaleString, targetLocaleString).generate(msgBundle);
  }

  private CharSequence generate(SoyMsgBundle msgBundle) {
    ilb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    ilb.appendLine("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\">");
    ilb.increaseIndent();
    ilb.appendLineStart(
        "<file original=\"SoyMsgBundle\" datatype=\"x-soy-msg-bundle\"", " xml:space=\"preserve\"",
        " source-language=\"", attributeEscaper.escape(sourceLocaleString), "\"");
    if (hasTarget) {
      ilb.appendParts(" target-language=\"", attributeEscaper.escape(targetLocaleString), "\"");
    }
    ilb.appendLineEnd(">");
    ilb.increaseIndent();
    ilb.appendLine("<body>");
    ilb.increaseIndent();

    for (SoyMsg msg : msgBundle) {
      generateSoyMsg(ilb, msg);
    }

    ilb.decreaseIndent();
    ilb.appendLine("</body>");
    ilb.decreaseIndent();
    ilb.appendLine("</file>");
    ilb.decreaseIndent();
    ilb.appendLine("</xliff>");

    return ilb;
  }

  private void generateSoyMsg(IndentedLinesBuilder ilb, SoyMsg msg) {
    generateSoyMsgHelper(ilb, msg, msg.getParts(), "");
  }

  /**
   * A message may be one or more nested select/plural branches.
   *
   * We need to flatten these into a list of transunits, one for each complete
   * path through the branches.
   */
  private void generateSoyMsgHelper(IndentedLinesBuilder ilb, SoyMsg msg, List<SoyMsgPart> parts, String idSuffix) {
    SoyMsgPart firstPart = Iterables.getFirst(parts, null);
    if (firstPart instanceof SoyMsgSelectPart) {
      if (parts.size() != 1) {
        throw new IllegalStateException("Illegal SELECT AST");
      }

      ilb.appendLine("<group restype=\"" + XliffResType.SELECT + "\">");
      ilb.increaseIndent();

      SoyMsgSelectPart selectPart = (SoyMsgSelectPart) firstPart;
      for (Case<String> selectCase : selectPart.getCases()) {
        String caseSpec = selectCase.spec();
        if (caseSpec == null) {
          caseSpec = "default";
        }
        generateSoyMsgHelper(
            ilb, msg, selectCase.parts(),
            idSuffix + "[" + caseSpec + "]");
      }

      ilb.decreaseIndent();
      ilb.appendLine("</group>");
    } else if (firstPart instanceof SoyMsgPluralPart) {
      if (parts.size() != 1) {
        throw new IllegalStateException("Illegal PLURALS AST");
      }

      ilb.appendLine("<group restype=\"" + XliffResType.PLURAL + "\">");
      ilb.increaseIndent();

      SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) firstPart;
      for (Case pluralCase : pluralPart.getCases()) {
        Object caseSpec = pluralCase.spec();
        if (caseSpec == null) {
          caseSpec = "default";
        }
        generateSoyMsgHelper(
            ilb, msg, pluralCase.parts(),
            idSuffix + "[" + caseSpec + "]");
      }

      ilb.decreaseIndent();
      ilb.appendLine("</group>");
    } else {
      generateTransUnit(ilb, msg, parts, idSuffix);
    }
  }

  private void generateTransUnit(IndentedLinesBuilder ilb, SoyMsg msg, List<SoyMsgPart> parts, String idSuffix) {
    Escaper attributeEscaper = XmlEscapers.xmlAttributeEscaper();
    Escaper contentEscaper = XmlEscapers.xmlContentEscaper();

    String id = Long.toString(msg.getId()) + idSuffix;
    ilb.appendLineStart("<trans-unit id=\"", attributeEscaper.escape(id), "\"");
    String contentType = msg.getContentType();
    if (contentType != null && contentType.length() > 0) {
      String xliffDatatype = CONTENT_TYPE_TO_XLIFF_DATATYPE_MAP.get(contentType);
      if (xliffDatatype == null) {
        xliffDatatype = contentType;  // just use the contentType string
      }
      ilb.appendParts(" datatype=\"", attributeEscaper.escape(xliffDatatype), "\"");
    }
    ilb.appendLineEnd(">");
    ilb.increaseIndent();

    // Source.
    ilb.appendLineStart("<source>");
    for (SoyMsgPart msgPart : parts) {
      if (msgPart instanceof SoyMsgRawTextPart) {
        String rawText = ((SoyMsgRawTextPart) msgPart).getRawText();
        ilb.append(contentEscaper.escape(rawText));
      } else if (msgPart instanceof SoyMsgPlaceholderPart) {
        String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
        ilb.appendParts("<x id=\"", attributeEscaper.escape(placeholderName), "\"/>");
      } else {
        throw new IllegalStateException("Found unexpected message part: " + msgPart);
      }
    }
    ilb.appendLineEnd("</source>");

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
}
