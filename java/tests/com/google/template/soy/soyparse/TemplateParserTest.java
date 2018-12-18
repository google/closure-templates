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

package com.google.template.soy.soyparse;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForIfemptyNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateSubject;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for the template parser.
 *
 */
@RunWith(JUnit4.class)
public final class TemplateParserTest {

  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  // -----------------------------------------------------------------------------------------------
  // Tests for recognition only.

  @Test
  public void testRecognizeSoyTag() throws Exception {

    assertValidTemplate("{sp}");
    assertInvalidTemplate("{space}", "Unbound global 'space'");
    assertInvalidTemplate("{ sp }", "Unbound global 'sp'");

    TemplateSubject.assertThatTemplateContent("{{sp}}")
        .causesError("Soy {{command}} syntax is no longer supported. Use single braces.");
    TemplateSubject.assertThatTemplateContent("{{print { }}")
        .causesError("Soy {{command}} syntax is no longer supported. Use single braces.");
    TemplateSubject.assertThatTemplateContent("a {} b")
        .causesError(
            "parse error at '}': expected null, true, false, number, string, -, not, [, (, "
                + "identifier, $ij, or variable");
    TemplateSubject.assertThatTemplateContent("{msg desc=\"\"}a {} b{/msg}")
        .causesError(
            "parse error at '}': expected null, true, false, number, string, -, not, [, (, "
                + "identifier, $ij, or variable");
    TemplateSubject.assertThatTemplateContent("{msg desc=\"\"}<a> {} </a>{/msg}")
        .causesError(
            "parse error at '}': expected null, true, false, number, string, -, not, [, (, "
                + "identifier, $ij, or variable");
    TemplateSubject.assertThatTemplateContent("{msg desc=\"\"}<a href=\"{}\" />{/msg}")
        .causesError(
            "parse error at '}': expected null, true, false, number, string, -, not, [, (, "
                + "identifier, $ij, or variable");

    TemplateSubject.assertThatTemplateContent("{/blah}").causesError("Unexpected closing tag.");

    TemplateSubject.assertThatTemplateContent("}")
        .causesError("Unexpected '}'; did you mean '{rb}'?");

    TemplateSubject.assertThatTemplateContent("{@blah}")
        .causesError(
            "parse error at '@': expected null, true, false, number, string, -, not, "
                + "[, (, identifier, $ij, or variable");
    TemplateSubject.assertThatTemplateContent("{sp ace}")
        .causesError("parse error at '}': expected =");
    TemplateSubject.assertThatTemplateContent("{literal a=b}")
        .causesError("parse error at 'b': expected \\\" or \\'");

    assertValidTemplate("{@param blah : ?}\n{if $blah == 'phname = \"foo\"'}{/if}");
    assertInvalidTemplate("{blah phname=\"\"}");

    assertInvalidTemplate("{");
    assertInvalidTemplate("{{ {sp} }}");
    assertInvalidTemplate("{{ {} }}");
    assertInvalidTemplate("{{ }s{p  { }}");
    assertInvalidTemplate("{}");
    assertInvalidTemplate("{namespace");
    assertInvalidTemplate("{sp");
    assertInvalidTemplate("{sp blah}");
    assertInvalidTemplate("{print } }");
    assertInvalidTemplate("{print }}");
    assertInvalidTemplate("{{}}");
    assertInvalidTemplate("{{{blah: blah}}}");
    assertInvalidTemplate("blah}blah");
    assertInvalidTemplate("blah}}blah");
    assertInvalidTemplate("{{print {{ }}");
  }

  @Test
  public void testRecognizeRawText() throws Exception {
    // prevent parsing as tags by setting content kind to text
    assertValidTemplate(SanitizedContentKind.TEXT, "blah>blah<blah<blah>blah>blah>blah>blah<blah");
    assertValidTemplate("{sp}{nil}{\\n}{\\r}{\\t}{lb}{rb}{nbsp}");
    assertValidTemplate("blah{literal}{ {{{ } }{ {}} { }}}}}}}\n" + "}}}}}}}}}{ { {{/literal}blah");

    assertValidTemplate("{literal}{literal}{/literal}");

    assertInvalidTemplate("{/literal}");
    assertInvalidTemplate("{literal attr=\"value\"}");
  }

  @Test
  public void testRecognizeComments() throws Exception {
    assertValidTemplate(
        "{@param boo : ?}\n"
            + "{@param items : list<[name:string]>}\n"
            + "blah // }\n"
            + "{$boo}{msg desc=\"\"} //}\n"
            + "msg content{/msg} // {/msg}\n"
            + "{for $item in $items}\t// }\n"
            + "{$item.name}{/for} //{{{{\n");

    assertValidTemplate(
        "{@param boo : ?}\n"
            + "{@param items : list<[name:string]>}\n"
            + "blah /* } */\n"
            + "{msg desc=\"\"} /*}*/{$boo}\n"
            + "/******************/ {/msg}\n"
            + "/* {}} { }* }* / }/ * { **}  //}{ { } {\n"
            + "\n  } {//*} {* /} { /* /}{} {}/ } **}}} */\n"
            + "{for $item in $items} /* }\n"
            + "{{{{{*/{$item.name}{/for}/*{{{{*/\n");

    assertValidTemplate(
        "{@param boo : ?}\n"
            + "{@param items : list<[name:string]>}\n"
            + "blah /** } */\n"
            + "{msg desc=\"\"} /**}*/{$boo}\n"
            + "/******************/ {/msg}\n"
            + "/** {}} { }* }* / }/ * { **}  //}{ { } {\n"
            + "\n  } {//**} {* /} { /** /}{} {}/ } **}}} */\n"
            + "{for $item in $items} /** }\n"
            + "{{{{{*/{$item.name}{/for}/**{{{{*/\n");

    assertValidTemplate(" // Not an invalid command: }\n");
    assertValidTemplate(" // Not an invalid command: {{let}}\n");
    assertValidTemplate(" // Not an invalid command: {@let }\n");
    assertValidTemplate(" // Not an invalid command: phname=\"???\"\n");
    assertValidTemplate("{msg desc=\"\"} content // <{/msg}> '<<>\n{/msg}");

    assertValidTemplate("//}\n");
    assertValidTemplate(" //}\n");
    assertValidTemplate("\n//}\n");
    assertValidTemplate("\n //}\n");

    assertValidTemplate("/*}*/\n");
    assertValidTemplate(" /*}*/\n");
    assertValidTemplate("\n/*}\n}*/\n");
    assertValidTemplate("\n /*}\n*/\n");

    assertValidTemplate("/**}*/\n");
    assertValidTemplate(" /**}*/\n");
    assertValidTemplate("\n/**}\n}*/\n");
    assertValidTemplate("\n /**}\n*/\n");

    assertValidTemplate(
        "{@param items : list<string>}\n"
            + "{for $item // }\n"
            + "                                   in $items}\n"
            + "{$item}\n"
            + "{/for}\n");

    assertInvalidTemplate("{css // }");
    assertInvalidTemplate("aa////}\n");
    assertInvalidTemplate("{nil}//}\n");
  }

  @Test
  public void testRecognizeHeaderParams() throws Exception {
    assertValidTemplate("{@param foo: int}\n{$foo}");
    assertValidTemplate("{@param foo: int}\nBODY{$foo}");
    assertValidTemplate("  {@param foo: int}\n  BODY{$foo}");
    assertValidTemplate("\n{@param foo: int}\n{$foo}");
    assertValidTemplate("  \n{@param foo: int}\nBODY{$foo}");
    assertValidTemplate("  \n  {@param foo:\n  int}\n  BODY{$foo}");

    assertValidTemplate("{@param foo: int|list<[a: map<string, int|string>, b:?|null]>}\n{$foo}");

    assertValidTemplate(
        ""
            + "  {@param foo1: int}  {@param foo2: int}\n"
            + "  {@param foo3: int}  /** ... */\n" // doc comment
            + "  {@param foo4: int}  // ...\n" // nondoc comment
            + "  {@param foo5:\n"
            + "       int}  /** ...\n" // doc comment
            + "      ...\n"
            + "      ... */\n"
            + "  /*\n" // nondoc comment
            + "   * ...\n"
            + "   */\n"
            + "  /* ... */\n" // nondoc comment
            + "  {@param foo6: int}  /**\n" // doc comment
            + "      ... */  \n"
            + "  {@param foo7: int}  /*\n" // nondoc comment
            + "      ... */  \n"
            + "\n"
            + "  BODY\n{$foo1 + $foo2 + $foo3 + $foo4 + $foo5 + $foo6 + $foo7}");

    assertValidTemplate(
        ""
            + "  /** */{@param foo1: int}\n" // doc comment
            + "  /** \n" // doc comment
            + "   */{@param foo2: int}\n"
            + "\n"
            + "  BODY\n{$foo1 + $foo2}");

    assertValidTemplate("{@param foo: int}{$foo}");
    assertInvalidTemplate("{@ param foo: int}\n");
    assertInvalidTemplate("{@foo}\n");
    assertInvalidTemplate("{@foo foo: int}\n");

    assertValidTemplate(
        ""
            + "  /** ... */\n" // doc comment
            + "  {@param foo: int}\n"
            + "  BODY\n{$foo}");
    assertValidTemplate(
        ""
            + "  {@param foo1: int}\n"
            + "  /**\n" // doc comment
            + "   * ...\n"
            + "   */\n"
            + "  {@param foo2: int}\n"
            + "  BODY\n{$foo1 + $foo2}");
    assertValidTemplate(
        ""
            + "  {@param foo1: int}  /*\n"
            + "      */  /** ... */\n" // doc comment
            + "  {@param foo2: int}\n"
            + "  BODY\n{$foo1 + $foo2}");

    assertInvalidTemplate("{@param 33: int}");
    assertInvalidTemplate("{@param f-oo: int}");
    assertInvalidTemplate("{@param foo}");
    assertInvalidTemplate("{@param foo:}");
    assertInvalidTemplate("{@param : int}");
    assertInvalidTemplate("{@param foo int}");
  }

