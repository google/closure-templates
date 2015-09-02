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

import static com.google.template.soy.jbcsrc.SoyExpression.FALSE;

import com.google.common.io.ByteStreams;
import com.google.common.testing.GcFinalization;
import com.google.template.soy.jbcsrc.ExpressionTester.BooleanInvoker;

import junit.framework.TestCase;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Tests for {@link MemoryClassLoader}
 */
public class MemoryClassLoaderTest extends TestCase {

  // Our memory classloaders should be garbage collectable when all references to their types 
  // disapear
  public void testCollectable() {
    BooleanInvoker invoker = ExpressionTester.createInvoker(BooleanInvoker.class, FALSE);
    assertEquals(false, invoker.invoke());  // sanity, the invoker works
    MemoryClassLoader loader = (MemoryClassLoader) invoker.getClass().getClassLoader();
    WeakReference<MemoryClassLoader> loaderRef = new WeakReference<MemoryClassLoader>(loader);
    invoker = null;  // unpin
    loader = null;
    GcFinalization.awaitClear(loaderRef);
  }

  public void testAsResource() throws IOException {
    BooleanInvoker invoker = ExpressionTester.createInvoker(BooleanInvoker.class, FALSE);
    byte[] classBytes = ByteStreams.toByteArray(
        invoker.getClass().getClassLoader().getResourceAsStream(
            invoker.getClass().getName().replace('.', '/') + ".class"));
    ClassNode node = new ClassNode();
    new ClassReader(classBytes).accept(node, 0);
    assertEquals(Type.getInternalName(invoker.getClass()), node.name);
  }
}
