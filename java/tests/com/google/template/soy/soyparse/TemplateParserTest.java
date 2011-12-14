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

import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.base.IncrementingIdGenerator;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
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
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyNode.SyntaxVersion;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.util.List;


/**
 * Unit tests for the template parser.
 *
 */
public class TemplateParserTest extends TestCase {


  public void testRecognizeSoyTag() throws Exception {

    assertIsTemplateBody("{sp}");
    assertIsTemplateBody("{space}");
    assertIsTemplateBody("{ sp }");
    assertIsTemplateBody("{{sp}}");
    assertIsTemplateBody("{{space}}");
    assertIsTemplateBody("{{ {sp} }}");
    assertIsTemplateBody("{{ {} }}");
    assertIsTemplateBody("{{ }s{p  { }}");

    assertIsNotTemplateBody("{}");
    assertIsNotTemplateBody("{sp");
    assertIsNotTemplateBody("{sp blah}");
    assertIsNotTemplateBody("{print { }");
    assertIsNotTemplateBody("{print } }");
    assertIsNotTemplateBody("{print }}");
    assertIsNotTemplateBody("{{}}");
    assertIsNotTemplateBody("{{{blah: blah}}}");
    assertIsNotTemplateBody("blah}blah");
    assertIsNotTemplateBody("blah}}blah");
    assertIsNotTemplateBody("{{print {{ }}");
    assertIsNotTemplateBody("{{print {}}");
  }


  public void testRecognizeRawText() throws Exception {

    assertIsTemplateBody("blah>blah<blah<blah>blah>blah>blah>blah<blah");
    assertIsTemplateBody("{sp}{nil}{\\n}{{\\r}}{\\t}{lb}{{rb}}");
    assertIsTemplateBody("blah{literal}{ {{{ } }{ {}} { }}}}}}}\n" +
                         "}}}}}}}}}{ { {{/literal}blah");

    assertIsNotTemplateBody("{sp ace}");
    assertIsNotTemplateBody("{/literal}");
    assertIsNotTemplateBody("{literal attrib=\"value\"}");
    assertIsNotTemplateBody("{literal}{literal}{/literal}");
  }


  public void testRecognizeCommands() throws Exception {

    assertIsTemplateBody("" +
        "{msg desc=\"blah\" hidden=\"true\"}\n" +
        "  {$boo} is a <a href=\"{$fooUrl}\">{$foo}</a>.\n" +
        "{/msg}");
    assertIsTemplateBody("{$aaa + 1}{print $bbb.ccc[$ddd] |noescape}");
    assertIsTemplateBody("{css selected-option}{css CSS_SELECTED_OPTION}{css $cssSelectedOption}");
    assertIsTemplateBody("{if $boo}foo{elseif $goo}moo{else}zoo{/if}");
    assertIsTemplateBody("" +
        "  {switch $boo}\n" +
        "    {case $foo} blah blah\n" +
        "    {case 2, $goo.moo, 'too'} bleh bleh\n" +
        "    {default} bluh bluh\n" +
        "  {/switch}\n");
    assertIsTemplateBody("{foreach $item in $items}{index($item)}. {$item.name}<br>{/foreach}");
    assertIsTemplateBody("" +
        "{for $i in range($boo + 1,\n" +
        "                 88, 11)}\n" +
        "Number {$i}.{{/for}}");
    assertIsTemplateBody("{call function=\"aaa.bbb.ccc\" data=\"all\" /}");
    assertIsTemplateBody("" +
        "{call name=\".aaa\"}\n" +
        "  {{param key=\"boo\" value=\"$boo\" /}}\n" +
        "  {param key=\"foo\"}blah blah{/param}\n" +
        "{/call}");
    assertIsTemplateBody("{call aaa.bbb.ccc data=\"all\" /}");
    assertIsTemplateBody("" +
        "{call .aaa}\n" +
        "  {{param key=\"boo\" value=\"$boo\" /}}\n" +
        "  {param key=\"foo\"}blah blah{/param}\n" +
        "{/call}");
    assertIsTemplateBody("{delcall aaa.bbb.ccc data=\"all\" /}");
    assertIsTemplateBody("" +
        "{delcall name=\"ddd.eee\"}\n" +
        "  {{param key=\"boo\" value=\"$boo\" /}}\n" +
        "  {param key=\"foo\"}blah blah{/param}\n" +
        "{/delcall}");
    assertIsTemplateBody("" +
        "{msg meaning=\"boo\" desc=\"blah\"}\n" +
        "  {$boo phname=\"foo\"} is a \n" +
        "  <a phname=\"begin_link\" href=\"{$fooUrl}\">\n" +
        "    {$foo |noAutoescape phname=\"booFoo\" }\n" +
        "  </a phname=\"END_LINK\" >.\n" +
        "  {call .aaa data=\"all\"\nphname=\"AaaBbb\"/}\n" +
        "  {call .aaa phname=\"AaaBbb\" data=\"all\"}{/call}\n" +
        "{/msg}");

    assertIsNotTemplateBody("{msg}blah{/msg}");
    assertIsNotTemplateBody("{/msg}");
    assertIsNotTemplateBody("{msg desc=\"\"}<a href=http://www.google.com{/msg}");
    assertIsNotTemplateBody("{msg desc=\"\"}blah{msg desc=\"\"}bleh{/msg}bluh{/msg}");
    assertIsNotTemplateBody("{msg desc=\"\"}blah{/msg blah}");
    assertIsNotTemplateBody("{namespace}");
    assertIsNotTemplateBody("{template}\n" + "blah\n" + "{/template}\n");
    assertIsNotTemplateBody("{msg}<blah<blah>{/msg}");
    assertIsNotTemplateBody("{msg}blah>blah{/msg}");
    assertIsNotTemplateBody("{msg}<blah>blah>{/msg}");
    assertIsNotTemplateBody("{print $boo /}");
    assertIsNotTemplateBody("{if true}aaa{else/}bbb{/if}");
    assertIsNotTemplateBody("{call .aaa.bbb /}");
    assertIsNotTemplateBody("{delcall name=\"ddd.eee\"}{param foo: 0}{/call}");
    assertIsNotTemplateBody("{delcall .dddEee /}");
    assertIsNotTemplateBody("{msg desc=\"\"}{$boo phname=\"boo.foo\"}{/msg}");
    assertIsNotTemplateBody("{msg desc=\"\"}<br phname=\"boo-foo\" />{/msg}");
    assertIsNotTemplateBody("{msg desc=\"\"}{call .boo phname=\"boo\" phname=\"boo\" /}{/msg}");
    assertIsNotTemplateBody("{msg desc=\"\"}<br phname=\"break\" phname=\"break\" />{/msg}");
  }


