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

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.internal.NullSafeAccumulator.FieldAccess;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link NullSafeAccumulator}. */
@RunWith(JUnit4.class)
public final class NullSafeAccumulatorTest {

  @Test
  public void testNullSafeChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(CodeChunk.id("a"));
    assertThat(accum).generates("a;");
    assertThat(accum.dotAccess(FieldAccess.id("b"), true /* nullSafe */))
        .generates("a == null ? null : a.b;");
    assertThat(accum.bracketAccess(CodeChunk.id("c"), true /* nullSafe */))
        .generates("a == null ? null : a.b == null ? null : a.b[c];");
    assertThat(accum.dotAccess(FieldAccess.id("d"), true /* nullSafe */))
        .generates("a == null ? null : a.b == null ? null : a.b[c] == null ? null : a.b[c].d;");
    assertThat(accum.bracketAccess(CodeChunk.id("e"), true /* nullSafe */))
        .generates("a == null ? null : a.b == null ? null : a.b[c] == null ? null : a.b[c].d == null ? null : a.b[c].d[e];");
  }

  @Test
  public void testNonNullSafeChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(CodeChunk.id("a"));
    assertThat(accum)
        .generates("a;");
    assertThat(accum.bracketAccess(CodeChunk.id("b"), false /* nullSafe */))
        .generates("a[b];");
    assertThat(accum.dotAccess(FieldAccess.id("c"), false /* nullSafe */))
        .generates("a[b].c;");
    assertThat(accum.bracketAccess(CodeChunk.id("d"), false /* nullSafe */))
        .generates("a[b].c[d];");
    assertThat(accum.dotAccess(FieldAccess.id("e"), false /* nullSafe */))
        .generates("a[b].c[d].e;");
  }

  @Test
  public void testMixedChains() {
    NullSafeAccumulator accum = new NullSafeAccumulator(CodeChunk.id("a"));
    assertThat(accum).generates("a;");
    assertThat(accum.dotAccess(FieldAccess.id("b"), true /* nullSafe */))
        .generates("a == null ? null : a.b;");
    assertThat(accum.bracketAccess(CodeChunk.id("c"), false /* nullSafe */))
        .generates("a == null ? null : a.b[c];");
    assertThat(accum.dotAccess(FieldAccess.id("d"), true /* nullSafe */))
        .generates("a == null ? null : a.b[c] == null ? null : a.b[c].d;");
    assertThat(accum.bracketAccess(CodeChunk.id("e"), false /* nullSafe */))
        .generates("a == null ? null : a.b[c] == null ? null : a.b[c].d[e];");
  }

  @Test
  public void testCallPreservesChain() {
    NullSafeAccumulator accum = new NullSafeAccumulator(CodeChunk.id("a"));
    assertThat(accum.dotAccess(FieldAccess.call("b", CodeChunk.id("c")), false /* nullSafe */))
        .generates("a.b(c);");
    assertThat(accum.dotAccess(FieldAccess.call("d", CodeChunk.id("e")), true /* nullSafe */))
        .generates("a.b(c) == null ? null : a.b(c).d(e);");
  }

  private static final SubjectFactory<AccumulatorSubject, NullSafeAccumulator> FACTORY =
      new SubjectFactory<AccumulatorSubject, NullSafeAccumulator>() {
        @Override
        public AccumulatorSubject getSubject(FailureStrategy fs, NullSafeAccumulator that) {
          return new AccumulatorSubject(fs, that);
        }
      };

  private static AccumulatorSubject assertThat(NullSafeAccumulator accumulator) {
    return Truth.assertAbout(FACTORY).that(accumulator);
  }

  private static final class AccumulatorSubject
      extends Subject<AccumulatorSubject, NullSafeAccumulator> {

    AccumulatorSubject(FailureStrategy failureStrategy, NullSafeAccumulator actual) {
      super(failureStrategy, actual);
    }

    void generates(String expectedCode) {
      String actualCode =
          actual()
              .result(CodeChunk.Generator.create(JsSrcNameGenerators.forLocalVariables()))
              .getCode();
      if (!actualCode.equals(expectedCode)) {
        failWithBadResults("generates", expectedCode, "generates", actualCode);
      }
    }
  }
}
