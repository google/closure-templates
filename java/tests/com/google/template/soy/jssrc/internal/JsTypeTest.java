/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.internal.JsType.forIdomSrcState;
import static com.google.template.soy.jssrc.internal.JsType.forJsSrc;
import static com.google.template.soy.types.SoyTypes.unionWithNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.StringSubject;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.FormatOptions;
import com.google.template.soy.testing3.Proto3Message;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link JsType}. */
@RunWith(JUnit4.class)
public final class JsTypeTest {
  private static final ListType LIST_OF_HTML = ListType.of(HtmlType.getInstance());
  private static final SoyType NULLABLE_LIST_OF_HTML = unionWithNull(LIST_OF_HTML);
  private static final SoyType UNION_OF_STRING_OR_INT =
      UnionType.of(StringType.getInstance(), IntType.getInstance());
  private static final SoyType NULLABLE_STRING = unionWithNull(StringType.getInstance());

  @Test
  public void testGetJsTypeName() {
    assertThatTypeExpr(AnyType.getInstance()).isEqualTo("*");
    assertThatTypeExpr(UnknownType.getInstance()).isEqualTo("?");
    assertThatTypeExprForRecordMember(UnknownType.getInstance()).isEqualTo("?");
    assertThatTypeExprForOptionalRecordMember(UnknownType.getInstance()).isEqualTo("(?|undefined)");

    assertThatTypeExpr(IntType.getInstance()).isEqualTo("number");

    // Basic unions
    assertThatTypeExpr(UNION_OF_STRING_OR_INT).isEqualTo("number|string");
    assertThatTypeExprForRecordMember(UNION_OF_STRING_OR_INT).isEqualTo("(number|string)");
    assertThatTypeExprForOptionalRecordMember(UNION_OF_STRING_OR_INT)
        .isEqualTo("(number|string|undefined)");
    assertThatTypeExprForRecordMember(
            UnionType.of(StringType.getInstance(), UnknownType.getInstance()))
        .isEqualTo("?");
    assertThatTypeExprForOptionalRecordMember(
            UnionType.of(StringType.getInstance(), UnknownType.getInstance()))
        .isEqualTo("(?|undefined)");

    // Sanitized types
    assertThatTypeExpr(HtmlType.getInstance())
        .isEqualTo("!goog.soy.data.SanitizedHtml|!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string");
    assertThatTypeExpr(unionWithNull(HtmlType.getInstance()))
        .isEqualTo(
            "!goog.soy.data.SanitizedHtml|!safevalues.SafeHtml|"
                + "!soy.$$EMPTY_STRING_|null|string");

    assertThatTypeExpr(UnionType.of(HtmlType.getInstance(), UriType.getInstance()))
        .isEqualTo(
            "!goog.Uri|!goog.soy.data.SanitizedHtml|!goog.soy.data.SanitizedUri"
                + "|!safevalues.SafeHtml|!safevalues.SafeUrl|!safevalues.TrustedResourceUrl"
                + "|!soy.$$EMPTY_STRING_|string");

    // Arrays
    assertThatTypeExpr(LIST_OF_HTML)
        .isEqualTo(
            "(!Array<!goog.soy.data.SanitizedHtml|!safevalues.SafeHtml|"
                + "!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!goog.soy.data.SanitizedHtml|!safevalues.SafeHtml|"
                + "!soy.$$EMPTY_STRING_|string>)");

    // Nullable types
    assertThatTypeExpr(NULLABLE_STRING).isEqualTo("null|string");

    assertThatTypeExpr(NULLABLE_LIST_OF_HTML)
        .isEqualTo(
            "(!Array<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>)|null");

    // Records
    assertThatTypeExpr(
            RecordType.of(ImmutableMap.of("foo", IntType.getInstance(), "bar", LIST_OF_HTML)))
        .isEqualTo(
            "{foo: number, bar:"
                + " (!Array<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>),}");
    assertThatTypeExpr(
            RecordType.of(ImmutableMap.of("foo", IntType.getInstance(), "bar", LIST_OF_HTML)))
        .isEqualTo(
            "{foo: number, bar:"
                + " (!Array<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>),}");

    assertThatTypeExpr(
            RecordType.of(
                ImmutableMap.of("foo", IntType.getInstance(), "bar", NULLABLE_LIST_OF_HTML)))
        .isEqualTo(
            "{foo: number, bar:"
                + " ((!Array<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!goog.soy.data.SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>)|null),}");

    assertThatTypeExpr(MapType.of(StringType.getInstance(), HtmlType.getInstance()))
        .isEqualTo(
            "!ReadonlyMap<"
                + "string,!goog.soy.data.SanitizedHtml|!safevalues.SafeHtml"
                + "|!soy.$$EMPTY_STRING_|string>");
  }

