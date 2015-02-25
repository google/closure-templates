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

import static com.google.template.soy.pysrc.internal.SoyCodeForPySubject.assertThatSoyCode;

import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;

import junit.framework.TestCase;

/**
 * Unit tests for GenPyExprsVisitor.
 *
 */
public final class GenPyExprsVisitorTest extends TestCase {

  public void testRawText() {
    assertThatSoyCode("I'm feeling lucky!").compilesTo(
        new PyExpr("'I\\'m feeling lucky!'", Integer.MAX_VALUE));
  }

  public void testIf() {
    String soyNodeCode =
        "{if $boo}\n"
      + "  Blah\n"
      + "{elseif not $goo}\n"
      + "  Bleh\n"
      + "{else}\n"
      + "  Bluh\n"
      + "{/if}\n";
    String expectedPyExprText =
        "'Blah' if opt_data.get('boo') else 'Bleh' if not opt_data.get('goo') else 'Bluh'";

    assertThatSoyCode(soyNodeCode).compilesTo(
        new PyExpr(expectedPyExprText,
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  public void testIf_nested() {
    String soyNodeCode =
        "{if $boo}\n"
      + "  {if $goo}\n"
      + "    Blah\n"
      + "  {/if}\n"
      + "{else}\n"
      + "  Bleh\n"
      + "{/if}\n";
    String expectedPyExprText =
        "('Blah' if opt_data.get('goo') else '') if opt_data.get('boo') else 'Bleh'";

    assertThatSoyCode(soyNodeCode).compilesTo(
        new PyExpr(expectedPyExprText,
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  public void testSimpleMsgFallbackGroupNodeWithOneNode() {
    String soyCode =
          "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
        + "  Archive\n"
        + "{/msg}\n";

    String expectedPyCode =
        "render_literal("
        + "prepare_literal("
          + "###, "
          + "'Archive', "
          + "desc='Used as a verb.', "
          + "meaning='verb'))";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  public void testMsgFallbackGroupNodeWithTwoNodes() {
    String soyCode =
          "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
        + "  archive\n"
        + "{fallbackmsg desc=\"\"}\n"
        + "  ARCHIVE\n"
        + "{/msg}\n";

    String expectedPyCode =
        "render_literal("
        + "prepare_literal("
          + "###, "
          + "'archive', "
          + "desc='Used as a verb.', "
          + "meaning='verb')) "
      + "if is_msg_available(###) else render_literal("
        + "prepare_literal(###, 'ARCHIVE', desc=''))";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode,
        PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  public void testMsgOnlyLiteral() {
    String soyCode =
        "{msg meaning=\"verb\" desc=\"The word 'Archive' used as a verb.\"}"
          + "Archive"
      + "{/msg}\n";

    String expectedPyCode =
        "render_literal("
        + "prepare_literal("
          + "###, "
          + "'Archive', "
          + "desc='The word 'Archive' used as a verb.', "
          + "meaning='verb'))";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  public void testMsgSimpleSoyExpression() {
    String soyCode =
        "{msg desc=\"var placeholder\"}" +
          "Hello {$username}" +
        "{/msg}\n";

    String expectedPyCode =
        "render("
        + "prepare("
        + "###, "
        + "'Hello {USERNAME}', "
        + "('USERNAME', ), "
        + "desc='var placeholder'), "
        + "{'USERNAME': str(opt_data.get('username')), })";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  public void testMsgMultipleSoyExpressions() {
    String soyCode =
        "{msg desc=\"var placeholder\"}" +
          "{$greet} {$username}" +
        "{/msg}\n";

    String expectedPyCode =
        "render("
        + "prepare("
        + "###, "
        + "'{GREET} {USERNAME}', "
        + "('GREET', 'USERNAME', ), "
        + "desc='var placeholder'), "
        + "{"
          + "'GREET': str(opt_data.get('greet')), "
          + "'USERNAME': str(opt_data.get('username')), "
        + "})";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }

  public void testMsgNamespacedSoyExpression() {
    String soyCode =
        "{msg desc=\"placeholder with namespace\"}" +
          "Hello {$foo.bar}" +
        "{/msg}\n";

    String expectedPyCode =
        "render("
        + "prepare("
        + "###, "
        + "'Hello {BAR}', "
        + "('BAR', ), "
        + "desc='placeholder with namespace'), "
        + "{'BAR': str(opt_data.get('foo').get('bar')), })";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }
  public void testMsgWithArithmeticExpression() {
    String soyCode =
        "{msg desc=\"var placeholder\"}" +
          "Hello {$username + 1}" +
        "{/msg}\n";

    String expectedPyCode =
        "render("
        + "prepare("
        + "###, "
        + "'Hello {XXX}', "
        + "('XXX', ), "
        + "desc='var placeholder'), "
        + "{'XXX': str(runtime.type_safe_add(opt_data.get('username'), 1)), })";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }
  public void testMsgWithHtmlNode() {
    // msg with HTML tags and raw texts
    String soyCode =
        "{msg desc=\"with link\"}" +
          "Please click <a href='{$url}'>here</a>." +
        "{/msg}";

    String expectedPyCode =
        "render("
        + "prepare("
          + "###, "
          + "'Please click {START_LINK}here{END_LINK}.', "
          + "('END_LINK', 'START_LINK', ), "
          + "desc='with link'), "
          + "{"
            + "'END_LINK': '</a>', "
            + "'START_LINK': ''.join(['<a href=\\'',str(opt_data.get('url')),'\\'>']), "
          + "})";

    assertThatSoyCode(soyCode).compilesTo(new PyExpr(expectedPyCode, Integer.MAX_VALUE));
  }
}