  @Test
  public void testQuotedStringsInCommands() throws Exception {
    assertValidTemplate("{let $a: null /}");
    assertValidTemplate("{let $a: '' /}");
    assertValidTemplate("{let $a: 'a\"b\"c' /}");
    assertValidTemplate("{let $a: 'abc\\'def' /}");
    assertValidTemplate("{let $a: 'abc\\\\def' /}");
    assertValidTemplate("{let $a: 'abc\\\\\\\\def' /}");

    assertValidTemplate("{let $a: '\\\\ \\' \\\" \\n \\r \\t \\b \\f  \\u00A9 \\u2468' /}");

    assertValidTemplate("{let $a: '{} abc {}' /}");
    assertValidTemplate("{let $a: '{} abc\\'def {}' /}");
    assertValidTemplate("{let $a: '{} abc\\\\def {}' /}");
    assertValidTemplate("{let $a: '{} abc\\\\\\\\def {}' /}");

    assertValidTemplate("{call .blah} {param a: record(blah: '{} abc\\\\\\\\def {}' ) /} {/call}");

    assertValidTemplate("{msg desc=\"\\\"\"}x{/msg}");
    assertValidTemplate("{msg desc=\"Hi! I'm short! {}\"}x{/msg}");
  }

  @Test
  public void testRecognizeHeaderInjectedParams() throws Exception {
    assertValidTemplate("{@inject foo: int}\n{$foo}");
    assertValidTemplate("{@inject foo: int}\nBODY{$foo}");
    assertValidTemplate("  {@inject foo: int}\n  BODY{$foo}");
    assertValidTemplate("\n{@inject foo: int}\n{$foo}");
    assertValidTemplate("  \n{@inject foo: int}\nBODY{$foo}");
    assertValidTemplate("  \n  {@inject foo:\n   int}\n  BODY{$foo}");

    assertValidTemplate(
        ""
            + "  {@inject foo1: int}  {@inject foo2: int}\n"
            + "  {@inject foo3: int}  /** ... */\n" // doc comment
            + "  {@inject foo4: int}  // ...\n" // nondoc comment
            + "  {@inject foo5:\n"
            + "       int}  /** ...\n" // doc comment
            + "      ...\n"
            + "      ... */\n"
            + "  /*\n" // nondoc comment
            + "   * ...\n"
            + "   */\n"
            + "  /* ... */\n" // nondoc comment
            + "  {@inject foo6: int}  /**\n" // doc comment
            + "      ... */  \n"
            + "  {@inject foo7: int}  /*\n" // nondoc comment
            + "      ... */  \n"
            + "\n"
            + "  {$foo1 + $foo2 + $foo3 + $foo4 + $foo5 + $foo6 + $foo7}\n");

    assertValidTemplate(
        ""
            + "  /** */{@inject foo1: int}\n" // doc comment
            + "  /** \n" // doc comment
            + "   */{@inject foo2: int}\n"
            + "\n"
            + "  {$foo1 + $foo2}\n");

    assertValidTemplate("{@inject foo: int}{$foo}");
    assertInvalidTemplate("{@ param foo: int}\n");
    assertInvalidTemplate("{@foo}\n");
    assertInvalidTemplate("{@foo foo: int}\n");

    assertValidTemplate(
        ""
            + "  /** ... */\n" // doc comment
            + "  {@inject foo: int}\n"
            + "  {$foo}\n");
    assertValidTemplate(
        ""
            + "  {@inject foo1: int}\n"
            + "  /**\n" // doc comment
            + "   * ...\n"
            + "   */\n"
            + "  {@inject foo2: int}\n"
            + "  {$foo1 + $foo2}\n");
    assertValidTemplate(
        ""
            + "  {@inject foo1: int}  /*\n"
            + "      */  /** ... */\n" // doc comment
            + "  {@inject foo2: int}\n"
            + "  {$foo1 + $foo2}\n");
  }

  @Test
  public void testRecognizeCommands() throws Exception {
    // Starts with `for`
    assertInvalidTemplate("{@param blah : ? }\n{formatDate($blah)}", "Unknown function");
    // Starts with `msg`
    assertInvalidTemplate("{@param blah : ? }\n{msgblah($blah)}", "Unknown function");
    assertValidTemplate("{let $a: 2 /}"); // Not a print

    assertValidTemplate(
        "{@param foo : ?}\n"
            + "{@param fooUrl : ?}\n"
            + "{@param boo : ?}\n"
            + "{msg desc=\"blah\" hidden=\"true\"}\n"
            + "  {$boo} is a <a href=\"{$fooUrl}\">{$foo}</a>.\n"
            + "{/msg}");
    assertValidTemplate(
        ""
            + "{msg meaning=\"verb\" desc=\"\"}\n"
            + "  Archive\n"
            + "{fallbackmsg desc=\"\"}\n"
            + "  Archive\n"
            + "{/msg}");
    assertValidTemplate(
        "{@param aaa : ?}{@param bbb : ?}{@param ddd : ?}\n"
            + "{$aaa + 1}{print $bbb.ccc[$ddd] |noAutoescape}");
    assertValidTemplate("{css('selected-option')}{css('CSS_SELECTED_OPTION')}");
    assertValidTemplate("{xid('selected-option')}{xid('SELECTED_OPTION_ID')}");
    assertValidTemplate(
        "{@param boo : ?}{@param goo : ?}" + "{if $boo}foo{elseif $goo}moo{else}zoo{/if}");
    assertValidTemplate(
        "{@param foo : ?}{@param boo : ?}{@param goo : ?}"
            + "  {switch $boo}\n"
            + "    {case $foo} blah blah\n"
            + "    {case 2, $goo.moo, 'too'} bleh bleh\n"
            + "    {default} bluh bluh\n"
            + "  {/switch}\n");
    assertValidTemplate(
        "{@param items : list<?>}" + "{for $item in $items}{index($item)}. {$item.name}<br>{/for}");
    assertValidTemplate(
        "{@param boo : ?}"
            + "{for $i in range($boo + 1,\n"
            + "                 88, 11)}\n"
            + "Number {$i}.{/for}");
    assertValidTemplate("{call aaa.bbb.ccc data=\"all\" /}");
    assertValidTemplate(
        "{@param boo : ?}"
            + "{call .aaa}\n"
            + "  {param boo: $boo /}\n"
            + "  {param foo kind=\"text\"}blah blah{/param}\n"
            + "  {param foo kind=\"html\"}blah blah{/param}\n"
            + "{/call}");

    TemplateSubject.assertThatTemplateContent("{'unfinished}")
        .causesError("Unexpected newline in Soy string.");
    TemplateSubject.assertThatTemplateContent("{\"unfinished}")
        .causesError("Unexpected newline in Soy string.");

    assertValidTemplate("{call aaa.bbb.ccc data=\"all\" /}");
    assertValidTemplate(
        "{@param boo : ?}"
            + "{call .aaa}\n"
            + "  {param boo: $boo /}\n"
            + "  {param foo kind=\"text\"}blah blah{/param}\n"
            + "{/call}");
    assertValidTemplate("{delcall aaa.bbb.ccc data=\"all\" /}");
    assertValidTemplate(
        "{@param boo : ?}"
            + "{delcall ddd.eee}\n"
            + "  {param boo: $boo /}\n"
            + "  {param foo kind=\"text\"}blah blah{/param}\n"
            + "{/delcall}");
    assertValidTemplate(
        "{@param boo : ?}"
            + "{@param foo : ?}"
            + "{@param fooUrl : ?}"
            + "{msg meaning=\"boo\" desc=\"blah\"}\n"
            + "  {$boo phname=\"foo\"} is a \n"
            + "  <a phname=\"begin_link\" href=\"{$fooUrl}\">\n"
            + "    {$foo |noAutoescape phname=\"booFoo\" }\n"
            + "  </a phname=\"END_LINK\" >.\n"
            + "  {call .aaa data=\"all\"\nphname=\"AaaBbb\"/}\n"
            + "  {call .aaa phname=\"AaaBbb\" data=\"all\"}{/call}\n"
            + "{/msg}");
    assertValidTemplate("{log}Blah blah.{/log}");
    assertValidTemplate("{debugger}");
    assertValidTemplate("{let $foo : 1 + 2/}\n");
    assertValidTemplate("{let $foo : '\"'/}\n");
    assertValidTemplate("{let $foo kind=\"text\"}Hello{/let}\n");
    assertValidTemplate("{let $foo kind=\"html\"}Hello{/let}\n");

    TemplateSubject.assertThatTemplateContent("{{let a: b}}")
        .causesError("Soy {{command}} syntax is no longer supported. Use single braces.");

    // This is parsed as a print command, which shouldn't end in /}
    TemplateSubject.assertThatTemplateContent("{{let a: b /}}")
        .causesError("Soy {{command}} syntax is no longer supported. Use single braces.");
    assertInvalidTemplate("{{let a: b /}}");

    assertInvalidTemplate("{namespace}");
    assertInvalidTemplate("{template}\n" + "blah\n" + "{/template}\n");
    assertInvalidTemplate("{msg}blah{/msg}");
    assertInvalidTemplate("{/msg}");
    assertInvalidTemplate("{msg desc=\"\"}<a href=http://www.google.com{/msg}");
    assertInvalidTemplate("{msg desc=\"\"}blah{msg desc=\"\"}bleh{/msg}bluh{/msg}");
    assertInvalidTemplate("{msg desc=\"\"}blah{/msg blah}");

    TemplateSubject.assertThatTemplateContent(
            "{@param blah : ?}\n"
                + "{msg meaning=\"verb\" desc=\"\"}\n"
                + "  Hi {if $blah}a{/if}\n"
                + "{/msg}")
        .causesError(
            "Unexpected soy command in '{msg ...}' block. Only message placeholder commands "
                + "({print, {call and html tags) are allowed to be direct children of messages.");
    TemplateSubject.assertThatTemplateContent(
            ""
                + "{msg meaning=\"verb\" desc=\"\"}\n"
                + "  Archive\n"
                + "{fallbackmsg desc=\"\"}\n"
                + "  Archive\n"
                + "{fallbackmsg desc=\"\"}\n"
                + "  Store\n"
                + "{/msg}")
        .causesError(
            "parse error at '{fallbackmsg ': expected text, {literal}, {call, {delcall, {msg, "
                + "{/msg}, {if, {let, {for, {plural, {select, {switch, {log}, {debugger}, {print, "
                + "{, {key, or whitespace");
    assertInvalidTemplate("{print $boo /}");
    assertInvalidTemplate("{if true}aaa{else/}bbb{/if}");
    assertInvalidTemplate("{call .aaa.bbb /}");
    assertInvalidTemplate("{delcall ddd.eee}{param foo: 0}{/call}");
    assertInvalidTemplate("{delcall .dddEee /}");
    assertInvalidTemplate("{call.aaa}{param boo kind=\"html\": 123 /}{/call}\n");
    assertInvalidTemplate("{log}");
    assertInvalidTemplate("{log 'Blah blah.'}");
    assertInvalidTemplate("{let $foo kind=\"html\" : 1 + 1/}\n");
    assertInvalidTemplate("{xid a.b-c}");
    assertInvalidTemplate("{msg desc=\"\"}{$boo phname=\"boo.foo\"}{/msg}");
    assertInvalidTemplate("{msg desc=\"\"}<br phname=\"boo-foo\" />{/msg}");
    assertInvalidTemplate("{msg desc=\"\"}{call .boo phname=\"boo\" phname=\"boo\" /}{/msg}");
    assertInvalidTemplate("{msg desc=\"\"}<br phname=\"break\" phname=\"break\" />{/msg}");
  }