  public void testRecognizeComments() throws Exception {

    assertIsTemplateBody("blah // }\n" +
                         "{$boo}{msg desc=\"\"}//}\n" +
                         "{/msg} // {/msg}\n" +
                         "{foreach $item in $items} // }\n" +
                         "{$item.name}{/foreach}//{{{{\n");
    assertIsTemplateBody("blah /* } */\n" +
                         "{msg desc=\"\"} /*}*/{$boo}\n" +
                         "/******************/ {/msg}\n" +
                         "/* {}} { }* }* / }/ * { **}  //}{ { } {\n" +
                         "\n  } {//*} {* /} { /* /}{} {}/ } **}}} */\n" +
                         "{foreach $item in $items} /* }\n" +
                         "{{{{{*/{$item.name}{/foreach}/*{{{{*/\n");

    assertIsNotTemplateBody("{blah /* { */ blah}");
    assertIsNotTemplateBody("{foreach $item // }\n" +
                            "         in $items}\n" +
                            "{$item}{/foreach}\n");
  }


  public void testRecognizeMsgPlural() throws Exception {
    // Normal, valid plural message.
    assertIsTemplateBody(
        "  {msg desc=\"A sample plural message\"}\n" +
        "    {plural $num_people offset=\"1\"}\n" +
        "      {case 0}I see no one in {$place}.\n" +
        "      {case 1}I see {$person} in {$place}.\n" +
        "      {case 2}I see {$person} and one other person in {$place}.\n" +
        "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n" +
        "    {/plural}\n" +
        "  {/msg}\n");

    // Offset is optional.
    assertIsTemplateBody(
        "  {msg desc=\"A sample plural message\"}\n" +
        "    {plural $num_people}\n" +
        "      {case 0}I see no one in {$place}.\n" +
        "      {case 1}I see {$person} in {$place}.\n" +
        "      {default}I see {$num_people} in {$place}, including {$person}.\n" +
        "    {/plural}\n" +
        "  {/msg}\n");

    // Plural message should have a default clause.
    assertIsNotTemplateBody(
        "  {msg desc=\"A sample plural message\"}\n" +
        "    {plural $num_people offset=\"1\"}\n" +
        "      {case 0}I see no one in {$place}.\n" +
        "      {case 1}I see {$person} in {$place}.\n" +
        "      {case 2}I see {$person} and one other person in {$place}.\n" +
        "    {/plural}\n" +
        "  {/msg}\n");

    // default should be the last clause, after all cases.
    assertIsNotTemplateBody(
        "  {msg desc=\"A sample plural message\"}\n" +
        "    {plural $num_people offset=\"1\"}\n" +
        "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n" +
        "      {case 1}I see {$person} in {$place}.\n" +
        "      {case 0}I see no one in {$place}.\n" +
        "      {case 2}I see {$person} and one other person in {$place}.\n" +
        "    {/plural}\n" +
        "  {/msg}\n");

    // Order is irrelevant for cases.
    assertIsTemplateBody(
        "  {msg desc=\"A sample plural message\"}\n" +
        "    {plural $num_people offset=\"1\"}\n" +
        "      {case 1}I see {$person} in {$place}.\n" +
        "      {case 0}I see no one in {$place}.\n" +
        "      {case 2}I see {$person} and one other person in {$place}.\n" +
        "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n" +
        "    {/plural}\n" +
        "  {/msg}\n");

    // Offset should not be less than 0.
    assertIsNotTemplateBody(
        "  {msg desc=\"A sample plural message\"}\n" +
        "    {plural $num_people offset=\"-1\"}\n" +
        "      {case 0}I see no one in {$place}.\n" +
        "      {case 1}I see {$person} in {$place}.\n" +
        "      {case 2}I see {$person} and one other person in {$place}.\n" +
        "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n" +
        "    {/plural}\n" +
        "  {/msg}\n");

     // Case should not be less than 0.
    assertIsNotTemplateBody(
        "  {msg desc=\"A sample plural message\"}\n" +
        "    {plural $num_people offset=\"1\"}\n" +
        "      {case 0}I see no one in {$place}.\n" +
        "      {case -1}I see {$person} in {$place}.\n" +
        "      {case 2}I see {$person} and one other person in {$place}.\n" +
        "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n" +
        "    {/plural}\n" +
        "  {/msg}\n");
  }


  public void testRecognizeMsgSelect() throws Exception {
    assertIsTemplateBody(
        "{msg desc=\"A sample gender message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "    {default}{$person} added you to his circle.\n" +
        "  {/select}\n" +
        "{/msg}\n");

    // Default should be present.
    assertIsNotTemplateBody(
        "  {select $gender}\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "    {default}{$person} added you to his circle.\n" +
        "  {/select}\n");

    // Default should be the last clause.
    assertIsNotTemplateBody(
        "{msg desc=\"A sample gender message\"}\n" +
        "  {select $gender}\n" +
        "    {default}{$person} added you to his circle.\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "  {/select}\n" +
        "{/msg}\n");

    // There is no restriction that 'female' and 'male' should not occur together.
    assertIsTemplateBody(
        "{msg desc=\"A sample gender message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "    {case 'male'}{$person} added you to his circle.\n" +
        "    {default}{$person} added you to his circle.\n" +
        "  {/select}\n" +
        "{/msg}\n");

    // There is no restriction of case keywords. An arbitrary word like 'neuter' is fine.
    assertIsTemplateBody(
        "{msg desc=\"A sample gender message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "    {case 'male'}{$person} added you to his circle.\n" +
        "    {case 'neuter'}{$person} added you to its circle.\n" +
        "    {default}{$person} added you to his circle.\n" +
        "  {/select}\n" +
        "{/msg}\n");

    // It is not possible to have more than one string in a case.
    assertIsNotTemplateBody(
        "{msg desc=\"A sample gender message\"}\n" +
        "  {select $job}\n" +
        "    {case 'hw_engineer', 'sw_engineer'}{$person}, an engineer, liked this.\n" +
        "    {default}{$person} liked this.\n" +
        "  {/select}\n" +
        "{/msg}\n");

    // select should have a default.
    assertIsNotTemplateBody(
        "{msg desc=\"A sample gender message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "    {case 'male'}{$person} added you to his circle.\n" +
        "  {/select}\n" +
        "{/msg}\n");
  }


