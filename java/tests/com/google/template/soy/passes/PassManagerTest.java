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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.HashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PassManagerTest {

  private static class TestFirstPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode unusedSoyNode, IdGenerator unusedIdGenerator) {}
  }

  private static class TestSecondPass extends CompilerFilePass {
    @Override
    public void run(SoyFileNode unusedSoyNode, IdGenerator unusedIdGenerator) {}
  }

  private final ImmutableList<CompilerFilePass> compilerPasses =
      ImmutableList.of(new TestFirstPass(), new TestSecondPass());

  @Test
  public void testRemapPassContinuationRegistry_remapsStopAfter() throws Exception {
    HashMap<String, PassContinuationRule> passRules = Maps.newHashMap();
    passRules.put("TestFirst", PassContinuationRule.STOP_AFTER_PASS);

    ImmutableMap<String, PassContinuationRule> remappedRules =
        PassManager.remapPassContinuationRegistry(passRules, compilerPasses);
    assertThat(remappedRules).containsExactly("TestSecond", PassContinuationRule.STOP_BEFORE_PASS);
  }

  @Test
  public void testRemapPassContinuationRegistry_removesStopAfterLastPass() throws Exception {
    HashMap<String, PassContinuationRule> passRules = Maps.newHashMap();
    passRules.put("TestSecond", PassContinuationRule.STOP_AFTER_PASS);

    ImmutableMap<String, PassContinuationRule> remappedRules =
        PassManager.remapPassContinuationRegistry(passRules, compilerPasses);
    assertThat(remappedRules).isEmpty();
  }

  @Test
  public void testRemapPassContinuationRegistry_keepsStopBefore() throws Exception {
    HashMap<String, PassContinuationRule> passRules = Maps.newHashMap();
    passRules.put("TestSecond", PassContinuationRule.STOP_BEFORE_PASS);

    ImmutableMap<String, PassContinuationRule> remappedRules =
        PassManager.remapPassContinuationRegistry(passRules, compilerPasses);
    assertThat(remappedRules).containsExactly("TestSecond", PassContinuationRule.STOP_BEFORE_PASS);
  }

  @Test
  public void testRemapPassContinuationRegistry_removesContinuee() throws Exception {
    HashMap<String, PassContinuationRule> passRules = Maps.newHashMap();
    passRules.put("TestFirst", PassContinuationRule.CONTINUE);
    passRules.put("TestSecond", PassContinuationRule.STOP_BEFORE_PASS);

    ImmutableMap<String, PassContinuationRule> remappedRules =
        PassManager.remapPassContinuationRegistry(passRules, compilerPasses);
    assertThat(remappedRules).containsExactly("TestSecond", PassContinuationRule.STOP_BEFORE_PASS);
  }
}
