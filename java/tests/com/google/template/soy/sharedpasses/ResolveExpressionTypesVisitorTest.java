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

package com.google.template.soy.sharedpasses;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.types.SoyTypes.makeNullable;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.template.soy.FormattingErrorReporter;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
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

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for ResolveNamesVisitor.
 *
 */
public final class ResolveExpressionTypesVisitorTest extends TestCase {

  private static final SoyTypeProvider typeProvider =
      new SoyTypeProvider() {
        @Override
        public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
          if (typeName.equals("unknown")) {
            return UnknownType.getInstance();
          }
          return null;
        }
      };

  private static final SoyTypeRegistry typeRegistry =
      new SoyTypeRegistry(ImmutableSet.of(typeProvider));

  private static ResolveNamesVisitor createResolveNamesVisitorForMaxSyntaxVersion() {
    return createResolveNamesVisitor(SyntaxVersion.V9_9);
  }

  private static ResolveNamesVisitor createResolveNamesVisitor(
      SyntaxVersion declaredSyntaxVersion) {
    return new ResolveNamesVisitor(declaredSyntaxVersion, ExplodingErrorReporter.get());
  }

  private static ResolveExpressionTypesVisitor
      createResolveExpressionTypesVisitorForMaxSyntaxVersion() {
    return createResolveExpressionTypesVisitor(SyntaxVersion.V9_9);
  }

  private static ResolveExpressionTypesVisitor createResolveExpressionTypesVisitor(
      SyntaxVersion declaredSyntaxVersion) {
    return new ResolveExpressionTypesVisitor(
        typeRegistry, declaredSyntaxVersion, ExplodingErrorReporter.get());
  }

  public void testOptionalParamTypes() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param? pa: bool}",
        "{@param? pb: list<int>}",
        "{$pa}",
        "{$pb}"))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0))
        .isEqualTo(makeNullable(BoolType.getInstance()));
    assertThat(types.get(1))
        .isEqualTo(makeNullable(ListType.of(IntType.getInstance())));
  }

  public void testDataRefTypes() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param pa: bool}",
        "{@param pb: list<int>}",
        "{@param pe: map<int, map<int, string>>}",
        "{$pa}",
        "{$pb}",
        "{$pb[0]}",
        "{$pe}",
        "{$pe[0]}",
        "{$pe[1 + 1][2]}"))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(ListType.of(IntType.getInstance()));
    assertThat(types.get(2)).isEqualTo(IntType.getInstance());
    assertThat(types.get(3))
        .isEqualTo(MapType.of(
            IntType.getInstance(), MapType.of(IntType.getInstance(), StringType.getInstance())));
    assertThat(types.get(4)).isEqualTo(MapType.of(IntType.getInstance(), StringType.getInstance()));
    assertThat(types.get(5)).isEqualTo(StringType.getInstance());
  }

  public void testRecordTypes() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param pa: [a:int, b:string]}",
        "{$pa.a}",
        "{$pa.b}"))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
    assertThat(types.get(1)).isEqualTo(StringType.getInstance());
  }

  public void testDataRefTypesWithUnknown() {
    // Test that data with the 'unknown' type is allowed to function as a map or list.
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param pa: unknown}",
            "{@param pb: map<string, float>}",
            "{@param pc: map<int, string>}",
            "{$pa[0]}",
            "{$pa.xxx}",
            "{$pa.xxx.yyy}",
            "{$pb[$pa]}",
            "{$pc[$pa]}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(2)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(3)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(4)).isEqualTo(StringType.getInstance());
  }

  public void testDataRefTypesError() {
    // Should fail because pa key type should be string, not int
    assertResolveExpressionTypesFails(
        "Invalid key type",
        constructTemplateSource(
            "{@param pa: map<string, float>}",
            "{$pa[0]}"));

    // Should fail because pa key type should be int, not bool
    assertResolveExpressionTypesFails(
        "Invalid key type",
        constructTemplateSource(
            "{@param pa: map<int, float>}",
            "{@param pb: bool}",
            "{$pa[$pb]}"));
  }

  public void testRecordTypesError() {
    // Should fail because key 'c' does not exist.
    assertResolveExpressionTypesFails(
        "Undefined field",
        constructTemplateSource(
            "{@param pa: [a:int, b:float]}",
            "{$pa.c}"));
  }

  public void testArithmeticOps() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param pa: unknown}",
            "{@param pi: int}",
            "{@param pf: float}",
            "{$pa + $pa}",
            "{$pi + $pi}",
            "{$pf + $pf}",
            "{$pa - $pa}",
            "{$pi - $pi}",
            "{$pf - $pf}",
            "{$pa * $pa}",
            "{$pi * $pi}",
            "{$pf * $pf}",
            "{$pa / $pa}",
            "{$pi / $pi}",
            "{$pf / $pf}",
            "{$pa % $pa}",
            "{$pi % $pi}",
            "{$pf % $pf}",
            "{-$pa}",
            "{-$pi}",
            "{-$pf}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(IntType.getInstance());
    assertThat(types.get(2)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(3)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(4)).isEqualTo(IntType.getInstance());
    assertThat(types.get(5)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(6)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(7)).isEqualTo(IntType.getInstance());
    assertThat(types.get(8)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(9)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(10)).isEqualTo(IntType.getInstance());
    assertThat(types.get(11)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(12)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(13)).isEqualTo(IntType.getInstance());
    assertThat(types.get(14)).isEqualTo(FloatType.getInstance());
    assertThat(types.get(15)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(16)).isEqualTo(IntType.getInstance());
    assertThat(types.get(17)).isEqualTo(FloatType.getInstance());
  }

  public void testStringConcatenation() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param ps: string}",
            "{@param pi: int}",
            "{@param pf: float}",
            "{@param pb: bool}",
            "{$ps + $ps}",
            "{$ps + $pi}",
            "{$ps + $pf}",
            "{$ps + $pb}",
            "{$pi + $ps}",
            "{$pf + $ps}",
            "{$pb + $ps}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(StringType.getInstance());
    assertThat(types.get(1)).isEqualTo(StringType.getInstance());
    assertThat(types.get(2)).isEqualTo(StringType.getInstance());
    assertThat(types.get(3)).isEqualTo(StringType.getInstance());
    assertThat(types.get(4)).isEqualTo(StringType.getInstance());
    assertThat(types.get(5)).isEqualTo(StringType.getInstance());
    assertThat(types.get(6)).isEqualTo(StringType.getInstance());
  }

  public void testLogicalOps() {
    String testTemplateContent = constructTemplateSource(
        "{@param pa: unknown}",
        "{@param pi: int}",
        "{@param pf: float}",
        "{$pa and $pa}",
        "{$pi and $pi}",
        "{$pf and $pf}",
        "{$pa or $pa}",
        "{$pi or $pi}",
        "{$pf or $pf}",
        "{not $pa}",
        "{not $pi}",
        "{not $pf}");

    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(testTemplateContent)
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitor(SyntaxVersion.V2_0).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_0).exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(2)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(3)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(4)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(5)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(6)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(7)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(8)).isEqualTo(BoolType.getInstance());

    soyTree = SoyFileSetParserBuilder.forFileContents(testTemplateContent)
        .declaredSyntaxVersion(SyntaxVersion.V2_3)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitor(SyntaxVersion.V2_3).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_3).exec(soyTree);
    types = getPrintStatementTypes(soyTree);
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

  public void testComparisonOps() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param pa: unknown}",
            "{@param pi: int}",
            "{@param pf: float}",
            "{$pa > $pa}",
            "{$pi > $pi}",
            "{$pf > $pf}",
            "{$pa >= $pa}",
            "{$pi >= $pi}",
            "{$pf >= $pf}",
            "{$pa < $pa}",
            "{$pi < $pi}",
            "{$pf < $pf}",
            "{$pa <= $pa}",
            "{$pi <= $pi}",
            "{$pf <= $pf}",
            "{$pa == $pa}",
            "{$pi == $pi}",
            "{$pf == $pf}",
            "{$pa != $pa}",
            "{$pi != $pi}",
            "{$pf != $pf}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    ImmutableSet<SoyType> types = ImmutableSet.copyOf(getPrintStatementTypes(soyTree));
    assertThat(types).containsExactly(BoolType.getInstance());
  }

  public void testNullCoalescingAndConditionalOps() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param pa: unknown}",
            "{@param pi: int}",
            "{@param pf: float}",
            "{@param? ni: int}",
            "{$pa ?: $pi}",
            "{$pi ?: $pf}",
            "{$pa ? $pi : $pf}",
            "{$ni ?: 0}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
    assertThat(types.get(2))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
    assertThat(types.get(3)).isEqualTo(IntType.getInstance());
  }

  public void testNullCoalescingAndConditionalOps_complexCondition() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param? l: [a :int]}",
            "{$l?.a ?: 0}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
  }

  public void testListLiteral() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param pi: int}",
            "{@param pf: float}",
            "{let $list: [$pi, $pf]/}",
            "{$list}",
            "{$list.length}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0))
        .isEqualTo(ListType.of(UnionType.of(IntType.getInstance(), FloatType.getInstance())));
    assertThat(types.get(1)).isEqualTo(IntType.getInstance());
  }

  public void testMapLiteral() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param pi: int}",
            "{@param pf: float}",
            "{let $map: [1: $pi, 2:$pf]/}",
            "{$map}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    SoyType type = Iterables.getOnlyElement(getPrintStatementTypes(soyTree));
    assertThat(type)
        .isEqualTo(MapType.of(
            IntType.getInstance(), UnionType.of(IntType.getInstance(), FloatType.getInstance())));
  }

  public void testMapLiteralWithStringKeysAsMap() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param v1: int}",
            "{@param v2: string}",
            "{@param k1: string}",
            "{let $map: [$k1: $v1, 'b': $v2] /}",
            "{$map}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    SoyType type = Iterables.getOnlyElement(getPrintStatementTypes(soyTree));
    assertThat(type)
        .isEqualTo(
            MapType.of(
                StringType.getInstance(),
                UnionType.of(StringType.getInstance(), IntType.getInstance())));
  }

  public void testMapLiteralAsRecord() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{@param pi: int}",
            "{@param pf: float}",
            "{let $map: ['a': $pi, 'b':$pf]/}",
            "{$map}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0))
        .isEqualTo(RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(), "b", FloatType.getInstance())));
  }

  public void testMapLiteralAsRecord_duplicateKeys() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(
        constructTemplateSource(
            "{let $map: ['a': 1, 'a': 2]/}"))
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    FormattingErrorReporter reporter = new FormattingErrorReporter();
    new ResolveExpressionTypesVisitor(typeRegistry, SyntaxVersion.V9_9, reporter).exec(soyTree);
    assertThat(Iterables.getOnlyElement(reporter.getErrorMessages()))
        .isEqualTo("Record literals with duplicate keys are not allowed.  Duplicate key: 'a'");
  }

  public void testDataFlowTypeNarrowing() {
    SoyType boolOrNullType = makeNullable(BoolType.getInstance());
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param pa: bool|null}",
        "{@param pb: bool}",
        "{if $pa != null}",
        "  {$pa}", // #0 must be non-null
        "{/if}",
        "{if $pa == null}",
        "  {$pa}", // #1 must be null
        "{else}",
        "  {$pa}", // #2 must be non-null
        "{/if}",
        "{if $pa == null or $pb}",
        "  {$pa}", // #3 don't know
        "{else}",
        "  {$pa}", // #4 must be non-null
        "{/if}",
        "{if $pa == null and $pb}",
        "  {$pa}", // #5 must be null
        "{else}",
        "  {$pa}", // #6 don't know
        "{/if}",
        "{if null != $pa}", // Reverse order
        "  {$pa}", // #7 must be non-null
        "{/if}",
        "{if not ($pa == null)}", // Not operator
        "  {$pa}", // #8 must be non-null
        "{/if}",
        "{if $pa}", // Implicit != null
        "  {$pa}", // #9 must be non-null
        "{/if}",
        "{if $pa and $pb}", // Implicit != null
        "  {$pa}", // #10 must be non-null
        "{/if}",
        "{if $pa}", // Chained conditions
        "{elseif $pb}",
        "  {$pa}", // #11 must be falsy
        "{else}",
        "  {$pa}", // #12 must be falsy
        "{/if}",
        "{if $pa}", // Nested if
        "  {if $pa}",
        "    {$pa}", // #13 must be non-null
        "  {/if}",
        "{/if}",
        "{if isNonnull($pa)}", // isNonnull function
        "  {$pa}", // #14 must be non-null
        "{else}",
        "  {$pa}", // #15 must be null
        "{/if}",
        "{if $pb or $pa == null}",
        "  {$pa}",  // #16 don't know
        "{else}",
        "  {$pa}",  // #17 must be null
        "{/if}",
        ""))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
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

    assertThat(types.get(16))
        .isEqualTo(makeNullable(BoolType.getInstance()));
    assertThat(types.get(17)).isEqualTo(BoolType.getInstance());
  }

  public void testDataFlowTypeNarrowing_complexExpressions() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param map: map<string, int|null>}",
        "{@param record: [a : [nullableInt : int|null, nullableBool : bool|null]|null]}",
        "{@param pb: bool}",
        "{if $map['a']}",
        "  {$map['a']}",
        "{/if}",
        "{if $record.a?.nullableInt}",
        "  {$record.a?.nullableInt}",
        "{/if}",
        ""))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
    assertThat(types.get(1)).isEqualTo(IntType.getInstance());
  }

  public void testDataFlowTypeNarrowing_deadExpression() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param record: ?}",
        "{if $record.unknownField}",
        "  {$record.unknownField}",
        "{else}",
        "  {if $record.unknownField}",
        "    {$record.unknownField}",  // This code is dead, but we can't prove it
        "  {/if}",
        "{/if}",
        ""))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(UnknownType.getInstance());
    assertThat(types.get(1)).isEqualTo(UnknownType.getInstance());
  }

  public void testDataFlowTypeNarrowing_logicalExpressions() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param? record: [active : bool|null]}",
        "{@param? selected: map<string,bool>}",
        "{$selected and $selected['a']}",
        "{$selected == null or $selected['a']}",
        "{if isNonnull($record.active) and (not $record.active)}",
        "  {$record.active}",
        "{/if}",
        ""))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
  }

  public void testDataFlowTypeNarrowingFailure() {
    // Test for places where type narrowing shouldn't work
    SoyType boolOrNullType = makeNullable(BoolType.getInstance());;
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param pa: bool|null}",
        "{@param pb: bool}",
        "{if ($pa != null) != ($pb != null)}",
        "  {$pa}", // #0 don't know
        "{else}",
        "  {$pa}", // #1 don't know
        "{/if}",
        "{if $pa ?: $pb}",
        "  {$pa}", // #2 don't know
        "{/if}",
        "{if $pb ? $pa : false}",
        "  {$pa}", // #3 don't know
        "{/if}"))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(boolOrNullType);
    assertThat(types.get(1)).isEqualTo(boolOrNullType);
    assertThat(types.get(2)).isEqualTo(boolOrNullType);
    assertThat(types.get(3)).isEqualTo(boolOrNullType);
  }

  public void testConditionalOperatorDataFlowTypeNarrowing() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@param pa: bool|null}",
        "{@param pb: bool}",
        "{@param pc: [a : int|null]}",
        "{$pa ? $pa : $pb}", // #0 must be non-null
        "{$pa != null ?: $pb}", // #1 must be non-null
        "{$pa ?: $pb}",
        "{$pc.a ? $pc.a : 0}",
        "{if not $pc.a}{$pc.a}{/if}"))
        .parse(); // #2 must be non-null (re-written to (isNonnull($pa) ? $pa : $pb))
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(3)).isEqualTo(IntType.getInstance());
    assertThat(types.get(4)).isEqualTo(makeNullable(IntType.getInstance()));
  }

  public void testFunctionTyping() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@inject list: list<int|null>}",
        "{foreach $item in $list}",
        "   {index($item)}",
        "   {isLast($item)}",
        "   {isFirst($item)}",
        "   {$item}",
        "   {checkNotNull($item)}",
        "{/foreach}"))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(IntType.getInstance());
    assertThat(types.get(1)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(2)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(3)).isEqualTo(makeNullable(IntType.getInstance()));
    assertThat(types.get(4)).isEqualTo(IntType.getInstance());
  }

  public void testInjectedParamTypes() {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(constructTemplateSource(
        "{@inject pa: bool}",
        "{@inject? pb: list<int>}",
        "{$pa}",
        "{$pb}"))
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertThat(types.get(0)).isEqualTo(BoolType.getInstance());
    assertThat(types.get(1)).isEqualTo(makeNullable(ListType.of(IntType.getInstance())));
  }

  /**
   * Helper function that constructs a boilerplate template given a list of body
   * statements to insert into the middle of the template. The body statements will be
   * indented and separated with newlines.
   * @param body The body statements.
   * @return The combined template.
   */
  private String constructTemplateSource(String... body) {
    return "" +
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  " + Joiner.on("\n   ").join(body) + "\n" +
        "{/template}\n";
  }

  /**
   * Assertions function that checks to make sure that name resolution fails with the
   * expected exception.
   * @param fileContent The template source.
   * @param expectedError The expected failure message (a substring).
   */
  private void assertResolveExpressionTypesFails(String expectedError, String fileContent) {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(fileContent)
        .declaredSyntaxVersion(SyntaxVersion.V2_0)
        .doRunInitialParsingPasses(false)
        .typeRegistry(typeRegistry)
        .parse();
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    try {
      createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
      fail("Expected SoySyntaxException");
    } catch (SoySyntaxException e) {
      assertThat(e.getMessage()).contains(expectedError);
    }
  }

  /**
   * Helper function that gathers all of the print statements within a Soy tree, and returns
   * a list of their types, that is a list of SoyType objects representing the type of the
   * expression that would have been printed.
   * @param node The root of the tree.
   * @return A list of expression types.
   */
  private List<SoyType> getPrintStatementTypes(SoyNode node) {
    CollectPrintStatementTypesVisitor visitor = new CollectPrintStatementTypesVisitor(
        ExplodingErrorReporter.get());
    visitor.exec(node);
    return visitor.getTypes();
  }

  /**
   * Test helper class that scarfs up all soy print nodes, and records the type of
   * the expression printed.
   */
  public static class CollectPrintStatementTypesVisitor extends AbstractSoyNodeVisitor<Void> {
    private final List<SoyType> types = Lists.newArrayList();

    public CollectPrintStatementTypesVisitor(ErrorReporter errorReporter) {
      super(errorReporter);
    }

    public List<SoyType> getTypes() {
      return types;
    }

    @Override protected void visitPrintNode(PrintNode node) {
      types.add(node.getExprUnion().getExpr().getType());
    }

    @Override protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
