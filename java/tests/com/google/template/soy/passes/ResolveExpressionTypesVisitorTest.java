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
import static com.google.template.soy.types.SoyTypes.makeNullable;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ResolveExpressionTypesVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class ResolveExpressionTypesVisitorTest {

  private static final SoyFunction CAPTURE_TYPE_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "captureType";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(1);
        }
      };

  private static final SoyTypeProvider TYPE_PROVIDER =
      new SoyTypeProvider() {
        @Override
        public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
          if (typeName.equals("unknown")) {
            return UnknownType.getInstance();
          }
          return null;
        }
      };

  private static final SoyTypeRegistry TYPE_REGISTRY =
      new SoyTypeRegistry(ImmutableSet.of(TYPE_PROVIDER));

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
                    "{captureType($pa)}",
                    "{captureType($pb)}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(makeNullable(BoolType.getInstance()));
    assertThat(types.get(1)).isEqualTo(makeNullable(ListType.of(IntType.getInstance())));
  }

  @Test
  public void testDataRefTypes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool}",
                    "{@param pb: list<int>}",
                    "{@param pe: map<int, map<int, string>>}",
                    "{captureType($pa)}",
                    "{captureType($pb)}",
                    "{captureType($pb[0])}",
                    "{captureType($pe)}",
                    "{captureType($pe[0])}",
                    "{captureType($pe[1 + 1][2])}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(ListType.of(IntType.getInstance()));
    assertThat(types.get(2)).isEqualTo(IntType.getInstance());
    assertThat(types.get(3))
        .isEqualTo(
            MapType.of(
                IntType.getInstance(),
                MapType.of(IntType.getInstance(), StringType.getInstance())));
    assertThat(types.get(4)).isEqualTo(MapType.of(IntType.getInstance(), StringType.getInstance()));
    assertThat(types.get(5)).isEqualTo(StringType.getInstance());
  }

  @Test
  public void testRecordTypes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: [a:int, b:string]}",
                    "{captureType($pa.a)}",
                    "{captureType($pa.b)}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
    assertThat(types.get(1)).isEqualTo(StringType.getInstance());
  }

  @Test
  public void testDataRefTypesWithUnknown() {
    // Test that data with the 'unknown' type is allowed to function as a map or list.
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: unknown}",
                    "{@param pb: map<string, float>}",
                    "{@param pc: map<int, string>}",
                    "{captureType($pa[0])}",
                    "{captureType($pa.xxx)}",
                    "{captureType($pa.xxx.yyy)}",
                    "{captureType($pb[$pa])}",
                    "{captureType($pc[$pa])}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .typeRegistry(TYPE_REGISTRY)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(2)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(3)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(4)).isEqualTo(StringType.getInstance());
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
        "Undefined field 'c' for record type [a: int, bb: float].",
        constructTemplateSource("{@param pa: [a:int, bb:float]}", "{$pa.c}"));
  }

  @Test
  public void testArithmeticOps() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: unknown}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{@param ps: string}",
                    "{captureType($pa + $pa)}",
                    "{captureType($pi + $pi)}",
                    "{captureType($pf + $pf)}",
                    "{captureType($pa - $pa)}",
                    "{captureType($pi - $pi)}",
                    "{captureType($pf - $pf)}",
                    "{captureType($pa * $pa)}",
                    "{captureType($pi * $pi)}",
                    "{captureType($pf * $pf)}",
                    "{captureType($pa / $pa)}",
                    "{captureType($pi / $pi)}",
                    "{captureType($pf / $pf)}",
                    "{captureType($pa % $pa)}",
                    "{captureType($pi % $pi)}",
                    "{captureType($pf % $pf)}",
                    "{captureType(-$pa)}",
                    "{captureType(-$pi)}",
                    "{captureType(-$pf)}",
                    // The remainder are all logically template errors but are not enforced by the
                    // compiler
                    "{captureType(-$ps)}",
                    "{captureType($ps / $pf)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .typeRegistry(TYPE_REGISTRY)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(IntType.getInstance());
    assertThat(types.get(2)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(3)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(4)).isEqualTo(IntType.getInstance());
    assertThat(types.get(5)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(6)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(7)).isEqualTo(IntType.getInstance());
    assertThat(types.get(8)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(9)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(10)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(11)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(12)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(13)).isEqualTo(IntType.getInstance());
    assertThat(types.get(14)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(15)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(16)).isEqualTo(IntType.getInstance());
    assertThat(types.get(17)).isEqualTo(FloatType.getInstance());

    // These are the 'error' cases
    assertThat(types.get(18)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(19)).isEqualTo(UnknownType.getInstance());
  }

  @Test
  @Ignore
  public void testArithmeticTypesError() {
    assertResolveExpressionTypesFails(
        "Incompatible types in arithmetic expression. Expected int or float",
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
                    "{captureType($ps + $ps)}",
                    "{captureType($ps + $pi)}",
                    "{captureType($ps + $pf)}",
                    "{captureType($pi + $ps)}",
                    "{captureType($pf + $ps)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(StringType.getInstance());
    assertThat(types.get(1)).isEqualTo(StringType.getInstance());
    assertThat(types.get(2)).isEqualTo(StringType.getInstance());
    assertThat(types.get(3)).isEqualTo(StringType.getInstance());
    assertThat(types.get(4)).isEqualTo(StringType.getInstance());
  }

  @Test
  public void testLogicalOps() {
    String testTemplateContent =
        constructTemplateSource(
            "{@param pa: unknown}",
            "{@param pi: int}",
            "{@param pf: float}",
            "{captureType($pa and $pa)}",
            "{captureType($pi and $pi)}",
            "{captureType($pf and $pf)}",
            "{captureType($pa or $pa)}",
            "{captureType($pi or $pi)}",
            "{captureType($pf or $pf)}",
            "{captureType(not $pa)}",
            "{captureType(not $pi)}",
            "{captureType(not $pf)}");

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testTemplateContent)
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    new ResolveNamesVisitor(ErrorReporter.exploding()).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_0).exec(soyTree);
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(2)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(3)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(4)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(5)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(6)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(7)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(8)).isEqualTo(BoolType.getInstance());

    soyTree =
        SoyFileSetParserBuilder.forFileContents(testTemplateContent)
            .declaredSyntaxVersion(SyntaxVersion.V2_3)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    new ResolveNamesVisitor(ErrorReporter.exploding()).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_3).exec(soyTree);
    types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(3)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(4)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(5)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(6)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(7)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(8)).isEqualTo(BoolType.getInstance());
  }

  @Test
  public void testComparisonOps() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: unknown}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{captureType($pa > $pa)}",
                    "{captureType($pi > $pi)}",
                    "{captureType($pf > $pf)}",
                    "{captureType($pa >= $pa)}",
                    "{captureType($pi >= $pi)}",
                    "{captureType($pf >= $pf)}",
                    "{captureType($pa < $pa)}",
                    "{captureType($pi < $pi)}",
                    "{captureType($pf < $pf)}",
                    "{captureType($pa <= $pa)}",
                    "{captureType($pi <= $pi)}",
                    "{captureType($pf <= $pf)}",
                    "{captureType($pa == $pa)}",
                    "{captureType($pi == $pi)}",
                    "{captureType($pf == $pf)}",
                    "{captureType($pa != $pa)}",
                    "{captureType($pi != $pi)}",
                    "{captureType($pf != $pf)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    ImmutableSet<SoyType> types = ImmutableSet.copyOf(getCapturedTypes(soyTree));
    assertThat(types).containsExactly(BoolType.getInstance());
  }

  @Test
  public void testNullCoalescingAndConditionalOps() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: unknown}",
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{@param? ni: int}",
                    "{captureType($pa ?: $pi)}",
                    "{captureType($pi ?: $pf)}",
                    "{captureType($pa ? $pi : $pf)}",
                    "{captureType($ni ?: 0)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
    assertThat(types.get(2))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
    assertThat(types.get(3)).isEqualTo(IntType.getInstance());
  }

  @Test
  public void testNullCoalescingAndConditionalOps_complexCondition() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource("{@param? l: [a :int]}", "{captureType($l?.a ?: 0)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
  }

  @Test
  public void testListLiteral() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{let $list: [$pi, $pf]/}",
                    "{captureType($list)}",
                    "{captureType(length($list))}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_4)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0))
        .isEqualTo(ListType.of(UnionType.of(IntType.getInstance(), FloatType.getInstance())));
    assertThat(types.get(1)).isEqualTo(UnknownType.getInstance());
  }

  @Test
  public void testMapLiteral() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{let $map: [1: $pi, 2:$pf]/}",
                    "{captureType($map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    SoyType type = Iterables.getOnlyElement(getCapturedTypes(soyTree));
    assertThat(type)
        .isEqualTo(
            MapType.of(
                IntType.getInstance(),
                UnionType.of(IntType.getInstance(), FloatType.getInstance())));
  }

  @Test
  public void testMapLiteralWithStringKeysAsMap() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param v1: int}",
                    "{@param v2: string}",
                    "{@param k1: string}",
                    "{let $map: [$k1: $v1, 'b': $v2] /}",
                    "{captureType($map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    SoyType type = Iterables.getOnlyElement(getCapturedTypes(soyTree));
    assertThat(type)
        .isEqualTo(
            MapType.of(
                StringType.getInstance(),
                UnionType.of(StringType.getInstance(), IntType.getInstance())));
  }

  @Test
  public void testMapLiteralAsRecord() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pi: int}",
                    "{@param pf: float}",
                    "{let $map: ['a': $pi, 'b':$pf]/}",
                    "{captureType($map)}"))
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0))
        .isEqualTo(
            RecordType.of(
                ImmutableMap.<String, SoyType>of(
                    "a", IntType.getInstance(), "b", FloatType.getInstance())));
  }

  @Test
  public void testMapLiteralAsRecord_duplicateKeys() {
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
    SoyType boolOrNullType = makeNullable(BoolType.getInstance());
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool|null}",
                    "{@param pb: bool}",
                    "{if $pa != null}",
                    "  {captureType($pa)}", // #0 must be non-null
                    "{/if}",
                    "{if $pa == null}",
                    "  {captureType($pa)}", // #1 must be null
                    "{else}",
                    "  {captureType($pa)}", // #2 must be non-null
                    "{/if}",
                    "{if $pa == null or $pb}",
                    "  {captureType($pa)}", // #3 don't know
                    "{else}",
                    "  {captureType($pa)}", // #4 must be non-null
                    "{/if}",
                    "{if $pa == null and $pb}",
                    "  {captureType($pa)}", // #5 must be null
                    "{else}",
                    "  {captureType($pa)}", // #6 don't know
                    "{/if}",
                    "{if null != $pa}", // Reverse order
                    "  {captureType($pa)}", // #7 must be non-null
                    "{/if}",
                    "{if not ($pa == null)}", // Not operator
                    "  {captureType($pa)}", // #8 must be non-null
                    "{/if}",
                    "{if $pa}", // Implicit != null
                    "  {captureType($pa)}", // #9 must be non-null
                    "{/if}",
                    "{if $pa and $pb}", // Implicit != null
                    "  {captureType($pa)}", // #10 must be non-null
                    "{/if}",
                    "{if $pa}", // Chained conditions
                    "{elseif $pb}",
                    "  {captureType($pa)}", // #11 must be falsy
                    "{else}",
                    "  {captureType($pa)}", // #12 must be falsy
                    "{/if}",
                    "{if $pa}", // Nested if
                    "  {if $pa}",
                    "    {captureType($pa)}", // #13 must be non-null
                    "  {/if}",
                    "{/if}",
                    "{if isNonnull($pa)}", // isNonnull function
                    "  {captureType($pa)}", // #14 must be non-null
                    "{else}",
                    "  {captureType($pa)}", // #15 must be null
                    "{/if}",
                    "{if isNull($pa)}", // isNull function
                    "  {captureType($pa)}", // #16 must be null
                    "{else}",
                    "  {captureType($pa)}", // #17 must be non-null
                    "{/if}",
                    "{if $pb or $pa == null}",
                    "  {captureType($pa)}", // #18 don't know
                    "{else}",
                    "  {captureType($pa)}", // #19 must be null
                    "{/if}",
                    "{let $null: null /}",
                    "{if $null == null or $null != null}",
                    "  {captureType($null)}", // #20  null type
                    "{/if}",
                    "{if $null}",
                    "  {captureType($null)}", // #21 null type (but this branch is dead)
                    "{/if}",
                    ""))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(NullType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(3)).isEqualTo(boolOrNullType);
    assertThat(types.get(4)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(5)).isEqualTo(NullType.getInstance());
    assertThat(types.get(6)).isEqualTo(boolOrNullType);
    assertThat(types.get(7)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(8)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(9)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(10)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(11)).isEqualTo(makeNullable(BoolType.getInstance()));
    assertThat(types.get(12)).isEqualTo(makeNullable(BoolType.getInstance()));
    assertThat(types.get(13)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(14)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(15)).isEqualTo(NullType.getInstance());
    assertThat(types.get(16)).isEqualTo(NullType.getInstance());
    assertThat(types.get(17)).isEqualTo(BoolType.getInstance());

    assertThat(types.get(18)).isEqualTo(makeNullable(BoolType.getInstance()));
    assertThat(types.get(19)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(20)).isEqualTo(NullType.getInstance());
    assertThat(types.get(21)).isEqualTo(NullType.getInstance());
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
                    "  {captureType($map['a'])}",
                    "{/if}",
                    "{if $record.a?.nullableInt}",
                    "  {captureType($record.a?.nullableInt)}",
                    "{/if}",
                    ""))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
    assertThat(types.get(1)).isEqualTo(IntType.getInstance());
  }

  @Test
  public void testDataFlowTypeNarrowing_deadExpression() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param record: ?}",
                    "{if $record.unknownField}",
                    "  {captureType($record.unknownField)}",
                    "{else}",
                    "  {if $record.unknownField}",
                    // This code is dead, but we can't prove it
                    "    {captureType($record.unknownField)}",
                    "  {/if}",
                    "{/if}",
                    ""))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(UnknownType.getInstance());
  }

  @Test
  public void testDataFlowTypeNarrowing_logicalExpressions() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param? record: [active : bool|null]}",
                    "{@param? selected: map<string,bool>}",
                    "{captureType($selected and $selected['a'])}",
                    "{captureType($selected == null or $selected['a'])}",
                    "{if isNonnull($record.active) and (not $record.active)}",
                    "  {captureType($record.active)}",
                    "{/if}",
                    ""))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .declaredSyntaxVersion(SyntaxVersion.V2_4)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
  }

  @Test
  public void testDataFlowTypeNarrowingFailure() {
    // Test for places where type narrowing shouldn't work
    SoyType boolOrNullType = makeNullable(BoolType.getInstance());
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool|null}",
                    "{@param pb: bool}",
                    "{if ($pa != null) != ($pb != null)}",
                    "  {captureType($pa)}", // #0 don't know
                    "{else}",
                    "  {captureType($pa)}", // #1 don't know
                    "{/if}",
                    "{if $pa ?: $pb}",
                    "  {captureType($pa)}", // #2 don't know
                    "{/if}",
                    "{if $pb ? $pa : false}",
                    "  {captureType($pa)}", // #3 don't know
                    "{/if}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(boolOrNullType);
    assertThat(types.get(1)).isEqualTo(boolOrNullType);
    assertThat(types.get(2)).isEqualTo(boolOrNullType);
    assertThat(types.get(3)).isEqualTo(boolOrNullType);
  }

  @Test
  public void testConditionalOperatorDataFlowTypeNarrowing() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool|null}",
                    "{@param pb: bool}",
                    "{@param pc: [a : int|null]}",
                    "{captureType($pa ? $pa : $pb)}", // #0 must be non-null
                    "{captureType($pa != null ?: $pb)}", // #1 must be non-null
                    "{captureType($pa ?: $pb)}",
                    "{captureType($pc.a ? $pc.a : 0)}",
                    "{if not $pc.a}{captureType($pc.a)}{/if}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet(); // #2 must be non-null (re-written to (isNonnull($pa) ? $pa : $pb))
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(3)).isEqualTo(IntType.getInstance());
    assertThat(types.get(4)).isEqualTo(makeNullable(IntType.getInstance()));
  }

  @Test
  public void testBuiltinFunctionTyping() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@inject list: list<int|null>}",
                    "{foreach $item in $list}",
                    "   {captureType(index($item))}",
                    "   {captureType(isLast($item))}",
                    "   {captureType(isFirst($item))}",
                    "   {captureType($item)}",
                    "   {captureType(checkNotNull($item))}",
                    "   {captureType(css('foo'))}",
                    "   {captureType(xid('bar'))}",
                    "{/foreach}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
    assertThat(types.get(1)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(3)).isEqualTo(makeNullable(IntType.getInstance()));
    assertThat(types.get(4)).isEqualTo(IntType.getInstance());
    assertThat(types.get(5)).isEqualTo(StringType.getInstance());
    assertThat(types.get(6)).isEqualTo(StringType.getInstance());
  }

  @Test
  public void testProtoInitTyping() {
    SoyTypeRegistry typeRegistry =
        new SoyTypeRegistry(
            ImmutableSet.<SoyTypeProvider>of(
                new SoyProtoTypeProvider.Builder()
                    .addDescriptors(ExampleExtendable.getDescriptor())
                    .buildNoFiles()));

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{let $proto: example.ExampleExtendable() /}", "{captureType($proto)}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .typeRegistry(typeRegistry)
            .parse()
            .fileSet();

    SoyType type = Iterables.getOnlyElement(getCapturedTypes(soyTree));
    assertThat(type)
        .isEqualTo(typeRegistry.getType(ExampleExtendable.getDescriptor().getFullName()));
  }

  @Test
  public void testBadForEach() {
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type int.",
        constructTemplateSource("{@param p: int}", "{foreach $item in $p}{/foreach}"));
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type int|string.",
        constructTemplateSource("{@param p: int|string}", "{foreach $item in $p}{/foreach}"));
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type list<string>|string|uri.",
        constructTemplateSource(
            "{@param p: list<string>|string|uri}", "{foreach $item in $p}{/foreach}"));
  }

  @Test
  public void testInjectedParamTypes() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@inject pa: bool}",
                    "{@inject? pb: list<int>}",
                    "{captureType($pa)}",
                    "{captureType($pb)}"))
            .addSoyFunction(CAPTURE_TYPE_FUNCTION)
            .parse()
            .fileSet();
    List<SoyType> types = getCapturedTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(makeNullable(ListType.of(IntType.getInstance())));
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
        + "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
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
    assertThat(errorReporter.getErrors().get(0).message()).contains(expectedError);
  }

  /**
   * Helper function that gathers all of the print statements within a Soy tree, and returns a list
   * of their types, that is a list of SoyType objects representing the type of the expression that
   * would have been printed.
   *
   * @param node The root of the tree.
   * @return A list of expression types.
   */
  private List<SoyType> getCapturedTypes(SoyNode node) {
    List<SoyType> types = new ArrayList<>();
    for (FunctionNode fn : SoyTreeUtils.getAllNodesOfType(node, FunctionNode.class)) {
      if (fn.getFunctionName().equals("captureType")) {
        types.add(fn.getChild(0).getType());
      }
    }
    return types;
  }
}
