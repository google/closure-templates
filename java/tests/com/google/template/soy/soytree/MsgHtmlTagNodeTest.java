/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import junit.framework.TestCase;


/**
 * Unit tests for MsgHtmlTagNode.
 *
 */
public class MsgHtmlTagNodeTest extends TestCase {


  public void testPlaceholderMethods() throws SoySyntaxException {

    MsgHtmlTagNode mhtn =
        new MsgHtmlTagNode(0, Lists.<StandaloneNode>newArrayList(new RawTextNode(0, "<b>")));
    assertEquals("START_BOLD", mhtn.genBasePlaceholderName());
    assertTrue(
        mhtn.genSamenessKey().equals(
            (new MsgHtmlTagNode(4, Lists.<StandaloneNode>newArrayList(new RawTextNode(0, "<b>"))))
                .genSamenessKey()));
    assertFalse(
        mhtn.genSamenessKey().equals(
            (new MsgHtmlTagNode(4, Lists.<StandaloneNode>newArrayList(new RawTextNode(0, "</b>"))))
                .genSamenessKey()));
    assertEquals("<b>", mhtn.toSourceString());

    mhtn = new MsgHtmlTagNode(0, Lists.<StandaloneNode>newArrayList(new RawTextNode(0, "<br />")));
    assertEquals("BREAK", mhtn.genBasePlaceholderName());
    assertFalse(
        mhtn.genSamenessKey().equals(
            (new MsgHtmlTagNode(4, Lists.<StandaloneNode>newArrayList(new RawTextNode(0, "<br/>"))))
                .genSamenessKey()));
    assertEquals("<br />", mhtn.toSourceString());

    mhtn = new MsgHtmlTagNode(
        1,
        Lists.<StandaloneNode>newArrayList(
            new RawTextNode(0, "<div class=\""),
            new PrintNode(0, true, "$cssClass", null),
            new RawTextNode(0, "\">")));
    assertEquals("START_DIV", mhtn.genBasePlaceholderName());
    assertFalse(
        mhtn.genSamenessKey().equals(
            (new MsgHtmlTagNode(
                2,
                Lists.<StandaloneNode>newArrayList(
                    new RawTextNode(0, "<div class=\""),
                    new PrintNode(0, true, "$cssClass", null),
                    new RawTextNode(0, "\">"))))
                .genSamenessKey()));
    assertEquals("<div class=\"{$cssClass}\">", mhtn.toSourceString());
  }

}
