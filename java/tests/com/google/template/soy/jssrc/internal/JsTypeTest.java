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
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;
import static com.google.template.soy.jssrc.internal.JsType.forSoyType;
import static com.google.template.soy.types.SoyTypes.makeNullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.StringSubject;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.MapType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.UriType;
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
    assertThatTypeExpr(UNION_OF_STRING_OR_INT)
        .isEqualTo("!goog.soy.data.SanitizedContent|number|string");
    assertThatTypeExprForRecordMember(UNION_OF_STRING_OR_INT)
        .isEqualTo("(!goog.soy.data.SanitizedContent|number|string)");
    assertThatTypeExprForOptionalRecordMember(UNION_OF_STRING_OR_INT)
        .isEqualTo("(!goog.soy.data.SanitizedContent|number|string|undefined)");
    assertThatTypeExprForRecordMember(
            UnionType.of(StringType.getInstance(), UnknownType.getInstance()))
        .isEqualTo("?");
    assertThatTypeExprForOptionalRecordMember(
            UnionType.of(StringType.getInstance(), UnknownType.getInstance()))
        .isEqualTo("(?|undefined)");

    // Sanitized types
    assertThatTypeExpr(HtmlType.getInstance())
        .isEqualTo(
            "!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml|!goog.soy.data.UnsanitizedText"
                + "|string");
    assertThatTypeExpr(makeNullable(HtmlType.getInstance()))
        .isEqualTo(
            "!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml|!goog.soy.data.UnsanitizedText|null"
                + "|string|undefined");
    assertThatTypeExpr(UnionType.of(HtmlType.getInstance(), UriType.getInstance()))
        .isEqualTo(
            "!goog.Uri|!goog.html.SafeHtml|!goog.html.SafeUrl|!goog.html.TrustedResourceUrl"
                + "|!goog.soy.data.SanitizedHtml|!goog.soy.data.SanitizedUri"
                + "|!goog.soy.data.UnsanitizedText|string");

    // Arrays
    assertThatTypeExpr(LIST_OF_HTML)
        .isEqualTo(
            "!Array<!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml"
                + "|!goog.soy.data.UnsanitizedText|string>");

    // Nullable types
    assertThatTypeExpr(NULLABLE_STRING)
        .isEqualTo("!goog.soy.data.SanitizedContent|null|string|undefined");

    assertThatTypeExpr(NULLABLE_LIST_OF_HTML)
        .isEqualTo(
            "!Array<!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml"
                + "|!goog.soy.data.UnsanitizedText|string>|null|undefined");

    // Records
    assertThatTypeExpr(
            RecordType.of(
                ImmutableMap.<String, SoyType>of(
                    "foo", IntType.getInstance(), "bar", LIST_OF_HTML)))
        .isEqualTo(
            "{bar: !Array<!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml"
                + "|!goog.soy.data.UnsanitizedText|string>, foo: number,}");
    assertThatTypeExpr(
            RecordType.of(
                ImmutableMap.<String, SoyType>of(
                    "foo", IntType.getInstance(), "bar", LIST_OF_HTML)))
        .isEqualTo(
            "{bar: !Array<!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml"
                + "|!goog.soy.data.UnsanitizedText|string>, foo: number,}");
    assertThatTypeExpr(
            RecordType.of(
                ImmutableMap.<String, SoyType>of(
                    "foo", IntType.getInstance(), "bar", NULLABLE_LIST_OF_HTML)))
        .isEqualTo(
            "{bar: (!Array<!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml"
                + "|!goog.soy.data.UnsanitizedText|string>|null|undefined), foo: number,}");
    assertThatTypeExpr(RecordType.of(ImmutableMap.<String, SoyType>of())).isEqualTo("!Object");

    assertThatTypeExpr(MapType.of(StringType.getInstance(), HtmlType.getInstance()))
        .isEqualTo(
            "!soy.map.Map<!goog.soy.data.SanitizedContent|string,!goog.html.SafeHtml|"
                + "!goog.soy.data.SanitizedHtml|!goog.soy.data.UnsanitizedText|string>");
  }

  @Test
  public void testTypeTests() {
    assertThat(getTypeAssertion(StringType.getInstance(), "x"))
        .isEqualTo("goog.isString(x) || x instanceof goog.soy.data.SanitizedContent");
    assertThat(getTypeAssertion(IntType.getInstance(), "x")).isEqualTo("goog.isNumber(x)");
    assertThat(getTypeAssertion(BoolType.getInstance(), "x"))
        .isEqualTo("goog.isBoolean(x) || x === 1 || x === 0");

    assertThat(getTypeAssertion(SoyTypes.makeNullable(BoolType.getInstance()), "x"))
        .isEqualTo("x == null || (goog.isBoolean(x) || x === 1 || x === 0)");
    assertThat(getTypeAssertion(HtmlType.getInstance(), "x"))
        .isEqualTo("goog.soy.data.SanitizedHtml.isCompatibleWith(x)");

    assertThat(getTypeAssertion(LIST_OF_HTML, "x")).isEqualTo("goog.isArray(x)");

    assertThat(getTypeAssertion(NULLABLE_LIST_OF_HTML, "x"))
        .isEqualTo("x == null || goog.isArray(x)");

    assertThat(
            getTypeAssertion(
                UnionType.of(StringType.getInstance(), ListType.of(IntType.getInstance())), "x"))
        .isEqualTo(
            "goog.isArray(x) || (goog.isString(x) || x instanceof goog.soy.data.SanitizedContent)");
  }

  private static String getTypeAssertion(SoyType instance, String varName) {
    return forSoyType(instance, false)
        .getTypeAssertion(
            id(varName), CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
        .get()
        .assertExprAndCollectRequires(CodeChunk.RequiresCollector.NULL)
        .getText();
  }

  private StringSubject assertThatTypeExpr(SoyType soyType) {
    return assertThat(forSoyType(soyType, false).typeExpr());
  }

  private StringSubject assertThatTypeExprForRecordMember(SoyType soyType) {
    return assertThat(forSoyType(soyType, false).typeExprForRecordMember(/* isOptional= */ false));
  }

  private StringSubject assertThatTypeExprForOptionalRecordMember(SoyType soyType) {
    return assertThat(forSoyType(soyType, false).typeExprForRecordMember(/* isOptional= */ true));
  }
}
