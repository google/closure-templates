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

package com.google.template.soy.jbcsrc.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.FALSE;
import static com.google.template.soy.jbcsrc.restricted.SoyExpression.TRUE;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.testing.GcFinalization;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator.BooleanInvoker;
import java.io.IOException;
import java.lang.ref.WeakReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/** Tests for {@link com.google.template.soy.jbcsrc.internal.MemoryClassLoader} */
@RunWith(JUnit4.class)
public class MemoryClassLoaderTest {

  // Our memory classloaders should be garbage collectable when all references to their types
  // disapear
  @Test
  public void testCollectable() {
    BooleanInvoker invoker = ExpressionEvaluator.createInvoker(BooleanInvoker.class, FALSE);
    assertThat(invoker.invoke()).isFalse(); // sanity, the invoker works
    MemoryClassLoader loader = (MemoryClassLoader) invoker.getClass().getClassLoader();
    WeakReference<MemoryClassLoader> loaderRef = new WeakReference<>(loader);
    invoker = null; // unpin
    loader = null;
    GcFinalization.awaitClear(loaderRef);
  }

  @Test
  public void testAsResource() throws IOException {
    BooleanInvoker invoker = ExpressionEvaluator.createInvoker(BooleanInvoker.class, FALSE);
    byte[] classBytes =
        ByteStreams.toByteArray(
            invoker
                .getClass()
                .getClassLoader()
                .getResourceAsStream(invoker.getClass().getName().replace('.', '/') + ".class"));
    ClassNode node = new ClassNode();
    new ClassReader(classBytes).accept(node, 0);
    assertThat(node.name).isEqualTo(Type.getInternalName(invoker.getClass()));
  }

  @Test
  public void testDelegation() throws Exception {
    // We want to make sure that for generated types, our classloader doesn't delegate to its parent
    // loader
    ClassData parentClass = ExpressionEvaluator.createClass(BooleanInvoker.class, FALSE);
    ClassData childClass = ExpressionEvaluator.createClass(BooleanInvoker.class, TRUE);
    // sanity check,  they have the same fully qualified class name
    assertThat(parentClass.type()).isEqualTo(childClass.type());
    TypeInfo type = childClass.type();

    MemoryClassLoader parentLoader = new MemoryClassLoader(ImmutableList.of(parentClass));
    MemoryClassLoader childLoader =
        new MemoryClassLoader(parentLoader, ImmutableList.of(childClass));

    BooleanInvoker fromParent =
        parentLoader
            .loadClass(type.className())
            .asSubclass(BooleanInvoker.class)
            .getConstructor()
            .newInstance();
    assertThat(fromParent.invoke()).isFalse();

    BooleanInvoker fromChild =
        childLoader
            .loadClass(type.className())
            .asSubclass(BooleanInvoker.class)
            .getConstructor()
            .newInstance();
    assertThat(fromChild.invoke()).isTrue();

    assertThat(fromChild.getClass()).isNotEqualTo(fromParent.getClass());
  }
}
