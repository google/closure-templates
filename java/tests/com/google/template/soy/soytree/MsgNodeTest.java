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
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import junit.framework.TestCase;


/**
 * Unit tests for MsgNode.
 *
 * @author Kai Huang
 */
public class MsgNodeTest extends TestCase {


  public void testGenPlaceholderNames() throws Exception {

    // Test message structure:
    // {msg desc=""}
    //   <a href="{$url1}">                  [START_LINK_1]
    //     {$boo}{$foo.goo}{1 + 1}{2 + 2}    [BOO, GOO_1, XXX_1, XXX_2]
    //   </a>                                [END_LINK]
    //   <br><br/><br /><br /><br>           [START_BREAK, BREAK_1, BREAK_2, BREAK_2, START_BREAK]
    //   <a href="{$url2}">                  [START_LINK_2]
    //     {$boo}{$goo}{$goo2}{2 + 2}        [BOO, GOO_3, GOO_2, XXX_2]
    //   </a>                                [END_LINK]
    //   <br phname="zoo">                   [ZOO_1]
    //   <br phname="zoo">                   [ZOO_1]
    //   {$zoo phname="zoo"}                 [ZOO_2]
    //   {$zoo}                              [ZOO_3]
    //   {$foo.zoo phname="zoo"}             [ZOO_4]
    //   {$foo.zoo phname="zoo"}             [ZOO_4]
    //   {call .helper phname="zoo" /}       [ZOO_5]
    //   {call .helper phname="zoo" /}       [ZOO_6]
    // {/msg}
    //
    // Note: The three 'print' tags {$foo.goo}, {$goo}, and {$goo2} end up as placeholders GOO_1,
    // GOO_3, and GOO_2 due to the following steps.
    // 1. {$foo.goo} and {$goo} have base placeholder name GOO, while {$goo2} has GOO_2.
    // 2. To differentiate {$foo.goo} and {$goo}, normally the new names would be GOO_1 and GOO_2.
    // 3. However, since GOO_2 is already used for {$goo2}, we use GOO_1 and GOO_3 instead.

    MsgNode msg = new MsgNode(0, "desc=\"\"");
    // Link 1 start tag.
    MsgHtmlTagNode link1Start = new MsgHtmlTagNode(
        1,
        Lists.<StandaloneNode>newArrayList(
            new RawTextNode(0, "<a href=\""),
            new PrintNode(0, true, "$url1", null),
            new RawTextNode(0, "\">")));
    msg.addChild(new MsgPlaceholderNode(0, link1Start));
    // Link 1 contents.
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$boo", null)));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$foo.goo", null)));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "1 + 1", null)));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "2 + 2", null)));
    // Link 1 end tag.
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("</a>")));
    // Intervening 'br' tags.
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("<br>")));
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("<br/>")));
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("<br />")));
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("<br />")));
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("<br>")));
    // Link 2 start tag.
    MsgHtmlTagNode link2Start = new MsgHtmlTagNode(
        2,
        Lists.<StandaloneNode>newArrayList(
            new RawTextNode(0, "<a href=\""),
            new PrintNode(0, true, "$url2", null),
            new RawTextNode(0, "\">")));
    msg.addChild(new MsgPlaceholderNode(0, link2Start));
    // Link 2 contents.
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$boo", null)));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$goo", null)));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$goo2", null)));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "2 + 2", null)));
    // Link 2 end tag.
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("</a>")));
    // All the parts with base placeholder name ZOO.
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("<br phname=\"zoo\">")));
    msg.addChild(new MsgPlaceholderNode(0, createSimpleHtmlTag("<br phname=\"zoo\">")));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$zoo", "zoo")));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$zoo", null)));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$foo.zoo", "zoo")));
    msg.addChild(new MsgPlaceholderNode(0, new PrintNode(0, true, "$foo.zoo", "zoo")));
    msg.addChild(new MsgPlaceholderNode(0, new CallBasicNode(3, ".helper", "zoo")));
    msg.addChild(new MsgPlaceholderNode(0, new CallBasicNode(4, ".helper", "zoo")));

    assertEquals("START_LINK_1", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(0)));
    assertEquals("BOO", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(1)));
    assertEquals("GOO_1", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(2)));
    assertEquals("XXX_1", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(3)));
    assertEquals("XXX_2", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(4)));
    assertEquals("END_LINK", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(5)));
    assertEquals("START_BREAK", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(6)));
    assertEquals("BREAK_1", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(7)));
    assertEquals("BREAK_2", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(8)));
    assertEquals("BREAK_2", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(9)));
    assertEquals("START_BREAK", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(10)));
    assertEquals("START_LINK_2", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(11)));
    assertEquals("BOO", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(12)));
    assertEquals("GOO_3", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(13)));
    assertEquals("GOO_2", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(14)));
    assertEquals("XXX_2", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(15)));
    assertEquals("END_LINK", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(16)));
    assertEquals("ZOO_1", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(17)));
    assertEquals("ZOO_1", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(18)));
    assertEquals("ZOO_2", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(19)));
    assertEquals("ZOO_3", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(20)));
    assertEquals("ZOO_4", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(21)));
    assertEquals("ZOO_4", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(22)));
    assertEquals("ZOO_5", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(23)));
    assertEquals("ZOO_6", msg.getPlaceholderName((MsgPlaceholderNode) msg.getChild(24)));

    assertSame(msg.getChild(0), msg.getRepPlaceholderNode("START_LINK_1"));
    assertSame(msg.getChild(1), msg.getRepPlaceholderNode("BOO"));
    assertSame(msg.getChild(2), msg.getRepPlaceholderNode("GOO_1"));
    assertSame(msg.getChild(3), msg.getRepPlaceholderNode("XXX_1"));
    assertSame(msg.getChild(4), msg.getRepPlaceholderNode("XXX_2"));
    assertSame(msg.getChild(5), msg.getRepPlaceholderNode("END_LINK"));
    assertSame(msg.getChild(6), msg.getRepPlaceholderNode("START_BREAK"));
    assertSame(msg.getChild(7), msg.getRepPlaceholderNode("BREAK_1"));
    assertSame(msg.getChild(8), msg.getRepPlaceholderNode("BREAK_2"));
    assertNotSame(msg.getChild(9), msg.getRepPlaceholderNode("BREAK_2"));
    assertNotSame(msg.getChild(10), msg.getRepPlaceholderNode("START_BREAK"));
    assertSame(msg.getChild(11), msg.getRepPlaceholderNode("START_LINK_2"));
    assertNotSame(msg.getChild(12), msg.getRepPlaceholderNode("BOO"));
    assertSame(msg.getChild(13), msg.getRepPlaceholderNode("GOO_3"));
    assertSame(msg.getChild(14), msg.getRepPlaceholderNode("GOO_2"));
    assertNotSame(msg.getChild(15), msg.getRepPlaceholderNode("XXX_2"));
    assertNotSame(msg.getChild(16), msg.getRepPlaceholderNode("END_LINK"));
    assertSame(msg.getChild(17), msg.getRepPlaceholderNode("ZOO_1"));
    assertNotSame(msg.getChild(18), msg.getRepPlaceholderNode("ZOO_1"));
    assertSame(msg.getChild(19), msg.getRepPlaceholderNode("ZOO_2"));
    assertSame(msg.getChild(20), msg.getRepPlaceholderNode("ZOO_3"));
    assertSame(msg.getChild(21), msg.getRepPlaceholderNode("ZOO_4"));
    assertNotSame(msg.getChild(22), msg.getRepPlaceholderNode("ZOO_4"));
    assertSame(msg.getChild(23), msg.getRepPlaceholderNode("ZOO_5"));
    assertSame(msg.getChild(24), msg.getRepPlaceholderNode("ZOO_6"));
  }


  // -----------------------------------------------------------------------------------------------
  // Plural/select messages.


  /**
   * Tests whether the names for plural and select nodes are assigned correctly.
   * This contains a normal select variable and three fall back plural variables
   * with conflict.
   */
  public static void testGenPlrselVarNames1() {
    /* Tests the soy message in the following code:
    {msg desc=""}
      {select $gender}    // Normal select variable.  GENDER.
        {case 'female'}
          {plural $values.people[0]}  // Plural variable, fall back to NUM_1
            {case 1}{$person} added one person to her circle.
            {default}{$person} added many people to her circle.
          {/plural}
        {case 'male'}
          {plural $values.people[1]}  // Plural variable, fall back to NUM_2
            {case 1}{$person} added one person to his circle.
            {default}{$person} added many people to his circle.
          {/plural}
        {default}
          {plural $values.people[1]}  // Plural variable, fall back to NUM_2
            {case 1}{$person} added one person to his/her circle.
            {default}{$person} added many people to his/her circle.
          {/plural}
      {/select}
    {/msg}
    */

    // Build the message.
    MsgNode msg = new MsgNode(0, "desc=\"\"");
    MsgSelectNode selectNode = new MsgSelectNode(0, "$gender");

    // case 'female'
    MsgSelectCaseNode femaleNode = new MsgSelectCaseNode(0, "'female'");

    MsgPluralNode pluralNode1 = new MsgPluralNode(0, "$values.people[0] offset=\"1\"");

    MsgPluralCaseNode pluralCaseNode11 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode111 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode11.addChild(placeholderNode111);
    RawTextNode rawTextNode111 = new RawTextNode(0, " added one person to her circle.");
    pluralCaseNode11.addChild(rawTextNode111);

    pluralNode1.addChild(pluralCaseNode11);

    MsgPluralDefaultNode pluralDefaultNode12 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode121 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode12.addChild(placeholderNode121);
    RawTextNode rawTextNode121 = new RawTextNode(0, " added many people to her circle.");
    pluralDefaultNode12.addChild(rawTextNode121);

    pluralNode1.addChild(pluralCaseNode11);

    femaleNode.addChild(pluralNode1);

    selectNode.addChild(femaleNode);

    // case 'male'
    MsgSelectCaseNode maleNode = new MsgSelectCaseNode(0, "'male'");

    MsgPluralNode pluralNode2 = new MsgPluralNode(0, "$values.people[1]");

    MsgPluralCaseNode pluralCaseNode21 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode211 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode21.addChild(placeholderNode211);
    RawTextNode rawTextNode211 = new RawTextNode(0, " added one person to his circle.");
    pluralCaseNode21.addChild(rawTextNode211);

    pluralNode2.addChild(pluralCaseNode21);

    MsgPluralDefaultNode pluralDefaultNode22 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode221 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode22.addChild(placeholderNode221);
    RawTextNode rawTextNode221 = new RawTextNode(0, " added many people to his circle.");
    pluralDefaultNode22.addChild(rawTextNode221);

    pluralNode2.addChild(pluralDefaultNode22);

    maleNode.addChild(pluralNode2);

    selectNode.addChild(maleNode);

    // default
    MsgSelectDefaultNode selectDefaultNode = new MsgSelectDefaultNode(0);

    MsgPluralNode pluralNode3 = new MsgPluralNode(0, "$values.people[1]");

    MsgPluralCaseNode pluralCaseNode31 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode311 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode31.addChild(placeholderNode311);
    RawTextNode rawTextNode311 = new RawTextNode(0, " added one person to his/her circle.");
    pluralCaseNode31.addChild(rawTextNode311);

    pluralNode3.addChild(pluralCaseNode31);

    MsgPluralDefaultNode pluralDefaultNode32 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode321 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode32.addChild(placeholderNode321);
    RawTextNode rawTextNode321 = new RawTextNode(0, " added many people to his/her circle.");
    pluralDefaultNode32.addChild(rawTextNode321);

    pluralNode3.addChild(pluralDefaultNode32);

    selectDefaultNode.addChild(pluralNode3);

    selectNode.addChild(selectDefaultNode);

    msg.addChild(selectNode);
    
    // msg.printMessageNode();

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("GENDER", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("GENDER"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) (
        (MsgSelectCaseNode) nodeSelect.getChild(0)).getChild(0);
    assertEquals("NUM_1", msg.getPluralVarName(nodePlural1));
    
    MsgPluralNode nodePlural2 = (MsgPluralNode) (
        (MsgSelectCaseNode) nodeSelect.getChild(1)).getChild(0);
    assertEquals("NUM_2", msg.getPluralVarName(nodePlural2));
    
    MsgPluralNode nodePlural3 = (MsgPluralNode) (
        (MsgSelectDefaultNode) nodeSelect.getChild(2)).getChild(0);
    assertEquals("NUM_2", msg.getPluralVarName(nodePlural3));
    assertNotSame(nodePlural2, nodePlural3);

    MsgPluralNode repPluralNode1 = msg.getRepPluralNode("NUM_1");
    assertSame(repPluralNode1, nodePlural1);
    MsgPluralNode repPluralNode2 = msg.getRepPluralNode("NUM_2");
    assertSame(repPluralNode2, nodePlural2);
    assertNotSame(repPluralNode2, nodePlural3);
  }


  /**
   * Tests whether the names for plural and select nodes are assigned correctly.
   */
  public static void testGenPlrselVarNames2() {
    /* Tests the soy message in the following code:
    {msg desc=""}
      {select $gender[5]}    // Select variable, fall back to STATUS.
        {case 'female'}
          {plural $woman.num_friends}  // Plural variable, NUM_FRIENDS_1
            {case 1}{$person} added one person to her circle.
            {default}{$person} added many people to her circle.
          {/plural}
        {case 'male'}
          {plural $man.num_friends}  // Plural variable, NUM_FRIENDS_2
            {case 1}{$person} added one person to his circle.
            {default}{$person} added many people to his circle.
          {/plural}
        {default}
          {plural $thing.nEntities}  // Plural variable, N_ENTITIES
            {case 1}{$object} added one entity to its circle.
            {default}{$object} added many entities to its circle.
          {/plural}
      {/select}
    {/msg}
    */

    // Build the message.
    MsgNode msg = new MsgNode(0, "desc=\"\"");
    MsgSelectNode selectNode = new MsgSelectNode(0, "$gender[5]");

    // case 'female'
    MsgSelectCaseNode femaleNode = new MsgSelectCaseNode(0, "'female'");

    MsgPluralNode pluralNode1 = new MsgPluralNode(0, "$woman.num_friends");

    MsgPluralCaseNode pluralCaseNode11 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode111 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode11.addChild(placeholderNode111);
    RawTextNode rawTextNode111 = new RawTextNode(0, " added one person to her circle.");
    pluralCaseNode11.addChild(rawTextNode111);

    pluralNode1.addChild(pluralCaseNode11);

    MsgPluralDefaultNode pluralDefaultNode12 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode121 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode12.addChild(placeholderNode121);
    RawTextNode rawTextNode121 = new RawTextNode(0, " added many people to her circle.");
    pluralDefaultNode12.addChild(rawTextNode121);

    pluralNode1.addChild(pluralCaseNode11);

    femaleNode.addChild(pluralNode1);

    selectNode.addChild(femaleNode);

    // case 'male'
    MsgSelectCaseNode maleNode = new MsgSelectCaseNode(0, "'male'");

    MsgPluralNode pluralNode2 = new MsgPluralNode(0, "$man.num_friends");

    MsgPluralCaseNode pluralCaseNode21 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode211 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode21.addChild(placeholderNode211);
    RawTextNode rawTextNode211 = new RawTextNode(0, " added one person to his circle.");
    pluralCaseNode21.addChild(rawTextNode211);

    pluralNode2.addChild(pluralCaseNode21);

    MsgPluralDefaultNode pluralDefaultNode22 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode221 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode22.addChild(placeholderNode221);
    RawTextNode rawTextNode221 = new RawTextNode(0, " added many people to his circle.");
    pluralDefaultNode22.addChild(rawTextNode221);

    pluralNode2.addChild(pluralDefaultNode22);

    maleNode.addChild(pluralNode2);

    selectNode.addChild(maleNode);

    // case 'other'
    MsgSelectDefaultNode selectDefaultNode = new MsgSelectDefaultNode(0);

    MsgPluralNode pluralNode3 = new MsgPluralNode(0, "$thing.nEntities");

    MsgPluralCaseNode pluralCaseNode31 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode311 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode31.addChild(placeholderNode311);
    RawTextNode rawTextNode311 = new RawTextNode(0, " added one person to his/her circle.");
    pluralCaseNode31.addChild(rawTextNode311);

    pluralNode3.addChild(pluralCaseNode31);

    MsgPluralDefaultNode pluralDefaultNode32 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode321 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode32.addChild(placeholderNode321);
    RawTextNode rawTextNode321 = new RawTextNode(0, " added many people to his/her circle.");
    pluralDefaultNode32.addChild(rawTextNode321);

    pluralNode3.addChild(pluralDefaultNode32);

    selectDefaultNode.addChild(pluralNode3);

    selectNode.addChild(selectDefaultNode);

    msg.addChild(selectNode);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("STATUS", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("STATUS"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) (
        (MsgSelectCaseNode) nodeSelect.getChild(0)).getChild(0);
    assertEquals("NUM_FRIENDS_1", msg.getPluralVarName(nodePlural1));
    MsgPluralNode nodePlural2 = (MsgPluralNode) (
        (MsgSelectCaseNode) nodeSelect.getChild(1)).getChild(0);
    assertEquals("NUM_FRIENDS_2", msg.getPluralVarName(nodePlural2));
    MsgPluralNode nodePlural3 = (MsgPluralNode) (
        (MsgSelectDefaultNode) nodeSelect.getChild(2)).getChild(0);
    assertEquals("N_ENTITIES", msg.getPluralVarName(nodePlural3));
    assertNotSame(nodePlural2, nodePlural3);

    MsgPluralNode repPluralNode1 = msg.getRepPluralNode("NUM_FRIENDS_1");
    assertSame(repPluralNode1, nodePlural1);
    MsgPluralNode repPluralNode2 = msg.getRepPluralNode("NUM_FRIENDS_2");
    assertSame(repPluralNode2, nodePlural2);
    MsgPluralNode repPluralNode3 = msg.getRepPluralNode("N_ENTITIES");
    assertSame(repPluralNode3, nodePlural3);
  }


  public static void testGenPlrselVarNames3() {
    /* Tests the soy message in the following code:
    {msg desc=""}
      {select $gender.person}    // Select variable, fall back to PERSON_1.
        {case 'female'}
          {plural $woman.num_friends.person}  // Plural variable, PERSON_3.
            {case 1}{$person} added one person to her circle.  // PERSON_5.
            {default}{$person2} added many people to her circle.  // PERSON_2.
          {/plural}
        {case default}
          {plural $man.num_friends.person}  // Plural variable, PERSON_4.
            {case 1}{$person} added one person to his circle.   // PERSON_5.
            {default}{$person2} added many people to his circle.  // PERSON_2.
          {/plural}
      {/select}
    {/msg}
    */

    // Build the message.
    MsgNode msg = new MsgNode(0, "desc=\"\"");
    MsgSelectNode selectNode = new MsgSelectNode(0, "$gender.person");

    // case 'female'
    MsgSelectCaseNode femaleNode = new MsgSelectCaseNode(0, "'female'");

    MsgPluralNode pluralNode1 = new MsgPluralNode(0, "$woman.num_friends.person");

    MsgPluralCaseNode pluralCaseNode11 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode111 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode11.addChild(placeholderNode111);
    RawTextNode rawTextNode111 = new RawTextNode(0, " added one person to her circle.");
    pluralCaseNode11.addChild(rawTextNode111);

    pluralNode1.addChild(pluralCaseNode11);

    MsgPluralDefaultNode pluralDefaultNode12 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode121 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person2", null));
    pluralDefaultNode12.addChild(placeholderNode121);
    RawTextNode rawTextNode121 = new RawTextNode(0, " added many people to her circle.");
    pluralDefaultNode12.addChild(rawTextNode121);

    pluralNode1.addChild(pluralDefaultNode12);

    femaleNode.addChild(pluralNode1);

    selectNode.addChild(femaleNode);

    // case 'other'
    MsgSelectDefaultNode selectDefaultNode = new MsgSelectDefaultNode(0);

    MsgPluralNode pluralNode3 = new MsgPluralNode(0, "$man.num_friends.person");

    MsgPluralCaseNode pluralCaseNode31 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode311 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode31.addChild(placeholderNode311);
    RawTextNode rawTextNode311 = new RawTextNode(0, " added one person to his/her circle.");
    pluralCaseNode31.addChild(rawTextNode311);

    pluralNode3.addChild(pluralCaseNode31);

    MsgPluralDefaultNode pluralDefaultNode32 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode321 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person2", null));
    pluralDefaultNode32.addChild(placeholderNode321);
    RawTextNode rawTextNode321 = new RawTextNode(0, " added many people to his/her circle.");
    pluralDefaultNode32.addChild(rawTextNode321);

    pluralNode3.addChild(pluralDefaultNode32);

    selectDefaultNode.addChild(pluralNode3);

    selectNode.addChild(selectDefaultNode);

    msg.addChild(selectNode);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("PERSON_1", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("PERSON_1"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) (
        (MsgSelectCaseNode) nodeSelect.getChild(0)).getChild(0);
    assertEquals("PERSON_3", msg.getPluralVarName(nodePlural1));
    MsgPlaceholderNode phNode11 = (MsgPlaceholderNode) (
        (MsgPluralCaseNode) nodePlural1.getChild(0)).getChild(0);
    assertEquals("PERSON_5", msg.getPlaceholderName(phNode11));
    MsgPlaceholderNode phNode12 = (MsgPlaceholderNode) (
        (MsgPluralDefaultNode) nodePlural1.getChild(1)).getChild(0);
    assertEquals("PERSON_2", msg.getPlaceholderName(phNode12));

    MsgPluralNode nodePlural2 = (MsgPluralNode) (
        (MsgSelectDefaultNode) nodeSelect.getChild(1)).getChild(0);
    assertEquals("PERSON_4", msg.getPluralVarName(nodePlural2));
    MsgPlaceholderNode phNode21 = (MsgPlaceholderNode) (
        (MsgPluralCaseNode) nodePlural2.getChild(0)).getChild(0);
    assertEquals("PERSON_5", msg.getPlaceholderName(phNode21));
    MsgPlaceholderNode phNode22 = (MsgPlaceholderNode) (
        (MsgPluralDefaultNode) nodePlural2.getChild(1)).getChild(0);
    assertEquals("PERSON_2", msg.getPlaceholderName(phNode22));


    MsgPlaceholderNode repPhNode1 = msg.getRepPlaceholderNode("PERSON_5");
    assertSame(repPhNode1, phNode11);
    MsgPlaceholderNode repPhNode2 = msg.getRepPlaceholderNode("PERSON_2");
    assertSame(repPhNode2, phNode12);
    MsgSelectNode repSelectNode = msg.getRepSelectNode("PERSON_1");
    assertSame(repSelectNode, nodeSelect);
    MsgPluralNode repPluralNode1 = msg.getRepPluralNode("PERSON_3");
    assertSame(repPluralNode1, nodePlural1);
    MsgPluralNode repPluralNode2 = msg.getRepPluralNode("PERSON_4");
    assertSame(repPluralNode2, nodePlural2);
 }


  /**
   * Tests arbitrary expression as plural variable.
   */
  public static void testGenPlrselVarNames4() {
    /* Tests the soy message in the following code:
    {msg desc=""}
      {select $gender}    // Select variable, fall back to GENDER.
        {case 'female'}
          {plural $woman.num}  // Plural variable, NUM_1
            {case 1}{$person} added one person to her circle.
            {default}{$person} added many people to her circle.
          {/plural}
        {case 'male'}
          {plural $man.num}  // Plural variable, NUM_2
            {case 1}{$person} added one person to his circle.
            {default}{$person} added many people to his circle.
          {/plural}
        {default}
          {plural {max($woman.num_friends, $man.num_friends)}}  // Plural variable, NUM_3
            {case 1}{$object} added one person to his/her circle.
            {default}{$object} added many people to his/her circle.
          {/plural}
      {/select}
    {/msg}
    */

    // Build the message.
    MsgNode msg = new MsgNode(0, "desc=\"\"");
    MsgSelectNode selectNode = new MsgSelectNode(0, "$gender");

    // case 'female'
    MsgSelectCaseNode femaleNode = new MsgSelectCaseNode(0, "'female'");

    MsgPluralNode pluralNode1 = new MsgPluralNode(0, "$woman.num");

    MsgPluralCaseNode pluralCaseNode11 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode111 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode11.addChild(placeholderNode111);
    RawTextNode rawTextNode111 = new RawTextNode(0, " added one person to her circle.");
    pluralCaseNode11.addChild(rawTextNode111);

    pluralNode1.addChild(pluralCaseNode11);

    MsgPluralDefaultNode pluralDefaultNode12 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode121 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode12.addChild(placeholderNode121);
    RawTextNode rawTextNode121 = new RawTextNode(0, " added many people to her circle.");
    pluralDefaultNode12.addChild(rawTextNode121);

    pluralNode1.addChild(pluralCaseNode11);

    femaleNode.addChild(pluralNode1);

    selectNode.addChild(femaleNode);

    // case 'male'
    MsgSelectCaseNode maleNode = new MsgSelectCaseNode(0, "'male'");

    MsgPluralNode pluralNode2 = new MsgPluralNode(0, "$man.num");

    MsgPluralCaseNode pluralCaseNode21 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode211 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode21.addChild(placeholderNode211);
    RawTextNode rawTextNode211 = new RawTextNode(0, " added one person to his circle.");
    pluralCaseNode21.addChild(rawTextNode211);

    pluralNode2.addChild(pluralCaseNode21);

    MsgPluralDefaultNode pluralDefaultNode22 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode221 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode22.addChild(placeholderNode221);
    RawTextNode rawTextNode221 = new RawTextNode(0, " added many people to his circle.");
    pluralDefaultNode22.addChild(rawTextNode221);

    pluralNode2.addChild(pluralDefaultNode22);

    maleNode.addChild(pluralNode2);

    selectNode.addChild(maleNode);

    // case 'other'
    MsgSelectDefaultNode selectDefaultNode = new MsgSelectDefaultNode(0);

    MsgPluralNode pluralNode3 = new MsgPluralNode(0, "max($woman.num, $man.num)");

    MsgPluralCaseNode pluralCaseNode31 = new MsgPluralCaseNode(0, "1");
    MsgPlaceholderNode placeholderNode311 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralCaseNode31.addChild(placeholderNode311);
    RawTextNode rawTextNode311 = new RawTextNode(0, " added one person to his/her circle.");
    pluralCaseNode31.addChild(rawTextNode311);

    pluralNode3.addChild(pluralCaseNode31);

    MsgPluralDefaultNode pluralDefaultNode32 = new MsgPluralDefaultNode(0);
    MsgPlaceholderNode placeholderNode321 =
        new MsgPlaceholderNode(0, new PrintNode(0, false, "$person", null));
    pluralDefaultNode32.addChild(placeholderNode321);
    RawTextNode rawTextNode321 = new RawTextNode(0, " added many people to his/her circle.");
    pluralDefaultNode32.addChild(rawTextNode321);

    pluralNode3.addChild(pluralDefaultNode32);

    selectDefaultNode.addChild(pluralNode3);

    selectNode.addChild(selectDefaultNode);

    msg.addChild(selectNode);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("GENDER", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("GENDER"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) (
        (MsgSelectCaseNode) nodeSelect.getChild(0)).getChild(0);
    assertEquals("NUM_1", msg.getPluralVarName(nodePlural1));
    MsgPluralNode nodePlural2 = (MsgPluralNode) (
        (MsgSelectCaseNode) nodeSelect.getChild(1)).getChild(0);
    assertEquals("NUM_2", msg.getPluralVarName(nodePlural2));
    MsgPluralNode nodePlural3 = (MsgPluralNode) (
        (MsgSelectDefaultNode) nodeSelect.getChild(2)).getChild(0);
    assertEquals("NUM_3", msg.getPluralVarName(nodePlural3));
    assertNotSame(nodePlural2, nodePlural3);

    MsgPluralNode repPluralNode1 = msg.getRepPluralNode("NUM_1");
    assertSame(repPluralNode1, nodePlural1);
    MsgPluralNode repPluralNode2 = msg.getRepPluralNode("NUM_2");
    assertSame(repPluralNode2, nodePlural2);
    MsgPluralNode repPluralNode3 = msg.getRepPluralNode("NUM_3");
    assertSame(repPluralNode3, nodePlural3);
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private static MsgHtmlTagNode createSimpleHtmlTag(String content) throws Exception {
    return new MsgHtmlTagNode(0, Lists.<StandaloneNode>newArrayList(new RawTextNode(0, content)));
  }

}
