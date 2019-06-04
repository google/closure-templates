/*
 * Copyright 2017 Google Inc.
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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.FieldAccess;
import com.google.template.soy.testing.Foo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link NullSafeAccumulator}. */
@RunWith(JUnit4.class)
public final class NullSafeAccumulatorTest {

  @Test
  public void testNullSafeChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(Expression.id("a"));
    assertThat(accum).generates("a;");
    assertThat(accum.dotAccess(FieldAccess.id("b"), /* nullSafe= */ true))
        .generates("a == null ? null : a.b;");
    assertThat(accum.bracketAccess(Expression.id("c"), /* nullSafe= */ true))
        .generates(
            "var $tmp$$1;\n"
                + "if (a == null) {\n"
                + "  $tmp$$1 = null;\n"
                + "} else {\n"
                + "  var $tmp = a.b;\n"
                + "  $tmp$$1 = $tmp == null ? null : $tmp[c];\n"
                + "}");
    assertThat(accum.dotAccess(FieldAccess.id("d"), /* nullSafe= */ true))
        .generates(
            "var $tmp$$3;\n"
                + "if (a == null) {\n"
                + "  $tmp$$3 = null;\n"
                + "} else {\n"
                + "  var $tmp$$2;\n"
                + "  var $tmp = a.b;\n"
                + "  if ($tmp == null) {\n"
                + "    $tmp$$2 = null;\n"
                + "  } else {\n"
                + "    var $tmp$$1 = $tmp[c];\n"
                + "    $tmp$$2 = $tmp$$1 == null ? null : $tmp$$1.d;\n"
                + "  }\n"
                + "  $tmp$$3 = $tmp$$2;\n"
                + "}");
    assertThat(accum.bracketAccess(Expression.id("e"), /* nullSafe= */ true))
        .generates(
            "var $tmp$$5;\n"
                + "if (a == null) {\n"
                + "  $tmp$$5 = null;\n"
                + "} else {\n"
                + "  var $tmp$$4;\n"
                + "  var $tmp = a.b;\n"
                + "  if ($tmp == null) {\n"
                + "    $tmp$$4 = null;\n"
                + "  } else {\n"
                + "    var $tmp$$3;\n"
                + "    var $tmp$$1 = $tmp[c];\n"
                + "    if ($tmp$$1 == null) {\n"
                + "      $tmp$$3 = null;\n"
                + "    } else {\n"
                + "      var $tmp$$2 = $tmp$$1.d;\n"
                + "      $tmp$$3 = $tmp$$2 == null ? null : $tmp$$2[e];\n"
                + "    }\n"
                + "    $tmp$$4 = $tmp$$3;\n"
                + "  }\n"
                + "  $tmp$$5 = $tmp$$4;\n"
                + "}");
  }

  @Test
  public void testNonNullSafeChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(Expression.id("a"));
    assertThat(accum)
        .generates("a;");
    assertThat(accum.bracketAccess(Expression.id("b"), /* nullSafe= */ false)).generates("a[b];");
    assertThat(accum.dotAccess(FieldAccess.id("c"), /* nullSafe= */ false)).generates("a[b].c;");
    assertThat(accum.bracketAccess(Expression.id("d"), /* nullSafe= */ false))
        .generates("a[b].c[d];");
    assertThat(accum.dotAccess(FieldAccess.id("e"), /* nullSafe= */ false))
        .generates("a[b].c[d].e;");
  }

  @Test
  public void testMixedChains() {
    NullSafeAccumulator accum = new NullSafeAccumulator(Expression.id("a"));
    assertThat(accum).generates("a;");
    assertThat(accum.dotAccess(FieldAccess.id("b"), /* nullSafe= */ true))
        .generates("a == null ? null : a.b;");
    assertThat(accum.bracketAccess(Expression.id("c"), /* nullSafe= */ false))
        .generates("a == null ? null : a.b[c];");
    assertThat(accum.dotAccess(FieldAccess.id("d"), /* nullSafe= */ true))
        .generates(
            "var $tmp$$1;\n"
                + "if (a == null) {\n"
                + "  $tmp$$1 = null;\n"
                + "} else {\n"
                + "  var $tmp = a.b[c];\n"
                + "  $tmp$$1 = $tmp == null ? null : $tmp.d;\n"
                + "}");
    assertThat(accum.bracketAccess(Expression.id("e"), /* nullSafe= */ false))
        .generates(
            "var $tmp$$1;\n"
                + "if (a == null) {\n"
                + "  $tmp$$1 = null;\n"
                + "} else {\n"
                + "  var $tmp = a.b[c];\n"
                + "  $tmp$$1 = $tmp == null ? null : $tmp.d[e];\n"
                + "}");
  }

  @Test
  public void testCallPreservesChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(Expression.id("a"));
    assertThat(accum.dotAccess(FieldAccess.call("b", Expression.id("c")), /* nullSafe= */ false))
        .generates("a.b(c);");
    assertThat(accum.dotAccess(FieldAccess.call("d", Expression.id("e")), /* nullSafe= */ true))
        .generates("var $tmp = a.b(c);\n$tmp == null ? null : $tmp.d(e);");
  }

  @Test
  public void testMap() {
    FieldDescriptor desc = Foo.getDescriptor().findFieldByName("map_field");
    NullSafeAccumulator accum = new NullSafeAccumulator(Expression.id("a"));
    assertThat(accum.dotAccess(FieldAccess.protoCall("mapFieldMap", desc), /* nullSafe= */ false))
        .generates("a.getMapFieldMap();");
  }

  @Test
  public void testMapGet() {
    FieldDescriptor desc = Foo.getDescriptor().findFieldByName("map_field");
    NullSafeAccumulator accum = new NullSafeAccumulator(Expression.id("a"));
    assertThat(
            accum
                .dotAccess(FieldAccess.protoCall("mapFieldMap", desc), /* nullSafe= */ false)
                .mapGetAccess(Expression.id("key"), /* nullSafe= */ false))
        .generates("a.getMapFieldMap().get(key);");
  }

  private static AccumulatorSubject assertThat(NullSafeAccumulator accumulator) {
    return Truth.assertAbout(AccumulatorSubject::new).that(accumulator);
  }

  private static final class AccumulatorSubject extends Subject {

    private final NullSafeAccumulator actual;

    AccumulatorSubject(FailureMetadata failureMetadata, NullSafeAccumulator actual) {
      super(failureMetadata, actual);
      this.actual = actual;
    }

    void generates(String expectedCode) {
      String actualCode =
          actual
              .result(CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
              .getCode();
      check("getCode()").that(actualCode).isEqualTo(expectedCode);
    }
  }
}