  @Test
  public void testForSoyTypeStrict() {
    assertThatTypeExprStrict(SoyProtoEnumType.create(Proto3Message.AnEnum.getDescriptor()))
        .isEqualTo("!proto.soy.test3.Proto3Message.AnEnum");

    assertThatTypeExprStrict(SoyProtoType.newForTest(Proto3Message.getDescriptor()))
        .isEqualTo("!proto.soy.test3.Proto3Message");

    assertThatTypeExprStrict(HtmlType.getInstance())
        .isEqualTo(
            "!element_lib_idom.IdomFunction|!goog.soy.data.SanitizedHtml|!safevalues.SafeHtml"
                + "|function(!incrementaldomlib.IncrementalDomRenderer): undefined");
  }

  @Test
  public void testGetTypeAssertion() {
    assertThat(getTypeAssertion(StringType.getInstance(), "x")).isEqualTo("typeof x === 'string'");
    assertThat(getTypeAssertion(IntType.getInstance(), "x")).isEqualTo("typeof x === 'number'");
    assertThat(getTypeAssertion(BoolType.getInstance(), "x")).isEqualTo("typeof x === 'boolean'");

    assertThat(getTypeAssertion(SoyTypes.unionWithNullish(BoolType.getInstance()), "x"))
        .isEqualTo("x == null || typeof x === 'boolean'");
    assertThat(getTypeAssertion(SoyTypes.unionWithNull(BoolType.getInstance()), "x"))
        .isEqualTo("x === null || typeof x === 'boolean'");
    assertThat(getTypeAssertion(SoyTypes.unionWithUndefined(BoolType.getInstance()), "x"))
        .isEqualTo("x === undefined || typeof x === 'boolean'");
    assertThat(getTypeAssertion(HtmlType.getInstance(), "x"))
        .isEqualTo("goog.soy.data.SanitizedHtml.isCompatibleWith(x)");

    assertThat(getTypeAssertion(LIST_OF_HTML, "x")).isEqualTo("Array.isArray(x)");

    assertThat(getTypeAssertion(NULLABLE_LIST_OF_HTML, "x"))
        .isEqualTo("x === null || Array.isArray(x)");

    assertThat(
            getTypeAssertion(
                UnionType.of(StringType.getInstance(), ListType.of(IntType.getInstance())), "x"))
        .isEqualTo("Array.isArray(x) || typeof x === 'string'");
  }

  @Test
  public void testGetSoyTypeAssertionStrict() {
    assertThat(
            getSoyTypeAssertionStrict(
                UnionType.of(BoolType.getInstance(), IntType.getInstance()), "x"))
        .isEqualTo(
            "soy.assertParamType(typeof x === 'boolean' || typeof x === 'number', 'x', x, '@param',"
                + " 'boolean|number')");
  }

