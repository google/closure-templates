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

package com.google.template.soy.exprtree;

import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.StandardSubjectBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.testing.KvPair;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExprEquivalence}. */
@RunWith(JUnit4.class)
public final class ExprEquivalenceTest {

  @Test
  public void testReflexive() {
    runTest("{assertReflexive(1)}");
    runTest("{assertReflexive('a')}");
    runTest("{assertReflexive(true)}");
    runTest("{assertReflexive(false)}");
    runTest("{assertReflexive(1.2)}");
    runTest("{assertReflexive(['a', 1.2, true, KvPair()])}");
    runTest("{assertReflexive(map('a': 1.2, 'b': true, 'c': KvPair()))}");
    runTest("{@param map: map<string, string>}", "{assertReflexive($map)}");
    runTest("{@param map: map<string, string>}", "{assertReflexive($map['a'])}");
    runTest(
        "{@param legacy_map: legacy_object_map<string, string>}", "{assertReflexive($legacy_map)}");
    runTest(
        "{@param legacy_map: legacy_object_map<string, string>}",
        "{assertReflexive($legacy_map['a'])}");
    runTest("{@param list: list<list<string>>}", "{assertReflexive($list)}");
    runTest("{@param list: list<list<string>>}", "{assertReflexive($list[1])}");
    runTest("{@param list: list<list<string>>}", "{assertReflexive($list[1][4])}");
    runTest("{@param rec: [a: string, b: [a: string]]}", "{assertReflexive($rec)}");
    runTest("{@param rec: [a: string, b: [a: string]]}", "{assertReflexive($rec.a)}");
    runTest("{@param rec: [a: string, b: [a: string]]}", "{assertReflexive($rec.b)}");
    runTest("{@param rec: [a: string, b: [a: string]]}", "{assertReflexive($rec.b.a)}");
    runTest("{@param proto: KvPair}", "{assertReflexive($proto)}");
    runTest("{@param proto: KvPair}", "{assertReflexive($proto.key)}");
    runTest("{@param proto: KvPair}", "{assertReflexive($proto.value)}");
  }

  @Test
  public void testEquality() {
    // map, keys are in different order
    runTest("{assertEquals(map('a': 1.2, 'b': true), map('b': true, 'a': 1.2))}");
    // records are layed out differently
    runTest("{assertEquals(record(a: 1.2, b: true), record(b: true, a: 1.2))}");
    // order matters for lists
    runTest("{assertNotEquals(['a', 1.2, 'b', true], ['b', 1.2, 'a', true])}");
    // proto inits
    runTest(
        "{assertEquals(",
        "  KvPair(key: 'a', value: 'b'),",
        "  KvPair(value: 'b', key: 'a')",
        ")}");
    runTest(
        "{assertNotEquals(",
        "  KvPair(key: 'a', value: 'b'),",
        "  KvPair(key: 'b', value: 'b')",
        ")}");
    // TODO(b/78775420): randomInt isn't a pure function so it shouldn't ever be equivalent :/
    // fixing this behavior requires a cleanup.
    runTest("{assertEquals(randomInt(10), randomInt(10))}");
    runTest("{assertEquals(parseInt('10'), parseInt('10'))}");
    runTest("{assertNotEquals(parseInt('10'), parseInt('11'))}");
    // consider encoding commutivity and associativity into the rules
    runTest("{assertNotEquals(1 + 2, 2 + 1)}");
    runTest("{assertEquals(1 + 2, 1 + 2)}");
    runTest("{assertNotEquals(1 + 2, 1 - 2)}");
    runTest(
        "{assertNotEquals([$a for $a, $b in [1,2,3] if $a < 0], [$a for $a, $b in [1,2,3] if $a <"
            + " 0])}");
    runTest("{let $a: [$a for $a, $b in [1,2,3] if $a < 0] /}", "{assertEquals($a, $a)}");

    // null safe matters, though perhaps it shouldn't.  The two expressions will evaluate to the
    // same thing (because if it wouldn't then some kind of runtime error would occur).
    runTest("{@param rec: [a: string, b: [a: string]]}", "{assertNotEquals($rec.a, $rec?.a)}");
  }

