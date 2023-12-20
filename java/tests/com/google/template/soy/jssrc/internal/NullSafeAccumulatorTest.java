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

import static com.google.template.soy.jssrc.dsl.Expressions.id;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.FormatOptions;
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
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(accum).generates("a;");
    assertThat(accum.dotAccess(FieldAccess.id("b"), /* nullSafe= */ true)).generates("a?.b;");
    assertThat(accum.bracketAccess(id("c"), /* nullSafe= */ true)).generates("a?.b?.[c];");
    assertThat(accum.dotAccess(FieldAccess.id("d"), /* nullSafe= */ true))
        .generates("a?.b?.[c]?.d;");
    assertThat(accum.bracketAccess(id("e"), /* nullSafe= */ true)).generates("a?.b?.[c]?.d?.[e];");
  }

  @Test
  public void testNonNullSafeChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(accum).generates("a;");
    assertThat(accum.bracketAccess(id("b"), /* nullSafe= */ false)).generates("a[b];");
    assertThat(accum.dotAccess(FieldAccess.id("c"), /* nullSafe= */ false)).generates("a[b].c;");
    assertThat(accum.bracketAccess(id("d"), /* nullSafe= */ false)).generates("a[b].c[d];");
    assertThat(accum.dotAccess(FieldAccess.id("e"), /* nullSafe= */ false))
        .generates("a[b].c[d].e;");
  }

  @Test
  public void testMixedChains() {
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(accum).generates("a;");
    assertThat(accum.dotAccess(FieldAccess.id("b"), /* nullSafe= */ true)).generates("a?.b;");
    assertThat(accum.bracketAccess(id("c"), /* nullSafe= */ false)).generates("a?.b[c];");
    assertThat(accum.dotAccess(FieldAccess.id("d"), /* nullSafe= */ true)).generates("a?.b[c]?.d;");
    assertThat(accum.bracketAccess(id("e"), /* nullSafe= */ false)).generates("a?.b[c]?.d[e];");
  }

  @Test
  public void testCallPreservesChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(
            accum.dotAccess(
                FieldAccess.call("b", ImmutableList.of(id("c"))), /* nullSafe= */ false))
        .generates("a.b(c);");
    assertThat(
            accum.dotAccess(FieldAccess.call("d", ImmutableList.of(id("e"))), /* nullSafe= */ true))
        .generates("a.b(c)?.d(e);");
  }

  @Test
  public void testMap() {
    FieldDescriptor desc = Foo.getDescriptor().findFieldByName("map_field");
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(accum.dotAccess(FieldAccess.protoCall("mapFieldMap", desc), /* nullSafe= */ false))
        .generates("a.getMapFieldMap();");
  }

  @Test
  public void testMapGet() {
    FieldDescriptor desc = Foo.getDescriptor().findFieldByName("map_field");
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(
            accum
                .dotAccess(FieldAccess.protoCall("mapFieldMap", desc), /* nullSafe= */ false)
                .mapGetAccess(id("key"), /* nullSafe= */ false))
        .generates("a.getMapFieldMap().get(key);");
  }

  @Test
  public void testNonNullMapGet() {
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(
            accum
                .dotAccess(FieldAccess.id("b"), /* nullSafe= */ true)
                .mapGetAccess(id("key"), /* nullSafe= */ false))
        .generates("a?.b.get(key);");
  }

  @Test
  public void testNonNullBracket() {
    NullSafeAccumulator accum = new NullSafeAccumulator(id("a"));
    assertThat(
            accum
                .dotAccess(FieldAccess.id("b"), /* nullSafe= */ true)
                .bracketAccess(id("c"), /* nullSafe= */ false))
        .generates("a?.b[c];");
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
              .getCode(FormatOptions.JSSRC);
      check("getCode()").that(actualCode).isEqualTo(expectedCode);
    }
  }
}
