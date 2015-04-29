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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Unit tests for TranslateToPyExprVisitor.
 *
 */
public class TranslateToPyExprVisitorTest extends TestCase {

  public void testNullLiteral() {
    assertThatSoyExpr("null").translatesTo(new PyExpr("None", Integer.MAX_VALUE));
  }

  public void testBooleanLiteral() {
    assertThatSoyExpr("true").translatesTo(new PyExpr("True", Integer.MAX_VALUE));
    assertThatSoyExpr("false").translatesTo(new PyExpr("False", Integer.MAX_VALUE));
  }

  public void testStringLiteral() {
    assertThatSoyExpr("'waldo'").translatesTo(
        new PyExpr("'waldo'", Integer.MAX_VALUE), PyStringExpr.class);
  }

  public void testListLiteral() {
    assertThatSoyExpr("[]").translatesTo(new PyExpr("[]", Integer.MAX_VALUE), PyListExpr.class);
    assertThatSoyExpr("['blah', 123, $foo]").translatesTo(
        new PyExpr("['blah', 123, opt_data.get('foo')]", Integer.MAX_VALUE), PyListExpr.class);
  }

  public void testMapLiteral() {
    // Unquoted keys.
    assertThatSoyExpr("[:]").translatesTo(new PyExpr("{}", Integer.MAX_VALUE));
    assertThatSoyExpr("['aaa': 123, 'bbb': 'blah']").translatesTo(
        new PyExpr("{'aaa': 123, 'bbb': 'blah'}", Integer.MAX_VALUE));
    assertThatSoyExpr("['aaa': $foo, 'bbb': 'blah']").translatesTo(
        new PyExpr("{'aaa': opt_data.get('foo'), 'bbb': 'blah'}", Integer.MAX_VALUE));

    // QuotedKeysIfJs should change nothing.
    assertThatSoyExpr("quoteKeysIfJs([:])").translatesTo(new PyExpr("{}", Integer.MAX_VALUE));
    assertThatSoyExpr("quoteKeysIfJs( ['aaa': $foo, 'bbb': 'blah'] )").translatesTo(
        new PyExpr("{'aaa': opt_data.get('foo'), 'bbb': 'blah'}", Integer.MAX_VALUE));

    // Non-string keys are allowed in Python.
    assertThatSoyExpr("[1: 'blah', 0: 123]").translatesTo(
        new PyExpr("{1: 'blah', 0: 123}", Integer.MAX_VALUE));
  }

  public void testGlobals() {
    ImmutableMap<String, PrimitiveData> globals = ImmutableMap.<String, PrimitiveData>builder()
        .put("STR", StringData.forValue("Hello World"))
        .put("NUM", IntegerData.forValue(55))
        .put("BOOL", BooleanData.forValue(true))
        .build();

    assertThatSoyExpr("STR").withGlobals(globals).translatesTo(
        new PyExpr("'Hello World'", Integer.MAX_VALUE));
    assertThatSoyExpr("NUM").withGlobals(globals).translatesTo(
        new PyExpr("55", Integer.MAX_VALUE));
    assertThatSoyExpr("BOOL").withGlobals(globals).translatesTo(
        new PyExpr("True", Integer.MAX_VALUE));
  }

  public void testDataRef() {
    assertThatSoyExpr("$boo").translatesTo(new PyExpr("opt_data.get('boo')", Integer.MAX_VALUE));
    assertThatSoyExpr("$boo.goo").translatesTo(
        new PyExpr("opt_data.get('boo').get('goo')", Integer.MAX_VALUE));
    assertThatSoyExpr("$boo['goo']").translatesTo(
        new PyExpr("runtime.key_safe_data_access(opt_data.get('boo'), 'goo')", Integer.MAX_VALUE));
    assertThatSoyExpr("$boo.0").translatesTo(
        new PyExpr("runtime.key_safe_data_access(opt_data.get('boo'), 0)", Integer.MAX_VALUE));
    assertThatSoyExpr("$boo[0]").translatesTo(
        new PyExpr("runtime.key_safe_data_access(opt_data.get('boo'), 0)", Integer.MAX_VALUE));
    assertThatSoyExpr("$boo[$foo][$foo+1]").translatesTo(
        new PyExpr("runtime.key_safe_data_access("
            + "runtime.key_safe_data_access(opt_data.get('boo'), opt_data.get('foo')), "
            + "runtime.type_safe_add(opt_data.get('foo'), 1))",
            Integer.MAX_VALUE));

    assertThatSoyExpr("$boo?.goo").translatesTo(
        new PyExpr(
            "None if opt_data.get('boo') is None else opt_data.get('boo').get('goo')",
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
    assertThatSoyExpr("$boo?[0]?.1").translatesTo(
        new PyExpr(
            "None if opt_data.get('boo') is None else "
            + "None if runtime.key_safe_data_access(opt_data.get('boo'), 0) is None else "
            + "runtime.key_safe_data_access("
            + "runtime.key_safe_data_access(opt_data.get('boo'), 0), 1)",
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  public void testDataRef_localVars() {
    Map<String, PyExpr> frame = Maps.newHashMap();
    frame.put("zoo", new PyExpr("zooData8", Integer.MAX_VALUE));

    assertThatSoyExpr("$zoo").with(frame).translatesTo(new PyExpr("zooData8", Integer.MAX_VALUE));
    assertThatSoyExpr("$zoo.boo").with(frame).translatesTo(
        new PyExpr("zooData8.get('boo')", Integer.MAX_VALUE));
  }

  public void testBasicOperators() {
    assertThatSoyExpr("not $boo or true and $foo").translatesTo(
        new PyExpr("not opt_data.get('boo') or True and opt_data.get('foo')",
            PyExprUtils.pyPrecedenceForOperator(Operator.OR)));
  }

  public void testEqualOperator() {
    assertThatSoyExpr("'5' == 5").translatesTo(
        new PyExpr("runtime.type_safe_eq('5', 5)", Integer.MAX_VALUE));
    assertThatSoyExpr("'5' == $boo").translatesTo(
        new PyExpr("runtime.type_safe_eq('5', opt_data.get('boo'))", Integer.MAX_VALUE));
  }

  public void testNotEqualOperator() {
    assertThatSoyExpr("'5' != 5").translatesTo(
        new PyExpr("not runtime.type_safe_eq('5', 5)",
            PyExprUtils.pyPrecedenceForOperator(Operator.NOT)));
  }

  public void testPlusOperator() {
    assertThatSoyExpr("( (8-4) + (2-1) )").translatesTo(
        new PyExpr("runtime.type_safe_add(8 - 4, 2 - 1)", Integer.MAX_VALUE));
  }

  public void testNullCoalescingOperator() {
    assertThatSoyExpr("$boo ?: 5").translatesTo(
        new PyExpr("opt_data.get('boo') if opt_data.get('boo') is not None else 5",
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }

  public void testConditionalOperator() {
    assertThatSoyExpr("$boo ? 5 : 6").translatesTo(
        new PyExpr("5 if opt_data.get('boo') else 6",
            PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }
}
