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

import static com.google.template.soy.soytree.SoyTreeUtils.getAllNodesOfType;
import static com.google.template.soy.soytree.TemplateSubject.assertThatTemplateContent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for MsgNode.
 *
 */
@RunWith(JUnit4.class)
public class MsgNodeTest {

  private static void assertSubstUnitInfo(MsgNode msg) {
    assertThrows(
        IllegalStateException.class,
        () -> {
          msg.ensureSubstUnitInfoHasNotBeenAccessed();
        });
    assertNotNull(msg.getSubstUnitInfo());
  }

  @Test
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
    //   <br phname="ZOO_1">                 [ZOO_1]
    //   <br phname="ZOO_1">                 [ZOO_1]
    //   {$zoo phname="ZOO_2"}               [ZOO_2]
    //   {$zoo}                              [ZOO]
    //   {$foo.zoo phname="ZOO_4"}           [ZOO_4]
    //   {$foo.zoo phname="ZOO_4"}           [ZOO_4]
    //   {call .helper phname="ZOO_5" /}     [ZOO_5]
    //   {call .helper phname="ZOO_6" /}     [ZOO_6]
    // {/msg}
    //
    // Note: The three 'print' tags {$foo.goo}, {$goo}, and {$goo2} end up as placeholders GOO_1,
    // GOO_3, and GOO_2 due to the following steps.
    // 1. {$foo.goo} and {$goo} have base placeholder name GOO, while {$goo2} has GOO_2.
    // 2. To differentiate {$foo.goo} and {$goo}, normally the new names would be GOO_1 and GOO_2.
    // 3. However, since GOO_2 is already used for {$goo2}, we use GOO_1 and GOO_3 instead.

    String template =
        "{@param url1 : ?}"
            + "{@param boo : ?}"
            + "{@param foo : ?}"
            + "{@param url2 : ?}"
            + "{@param goo : ?}\n"
            + "{@param goo2 : ?}\n"
            + "{@param zoo : ?}\n"
            + "{msg desc=\"\"}\n"
            + "  <a href=\"{$url1}\">\n"
            + "    {$boo}{$foo.goo}{1 + 1}{2 + 2}\n"
            + "  </a>\n"
            + "  <br><br/><br /><br /><br>\n"
            + "  <a href=\"{$url2}\">\n"
            + "    {$boo}{$goo}{$goo2}{2 + 2}\n"
            + "  </a>\n"
            + "  <br phname=\"ZOO_1\">\n"
            + "  <br phname=\"ZOO_1\">\n"
            + "  {$zoo phname=\"ZOO_2\"}\n"
            + "  {$zoo}\n"
            + "  {$foo.zoo phname=\"ZOO_4\"}\n"
            + "  {$foo.zoo phname=\"ZOO_4\"}\n"
            + "  {call .helper phname=\"ZOO_5\" /}\n"
            + "  {call .helper phname=\"ZOO_6\" /}\n"
            + "{/msg}";
    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    List<MsgPlaceholderNode> placeholders = getAllNodesOfType(msg, MsgPlaceholderNode.class);

