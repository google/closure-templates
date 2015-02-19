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

import static com.google.template.soy.pysrc.internal.PyCodeSubject.assertThatSoyCode;

import com.google.common.collect.ImmutableList;
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
        ImmutableList.of(new PyExpr("'I\\'m feeling lucky!'", Integer.MAX_VALUE)));
  }

  public void testIf() {
    String soyNodeCode =
        "{if $boo}\n" +
        "  Blah\n" +
        "{elseif not $goo}\n" +
        "  Bleh\n" +
        "{else}\n" +
        "  Bluh\n" +
        "{/if}\n";
    String expectedPyExprText =
        "'Blah' if opt_data.get('boo') else 'Bleh' if not opt_data.get('goo') else 'Bluh'";

    assertThatSoyCode(soyNodeCode).compilesTo(
        ImmutableList.of(new PyExpr(expectedPyExprText,
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL))));
  }

  public void testIf_nested() {
    String soyNodeCode =
        "{if $boo}\n" +
        "  {if $goo}\n" +
        "    Blah\n" +
        "  {/if}\n" +
        "{else}\n" +
        "  Bleh\n" +
        "{/if}\n";
    String expectedPyExprText =
        "('Blah' if opt_data.get('goo') else '') if opt_data.get('boo') else 'Bleh'";

    assertThatSoyCode(soyNodeCode).compilesTo(
        ImmutableList.of(new PyExpr(expectedPyExprText,
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL))));
  }
}