  public void testRecognizeNestedPluralSelect() throws Exception {
    // Select nested inside select should be allowed.
    assertIsTemplateBody(
        "{msg desc=\"A sample nested message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}\n" +
        "      {select $gender2}\n" +
        "        {case 'female'}{$person1} added {$person2} and her friends to her circle.\n" +
        "        {default}{$person1} added {$person2} and his friends to her circle.\n" +
        "      {/select}\n" +
        "    {default}\n" +
        "      {select $gender2}\n" +
        "        {case 'female'}{$person1} added {$person2} and her friends to his circle.\n" +
        "        {default}{$person1} added {$person2} and his friends to his circle.\n" +
        "      {/select}\n" +
       "  {/select}\n" +
        "{/msg}\n");

    // Plural nested inside select should be allowed.
    assertIsTemplateBody(
        "{msg desc=\"A sample nested message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}\n" +
        "      {plural $num_people}\n" +
        "        {case 1}{$person} added one person to her circle.\n" +
        "        {default}{$person} added {$num_people} to her circle.\n" +
        "      {/plural}\n" +
        "    {default}\n" +
        "      {plural $num_people}\n" +
        "        {case 1}{$person} added one person to his circle.\n" +
        "        {default}{$person} added {$num_people} to his circle.\n" +
        "      {/plural}\n" +
        "  {/select}\n" +
        "{/msg}\n");

    // Plural inside plural should not be allowed.
    assertIsNotTemplateBody(
        "{msg desc=\"A sample nested message\"}\n" +
        "  {plural $n_friends}\n" +
        "    {case 1}\n" +
        "      {plural $n_circles}\n" +
        "        {case 1}You have one friend in one circle.\n" +
        "        {default}You have one friend in {$n_circles} circles.\n" +
        "      {/plural}\n" +
        "    {default}\n" +
        "      {plural $n_circles}\n" +
        "        {case 1}You have {$n_friends} friends in one circle.\n" +
        "        {default}You have {$n_friends} friends in {$n_circles} circles.\n" +
        "      {/plural}\n" +
        "  {/plural}\n" +
        "{/msg}\n");

    // Select inside plural should not be allowed.
    assertIsNotTemplateBody(
        "{msg desc=\"A sample nested message\"}\n" +
        "  {plural $n_friends}\n" +
        "    {case 1}\n" +
        "      {select $gender}\n" +
        "        {case 'female'}{$person} has one person in her circle.\n" +
        "        {default}{$person} has one person in his circle.\n" +
        "      {/select}\n" +
        "    {default}\n" +
        "      {select $gender}\n" +
        "        {case 'female'}{$person} has {$n_friends} persons in her circle.\n" +
        "        {default}{$person} has {$n_friends} persons in his circle.\n" +
        "      {/select}\n" +
        "  {/plural}\n" +
        "{/msg}\n");

    // Messages with more than one plural/gender clauses should not be allowed.
    assertIsNotTemplateBody(
        "{msg desc=\"A sample plural message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "    {default}{$person} added you to his circle.\n" +
        "  {/select}\n" +
        "  {plural $num_people offset=\"1\"}\n" +
        "    {case 0}I see no one in {$place}.\n" +
        "    {case 1}I see {$person} in {$place}.\n" +
        "    {case 2}I see {$person} and one other person in {$place}.\n" +
        "    {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n" +
        "  {/plural}\n" +
        " {/msg}\n");

  }


  public void testParseRawText() throws Exception {

    String templateBody =
        "  {sp} aaa bbb  \n" +
        "  ccc {lb}{rb} ddd {\\n}\n" +
        "  eee <br>\n" +
        "  fff\n" +
        "  {literal}ggg\n" +
        "hhh }{  {/literal}  \n" +
        "  \u2222\uEEEE\u9EC4\u607A\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());
    RawTextNode rtn = (RawTextNode) nodes.get(0);
    assertEquals(
        "  aaa bbb ccc {} ddd \neee <br>fffggg\nhhh }{  \u2222\uEEEE\u9EC4\u607A",
        rtn.getRawText());
    assertEquals(
        "  aaa bbb ccc {lb}{rb} ddd {\\n}eee <br>fffggg{\\n}hhh {rb}{lb}  \u2222\uEEEE\u9EC4\u607A",
        rtn.toSourceString());
  }