    assertEquals("START_LINK_1", msg.getPlaceholder(placeholders.get(0)).name());
    assertEquals("BOO", msg.getPlaceholder(placeholders.get(1)).name());
    assertEquals("GOO_1", msg.getPlaceholder(placeholders.get(2)).name());
    assertEquals("XXX_1", msg.getPlaceholder(placeholders.get(3)).name());
    assertEquals("XXX_2", msg.getPlaceholder(placeholders.get(4)).name());
    assertEquals("END_LINK", msg.getPlaceholder(placeholders.get(5)).name());
    assertEquals("START_BREAK", msg.getPlaceholder(placeholders.get(6)).name());
    assertEquals("BREAK", msg.getPlaceholder(placeholders.get(7)).name());
    assertEquals("BREAK", msg.getPlaceholder(placeholders.get(8)).name());
    assertEquals("BREAK", msg.getPlaceholder(placeholders.get(9)).name());
    assertEquals("START_BREAK", msg.getPlaceholder(placeholders.get(10)).name());
    assertEquals("START_LINK_2", msg.getPlaceholder(placeholders.get(11)).name());
    assertEquals("BOO", msg.getPlaceholder(placeholders.get(12)).name());
    assertEquals("GOO_3", msg.getPlaceholder(placeholders.get(13)).name());
    assertEquals("GOO_2", msg.getPlaceholder(placeholders.get(14)).name());
    assertEquals("XXX_2", msg.getPlaceholder(placeholders.get(15)).name());
    assertEquals("END_LINK", msg.getPlaceholder(placeholders.get(16)).name());
    assertEquals("ZOO_1", msg.getPlaceholder(placeholders.get(17)).name());
    assertEquals("ZOO_1", msg.getPlaceholder(placeholders.get(18)).name());
    assertEquals("ZOO_2", msg.getPlaceholder(placeholders.get(19)).name());
    assertEquals("ZOO", msg.getPlaceholder(placeholders.get(20)).name());
    assertEquals("ZOO_4", msg.getPlaceholder(placeholders.get(21)).name());
    assertEquals("ZOO_4", msg.getPlaceholder(placeholders.get(22)).name());
    assertEquals("ZOO_5", msg.getPlaceholder(placeholders.get(23)).name());
    assertEquals("ZOO_6", msg.getPlaceholder(placeholders.get(24)).name());