  public void runTest(String... templateSourceLines) {
    runTestForNormal(templateSourceLines);
    runTestForXmbGen(templateSourceLines);
  }

  public void runTestForNormal(String... templateSourceLines) {
    runTestInternal(/* disableAllTypeChecking= */ false, templateSourceLines);
  }

  public void runTestForXmbGen(String... templateSourceLines) {
    // XMB generation runs with disableAllTypeChecking but depends heavily on ExprEquivalence.
    runTestInternal(/* disableAllTypeChecking= */ true, templateSourceLines);
  }

  public void runTestInternal(boolean disableAllTypeChecking, String... templateSourceLines) {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateAndImports(
                "{template .aaa}\n"
                    + "  "
                    + Joiner.on("\n   ").join(templateSourceLines)
                    + "\n"
                    + "{/template}\n",
                KvPair.getDescriptor())
            .addSoyFunction(ASSERT_REFLEXIVE_FUNCTION)
            .addSoyFunction(ASSERT_EQUALS_FUNCTION)
            .addSoyFunction(ASSERT_NOT_EQUALS_FUNCTION)
            .disableAllTypeChecking(disableAllTypeChecking)
            .parse()
            .fileSet();
    for (FunctionNode fn : SoyTreeUtils.getAllNodesOfType(soyTree, FunctionNode.class)) {
      if (fn.getFunctionName().equals(ASSERT_REFLEXIVE_FUNCTION.getName())) {
        assertEquivalent(
            fn.getSourceLocation(), fn.getChild(0), fn.getChild(0).copy(new CopyState()));
      } else if (fn.getFunctionName().equals(ASSERT_EQUALS_FUNCTION.getName())) {
        assertEquivalent(fn.getSourceLocation(), fn.getChild(0), fn.getChild(1));
      } else if (fn.getFunctionName().equals(ASSERT_NOT_EQUALS_FUNCTION.getName())) {
        assertNotEquivalent(fn.getSourceLocation(), fn.getChild(0), fn.getChild(1));
      }
    }
  }

  private static final SoyFunction ASSERT_REFLEXIVE_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "assertReflexive";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(1);
        }
      };
  private static final SoyFunction ASSERT_EQUALS_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "assertEquals";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(2);
        }
      };

  private static final SoyFunction ASSERT_NOT_EQUALS_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "assertNotEquals";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(2);
        }
      };

  private void assertEquivalent(SourceLocation location, ExprNode left, ExprNode right) {
    StandardSubjectBuilder assertion = assertWithMessage("assertion @ " + location);
    ExprEquivalence exprEquivalence = new ExprEquivalence();
    ExprEquivalence.Wrapper wrappedLeft = exprEquivalence.wrap(left);
    ExprEquivalence.Wrapper wrappedRight = exprEquivalence.wrap(right);

    assertion.that(wrappedLeft).isEqualTo(wrappedRight);
    // Test symmetry
    assertion.that(wrappedRight).isEqualTo(wrappedLeft);

    assertion.that(wrappedLeft.hashCode()).isEqualTo(wrappedRight.hashCode());
  }

  private static void assertNotEquivalent(SourceLocation location, ExprNode left, ExprNode right) {
    StandardSubjectBuilder assertion = assertWithMessage("assertion @ " + location);
    // test symmetry
    ExprEquivalence exprEquivalence = new ExprEquivalence();
    ExprEquivalence.Wrapper wrappedLeft = exprEquivalence.wrap(left);
    ExprEquivalence.Wrapper wrappedRight = exprEquivalence.wrap(right);
    assertion.that(wrappedRight).isNotEqualTo(wrappedLeft);
    assertion.that(wrappedLeft).isNotEqualTo(wrappedRight);
  }
}
