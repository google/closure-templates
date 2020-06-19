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

package com.google.template.soy.jbcsrc;

import static com.google.template.soy.jbcsrc.TemplateTester.assertThatTemplateBody;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantNull;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.RuntimeMapTypeTracker;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.AbstractLocalVarDefn;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.jbcsrc.TemplateTester.CompiledTemplateSubject;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionSubject;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.testing.SharedTestUtils;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.LegacyObjectMapType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** Tests for {@link ExpressionCompiler} */
@RunWith(JUnit4.class)
public class ExpressionCompilerTest {
  private final Map<String, SoyExpression> variables = new HashMap<>();
  private final FieldManager fields = new FieldManager(null);
  private final ExpressionCompiler testExpressionCompiler =
      ExpressionCompiler.create(
          new TemplateParameterLookup() {
            @Override
            public Expression getParam(TemplateParam paramName) {
              return variables.get(paramName.name());
            }

            @Override
            public Expression getLocal(SyntheticVarName varName) {
              throw new UnsupportedOperationException();
            }

            @Override
            public Expression getLocal(AbstractLocalVarDefn<?> localName) {
              throw new UnsupportedOperationException();
            }

            @Override
            public RenderContextExpression getRenderContext() {
              return new RenderContextExpression(
                  BytecodeUtils.constantNull(BytecodeUtils.RENDER_CONTEXT_TYPE));
            }

            @Override
            public JbcSrcPluginContext getPluginContext() {
              return getRenderContext();
            }

            @Override
            public Expression getParamsRecord() {
              throw new UnsupportedOperationException();
            }

            @Override
            public Expression getIjRecord() {
              throw new UnsupportedOperationException();
            }
          },
          new TemplateVariableManager(
              fields,
              LocalVariable.createThisVar(TypeInfo.create(Object.class), new Label(), new Label()),
              getRenderMethod()),
          fields,
          ErrorReporter.exploding(),
          SoyTypeRegistryBuilder.create(),
          new CompiledTemplateRegistry(TemplateRegistry.EMPTY));