    assertSame(placeholders.get(0), msg.getRepPlaceholderNode("START_LINK_1"));
    assertSame(placeholders.get(1), msg.getRepPlaceholderNode("BOO"));
    assertSame(placeholders.get(2), msg.getRepPlaceholderNode("GOO_1"));
    assertSame(placeholders.get(3), msg.getRepPlaceholderNode("XXX_1"));
    assertSame(placeholders.get(4), msg.getRepPlaceholderNode("XXX_2"));
    assertSame(placeholders.get(5), msg.getRepPlaceholderNode("END_LINK"));
    assertSame(placeholders.get(6), msg.getRepPlaceholderNode("START_BREAK"));
    assertSame(placeholders.get(7), msg.getRepPlaceholderNode("BREAK"));
    assertNotSame(placeholders.get(10), msg.getRepPlaceholderNode("START_BREAK"));
    assertSame(placeholders.get(11), msg.getRepPlaceholderNode("START_LINK_2"));
    assertNotSame(placeholders.get(12), msg.getRepPlaceholderNode("BOO"));
    assertSame(placeholders.get(13), msg.getRepPlaceholderNode("GOO_3"));
    assertSame(placeholders.get(14), msg.getRepPlaceholderNode("GOO_2"));
    assertNotSame(placeholders.get(15), msg.getRepPlaceholderNode("XXX_2"));
    assertNotSame(placeholders.get(16), msg.getRepPlaceholderNode("END_LINK"));
    assertSame(placeholders.get(17), msg.getRepPlaceholderNode("ZOO_1"));
    assertNotSame(placeholders.get(18), msg.getRepPlaceholderNode("ZOO_1"));
    assertSame(placeholders.get(19), msg.getRepPlaceholderNode("ZOO_2"));
    assertSame(placeholders.get(20), msg.getRepPlaceholderNode("ZOO"));
    assertSame(placeholders.get(21), msg.getRepPlaceholderNode("ZOO_4"));
    assertNotSame(placeholders.get(22), msg.getRepPlaceholderNode("ZOO_4"));
    assertSame(placeholders.get(23), msg.getRepPlaceholderNode("ZOO_5"));
    assertSame(placeholders.get(24), msg.getRepPlaceholderNode("ZOO_6"));
  }

  // -----------------------------------------------------------------------------------------------
  // Plural/select messages.

  /**
   * Tests whether the names for plural and select nodes are assigned correctly. This contains a
   * normal select variable and three fall back plural variables with conflict.
   */
  @Test
  public void testGenPlrselVarNames1() {
    String template =
        "{@param gender : ?}{@param values :?}{@param person : ?}\n"
            + "{msg desc=\"\"}\n"
            + "  {select $gender}\n" // Normal select variable.  GENDER.
            + "    {case 'female'}\n"
            + "      {plural $values.people[0]}\n" // Plural variable, fall back to NUM_1
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$person} added many people to her circle.\n"
            + "      {/plural}\n"
            + "    {case 'male'}\n"
            + "      {plural $values.people[1]}\n" // Plural variable, fall back to NUM_2
            + "        {case 1}{$person} added one person to his circle.\n"
            + "        {default}{$person} added many people to his circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $values.people[1]}\n" // Plural variable, fall back to NUM_2
            + "        {case 1}{$person} added one person to his/her circle.\n"
            + "        {default}{$person} added many people to his/her circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("GENDER", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("GENDER"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) nodeSelect.getChild(0).getChild(0);
    assertEquals("NUM_1", msg.getPluralVarName(nodePlural1));

    MsgPluralNode nodePlural2 = (MsgPluralNode) nodeSelect.getChild(1).getChild(0);
    assertEquals("NUM_2", msg.getPluralVarName(nodePlural2));

    MsgPluralNode nodePlural3 = (MsgPluralNode) nodeSelect.getChild(2).getChild(0);
    assertEquals("NUM_2", msg.getPluralVarName(nodePlural3));
    assertNotSame(nodePlural2, nodePlural3);

    MsgPluralNode repPluralNode1 = msg.getRepPluralNode("NUM_1");
    assertSame(repPluralNode1, nodePlural1);
    MsgPluralNode repPluralNode2 = msg.getRepPluralNode("NUM_2");
    assertSame(repPluralNode2, nodePlural2);
    assertNotSame(repPluralNode2, nodePlural3);
  }

  /** Tests whether the names for plural and select nodes are assigned correctly. */
  @Test
  public void testGenPlrselVarNames2() {
    String template =
        "{@param gender : ?}"
            + "{@param man : ?}"
            + "{@param woman : ?}"
            + "{@param thing : ?}"
            + "{@param person : ?}"
            + "{@param object : ?}\n"
            + "{msg desc=\"\"}\n"
            + "  {select $gender[5]}\n" // Select variable, fall back to STATUS.
            + "    {case 'female'}\n"
            + "      {plural $woman.num_friends}\n" // Plural variable, NUM_FRIENDS_1
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$person} added many people to her circle.\n"
            + "      {/plural}\n"
            + "    {case 'male'}\n"
            + "      {plural $man.num_friends}\n" // Plural variable, NUM_FRIENDS_2
            + "        {case 1}{$person} added one person to his circle.\n"
            + "        {default}{$person} added many people to his circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $thing.nEntities}\n" // Plural variable, N_ENTITIES
            + "        {case 1}{$object} added one entity to its circle.\n"
            + "        {default}{$object} added many entities to its circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("STATUS", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("STATUS"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) nodeSelect.getChild(0).getChild(0);
    assertEquals("NUM_FRIENDS_1", msg.getPluralVarName(nodePlural1));
    MsgPluralNode nodePlural2 = (MsgPluralNode) nodeSelect.getChild(1).getChild(0);
    assertEquals("NUM_FRIENDS_2", msg.getPluralVarName(nodePlural2));
    MsgPluralNode nodePlural3 = (MsgPluralNode) nodeSelect.getChild(2).getChild(0);
    assertEquals("N_ENTITIES", msg.getPluralVarName(nodePlural3));
    assertNotSame(nodePlural2, nodePlural3);

    MsgPluralNode repPluralNode1 = msg.getRepPluralNode("NUM_FRIENDS_1");
    assertSame(repPluralNode1, nodePlural1);
    MsgPluralNode repPluralNode2 = msg.getRepPluralNode("NUM_FRIENDS_2");
    assertSame(repPluralNode2, nodePlural2);
    MsgPluralNode repPluralNode3 = msg.getRepPluralNode("N_ENTITIES");
    assertSame(repPluralNode3, nodePlural3);
  }

  @Test
  public void testGenPlrselVarNames3() {
    String template =
        "{@param gender : ?}"
            + "{@param man : ?}"
            + "{@param woman : ?}"
            + "{@param person : ?}"
            + "{@param person2 : ?}\n"
            + "{msg desc=\"\"}\n"
            + "  {select $gender.person}\n" // Select variable, fall back to PERSON_1.
            + "    {case 'female'}\n"
            + "      {plural $woman.num_friends.person}\n" // Plural variable, PERSON_3.
            + "        {case 1}{$person} added one person to her circle.\n" // PERSON_5.
            + "        {default}{$person2} added many people to her circle.\n" // PERSON_2.
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $man.num_friends.person}\n" // Plural variable, PERSON_4.
            + "        {case 1}{$person} added one person to his circle.\n" // PERSON_5.
            + "        {default}{$person2} added many people to his circle.\n" // PERSON_2.
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("PERSON_1", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("PERSON_1"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) nodeSelect.getChild(0).getChild(0);
    assertEquals("PERSON_3", msg.getPluralVarName(nodePlural1));
    MsgPlaceholderNode phNode11 = (MsgPlaceholderNode) nodePlural1.getChild(0).getChild(0);
    assertEquals("PERSON_5", msg.getPlaceholder(phNode11).name());
    MsgPlaceholderNode phNode12 = (MsgPlaceholderNode) nodePlural1.getChild(1).getChild(0);
    assertEquals("PERSON_2", msg.getPlaceholder(phNode12).name());

    MsgPluralNode nodePlural2 = (MsgPluralNode) nodeSelect.getChild(1).getChild(0);
    assertEquals("PERSON_4", msg.getPluralVarName(nodePlural2));
    MsgPlaceholderNode phNode21 = (MsgPlaceholderNode) nodePlural2.getChild(0).getChild(0);
    assertEquals("PERSON_5", msg.getPlaceholder(phNode21).name());
    MsgPlaceholderNode phNode22 = (MsgPlaceholderNode) nodePlural2.getChild(1).getChild(0);
    assertEquals("PERSON_2", msg.getPlaceholder(phNode22).name());

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

  /** Tests arbitrary expression as plural variable. */
  @Test
  public void testGenPlrselVarNames4() {
    String template =
        "{@param gender : ?}"
            + "{@param man : ?}"
            + "{@param woman : ?}"
            + "{@param person : ?}"
            + "{@param object : ?}\n"
            + "{msg desc=\"\"}\n"
            + "  {select $gender}\n" // Select variable, fall back to GENDER.
            + "    {case 'female'}\n"
            + "      {plural $woman.num}\n" // Plural variable, NUM_1
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$person} added many people to her circle.\n"
            + "      {/plural}\n"
            + "    {case 'male'}\n"
            + "      {plural $man.num}\n" // Plural variable, NUM_2
            + "        {case 1}{$person} added one person to his circle.\n"
            + "        {default}{$person} added many people to his circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural max($woman.num_friends, $man.num_friends)}\n" // Plural variable, NUM_3
            + "        {case 1}{$object} added one person to his/her circle.\n"
            + "        {default}{$object} added many people to his/her circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("GENDER", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("GENDER"));

    MsgPluralNode nodePlural1 = (MsgPluralNode) nodeSelect.getChild(0).getChild(0);
    assertEquals("NUM_1", msg.getPluralVarName(nodePlural1));
    MsgPluralNode nodePlural2 = (MsgPluralNode) nodeSelect.getChild(1).getChild(0);
    assertEquals("NUM_2", msg.getPluralVarName(nodePlural2));
    MsgPluralNode nodePlural3 = (MsgPluralNode) nodeSelect.getChild(2).getChild(0);
    assertEquals("NUM_3", msg.getPluralVarName(nodePlural3));
    assertNotSame(nodePlural2, nodePlural3);

    MsgPluralNode repPluralNode1 = msg.getRepPluralNode("NUM_1");
    assertSame(repPluralNode1, nodePlural1);
    MsgPluralNode repPluralNode2 = msg.getRepPluralNode("NUM_2");
    assertSame(repPluralNode2, nodePlural2);
    MsgPluralNode repPluralNode3 = msg.getRepPluralNode("NUM_3");
    assertSame(repPluralNode3, nodePlural3);
  }

  /**
   * Tests how automatic placeholders work with genders. rewrite genders forks the message into all
   * branches, however placeholders should be shared across the branches.
   */
  @Test
  public void testGenPlaceholdersForGenders() {
    String template =
        "{@param gender : ?}"
            + "{@param person : ?}"
            + "{msg desc=\"\" genders=\"$gender\"}\n"
            + "  {$person} invited you to a group conversation with {call .everyoneElse /}"
            + "{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    MsgSelectNode nodeSelect = (MsgSelectNode) msg.getChild(0);
    assertEquals("GENDER", msg.getSelectVarName(nodeSelect));
    assertSame(nodeSelect, msg.getRepSelectNode("GENDER"));

    CaseOrDefaultNode firstCase = nodeSelect.getChild(0);
    assertEquals(
        MessagePlaceholder.create("PERSON"),
        ((MsgPlaceholderNode) firstCase.getChild(0)).getPlaceholder());
    assertEquals(
        " invited you to a group conversation with ",
        ((RawTextNode) firstCase.getChild(1)).getRawText());
    assertEquals(
        MessagePlaceholder.create("XXX"),
        ((MsgPlaceholderNode) firstCase.getChild(2)).getPlaceholder());
    Set<String> placeholders = new TreeSet<>();
    for (MsgPlaceholderNode placeholder :
        SoyTreeUtils.getAllNodesOfType(msg, MsgPlaceholderNode.class)) {
      placeholders.add(msg.getPlaceholder(placeholder).name());
    }
    // These are the only placeholders generated
    assertEquals(ImmutableSet.of("PERSON", "XXX"), placeholders);
    assertSame(firstCase.getChild(0), msg.getRepPlaceholderNode("PERSON"));
    assertSame(firstCase.getChild(2), msg.getRepPlaceholderNode("XXX"));
  }

  @Test
  public void testIsSelectMsg() {
    String template =
        "{@param gender : ?}"
            + "{msg desc=\"\"}\n"
            + "  {select $gender.person}\n" // Select variable, fall back to PERSON_1.
            + "    {case 'female'}\n"
            + "      'female'\n"
            + "    {default}\n"
            + "      'default'\n"
            + "  {/select}\n"
            + "{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    assertFalse(msg.isPluralMsg());
    assertTrue(msg.isSelectMsg());
    assertTrue(msg.isPlrselMsg());
  }

  @Test
  public void testIsPluralMsg() {
    String template =
        "{@param woman : ?}"
            + "{@param person : ?}"
            + "{msg desc=\"\"}\n"
            + "  {plural $woman.num}\n" // Plural variable, NUM_1
            + "    {case 1}{$person} added one person to her circle.\n"
            + "    {default}{$person} added many people to her circle.\n"
            + "  {/plural}\n"
            + "{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    assertTrue(msg.isPluralMsg());
    assertFalse(msg.isSelectMsg());
    assertTrue(msg.isPlrselMsg());
  }

  @Test
  public void testIsRawTextMsg() {
    String template = "{msg desc=\"\"}raw text{/msg}";

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    MsgNode msg = getAllNodesOfType(templateNode, MsgFallbackGroupNode.class).get(0).getMsg();
    assertSubstUnitInfo(msg);

    // Test.
    assertTrue(msg.isRawTextMsg());
    assertFalse(msg.isPlrselMsg());
  }

  @Test
  public void testWrongNumberOfGenderExprs() {
    assertThatTemplateContent("{msg desc=\"\" genders=\"$foo, $bar, $baz, $quux\"}{/msg}")
        .causesError("Attribute 'genders' should contain 1-3 expressions.")
        .at(1, 23);
  }
}
