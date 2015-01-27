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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
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
  private static final UnionType NULLABLE_LIST_OF_HTML = nullable(LIST_OF_HTML);
  private static final UnionType UNION_OF_STRING_OR_INT =
      UnionType.of(StringType.getInstance(), IntType.getInstance());
  private static final UnionType NULLABLE_STRING = nullable(StringType.getInstance());

  public void testEscapeUnicodeFormatChars() {
    assertEquals(
        "'Cf:\\u202A\\u202B\\u202C\\u202D\\u202E\\u200E\\u200F; Not Cf:\u2222\uEEEE\u9EC4\u607A'",
        JsSrcUtils.escapeUnicodeFormatChars(
            "'Cf:\u202A\u202B\u202C\u202D\u202E\u200E\u200F; Not Cf:\u2222\uEEEE\u9EC4\u607A'"));
  }


  public void testGetJsTypeName() {
    // Primitives
    assertEquals("*", JsSrcUtils.getJsTypeExpr(AnyType.getInstance()));
    assertEquals("?", JsSrcUtils.getJsTypeExpr(UnknownType.getInstance()));
    assertEquals("number", JsSrcUtils.getJsTypeExpr(IntType.getInstance()));

    // Basic unions
    assertEquals("number|string",
        JsSrcUtils.getJsTypeExpr(UNION_OF_STRING_OR_INT));
    assertEquals("(number|string)",
        JsSrcUtils.getJsTypeExpr(UNION_OF_STRING_OR_INT, true, false));
    assertEquals("(?)", JsSrcUtils.getJsTypeExpr(
        UnionType.of(StringType.getInstance(), UnknownType.getInstance())));

    // Sanitized types
    assertEquals("soydata.SanitizedHtml|string",
        JsSrcUtils.getJsTypeExpr(HtmlType.getInstance()));
    assertEquals("soydata.SanitizedHtml|soydata.SanitizedUri|string",
        JsSrcUtils.getJsTypeExpr(UnionType.of(HtmlType.getInstance(), UriType.getInstance())));

    // Arrays
    assertEquals("Array.<soydata.SanitizedHtml|string>",
        JsSrcUtils.getJsTypeExpr(LIST_OF_HTML, false, false));
    assertEquals("!Array.<soydata.SanitizedHtml|string>",
        JsSrcUtils.getJsTypeExpr(LIST_OF_HTML, false, true));

    // Nullable types
    assertEquals("null|string|undefined",
        JsSrcUtils.getJsTypeExpr(NULLABLE_STRING));
    assertEquals("(null|string|undefined)",
        JsSrcUtils.getJsTypeExpr(NULLABLE_STRING, true, false));
    assertEquals("null|string|undefined",
        JsSrcUtils.getJsTypeExpr(NULLABLE_STRING, false, true));
    assertEquals("Array.<soydata.SanitizedHtml|string>|undefined",
        JsSrcUtils.getJsTypeExpr(NULLABLE_LIST_OF_HTML));
    assertEquals("(Array.<soydata.SanitizedHtml|string>|undefined)",
        JsSrcUtils.getJsTypeExpr(NULLABLE_LIST_OF_HTML, true, false));
    assertEquals("Array.<soydata.SanitizedHtml|string>|undefined",
        JsSrcUtils.getJsTypeExpr(NULLABLE_LIST_OF_HTML, false, true));

    // Records
    assertEquals("{bar: !Array.<soydata.SanitizedHtml|string>, foo: number}",
        JsSrcUtils.getJsTypeExpr(RecordType.of(
            ImmutableMap.<String, SoyType>of(
                "foo", IntType.getInstance(),
                "bar", LIST_OF_HTML))));
    assertEquals("{bar: !Array.<soydata.SanitizedHtml|string>, foo: number}",
        JsSrcUtils.getJsTypeExpr(RecordType.of(
            ImmutableMap.<String, SoyType>of(
                "foo", IntType.getInstance(),
                "bar", LIST_OF_HTML)), false, false));
    assertEquals("{bar: (Array.<soydata.SanitizedHtml|string>|undefined), foo: number}",
        JsSrcUtils.getJsTypeExpr(RecordType.of(
            ImmutableMap.<String, SoyType>of(
                "foo", IntType.getInstance(),
                "bar", NULLABLE_LIST_OF_HTML))));
  }


  private static final UnionType nullable(SoyType type) {
    return UnionType.of(type, NullType.getInstance());
  }
}