  @Test
  public void testRecognizeMsgPlural() throws Exception {
    // Normal, valid plural message.
    assertValidTemplate(
        "{@param num_people : int}\n"
            + "{@param person: int}\n"
            + "{@param place : int}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($num_people)} "
            + "other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n");

    assertValidTemplate(
        "  {let $roundedWeeksSinceStart : 3 /}\n"
            + "  {msg desc=\"Message for number of weeks ago something happened.\"}\n"
            + "    {plural $roundedWeeksSinceStart}\n"
            + "      {case 1} 1 week ago\n"
            + "      {default} {$roundedWeeksSinceStart} weeks ago\n"
            + "    {/plural}\n"
            + "  {/msg}");

    // Offset is optional.
    assertValidTemplate(
        "{@param num_people : int}\n"
            + "{@param person: int}\n"
            + "{@param place : int}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {default}I see {$num_people} in {$place}, including {$person}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n");

    // Plural message should have a default clause.
    assertInvalidTemplate(
        "{@param num_people : int}\n"
            + "{@param person: int}\n"
            + "{@param place : int}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n");

    // default should be the last clause, after all cases.
    assertInvalidTemplate(
        "{@param num_people : int}\n"
            + "{@param person: int}\n"
            + "{@param place : int}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {default}I see {$person} and {remainder($num_people)} "
            + "other people in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n");

    // Order is irrelevant for cases.
    assertValidTemplate(
        "{@param num_people : int}\n"
            + "{@param person: int}\n"
            + "{@param place : int}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($num_people)} "
            + "other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n");

    // Offset should not be less than 0.
    assertInvalidTemplate(
        "{@param num_people : int}\n"
            + "{@param person: int}\n"
            + "{@param place : int}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"-1\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($num_people)} "
            + "other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n");

