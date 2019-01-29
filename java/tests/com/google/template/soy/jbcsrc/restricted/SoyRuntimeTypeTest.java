/*
 * Copyright 2018 Google Inc.
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
package com.google.template.soy.jbcsrc.restricted;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.testing.Proto3Message;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.BoolType;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.Type;

/** Tests for {@link SoyRuntimeType} */
@RunWith(JUnit4.class)
public class SoyRuntimeTypeTest {
  @Test
  public void testPrimitiveTypes() {
    assertThat(NullType.getInstance()).isBoxedAs(SoyValue.class).isUnboxedAs(Object.class);

    assertThat(IntType.getInstance()).isBoxedAs(IntegerData.class).isUnboxedAs(long.class);

    assertThat(BoolType.getInstance()).isBoxedAs(BooleanData.class).isUnboxedAs(boolean.class);

    assertThat(StringType.getInstance()).isBoxedAs(SoyString.class).isUnboxedAs(String.class);

    assertThat(FloatType.getInstance()).isBoxedAs(FloatData.class).isUnboxedAs(double.class);

    assertThat(new SoyProtoEnumType(Proto3Message.AnEnum.getDescriptor()))
        .isBoxedAs(IntegerData.class)
        .isUnboxedAs(int.class);
    for (SanitizedContentKind kind : SanitizedContentKind.values()) {
      if (kind == SanitizedContentKind.TEXT) {
        continue;
      }
      assertThat(SanitizedType.getTypeForContentKind(kind))
          .isBoxedAs(SanitizedContent.class)
          .isUnboxedAs(String.class);
    }
    assertThat(
            new SoyProtoType(
                new SoyTypeRegistry(), Proto3Message.getDescriptor(), ImmutableSet.of()))
        .isBoxedAs(SoyProtoValue.class)
        .isUnboxedAs(Proto3Message.class);
    assertThat(ListType.of(IntType.getInstance())).isBoxedAs(SoyList.class).isUnboxedAs(List.class);

    assertThat(UnknownType.getInstance()).isBoxedAs(SoyValue.class).isNotUnboxable();
    assertThat(AnyType.getInstance()).isBoxedAs(SoyValue.class).isNotUnboxable();
  }

  @Test
  public void testUnionTypes() {
    // no unboxed representation for this one
    assertThat(UnionType.of(IntType.getInstance(), StringType.getInstance()))
        .isBoxedAs(SoyValue.class)
        .isNotUnboxable();

    // But unions of lists do work
    assertThat(
            UnionType.of(ListType.of(IntType.getInstance()), ListType.of(StringType.getInstance())))
        .isBoxedAs(SoyList.class)
        .isUnboxedAs(List.class);
    // as do union of sanitized
    assertThat(
            UnionType.of(SanitizedType.HtmlType.getInstance(), SanitizedType.JsType.getInstance()))
        .isBoxedAs(SanitizedContent.class)
        .isUnboxedAs(String.class);
  }

  static SoyRuntimeTypeSubject assertThat(SoyType type) {
    return Truth.assertAbout(SoyRuntimeTypeSubject::new).that(type);
  }

  private static final class SoyRuntimeTypeSubject extends Subject<SoyRuntimeTypeSubject, SoyType> {
    protected SoyRuntimeTypeSubject(FailureMetadata metadata, SoyType actual) {
      super(metadata, actual);
    }

    SoyRuntimeTypeSubject isBoxedAs(Class<?> type) {
      return isBoxedAs(Type.getType(type));
    }

    SoyRuntimeTypeSubject isBoxedAs(Type type) {
      SoyRuntimeType boxed = SoyRuntimeType.getBoxedType(actual());
      if (!boxed.runtimeType().equals(type)) {
        failWithBadResults("isBoxedAs", type.toString(), "boxed as", boxed.runtimeType());
      }
      return this;
    }

    SoyRuntimeTypeSubject isUnboxedAs(Class<?> type) {
      return isUnboxedAs(Type.getType(type));
    }

    SoyRuntimeTypeSubject isUnboxedAs(Type type) {
      Optional<SoyRuntimeType> unboxedAs = SoyRuntimeType.getUnboxedType(actual());
      if (!unboxedAs.isPresent()) {
        failWithBadResults("isUnboxedAs", type.toString(), "has no unboxed form", "");
      }
      if (!unboxedAs.get().runtimeType().equals(type)) {
        failWithBadResults(
            "isUnboxedAs", type.toString(), "unboxed as", unboxedAs.get().runtimeType());
      }
      return this;
    }

    SoyRuntimeTypeSubject isNotUnboxable() {
      Optional<SoyRuntimeType> unboxedAs = SoyRuntimeType.getUnboxedType(actual());
      if (unboxedAs.isPresent()) {
        failWithBadResults("isNotUnboxable", "", "unboxed as", unboxedAs.get().runtimeType());
      }
      return this;
    }
  }
}
