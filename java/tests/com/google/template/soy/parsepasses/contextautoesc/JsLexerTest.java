/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.soytree.HtmlContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JsLexerTest {

  @Test
  public void testIsRegexPreceder() {
    // Statement terminators precede regexs.
    assertIsRegexPreceder(";");
    assertIsRegexPreceder("}");
    // But expression terminators precede div ops.
    assertIsDivOpPreceder(")");
    assertIsDivOpPreceder("]");
    // At the start of an expression or statement, expect a regex.
    assertIsRegexPreceder("(");
    assertIsRegexPreceder("[");
    assertIsRegexPreceder("{");
    // Assignment operators precede regexs.
    assertIsRegexPreceder("=");
    assertIsRegexPreceder("+=");
    assertIsRegexPreceder("*=");
    // Whether the + or - is infix or prefix, it cannot precede a div op.
    assertIsRegexPreceder("+");
    assertIsRegexPreceder("-");
    // An incr/decr op precedes a div operator.
    assertIsDivOpPreceder("--");
    assertIsDivOpPreceder("++");
    assertIsDivOpPreceder("x--");
    // When we have many dashes or pluses, then they are grouped left to right.
    assertIsRegexPreceder("x---"); // A postfix -- then a -.
    // return followed by a slash returns the regex literal.
    assertIsRegexPreceder("return");
    // Identifiers can be divided by.
    assertIsDivOpPreceder("x");
    assertIsDivOpPreceder("preturn");
    // Dots precede regexs.
    assertIsRegexPreceder("..");
    assertIsRegexPreceder("...");
    // But not if part of a number.
    assertIsDivOpPreceder("0.");
    // Numbers precede div ops.
    assertIsDivOpPreceder("0");
  }

  private static Context nextContext(Context starting, String jsTokens) {
    return JsLexerTokenManager.calculateTransitions(starting, jsTokens, /* offset= */ 0);
  }

  private static Context.JsFollowingSlash getSlashType(String jsTokens) {
    Context next =
        nextContext(Context.getStartContextForContentKind(SanitizedContentKind.JS), jsTokens);
    assertThat(next.state()).isEqualTo(HtmlContext.JS);
    return next.slashType();
  }

  private static void assertIsRegexPreceder(String jsTokens) {
    assertWithMessage(jsTokens)
        .that(getSlashType(jsTokens))
        .isEqualTo(Context.JsFollowingSlash.REGEX);
  }

  private static void assertIsDivOpPreceder(String jsTokens) {
    assertWithMessage(jsTokens)
        .that(getSlashType(jsTokens))
        .isEqualTo(Context.JsFollowingSlash.DIV_OP);
  }
}