  public void testParseComments() throws Exception {

    String templateBody =
        "  {sp}  // {sp}\n" +  // first {sp} outside of comments
        "  /* {sp} {sp} */  // {sp}\n" +
        "  /* {sp} */{sp}/* {sp} */\n" +  // middle {sp} outside of comments
        "  /* {sp}\n" +
        "  {sp} */{sp}\n" +  // last {sp} outside of comments
        "  // {sp} /* {sp} */\n" +
        "  http://www.google.com\n";  // not a comment if "//" preceded by a non-space such as ":"

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());
    assertEquals("   http://www.google.com", ((RawTextNode) nodes.get(0)).getRawText());
  }


  public void testParsePrintStmt() throws Exception {

    String templateBody =
        "  {$boo.foo}{$boo.foo}\n" +
        "  {$goo + 1 |noescape}\n" +  // note 'noescape' is V1 syntax
        "  {print 'blah    blahblahblah' |escapeHtml|insertWordBreaks:8}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(4, nodes.size());

    PrintNode pn0 = (PrintNode) nodes.get(0);
    assertEquals(SyntaxVersion.V2, pn0.getSyntaxVersion());
    assertEquals("$boo.foo", pn0.getExprText());
    assertEquals(0, pn0.getChildren().size());
    assertEquals("FOO", pn0.genBasePlaceholderName());
    assertEquals("{$boo.foo}", pn0.toSourceString());
    assertTrue(pn0.getExprUnion().getExpr().getChild(0) instanceof DataRefNode);

    PrintNode pn1 = (PrintNode) nodes.get(1);
    assertTrue(pn0.genSamenessKey().equals(pn1.genSamenessKey()));
    assertTrue(pn1.getExprUnion().getExpr().getChild(0) instanceof DataRefNode);

    PrintNode pn2 = (PrintNode) nodes.get(2);
    assertEquals(SyntaxVersion.V2, pn2.getSyntaxVersion());
    assertEquals("$goo + 1", pn2.getExprText());
    assertEquals(1, pn2.getChildren().size());
    PrintDirectiveNode pn2d0 = pn2.getChild(0);
    assertEquals(SyntaxVersion.V1, pn2d0.getSyntaxVersion());
    assertEquals("|noAutoescape", pn2d0.getName());
    assertEquals("XXX", pn2.genBasePlaceholderName());
    assertTrue(pn2.getExprUnion().getExpr().getChild(0) instanceof PlusOpNode);

    PrintNode pn3 = (PrintNode) nodes.get(3);
    assertEquals(SyntaxVersion.V2, pn3.getSyntaxVersion());
    assertEquals("'blah    blahblahblah'", pn3.getExprText());
    assertEquals(2, pn3.getChildren().size());
    PrintDirectiveNode pn3d0 = pn3.getChild(0);
    assertEquals(SyntaxVersion.V2, pn3d0.getSyntaxVersion());
    assertEquals("|escapeHtml", pn3d0.getName());
    PrintDirectiveNode pn3d1 = pn3.getChild(1);
    assertEquals(SyntaxVersion.V2, pn3d1.getSyntaxVersion());
    assertEquals("|insertWordBreaks", pn3d1.getName());
    assertEquals(8, ((IntegerNode) pn3d1.getArgs().get(0).getChild(0)).getValue());
    assertEquals("XXX", pn3.genBasePlaceholderName());
    assertTrue(pn3.getExprUnion().getExpr().getChild(0) instanceof StringNode);

    assertFalse(pn0.genSamenessKey().equals(pn2.genSamenessKey()));
    assertFalse(pn3.genSamenessKey().equals(pn0.genSamenessKey()));
  }


  public void testParsePrintStmtWithPhname() throws Exception {

    String templateBody = "" +
        "  {$boo.foo}\n" +
        "  {$boo.foo phname=\"booFoo\"}\n" +
        "  {$boo.foo    phname=\"booFoo\"    }\n" +
        "  {$boo.foo phname=\"boo_foo\"}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(4, nodes.size());

    PrintNode pn0 = (PrintNode) nodes.get(0);
    assertEquals("$boo.foo", pn0.getExprText());
    assertEquals("FOO", pn0.genBasePlaceholderName());
    assertEquals("{$boo.foo}", pn0.toSourceString());

    PrintNode pn1 = (PrintNode) nodes.get(1);
    assertEquals("$boo.foo", pn1.getExprText());
    assertEquals("BOO_FOO", pn1.genBasePlaceholderName());
    assertEquals("{$boo.foo phname=\"booFoo\"}", pn1.toSourceString());
    assertEquals(SyntaxVersion.V2, pn1.getSyntaxVersion());
    assertEquals(0, pn1.getChildren().size());
    assertTrue(pn1.getExprUnion().getExpr().getChild(0) instanceof DataRefNode);

    PrintNode pn2 = (PrintNode) nodes.get(2);
    assertEquals("$boo.foo", pn2.getExprText());
    assertEquals("BOO_FOO", pn2.genBasePlaceholderName());
    assertEquals("{$boo.foo phname=\"booFoo\"}", pn2.toSourceString());

    PrintNode pn3 = (PrintNode) nodes.get(3);
    assertEquals("$boo.foo", pn3.getExprText());
    assertEquals("BOO_FOO", pn3.genBasePlaceholderName());
    assertEquals("{$boo.foo phname=\"boo_foo\"}", pn3.toSourceString());

    assertFalse(pn0.genSamenessKey().equals(pn1.genSamenessKey()));
    assertTrue(pn1.genSamenessKey().equals(pn2.genSamenessKey()));
    assertFalse(pn1.genSamenessKey().equals(pn3.genSamenessKey()));
  }


  public void testParseCssStmt() throws Exception {

    String templateBody =
        "{css selected-option}\n" +
        "{css CSS_SELECTED_OPTION}\n" +
        "{css $cssSelectedOption}";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(3, nodes.size());
    assertEquals("selected-option", ((CssNode) nodes.get(0)).getCommandText());
    assertEquals("CSS_SELECTED_OPTION", ((CssNode) nodes.get(1)).getCommandText());
    assertEquals("$cssSelectedOption", ((CssNode) nodes.get(2)).getCommandText());
  }


  public void testParseMsgStmt() throws Exception {

    String templateBody =
        "  {msg desc=\"Tells user's quota usage.\"}\n" +
        "    You're currently using {$usedMb} MB of your quota.{sp}\n" +
        "    <a href=\"{$learnMoreUrl}\">Learn more</A>\n" +
        "    <br /><br />\n" +
        "  {/msg}\n" +
        "  {msg meaning=\"noun\" desc=\"\" hidden=\"true\"}Archive{/msg}\n" +
        "  {msg meaning=\"noun\" desc=\"The archive (noun).\"}Archive{/msg}\n" +
        "  {msg meaning=\"verb\" desc=\"\"}Archive{/msg}\n" +
        "  {msg desc=\"\"}Archive{/msg}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(5, nodes.size());

    MsgNode mn0 = (MsgNode) nodes.get(0);
    assertEquals("Tells user's quota usage.", mn0.getDesc());
    assertEquals(null, mn0.getMeaning());
    assertEquals(false, mn0.isHidden());
    assertEquals(8, mn0.numChildren());

    assertEquals("You're currently using ", ((RawTextNode) mn0.getChild(0)).getRawText());
    MsgPlaceholderNode mpn1 = (MsgPlaceholderNode) mn0.getChild(1);
    assertEquals("$usedMb", ((PrintNode) mpn1.getChild(0)).getExprText());
    assertEquals(" MB of your quota. ", ((RawTextNode) mn0.getChild(2)).getRawText());

    MsgPlaceholderNode mpn3 = (MsgPlaceholderNode) mn0.getChild(3);
    MsgHtmlTagNode mhtn3 = (MsgHtmlTagNode) mpn3.getChild(0);
    assertEquals("a", mhtn3.getLcTagName());
    assertEquals("START_LINK", mhtn3.genBasePlaceholderName());
    assertEquals("<a href=\"{$learnMoreUrl}\">", mhtn3.toSourceString());

    assertEquals(3, mhtn3.numChildren());
    assertEquals("<a href=\"", ((RawTextNode) mhtn3.getChild(0)).getRawText());
    assertEquals("$learnMoreUrl", ((PrintNode) mhtn3.getChild(1)).getExprText());
    assertEquals("\">", ((RawTextNode) mhtn3.getChild(2)).getRawText());

    assertEquals("Learn more", ((RawTextNode) mn0.getChild(4)).getRawText());

    MsgPlaceholderNode mpn5 = (MsgPlaceholderNode) mn0.getChild(5);
    MsgHtmlTagNode mhtn5 = (MsgHtmlTagNode) mpn5.getChild(0);
    assertEquals("/a", mhtn5.getLcTagName());
    assertEquals("END_LINK", mhtn5.genBasePlaceholderName());
    assertEquals("</A>", mhtn5.toSourceString());

    MsgPlaceholderNode mpn6 = (MsgPlaceholderNode) mn0.getChild(6);
    MsgHtmlTagNode mhtn6 = (MsgHtmlTagNode) mpn6.getChild(0);
    assertEquals("BREAK", mhtn6.genBasePlaceholderName());
    assertTrue(mpn6.isSamePlaceholderAs((MsgPlaceholderNode) mn0.getChild(7)));
    assertTrue(! mpn6.isSamePlaceholderAs(mpn5));
    assertTrue(! mpn5.isSamePlaceholderAs(mpn3));

    MsgNode mn1 = (MsgNode) nodes.get(1);
    assertEquals("", mn1.getDesc());
    assertEquals("noun", mn1.getMeaning());
    assertEquals(true, mn1.isHidden());
    assertEquals(1, mn1.numChildren());
    assertEquals("Archive", ((RawTextNode) mn1.getChild(0)).getRawText());
    assertEquals("{msg meaning=\"noun\" desc=\"\" hidden=\"true\"}Archive{/msg}",
                 mn1.toSourceString());
  }


  public void testParseMsgHtmlTagWithPhname() throws Exception {

    String templateBody = "" +
        "  {msg desc=\"\"}\n" +
        "    <a href=\"{$learnMoreUrl}\" phname=\"beginLearnMoreLink\">\n" +
        "      Learn more\n" +
        "    </A phname=\"end_LearnMore_LINK\">\n" +
        "    <br phname=\"breakTag\" /><br phname=\"breakTag\" /><br phname=\"break_tag\" />\n" +
        "  {/msg}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());

    MsgNode mn0 = (MsgNode) nodes.get(0);
    assertEquals(6, mn0.numChildren());

    MsgPlaceholderNode mpn0 = (MsgPlaceholderNode) mn0.getChild(0);
    MsgHtmlTagNode mhtn0 = (MsgHtmlTagNode) mpn0.getChild(0);
    assertEquals("a", mhtn0.getLcTagName());
    assertEquals("BEGIN_LEARN_MORE_LINK", mhtn0.genBasePlaceholderName());
    assertEquals(
        "<a href=\"{$learnMoreUrl}\" phname=\"beginLearnMoreLink\">", mhtn0.toSourceString());

    MsgPlaceholderNode mpn2 = (MsgPlaceholderNode) mn0.getChild(2);
    MsgHtmlTagNode mhtn2 = (MsgHtmlTagNode) mpn2.getChild(0);
    assertEquals("/a", mhtn2.getLcTagName());
    assertEquals("END_LEARN_MORE_LINK", mhtn2.genBasePlaceholderName());
    assertEquals("</A phname=\"end_LearnMore_LINK\">", mhtn2.toSourceString());

    MsgPlaceholderNode mpn3 = (MsgPlaceholderNode) mn0.getChild(3);
    MsgHtmlTagNode mhtn3 = (MsgHtmlTagNode) mpn3.getChild(0);
    assertEquals("br", mhtn3.getLcTagName());
    assertEquals("BREAK_TAG", mhtn3.genBasePlaceholderName());
    assertEquals("<br  phname=\"breakTag\"/>", mhtn3.toSourceString());

    MsgPlaceholderNode mpn4 = (MsgPlaceholderNode) mn0.getChild(4);
    MsgHtmlTagNode mhtn4 = (MsgHtmlTagNode) mpn4.getChild(0);

    MsgPlaceholderNode mpn5 = (MsgPlaceholderNode) mn0.getChild(5);
    MsgHtmlTagNode mhtn5 = (MsgHtmlTagNode) mpn5.getChild(0);
    assertEquals("br", mhtn5.getLcTagName());
    assertEquals("BREAK_TAG", mhtn5.genBasePlaceholderName());
    assertEquals("<br  phname=\"break_tag\"/>", mhtn5.toSourceString());

    assertFalse(mhtn0.genSamenessKey().equals(mhtn2.genSamenessKey()));
    assertFalse(mhtn0.genSamenessKey().equals(mhtn3.genSamenessKey()));
    assertTrue(mhtn3.genSamenessKey().equals(mhtn4.genSamenessKey()));
    assertFalse(mhtn3.genSamenessKey().equals(mhtn5.genSamenessKey()));
  }


  public void testParseMsgStmtWithCall() throws Exception {

    String templateBody =
        "  {msg desc=\"Blah.\"}\n" +
        "    Blah {call name=\".helper_\" data=\"all\" /} blah{sp}\n" +
        "    {call .helper_}\n" +
        "      {param foo}Foo{/param}\n" +
        "    {/call}{sp}\n" +
        "    blah.\n" +
        "  {/msg}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());

    MsgNode mn = (MsgNode) nodes.get(0);
    assertEquals(5, mn.numChildren());

    assertEquals("Blah ", ((RawTextNode) mn.getChild(0)).getRawText());
    assertEquals(0, ((CallNode) ((MsgPlaceholderNode) mn.getChild(1)).getChild(0)).numChildren());
    assertEquals(" blah ", ((RawTextNode) mn.getChild(2)).getRawText());
    assertEquals(1, ((CallNode) ((MsgPlaceholderNode) mn.getChild(3)).getChild(0)).numChildren());
    assertEquals(" blah.", ((RawTextNode) mn.getChild(4)).getRawText());
  }


  public void testParseMsgStmtWithIf() throws Exception {

    String templateBody =
        "  {msg desc=\"Blah.\"}\n" +
        "    Blah \n" +
        "    {if $boo}\n" +
        "      bleh\n" +
        "    {else}\n" +
        "      bluh\n" +
        "    {/if}\n" +
        "    .\n" +
        "  {/msg}\n";

    try {
      parseTemplateBody(templateBody);
      fail();
    } catch (SoySyntaxException sse) {
      // Test passes.
    }
  }


  public void testParseLetStmt() throws Exception {

    String templateBody =
        "  {let $alpha: $boo.foo /}\n" +
        "  {let $beta}Boo!{/let}\n" +
        "  {let $gamma}\n" +
        "    {for $i in range($alpha)}\n" +
        "      {$i}{$beta}\n" +
        "    {/for}\n" +
        "  {/let}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(3, nodes.size());

    LetValueNode alphaNode = (LetValueNode) nodes.get(0);
    assertEquals("alpha", alphaNode.getVarName());
    assertEquals("$boo.foo", alphaNode.getValueExpr().toSourceString());
    LetContentNode betaNode = (LetContentNode) nodes.get(1);
    assertEquals("beta", betaNode.getVarName());
    assertEquals("Boo!", ((RawTextNode) betaNode.getChild(0)).getRawText());
    LetContentNode gammaNode = (LetContentNode) nodes.get(2);
    assertEquals("gamma", gammaNode.getVarName());
    assertTrue(gammaNode.getChild(0) instanceof ForNode);

    // Test error case.
    try {
      parseTemplateBody("{let $alpha /}");
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "A 'let' tag should be self-ending (with a trailing '/') if and only if it also" +
          " contains a value"));
    }

    // Test error case.
    try {
      parseTemplateBody("{let $alpha: $boo.foo}");
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "A 'let' tag should contain a value if and only if it is also self-ending (with a" +
          " trailing '/')"));
    }
  }


  public void testParseIfStmt() throws Exception {

    String templateBody =
        "  {if $zoo}{$zoo}{/if}\n" +
        "  {if $boo}\n" +
        "    Blah\n" +
        "  {elseif $foo.goo > 2}\n" +
        "    {$moo}\n" +
        "  {else}\n" +
        "    Blah {$moo}\n" +
        "  {/if}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(2, nodes.size());

    IfNode in0 = (IfNode) nodes.get(0);
    assertEquals(1, in0.numChildren());
    IfCondNode in0icn0 = (IfCondNode) in0.getChild(0);
    assertEquals("$zoo", in0icn0.getExprText());
    assertEquals(1, in0icn0.numChildren());
    assertEquals("$zoo", ((PrintNode) in0icn0.getChild(0)).getExprText());
    assertTrue(in0icn0.getExprUnion().getExpr().getChild(0) instanceof DataRefNode);

    IfNode in1 = (IfNode) nodes.get(1);
    assertEquals(3, in1.numChildren());
    IfCondNode in1icn0 = (IfCondNode) in1.getChild(0);
    assertEquals("$boo", in1icn0.getExprText());
    assertTrue(in1icn0.getExprUnion().getExpr().getChild(0) instanceof DataRefNode);
    IfCondNode in1icn1 = (IfCondNode) in1.getChild(1);
    assertEquals("$foo.goo > 2", in1icn1.getExprText());
    assertTrue(in1icn1.getExprUnion().getExpr().getChild(0) instanceof GreaterThanOpNode);
    assertEquals("", ((IfElseNode) in1.getChild(2)).getCommandText());
    assertEquals("{if $boo}Blah{elseif $foo.goo > 2}{$moo}{else}Blah {$moo}{/if}",
                 in1.toSourceString());
  }


  public void testParseSwitchStmt() throws Exception {

    String templateBody =
        "  {switch $boo} {case 0}Blah\n" +
        "    {case $foo.goo}\n" +
        "      Bleh\n" +
        "    {case -1, 1, $moo}\n" +
        "      Bluh\n" +
        "    {default}\n" +
        "      Bloh\n" +
        "  {/switch}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());

    SwitchNode sn = (SwitchNode) nodes.get(0);
    assertEquals("$boo", sn.getExpr().toSourceString());
    assertTrue(sn.getExpr().getChild(0) instanceof DataRefNode);
    assertEquals(4, sn.numChildren());

    SwitchCaseNode scn0 = (SwitchCaseNode) sn.getChild(0);
    assertEquals("0", scn0.getExprListText());
    assertEquals(1, scn0.getExprList().size());
    assertTrue(scn0.getExprList().get(0).getChild(0) instanceof IntegerNode);

    SwitchCaseNode scn1 = (SwitchCaseNode) sn.getChild(1);
    assertEquals("$foo.goo", scn1.getExprListText());
    assertEquals(1, scn1.getExprList().size());
    assertTrue(scn1.getExprList().get(0).getChild(0) instanceof DataRefNode);

    SwitchCaseNode scn2 = (SwitchCaseNode) sn.getChild(2);
    assertEquals("-1, 1, $moo", scn2.getExprListText());
    assertEquals(3, scn2.getExprList().size());
    assertTrue(scn2.getExprList().get(0).getChild(0) instanceof NegativeOpNode);
    assertTrue(scn2.getExprList().get(1).getChild(0) instanceof IntegerNode);
    assertTrue(scn2.getExprList().get(2).getChild(0) instanceof DataRefNode);
    assertEquals("Bluh", ((RawTextNode) scn2.getChild(0)).getRawText());

    assertEquals(
        "Bloh", ((RawTextNode) ((SwitchDefaultNode) sn.getChild(3)).getChild(0)).getRawText());

    assertEquals(
        "{switch $boo}{case 0}Blah{case $foo.goo}Bleh{case -1, 1, $moo}Bluh{default}Bloh{/switch}",
        sn.toSourceString());
  }


  public void testParseForeachStmt() throws Exception {

    String templateBody =
        "  {foreach $goo in $goose}\n" +
        "    {$goose.numKids} goslings.{\\n}\n" +
        "  {/foreach}\n" +
        "  {foreach $boo in $foo.booze}\n" +
        "    Scary drink {$boo.name}!\n" +
        "    {if not isLast($boo)}{\\n}{/if}\n" +
        "  {ifempty}\n" +
        "    Sorry, no booze.\n" +
        "  {/foreach}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(2, nodes.size());

    ForeachNode fn0 = (ForeachNode) nodes.get(0);
    assertEquals("goo", fn0.getVarName());
    assertEquals("$goose", fn0.getExprText());
    assertEquals(1, ((DataRefNode) fn0.getExpr().getChild(0)).numChildren());
    assertEquals(1, fn0.numChildren());

    ForeachNonemptyNode fn0fnn0 = (ForeachNonemptyNode) fn0.getChild(0);
    assertEquals(2, fn0fnn0.numChildren());
    assertEquals("$goose.numKids", ((PrintNode) fn0fnn0.getChild(0)).getExprText());
    assertEquals(" goslings.\n", ((RawTextNode) fn0fnn0.getChild(1)).getRawText());

    ForeachNode fn1 = (ForeachNode) nodes.get(1);
    assertEquals("boo", fn1.getVarName());
    assertEquals("$foo.booze", fn1.getExprText());
    assertEquals(2, ((DataRefNode) fn1.getExpr().getChild(0)).numChildren());
    assertEquals(2, fn1.numChildren());

    ForeachNonemptyNode fn1fnn0 = (ForeachNonemptyNode) fn1.getChild(0);
    assertEquals("boo", fn1fnn0.getVarName());
    assertEquals("$foo.booze", fn1fnn0.getExprText());
    assertEquals("boo", fn1fnn0.getVarName());
    assertEquals(4, fn1fnn0.numChildren());
    IfNode fn1fnn0in = (IfNode) fn1fnn0.getChild(3);
    assertEquals(1, fn1fnn0in.numChildren());
    assertEquals("not isLast($boo)", ((IfCondNode) fn1fnn0in.getChild(0)).getExprText());

    ForeachIfemptyNode fn1fin1 = (ForeachIfemptyNode) fn1.getChild(1);
    assertEquals(1, fn1fin1.numChildren());
    assertEquals("Sorry, no booze.", ((RawTextNode) fn1fin1.getChild(0)).getRawText());
  }


  public void testParseForStmt() throws Exception {

    String templateBody =
        "  {for $i in range(1, $items.length + 1)}\n" +  // note: not actually V2, but parses fine
        "    {msg desc=\"Numbered item.\"}\n" +
        "      {$i}: {$items[$i - 1]}{\\n}\n" +
        "    {/msg}\n" +
        "  {/for}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());

    ForNode fn = (ForNode) nodes.get(0);
    assertEquals("i", fn.getVarName());

    List<String> rangeArgTexts = fn.getRangeArgTexts();
    assertEquals(2, rangeArgTexts.size());
    assertEquals("1", rangeArgTexts.get(0));
    assertEquals("$items.length + 1", rangeArgTexts.get(1));

    List<ExprRootNode<?>> rangeArgs = fn.getRangeArgs();
    assertEquals(2, rangeArgs.size());
    assertTrue(rangeArgs.get(0).getChild(0) instanceof IntegerNode);
    assertTrue(rangeArgs.get(1).getChild(0) instanceof PlusOpNode);

    assertEquals(1, fn.numChildren());
    MsgNode mn = (MsgNode) fn.getChild(0);
    assertEquals(4, mn.numChildren());
    assertEquals("$i",
        ((PrintNode) ((MsgPlaceholderNode) mn.getChild(0)).getChild(0)).getExprText());
    assertEquals("$items[$i - 1]",
        ((PrintNode) ((MsgPlaceholderNode) mn.getChild(2)).getChild(0)).getExprText());
  }


  public void testParseBasicCallStmt() throws Exception {

    String templateBody =
        "  {call name=\".booTemplate_\" /}\n" +
        "  {call function=\"foo.goo.mooTemplate\" data=\"all\" /}\n" +
        "  {call name=\".zooTemplate\" data=\"$animals\"}\n" +
        "    {param key=\"yoo\" value=\"round($too)\" /}\n" +
        "    {param key=\"woo\"}poo{/param}\n" +
        "  {/call}\n" +
        "  {call .booTemplate_ /}\n" +
        "  {call .zooTemplate data=\"$animals\"}\n" +
        "    {param yoo: round($too) /}\n" +
        "    {param woo}poo{/param}\n" +
        "    {param zoo: 0 /}\n" +
        "  {/call}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(5, nodes.size());

    CallBasicNode cn0 = (CallBasicNode) nodes.get(0);
    assertEquals(SyntaxVersion.V2, cn0.getSyntaxVersion());
    assertEquals(null, cn0.getCalleeName());
    assertEquals(".booTemplate_", cn0.getPartialCalleeName());
    assertEquals(false, cn0.isPassingData());
    assertEquals(false, cn0.isPassingAllData());
    assertEquals(null, cn0.getExprText());
    assertEquals(null, cn0.getExpr());
    assertEquals("XXX", cn0.genBasePlaceholderName());
    assertEquals(0, cn0.numChildren());

    CallBasicNode cn1 = (CallBasicNode) nodes.get(1);
    assertEquals(SyntaxVersion.V1, cn1.getSyntaxVersion());
    assertEquals("foo.goo.mooTemplate", cn1.getCalleeName());
    assertEquals(null, cn1.getPartialCalleeName());
    assertEquals(true, cn1.isPassingData());
    assertEquals(true, cn1.isPassingAllData());
    assertEquals(null, cn1.getExprText());
    assertEquals(null, cn1.getExpr());
    assertFalse(cn1.genSamenessKey().equals(cn0.genSamenessKey()));
    assertEquals(0, cn1.numChildren());

    CallBasicNode cn2 = (CallBasicNode) nodes.get(2);
    assertEquals(SyntaxVersion.V2, cn2.getSyntaxVersion());
    assertEquals(null, cn2.getCalleeName());
    assertEquals(".zooTemplate", cn2.getPartialCalleeName());
    assertEquals(true, cn2.isPassingData());
    assertEquals(false, cn2.isPassingAllData());
    assertEquals("$animals", cn2.getExprText());
    assertTrue(cn2.getExpr().getChild(0) != null);
    assertEquals(2, cn2.numChildren());

    CallParamValueNode cn2cpvn0 = (CallParamValueNode) cn2.getChild(0);
    assertEquals("yoo", cn2cpvn0.getKey());
    assertEquals("round($too)", cn2cpvn0.getValueExprText());
    assertTrue(cn2cpvn0.getValueExprUnion().getExpr().getChild(0) instanceof FunctionNode);

    CallParamContentNode cn2cpcn1 = (CallParamContentNode) cn2.getChild(1);
    assertEquals("woo", cn2cpcn1.getKey());
    assertEquals("poo", ((RawTextNode) cn2cpcn1.getChild(0)).getRawText());

    CallBasicNode cn3 = (CallBasicNode) nodes.get(3);
    assertEquals(SyntaxVersion.V2, cn3.getSyntaxVersion());
    assertEquals(null, cn3.getCalleeName());
    assertEquals(".booTemplate_", cn3.getPartialCalleeName());
    assertEquals(false, cn3.isPassingData());
    assertEquals(false, cn3.isPassingAllData());
    assertEquals(null, cn3.getExprText());
    assertEquals(null, cn3.getExpr());
    assertEquals("XXX", cn3.genBasePlaceholderName());
    assertEquals(0, cn3.numChildren());

    CallBasicNode cn4 = (CallBasicNode) nodes.get(4);
    assertEquals(SyntaxVersion.V2, cn4.getSyntaxVersion());
    assertEquals(null, cn4.getCalleeName());
    assertEquals(".zooTemplate", cn4.getPartialCalleeName());
    assertEquals(true, cn4.isPassingData());
    assertEquals(false, cn4.isPassingAllData());
    assertEquals("$animals", cn4.getExprText());
    assertTrue(cn4.getExpr().getChild(0) != null);
    assertEquals(3, cn4.numChildren());

    CallParamValueNode cn4cpvn0 = (CallParamValueNode) cn4.getChild(0);
    assertEquals("yoo", cn4cpvn0.getKey());
    assertEquals("round($too)", cn4cpvn0.getValueExprText());
    assertTrue(cn4cpvn0.getValueExprUnion().getExpr().getChild(0) instanceof FunctionNode);

    CallParamContentNode cn4cpcn1 = (CallParamContentNode) cn4.getChild(1);
    assertEquals("woo", cn4cpcn1.getKey());
    assertEquals("poo", ((RawTextNode) cn4cpcn1.getChild(0)).getRawText());

    CallParamValueNode cn4cpvn2 = (CallParamValueNode) cn4.getChild(2);
    assertEquals("zoo", cn4cpvn2.getKey());
    assertEquals("0", cn4cpvn2.getValueExprText());
  }


  public void testParseDelegateCallStmt() throws Exception {

    String templateBody =
        "  {delcall name=\"booTemplate\" /}\n" +
        "  {delcall foo.goo.mooTemplate data=\"all\" /}\n" +
        "  {delcall MySecretFeature.zooTemplate data=\"$animals\"}\n" +
        "    {param yoo: round($too) /}\n" +
        "    {param woo}poo{/param}\n" +
        "  {/delcall}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(3, nodes.size());

    CallDelegateNode cn0 = (CallDelegateNode) nodes.get(0);
    assertEquals(SyntaxVersion.V2, cn0.getSyntaxVersion());
    assertEquals("booTemplate", cn0.getDelCalleeName());
    assertEquals(false, cn0.isPassingData());
    assertEquals(false, cn0.isPassingAllData());
    assertEquals(null, cn0.getExprText());
    assertEquals(null, cn0.getExpr());
    assertEquals("XXX", cn0.genBasePlaceholderName());
    assertEquals(0, cn0.numChildren());

    CallDelegateNode cn1 = (CallDelegateNode) nodes.get(1);
    assertEquals(SyntaxVersion.V2, cn1.getSyntaxVersion());
    assertEquals("foo.goo.mooTemplate", cn1.getDelCalleeName());
    assertEquals(true, cn1.isPassingData());
    assertEquals(true, cn1.isPassingAllData());
    assertEquals(null, cn1.getExprText());
    assertEquals(null, cn1.getExpr());
    assertFalse(cn1.genSamenessKey().equals(cn0.genSamenessKey()));
    assertEquals(0, cn1.numChildren());

    CallDelegateNode cn2 = (CallDelegateNode) nodes.get(2);
    assertEquals(SyntaxVersion.V2, cn2.getSyntaxVersion());
    assertEquals("MySecretFeature.zooTemplate", cn2.getDelCalleeName());
    assertEquals(true, cn2.isPassingData());
    assertEquals(false, cn2.isPassingAllData());
    assertEquals("$animals", cn2.getExprText());
    assertTrue(cn2.getExpr().getChild(0) != null);
    assertEquals(2, cn2.numChildren());

    CallParamValueNode cn2cpvn0 = (CallParamValueNode) cn2.getChild(0);
    assertEquals("yoo", cn2cpvn0.getKey());
    assertEquals("round($too)", cn2cpvn0.getValueExprText());
    assertTrue(cn2cpvn0.getValueExprUnion().getExpr().getChild(0) instanceof FunctionNode);

    CallParamContentNode cn2cpcn1 = (CallParamContentNode) cn2.getChild(1);
    assertEquals("woo", cn2cpcn1.getKey());
    assertEquals("poo", ((RawTextNode) cn2cpcn1.getChild(0)).getRawText());
  }


  public void testParseCallStmtWithPhname() throws Exception {

    String templateBody = "" +
        "  {call .booTemplate_ phname=\"booTemplate_\" /}\n" +
        "  {call .booTemplate_ phname=\"booTemplate_\" /}\n" +
        "  {delcall MySecretFeature.zooTemplate data=\"$animals\" phname=\"secret_zoo\"}\n" +
        "    {param zoo: 0 /}\n" +
        "  {/delcall}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(3, nodes.size());

    CallBasicNode cn0 = (CallBasicNode) nodes.get(0);
    assertEquals("BOO_TEMPLATE", cn0.genBasePlaceholderName());
    assertEquals(SyntaxVersion.V2, cn0.getSyntaxVersion());
    assertEquals(null, cn0.getCalleeName());
    assertEquals(".booTemplate_", cn0.getPartialCalleeName());
    assertEquals(false, cn0.isPassingData());
    assertEquals(false, cn0.isPassingAllData());
    assertEquals(null, cn0.getExprText());
    assertEquals(null, cn0.getExpr());
    assertEquals(0, cn0.numChildren());

    CallBasicNode cn1 = (CallBasicNode) nodes.get(1);

    CallDelegateNode cn2 = (CallDelegateNode) nodes.get(2);
    assertEquals("SECRET_ZOO", cn2.genBasePlaceholderName());
    assertEquals(SyntaxVersion.V2, cn2.getSyntaxVersion());
    assertEquals("MySecretFeature.zooTemplate", cn2.getDelCalleeName());
    assertEquals(true, cn2.isPassingData());
    assertEquals(false, cn2.isPassingAllData());
    assertEquals("$animals", cn2.getExprText());
    assertTrue(cn2.getExpr().getChild(0) != null);
    assertEquals(1, cn2.numChildren());

    assertFalse(cn0.genSamenessKey().equals(cn1.genSamenessKey()));  // CallNodes are never same
    assertFalse(cn2.genSamenessKey().equals(cn0.genSamenessKey()));
  }


  // -----------------------------------------------------------------------------------------------
  // Plural/select messages.


  public void testParseMsgStmtWithPlural() throws Exception {
    String templateBody =
      "  {msg desc=\"A sample plural message\"}\n" +
      "    {plural $num_people offset=\"1\"}\n" +
      "      {case 0}I see no one in {$place}.\n" +
      "      {case 1}I see {$person} in {$place}.\n" +
      "      {case 2}I see {$person} and one other person in {$place}.\n" +
      "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n" +
      "    {/plural}" +
      "  {/msg}\n";


    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());

    MsgNode mn = (MsgNode) nodes.get(0);
    assertEquals(1, mn.numChildren());
    assertEquals("A sample plural message", mn.getDesc());

    MsgPluralNode pn = (MsgPluralNode) mn.getChild(0);
    assertEquals("$num_people offset=\"1\"", pn.getCommandText());
    assertEquals(1, pn.getOffset());
    assertEquals(4, pn.numChildren());   // 3 cases and default

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
    assertEquals("{remainder($num_people)}", phnd2.toSourceString());

    RawTextNode rtnd3 = (RawTextNode) dn.getChild(4);
    assertEquals(" other people in ", rtnd3.getRawText());

    MsgPlaceholderNode phnd3 = (MsgPlaceholderNode) dn.getChild(5);
    assertEquals("{$place}", phnd3.toSourceString());

    RawTextNode rtnd4 = (RawTextNode) dn.getChild(6);
    assertEquals(".", rtnd4.getRawText());
  }


  public void testParseMsgStmtWithSelect() throws Exception {
    String templateBody =
        "{msg desc=\"A sample gender message\"}\n" +
        "  {select $gender}\n" +
        "    {case 'female'}{$person} added you to her circle.\n" +
        "    {default}{$person} added you to his circle.\n" +
        "  {/select}\n" +
        "{/msg}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());

    MsgNode mn = (MsgNode) nodes.get(0);
    assertEquals(1, mn.numChildren());
    assertEquals("A sample gender message", mn.getDesc());

    MsgSelectNode sn = (MsgSelectNode) mn.getChild(0);
    assertEquals("$gender", sn.getCommandText());
    assertEquals(2, sn.numChildren());   // female and default

    // Case 'female'
    MsgSelectCaseNode cnf = (MsgSelectCaseNode) sn.getChild(0);
    assertEquals("'female'", cnf.getCommandText());
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


  public void testParseMsgStmtWithNestedSelects() throws Exception {
    String templateBody =
        "{msg desc=\"A sample nested message\"}\n" +
        "  {select $gender1}\n" +
        "    {case 'female'}\n" +
        "      {select $gender2}\n" +
        "        {case 'female'}{$person1} added {$person2} and her friends to her circle.\n" +
        "        {default}{$person1} added {$person2} and his friends to her circle.\n" +
        "      {/select}\n" +
        "    {default}\n" +
        "      {select $gender2}\n" +
        "        {case 'female'}{$person1} put {$person2} and her friends to his circle.\n" +
        "        {default}{$person1} put {$person2} and his friends to his circle.\n" +
        "      {/select}\n" +
        "  {/select}\n" +
        "{/msg}\n";

    List<StandaloneNode> nodes = parseTemplateBody(templateBody);
    assertEquals(1, nodes.size());

    MsgNode mn = (MsgNode) nodes.get(0);
    assertEquals(1, mn.numChildren());
    assertEquals("A sample nested message", mn.getDesc());

    // Outer select
    MsgSelectNode sn = (MsgSelectNode) mn.getChild(0);
    assertEquals("$gender1", sn.getCommandText());
    assertEquals(2, sn.numChildren());   // female and default

    // Outer select: Case 'female'
    MsgSelectCaseNode cnf = (MsgSelectCaseNode) sn.getChild(0);
    assertEquals("'female'", cnf.getCommandText());
    assertEquals(1, cnf.numChildren()); // Another select

    // Outer select: Case 'female': Inner select
    MsgSelectNode sn2 = (MsgSelectNode) cnf.getChild(0);
    assertEquals("$gender2", sn2.getCommandText());
    assertEquals(2, sn2.numChildren());   // female and default

    // Outer select: Case 'female': Inner select: Case 'female'
    MsgSelectCaseNode cnf2 = (MsgSelectCaseNode) sn2.getChild(0);
    assertEquals("'female'", cnf2.getCommandText());
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
    assertEquals("$gender2", sn3.getCommandText());
    assertEquals(2, sn3.numChildren());   // female and default

    // Outer select: Default: Inner select: Case 'female'
    MsgSelectCaseNode cnf3 = (MsgSelectCaseNode) sn3.getChild(0);
    assertEquals("'female'", cnf3.getCommandText());
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


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  /**
   * Parses the given input as a template body.
   * @param input The input string to parse.
   * @throws SoySyntaxException When the given input has a syntax error.
   * @throws TokenMgrError When the given input has a token error.
   * @throws ParseException When the given input has a parse error.
   * @return The parse tree nodes created.
   */
  private static List<StandaloneNode> parseTemplateBody(String input)
      throws SoySyntaxException, TokenMgrError, ParseException {
    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    return (new TemplateParser(input, "test.soy", /* start line number */ 1, nodeIdGen))
        .parseTemplateBody();
  }


  /**
   * Asserts that the given input is a valid template.
   * @param input The input string to parse.
   * @throws SoySyntaxException When the given input has a syntax error.
   * @throws TokenMgrError When the given input has a token error.
   * @throws ParseException When the given input has a parse error.
   */
  private static void assertIsTemplateBody(String input)
      throws SoySyntaxException, TokenMgrError, ParseException {
    parseTemplateBody(input);
  }


  /**
   * Asserts that the given input is not a valid template.
   * @param input The input string to parse.
   * @throws AssertionFailedError When the given input is actually a valid template.
   */
  private static void assertIsNotTemplateBody(String input) {
    try {
      parseTemplateBody(input);
      fail();
    } catch (SoySyntaxException sse) {
      // Test passes.
    } catch (TokenMgrError tme) {
      // Test passes.
    } catch (ParseException pe) {
      // Test passes.
    }
  }
}
