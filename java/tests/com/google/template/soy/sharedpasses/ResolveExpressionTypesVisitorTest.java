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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.shared.internal.SharedTestUtils;
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
public class ResolveExpressionTypesVisitorTest extends TestCase {

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
    return new ResolveNamesVisitor(declaredSyntaxVersion);
  }

  private static ResolveExpressionTypesVisitor
      createResolveExpressionTypesVisitorForMaxSyntaxVersion() {
    return createResolveExpressionTypesVisitor(SyntaxVersion.V9_9);
  }

  private static ResolveExpressionTypesVisitor createResolveExpressionTypesVisitor(
      SyntaxVersion declaredSyntaxVersion) {
    return new ResolveExpressionTypesVisitor(typeRegistry, declaredSyntaxVersion);
  }

  public void testOptionalParamTypes() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@param? pa: bool}",
        "{@param? pb: list<int>}",
        "{$pa}",
        "{$pb}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(UnionType.of(BoolType.getInstance(), NullType.getInstance()), types.get(0));
    assertEquals(
        UnionType.of(ListType.of(IntType.getInstance()), NullType.getInstance()),
        types.get(1));
  }

  public void testDataRefTypes() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@param pa: bool}",
        "{@param pb: list<int>}",
        "{@param pe: map<int, map<int, string>>}",
        "{$pa}",
        "{$pb}",
        "{$pb[0]}",
        "{$pe}",
        "{$pe[0]}",
        "{$pe[1 + 1][2]}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(BoolType.getInstance(), types.get(0));
    assertEquals(ListType.of(IntType.getInstance()), types.get(1));
    assertEquals(IntType.getInstance(), types.get(2));
    assertEquals(
        MapType.of(
            IntType.getInstance(),
            MapType.of(IntType.getInstance(), StringType.getInstance())),
        types.get(3));
    assertEquals(MapType.of(IntType.getInstance(), StringType.getInstance()), types.get(4));
    assertEquals(StringType.getInstance(), types.get(5));
  }

  public void testRecordTypes() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@param pa: [a:int, b:string]}",
        "{$pa.a}",
        "{$pa.b}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(IntType.getInstance(), types.get(0));
    assertEquals(StringType.getInstance(), types.get(1));
  }

  public void testDataRefTypesWithUnknown() {
    // Test that data with the 'unknown' type is allowed to function as a map or list.
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false,
        constructTemplateSource(
            "{@param pa: unknown}",
            "{@param pb: map<string, float>}",
            "{@param pc: map<int, string>}",
            "{$pa[0]}",
            "{$pa.xxx}",
            "{$pa.xxx.yyy}",
            "{$pb[$pa]}",
            "{$pc[$pa]}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(UnknownType.getInstance(), types.get(0));
    assertEquals(UnknownType.getInstance(), types.get(1));
    assertEquals(UnknownType.getInstance(), types.get(2));
    assertEquals(FloatType.getInstance(), types.get(3));
    assertEquals(StringType.getInstance(), types.get(4));
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
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false,
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
            "{-$pf}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(UnknownType.getInstance(), types.get(0));
    assertEquals(IntType.getInstance(), types.get(1));
    assertEquals(FloatType.getInstance(), types.get(2));
    assertEquals(UnknownType.getInstance(), types.get(3));
    assertEquals(IntType.getInstance(), types.get(4));
    assertEquals(FloatType.getInstance(), types.get(5));
    assertEquals(UnknownType.getInstance(), types.get(6));
    assertEquals(IntType.getInstance(), types.get(7));
    assertEquals(FloatType.getInstance(), types.get(8));
    assertEquals(UnknownType.getInstance(), types.get(9));
    assertEquals(IntType.getInstance(), types.get(10));
    assertEquals(FloatType.getInstance(), types.get(11));
    assertEquals(UnknownType.getInstance(), types.get(12));
    assertEquals(IntType.getInstance(), types.get(13));
    assertEquals(FloatType.getInstance(), types.get(14));
    assertEquals(UnknownType.getInstance(), types.get(15));
    assertEquals(IntType.getInstance(), types.get(16));
    assertEquals(FloatType.getInstance(), types.get(17));
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

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(
        typeRegistry, SyntaxVersion.V2_0, false, testTemplateContent);
    createResolveNamesVisitor(SyntaxVersion.V2_0).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_0).exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(UnknownType.getInstance(), types.get(0));
    assertEquals(UnknownType.getInstance(), types.get(1));
    assertEquals(UnknownType.getInstance(), types.get(2));
    assertEquals(UnknownType.getInstance(), types.get(3));
    assertEquals(UnknownType.getInstance(), types.get(4));
    assertEquals(UnknownType.getInstance(), types.get(5));
    assertEquals(BoolType.getInstance(), types.get(6));
    assertEquals(BoolType.getInstance(), types.get(7));
    assertEquals(BoolType.getInstance(), types.get(8));

    soyTree = SharedTestUtils.parseSoyFiles(
        typeRegistry, SyntaxVersion.V2_3, false, testTemplateContent);
    createResolveNamesVisitor(SyntaxVersion.V2_3).exec(soyTree);
    createResolveExpressionTypesVisitor(SyntaxVersion.V2_3).exec(soyTree);
    types = getPrintStatementTypes(soyTree);
    assertEquals(BoolType.getInstance(), types.get(0));
    assertEquals(BoolType.getInstance(), types.get(1));
    assertEquals(BoolType.getInstance(), types.get(2));
    assertEquals(BoolType.getInstance(), types.get(3));
    assertEquals(BoolType.getInstance(), types.get(4));
    assertEquals(BoolType.getInstance(), types.get(5));
    assertEquals(BoolType.getInstance(), types.get(6));
    assertEquals(BoolType.getInstance(), types.get(7));
    assertEquals(BoolType.getInstance(), types.get(8));
  }

  public void testComparisonOps() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false,
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
            "{$pf != $pf}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    for (SoyType type : types) {
      assertEquals(BoolType.getInstance(), type);
    }
  }

  public void testNullCoalescingAndConditionalOps() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false,
        constructTemplateSource(
            "{@param pa: unknown}",
            "{@param pi: int}",
            "{@param pf: float}",
            "{$pa ?: $pi}",
            "{$pi ?: $pf}",
            "{$pa ? $pi : $pf}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(UnknownType.getInstance(), types.get(0));
    assertEquals(UnionType.of(IntType.getInstance(), FloatType.getInstance()), types.get(1));
    assertEquals(UnionType.of(IntType.getInstance(), FloatType.getInstance()), types.get(2));
  }

  public void testListLiteral() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false,
        constructTemplateSource(
            "{@param pi: int}",
            "{@param pf: float}",
            "{let $list: [$pi, $pf]/}",
            "{$list}",
            "{$list.length}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(ListType.of(
        UnionType.of(IntType.getInstance(), FloatType.getInstance())), types.get(0));
    assertEquals(IntType.getInstance(), types.get(1));
  }

  public void testMapLiteral() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false,
        constructTemplateSource(
            "{@param pi: int}",
            "{@param pf: float}",
            "{let $map: [1: $pi, 2:$pf]/}",
            "{$map}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(MapType.of(
        IntType.getInstance(),
        UnionType.of(IntType.getInstance(), FloatType.getInstance())), types.get(0));
  }

  public void testMapLiteralAsRecord() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false,
        constructTemplateSource(
            "{@param pi: int}",
            "{@param pf: float}",
            "{let $map: ['a': $pi, 'b':$pf]/}",
            "{$map}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(RecordType.of(
        ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "b", FloatType.getInstance())),
        types.get(0));
  }

  public void testDataFlowTypeNarrowing() {
    SoyType boolOrNullType = UnionType.of(BoolType.getInstance(), NullType.getInstance());
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
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
        "  {$pa}", // #11 must be null
        "{else}",
        "  {$pa}", // #12 must be null
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
        "{/if}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(BoolType.getInstance(), types.get(0));
    assertEquals(NullType.getInstance(), types.get(1));
    assertEquals(BoolType.getInstance(), types.get(2));
    assertEquals(boolOrNullType, types.get(3));
    assertEquals(BoolType.getInstance(), types.get(4));
    assertEquals(NullType.getInstance(), types.get(5));
    assertEquals(boolOrNullType, types.get(6));
    assertEquals(BoolType.getInstance(), types.get(7));
    assertEquals(BoolType.getInstance(), types.get(8));
    assertEquals(BoolType.getInstance(), types.get(9));
    assertEquals(BoolType.getInstance(), types.get(10));
    assertEquals(NullType.getInstance(), types.get(11));
    assertEquals(NullType.getInstance(), types.get(12));
    assertEquals(BoolType.getInstance(), types.get(13));
    assertEquals(BoolType.getInstance(), types.get(14));
    assertEquals(NullType.getInstance(), types.get(15));
  }

  public void testDataFlowTypeNarrowingFailure() {
    // Test for places where type narrowing shouldn't work
    SoyType boolOrNullType = UnionType.of(BoolType.getInstance(), NullType.getInstance());
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
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
        "{/if}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(boolOrNullType, types.get(0));
    assertEquals(boolOrNullType, types.get(1));
    assertEquals(boolOrNullType, types.get(2));
    assertEquals(boolOrNullType, types.get(3));
  }

  public void testConditionalOperatorDataFlowTypeNarrowing() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@param pa: bool|null}",
        "{@param pb: bool}",
        "{$pa ? $pa : $pb}", // #0 must be non-null
        "{$pa != null ?: $pb}", // #1 must be non-null
        "{$pa ?: $pb}")); // #2 must be non-null (re-written to (isNonnull($pa) ? $pa : $pb))
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(BoolType.getInstance(), types.get(0));
    assertEquals(BoolType.getInstance(), types.get(1));
    assertEquals(BoolType.getInstance(), types.get(2));
  }

  public void testInjectedParamTypes() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@inject pa: bool}",
        "{@inject? pb: list<int>}",
        "{$pa}",
        "{$pb}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
    List<SoyType> types = getPrintStatementTypes(soyTree);
    assertEquals(BoolType.getInstance(), types.get(0));
    assertEquals(
        UnionType.of(ListType.of(IntType.getInstance()), NullType.getInstance()),
        types.get(1));
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
    SoyFileSetNode soyTree =
        SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false, fileContent);
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    try {
      createResolveExpressionTypesVisitorForMaxSyntaxVersion().exec(soyTree);
      fail("Expected SoySyntaxException");
    } catch (SoySyntaxException e) {
      assertTrue(e.getMessage().contains(expectedError));
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
    CollectPrintStatementTypesVisitor visitor = new CollectPrintStatementTypesVisitor();
    visitor.exec(node);
    return visitor.getTypes();
  }

  /**
   * Test helper class that scarfs up all soy print nodes, and records the type of
   * the expression printed.
   */
  public static class CollectPrintStatementTypesVisitor extends AbstractSoyNodeVisitor<Void> {
    private final List<SoyType> types = Lists.newArrayList();

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
