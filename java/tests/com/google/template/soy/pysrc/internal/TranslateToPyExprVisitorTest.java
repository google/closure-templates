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
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for TranslateToPyExprVisitor.
 *
 */
@RunWith(JUnit4.class)
public class TranslateToPyExprVisitorTest {

  @Test
  public void testNullLiteral() {
    assertThatSoyExpr("null").translatesTo("None", Integer.MAX_VALUE);
  }

  @Test
  public void testBooleanLiteral() {
    assertThatSoyExpr("true").translatesTo("True", Integer.MAX_VALUE);
    assertThatSoyExpr("false").translatesTo("False", Integer.MAX_VALUE);
  }

  @Test
  public void testStringLiteral() {
    assertThatSoyExpr("'waldo'")
        .translatesTo(new PyExpr("'waldo'", Integer.MAX_VALUE), PyStringExpr.class);
  }

  @Test
  public void testListLiteral() {
    assertThatSoyExpr("[]").translatesTo(new PyExpr("[]", Integer.MAX_VALUE), PyListExpr.class);
    assertThatSoyExpr("['blah', 123, $foo]")
        .translatesTo(
            new PyExpr("['blah', 123, data.get('foo')]", Integer.MAX_VALUE), PyListExpr.class);
  }

  @Test
  public void testRecordLiteral() {
    assertThatSoyExpr("record(aaa: 123, bbb: 'blah')")
        .translatesTo(
            "collections.OrderedDict([('aaa', 123), ('bbb', 'blah')])", Integer.MAX_VALUE);
    assertThatSoyExpr("record(aaa: $foo, bbb: 'blah')")
        .translatesTo(
            "collections.OrderedDict([('aaa', data.get('foo')), ('bbb', 'blah')])",
            Integer.MAX_VALUE);
  }

  @Test
  public void testGlobals() {
    ImmutableMap<String, Object> globals =
        ImmutableMap.<String, Object>builder()
            .put("STR", "Hello World")
            .put("NUM", 55)
            .put("BOOL", true)
            .build();

    assertThatSoyExpr("STR").withGlobals(globals).translatesTo("'Hello World'", Integer.MAX_VALUE);
    assertThatSoyExpr("NUM").withGlobals(globals).translatesTo("55", Integer.MAX_VALUE);
    assertThatSoyExpr("BOOL").withGlobals(globals).translatesTo("True", Integer.MAX_VALUE);
  }

  @Test
  public void testDataRef() {
    assertThatSoyExpr("$boo").translatesTo("data.get('boo')", Integer.MAX_VALUE);
    assertThatSoyExpr("$boo.goo").translatesTo("data.get('boo').get('goo')", Integer.MAX_VALUE);
    assertThatSoyExpr("$boo?.goo")
        .translatesTo(
            "None if data.get('boo') is None else data.get('boo').get('goo')",
            Operator.CONDITIONAL);
  }

  @Test
  public void testDataRef_localVars() {
    Map<String, PyExpr> frame = Maps.newHashMap();
    frame.put("zoo", new PyExpr("zooData8", Integer.MAX_VALUE));

    assertThatSoyExpr("$zoo").with(frame).translatesTo("zooData8", Integer.MAX_VALUE);
    assertThatSoyExpr("$zoo.boo")
        .with(frame)
        .translatesTo("zooData8.get('boo')", Integer.MAX_VALUE);
  }

  @Test
  public void testBasicOperators() {
    assertThatSoyExpr("not $boo or true and $foo")
        .translatesTo("not data.get('boo') or True and data.get('foo')", Operator.OR);
  }

  @Test
  public void testEqualOperator() {
    assertThatSoyExpr("'5' == 5 ? 1 : 0")
        .translatesTo("1 if runtime.type_safe_eq('5', 5) else 0", 1);
    assertThatSoyExpr("'5' == $boo ? 1 : 0")
        .translatesTo("1 if runtime.type_safe_eq('5', data.get('boo')) else 0", 1);
  }

  @Test
  public void testNotEqualOperator() {
    assertThatSoyExpr("'5' != 5").translatesTo("not runtime.type_safe_eq('5', 5)", Operator.NOT);
  }

  @Test
  public void testPlusOperator() {
    assertThatSoyExpr("( (8-4) + (2-1) )")
        .translatesTo("runtime.type_safe_add(8 - 4, 2 - 1)", Integer.MAX_VALUE);
  }

  @Test
  public void testNullCoalescingOperator() {
    assertThatSoyExpr("$boo ?: 5")
        .translatesTo(
            "data.get('boo') if data.get('boo') is not None else 5", Operator.CONDITIONAL);
  }

  @Test
  public void testConditionalOperator() {
    assertThatSoyExpr("$boo ? 5 : 6")
        .translatesTo("5 if data.get('boo') else 6", Operator.CONDITIONAL);
  }

  @Test
  public void testCheckNotNull() {
    assertThatSoyExpr("checkNotNull($boo) ? 1 : 0")
        .translatesTo("1 if runtime.check_not_null(data.get('boo')) else 0", Operator.CONDITIONAL);
  }

  @Test
  public void testCss() {
    assertThatSoyExpr("css('foo')").translatesTo("runtime.get_css_name('foo')", Integer.MAX_VALUE);
    assertThatSoyExpr("css($foo, 'base')")
        .translatesTo("runtime.get_css_name(data.get('foo'), 'base')", Integer.MAX_VALUE);
  }

  @Test
  public void testXid() {
    assertThatSoyExpr("xid('foo')").translatesTo("runtime.get_xid_name('foo')", Integer.MAX_VALUE);
  }

  @Test
  public void testDefaultParamAccess() {
    assertThatSoyExpr("{@param p:= 18}\n" + "  {$p}\n")
        .compilesTo(new PyExpr("sanitize.escape_html(data.get('p', 18))", Integer.MAX_VALUE));
  }
}
