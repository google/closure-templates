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
import static com.google.template.soy.jssrc.internal.JsType.forIncrementalDomState;
import static com.google.template.soy.jssrc.internal.JsType.forJsSrc;
import static com.google.template.soy.types.SoyTypes.makeNullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.StringSubject;
import com.google.template.soy.jssrc.dsl.CodeChunk;
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
  private static final SoyType NULLABLE_LIST_OF_HTML = makeNullable(LIST_OF_HTML);
  private static final SoyType UNION_OF_STRING_OR_INT =
      UnionType.of(StringType.getInstance(), IntType.getInstance());
  private static final SoyType NULLABLE_STRING = makeNullable(StringType.getInstance());

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
        .isEqualTo("!$SanitizedHtml|!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string");
    assertThatTypeExpr(makeNullable(HtmlType.getInstance()))
        .isEqualTo("!$SanitizedHtml|!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|null|string");

    assertThatTypeExpr(UnionType.of(HtmlType.getInstance(), UriType.getInstance()))
        .isEqualTo(
            "!$GoogUri|!$SanitizedHtml|!$SanitizedUri"
                + "|!safevalues.SafeHtml|!safevalues.SafeUrl|!safevalues.TrustedResourceUrl"
                + "|!soy.$$EMPTY_STRING_|string");

    // Arrays
    assertThatTypeExpr(LIST_OF_HTML)
        .isEqualTo(
            "(!Array<!$SanitizedHtml|!safevalues.SafeHtml|"
                + "!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!$SanitizedHtml|!safevalues.SafeHtml|"
                + "!soy.$$EMPTY_STRING_|string>)");

    // Nullable types
    assertThatTypeExpr(NULLABLE_STRING).isEqualTo("null|string");

    assertThatTypeExpr(NULLABLE_LIST_OF_HTML)
        .isEqualTo(
            "(!Array<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>)|null");

    // Records
    assertThatTypeExpr(
            RecordType.of(ImmutableMap.of("foo", IntType.getInstance(), "bar", LIST_OF_HTML)))
        .isEqualTo(
            "{foo: number, bar:"
                + " (!Array<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>),}");
    assertThatTypeExpr(
            RecordType.of(ImmutableMap.of("foo", IntType.getInstance(), "bar", LIST_OF_HTML)))
        .isEqualTo(
            "{foo: number, bar:"
                + " (!Array<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>),}");

    assertThatTypeExpr(
            RecordType.of(
                ImmutableMap.of("foo", IntType.getInstance(), "bar", NULLABLE_LIST_OF_HTML)))
        .isEqualTo(
            "{foo: number, bar:"
                + " ((!Array<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>|"
                + "!ReadonlyArray<!$SanitizedHtml|"
                + "!safevalues.SafeHtml|!soy.$$EMPTY_STRING_|string>)|null),}");

    assertThatTypeExpr(MapType.of(StringType.getInstance(), HtmlType.getInstance()))
        .isEqualTo(
            "!soyMap.Map<"
                + "string,!$SanitizedHtml|!safevalues.SafeHtml"
                + "|!soy.$$EMPTY_STRING_|string>");
  }

  @Test
  public void testForSoyTypeStrict() {
    assertThatTypeExprStrict(new SoyProtoEnumType(Proto3Message.AnEnum.getDescriptor()))
        .isEqualTo("!$proto$soy$test3$Proto3Message$AnEnum");

    assertThatTypeExprStrict(SoyProtoType.newForTest(Proto3Message.getDescriptor()))
        .isEqualTo("!$proto$soy$test3$Proto3Message");

    assertThatTypeExprStrict(HtmlType.getInstance())
        .isEqualTo(
            "!$SanitizedHtml|!element_lib_idom.IdomFunction|!safevalues.SafeHtml"
                + "|function(!incrementaldomlib.IncrementalDomRenderer): undefined");
  }

  @Test
  public void testGetTypeAssertion() {
    assertThat(getTypeAssertion(StringType.getInstance(), "x")).isEqualTo("typeof x === 'string'");
    assertThat(getTypeAssertion(IntType.getInstance(), "x")).isEqualTo("typeof x === 'number'");
    assertThat(getTypeAssertion(BoolType.getInstance(), "x")).isEqualTo("typeof x === 'boolean'");

    assertThat(getTypeAssertion(SoyTypes.makeNullish(BoolType.getInstance()), "x"))
        .isEqualTo("x == null || typeof x === 'boolean'");
    assertThat(getTypeAssertion(SoyTypes.makeNullable(BoolType.getInstance()), "x"))
        .isEqualTo("x === null || typeof x === 'boolean'");
    assertThat(getTypeAssertion(SoyTypes.makeUndefinable(BoolType.getInstance()), "x"))
        .isEqualTo("x === undefined || typeof x === 'boolean'");
    assertThat(getTypeAssertion(HtmlType.getInstance(), "x"))
        .isEqualTo("$SanitizedHtml.isCompatibleWith(x)");

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
    return forJsSrc(instance)
        .getTypeAssertion(
            id(varName), CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
        .get()
        .assertExprAndCollectRequires(r -> {})
        .getText();
  }

  private String getSoyTypeAssertionStrict(SoyType instance, String varName) {
    return forIncrementalDomState(instance)
        .getSoyParamTypeAssertion(
            id(varName),
            varName,
            "@param",
            CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
        .get()
        .assertExprAndCollectRequires(r -> {})
        .getText();
  }

  private StringSubject assertThatTypeExpr(SoyType soyType) {
    return assertThat(forJsSrc(soyType).typeExpr());
  }

  private StringSubject assertThatTypeExprStrict(SoyType soyType) {
    return assertThat(forIncrementalDomState(soyType).typeExpr());
  }

  private StringSubject assertThatTypeExprForRecordMember(SoyType soyType) {
    return assertThat(forJsSrc(soyType).typeExprForRecordMember(/* isOptional= */ false));
  }

  private StringSubject assertThatTypeExprForOptionalRecordMember(SoyType soyType) {
    return assertThat(forJsSrc(soyType).typeExprForRecordMember(/* isOptional= */ true));
  }
}
