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

import com.google.common.collect.Lists;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgException;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;


/**
 * Static function for parsing the content of a translated XLIFF file and creating a SoyMsgBundle.
 *
 * <p> XLIFF specification: http://docs.oasis-open.org/xliff/xliff-core/xliff-core.html
 *
 */
class XliffParser {

  private XliffParser() {}


  /**
   * Parses the content of a translated XLIFF file and creates a SoyMsgBundle.
   *
   * @param xliffContent The XLIFF content to parse.
   * @return The resulting SoyMsgBundle.
   * @throws SAXException If there's an error parsing the data.
   * @throws SoyMsgException If there's an error in parsing the data.
   */
  static SoyMsgBundle parseXliffTargetMsgs(String xliffContent)
      throws SAXException, SoyMsgException {

    // Get a SAX parser.
    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    SAXParser saxParser;
    try {
      saxParser = saxParserFactory.newSAXParser();
    } catch (ParserConfigurationException pce) {
      throw new AssertionError("Could not get SAX parser for XML.");
    }

    // Construct the handler for SAX parsing.
    XliffSaxHandler xliffSaxHandler = new XliffSaxHandler();

    // Parse the XLIFF content.
    try {
      saxParser.parse(new InputSource(new StringReader(xliffContent)), xliffSaxHandler);
    } catch (IOException e) {
      throw new AssertionError("Should not fail in reading a string.");
    }

    // Build a SoyMsgBundle from the parsed data (stored in xliffSaxHandler).
    return new SoyMsgBundleImpl(xliffSaxHandler.getTargetLocaleString(), xliffSaxHandler.getMsgs());
  }


  // -----------------------------------------------------------------------------------------------


  /**
   * SAX handler for parsing the target messages from an XLIFF file.
   */
  private static class XliffSaxHandler extends DefaultHandler {


    /** Target locale string. */
    private String targetLocaleString;

    /** List of target messages collected. */
    private final List<SoyMsg> msgs;

    /** Whether we're currently inside a target message (while parsing). */
    private boolean isInMsg;

    /** Message id of the message we're currently building (while parsing). */
    private long currMsgId;

    /** Parts of the message we're currently building (while parsing). */
    private List<SoyMsgPart> currMsgParts;

    /**
     * The raw text part we're currently building. We're doing this because in theory, the SAX
     * parser can break up a raw text string and return it in multiple {@code characters()} calls.
     */
    private String currRawTextPart;


    public XliffSaxHandler() {
      msgs = Lists.newArrayList();
      isInMsg = false;
    }


    /** Returns the target locale string parsed from the XLIFF file. */
    public String getTargetLocaleString() {
      return targetLocaleString;
    }

    /** Returns the target messages parsed from the XLIFF file. */
    public List<SoyMsg> getMsgs() {
      return msgs;
    }


    @Override public void startElement(
        String uri, String localName, String qName, Attributes atts) {

      if (qName.equals("file")) {
        // Start 'file': Save the target locale string.
        if (targetLocaleString == null) {
          targetLocaleString = atts.getValue("target-language");
        } else {
          if (!atts.getValue("target-language").equals(targetLocaleString)) {
            throw new SoyMsgException(
                "If XLIFF input contains multiple 'file' elements, they must have the same" +
                " 'target-language'.");
          }
        }

      } else if (qName.equals("trans-unit")) {
        // Start 'trans-unit': Save the message id.
        String id = atts.getValue("id");
        try {
          currMsgId = Long.parseLong(id);
        } catch (NumberFormatException e) {
          throw new SoyMsgException(
              "Invalid message id '" + id + "' could not have been generated by the Soy compiler.");
        }

      } else if (qName.equals("target")) {
        // Start 'target': Prepare to collect the message parts (coming next).
        currMsgParts = Lists.newArrayList();
        currRawTextPart = null;
        isInMsg = true;

      } else if (isInMsg) {
        if (!qName.equals("x")) {
          throw new SoyMsgException(
              "In messages extracted by the Soy compiler, all placeholders should be element 'x'" +
              " (found element '" + qName + "' in message).");
        }
        // Placeholder in message: Save the preceding raw text (if any) and then save the
        // placeholder name.
        if (currRawTextPart != null) {
          currMsgParts.add(new SoyMsgRawTextPart(currRawTextPart));
          currRawTextPart = null;
        }
        currMsgParts.add(new SoyMsgPlaceholderPart(atts.getValue("id")));
      }
    }


    @Override public void endElement(String uri, String localName, String qName) {

      if (qName.equals("target")) {
        // End 'target': Save the preceding raw text (if any). Then create a SoyMsg object from the
        // collected message data and add it to msgs list.
        if (currRawTextPart != null) {
          currMsgParts.add(new SoyMsgRawTextPart(currRawTextPart));
          currRawTextPart = null;
        }
        isInMsg = false;
        if (currMsgParts.size() > 0) {
          msgs.add(new SoyMsg(
              currMsgId, targetLocaleString, null, null, false, null, null, currMsgParts));
        }
      }
    }


    @Override public void characters(char[] buffer, int start, int length) {

      if (!isInMsg) {
        // We don't care about characters if not currently inside a message.
        return;
      } else if (currRawTextPart == null) {
        // Common case: Save the characters to the currRawTextPart.
        currRawTextPart = new String(buffer, start, length);
      } else {
        // Rare but possible case: The current raw text part is being given to us in multiple calls
        // of characters(). Since we already have a currRawTextPart, we must append to it.
        currRawTextPart += new String(buffer, start, length);
      }
    }

  }

}
