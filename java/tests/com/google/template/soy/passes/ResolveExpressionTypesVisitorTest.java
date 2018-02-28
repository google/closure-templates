/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ResolveExpressionTypesVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class ResolveExpressionTypesVisitorTest {
  private static final SoyFunction ASSERT_TYPE_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "assertType";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(2);
        }
      };

  private static final SoyTypeRegistry TYPE_REGISTRY = new SoyTypeRegistry();

  private static ResolveExpressionTypesVisitor createResolveExpressionTypesVisitor(
      SyntaxVersion declaredSyntaxVersion) {
    return new ResolveExpressionTypesVisitor(
        TYPE_REGISTRY, declaredSyntaxVersion, ErrorReporter.exploding());
  }

  @Test
  public void testOptionalParamTypes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param? pa: bool}",
                    "{@param? pb: list<int>}",
                    "{assertType('bool|null', $pa)}",
                    "{assertType('list<int>|null', $pb)}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testDataRefTypes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool}",
                    "{@param pb: list<int>}",
                    "{@param pe: map<int, map<int, string>>}",
                    "{assertType('bool', $pa)}",
                    "{assertType('list<int>', $pb)}",
                    "{assertType('int', $pb[0])}",
                    "{assertType('map<int,map<int,string>>', $pe)}",
                    "{assertType('map<int,string>', $pe[0])}",
                    "{assertType('string', $pe[1 + 1][2])}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testRecordTypes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: [a:int, b:string]}",
                    "{assertType('int', $pa.a)}",
                    "{assertType('string', $pa.b)}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testDataRefTypesWithUnknown() {
    // Test that data with the 'unknown' type is allowed to function as a map or list.
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: ?}",
                    "{@param pb: map<string, float>}",
                    "{@param pc: map<int, string>}",
                    "{assertType('?', $pa[0])}",
                    "{assertType('?', $pa.xxx)}",
                    "{assertType('?', $pa.xxx.yyy)}",
                    "{assertType('float', $pb[$pa])}",
                    "{assertType('string', $pc[$pa])}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .typeRegistry(TYPE_REGISTRY)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testDataRefTypesError() {
    assertResolveExpressionTypesFails(
        "Bad key type int for map<string,float>.",
        constructTemplateSource("{@param pa: map<string, float>}", "{$pa[0]}"));

    assertResolveExpressionTypesFails(
        "Bad key type bool for map<int,float>.",
        constructTemplateSource("{@param pa: map<int, float>}", "{@param pb: bool}", "{$pa[$pb]}"));
  }

  @Test
  public void testRecordTypesError() {
    assertResolveExpressionTypesFails(
        "Undefined field 'c' for record type [a: int, bb: float]. Did you mean 'a'?",
        constructTemplateSource("{@param pa: [a:int, bb:float]}", "{$pa.c}"));
  }

  @Test
  public void testArithmeticOps() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: ?}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{assertType('?', $pa + $pa)}",
                    "{assertType('int', $pi + $pi)}",
                    "{assertType('float', $pf + $pf)}",
                    "{assertType('?', $pa - $pa)}",
                    "{assertType('int', $pi - $pi)}",
                    "{assertType('float', $pf - $pf)}",
                    "{assertType('?', $pa * $pa)}",
                    "{assertType('int', $pi * $pi)}",
                    "{assertType('float', $pf * $pf)}",
                    "{assertType('float', $pa / $pa)}",
                    "{assertType('float', $pi / $pi)}",
                    "{assertType('float', $pf / $pf)}",
                    "{assertType('?', $pa % $pa)}",
                    "{assertType('int', $pi % $pi)}",
                    "{assertType('float', $pf % $pf)}",
                    "{assertType('?', -$pa)}",
                    "{assertType('int', -$pi)}",
                    "{assertType('float', -$pf)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .typeRegistry(TYPE_REGISTRY)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testArithmeticTypesError() {
    assertResolveExpressionTypesFails(
        "Using arithmetic operators on Soy types 'string' and 'string' is illegal.",
        constructTemplateSource("{'a' / 'b'}"));
  }

  @Test
  public void testStringConcatenation() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param ps: string}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{assertType('string', $ps + $ps)}",
                    "{assertType('string', $ps + $pi)}",
                    "{assertType('string', $ps + $pf)}",
                    "{assertType('string', $pi + $ps)}",
                    "{assertType('string', $pf + $ps)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testLogicalOps() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: ?}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{assertType('?', $pa and $pa)}",
                    "{assertType('?', $pi and $pi)}",
                    "{assertType('?', $pf and $pf)}",
                    "{assertType('?', $pa or $pa)}",
                    "{assertType('?', $pi or $pi)}",
                    "{assertType('?', $pf or $pf)}",
                    "{assertType('bool', not $pa)}",
                    "{assertType('bool', not $pi)}",
                    "{assertType('bool', not $pf)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    new ResolveNamesVisitor(ErrorReporter.exploding()).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_0).exec(soyTree);
    assertTypes(soyTree);

    soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: ?}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{assertType('bool', $pa and $pa)}",
                    "{assertType('bool', $pi and $pi)}",
                    "{assertType('bool', $pf and $pf)}",
                    "{assertType('bool', $pa or $pa)}",
                    "{assertType('bool', $pi or $pi)}",
                    "{assertType('bool', $pf or $pf)}",
                    "{assertType('bool', not $pa)}",
                    "{assertType('bool', not $pi)}",
                    "{assertType('bool', not $pf)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_3)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    new ResolveNamesVisitor(ErrorReporter.exploding()).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_3).exec(soyTree);
    assertTypes(soyTree);
  }

  @Test
  public void testComparisonOps() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: ?}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{assertType('bool', $pa > $pa)}",
                    "{assertType('bool', $pi > $pi)}",
                    "{assertType('bool', $pf > $pf)}",
                    "{assertType('bool', $pa >= $pa)}",
                    "{assertType('bool', $pi >= $pi)}",
                    "{assertType('bool', $pf >= $pf)}",
                    "{assertType('bool', $pa < $pa)}",
                    "{assertType('bool', $pi < $pi)}",
                    "{assertType('bool', $pf < $pf)}",
                    "{assertType('bool', $pa <= $pa)}",
                    "{assertType('bool', $pi <= $pi)}",
                    "{assertType('bool', $pf <= $pf)}",
                    "{assertType('bool', $pa == $pa)}",
                    "{assertType('bool', $pi == $pi)}",
                    "{assertType('bool', $pf == $pf)}",
                    "{assertType('bool', $pa != $pa)}",
                    "{assertType('bool', $pi != $pi)}",
                    "{assertType('bool', $pf != $pf)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testNullCoalescingAndConditionalOps() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: ?}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{@param? ni: int}",
                    "{assertType('?', $pa ?: $pi)}",
                    "{assertType('float|int', $pi ?: $pf)}",
                    "{assertType('float|int', $pa ? $pi : $pf)}",
                    "{assertType('int', $ni ?: 0)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testNullCoalescingAndConditionalOps_complexCondition() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource("{@param? l: [a :int]}", "{assertType('int', $l?.a ?: 0)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testListLiteral() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{let $list: [$pi, $pf]/}",
                    "{assertType('list<float|int>', $list)}",
                    "{assertType('int', length($list))}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_4)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testMapLiteral() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{let $map: map(1: $pi, 2:$pf)/}",
                    "{assertType('map<int,float|int>', $map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testLegacyObjectMapLiteral() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{let $map: [1: $pi, 2:$pf]/}",
                    "{assertType('legacy_object_map<int,float|int>', $map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testMapLiteralWithStringKeysAsMap() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param v1: int}",
                    "{@param v2: string}",
                    "{@param k1: string}",
                    "{let $map: map($k1: $v1, 'b': $v2) /}",
                    "{assertType('map<string,int|string>', $map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testLegacyObjectMapLiteralWithStringKeysAsMap() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param v1: int}",
                    "{@param v2: string}",
                    "{@param k1: string}",
                    "{let $map: [$k1: $v1, 'b': $v2] /}",
                    "{assertType('legacy_object_map<string,int|string>', $map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testMapLiteralWithStringLiteralKeysDoesNotCreateRecord() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    // With the old map syntax, this would create a record type (see next test)
                    "{let $map: map('a': $pi, 'b':$pf)/}",
                    "{assertType('map<string,float|int>', $map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testLegacyObjectMapLiteralAsRecord() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{let $map: ['a': $pi, 'b':$pf]/}",
                    "{assertType('[a: int, b: float]', $map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testMapLiteral_duplicateKeys() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(
            constructTemplateSource("{let $map: map('a': 1, 'a': 2)/}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .errorReporter(reporter)
        .typeRegistry(TYPE_REGISTRY)
        .parse()
        .fileSet();
    assertThat(Iterables.getOnlyElement(reporter.getErrors()).message())
        .isEqualTo("Map literals with duplicate keys are not allowed.  Duplicate key: 'a'");
  }

  @Test
  public void testLegacyObjectMapLiteralAsRecord_duplicateKeys() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(
            constructTemplateSource("{let $map: ['a': 1, 'a': 2]/}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .errorReporter(reporter)
        .typeRegistry(TYPE_REGISTRY)
        .parse()
        .fileSet();
    assertThat(Iterables.getOnlyElement(reporter.getErrors()).message())
        .isEqualTo("Record literals with duplicate keys are not allowed.  Duplicate key: 'a'");
  }

  @Test
  public void testDataFlowTypeNarrowing() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool|null}",
                    "{@param pb: bool}",
                    "{if $pa != null}",
                    "  {assertType('bool', $pa)}", // #0 must be non-null
                    "{/if}",
                    "{if $pa == null}",
                    "  {assertType('null', $pa)}", // #1 must be null
                    "{else}",
                    "  {assertType('bool', $pa)}", // #2 must be non-null
                    "{/if}",
                    "{if $pa == null or $pb}",
                    "  {assertType('bool|null', $pa)}", // #3 don't know
                    "{else}",
                    "  {assertType('bool', $pa)}", // #4 must be non-null
                    "{/if}",
                    "{if $pa == null and $pb}",
                    "  {assertType('null', $pa)}", // #5 must be null
                    "{else}",
                    "  {assertType('bool|null', $pa)}", // #6 don't know
                    "{/if}",
                    "{if null != $pa}", // Reverse order
                    "  {assertType('bool', $pa)}", // #7 must be non-null
                    "{/if}",
                    "{if not ($pa == null)}", // Not operator
                    "  {assertType('bool', $pa)}", // #8 must be non-null
                    "{/if}",
                    "{if $pa}", // Implicit != null
                    "  {assertType('bool', $pa)}", // #9 must be non-null
                    "{/if}",
                    "{if $pa and $pb}", // Implicit != null
                    "  {assertType('bool', $pa)}", // #10 must be non-null
                    "{/if}",
                    "{if $pa}", // Chained conditions
                    "{elseif $pb}",
                    "  {assertType('bool|null', $pa)}", // #11 must be falsy
                    "{else}",
                    "  {assertType('bool|null', $pa)}", // #12 must be falsy
                    "{/if}",
                    "{if $pa}", // Nested if
                    "  {if $pa}",
                    "    {assertType('bool', $pa)}", // #13 must be non-null
                    "  {/if}",
                    "{/if}",
                    "{if isNonnull($pa)}", // isNonnull function
                    "  {assertType('bool', $pa)}", // #14 must be non-null
                    "{else}",
                    "  {assertType('null', $pa)}", // #15 must be null
                    "{/if}",
                    "{if isNull($pa)}", // isNull function
                    "  {assertType('null', $pa)}", // #16 must be null
                    "{else}",
                    "  {assertType('bool', $pa)}", // #17 must be non-null
                    "{/if}",
                    "{if $pb or $pa == null}",
                    "  {assertType('bool|null', $pa)}", // #18 don't know
                    "{else}",
                    "  {assertType('bool', $pa)}", // #19 must be non-null
                    "{/if}",
                    "{let $null: null /}",
                    "{if $null == null or $null != null}",
                    "  {assertType('null', $null)}", // #20  null type
                    "{/if}",
                    "{if $null}",
                    "  {assertType('null', $null)}", // #21 null type (but this branch is dead)
                    "{/if}",
                    ""))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testDataFlowTypeNarrowing_complexExpressions() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param map: map<string, int|null>}",
                    "{@param record: "
                        + "[a : [nullableInt : int|null, nullableBool : bool|null]|null]}",
                    "{if $map['a']}",
                    "  {assertType('int', $map['a'])}",
                    "{/if}",
                    "{if $record.a?.nullableInt}",
                    "  {assertType('int', $record.a?.nullableInt)}",
                    "{/if}",
                    ""))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testDataFlowTypeNarrowing_deadExpression() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param record: ?}",
                    "{if $record.unknownField}",
                    "  {assertType('?', $record.unknownField)}",
                    "{else}",
                    "  {if $record.unknownField}",
                    // This code is dead, but we can't prove it
                    "    {assertType('?', $record.unknownField)}",
                    "  {/if}",
                    "{/if}",
                    ""))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testDataFlowTypeNarrowing_logicalExpressions() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param? record: [active : bool|null]}",
                    "{@param? selected: map<string,bool>}",
                    "{assertType('bool', $selected and $selected['a'])}",
                    "{assertType('bool', $selected == null or $selected['a'])}",
                    "{if isNonnull($record.active) and (not $record.active)}",
                    "  {assertType('bool', $record.active)}",
                    "{/if}",
                    ""))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .declaredSyntaxVersion(SyntaxVersion.V2_4)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testDataFlowTypeNarrowingFailure() {
    // Test for places where type narrowing shouldn't work
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool|null}",
                    "{@param pb: bool}",
                    "{if ($pa != null) != ($pb != null)}",
                    "  {assertType('bool|null', $pa)}", // #0 don't know
                    "{else}",
                    "  {assertType('bool|null', $pa)}", // #1 don't know
                    "{/if}",
                    "{if $pa ?: $pb}",
                    "  {assertType('bool|null', $pa)}", // #2 don't know
                    "{/if}",
                    "{if $pb ? $pa : false}",
                    "  {assertType('bool|null', $pa)}", // #3 don't know
                    "{/if}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testConditionalOperatorDataFlowTypeNarrowing() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool|null}",
                    "{@param pb: bool}",
                    "{@param pc: [a : int|null]}",
                    "{assertType('bool', $pa ? $pa : $pb)}", // #0 must be non-null
                    "{assertType('bool', $pa != null ?: $pb)}", // #1 must be non-null
                    "{assertType('bool', $pa ?: $pb)}",
                    "{assertType('int', $pc.a ? $pc.a : 0)}",
                    "{if not $pc.a}{assertType('int|null', $pc.a)}{/if}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet(); // #2 must be non-null (re-written to (isNonnull($pa) ? $pa : $pb))
    assertTypes(soyTree);
  }

  @Test
  public void testBuiltinFunctionTyping() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@inject list: list<int|null>}",
                    "{for $item in $list}",
                    "   {assertType('int', index($item))}",
                    "   {assertType('bool', isLast($item))}",
                    "   {assertType('bool', isFirst($item))}",
                    "   {assertType('int|null', $item)}",
                    "   {assertType('int', checkNotNull($item))}",
                    "   {assertType('string', css('foo'))}",
                    "   {assertType('string', xid('bar'))}",
                    "{/for}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testProtoInitTyping() {
    SoyTypeRegistry typeRegistry =
        new SoyTypeRegistry.Builder()
            .addDescriptors(ImmutableList.of(ExampleExtendable.getDescriptor()))
            .build();

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{let $proto: example.ExampleExtendable() /}",
                    "{assertType('example.ExampleExtendable', $proto)}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .typeRegistry(typeRegistry)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testBadForEach() {
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type int.",
        constructTemplateSource("{@param p: int}", "{for $item in $p}{/for}"));
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type int|string.",
        constructTemplateSource("{@param p: int|string}", "{for $item in $p}{/for}"));
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type list<string>|string|uri.",
        constructTemplateSource("{@param p: list<string>|string|uri}", "{for $item in $p}{/for}"));
  }

  @Test
  public void testInjectedParamTypes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@inject pa: bool}",
                    "{@inject? pb: list<int>}",
                    "{assertType('bool', $pa)}",
                    "{assertType('list<int>|null', $pb)}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testMapKeys() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param m: map<string, int>}",
                    "{assertType('list<string>', mapKeys($m))}",
                    "{assertType('list<null>', mapKeys(map()))}",
                    ""))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testMapToLegacyObjectMap() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param m: map<string, int>}",
                    "{assertType('legacy_object_map<string,int>', mapToLegacyObjectMap($m))}",
                    "{assertType('legacy_object_map<null,null>', mapToLegacyObjectMap(map()))}",
                    ""))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testErrorMessagesInUnionTypes() {
    assertResolveExpressionTypesFails(
        "Type float does not support bracket access.",
        constructTemplateSource("{@param p: float|int}", "{$p[1]}"));

    assertResolveExpressionTypesFails(
        "Type float does not support dot access.",
        constructTemplateSource("{@param p: float|int}", "{$p.a}"));
  }

  @Test
  public void testTypeNarrowingError() {
    assertResolveExpressionTypesFails(
        "Expected expression of type 'string', found 'null'.",
        constructTemplateSource(
            "{@param p: [a: string]}",
            "{if $p.a != null}",
            "  x: {$p.a}",
            "{else}",
            "  y: {$p.a}",
            "{/if}"));
  }

  @Test
  public void testTypeParser() {
    SoyType type = parseSoyType("string");
    assertThat(type).isEqualTo(StringType.getInstance());
    type = parseSoyType("int");
    assertThat(type).isEqualTo(IntType.getInstance());
    type = parseSoyType("list<any>");
    assertThat(type.getKind()).isEqualTo(SoyType.Kind.LIST);
    assertThat(((ListType) type).getElementType()).isEqualTo(AnyType.getInstance());
    type = parseSoyType("map<string, ?>");
    assertThat(type.getKind()).isEqualTo(Kind.MAP);
    assertThat(((MapType) type).getKeyType()).isEqualTo(StringType.getInstance());
    assertThat(((MapType) type).getValueType()).isEqualTo(UnknownType.getInstance());
  }


  private SoyType parseSoyType(String type) {
    return parseSoyType(type, ErrorReporter.exploding());
  }

  private SoyType parseSoyType(String type, ErrorReporter errorReporter) {
    return SoyFileParser.parseType(
        type, TYPE_REGISTRY, "com.google.foo.bar.FakeSoyFunction", errorReporter);
  }

  /**
   * Helper function that constructs a boilerplate template given a list of body statements to
   * insert into the middle of the template. The body statements will be indented and separated with
   * newlines.
   *
   * @param body The body statements.
   * @return The combined template.
   */
  private static String constructTemplateSource(String... body) {
    return ""
        + "{namespace ns}\n"
        + "/***/\n"
        + "{template .aaa}\n"
        + "  "
        + Joiner.on("\n   ").join(body)
        + "\n"
        + "{/template}\n";
  }

  /**
   * Assertions function that checks to make sure that name resolution fails with the expected
   * exception.
   *
   * @param fileContent The template source.
   * @param expectedError The expected failure message (a substring).
   */
  private void assertResolveExpressionTypesFails(String expectedError, String fileContent) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(fileContent)
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .errorReporter(errorReporter)
        .typeRegistry(TYPE_REGISTRY)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(errorReporter.getErrors().get(0).message()).isEqualTo(expectedError);
  }

  /** Traverses the tree and checks all the calls to {@code assertType} */
  private void assertTypes(SoyNode node) {
    for (FunctionNode fn : SoyTreeUtils.getAllNodesOfType(node, FunctionNode.class)) {
      if (fn.getFunctionName().equals("assertType")) {
        StringNode expected = (StringNode) fn.getChild(0);
        SoyType actualType = fn.getChild(1).getType();
        assertWithMessage("assertion @ " + fn.getSourceLocation())
            .that(actualType.toString())
            .isEqualTo(expected.getValue());
      }
    }
  }
}