  private static Method getRenderMethod() {
    try {
      return Method.getMethod(
          CompiledTemplate.class.getMethod(
              "render", LoggingAdvisingAppendable.class, RenderContext.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Before
  public void setUp() {
    variables.clear();
  }

  @Test
  public void testConstants() {
    assertExpression("1").evaluatesTo(1L);
    assertExpression("1.0").evaluatesTo(1D);
    assertExpression("false").evaluatesTo(false);
    assertExpression("true").evaluatesTo(true);
    assertExpression("'asdf'").evaluatesTo("asdf");
  }

  @Test
  public void testCollectionLiterals_list() {
    assertExpression("[]").evaluatesTo(ImmutableList.of());
    // Lists values are always boxed
    assertExpression("[1, 1.0, 'asdf', false]")
        .evaluatesTo(
            ImmutableList.of(
                IntegerData.forValue(1),
                FloatData.forValue(1.0),
                StringData.forValue("asdf"),
                BooleanData.FALSE));
  }

  @Test
  public void testCollectionLiterals_record() {
    // Record values are always boxed.  SoyMaps use == for equality, so check equivalence by
    // comparing their string representations.
    SoyExpression compile =
        compileExpression("record(a: 1, b: 1.0, c: 'asdf', d: false)").coerceToString();
    ExpressionSubject.assertThatExpression(compile)
        .evaluatesTo(
            DictImpl.forProviderMap(
                    ImmutableMap.<String, SoyValue>of(
                        "a", IntegerData.forValue(1),
                        "b", FloatData.forValue(1.0),
                        "c", StringData.forValue("asdf"),
                        "d", BooleanData.FALSE),
                    RuntimeMapTypeTracker.Type.LEGACY_OBJECT_MAP_OR_RECORD)
                .toString());
  }

  @Test
  public void testNegativeOpNode() {
    assertExpression("-1").evaluatesTo(-1L);
    assertExpression("-1.0").evaluatesTo(-1.0);

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forInt(constant(1L))));
    assertExpression("-$foo").evaluatesTo(IntegerData.forValue(-1));

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(1D))));
    assertExpression("-$foo").evaluatesTo(FloatData.forValue(-1.0));
  }

  @Test
  public void testModOpNode() {
    assertExpression("3 % 2").evaluatesTo(1L);
    assertExpression("5 % 3").evaluatesTo(2L);
    // TODO(b/19833234): the soy type checker should flag this, but it doesn't.
    try {
      compileExpression("5.0 % 3.0");
      fail();
    } catch (Exception expected) {
    }

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forInt(constant(3L))));
    variables.put("bar", untypedBoxedSoyExpression(SoyExpression.forInt(constant(2L))));
    assertExpression("$foo % $bar").evaluatesTo(1L);

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(3.0))));
    variables.put("bar", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(2.0))));
    assertExpression("$foo % $bar").throwsException(SoyDataException.class);
  }

  @Test
  public void testDivideByOpNode() {
    assertExpression("3 / 2").evaluatesTo(1.5); // note the coercion to floating point
    assertExpression("4.2 / 2").evaluatesTo(2.1);

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(3.0))));
    variables.put("bar", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(2.0))));
    assertExpression("$foo / $bar").evaluatesTo(1.5);
  }

  @Test
  public void testTimesOpNode() {
    assertExpression("4.2 * 2").evaluatesTo(8.4);
    assertExpression("4 * 2").evaluatesTo(8L);

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(3.0))));
    variables.put("bar", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(2.0))));
    assertExpression("$foo * $bar").evaluatesTo(FloatData.forValue(6.0));
  }

  @Test
  public void testMinusOpNode() {
    assertExpression("4.2 - 2").evaluatesTo(2.2);
    assertExpression("4 - 2").evaluatesTo(2L);

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(3.0))));
    variables.put("bar", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(2.0))));
    assertExpression("$foo - $bar").evaluatesTo(FloatData.forValue(1.0));
  }

  @Test
  public void testPlusOpNode() {
    assertExpression("4.2 + 2").evaluatesTo(6.2);
    assertExpression("4 + 2").evaluatesTo(6L);
    assertExpression("4 + '2'").evaluatesTo("42");
    assertExpression("'4' + 2").evaluatesTo("42");

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forString(constant("foo"))));
    assertExpression("$foo + 2").evaluatesTo(StringData.forValue("foo2"));
    assertExpression("$foo + '2'").evaluatesTo("foo2"); // Note, not boxed

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forInt(constant(1L))));
    assertExpression("$foo + 2").evaluatesTo(IntegerData.forValue(3));
    assertExpression("$foo + '2'").evaluatesTo("12");
  }

  @Test
  public void testNotOpNode() {
    assertExpression("not false").evaluatesTo(true);
    assertExpression("not true").evaluatesTo(false);

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forString(constant("foo"))));
    assertExpression("not $foo").evaluatesTo(false);

    variables.put("foo", untypedBoxedSoyExpression(SoyExpression.forString(constant(""))));
    assertExpression("not $foo").evaluatesTo(true); // empty string is falsy
  }

  @Test
  public void testComparisonOperators() {
    variables.put("oneInt", untypedBoxedSoyExpression(SoyExpression.forInt(constant(1L))));
    variables.put("oneFloat", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(1.0))));
    variables.put("twoInt", untypedBoxedSoyExpression(SoyExpression.forInt(constant(2L))));
    variables.put("twoFloat", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(2.0))));
    variables.put("oneStr", untypedBoxedSoyExpression(SoyExpression.forString(constant("kill"))));
    variables.put("twoStr", untypedBoxedSoyExpression(SoyExpression.forString(constant("zoo"))));

    for (String one : ImmutableList.of("1", "1.0", "$oneInt", "$oneFloat")) {
      for (String two : ImmutableList.of("2", "2.0", "$twoInt", "$twoFloat")) {
        assertExpression(one + " < " + two).evaluatesTo(true);
        assertExpression(one + " < " + one).evaluatesTo(false);
        assertExpression(two + " < " + one).evaluatesTo(false);

        assertExpression(one + " <= " + two).evaluatesTo(true);
        assertExpression(one + " <= " + one).evaluatesTo(true);
        assertExpression(two + " <= " + one).evaluatesTo(false);

        assertExpression(one + " > " + two).evaluatesTo(false);
        assertExpression(one + " > " + one).evaluatesTo(false);
        assertExpression(two + " > " + one).evaluatesTo(true);

        assertExpression(one + " >= " + two).evaluatesTo(false);
        assertExpression(one + " >= " + one).evaluatesTo(true);
        assertExpression(two + " >= " + one).evaluatesTo(true);

        assertExpression(one + " == " + two).evaluatesTo(false);
        assertExpression(one + " == " + one).evaluatesTo(true);
        assertExpression(two + " == " + one).evaluatesTo(false);

        assertExpression(one + " != " + two).evaluatesTo(true);
        assertExpression(one + " != " + one).evaluatesTo(false);
        assertExpression(two + " != " + one).evaluatesTo(true);
      }
    }

    // Unit tests for string comparisons.
    for (String one : ImmutableList.of("'bar'", "'hello'", "$oneStr")) {
      for (String two : ImmutableList.of("'too'", "'world'", "$twoStr")) {
        assertExpression(one + " < " + two).evaluatesTo(true);
        assertExpression(one + " < " + one).evaluatesTo(false);
        assertExpression(two + " < " + one).evaluatesTo(false);

        assertExpression(one + " <= " + two).evaluatesTo(true);
        assertExpression(one + " <= " + one).evaluatesTo(true);
        assertExpression(two + " <= " + one).evaluatesTo(false);

        assertExpression(one + " > " + two).evaluatesTo(false);
        assertExpression(one + " > " + one).evaluatesTo(false);
        assertExpression(two + " > " + one).evaluatesTo(true);

        assertExpression(one + " >= " + two).evaluatesTo(false);
        assertExpression(one + " >= " + one).evaluatesTo(true);
        assertExpression(two + " >= " + one).evaluatesTo(true);

        assertExpression(one + " == " + two).evaluatesTo(false);
        assertExpression(one + " == " + one).evaluatesTo(true);
        assertExpression(two + " == " + one).evaluatesTo(false);

        assertExpression(one + " != " + two).evaluatesTo(true);
        assertExpression(one + " != " + one).evaluatesTo(false);
        assertExpression(two + " != " + one).evaluatesTo(true);
      }
    }
  }

  @Test
  public void testConditionalOperators() {
    variables.put("true", untypedBoxedSoyExpression(SoyExpression.TRUE));
    variables.put("false", untypedBoxedSoyExpression(SoyExpression.FALSE));
    String trueExpr = "$true";
    String falseExpr = "$false";
    assertExpression(falseExpr + " or " + falseExpr).evaluatesTo(false);
    assertExpression(falseExpr + " or " + trueExpr).evaluatesTo(true);
    assertExpression(trueExpr + " or " + falseExpr).evaluatesTo(true);
    assertExpression(trueExpr + " or " + trueExpr).evaluatesTo(true);

    assertExpression(falseExpr + " and " + falseExpr).evaluatesTo(false);
    assertExpression(falseExpr + " and " + trueExpr).evaluatesTo(false);
    assertExpression(trueExpr + " and " + falseExpr).evaluatesTo(false);
    assertExpression(trueExpr + " and " + trueExpr).evaluatesTo(true);
  }

  // The arithmetic types are handled by testComparisonOperators, the == and != operators have
  // extra semantics for strings as well as boxed fallback
  @Test
  public void testEqualOpNode() {
    assertExprNotEquals("'asdf'", "12.0");
    assertExprEquals("'12'", "12.0");
    assertExprEquals("'12.0'", "12.0");
    assertExprEquals("'12.0'", "'12.0'");
    assertExprEquals("'asdf'", "'asdf'");

    variables.put("str", untypedBoxedSoyExpression(SoyExpression.forString(constant("foo"))));
    assertExprEquals("$str", "'foo'");
    assertExprNotEquals("$str", "'bar'");

    variables.put("intStr", untypedBoxedSoyExpression(SoyExpression.forString(constant("12"))));
    assertExprEquals("$intStr", "'12'");
    assertExprEquals("$intStr", "12");
    assertExprNotEquals("$intStr", "'bar'");

    variables.put("floatStr", untypedBoxedSoyExpression(SoyExpression.forFloat(constant(12.0))));
    assertExprEquals("$floatStr", "'12'");
    assertExprEquals("$floatStr", "12");
    assertExprNotEquals("$floatStr", "'bar'");

    assertExprEquals("null", "null");
    assertExprNotEquals("'a'", "null");
    assertExprNotEquals("null", "'a'");
  }

  @Test
  public void testConditionalOpNode() {
    assertExpression("false ? 1 : 2").evaluatesTo(2L);
    assertExpression("true ? 1 : 2").evaluatesTo(1L);

    assertExpression("false ? 1.0 : 2").evaluatesTo(IntegerData.forValue(2));
    assertExpression("true ? 1 : 2.0").evaluatesTo(IntegerData.forValue(1));

    assertExpression("false ? 'a' : 'b'").evaluatesTo("b");
    assertExpression("true ? 'a' : 'b'").evaluatesTo("a");

    // note the boxing
    assertExpression("false ? 'a' : 2").evaluatesTo(IntegerData.forValue(2));
    assertExpression("true ? 1 : 'b'").evaluatesTo(IntegerData.forValue(1));
    assertExpression("false ? 1 : 'b'").evaluatesTo(StringData.forValue("b"));
    assertExpression("true ? 'a' : 2").evaluatesTo(StringData.forValue("a"));
  }

  // conditional op expression have had a number of bugs due previous implementations that
  // aggressively unboxed operands
  @Test
  public void testConditionalOpNode_advanced() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody("{@param? p : string}", "{$p ? $p : '' }");
    tester.rendersAs("", ImmutableMap.of());
    tester.rendersAs("hello", ImmutableMap.<String, Object>of("p", "hello"));
    tester =
        assertThatTemplateBody(
            "{@param? p : map<string, string>}", "{if $p}", "  {$p['key']}", "{/if}");
    tester.rendersAs("", ImmutableMap.of());
    tester = assertThatTemplateBody("{@param? p : string}", "{$p ? $p : 1 }");
    tester.rendersAs("1", ImmutableMap.of());
    tester.rendersAs("hello", ImmutableMap.<String, Object>of("p", "hello"));

    tester = assertThatTemplateBody("{@param p : int}", "{$p ? 1 : $p }");
    tester.rendersAs("0", ImmutableMap.<String, Object>of("p", 0));
    tester.rendersAs("1", ImmutableMap.<String, Object>of("p", 2));

    tester =
        assertThatTemplateBody(
            "{@param b : bool}", "{@param v : list<int>}", "{$b ? $v[0] : $v[1] + 1}");
    tester.rendersAs("null", ImmutableMap.of("b", true, "v", Arrays.asList()));
    tester.rendersAs("3", ImmutableMap.of("b", false, "v", Arrays.asList(1, 2)));
  }

  @Test
  public void testNullCoalescingOpNode() {
    assertExpression("1 ?: 2").evaluatesTo(1L);
    // force the type checker to interpret the left hand side as a nullable string, the literal null
    // is rejected by the type checker.
    assertExpression("(true ? null : 'a') ?: 2").evaluatesTo(IntegerData.forValue(2));
    assertExpression("(true ? null : 'a') ?: 'b'").evaluatesTo(StringData.forValue("b"));
    assertExpression("(false ? null : 'a') ?: 'b'").evaluatesTo(StringData.forValue("a"));

    variables.put(
        "p1", untypedBoxedSoyExpression(SoyExpression.forString(constantNull(STRING_TYPE))));
    variables.put("p2", SoyExpression.forString(constant("a")).box());
    assertExpression("$p1 ?: $p2").evaluatesTo(StringData.forValue("a"));

    SoyType htmlType = SanitizedType.getTypeForContentKind(SanitizedContentKind.HTML);
    variables.put(
        "p1",
        SoyExpression.forSoyValue(
            htmlType,
            MethodRef.ORDAIN_AS_SAFE.invoke(constant("<b>hello</b>"), constant(ContentKind.HTML))));
    variables.put("p2", SoyExpression.forString(constant("")).box());
    assertExpression("$p1 ?: $p2").evaluatesTo(SanitizedContents.constantHtml("<b>hello</b>"));
    variables.put(
        "p1",
        SoyExpression.forSoyValue(htmlType, constantNull(Type.getType(SanitizedContent.class)))
            .asNullable());
    assertExpression("$p1 ?: $p2").evaluatesTo(StringData.forValue(""));
  }

  // null coalescing op expression have had a number of bugs due to the advanced unboxing
  // conversions forcing unnecessary NullPointerExceptions
  @Test
  public void testNullCoalescingOpNode_advanced() {
    CompiledTemplateSubject tester =
        assertThatTemplateBody("{@param v : list<string>}", "{$v[0] ?: $v[1] }");
    tester.rendersAs("null", ImmutableMap.<String, Object>of("v", Arrays.asList()));
    tester.rendersAs("b", ImmutableMap.<String, Object>of("v", Arrays.asList(null, "b")));
    tester.rendersAs("a", ImmutableMap.<String, Object>of("v", Arrays.asList("a", "b")));
  }

  @Test
  public void testCheckNotNull() {
    assertExpression("checkNotNull(1 < 2 ? null : 'a')")
        .throwsException(NullPointerException.class, "'1 < 2 ? null : 'a'' evaluates to null");
    assertExpression("checkNotNull('a')").evaluatesTo("a");
  }

  @Test
  public void testItemAccess_lists() {
    variables.put("list", compileExpression("[0, 1, 2]").box());
    // By default all values are boxed
    assertExpression("$list[0]").evaluatesTo(IntegerData.forValue(0));
    assertExpression("$list[1]").evaluatesTo(IntegerData.forValue(1));
    assertExpression("$list[2]").evaluatesTo(IntegerData.forValue(2));

    // However, they will be unboxed if possible
    assertExpression("$list[0] + 1").evaluatesTo(1L);

    // null, not IndexOutOfBoundsException
    assertExpression("$list[3]").evaluatesTo(null);
    assertExpression("$list[3] + 1").throwsException(NullPointerException.class);

    // even if the index type is not known, it still works
    variables.put("anInt", untypedBoxedSoyExpression(SoyExpression.forInt(constant(1L))));
    assertExpression("$list[$anInt]").evaluatesTo(IntegerData.forValue(1));
    // And we can still unbox the return value
    assertExpression("$list[$anInt] + 1").evaluatesTo(2L);
    variables.put("anInt", untypedBoxedSoyExpression(SoyExpression.forInt(constant(3L))));
    assertExpression("$list[$anInt]").evaluatesTo(null);
  }

  @Test
  public void testItemAccess_maps() {
    variables.put(
        "map", compileExpression("mapToLegacyObjectMap(map('a': 0, 'b': 1, 'c': 2))").box());
    // By default all values are boxed
    assertExpression("$map['a']").evaluatesTo(IntegerData.forValue(0));
    assertExpression("$map['b']").evaluatesTo(IntegerData.forValue(1));
    assertExpression("$map['c']").evaluatesTo(IntegerData.forValue(2));
    assertExpression("$map['not valid']").evaluatesTo(null);
  }

  @Test
  public void testNullSafeItemAccess_map() {
    // Note: due to bugs in the type resolver (b/20537225) we can't properly type this variable
    // so instead we have to lie about the nullability of this map.
    variables.put(
        "nullMap",
        SoyExpression.forSoyValue(
            LegacyObjectMapType.of(StringType.getInstance(), IntType.getInstance()),
            BytecodeUtils.constantNull(Type.getType(SoyLegacyObjectMap.class))));
    assertExpression("$nullMap['a']").throwsException(NullPointerException.class);
    assertExpression("$nullMap?['a']").evaluatesTo(null);
  }

  @Test
  public void testNullSafeItemAccess_list() {
    variables.put(
        "nullList",
        SoyExpression.forSoyValue(
            ListType.of(StringType.getInstance()),
            BytecodeUtils.constantNull(Type.getType(SoyList.class))));
    assertExpression("$nullList[1]")
        .doesNotContainCode("IFNULL")
        .doesNotContainCode("IFNNONULL") // no null checks
        .throwsException(NullPointerException.class);
    assertExpression("$nullList?[1]").evaluatesTo(null);
  }

  @Test
  public void testFieldAccess() {
    variables.put("record", compileExpression("record(a: 0, b: 1, c: 2)").box());
    // By default all values are boxed
    assertExpression("$record.a").evaluatesTo(IntegerData.forValue(0));
    assertExpression("$record.b").evaluatesTo(IntegerData.forValue(1));
    assertExpression("$record.c").evaluatesTo(IntegerData.forValue(2));

    // However, they will be unboxed if possible
    assertExpression("$record.a + 1").evaluatesTo(1L);
  }

  @Test
  public void testNullSafeFieldAccess() {
    variables.put(
        "nullRecord",
        SoyExpression.forSoyValue(
            SoyTypes.makeNullable(RecordType.of(ImmutableMap.of("a", StringType.getInstance()))),
            BytecodeUtils.constantNull(Type.getType(SoyDict.class))));
    assertExpression("$nullRecord.a").throwsException(NullPointerException.class);
    assertExpression("$nullRecord?.a").evaluatesTo(null);
  }

  @Test
  public void testBuiltinFunctions() {
    variables.put("x", compileExpression("record(a: 1)").box());
    variables.put(
        "y",
        SoyExpression.forSoyValue(
            SoyTypes.makeNullable(FloatType.getInstance()),
            BytecodeUtils.constantNull(Type.getType(FloatData.class))));
    assertExpression("checkNotNull($x.a)").evaluatesTo(IntegerData.forValue(1));
    assertExpression("checkNotNull($y)").throwsException(NullPointerException.class);
  }

  @Test
  public void testMaxAndMin() {
    assertExpression("min(2, 3)").evaluatesTo(IntegerData.forValue(2L));
    assertExpression("max(2, 3)").evaluatesTo(IntegerData.forValue(3L));

    assertExpression("min(0.1, 1.1)").evaluatesTo(FloatData.forValue(0.1));
    assertExpression("max(0.1, 1.1)").evaluatesTo(FloatData.forValue(1.1));
  }

  private void assertExprEquals(String left, String right) {
    assertExpression(left + " == " + right).evaluatesTo(true);
    assertExpression(left + " != " + right).evaluatesTo(false);
  }

  private void assertExprNotEquals(String left, String right) {
    assertExpression(left + " == " + right).evaluatesTo(false);
    assertExpression(left + " != " + right).evaluatesTo(true);
  }

  private ExpressionSubject assertExpression(String soyExpr) {
    SoyExpression compile = compileExpression(soyExpr);
    return ExpressionSubject.assertThatExpression(compile);
  }

  private SoyExpression compileExpression(String soyExpr) {
    ImmutableMap.Builder<String, SoyType> types = ImmutableMap.builder();
    for (Map.Entry<String, SoyExpression> variable : variables.entrySet()) {
      types.put(variable.getKey(), variable.getValue().soyType());
    }
    // The fake function allows us to work around the 'can't print bool' restrictions
    String createTemplateBody =
        SharedTestUtils.createTemplateBodyForExpression(
            "fakeFunction(" + soyExpr + ")", types.build());
    PrintNode code =
        (PrintNode)
            ((TemplateNode)
                    SoyFileSetParserBuilder.forTemplateContents(createTemplateBody)
                        .errorReporter(ErrorReporter.explodeOnErrorsAndIgnoreWarnings())
                        .addSoyFunction(
                            new SoyFunction() {
                              @Override
                              public String getName() {
                                return "fakeFunction";
                              }

                              @Override
                              public Set<Integer> getValidArgsSizes() {
                                return ImmutableSet.of(1);
                              }
                            })
                        .parse()
                        .fileSet()
                        .getChild(0)
                        .getChild(0))
                .getChild(0);
    return testExpressionCompiler.compile(
        ((FunctionNode) code.getExpr().getChild(0)).getChild(0),
        new ExpressionDetacher.Factory() {
          @Override
          public ExpressionDetacher createExpressionDetacher(Label label) {
            return new ExpressionDetacher() {
              @Override
              public Expression resolveSoyValueProvider(Expression soyValueProvider) {
                if (variables.containsValue(soyValueProvider)) {
                  // This is hacky, but our variables are not SVPs, just SoyValues.  This is
                  // inconsistent with reality but makes the tests easier to write.
                  // A better solution may be to have the variables map just hold expressions for
                  // SoyValueProviders, but that is annoying.
                  return soyValueProvider;
                }
                return MethodRef.SOY_VALUE_PROVIDER_RESOLVE.invoke(soyValueProvider);
              }

              @Override
              public Expression resolveSoyValueProviderList(Expression soyValueProviderList) {
                throw new UnsupportedOperationException();
              }

              @Override
              public Expression resolveSoyValueProviderMap(Expression soyValueProviderMap) {
                throw new UnsupportedOperationException();
              }
            };
          }
        });
  }

  /**
   * This helper can take a SoyExpression and essentially strip type information from it, this is
   * useful for testing fallback implementations in the compiler.
   */
  private SoyExpression untypedBoxedSoyExpression(final SoyExpression expr) {
    return SoyExpression.forSoyValue(UnknownType.getInstance(), expr.box());
  }
}