  private static String getTypeAssertion(SoyType instance, String varName) {
    return forJsSrc()
        .get(instance)
        .getTypeAssertion(
            id(varName), CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
        .get()
        .assertExprAndCollectRequires(r -> {})
        .getText();
  }

  private String getSoyTypeAssertionStrict(SoyType instance, String varName) {
    return forIdomSrcState()
        .get(instance)
        .getSoyParamTypeAssertion(
            id(varName),
            varName,
            "@param",
            CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
        .get()
        .assertExprAndCollectRequires(r -> {})
        .getText();
  }

  @Test
  public void testConstantTypesUseReadonlyArray() {
    assertThat(JsType.forJsSrcConstant().get(ListType.of(IntType.getInstance())).typeExpr())
        .isEqualTo("!ReadonlyArray<number>");
    assertThat(
            JsType.forJsSrcConstant()
                .get(ListType.of(ListType.of(StringType.getInstance())))
                .typeExpr())
        .isEqualTo("!ReadonlyArray<!ReadonlyArray<string>>");
    assertThat(JsType.forIdomSrcConstant().get(ListType.of(HtmlType.getInstance())).typeExpr())
        .isEqualTo(
            "!ReadonlyArray<!element_lib_idom.IdomFunction|!goog.soy.data.SanitizedHtml"
                + "|!safevalues.SafeHtml"
                + "|function(!incrementaldomlib.IncrementalDomRenderer): undefined>");
  }

  @Test
  public void testNestedArrayTypeDepthIsCapped() {
    SoyType depth1 = ListType.of(IntType.getInstance());
    SoyType depth2 = ListType.of(depth1);
    SoyType depth3 = ListType.of(depth2);

    assertThatTypeExpr(depth1).isEqualTo("(!Array<number>|!ReadonlyArray<number>)");
    assertThatTypeExpr(depth2)
        .isEqualTo(
            "(!Array<(!Array<number>|!ReadonlyArray<number>)>"
                + "|!ReadonlyArray<(!Array<number>|!ReadonlyArray<number>)>)");
    assertThatTypeExpr(depth3)
        .isEqualTo(
            "(!Array<(!Array<!ReadonlyArray<number>>|!ReadonlyArray<!ReadonlyArray<number>>)>"
                + "|!ReadonlyArray<(!Array<!ReadonlyArray<number>>"
                + "|!ReadonlyArray<!ReadonlyArray<number>>)>)");

    SoyType depth20 = IntType.getInstance();
    for (int i = 0; i < 20; i++) {
      depth20 = ListType.of(depth20);
    }
    String depth20TypeExpr = forJsSrc().get(depth20).typeExpr();
    assertThat(depth20TypeExpr.length()).isLessThan(2_000);
    assertThat(depth20TypeExpr).contains("!ReadonlyArray<number>");
  }

  @Test
  public void testTypeNodeBudgetBoundsDagExplosion() {
    SoyType dag = IntType.getInstance();
    for (int i = 0; i < 20; i++) {
      dag = RecordType.of(ImmutableMap.of("left", dag, "right", dag));
    }
    JsType.RecursiveJsTypeProducer producer = forJsSrc();
    JsType dagJsType = producer.get(dag);
    assertThat(dagJsType.typeExpr()).contains("?");
    assertThat(dagJsType.typeExpr().length()).isLessThan(200_000);

    // State resets after each top-level conversion on the same producer instance.
    assertThat(producer.get(ListType.of(IntType.getInstance())).typeExpr())
        .isEqualTo("(!Array<number>|!ReadonlyArray<number>)");

    // Runtime predicate is preserved even for unions containing a truncated record.
    JsType nullableDag = producer.get(unionWithNull(dag));
    assertThat(nullableDag.typeExpr()).startsWith("null|{");
    assertThat(nullableDag.typeExpr()).contains("?");
    assertThat(nullableDag.typeExpr().length()).isLessThan(200_000);
    assertThat(getTypeAssertion(unionWithNull(dag), "x"))
        .isEqualTo("x === null || goog.isObject(x)");
    assertThat(
            nullableDag
                .getTypeAssertion(
                    id("x"), CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
                .get()
                .getCode(FormatOptions.JSSRC))
        .contains("x === null || goog.isObject(x)");

    // When a direct member of a union itself hits the node budget and falls back to `?`, the
    // union collapses its type expression to `?` while keeping member predicates intact.
    SoyType unionWithTruncatedTail =
        UnionType.of(
            RecordType.of(ImmutableMap.of("head", dag)),
            RecordType.of(ImmutableMap.of("tail", dag)));
    JsType unionJsType = producer.get(unionWithTruncatedTail);
    assertThat(unionJsType.typeExpr()).isEqualTo("?");
    assertThat(
            unionJsType
                .getTypeAssertion(
                    id("x"), CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
                .get()
                .getCode(FormatOptions.JSSRC))
        .contains("goog.isObject(x)");
  }

  private StringSubject assertThatTypeExpr(SoyType soyType) {
    return assertThat(forJsSrc().get(soyType).typeExpr());
  }

  private StringSubject assertThatTypeExprStrict(SoyType soyType) {
    return assertThat(forIdomSrcState().get(soyType).typeExpr());
  }

  private StringSubject assertThatTypeExprForRecordMember(SoyType soyType) {
    return assertThat(forJsSrc().get(soyType).typeExprForRecordMember(/* isOptional= */ false));
  }

  private StringSubject assertThatTypeExprForOptionalRecordMember(SoyType soyType) {
    return assertThat(forJsSrc().get(soyType).typeExprForRecordMember(/* isOptional= */ true));
  }
}