    // Case should not be less than 0.
    assertInvalidTemplate(
        "{@param num_people : int}\n"
            + "{@param person: int}\n"
            + "{@param place : int}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case -1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($num_people)} "
            + "other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n");
  }

  @Test
  public void testRecognizeMsgSelect() throws Exception {
    assertValidTemplate(
        "{@param gender : ?}\n"
            + "{@param person: ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "{/msg}\n");

    // Default should be present.
    assertInvalidTemplate(
        "{@param gender : ?}\n"
            + "{@param person: ?}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n");

    // Default should be the last clause.
    assertInvalidTemplate(
        "{@param gender : ?}\n"
            + "{@param person: ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {default}{$person} added you to his circle.\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "  {/select}\n"
            + "{/msg}\n");

    // There is no restriction that 'female' and 'male' should not occur together.
    assertValidTemplate(
        "{@param gender : ?}\n"
            + "{@param person: ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {case 'male'}{$person} added you to his circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "{/msg}\n");

    // There is no restriction of case keywords. An arbitrary word like 'neuter' is fine.
    assertValidTemplate(
        "{@param gender : ?}\n"
            + "{@param person: ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {case 'male'}{$person} added you to his circle.\n"
            + "    {case 'neuter'}{$person} added you to its circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "{/msg}\n");

    // It is not possible to have more than one string in a case.
    assertInvalidTemplate(
        "{@param job : ?}\n"
            + "{@param person: ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $job}\n"
            + "    {case 'hw_engineer', 'sw_engineer'}{$person}, an engineer, liked this.\n"
            + "    {default}{$person} liked this.\n"
            + "  {/select}\n"
            + "{/msg}\n");

    // select should have a default.
    assertInvalidTemplate(
        "{@param gender : ?}\n"
            + "{@param person: ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {case 'male'}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "{/msg}\n");
  }

  @Test
  public void testRecognizeNestedPlrsel() throws Exception {
    // Select nested inside select should be allowed.
    assertValidTemplate(
        "{@param gender : ?}{@param gender2 : ?}{@param person1 : ?}{@param person2 : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}\n"
            + "      {select $gender2}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to her circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $gender2}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to his circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to his circle.\n"
            + "      {/select}\n"
            + "  {/select}\n"
            + "{/msg}\n");

    // Plural nested inside select should be allowed.
    assertValidTemplate(
        "{@param gender : ?}{@param person : ?}{@param num_people : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}\n"
            + "      {plural $num_people}\n"
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$person} added {$num_people} to her circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $num_people}\n"
            + "        {case 1}{$person} added one person to his circle.\n"
            + "        {default}{$person} added {$num_people} to his circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}\n");

    // Plural inside plural should not be allowed.
    assertInvalidTemplate(
        "{@param n_friends : ?}{@param n_circles : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {plural $n_friends}\n"
            + "    {case 1}\n"
            + "      {plural $n_circles}\n"
            + "        {case 1}You have one friend in one circle.\n"
            + "        {default}You have one friend in {$n_circles} circles.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $n_circles}\n"
            + "        {case 1}You have {$n_friends} friends in one circle.\n"
            + "        {default}You have {$n_friends} friends in {$n_circles} circles.\n"
            + "      {/plural}\n"
            + "  {/plural}\n"
            + "{/msg}\n");

    // Select inside plural should not be allowed.
    assertInvalidTemplate(
        "{@param n_friends : ?}{@param gender : ?}{@param person : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {plural $n_friends}\n"
            + "    {case 1}\n"
            + "      {select $gender}\n"
            + "        {case 'female'}{$person} has one person in her circle.\n"
            + "        {default}{$person} has one person in his circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $gender}\n"
            + "        {case 'female'}{$person} has {$n_friends} persons in her circle.\n"
            + "        {default}{$person} has {$n_friends} persons in his circle.\n"
            + "      {/select}\n"
            + "  {/plural}\n"
            + "{/msg}\n");

    // Messages with more than one plural/gender clauses should not be allowed.
    assertInvalidTemplate(
        "{@param num_people : ?}{@param gender : ?}{@param person : ?}{@param place : ?}\n"
            + "{msg desc=\"A sample plural message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "  {plural $num_people offset=\"1\"}\n"
            + "    {case 0}I see no one in {$place}.\n"
            + "    {case 1}I see {$person} in {$place}.\n"
            + "    {case 2}I see {$person} and one other person in {$place}.\n"
            + "    {default}I see {$person} and {remainder($num_people)} "
            + "other people in {$place}.\n"
            + "  {/plural}\n"
            + " {/msg}\n");
  }

  @Test
  public void testMapTrailingComma() throws Exception {
    assertValidTemplate("{map(1:2,)}");
    assertValidTemplate("{map(1:2,2:3,3:4,)}");

    assertInvalidTemplate("{map(,)}");
    assertInvalidTemplate("{map(,1:2)}");
    assertInvalidTemplate("{map(,1:2,)}");
    assertInvalidTemplate("{map(,1:2,2:3,4:5)}");
    assertInvalidTemplate("{map(,1:2,2:3,4:5,)}");
  }

  // -----------------------------------------------------------------------------------------------
  // Tests for recognition and parse results.

  @Test
  public void testParseRawText() throws Exception {

    String templateBody =
        "  {sp} aaa bbb  \n"
            + "  ccc {lb}{rb} ddd {\\n}\n"
            + "  eee {nbsp}<br>\n"
            + "  fff\n"
            + "  {literal}ggg\n"
            + "hhh }{  {/literal}  \n"
            + "  \u2222\uEEEE\u9EC4\u607A\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();

    assertEquals(1, nodes.size());
    RawTextNode rtn = (RawTextNode) nodes.get(0);
    assertEquals(
        "  aaa bbb ccc {} ddd \neee \u00A0<br>fffggg\nhhh }{  \u2222\uEEEE\u9EC4\u607A",
        rtn.getRawText());
    assertEquals(
        "  aaa bbb ccc {lb}{rb} ddd {\\n}eee {nbsp}<br>"
            + "fffggg{\\n}hhh {rb}{lb}  \u2222\uEEEE\u9EC4\u607A",
        rtn.toSourceString());
  }

  @Test
  public void testParseComments() throws Exception {

    String templateBody =
        ""
            + "  {sp}  // {sp}\n" // first {sp} outside of comments
            + "  /* {sp} {sp} */  // {sp}\n"
            + "  /** {sp} {sp} */  // {sp}\n"
            + "  /* {sp} */{sp}/* {sp} */\n" // middle {sp} outside of comments
            + "  /** {sp} */{sp}/** {sp} */\n" // middle {sp} outside of comments
            + "  /* {sp}\n"
            + "  {sp} */{sp}\n" // last {sp} outside of comments
            + "  /** {sp}\n"
            + "  {sp} */{sp}\n" // last {sp} outside of comments
            + "  {sp}/* {sp}\n" // first {sp} outside of comments
            + "  {sp} */\n"
            + "  {sp}/** {sp}\n" // first {sp} outside of comments
            + "  {sp} */\n"
            + "  // {sp} /* {sp} */\n"
            + "  // {sp} /** {sp} */\n"
            // not a comment if "//" preceded by a non-space such as ":"
            + "  http://www.google.com\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());
    assertEquals("       http://www.google.com", ((RawTextNode) nodes.get(0)).getRawText());
  }

  @Test
  public void testParseHeaderDecls() throws Exception {

    String templateHeaderAndBody =
        ""
            + "  {@param boo: string}  // Something scary. (Not doc comment.)\n"
            + "  {@param foo: list<int>}  /** Something random. */\n"
            + "  {@param goo: string}/** Something\n"
            + "      slimy. */\n"
            + "  /* Something strong. (Not doc comment.) */"
            + "  // {@param commentedOut: string}\n"
            + "  {@param moo: string}{@param too: string}\n"
            + "  {@param? woo: string}  /** Something exciting. */  {@param hoo: string}\n"
            + "  {$boo + $goo + $moo + $too + $woo + $hoo}{$foo}\n"; // use all the params

    TemplateNode result = parseTemplateContent(templateHeaderAndBody, FAIL);
    assertEquals(7, Iterables.size(result.getAllParams()));
    assertEquals(
        "{$boo + $goo + $moo + $too + $woo + $hoo}", result.getChildren().get(0).toSourceString());

    List<TemplateParam> declInfos = ImmutableList.copyOf(result.getAllParams());
    assertFalse(declInfos.get(0).isInjected());
    assertEquals("boo", declInfos.get(0).name());
    assertEquals("string", declInfos.get(0).type().toString());
    assertEquals(null, declInfos.get(0).desc());
    assertEquals("foo", declInfos.get(1).name());
    assertEquals("list<int>", declInfos.get(1).type().toString());
    assertEquals(null, declInfos.get(1).desc());
    assertEquals("Something random.", declInfos.get(2).desc());
    assertEquals("Something\n      slimy.", declInfos.get(3).desc());
    assertEquals("too", declInfos.get(4).name());
    assertEquals(null, declInfos.get(4).desc());
    assertEquals("woo", declInfos.get(5).name());
    assertEquals(null, declInfos.get(5).desc());
    assertEquals("Something exciting.", declInfos.get(6).desc());
  }

  @Test
  public void testParsePrintStmt() throws Exception {

    String templateBody =
        "{@param boo : ?}{@param goo : ?}\n"
            + "  {$boo.foo}{$boo.foo}\n"
            + "  {$goo + 1 |noAutoescape}\n"
            + "  {'blah    blahblahblah' |insertWordBreaks:8}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(4, nodes.size());

    PrintNode pn0 = (PrintNode) nodes.get(0);
    assertEquals("$boo.foo", pn0.getExpr().toSourceString());
    assertEquals(0, pn0.getChildren().size());
    assertEquals("FOO", pn0.genBasePhName());
    assertEquals("{$boo.foo}", pn0.toSourceString());
    assertTrue(pn0.getExpr().getRoot() instanceof FieldAccessNode);

    PrintNode pn1 = (PrintNode) nodes.get(1);
    assertTrue(pn0.genSamenessKey().equals(pn1.genSamenessKey()));
    assertTrue(pn1.getExpr().getRoot() instanceof FieldAccessNode);

    PrintNode pn2 = (PrintNode) nodes.get(2);
    assertEquals("$goo + 1", pn2.getExpr().toSourceString());
    assertEquals(1, pn2.getChildren().size());
    PrintDirectiveNode pn2d0 = pn2.getChild(0);
    assertEquals("|noAutoescape", pn2d0.getName());
    assertEquals("XXX", pn2.genBasePhName());
    assertTrue(pn2.getExpr().getRoot() instanceof PlusOpNode);

    PrintNode pn3 = (PrintNode) nodes.get(3);
    assertEquals("'blah    blahblahblah'", pn3.getExpr().toSourceString());
    assertEquals(1, pn3.getChildren().size());
    PrintDirectiveNode pn3d0 = pn3.getChild(0);
    assertEquals("|insertWordBreaks", pn3d0.getName());
    assertEquals(8, ((IntegerNode) pn3d0.getArgs().get(0).getRoot()).getValue());
    assertEquals("XXX", pn3.genBasePhName());
    assertTrue(pn3.getExpr().getRoot() instanceof StringNode);

    assertFalse(pn0.genSamenessKey().equals(pn2.genSamenessKey()));
    assertFalse(pn3.genSamenessKey().equals(pn0.genSamenessKey()));
  }

  @Test
  public void testParsePrintStmtWithPhname() throws Exception {

    String templateBody =
        "{@param boo : ?}\n"
            + "{msg desc=\"...\"}\n"
            + "  {$boo.foo}\n"
            + "  {$boo.foo phname=\"booFoo\"}\n"
            + "  {$boo.foo    phname=\"booFoo\"    }\n"
            + "    {print $boo.foo phname=\"boo_foo\"}\n"
            + "{/msg}";

    List<StandaloneNode> nodes =
        ((MsgFallbackGroupNode) parseTemplateContent(templateBody, FAIL).getChild(0))
            .getChild(0)
            .getChildren();
    assertEquals(4, nodes.size());

    PrintNode pn0 = (PrintNode) ((MsgPlaceholderNode) nodes.get(0)).getChild(0);
    assertEquals("$boo.foo", pn0.getExpr().toSourceString());
    assertEquals("FOO", pn0.genBasePhName());
    assertEquals("{$boo.foo}", pn0.toSourceString());

    PrintNode pn1 = (PrintNode) ((MsgPlaceholderNode) nodes.get(1)).getChild(0);
    assertEquals("$boo.foo", pn1.getExpr().toSourceString());
    assertEquals("BOO_FOO", pn1.genBasePhName());
    assertEquals("{$boo.foo phname=\"booFoo\"}", pn1.toSourceString());
    assertEquals(0, pn1.getChildren().size());
    assertTrue(pn1.getExpr().getRoot() instanceof FieldAccessNode);

    PrintNode pn2 = (PrintNode) ((MsgPlaceholderNode) nodes.get(2)).getChild(0);
    assertEquals("$boo.foo", pn2.getExpr().toSourceString());
    assertEquals("BOO_FOO", pn2.genBasePhName());
    assertEquals("{$boo.foo phname=\"booFoo\"}", pn2.toSourceString());

    PrintNode pn3 = (PrintNode) ((MsgPlaceholderNode) nodes.get(3)).getChild(0);
    assertEquals("$boo.foo", pn3.getExpr().toSourceString());
    assertEquals("BOO_FOO", pn3.genBasePhName());
    assertEquals("{print $boo.foo phname=\"boo_foo\"}", pn3.toSourceString());

    assertFalse(pn0.genSamenessKey().equals(pn1.genSamenessKey()));
    assertTrue(pn1.genSamenessKey().equals(pn2.genSamenessKey()));
    assertFalse(pn1.genSamenessKey().equals(pn3.genSamenessKey()));
  }

  @Test
  public void testParseMsgStmt() throws Exception {

    String templateBody =
        "{@param usedMb :?}{@param learnMoreUrl :?}\n"
            + "  {msg desc=\"Tells user's quota usage.\"}\n"
            + "    You're currently using {$usedMb} MB of your quota.{sp}\n"
            + "    <a href=\"{$learnMoreUrl}\">Learn more</A>\n"
            + "    <br /><br />\n"
            + "  {/msg}\n"
            + "  {msg meaning=\"noun\" desc=\"\" hidden=\"true\"}Archive{/msg}\n"
            + "  {msg meaning=\"noun\" desc=\"The archive (noun).\"}Archive{/msg}\n"
            + "  {msg meaning=\"verb\" desc=\"\"}Archive{/msg}\n"
            + "  {msg desc=\"\"}Archive{/msg}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(5, nodes.size());

    MsgNode mn0 = ((MsgFallbackGroupNode) nodes.get(0)).getMsg();
    assertEquals("Tells user's quota usage.", mn0.getDesc());
    assertEquals(null, mn0.getMeaning());
    assertEquals(false, mn0.isHidden());
    assertEquals(8, mn0.numChildren());

    assertEquals("You're currently using ", ((RawTextNode) mn0.getChild(0)).getRawText());
    MsgPlaceholderNode mpn1 = (MsgPlaceholderNode) mn0.getChild(1);
    assertEquals("$usedMb", ((PrintNode) mpn1.getChild(0)).getExpr().toSourceString());
    assertEquals(" MB of your quota. ", ((RawTextNode) mn0.getChild(2)).getRawText());

    MsgPlaceholderNode mpn3 = (MsgPlaceholderNode) mn0.getChild(3);
    MsgHtmlTagNode mhtn3 = (MsgHtmlTagNode) mpn3.getChild(0);
    assertEquals("a", mhtn3.getLcTagName());
    assertEquals("START_LINK", mhtn3.genBasePhName());
    assertEquals("<a href=\"{$learnMoreUrl}\">", mhtn3.toSourceString());

    assertEquals(3, mhtn3.numChildren());
    assertEquals("<a href=\"", ((RawTextNode) mhtn3.getChild(0)).getRawText());
    assertEquals("$learnMoreUrl", ((PrintNode) mhtn3.getChild(1)).getExpr().toSourceString());
    assertEquals("\">", ((RawTextNode) mhtn3.getChild(2)).getRawText());

    assertEquals("Learn more", ((RawTextNode) mn0.getChild(4)).getRawText());

    MsgPlaceholderNode mpn5 = (MsgPlaceholderNode) mn0.getChild(5);
    MsgHtmlTagNode mhtn5 = (MsgHtmlTagNode) mpn5.getChild(0);
    assertEquals("/a", mhtn5.getLcTagName());
    assertEquals("END_LINK", mhtn5.genBasePhName());
    assertEquals("</A>", mhtn5.toSourceString());

    MsgPlaceholderNode mpn6 = (MsgPlaceholderNode) mn0.getChild(6);
    MsgHtmlTagNode mhtn6 = (MsgHtmlTagNode) mpn6.getChild(0);
    assertEquals("BREAK", mhtn6.genBasePhName());
    assertTrue(mpn6.shouldUseSameVarNameAs((MsgPlaceholderNode) mn0.getChild(7)));
    assertFalse(mpn6.shouldUseSameVarNameAs(mpn5));
    assertFalse(mpn5.shouldUseSameVarNameAs(mpn3));

    MsgFallbackGroupNode mfgn1 = (MsgFallbackGroupNode) nodes.get(1);
    assertEquals(
        "{msg meaning=\"noun\" desc=\"\" hidden=\"true\"}Archive{/msg}", mfgn1.toSourceString());
    MsgNode mn1 = mfgn1.getMsg();
    assertEquals("", mn1.getDesc());
    assertEquals("noun", mn1.getMeaning());
    assertEquals(true, mn1.isHidden());
    assertEquals(1, mn1.numChildren());
    assertEquals("Archive", ((RawTextNode) mn1.getChild(0)).getRawText());
  }

  @Test
  public void testParseMsgHtmlTagWithPhname() throws Exception {

    String templateBody =
        "{@param learnMoreUrl :?}\n"
            + "  {msg desc=\"\"}\n"
            + "    <a href=\"{$learnMoreUrl}\" phname=\"beginLearnMoreLink\">\n"
            + "      Learn more\n"
            + "    </A phname=\"end_LearnMore_LINK\">\n"
            + "    <br phname=\"breakTag\" /><br phname=\"breakTag\" />"
            + "<br phname=\"break_tag\" />\n"
            + "  {/msg}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    MsgNode mn0 = ((MsgFallbackGroupNode) nodes.get(0)).getChild(0);
    assertEquals(6, mn0.numChildren());

    MsgPlaceholderNode mpn0 = (MsgPlaceholderNode) mn0.getChild(0);
    MsgHtmlTagNode mhtn0 = (MsgHtmlTagNode) mpn0.getChild(0);
    assertEquals("a", mhtn0.getLcTagName());
    assertEquals("BEGIN_LEARN_MORE_LINK", mhtn0.genBasePhName());
    assertEquals(
        "<a href=\"{$learnMoreUrl}\" phname=\"beginLearnMoreLink\">", mhtn0.toSourceString());

    MsgPlaceholderNode mpn2 = (MsgPlaceholderNode) mn0.getChild(2);
    MsgHtmlTagNode mhtn2 = (MsgHtmlTagNode) mpn2.getChild(0);
    assertEquals("/a", mhtn2.getLcTagName());
    assertEquals("END_LEARN_MORE_LINK", mhtn2.genBasePhName());
    assertEquals("</A phname=\"end_LearnMore_LINK\">", mhtn2.toSourceString());

    MsgPlaceholderNode mpn3 = (MsgPlaceholderNode) mn0.getChild(3);
    MsgHtmlTagNode mhtn3 = (MsgHtmlTagNode) mpn3.getChild(0);
    assertEquals("br", mhtn3.getLcTagName());
    assertEquals("BREAK_TAG", mhtn3.genBasePhName());
    assertEquals("<br phname=\"breakTag\"/>", mhtn3.toSourceString());

    MsgPlaceholderNode mpn4 = (MsgPlaceholderNode) mn0.getChild(4);
    MsgHtmlTagNode mhtn4 = (MsgHtmlTagNode) mpn4.getChild(0);

    MsgPlaceholderNode mpn5 = (MsgPlaceholderNode) mn0.getChild(5);
    MsgHtmlTagNode mhtn5 = (MsgHtmlTagNode) mpn5.getChild(0);
    assertEquals("br", mhtn5.getLcTagName());
    assertEquals("BREAK_TAG", mhtn5.genBasePhName());
    assertEquals("<br phname=\"break_tag\"/>", mhtn5.toSourceString());

    assertFalse(mhtn0.genSamenessKey().equals(mhtn2.genSamenessKey()));
    assertFalse(mhtn0.genSamenessKey().equals(mhtn3.genSamenessKey()));
    assertTrue(mhtn3.genSamenessKey().equals(mhtn4.genSamenessKey()));
    assertFalse(mhtn3.genSamenessKey().equals(mhtn5.genSamenessKey()));
  }

  @Test
  public void testParseMsgStmtWithCall() throws Exception {

    String templateBody =
        "  {msg desc=\"Blah.\"}\n"
            + "    Blah {call .helper_ data=\"all\" /} blah{sp}\n"
            + "    {call .helper_}\n"
            + "      {param foo : 'foo' /}\n"
            + "    {/call}{sp}\n"
            + "    blah.\n"
            + "  {/msg}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    MsgNode mn = ((MsgFallbackGroupNode) nodes.get(0)).getChild(0);
    assertEquals(5, mn.numChildren());

    assertEquals("Blah ", ((RawTextNode) mn.getChild(0)).getRawText());
    assertEquals(0, ((CallNode) ((MsgPlaceholderNode) mn.getChild(1)).getChild(0)).numChildren());
    assertEquals(" blah ", ((RawTextNode) mn.getChild(2)).getRawText());
    assertEquals(1, ((CallNode) ((MsgPlaceholderNode) mn.getChild(3)).getChild(0)).numChildren());
    assertEquals(" blah.", ((RawTextNode) mn.getChild(4)).getRawText());
  }

  @Test
  public void testParseMsgStmtWithIf() throws Exception {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parseTemplateContent(
        "{@param boo :?}\n"
            + "  {msg desc=\"Blah.\"}\n"
            + "    Blah \n"
            + "    {if $boo}\n"
            + "      bleh\n"
            + "    {else}\n"
            + "      bluh\n"
            + "    {/if}\n"
            + "    .\n"
            + "  {/msg}\n",
        errorReporter);
    assertThat(errorReporter.getErrors()).isNotEmpty();
  }

  @Test
  public void testParseMsgStmtWithFallback() throws Exception {

    String templateBody =
        ""
            + "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
            + "  Archive\n"
            + "{fallbackmsg desc=\"\"}\n"
            + "  Archive\n"
            + "{/msg}";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    MsgFallbackGroupNode mfgn = (MsgFallbackGroupNode) nodes.get(0);
    assertEquals(2, mfgn.numChildren());

    MsgNode mn0 = mfgn.getChild(0);
    assertEquals("msg", mn0.getCommandName());
    assertEquals("verb", mn0.getMeaning());
    assertEquals("Used as a verb.", mn0.getDesc());
    assertEquals("Archive", ((RawTextNode) mn0.getChild(0)).getRawText());

    MsgNode mn1 = mfgn.getChild(1);
    assertEquals("fallbackmsg", mn1.getCommandName());
    assertEquals(null, mn1.getMeaning());
    assertEquals("", mn1.getDesc());
    assertEquals("Archive", ((RawTextNode) mn1.getChild(0)).getRawText());
  }

  @Test
  public void testParseLetStmt() throws Exception {

    String templateBody =
        "{@param boo : ?}\n"
            + "  {let $alpha: $boo.foo /}\n"
            + "  {let $beta kind=\"html\"}Boo!{/let}\n"
            + "  {let $gamma kind=\"html\"}\n"
            + "    {for $i in range($alpha)}\n"
            + "      {$i}{$beta}\n"
            + "    {/for}\n"
            + "  {/let}\n"
            + "  {let $delta kind=\"html\"}Boo!{/let}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(4, nodes.size());

    LetValueNode alphaNode = (LetValueNode) nodes.get(0);
    assertEquals("alpha", alphaNode.getVarName());
    assertEquals("$boo.foo", alphaNode.getExpr().toSourceString());
    LetContentNode betaNode = (LetContentNode) nodes.get(1);
    assertEquals("beta", betaNode.getVarName());
    assertEquals("Boo!", ((RawTextNode) betaNode.getChild(0)).getRawText());
    assertEquals(SanitizedContentKind.HTML, betaNode.getContentKind());
    LetContentNode gammaNode = (LetContentNode) nodes.get(2);
    assertEquals("gamma", gammaNode.getVarName());
    assertThat(gammaNode.getChild(0)).isInstanceOf(ForNode.class);
    assertEquals(SanitizedContentKind.HTML, gammaNode.getContentKind());
    LetContentNode deltaNode = (LetContentNode) nodes.get(3);
    assertEquals("delta", deltaNode.getVarName());
    assertEquals("Boo!", ((RawTextNode) betaNode.getChild(0)).getRawText());
    assertEquals(SanitizedContentKind.HTML, deltaNode.getContentKind());

    // Test error case.
    TemplateSubject.assertThatTemplateContent("{let $alpha /}{/let}")
        .causesError("parse error at '/}': expected }, identifier, or ':'")
        .at(1, 13);

    // Test error case.
    TemplateSubject.assertThatTemplateContent("{let $alpha: $boo.foo}{/let}")
        .causesError(
            "parse error at '}': expected /}, ?, '?:', or, and, ==, !=, <, >, <=, >=, +, -, *, /, "
                + "%, ., ?., [, or ?[")
        .at(1, 22);
  }

  @Test
  public void testParseIfStmt() throws Exception {

    String templateBody =
        "{@param zoo : ?}{@param boo: ?}{@param foo : ?}{@param moo : ?}\n"
            + "  {if $zoo}{$zoo}{/if}\n"
            + "  {if $boo}\n"
            + "    Blah\n"
            + "  {elseif $foo.goo > 2}\n"
            + "    {$moo}\n"
            + "  {else}\n"
            + "    Blah {$moo}\n"
            + "  {/if}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(2, nodes.size());

    IfNode in0 = (IfNode) nodes.get(0);
    assertEquals(1, in0.numChildren());
    IfCondNode in0icn0 = (IfCondNode) in0.getChild(0);
    assertEquals("$zoo", in0icn0.getExpr().toSourceString());
    assertEquals(1, in0icn0.numChildren());
    assertEquals("$zoo", ((PrintNode) in0icn0.getChild(0)).getExpr().toSourceString());
    assertTrue(in0icn0.getExpr().getRoot() instanceof VarRefNode);

    IfNode in1 = (IfNode) nodes.get(1);
    assertEquals(3, in1.numChildren());
    IfCondNode in1icn0 = (IfCondNode) in1.getChild(0);
    assertEquals("$boo", in1icn0.getExpr().toSourceString());
    assertTrue(in1icn0.getExpr().getRoot() instanceof VarRefNode);
    IfCondNode in1icn1 = (IfCondNode) in1.getChild(1);
    assertEquals("$foo.goo > 2", in1icn1.getExpr().toSourceString());
    assertTrue(in1icn1.getExpr().getRoot() instanceof GreaterThanOpNode);
    assertEquals(
        "{if $boo}Blah{elseif $foo.goo > 2}{$moo}{else}Blah {$moo}{/if}", in1.toSourceString());
  }

  @Test
  public void testParseSwitchStmt() throws Exception {

    String templateBody =
        "{@param boo: ?}{@param foo : ?}{@param moo : ?}\n"
            + "  {switch $boo} {case 0}Blah\n"
            + "    {case $foo.goo}\n"
            + "      Bleh\n"
            + "    {case -1, 1, $moo}\n"
            + "      Bluh\n"
            + "    {default}\n"
            + "      Bloh\n"
            + "  {/switch}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    SwitchNode sn = (SwitchNode) nodes.get(0);
    assertEquals("$boo", sn.getExpr().toSourceString());
    assertTrue(sn.getExpr().getRoot() instanceof VarRefNode);
    assertEquals(4, sn.numChildren());

    SwitchCaseNode scn0 = (SwitchCaseNode) sn.getChild(0);
    assertEquals(1, scn0.getExprList().size());
    assertTrue(scn0.getExprList().get(0).getRoot() instanceof IntegerNode);
    assertEquals(0, ((IntegerNode) scn0.getExprList().get(0).getRoot()).getValue());

    SwitchCaseNode scn1 = (SwitchCaseNode) sn.getChild(1);
    assertEquals(1, scn1.getExprList().size());
    assertTrue(scn1.getExprList().get(0).getRoot() instanceof FieldAccessNode);
    assertEquals("$foo.goo", scn1.getExprList().get(0).getRoot().toSourceString());

    SwitchCaseNode scn2 = (SwitchCaseNode) sn.getChild(2);
    assertEquals(3, scn2.getExprList().size());
    assertTrue(scn2.getExprList().get(0).getRoot() instanceof IntegerNode);
    assertTrue(scn2.getExprList().get(1).getRoot() instanceof IntegerNode);
    assertTrue(scn2.getExprList().get(2).getRoot() instanceof VarRefNode);
    assertEquals("-1", scn2.getExprList().get(0).getRoot().toSourceString());
    assertEquals("1", scn2.getExprList().get(1).getRoot().toSourceString());
    assertEquals("$moo", scn2.getExprList().get(2).getRoot().toSourceString());
    assertEquals("Bluh", ((RawTextNode) scn2.getChild(0)).getRawText());

    assertEquals(
        "Bloh", ((RawTextNode) ((SwitchDefaultNode) sn.getChild(3)).getChild(0)).getRawText());

    assertEquals(
        "{switch $boo}{case 0}Blah{case $foo.goo}Bleh{case -1, 1, $moo}Bluh{default}Bloh{/switch}",
        sn.toSourceString());
  }

  @Test
  public void testParseForeachStmt() throws Exception {

    String templateBody =
        "{@param goose : ?}{@param foo: ?}\n"
            + "  {for $goo in $goose}\n"
            + "    {$goose.numKids} goslings.{\\n}\n"
            + "  {/for}\n"
            + "  {for $boo in $foo.booze}\n"
            + "    Scary drink {$boo.name}!\n"
            + "    {if not isLast($boo)}{\\n}{/if}\n"
            + "  {ifempty}\n"
            + "    Sorry, no booze.\n"
            + "  {/for}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(2, nodes.size());

    ForNode fn0 = (ForNode) nodes.get(0);
    assertEquals("$goose", fn0.getExpr().toSourceString());
    assertTrue(fn0.getExpr().getRoot() instanceof VarRefNode);
    assertEquals(1, fn0.numChildren());

    ForNonemptyNode fn0fnn0 = (ForNonemptyNode) fn0.getChild(0);
    assertEquals("goo", fn0fnn0.getVarName());
    assertEquals(2, fn0fnn0.numChildren());
    assertEquals("$goose.numKids", ((PrintNode) fn0fnn0.getChild(0)).getExpr().toSourceString());
    assertEquals(" goslings.\n", ((RawTextNode) fn0fnn0.getChild(1)).getRawText());

    ForNode fn1 = (ForNode) nodes.get(1);
    assertEquals("$foo.booze", fn1.getExpr().toSourceString());
    assertTrue(fn1.getExpr().getRoot() instanceof FieldAccessNode);
    assertEquals(2, fn1.numChildren());

    ForNonemptyNode fn1fnn0 = (ForNonemptyNode) fn1.getChild(0);
    assertEquals("boo", fn1fnn0.getVarName());
    assertEquals("$foo.booze", fn1fnn0.getExpr().toSourceString());
    assertEquals("boo", fn1fnn0.getVarName());
    assertEquals(4, fn1fnn0.numChildren());
    IfNode fn1fnn0in = (IfNode) fn1fnn0.getChild(3);
    assertEquals(1, fn1fnn0in.numChildren());
    assertEquals(
        "not isLast($boo)", ((IfCondNode) fn1fnn0in.getChild(0)).getExpr().toSourceString());

    ForIfemptyNode fn1fin1 = (ForIfemptyNode) fn1.getChild(1);
    assertEquals(1, fn1fin1.numChildren());
    assertEquals("Sorry, no booze.", ((RawTextNode) fn1fin1.getChild(0)).getRawText());
  }

  @SuppressWarnings({"ConstantConditions"})
  @Test
  public void testParseBasicCallStmt() throws Exception {

    String templateBody =
        "{@param too : ?}{@param animals: ?}\n"
            + "  {call .booTemplate_ /}\n"
            + "  {call foo.goo.mooTemplate data=\"all\" /}\n"
            + "  {call .booTemplate_ /}\n"
            + "  {call .zooTemplate data=\"$animals\"}\n"
            + "    {param yoo: round($too) /}\n"
            + "    {param woo kind=\"html\"}poo{/param}\n"
            + "    {param zoo: 0 /}\n"
            + "    {param doo kind=\"html\"}doopoo{/param}\n"
            + "  {/call}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertThat(nodes).hasSize(4);

    CallBasicNode cn0 = (CallBasicNode) nodes.get(0);
    assertEquals("brittle.test.ns.booTemplate_", cn0.getCalleeName());
    assertEquals(".booTemplate_", cn0.getSourceCalleeName());
    assertEquals(false, cn0.isPassingData());
    assertEquals(false, cn0.isPassingAllData());
    assertEquals(null, cn0.getDataExpr());
    assertEquals("XXX", cn0.genBasePhName());
    assertEquals(0, cn0.numChildren());

    CallBasicNode cn1 = (CallBasicNode) nodes.get(1);
    assertEquals("foo.goo.mooTemplate", cn1.getCalleeName());
    assertEquals("foo.goo.mooTemplate", cn1.getSourceCalleeName());
    assertEquals(true, cn1.isPassingData());
    assertEquals(true, cn1.isPassingAllData());
    assertEquals(null, cn1.getDataExpr());
    assertFalse(cn1.genSamenessKey().equals(cn0.genSamenessKey()));
    assertEquals(0, cn1.numChildren());

    CallBasicNode cn2 = (CallBasicNode) nodes.get(2);
    assertEquals("brittle.test.ns.booTemplate_", cn2.getCalleeName());
    assertEquals(".booTemplate_", cn2.getSourceCalleeName());
    assertFalse(cn2.isPassingData());
    assertEquals(false, cn2.isPassingAllData());
    assertEquals(null, cn2.getDataExpr());
    assertEquals("XXX", cn2.genBasePhName());
    assertEquals(0, cn2.numChildren());

    CallBasicNode cn3 = (CallBasicNode) nodes.get(3);
    assertEquals("brittle.test.ns.zooTemplate", cn3.getCalleeName());
    assertEquals(".zooTemplate", cn3.getSourceCalleeName());
    assertEquals(true, cn3.isPassingData());
    assertEquals(false, cn3.isPassingAllData());
    assertTrue(cn3.getDataExpr().getRoot() != null);
    assertEquals("$animals", cn3.getDataExpr().toSourceString());
    assertEquals(4, cn3.numChildren());

    {
      final CallParamValueNode cn4cpvn0 = (CallParamValueNode) cn3.getChild(0);
      assertEquals("yoo", cn4cpvn0.getKey().identifier());
      assertEquals("round($too)", cn4cpvn0.getExpr().toSourceString());
      assertTrue(cn4cpvn0.getExpr().getRoot() instanceof FunctionNode);
    }

    {
      final CallParamContentNode cn4cpcn1 = (CallParamContentNode) cn3.getChild(1);
      assertEquals("woo", cn4cpcn1.getKey().identifier());
      assertEquals(SanitizedContentKind.HTML, cn4cpcn1.getContentKind());
      assertEquals("poo", ((RawTextNode) cn4cpcn1.getChild(0)).getRawText());
    }

    {
      final CallParamValueNode cn4cpvn2 = (CallParamValueNode) cn3.getChild(2);
      assertEquals("zoo", cn4cpvn2.getKey().identifier());
      assertEquals("0", cn4cpvn2.getExpr().toSourceString());
    }

    {
      final CallParamContentNode cn4cpcn3 = (CallParamContentNode) cn3.getChild(3);
      assertEquals("doo", cn4cpcn3.getKey().identifier());
      assertNotNull(cn4cpcn3.getContentKind());
      assertEquals(SanitizedContentKind.HTML, cn4cpcn3.getContentKind());
      assertEquals("doopoo", ((RawTextNode) cn4cpcn3.getChild(0)).getRawText());
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  @Test
  public void testParseDelegateCallStmt() throws Exception {

    String templateBody =
        "{@param animals : ?}{@param too : ?}\n"
            + "  {delcall booTemplate /}\n"
            + "  {delcall foo.goo.mooTemplate data=\"all\" /}\n"
            + "  {delcall MySecretFeature.zooTemplate data=\"$animals\"}\n"
            + "    {param yoo: round($too) /}\n"
            + "    {param woo kind=\"html\"}poo{/param}\n"
            + "  {/delcall}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(3, nodes.size());

    CallDelegateNode cn0 = (CallDelegateNode) nodes.get(0);
    assertEquals("booTemplate", cn0.getDelCalleeName());
    assertEquals(false, cn0.isPassingData());
    assertEquals(false, cn0.isPassingAllData());
    assertEquals(null, cn0.getDataExpr());
    assertEquals("XXX", cn0.genBasePhName());
    assertEquals(0, cn0.numChildren());

    CallDelegateNode cn1 = (CallDelegateNode) nodes.get(1);
    assertEquals("foo.goo.mooTemplate", cn1.getDelCalleeName());
    assertEquals(true, cn1.isPassingData());
    assertEquals(true, cn1.isPassingAllData());
    assertEquals(null, cn1.getDataExpr());
    assertFalse(cn1.genSamenessKey().equals(cn0.genSamenessKey()));
    assertEquals(0, cn1.numChildren());

    CallDelegateNode cn2 = (CallDelegateNode) nodes.get(2);
    assertEquals("MySecretFeature.zooTemplate", cn2.getDelCalleeName());
    assertEquals(true, cn2.isPassingData());
    assertEquals(false, cn2.isPassingAllData());
    assertTrue(cn2.getDataExpr().getRoot() != null);
    assertEquals("$animals", cn2.getDataExpr().toSourceString());
    assertEquals(2, cn2.numChildren());

    CallParamValueNode cn2cpvn0 = (CallParamValueNode) cn2.getChild(0);
    assertEquals("yoo", cn2cpvn0.getKey().identifier());
    assertEquals("round($too)", cn2cpvn0.getExpr().toSourceString());
    assertTrue(cn2cpvn0.getExpr().getRoot() instanceof FunctionNode);

    CallParamContentNode cn2cpcn1 = (CallParamContentNode) cn2.getChild(1);
    assertEquals("woo", cn2cpcn1.getKey().identifier());
    assertEquals("poo", ((RawTextNode) cn2cpcn1.getChild(0)).getRawText());
  }

  @SuppressWarnings({"ConstantConditions"})
  @Test
  public void testParseCallStmtWithPhname() throws Exception {

    String templateBody =
        "{@param animals:?}\n"
            + "{msg desc=\"...\"}\n"
            + "  {call .booTemplate_ phname=\"booTemplate_\" /}\n"
            + "  {call .booTemplate_ phname=\"booTemplate_\" /}\n"
            + "  {delcall MySecretFeature.zooTemplate data=\"$animals\" phname=\"secret_zoo\"}\n"
            + "    {param zoo: 0 /}\n"
            + "  {/delcall}\n"
            + "{/msg}";

    List<StandaloneNode> nodes =
        ((MsgFallbackGroupNode) parseTemplateContent(templateBody, FAIL).getChild(0))
            .getChild(0)
            .getChildren();
    assertEquals(3, nodes.size());

    CallBasicNode cn0 = (CallBasicNode) ((MsgPlaceholderNode) nodes.get(0)).getChild(0);
    assertEquals("BOO_TEMPLATE", cn0.genBasePhName());
    assertEquals("brittle.test.ns.booTemplate_", cn0.getCalleeName());
    assertEquals(".booTemplate_", cn0.getSourceCalleeName());
    assertEquals(false, cn0.isPassingData());
    assertEquals(false, cn0.isPassingAllData());
    assertEquals(null, cn0.getDataExpr());
    assertEquals(0, cn0.numChildren());

    CallBasicNode cn1 = (CallBasicNode) ((MsgPlaceholderNode) nodes.get(1)).getChild(0);

    CallDelegateNode cn2 = (CallDelegateNode) ((MsgPlaceholderNode) nodes.get(2)).getChild(0);
    assertEquals("SECRET_ZOO", cn2.genBasePhName());
    assertEquals("MySecretFeature.zooTemplate", cn2.getDelCalleeName());
    assertEquals(true, cn2.isPassingData());
    assertEquals(false, cn2.isPassingAllData());
    assertTrue(cn2.getDataExpr().getRoot() != null);
    assertEquals("$animals", cn2.getDataExpr().toSourceString());
    assertEquals(1, cn2.numChildren());

    assertFalse(cn0.genSamenessKey().equals(cn1.genSamenessKey())); // CallNodes are never same
    assertFalse(cn2.genSamenessKey().equals(cn0.genSamenessKey()));
  }

  @Test
  public void testParseLogStmt() throws Exception {

    String templateBody = "{@param foo : ?}\n" + "{log}Blah {$foo}.{/log}";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    LogNode logNode = (LogNode) nodes.get(0);
    assertEquals(3, logNode.numChildren());
    assertEquals("Blah ", ((RawTextNode) logNode.getChild(0)).getRawText());
    assertEquals("$foo", ((PrintNode) logNode.getChild(1)).getExpr().toSourceString());
  }

  @Test
  public void testParseDebuggerStmt() throws Exception {

    String templateBody = "{debugger}";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    assertTrue(nodes.get(0) instanceof DebuggerNode);
  }

  // -----------------------------------------------------------------------------------------------
  // Tests for plural/select messages.

  @Test
  public void testParseMsgStmtWithPlural() throws Exception {
    String templateBody =
        "{@param num_people : ?}{@param person : ?}{@param place : ?}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($num_people)} "
            + "other people in {$place}.\n"
            + "    {/plural}"
            + "  {/msg}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    MsgNode mn = ((MsgFallbackGroupNode) nodes.get(0)).getChild(0);
    assertEquals(1, mn.numChildren());
    assertEquals("A sample plural message", mn.getDesc());

    MsgPluralNode pn = (MsgPluralNode) mn.getChild(0);
    assertEquals("$num_people", pn.getExpr().toSourceString());
    assertEquals(1, pn.getOffset());
    assertEquals(4, pn.numChildren()); // 3 cases and default

    // Case 0
    MsgPluralCaseNode cn0 = (MsgPluralCaseNode) pn.getChild(0);
    assertEquals(3, cn0.numChildren());
    assertEquals(0, cn0.getCaseNumber());

    RawTextNode rtn01 = (RawTextNode) cn0.getChild(0);
    assertEquals("I see no one in ", rtn01.getRawText());

    MsgPlaceholderNode phn01 = (MsgPlaceholderNode) cn0.getChild(1);
    assertEquals("{$place}", phn01.toSourceString());

    RawTextNode rtn02 = (RawTextNode) cn0.getChild(2);
    assertEquals(".", rtn02.getRawText());

    // Case 1
    MsgPluralCaseNode cn1 = (MsgPluralCaseNode) pn.getChild(1);
    assertEquals(5, cn1.numChildren());
    assertEquals(1, cn1.getCaseNumber());

    RawTextNode rtn11 = (RawTextNode) cn1.getChild(0);
    assertEquals("I see ", rtn11.getRawText());

    MsgPlaceholderNode phn11 = (MsgPlaceholderNode) cn1.getChild(1);
    assertEquals("{$person}", phn11.toSourceString());

    RawTextNode rtn12 = (RawTextNode) cn1.getChild(2);
    assertEquals(" in ", rtn12.getRawText());

    MsgPlaceholderNode phn12 = (MsgPlaceholderNode) cn1.getChild(3);
    assertEquals("{$place}", phn12.toSourceString());

    RawTextNode rtn13 = (RawTextNode) cn1.getChild(4);
    assertEquals(".", rtn13.getRawText());

    // Case 2
    MsgPluralCaseNode cn2 = (MsgPluralCaseNode) pn.getChild(2);
    assertEquals(5, cn2.numChildren());
    assertEquals(2, cn2.getCaseNumber());

    RawTextNode rtn21 = (RawTextNode) cn2.getChild(0);
    assertEquals("I see ", rtn21.getRawText());

    MsgPlaceholderNode phn21 = (MsgPlaceholderNode) cn2.getChild(1);
    assertEquals("{$person}", phn21.toSourceString());

    RawTextNode rtn22 = (RawTextNode) cn2.getChild(2);
    assertEquals(" and one other person in ", rtn22.getRawText());

    MsgPlaceholderNode phn22 = (MsgPlaceholderNode) cn2.getChild(3);
    assertEquals("{$place}", phn22.toSourceString());

    RawTextNode rtn23 = (RawTextNode) cn2.getChild(4);
    assertEquals(".", rtn23.getRawText());

    // Default
    MsgPluralDefaultNode dn = (MsgPluralDefaultNode) pn.getChild(3);
    assertEquals(7, dn.numChildren());

    RawTextNode rtnd1 = (RawTextNode) dn.getChild(0);
    assertEquals("I see ", rtnd1.getRawText());

    MsgPlaceholderNode phnd1 = (MsgPlaceholderNode) dn.getChild(1);
    assertEquals("{$person}", phnd1.toSourceString());

    RawTextNode rtnd2 = (RawTextNode) dn.getChild(2);
    assertEquals(" and ", rtnd2.getRawText());

    MsgPlaceholderNode phnd2 = (MsgPlaceholderNode) dn.getChild(3);
    assertEquals("{$num_people - 1}", phnd2.toSourceString());

    RawTextNode rtnd3 = (RawTextNode) dn.getChild(4);
    assertEquals(" other people in ", rtnd3.getRawText());

    MsgPlaceholderNode phnd3 = (MsgPlaceholderNode) dn.getChild(5);
    assertEquals("{$place}", phnd3.toSourceString());

    RawTextNode rtnd4 = (RawTextNode) dn.getChild(6);
    assertEquals(".", rtnd4.getRawText());
  }

  @Test
  public void testParseMsgStmtWithSelect() throws Exception {
    String templateBody =
        "{@param gender : ?}{@param person : ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "{/msg}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    MsgNode mn = ((MsgFallbackGroupNode) nodes.get(0)).getChild(0);
    assertEquals(1, mn.numChildren());
    assertEquals("A sample gender message", mn.getDesc());

    MsgSelectNode sn = (MsgSelectNode) mn.getChild(0);
    assertEquals("$gender", sn.getExpr().toSourceString());
    assertEquals(2, sn.numChildren()); // female and default

    // Case 'female'
    MsgSelectCaseNode cnf = (MsgSelectCaseNode) sn.getChild(0);
    assertEquals("female", cnf.getCaseValue());
    assertEquals(2, cnf.numChildren());

    MsgPlaceholderNode phnf = (MsgPlaceholderNode) cnf.getChild(0);
    assertEquals("{$person}", phnf.toSourceString());

    RawTextNode rtnf = (RawTextNode) cnf.getChild(1);
    assertEquals(" added you to her circle.", rtnf.getRawText());

    // Default
    MsgSelectDefaultNode dn = (MsgSelectDefaultNode) sn.getChild(1);
    assertEquals(2, dn.numChildren());

    MsgPlaceholderNode phnd = (MsgPlaceholderNode) dn.getChild(0);
    assertEquals("{$person}", phnd.toSourceString());

    RawTextNode rtnd = (RawTextNode) dn.getChild(1);
    assertEquals(" added you to his circle.", rtnd.getRawText());
  }

  @Test
  public void testParseMsgStmtWithNestedSelects() throws Exception {
    String templateBody =
        "{@param gender1 : ?}{@param gender2: ?}{@param person1 : ?}{@param person2: ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $gender1}\n"
            + "    {case 'female'}\n"
            + "      {select $gender2}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to her circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $gender2}\n"
            + "        {case 'female'}{$person1} put {$person2} and her friends to his circle.\n"
            + "        {default}{$person1} put {$person2} and his friends to his circle.\n"
            + "      {/select}\n"
            + "  {/select}\n"
            + "{/msg}\n";

    List<StandaloneNode> nodes = parseTemplateContent(templateBody, FAIL).getChildren();
    assertEquals(1, nodes.size());

    MsgNode mn = ((MsgFallbackGroupNode) nodes.get(0)).getChild(0);
    assertEquals(1, mn.numChildren());
    assertEquals("A sample nested message", mn.getDesc());

    // Outer select
    MsgSelectNode sn = (MsgSelectNode) mn.getChild(0);
    assertEquals("$gender1", sn.getExpr().toSourceString());
    assertEquals(2, sn.numChildren()); // female and default

    // Outer select: Case 'female'
    MsgSelectCaseNode cnf = (MsgSelectCaseNode) sn.getChild(0);
    assertEquals("female", cnf.getCaseValue());
    assertEquals(1, cnf.numChildren()); // Another select

    // Outer select: Case 'female': Inner select
    MsgSelectNode sn2 = (MsgSelectNode) cnf.getChild(0);
    assertEquals("$gender2", sn2.getExpr().toSourceString());
    assertEquals(2, sn2.numChildren()); // female and default

    // Outer select: Case 'female': Inner select: Case 'female'
    MsgSelectCaseNode cnf2 = (MsgSelectCaseNode) sn2.getChild(0);
    assertEquals("female", cnf2.getCaseValue());
    assertEquals(4, cnf2.numChildren());

    // Outer select: Case 'female': Inner select: Case 'female': Placeholder $person1
    MsgPlaceholderNode phn1 = (MsgPlaceholderNode) cnf2.getChild(0);
    assertEquals("{$person1}", phn1.toSourceString());

    // Outer select: Case 'female': Inner select: Case 'female': RawText
    RawTextNode rtn1 = (RawTextNode) cnf2.getChild(1);
    assertEquals(" added ", rtn1.getRawText());

    // Outer select: Case 'female': Inner select: Case 'female': Placeholder $person2
    MsgPlaceholderNode phn2 = (MsgPlaceholderNode) cnf2.getChild(2);
    assertEquals("{$person2}", phn2.toSourceString());

    // Outer select: Case 'female': Inner select: Case 'female': RawText
    RawTextNode rtn2 = (RawTextNode) cnf2.getChild(3);
    assertEquals(" and her friends to her circle.", rtn2.getRawText());

    // Outer select: Case 'female': Inner select: Default
    MsgSelectDefaultNode dn2 = (MsgSelectDefaultNode) sn2.getChild(1);
    assertEquals(4, dn2.numChildren());

    // Outer select: Case 'female': Inner select: Default: Placeholder $person1
    MsgPlaceholderNode phn21 = (MsgPlaceholderNode) dn2.getChild(0);
    assertEquals("{$person1}", phn21.toSourceString());

    // Outer select: Case 'female': Inner select: Default: RawText
    RawTextNode rtn21 = (RawTextNode) dn2.getChild(1);
    assertEquals(" added ", rtn21.getRawText());

    // Outer select: Case 'female': Inner select: Default: Placeholder $person2
    MsgPlaceholderNode phn22 = (MsgPlaceholderNode) dn2.getChild(2);
    assertEquals("{$person2}", phn22.toSourceString());

    // Outer select: Case 'female': Inner select: Default: RawText
    RawTextNode rtn22 = (RawTextNode) dn2.getChild(3);
    assertEquals(" and his friends to her circle.", rtn22.getRawText());

    // Outer select: Default
    MsgSelectDefaultNode dn = (MsgSelectDefaultNode) sn.getChild(1);
    assertEquals(1, dn.numChildren()); // Another select

    // Outer select: Default: Inner select
    MsgSelectNode sn3 = (MsgSelectNode) dn.getChild(0);
    assertEquals("$gender2", sn3.getExpr().toSourceString());
    assertEquals(2, sn3.numChildren()); // female and default

    // Outer select: Default: Inner select: Case 'female'
    MsgSelectCaseNode cnf3 = (MsgSelectCaseNode) sn3.getChild(0);
    assertEquals("female", cnf3.getCaseValue());
    assertEquals(4, cnf3.numChildren());

    // Outer select: Default: Inner select: Case 'female': Placeholder $person1
    MsgPlaceholderNode phn3 = (MsgPlaceholderNode) cnf3.getChild(0);
    assertEquals("{$person1}", phn3.toSourceString());

    // Outer select: Default: Inner select: Case 'female': RawText
    RawTextNode rtn3 = (RawTextNode) cnf3.getChild(1);
    assertEquals(" put ", rtn3.getRawText());

    // Outer select: Default: Inner select: Case 'female': Placeholder $person2
    MsgPlaceholderNode phn4 = (MsgPlaceholderNode) cnf3.getChild(2);
    assertEquals("{$person2}", phn4.toSourceString());

    // Outer select: Default: Inner select: Case 'female': RawText
    RawTextNode rtn4 = (RawTextNode) cnf3.getChild(3);
    assertEquals(" and her friends to his circle.", rtn4.getRawText());

    // Outer select: Default: Inner select: Default
    MsgSelectDefaultNode dn3 = (MsgSelectDefaultNode) sn3.getChild(1);
    assertEquals(4, dn3.numChildren());

    // Outer select: Default: Inner select: Default: Placeholder $person1
    MsgPlaceholderNode phn5 = (MsgPlaceholderNode) dn3.getChild(0);
    assertEquals("{$person1}", phn5.toSourceString());

    // Outer select: Default: Inner select: Default: RawText
    RawTextNode rtn5 = (RawTextNode) dn3.getChild(1);
    assertEquals(" put ", rtn5.getRawText());

    // Outer select: Default: Inner select: Default: Placeholder $person2
    MsgPlaceholderNode phn6 = (MsgPlaceholderNode) dn3.getChild(2);
    assertEquals("{$person2}", phn6.toSourceString());

    // Outer select: Default: Inner select: Default: RawText
    RawTextNode rtn6 = (RawTextNode) dn3.getChild(3);
    assertEquals(" and his friends to his circle.", rtn6.getRawText());
  }

  @Test
  public void testMultipleErrors() throws ParseException {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parseTemplateContent(
        "{call .123 /}\n" // Invalid callee name
            + "{delcall 456 /}\n" // Invalid callee name
            + "{for foo in bar}{/for}\n" // Invalid foreach var
            + "{let /}\n", // Missing let var
        errorReporter);
    List<SoyError> errors = errorReporter.getErrors();
    assertThat(errors).hasSize(4);
    assertThat(errors.get(0).message()).isEqualTo("parse error at '1': expected identifier");
    assertThat(errors.get(1).message()).isEqualTo("parse error at '4': expected identifier or .");
    assertThat(errors.get(2).message()).isEqualTo("parse error at 'foo': expected variable");
    assertThat(errors.get(3).message()).isEqualTo("parse error at '/}': expected variable");
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /**
   * Parses the given input as a template content (header and body).
   *
   * @param input The input string to parse.
   * @throws TokenMgrError When the given input has a token error.
   * @return The decl infos and parse tree nodes created.
   */
  private static TemplateNode parseTemplateContent(String input, ErrorReporter errorReporter) {
    String soyFile = SharedTestUtils.buildTestSoyFileContent(ImmutableList.<String>of(), input);

    SoyFileSetNode fileSet =
        SoyFileSetParserBuilder.forFileContents(soyFile)
            .errorReporter(errorReporter)
            .parse()
            .fileSet();
    return fileSet.numChildren() > 0 ? fileSet.getChild(0).getChild(0) : null;
  }

  /**
   * Asserts that the given input is a valid template, running all parsing phases.
   *
   * @param input The input string to parse.
   */
  private static TemplateNode assertValidTemplate(String input) {
    return assertValidTemplate(SanitizedContentKind.HTML, input);
  }

  private static TemplateNode assertValidTemplate(SanitizedContentKind kind, String input) {
    return assertValidTemplate(kind, "", input);
  }

  private static TemplateNode assertValidTemplate(
      SanitizedContentKind kind, String namespaceAttrs, String input) {
    StringBuilder soyFileContentBuilder = new StringBuilder();
    soyFileContentBuilder
        .append("{namespace brittle.test.ns ")
        .append(namespaceAttrs)
        .append("}\n\n")
        .append("{template .brittleTestTemplate")
        .append(
            kind == SanitizedContentKind.HTML
                ? ""
                : " kind=\"" + Ascii.toLowerCase(kind.toString()) + '"')
        .append("}\n")
        .append(input)
        .append("\n{/template}\n");
    return SoyFileSetParserBuilder.forFileContents(soyFileContentBuilder.toString())
        .parse()
        .fileSet()
        .getChild(0)
        .getChild(0);
  }

  private static void assertInvalidTemplate(String input) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parseTemplateContent(input, errorReporter);
    assertThat(errorReporter.getErrors()).isNotEmpty();
  }

  private static void assertInvalidTemplate(String input, String expectedErrorMessage) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parseTemplateContent(input, errorReporter);
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(errorReporter.getErrors().get(0).message()).contains(expectedErrorMessage);
  }
}
