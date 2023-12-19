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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateElementNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateStateVar;
import com.google.template.soy.testing.Example;
import com.google.template.soy.testing.ExampleExtendable;
import com.google.template.soy.testing.SomeExtension;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.testing.correct.Proto2CorrectSemantics.Proto2ImplicitDefaults;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnknownType;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeConverter;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ResolveExpressionTypesPass}. */
@RunWith(JUnit4.class)
public final class ResolveExpressionTypesPassTest {
  private static final SoyFunction ASSERT_TYPE_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "assertType";
        }

        @Override
        public ImmutableSet<Integer> getValidArgsSizes() {
          return ImmutableSet.of(2);
        }
      };

  private static final SoyTypeRegistry TYPE_REGISTRY = SoyTypeRegistryBuilder.create();

  @Test
  public void testOptionalParamTypes() {
    assertTypes(
        "{@param? pa: bool}",
        "{@param? pb: list<int>}",
        "{assertType('bool|undefined', $pa)}",
        "{assertType('list<int>|undefined', $pb)}");
  }

  @Test
  public void testState() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructElementFileSource(
                    "{@state pa:= true}",
                    "{@state pb:= [1,2,3]}",
                    "{@param pc: bool|null = null}",
                    "{@param pd: list<int>|null = null}",
                    "<div>",
                    "{assertType('bool', $pa)}",
                    "{assertType('list<int>', $pb)}",
                    "{assertType('bool|null', $pc)}",
                    "{assertType('list<int>|null', $pd)}",
                    "</div>"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .desugarHtmlNodes(false)
            .desugarIdomFeatures(false)
            .parse()
            .fileSet();
    assertTypes(soyTree);
    TemplateElementNode node = (TemplateElementNode) soyTree.getChild(0).getChild(0);

    List<TemplateStateVar> stateVars = node.getStateVars();
    assertThat(stateVars.get(0).defaultValue().getType()).isEqualTo(BoolType.getInstance());
    assertThat(stateVars.get(1).defaultValue().getType())
        .isEqualTo(ListType.of(IntType.getInstance()));

    List<TemplateParam> params = node.getParams();
    assertThat(params.get(0).defaultValue().getType()).isEqualTo(NullType.getInstance());
    assertThat(params.get(1).defaultValue().getType()).isEqualTo(NullType.getInstance());
  }

  @Test
  public void testDefaultParam() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructFileSource(
                    "{@param pa:= map('cats': true, 'dogs': false)}",
                    "{@param pb: string|null = null}",
                    "{assertType('map<string,bool>', $pa)}",
                    "{assertType('null|string', $pb)}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();

    assertTypes(soyTree);

    TemplateBasicNode node = (TemplateBasicNode) soyTree.getChild(0).getChild(0);
    List<TemplateParam> params = node.getParams();
    assertThat(params.get(0).defaultValue().getType())
        .isEqualTo(MapType.of(StringType.getInstance(), BoolType.getInstance()));
    assertThat(params.get(1).defaultValue().getType()).isEqualTo(NullType.getInstance());
  }

  @Test
  public void testStateTypeInference() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateAndImports(
                constructElementSource(
                    "{@state pa:= true}",
                    "{@state pb:= [1,2,3]}",
                    "{@state proto:= ExampleExtendable()}",
                    "<div>",
                    "{assertType('bool', $pa)}",
                    "{assertType('list<int>', $pb)}",
                    "{assertType('example.ExampleExtendable', $proto)}",
                    "</div>"),
                ExampleExtendable.getDescriptor())
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .desugarHtmlNodes(false)
            .desugarIdomFeatures(false)
            .parse()
            .fileSet();
    assertTypes(soyTree);
    TemplateElementNode node =
        SoyTreeUtils.getAllNodesOfType(soyTree, TemplateElementNode.class).get(0);
    List<TemplateStateVar> stateVars = node.getStateVars();

    assertThat(stateVars.get(0).name()).isEqualTo("pa");
    assertThat(stateVars.get(0).type()).isEqualTo(BoolType.getInstance());

    assertThat(stateVars.get(1).name()).isEqualTo("pb");
    assertThat(stateVars.get(1).type()).isEqualTo(ListType.of(IntType.getInstance()));

    assertThat(stateVars.get(2).name()).isEqualTo("proto");
    assertThat(stateVars.get(2).type().toString())
        .isEqualTo(ExampleExtendable.getDescriptor().getFullName());
  }

  @Test
  public void testDataRefTypes() {
    assertTypes(
        "{@param pa: bool}",
        "{@param pb: list<int>}",
        "{@param pe: map<int, map<int, string>>}",
        "{assertType('bool', $pa)}",
        "{assertType('list<int>', $pb)}",
        "{assertType('int', $pb[0])}",
        "{assertType('map<int,map<int,string>>', $pe)}",
        "{assertType('map<int,string>', $pe.get(0)!)}",
        "{assertType('string', $pe.get(1 + 1)!.get(2)!)}");
  }

  @Test
  public void testRecordTypes() {
    assertTypes(
        "{@param pa: [a:int, b:string]}",
        "{assertType('int', $pa.a)}",
        "{assertType('string', $pa.b)}");
  }

  @Test
  public void testDataRefTypesWithUnknown() {
    // Test that data with the 'unknown' type is allowed to function as a map or list.
    assertTypes(
        "{@param pa: ?}",
        "{@param pb: map<string, float>}",
        "{@param pc: map<int, string>}",
        "{assertType('?', $pa[0])}",
        "{assertType('?', $pa.xxx)}",
        "{assertType('?', $pa.xxx.yyy)}",
        "{assertType('float', $pb.get($pa)!)}",
        "{assertType('string', $pc.get($pa)!)}");
  }

  @Test
  public void testDataRefTypesError() {
    assertResolveExpressionTypesFails(
        "Method 'get' called with parameter types (int) but expected (string).",
        constructFileSource("{@param pa: map<string, float>}", "{$pa.get(0)}"));
  }

  @Test
  public void testRecordTypesError() {
    assertResolveExpressionTypesFails(
        "Undefined field 'c' for record type [a: int, bb: float]. Did you mean 'a'?",
        constructFileSource("{@param pa: [a:int, bb:float]}", "{$pa.c}"));
  }

  @Test
  public void testGetExtensionMethodTyping() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateAndImports(
                constructTemplateSource(
                    "{@param proto: ExampleExtendable}",
                    "{assertType('bool', $proto.getExtension(someBoolExtension))}",
                    "{assertType('int|undefined', $proto.getExtension("
                        + "SomeExtension.someExtensionField).getSomeExtensionNumOrUndefined())}"),
                ExampleExtendable.getDescriptor(),
                SomeExtension.getDescriptor(),
                Example.someBoolExtension.getDescriptor())
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testArithmeticOps() {
    assertTypes(
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
        "{assertType('float', -$pf)}");
  }

  @Test
  public void testArithmeticTypesError() {
    assertResolveExpressionTypesFails(
        "Using arithmetic operator '/' on Soy types 'string' and 'string' is illegal.",
        constructFileSource("{'a' / 'b'}"));
  }

  @Test
  public void testStringConcatenation() {
    assertTypes(
        "{@param ps: string}",
        "{@param pi: int}",
        "{@param pf: float}",
        "{assertType('string', $ps + $ps)}",
        "{assertType('string', $ps + $pi)}",
        "{assertType('string', $ps + $pf)}",
        "{assertType('string', $pi + $ps)}",
        "{assertType('string', $pf + $ps)}");
  }

  @Test
  public void testLogicalOps() {
    assertTypes(
        "{@param pa: ?}",
        "{@param pi: int}",
        "{@param pf: float}",
        "{assertType('?', $pa && $pa)}",
        "{assertType('int', $pi && $pi)}",
        "{assertType('float', $pf && $pf)}",
        "{assertType('?', $pa || $pa)}",
        "{assertType('int', $pi || $pi)}",
        "{assertType('float', $pf || $pf)}",
        "{assertType('bool', not $pa)}",
        "{assertType('bool', not $pi)}",
        "{assertType('bool', not $pf)}");
  }

  @Test
  public void testComparisonOps() {
    assertTypes(
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
        "{assertType('bool', $pf != $pf)}");
  }

  @Test
  public void testNullCoalescingAndConditionalOps() {
    assertTypes(
        "{@param pa: ?}",
        "{@param pi: int}",
        "{@param pf: float}",
        "{@param? ni: int|null}",
        "{assertType('?', $pa ?? $pi)}",
        "{assertType('float|int', $pi ?? $pf)}",
        "{assertType('float|int', $pa ? $pi : $pf)}",
        "{assertType('int', $ni ?? 0)}");
  }

  @Test
  public void testNullCoalescingAndConditionalOps_complexCondition() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructFileSource(
                    "{@param? l: [a :int]|null}", "{assertType('int', $l?.a ?? 0)}"))
            .typeRegistry(TYPE_REGISTRY)
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testListLiteral() {
    assertTypes(
        "{@param pi: int}",
        "{@param pf: float}",
        "{let $list: [$pi, $pf]/}",
        "{assertType('list<float|int>', $list)}",
        "{assertType('int', length($list))}");
  }

  @Test
  public void testMapLiteral() {
    assertTypes(
        "{@param pi: int}",
        "{@param pf: float}",
        "{let $map: map(1: $pi, 2:$pf)/}",
        "{assertType('map<int,float|int>', $map)}");
  }

  @Test
  public void testMapLiteralWithStringKeysAsMap() {
    assertTypes(
        "{@param v1: int}",
        "{@param v2: string}",
        "{@param k1: string}",
        "{let $map: map($k1: $v1, 'b': $v2) /}",
        "{assertType('map<string,int|string>', $map)}");
  }

  @Test
  public void testMapLiteralWithStringLiteralKeysDoesNotCreateRecord() {
    assertTypes(
        "{@param pi: int}",
        "{@param pf: float}",
        // With the old map syntax, this would create a record type (see next test)
        "{let $map: map('a': $pi, 'b':$pf)/}",
        "{assertType('map<string,float|int>', $map)}");
  }

  @Test
  public void testRecordLiteralAsRecord() {
    assertTypes(
        "{@param pi: int}",
        "{@param pf: float}",
        "{let $record: record(a: $pi, b: $pf)/}",
        "{assertType('[a: int, b: float]', $record)}");
  }

  @Test
  public void testRecordLiteral_duplicateKeys() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(
            constructFileSource("{let $record: record(a: 1, a: 2)/}"))
        .errorReporter(reporter)
        .typeRegistry(TYPE_REGISTRY)
        .parse()
        .fileSet();
    assertThat(Iterables.getOnlyElement(reporter.getErrors()).message())
        .isEqualTo("Duplicate argument 'a'.");
  }

  @Test
  public void testDataFlowTypeNarrowing() {
    assertTypes(
        "{@param? pa: bool}",
        "{@param pb: bool}",
        "{if $pa != null}",
        "  {assertType('bool', $pa)}", // #0 must be non-null
        "{/if}",
        "{if $pa == null}",
        "  {assertType('undefined', $pa)}", // #1 must be null
        "{else}",
        "  {assertType('bool', $pa)}", // #2 must be non-null
        "{/if}");
    assertTypes(
        "{@param? pa: bool}",
        "{@param pb: bool}",
        "{if $pa == null || $pb}",
        "  {assertType('bool|undefined', $pa)}", // #3 don't know
        "{else}",
        "  {assertType('bool', $pa)}", // #4 must be non-null
        "{/if}",
        "{if $pa == null && $pb}",
        "  {assertType('undefined', $pa)}", // #5 must be null
        "{else}",
        "  {assertType('bool|undefined', $pa)}", // #6 don't know
        "{/if}");
    assertTypes(
        "{@param? pa: bool|null}",
        "{@param pb: bool}",
        "{if null != $pa}", // Reverse order
        "  {assertType('bool', $pa)}", // #7 must be non-null
        "{/if}",
        "{if not ($pa == null)}", // Not operator
        "  {assertType('bool', $pa)}", // #8 must be non-null
        "{/if}",
        "{if $pa}", // Implicit != null
        "  {assertType('bool', $pa)}", // #9 must be non-null
        "{/if}",
        "{if $pa && $pb}", // Implicit != null
        "  {assertType('bool', $pa)}", // #10 must be non-null
        "{/if}");
    assertTypes(
        "{@param pa: bool|null}",
        "{@param pb: bool}",
        "{if $pa}", // Chained conditions
        "{elseif $pb}",
        "  {assertType('bool|null', $pa)}", // #11 must be falsy
        "{else}",
        "  {assertType('bool|null', $pa)}", // #12 must be falsy
        "{/if}");
    assertTypes(
        "{@param? pa: bool}",
        "{@param pb: bool}",
        "{if $pa}", // Nested if
        "  {if $pa}",
        "    {assertType('bool', $pa)}", // #13 must be non-null
        "  {/if}",
        "{/if}");
    assertTypes(
        "{@param? pa: bool}",
        "{@param pb: bool}",
        "{if $pa != null}", // != null
        "  {assertType('bool', $pa)}", // #14 must be non-null
        "{else}",
        "  {assertType('undefined', $pa)}", // #15 must be null
        "{/if}");
    assertTypes(
        "{@param? pa: bool}",
        "{@param pb: bool}",
        "{if $pa == null}", // == null
        "  {assertType('undefined', $pa)}", // #16 must be null
        "{else}",
        "  {assertType('bool', $pa)}", // #17 must be non-null
        "{/if}");
    assertTypes(
        "{@param? pa: bool}",
        "{@param pb: bool}",
        "{if $pb || $pa == undefined}",
        "  {assertType('bool|undefined', $pa)}", // #18 don't know
        "{else}",
        "  {assertType('bool', $pa)}", // #19 must be non-null
        "{/if}");
    assertTypes(
        "{let $null: null /}",
        "{if $null == null || $null != null}",
        "  {assertType('null', $null)}", // #20  null type
        "{/if}",
        "{if $null}",
        "  {assertType('null', $null)}", // #21 null type (but this branch is dead)
        "{/if}",
        "");
    assertTypes(
        "{@param pa: string|null}",
        "{@param? pb: string}",
        "{if $pa != null && $pb != null}",
        "  {assertType('string', $pa)}",
        "  {assertType('string', $pb)}",
        "{else}",
        "  {assertType('null|string', $pa)}",
        "  {assertType('string|undefined', $pb)}",
        "{/if}",
        "",
        "{if $pa != null || $pb != null}",
        "  {assertType('null|string', $pa)}",
        "  {assertType('string|undefined', $pb)}",
        "{else}",
        "  {assertType('null', $pa)}",
        "  {assertType('undefined', $pb)}",
        "{/if}",
        "");
    assertTypes(
        "{@param? pa: string}",
        "{if $pa != null && $pa.length > 0}",
        "  {assertType('string', $pa)}",
        "{/if}",
        "",
        "{if ($pa == null || $pa.length != 1) or $pa.substring(1) == 'a'}",
        "  {assertType('string|undefined', $pa)}",
        "{/if}",
        "");
    assertTypes(
        "{@param? pa: string}",
        "{@param? pb: string}",
        "{if $pa && $pb}",
        "  {assertType('string', $pa)}",
        "  {assertType('string', $pb)}",
        "  {assertType('string', $pa && $pb)}",
        "{/if}");
    assertTypes(
        "{@param? pa: string}",
        "{if $pa && $pa.length > 0}",
        "  {assertType('string', $pa)}",
        "{/if}");
    assertTypes(
        "{@param pa: string|null}",
        "{@param pb: int|null}",
        "{assertType('int|null|string', $pa && $pb)}",
        "{assertType('int|null|string', $pa || $pb)}");
  }

  @Test
  public void testDataFlowTypeNarrowing_nullSafeChains() {
    assertTypes(
        "{@param r: [a: null|[b: null|[c: null|string]]]}",
        "{if $r.a?.b?.c}",
        "  {assertType('[b: [c: null|string]|null]', $r.a)}",
        "  {assertType('[c: null|string]', $r.a.b)}",
        "  {assertType('string', $r.a.b.c)}",
        "{/if}",
        "");
    assertTypes(
        "{@param r: [a: null|[b: null|string]]}",
        "{if $r.a?.b != null}",
        "  {assertType('[b: null|string]', $r.a)}",
        "  {assertType('string', $r.a.b)}",
        "{else}",
        "  {assertType('[b: null|string]|null', $r.a)}",
        "{/if}",
        "",
        "{if $r.a?.b?.length}",
        "  {assertType('[b: null|string]', $r.a)}",
        "  {assertType('string', $r.a.b)}",
        "{else}",
        "  {assertType('[b: null|string]|null', $r.a)}",
        "{/if}",
        "");
    assertTypes(
        "{@param r: [a?: [b?: string]]}",
        "{if $r.a?.b != null}",
        // "  {assertType('[b: string]', $r.a)}",
        "  {assertType('string', $r.a.b)}",
        "{else}",
        "  {assertType('[b?: string]|undefined', $r.a)}",
        "{/if}",
        "",
        "{if $r.a?.b?.length}",
        "  {assertType('[b?: string]', $r.a)}",
        "  {assertType('string', $r.a.b)}",
        "{else}",
        "  {assertType('[b?: string]|undefined', $r.a)}",
        "{/if}",
        "",
        "{if $r.a?.b !== undefined}",
        // "  {assertType('[b: string]', $r.a)}",
        "  {assertType('string', $r.a.b)}",
        "{else}",
        "  {assertType('[b?: string]|undefined', $r.a)}",
        "{/if}",
        "");
    assertTypes(
        "{@param? m: map<string, string>}",
        "{if $m?.get('a')}",
        "  {assertType('map<string,string>', $m)}",
        "  {assertType('string', $m?.get('a'))}",
        "  {assertType('string', $m.get('a'))}",
        "{else}",
        "  {assertType('map<string,string>|undefined', $m)}",
        "  {assertType('string|undefined', $m?.get('a'))}",
        "{/if}",
        "",
        "{if $m?.get('a') == 'b'}",
        "  {assertType('map<string,string>', $m)}",
        "  {assertType('string', $m.get('a'))}",
        "{else}",
        "  {assertType('map<string,string>|undefined', $m)}",
        "  {assertType('string|undefined', $m?.get('a'))}",
        "{/if}",
        "",
        "{if $m?.get('a') != 'b'}",
        "  {assertType('string|undefined', $m?.get('a'))}",
        "  {assertType('map<string,string>|undefined', $m)}",
        "{else}",
        "  {assertType('string|undefined', $m?.get('a'))}",
        "  {assertType('map<string,string>|undefined', $m)}",
        "{/if}",
        "",
        "{if $m?.get('a') == null}",
        "  {assertType('undefined', $m?.get('a'))}",
        "  {assertType('map<string,string>|undefined', $m)}",
        "{else}",
        "  {assertType('string', $m.get('a'))}",
        "  {assertType('map<string,string>', $m)}",
        "{/if}",
        "",
        "{if $m?.get('a') != null}",
        "  {assertType('string', $m.get('a'))}",
        "  {assertType('map<string,string>', $m)}",
        "{else}",
        "  {assertType('undefined', $m?.get('a'))}",
        "  {assertType('map<string,string>|undefined', $m)}",
        "{/if}",
        "");
    assertTypes(
        "{@param m: map<string, null|map<string, null|string>>}",
        "{if $m.get('a')?.get('b')}",
        "  {assertType('map<string,null|string>', $m.get('a'))}",
        "  {assertType('string', $m.get('a').get('b'))}",
        // We aren't quite sure why you can call `get` on $m?.get('a') since the base's type hasn't
        // been narrowed according to assertType.
        "  {assertType('map<string,null|string>|null|undefined', $m?.get('a'))}",
        "  {assertType('null|string|undefined', $m?.get('a').get('b'))}",
        "{else}",
        "  {assertType('null|string|undefined', $m?.get('a')?.get('b'))}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowing_nullSafeChains_notAllPermutations() {
    // There's probably no reason not to support these narrowings. Just a regression test to make
    // explicit that we don't support them currently.
    assertTypes(
        "{@param r: [a: null|[b: null|[c: null|string]]]}",
        "{if $r.a?.b?.c}",
        "  {assertType('string', $r.a.b.c)}",
        "  {assertType('string', $r.a?.b?.c)}",
        "  {assertType('null|string', $r?.a.b.c)}",
        "  {assertType('null|string', $r.a?.b.c)}",
        "  {assertType('null|string', $r.a.b?.c)}",
        "  {assertType('null|string|undefined', $r?.a?.b.c)}",
        "  {assertType('null|string|undefined', $r?.a.b?.c)}",
        "  {assertType('null|string|undefined', $r?.a?.b?.c)}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowing_tripleEq() {
    assertTypes(
        "{@param? m: map<string, string|undefined>}",
        "{assertType('string|undefined', $m?.get('a'))}",
        "{if $m?.get('a') === undefined}",
        "  {assertType('undefined', $m?.get('a'))}",
        "  {assertType('map<string,string|undefined>|undefined', $m)}",
        "{else}",
        "  {assertType('string', $m?.get('a'))}",
        "  {assertType('map<string,string|undefined>', $m)}",
        "{/if}",
        "",
        "{if $m?.get('a') !== undefined}",
        "  {assertType('string', $m?.get('a'))}",
        "  {assertType('map<string,string|undefined>', $m)}",
        "{else}",
        "  {assertType('undefined', $m?.get('a'))}",
        "  {assertType('map<string,string|undefined>|undefined', $m)}",
        "{/if}",
        "");
    assertTypes(
        "{@param? m: map<string, string|null>}",
        "{if $m?.get('a') === null}",
        "  {assertType('null', $m?.get('a'))}",
        "  {assertType('map<string,null|string>', $m)}",
        "{else}",
        "  {assertType('string|undefined', $m?.get('a'))}",
        "  {assertType('map<string,null|string>|undefined', $m)}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowing_greaterLessThan() {
    assertTypes(
        "{@param? r: [a?: string]}",
        "{if $r?.a?.length > 0}",
        "  {assertType('string', $r.a)}",
        "{/if}",
        "{if $r?.a?.length < 0}",
        "  {assertType('string', $r.a)}",
        "{/if}",
        "{if $r?.a?.length >= 0}",
        "  {assertType('string|undefined', $r.a)}",
        "{/if}",
        "{if $r?.a?.length <= 0}",
        "  {assertType('string|undefined', $r.a)}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowing_complexExpressions() {
    assertTypes(
        "{@param map: map<string, int|null>}",
        "{@param record: " + "[a : [nullableInt : int|null, nullableBool : bool|null]|null]}",
        "{if $map.get('a')}",
        "  {assertType('int', $map.get('a'))}",
        "{/if}",
        "{if $record.a?.nullableInt}",
        "  {assertType('int', $record.a?.nullableInt)}",
        "{/if}",
        "");
    // Don't add |null to types for checks like this.
    assertTypes(
        "{@param s: string}",
        "{if $s == null || $s == 'a'}",
        "  {assertType('string', $s)}",
        "{else}",
        "  {assertType('string', $s)}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowing_deadExpression() {
    assertTypes(
        "{@param record: ?}",
        "{if $record.unknownField}",
        "  {assertType('?', $record.unknownField)}",
        "{else}",
        "  {if $record.unknownField}",
        // This code is dead, but we can't prove it
        "    {assertType('?', $record.unknownField)}",
        "  {/if}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowing_isFalseyOrEmpty() {
    assertTypes(
        "{@param? h: html|null}",
        "{if not isFalseyOrEmpty($h)}",
        "  {assertType('html', $h)}",
        "{else}",
        "  {assertType('html|null|undefined', $h)}",
        "{/if}");
  }

  @Test
  public void testDataFlowTypeNarrowing_hasContent() {
    assertTypes(
        "{@param? h: html|null}",
        "{if hasContent($h)}",
        "  {assertType('html', $h)}",
        "{else}",
        "  {assertType('html|null|undefined', $h)}",
        "{/if}");
  }

  @Test
  public void testDataFlowTypeNarrowing_isTruthyNonEmpty() {
    assertTypes(
        "{@param? h: html|null}",
        "{if isTruthyNonEmpty($h)}",
        "  {assertType('html', $h)}",
        "{else}",
        "  {assertType('html|null|undefined', $h)}",
        "{/if}");
  }

  @Test
  public void testDataFlowTypeNarrowing_logicalExpressions() {
    assertTypes(
        "{@param? record: [active : bool|null]|null}",
        "{@param? selected: map<string,bool>|null}",
        "{assertType('bool|map<string,bool>|null|undefined', $selected && $selected.get('a'))}",
        "{assertType('bool|undefined', $selected == null || $selected.get('a'))}",
        "{if ($record.active != null) && (not $record.active)}",
        "  {assertType('bool', $record.active)}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowing_ignoredFunctions() {
    assertTypes(
        "{@param? param: string}",
        "{if Boolean($param)}",
        "  {assertType('string', $param)}",
        "{/if}",
        "{if undefinedToNullForMigration($param)}",
        "  {assertType('string', $param)}",
        "{/if}",
        "{if undefinedToNullForSsrMigration($param)}",
        "  {assertType('string', $param)}",
        "{/if}",
        "{if checkNotNull($param)}",
        "  {assertType('string', $param)}",
        "{/if}",
        "{if Boolean($param) != null}", // Boolean() ignored only in truthy narrowing.
        "  {assertType('string|undefined', $param)}",
        "{/if}",
        "{if undefinedToNullForMigration($param) != null}",
        "  {assertType('string', $param)}",
        "{/if}",
        "{if round($param)}", // non-special function takes a ? param.
        "  {assertType('string|undefined', $param)}",
        "{/if}",
        "");
  }

  @Test
  public void testDataFlowTypeNarrowingFailure() {
    // Test for places where type narrowing shouldn't work
    assertTypes(
        "{@param? pa: bool}",
        "{@param pb: bool}",
        "{if ($pa != null) != ($pb != null)}",
        "  {assertType('bool|undefined', $pa)}", // #0 don't know
        "{else}",
        "  {assertType('bool|undefined', $pa)}", // #1 don't know
        "{/if}",
        "{if $pa ?? $pb}",
        "  {assertType('bool|undefined', $pa)}", // #2 don't know
        "{/if}",
        "{if $pb ? $pa : false}",
        "  {assertType('bool|undefined', $pa)}", // #3 don't know
        "{/if}");
  }

  @Test
  public void testDataFlowTypeNarrowing_switch() {
    assertTypes(
        "{@param? p: string|bool|int}",
        "{switch $p}",
        "  {case 'str'}",
        "    {assertType('string', $p)}",
        "  {case true}",
        "    {assertType('bool', $p)}",
        "  {case 'str', 'str2'}",
        "    {assertType('string', $p)}",
        "  {case 'str', 8675309}",
        "    {assertType('int|string', $p)}",
        "  {default}",
        "    {assertType('bool|int|string|undefined', $p)}",
        "{/switch}");
    assertTypes(
        "{@param? p: string|bool|int|null}",
        "{switch $p}",
        "  {case null}",
        "    {assertType('null', $p)}",
        "  {default}",
        "    {assertType('bool|int|string|undefined', $p)}",
        "{/switch}");
    assertTypes(
        "{@param? p: string|bool|int|null}",
        "{switch $p}",
        "  {case 'str', null}",
        "    {assertType('null|string', $p)}",
        "  {case undefined}",
        "    {assertType('undefined', $p)}",
        "  {default}",
        "    {assertType('bool|int|string', $p)}",
        "{/switch}");
  }

  @Test
  public void testConditionalOperatorDataFlowTypeNarrowing() {
    assertTypes(
        "{@param? pa: bool|null}",
        "{@param pb: bool}",
        "{@param pc: [a : int|null]}",
        "{assertType('bool', $pa ? $pa : $pb)}", // #0 must be non-null
        "{assertType('bool', $pa != null ?? $pb)}", // #1 must be non-null
        "{assertType('bool', $pa ?? $pb)}", // #2 must be non-null (re-written to ($pa != null ? $pa
        // : $pb))
        "{assertType('int', $pc.a ? $pc.a : 0)}",
        "{if not $pc.a}{assertType('int|null', $pc.a)}{/if}");
  }

  @Test
  public void testBuiltinFunctionTyping() {
    assertTypes(
        "{@inject list: list<int|null>}",
        "{for $item in $list}",
        "   {assertType('int|null', $item)}",
        "   {assertType('int', checkNotNull($item))}",
        "   {assertType('string', css('foo'))}",
        "   {assertType('string', xid('bar'))}",
        "{/for}");
  }

  @Test
  public void testProtoTyping() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateAndImports(
                constructTemplateSource(
                    "{let $proto: ExampleExtendable() /}",
                    "{assertType('example.ExampleExtendable', $proto)}",
                    "{assertType('string', $proto.getSomeString())}",
                    "{assertType('string|undefined', $proto.getSomeStringOrUndefined())}",
                    "{assertType('int|undefined', $proto.getSomeNumNoDefaultOrUndefined())}",
                    "{assertType('example.SomeEmbeddedMessage|undefined',"
                        + " $proto.getSomeEmbeddedMessage())}",
                    "",
                    "{let $protoCorrect: Proto2ImplicitDefaults() /}",
                    "{assertType('string', $protoCorrect.getString())}",
                    "{assertType('string|undefined', $protoCorrect.getStringOrUndefined())}",
                    "{assertType('int', $protoCorrect.getLongWithDefaultJsType())}"
                    ),
                ExampleExtendable.getDescriptor(),
                Proto2ImplicitDefaults.getDescriptor())
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testBadForEach() {
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type int.",
        constructFileSource("{@param p: int}", "{for $item in $p}{/for}"));
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type int|string.",
        constructFileSource("{@param p: int|string}", "{for $item in $p}{/for}"));
    assertResolveExpressionTypesFails(
        "Cannot iterate over $p of type list<string>|string|uri.",
        constructFileSource("{@param p: list<string>|string|uri}", "{for $item in $p}{/for}"));
  }

  @Test
  public void testInjectedParamTypes() {
    assertTypes(
        "{@inject pa: bool}",
        "{@inject? pb: list<int>}",
        "{assertType('bool', $pa)}",
        "{assertType('list<int>|undefined', $pb)}");
  }

  @Test
  public void testConcatLists() {
    assertTypes(
        "{assertType('list<string>', ['1'].concat(['2']))}",
        "{assertType('list<int>', [1].concat([2]))}",
        "{assertType('list<int>', [1].concat([]))}",
        "{assertType('list<int>', [].concat([1]))}",
        "{assertType('list<int>', (true ? [] : [1]).concat([2]))}",
        "{assertType('list<null>', [].concat([]))}",
        "{assertType('list<int|string>', [1].concat([\"2\"]))}");
  }

  @Test
  public void testConcatMaps() {
    assertTypes(
        "{assertType('map<string,string>', map('1' : '2').concat(map('3':'4')))}",
        "{assertType('map<int,int>', map(1: 2).concat(map(3: 4)))}",
        "{assertType('map<int,int>', map(1: 2).concat(map()))}",
        "{assertType('map<int,int>', map().concat(map(3: 4)))}",
        "{assertType('map<int,int>', map().concat(true ? map() : map(3: 4)))}",
        "{assertType('map<int,int>', (true ? map() : map(1:2)).concat(map()))}",
        "{assertType('map<?,?>', map().concat(map()))}",
        "{assertType('map<int,int|string>', map(1: '2').concat(map(3: 4)))}");
  }

  @Test
  public void testMapKeys() {
    assertTypes(
        "{@param m: map<string, int>}",
        "{assertType('list<string>', $m.keys())}",
        "{assertType('list<null>', map().keys())}",
        "");
  }

  @Test
  public void testMapToLegacyObjectMap() {
    assertTypes(
        "{@param m: map<string, int>}",
        "{assertType('legacy_object_map<string,int>', mapToLegacyObjectMap($m))}",
        "{assertType('legacy_object_map<null,null>', mapToLegacyObjectMap(map()))}",
        "");
  }

  @Test
  public void testVeDataLiteral() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateAndImports(
                "{const VeData = ve_def('VeData', 1, ExampleExtendable) /}"
                    + "{const VeNoData = ve_def('VeNoData', 2) /}"
                    + constructTemplateSource(
                        "{assertType('ve_data', ve_data(VeData, ExampleExtendable()))}",
                        "{assertType('ve_data', ve_data(VeNoData, null))}"),
                ExampleExtendable.getDescriptor())
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testErrorMessagesInUnionTypes() {
    assertResolveExpressionTypesFails(
        "Type float does not support bracket access.",
        constructFileSource("{@param p: float|int}", "{$p[1]}"));

    assertResolveExpressionTypesFails(
        "Field 'a' does not exist on type float.",
        constructFileSource("{@param p: float|int}", "{$p.a}"));
  }

  @Test
  public void testTypeNarrowingError() {
    assertResolveExpressionTypesFails(
        "Cannot narrow expression of type 'string' to 'null|undefined'.",
        constructFileSource(
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

  @Test
  public void testNonNullAssertion() {
    assertTypes(
        "{@param? i: int}",
        "{@param? n: int|null}",
        "{@param b: bool}",
        "{@param r: [a: null|[b: null|[c: null|string]]]}",
        "{assertType('int', $i!)}",
        "{assertType('int|null', $i != null ? $i! : null)}",
        "{assertType('string', $r.a.b.c!)}",
        "{assertType('[c: null|string]', $r.a.b!)}",
        "{assertType('int', $i ?? $n!)}",
        "{assertType('int', ($b ? $i : $n)!)}",
        "{assertType('int|undefined', $b ? $i : $n!)}",
        "{assertType('[c: null|string]|null|undefined', $r!.a?.b)}",
        "{assertType('[c: null|string]|null', $r?.a!.b)}",
        "{assertType('string', $r?.a.b.c!)}",
        "{assertType('null|string', $r!.a!.b!.c)}");
  }

  @Test
  public void testNullableExternRefinement() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                Joiner.on('\n')
                    .join(
                        "{namespace ns}",
                        "",
                        "{extern returnsNullable: () => string|null}",
                        "  {jsimpl namespace=\"this.is.not.real\" function=\"notReal\" /}",
                        "{/extern}",
                        "",
                        "{template aaa}",
                        "  {@param? nullableString: string|null}",
                        "",
                        "  {assertType('string', returnsNullable() ?? '')}",
                        "{/template}"))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  @Test
  public void testUndefinedToNullForMigration() {
    assertTypes(
        "{@param s1: string}",
        "{@param s2: string|undefined}",
        "{@param? s3: string|null}",
        "{@param? s4: string|null|undefined}",
        "{assertType('string', undefinedToNullForMigration($s1))}",
        "{assertType('null|string', undefinedToNullForMigration($s2))}",
        "{assertType('null|string', undefinedToNullForMigration($s3))}",
        "{assertType('null|string', undefinedToNullForMigration($s4))}",
        "{assertType('string', undefinedToNullForSsrMigration($s1))}",
        "{assertType('null|string', undefinedToNullForSsrMigration($s2))}",
        "{assertType('null|string', undefinedToNullForSsrMigration($s3))}",
        "{assertType('null|string', undefinedToNullForSsrMigration($s4))}");
  }

  private SoyType parseSoyType(String type) {
    return parseSoyType(type, ErrorReporter.exploding());
  }

  private SoyType parseSoyType(String type, ErrorReporter errorReporter) {
    TypeNode parsed =
        SoyFileParser.parseType(
            type, SourceFilePath.forTest("com.google.foo.bar.FakeSoyFunction"), errorReporter);
    return TypeNodeConverter.builder(errorReporter)
        .setTypeRegistry(TYPE_REGISTRY)
        .build()
        .getOrCreateType(parsed);
  }

  /**
   * Helper function that constructs a boilerplate template given a list of body statements to
   * insert into the middle of the template. The body statements will be indented and separated with
   * newlines.
   *
   * @param body The body statements.
   * @return The combined template.
   */
  private static String constructFileSource(String... body) {
    return "{namespace ns}\n" + constructTemplateSource(body);
  }

  private static String constructTemplateSource(String... body) {
    return "/***/\n"
        + "{template aaa}\n"
        + "  "
        + Joiner.on("\n   ").join(body)
        + "\n"
        + "{/template}\n";
  }

  /**
   * Helper function that constructs a boilerplate template given a list of body statements to
   * insert into the middle of the template. The body statements will be indented and separated with
   * newlines.
   *
   * @param body The body statements.
   * @return The combined template.
   */
  private static String constructElementFileSource(String... body) {
    return "{namespace ns}\n" + constructElementSource(body);
  }

  private static String constructElementSource(String... body) {
    return "" + "/***/\n" + "{element aaa}\n" + Joiner.on("\n   ").join(body) + "{/element}\n";
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
        .errorReporter(errorReporter)
        .typeRegistry(TYPE_REGISTRY)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(errorReporter.getErrors().get(0).message()).isEqualTo(expectedError);
  }

  private void assertTypes(String... lines) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(constructFileSource(lines))
            .addSoyFunction(ASSERT_TYPE_FUNCTION)
            .parse()
            .fileSet();
    assertTypes(soyTree);
  }

  /** Traverses the tree and checks all the calls to {@code assertType} */
  private void assertTypes(SoyNode node) {
    SoyTreeUtils.allFunctionInvocations(node, ASSERT_TYPE_FUNCTION)
        .forEach(
            fn -> {
              StringNode expected = (StringNode) fn.getChild(0);
              SoyType actualType = fn.getChild(1).getType();
              assertWithMessage("assertion @ " + fn.getSourceLocation())
                  .that(actualType.toString())
                  .isEqualTo(expected.getValue());
            });
  }
}
