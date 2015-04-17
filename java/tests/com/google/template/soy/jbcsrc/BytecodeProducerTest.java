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

package com.google.template.soy.jbcsrc;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

/**
 * Tests for {@link BytecodeProducer}
 */
public class BytecodeProducerTest extends TestCase {

  public void testGenDoesntOverlapWithCompile() {
    BytecodeProducer producer = new BytecodeProducer() {
      @Override void doGen(CodeBuilder adapter) {
        BytecodeUtils.constant('c').gen(adapter);
      }
    };
    try {
      CodeBuilder adaterAdapter = 
          new CodeBuilder(Opcodes.ACC_PUBLIC, BytecodeUtils.NULLARY_INIT, new MethodNode());
      producer.gen(adaterAdapter);
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage(
          "All bytecode producers should be created prior to code generation beginning.");
    }
  }
}
