/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.pysrc.internal;

import static com.google.template.soy.pysrc.internal.SoyExprForPySubject.assertThatSoyExpr;

import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for GenPyExprsVisitor.
 */
@RunWith(JUnit4.class)
public final class GenPyExprsVisitorTest {

  @Test
  public void testRawText() {
    assertThatSoyExpr("I'm feeling lucky!")
        .compilesTo(new PyExpr("'I\\'m feeling lucky!'", Integer.MAX_VALUE));
  }

  @Test
  public void testCss() {
    assertThatSoyExpr("{css('primary')}")
        .compilesTo(
            new PyExpr("sanitize.escape_html(runtime.get_css_name('primary'))", Integer.MAX_VALUE));

    assertThatSoyExpr("{@param foo:?}\n{css($foo, 'bar')}")
        .compilesTo(
            new PyExpr(
                "sanitize.escape_html(runtime.get_css_name(data.get('foo'), 'bar'))",
                Integer.MAX_VALUE));
  }

  @Test
  public void testXid() {
    assertThatSoyExpr("{xid('primary')}")
        .compilesTo(
            new PyExpr("sanitize.escape_html(runtime.get_xid_name('primary'))", Integer.MAX_VALUE));
  }

  @Test
  public void testIf() {
    String soyNodeCode =
        "{@param boo:?}\n"
            + "{@param goo:?}\n"
            + "{if $boo}\n"
            + "  Blah\n"
            + "{elseif not $goo}\n"
            + "  Bleh\n"
            + "{else}\n"
            + "  Bluh\n"
            + "{/if}\n";
    String expectedPyExprText =
        "('Blah') if (data.get('boo')) else (('Bleh') if (not data.get('goo')) else ('Bluh'))";

    assertThatSoyExpr(soyNodeCode)
        .compilesTo(
            new PyExpr(
                expectedPyExprText, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testIf_noelse() {
    String soyNodeCode =
        "{@param boo:?}\n"
            + "{@param goo:?}\n"
            + "{@param woo:?}\n"
            + "{if $boo}\n"
            + "  Blah\n"
            + "{elseif not $goo}\n"
            + "  Bleh\n"
            + "{elseif $woo}\n"
            + "  Bluh\n"
            + "{/if}\n";
    String expectedPyExprText =
        "('Blah') if (data.get('boo')) else "
            + "(('Bleh') if (not data.get('goo')) else (('Bluh') if (data.get('woo')) else ('')))";

    assertThatSoyExpr(soyNodeCode)
        .compilesTo(
            new PyExpr(
                expectedPyExprText, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testIf_nested() {
    String soyNodeCode =
        "{@param boo:?}\n"
            + "{@param goo:?}\n"
            + "{if $boo}\n"
            + "  {if $goo}\n"
            + "    Blah\n"
            + "  {/if}\n"
            + "{else}\n"
            + "  Bleh\n"
            + "{/if}\n";
    String expectedPyExprText =
        "(('Blah') if (data.get('goo')) else ('')) if (data.get('boo')) else ('Bleh')";

    assertThatSoyExpr(soyNodeCode)
        .compilesTo(
            new PyExpr(
                expectedPyExprText, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testIf_double_nested() {
    String soyNodeCode =
        "{@param boo:?}\n"
            + "{@param foo:?}\n"
            + "{if $boo}\n"
            + "  {if $foo}\n"
            + "    bf\n"
            + "  {else}\n"
            + "    b\n"
            + "  {/if}\n"
            + "{else}\n"
            + "  {if $foo}\n"
            + "    f\n"
            + "  {else}\n"
            + "    \n"
            + "  {/if}\n"
            + "{/if}\n";
    String expectedPyExprText =
        "(('bf') if (data.get('foo')) else ('b'))"
            + " if (data.get('boo')) else "
            + "(('f') if (data.get('foo')) else (''))";

    assertThatSoyExpr(soyNodeCode)
        .compilesTo(
            new PyExpr(
                expectedPyExprText, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testSimpleMsgFallbackGroupNodeWithOneNode() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n" + "  Archive\n" + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render_literal("
            + "translator_impl.prepare_literal("
            + "###, "
            + "'Archive'), True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgFallbackGroupNodeWithTwoNodes() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
            + "  archive\n"
            + "{fallbackmsg desc=\"\"}\n"
            + "  ARCHIVE\n"
            + "{/msg}\n";

    String expectedPyCode =
        "(translator_impl.render_literal("
            + "translator_impl.prepare_literal("
            + "###, "
            + "'archive'), True)) "
            + "if (translator_impl.is_msg_available(###) or "
            + "not translator_impl.is_msg_available(###)) "
            + "else (translator_impl.render_literal("
            + "translator_impl.prepare_literal(###, 'ARCHIVE'), True))";

    assertThatSoyExpr(soyCode)
        .compilesTo(
            new PyExpr(expectedPyCode, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testMsgOnlyLiteral() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"The word 'Archive' used as a verb.\"}"
            + "Archive"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render_literal("
            + "translator_impl.prepare_literal("
            + "###, "
            + "'Archive'), True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgOnlyLiteralWithBraces() {
    // Should escape '{' and '}' in format string.
    // @see https://docs.python.org/2/library/string.html#formatstrings

    String soyCode =
        "{msg meaning=\"verb\" desc=\"The word 'Archive' used as a verb.\"}"
            + "{lb}Archive{rb}"
            + "{/msg}\n";
    String expectedPyCode =
        "translator_impl.render_literal("
            + "translator_impl.prepare_literal("
            + "###, "
            + "'{{Archive}}'), True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgOnlyLiteralWithApostrophe() {
    // Should escape '\'' in format string.

    String soyCode =
        "{msg meaning=\"verb\" desc=\"The word 'Archive' used as a verb.\"}"
            + "Archive's"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render_literal("
            + "translator_impl.prepare_literal("
            + "###, "
            + "'Archive\\'s'), True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgSimpleSoyExpression() {
    String soyCode =
        "{@param username:?}\n"
            + "{msg desc=\"var placeholder\"}"
            + "Hello {$username}"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render("
            + "translator_impl.prepare("
            + "###, "
            + "'Hello {USERNAME}', "
            + "('USERNAME',)), "
            + "{'USERNAME': str(sanitize.escape_html(data.get('username')))}, True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgMultipleSoyExpressions() {
    String soyCode =
        "{@param greet:?}\n"
            + "{@param username:?}\n"
            + "{msg desc=\"var placeholder\"}"
            + "{$greet} {$username}"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render("
            + "translator_impl.prepare("
            + "###, "
            + "'{GREET} {USERNAME}', "
            + "('GREET', 'USERNAME')), "
            + "{"
            + "'GREET': str(sanitize.escape_html(data.get('greet'))), "
            + "'USERNAME': str(sanitize.escape_html(data.get('username')))"
            + "}, True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgMultipleSoyExpressionsWithBraces() {
    String soyCode =
        "{@param username:?}\n"
            + "{@param greet:?}\n"
            + "{msg desc=\"var placeholder\"}"
            + "{$greet} {lb}{$username}{rb}"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render("
            + "translator_impl.prepare("
            + "###, "
            + "'{GREET} {{{USERNAME}}}', "
            + "('GREET', 'USERNAME')), "
            + "{"
            + "'GREET': str(sanitize.escape_html(data.get('greet'))), "
            + "'USERNAME': str(sanitize.escape_html(data.get('username')))"
            + "}, True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgNamespacedSoyExpression() {
    String soyCode =
        "{@param foo:?}\n"
            + "{msg desc=\"placeholder with namespace\"}"
            + "Hello {$foo.bar}"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render("
            + "translator_impl.prepare("
            + "###, "
            + "'Hello {BAR}', "
            + "('BAR',)), "
            + "{'BAR': str(sanitize.escape_html("
            + "runtime.get_field_through_introspection(data.get('foo'), 'bar')"
            + "))}, True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgWithArithmeticExpression() {
    String soyCode =
        "{@param username:?}\n"
            + "{msg desc=\"var placeholder\"}"
            + "Hello {$username + 1}"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render("
            + "translator_impl.prepare("
            + "###, "
            + "'Hello {XXX}', "
            + "('XXX',)), "
            + "{'XXX': str(sanitize.escape_html(runtime.type_safe_add(data.get('username'), 1)))}, "
            + "True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgWithHtmlNode() {
    // msg with HTML tags and raw texts
    String soyCode =
        "{@param url:?}\n"
            + "{msg desc=\"with link\"}"
            + "Please click <a href='{$url}'>here</a>."
            + "{/msg}";

    String expectedPyCode =
        "translator_impl.render("
            + "translator_impl.prepare("
            + "###, "
            + "'Please click {START_LINK}here{END_LINK}.', "
            + "('START_LINK', 'END_LINK')), "
            + "{"
            + "'START_LINK': ''.join(['<a href=\\'',str(sanitize.escape_html_attribute("
            + "sanitize.filter_normalize_uri(data.get('url')))),'\\'>']), "
            + "'END_LINK': '</a>'"
            + "}, True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgWithPlural() {
    String soyCode =
        "{@param numDrafts:?}\n"
            + "{msg desc=\"simple plural\"}"
            + "{plural $numDrafts}"
            + "{case 0}No drafts"
            + "{case 1}1 draft"
            + "{default}{$numDrafts} drafts"
            + "{/plural}"
            + "{/msg}";

    String expectedPyCode =
        "translator_impl.render_plural("
            + "translator_impl.prepare_plural("
            + "###, "
            + "{"
            + "'=0': 'No drafts', "
            + "'=1': '1 draft', "
            + "'other': '{NUM_DRAFTS_2} drafts'"
            + "}, "
            + "('NUM_DRAFTS_1', 'NUM_DRAFTS_2')), "
            + "data.get('numDrafts'), "
            + "{"
            + "'NUM_DRAFTS_1': data.get('numDrafts'), "
            + "'NUM_DRAFTS_2': str(sanitize.escape_html(data.get('numDrafts')))"
            + "}, True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgWithPluralAndOffset() {
    String soyCode =
        "{@param numDrafts:?}\n"
            + "{msg desc=\"offset plural\"}"
            + "{plural $numDrafts offset=\"2\"}"
            + "{case 0}No drafts"
            + "{case 1}1 draft"
            + "{default}{remainder($numDrafts)} drafts"
            + "{/plural}"
            + "{/msg}";

    String expectedPyCode =
        "translator_impl.render_plural("
            + "translator_impl.prepare_plural("
            + "###, "
            + "{"
            + "'=0': 'No drafts', "
            + "'=1': '1 draft', "
            + "'other': '{XXX} drafts'"
            + "}, "
            + "('NUM_DRAFTS', 'XXX')), "
            + "data.get('numDrafts'), "
            + "{"
            + "'NUM_DRAFTS': data.get('numDrafts'), "
            + "'XXX': str(sanitize.escape_html(data.get('numDrafts') - 2))"
            + "}, True)";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgWithSelect() {
    String soyCode =
        "{@param userGender:?}\n"
            + "{@param targetGender:?}\n"
            + "{msg desc=\"...\"}\n"
            + "  {select $userGender}\n"
            + "    {case 'female'}\n"
            + "      {select $targetGender}\n"
            + "        {case 'female'}Reply to her.\n"
            + "        {case 'male'}Reply to him.\n"
            + "        {default}Reply to them.\n"
            + "      {/select}\n"
            + "    {case 'male'}\n"
            + "      {select $targetGender}\n"
            + "        {case 'female'}Reply to her.\n"
            + "        {case 'male'}Reply to him.\n"
            + "        {default}Reply to them.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $targetGender}\n"
            + "        {case 'female'}Reply to her.\n"
            + "        {case 'male'}Reply to him.\n"
            + "        {default}Reply to them.\n"
            + "      {/select}\n"
            + "   {/select}\n"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render_icu("
            + "translator_impl.prepare_icu("
            + "###, "
            + "'{USER_GENDER,select,"
            + "female{"
            + "{TARGET_GENDER,select,"
            + "female{Reply to her.}"
            + "male{Reply to him.}"
            + "other{Reply to them.}}"
            + "}"
            + "male{"
            + "{TARGET_GENDER,select,"
            + "female{Reply to her.}"
            + "male{Reply to him.}"
            + "other{Reply to them.}}"
            + "}"
            + "other{"
            + "{TARGET_GENDER,select,"
            + "female{Reply to her.}"
            + "male{Reply to him.}"
            + "other{Reply to them.}}"
            + "}"
            + "}', "
            + "('USER_GENDER', 'TARGET_GENDER'), True), "
            + "{"
            + "'USER_GENDER': data.get('userGender'), "
            + "'TARGET_GENDER': data.get('targetGender')"
            + "})";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testMsgWithPluralWithGender() {
    String soyCode =
        "{@param people:list<?>}\n"
            + "{msg genders=\"$people[0]?.gender, $people[1]?.gender\" desc=\"plural w offsets\"}\n"
            + "  {plural length($people)}\n"
            + "    {case 1}{$people[0].name} is attending\n"
            + "    {case 2}{$people[0].name} and {$people[1]?.name} are attending\n"
            + "    {case 3}{$people[0].name}, {$people[1]?.name}, and 1 other are attending\n"
            + "    {default}{$people[0].name}, {$people[1]?.name}, and length($people) others\n"
            + "  {/plural}\n"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render_icu"
            + "(translator_impl.prepare_icu("
            + "###, "
            + "'{PEOPLE_0_GENDER,select,"
            + "female{{PEOPLE_1_GENDER,select,"
            + "female{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
            + "}"
            + "male{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
            + "}"
            + "other{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}}"
            + "}"
            + "}"
            + "male{{PEOPLE_1_GENDER,select,"
            + "female{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
            + "}"
            + "male{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
            + "}"
            + "other{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}}"
            + "}"
            + "}"
            + "other{{PEOPLE_1_GENDER,select,"
            + "female{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
            + "}"
            + "male{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}"
            + "}"
            + "other{{NUM,plural,"
            + "=1{{NAME_1} is attending}"
            + "=2{{NAME_1} and {NAME_2} are attending}"
            + "=3{{NAME_1}, {NAME_2}, and 1 other are attending}"
            + "other{{NAME_1}, {NAME_2}, and length($people) others}}}"
            + "}"
            + "}"
            + "}', "
            + "('PEOPLE_0_GENDER', 'PEOPLE_1_GENDER', 'NUM', 'NAME_1', 'NAME_2'), True), "
            + "{"
            + "'PEOPLE_0_GENDER': None "
            + "if runtime.key_safe_data_access(data.get('people'), 0) is None "
            + "else runtime.get_field_through_introspection("
            + "runtime.key_safe_data_access(data.get('people'), 0), 'gender'), "
            + "'PEOPLE_1_GENDER': None "
            + "if runtime.key_safe_data_access(data.get('people'), 1) is None "
            + "else runtime.get_field_through_introspection("
            + "runtime.key_safe_data_access(data.get('people'), 1), 'gender'), "
            + "'NUM': len(data.get('people')), "
            + "'NAME_1': str(sanitize.escape_html("
            + "runtime.get_field_through_introspection("
            + "runtime.key_safe_data_access(data.get('people'), 0), 'name'))), "
            + "'NAME_2': str(sanitize.escape_html(None "
            + "if runtime.key_safe_data_access(data.get('people'), 1) is None "
            + "else runtime.get_field_through_introspection("
            + "runtime.key_safe_data_access(data.get('people'), 1), 'name')))"
            + "}"
            + ")";

    assertThatSoyExpr(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  @Test
  public void testPrimaryAlternateWithFallbackAlternate() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\" alternateId=\"123456\"}\n"
            + "  archive\n"
            + "{fallbackmsg desc=\"\" alternateId=\"654321\"}\n"
            + "  ARCHIVE\n"
            + "{/msg}\n";

    String expectedPyCode =
        "(translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'), True))"
            + " if (translator_impl.is_msg_available(###) or not"
            + " translator_impl.is_msg_available(###)) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'),"
            + " True)) if (translator_impl.is_msg_available(###) or"
            + " translator_impl.is_msg_available(###) or (not"
            + " translator_impl.is_msg_available(###) and not"
            + " translator_impl.is_msg_available(###))) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'ARCHIVE'),"
            + " True)) if (translator_impl.is_msg_available(###) or not"
            + " translator_impl.is_msg_available(###)) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'ARCHIVE'),"
            + " True))";

    assertThatSoyExpr(soyCode)
        .compilesTo(
            new PyExpr(expectedPyCode, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testPrimaryAlternateWithFallback() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\" alternateId=\"123456\"}\n"
            + "  archive\n"
            + "{fallbackmsg desc=\"\"}\n"
            + "  ARCHIVE\n"
            + "{/msg}\n";

    String expectedPyCode =
        "(translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'), True))"
            + " if (translator_impl.is_msg_available(###) or not"
            + " translator_impl.is_msg_available(###)) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'),"
            + " True)) if (translator_impl.is_msg_available(###) or"
            + " translator_impl.is_msg_available(###) or not"
            + " translator_impl.is_msg_available(###)) else"
            + " translator_impl.render_literal(translator_impl.prepare_literal(###, 'ARCHIVE'),"
            + " True)";

    assertThatSoyExpr(soyCode)
        .compilesTo(
            new PyExpr(expectedPyCode, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testPrimaryWithFallbackAlternate() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
            + "  archive\n"
            + "{fallbackmsg desc=\"\" alternateId=\"654321\"}\n"
            + "  ARCHIVE\n"
            + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'), True) if"
            + " (translator_impl.is_msg_available(###) or (not"
            + " translator_impl.is_msg_available(###) and not"
            + " translator_impl.is_msg_available(###))) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'ARCHIVE'),"
            + " True)) if (translator_impl.is_msg_available(###) or not"
            + " translator_impl.is_msg_available(###)) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'ARCHIVE'),"
            + " True))";

    assertThatSoyExpr(soyCode)
        .compilesTo(
            new PyExpr(expectedPyCode, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testPrimaryWithFallback() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
            + "  archive\n"
            + "{fallbackmsg desc=\"\"}\n"
            + "  ARCHIVE\n"
            + "{/msg}\n";

    String expectedPyCode =
        "(translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'), True))"
            + " if (translator_impl.is_msg_available(###) or not"
            + " translator_impl.is_msg_available(###)) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'ARCHIVE'),"
            + " True))";

    assertThatSoyExpr(soyCode)
        .compilesTo(
            new PyExpr(expectedPyCode, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testPrimaryAlternate() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\" alternateId=\"123456\"}\n"
            + "  archive\n"
            + "{/msg}\n";

    String expectedPyCode =
        "(translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'), True))"
            + " if (translator_impl.is_msg_available(###) or not"
            + " translator_impl.is_msg_available(###)) else"
            + " (translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'),"
            + " True))";

    assertThatSoyExpr(soyCode)
        .compilesTo(
            new PyExpr(expectedPyCode, PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  @Test
  public void testPrimary() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n" + "  archive\n" + "{/msg}\n";

    String expectedPyCode =
        "translator_impl.render_literal(translator_impl.prepare_literal(###, 'archive'), True)";

    assertThatSoyExpr(soyCode)
        .compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }
}
