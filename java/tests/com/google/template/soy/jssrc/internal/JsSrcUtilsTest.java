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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.types.SoyTypes.makeNullable;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.SanitizedType.HtmlType;
import com.google.template.soy.types.primitive.SanitizedType.UriType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;

import junit.framework.TestCase;

/**
 * Unit tests for JsSrcUtils.
 *
 */
public class JsSrcUtilsTest extends TestCase {

  private static final ListType LIST_OF_HTML = ListType.of(HtmlType.getInstance());
  private static final SoyType NULLABLE_LIST_OF_HTML = makeNullable(LIST_OF_HTML);
  private static final SoyType UNION_OF_STRING_OR_INT =
      UnionType.of(StringType.getInstance(), IntType.getInstance());
  private static final SoyType NULLABLE_STRING = makeNullable(StringType.getInstance());

  public void testEscapeUnicodeFormatChars() {
    assertThat(
        JsSrcUtils.escapeUnicodeFormatChars(
            "'Cf:\u202A\u202B\u202C\u202D\u202E\u200E\u200F; Not Cf:\u2222\uEEEE\u9EC4\u607A'"))
        .isEqualTo(
            "'Cf:\\u202A\\u202B\\u202C\\u202D\\u202E\\u200E\\u200F; Not Cf:\u2222\uEEEE\u9EC4\u607A'");
  }


  public void testGetJsTypeName() {
    // Primitives
    assertThat(JsSrcUtils.getJsTypeExpr(AnyType.getInstance())).isEqualTo("*");
    assertThat(JsSrcUtils.getJsTypeExpr(UnknownType.getInstance())).isEqualTo("(?)");
    assertThat(JsSrcUtils.getJsTypeExpr(IntType.getInstance())).isEqualTo("number");

    // Basic unions
    assertThat(JsSrcUtils.getJsTypeExpr(UNION_OF_STRING_OR_INT)).isEqualTo("number|string");
    assertThat(JsSrcUtils.getJsTypeExpr(UNION_OF_STRING_OR_INT, true, false))
        .isEqualTo("(number|string)");
    assertThat(
        JsSrcUtils.getJsTypeExpr(UnionType.of(StringType.getInstance(), UnknownType.getInstance())))
        .isEqualTo("(?)");

    // Sanitized types
    assertThat(JsSrcUtils.getJsTypeExpr(HtmlType.getInstance()))
        .isEqualTo("!soydata.SanitizedHtml|string");
    assertThat(JsSrcUtils.getJsTypeExpr(makeNullable(HtmlType.getInstance())))
        .isEqualTo("?soydata.SanitizedHtml|string|undefined");
    assertThat(
        JsSrcUtils.getJsTypeExpr(UnionType.of(HtmlType.getInstance(), UriType.getInstance())))
        .isEqualTo("!soydata.SanitizedHtml|!soydata.SanitizedUri|string");

    // Arrays
    assertThat(JsSrcUtils.getJsTypeExpr(LIST_OF_HTML, false, false))
        .isEqualTo("?Array<!soydata.SanitizedHtml|string>");
    assertThat(JsSrcUtils.getJsTypeExpr(LIST_OF_HTML, false, true))
        .isEqualTo("!Array<!soydata.SanitizedHtml|string>");

    // Nullable types
    assertThat(JsSrcUtils.getJsTypeExpr(NULLABLE_STRING)).isEqualTo("null|string|undefined");
    assertThat(JsSrcUtils.getJsTypeExpr(NULLABLE_STRING, true, false))
        .isEqualTo("(null|string|undefined)");
    assertThat(JsSrcUtils.getJsTypeExpr(NULLABLE_STRING, false, true))
        .isEqualTo("null|string|undefined");
    assertThat(JsSrcUtils.getJsTypeExpr(NULLABLE_LIST_OF_HTML))
        .isEqualTo("?Array<!soydata.SanitizedHtml|string>|undefined");
    assertThat(JsSrcUtils.getJsTypeExpr(NULLABLE_LIST_OF_HTML, true, false))
        .isEqualTo("(?Array<!soydata.SanitizedHtml|string>|undefined)");
    assertThat(JsSrcUtils.getJsTypeExpr(NULLABLE_LIST_OF_HTML, false, true))
        .isEqualTo("?Array<!soydata.SanitizedHtml|string>|undefined");

    // Records
    assertThat(
        JsSrcUtils.getJsTypeExpr(RecordType.of(
            ImmutableMap.<String, SoyType>of("foo", IntType.getInstance(), "bar", LIST_OF_HTML))))
        .isEqualTo("{bar: !Array<!soydata.SanitizedHtml|string>, foo: number}");
    assertThat(
        JsSrcUtils.getJsTypeExpr(
            RecordType.of(ImmutableMap.<String, SoyType>of(
                "foo", IntType.getInstance(), "bar", LIST_OF_HTML)),
            false, false)).isEqualTo("{bar: !Array<!soydata.SanitizedHtml|string>, foo: number}");
    assertThat(
        JsSrcUtils.getJsTypeExpr(RecordType.of(ImmutableMap.<String, SoyType>of(
            "foo", IntType.getInstance(), "bar", NULLABLE_LIST_OF_HTML))))
        .isEqualTo("{bar: (?Array<!soydata.SanitizedHtml|string>|undefined), foo: number}");
    assertThat(JsSrcUtils.getJsTypeExpr(RecordType.of(ImmutableMap.<String, SoyType>of())))
        .isEqualTo("!Object");
  }
}
