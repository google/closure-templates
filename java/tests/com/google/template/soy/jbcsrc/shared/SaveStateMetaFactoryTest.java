/*
 * Copyright 2021 Google Inc.
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
package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SaveStateMetaFactoryTest {

  @Test
  public void testSaveConstantFrame() throws Throwable {
    StackFrame frame =
        (StackFrame) createSaveFrame(methodType(void.class, RenderContext.class), 20).invokeExact();
    assertThat(frame.getClass()).isEqualTo(StackFrame.class);
  }

  @Test
  public void testSaveFrameWithParameters() throws Throwable {
    StackFrame frame =
        (StackFrame)
            createSaveFrame(
                    methodType(
                        void.class, RenderContext.class, int.class, long.class, String.class),
                    20)
                .invokeExact(1, 5L, "foo");
    // make sure it is holding our data
    assertThat(getField(frame, 0).getInt(frame)).isEqualTo(1);
    assertThat(getField(frame, 1).getLong(frame)).isEqualTo(5L);
    assertThat(getField(frame, 2).get(frame)).isEqualTo("foo");

    // assert on the reflective shape.
    // This class should look exactly like
    // class StackFrameIJA extends StackFrame {
    //   public final int f_0;
    //   public final long f_1;
    //   public final Object f_2;
    //   public StackFrameIJA(int state, int f_0, long f_1, Objct f_2) {
    //     super(state);
    //     this.f_0 = f_0;
    //     this.f_1 = f_1;
    //     this.f_2 = f_2;
    //   }
    // }
    Class<?> frameClass = frame.getClass();
    assertThat(frameClass.getSimpleName()).isEqualTo("StackFrameIJA");
    assertThat(frameClass.getSuperclass()).isEqualTo(StackFrame.class);
    assertThat(frameClass.getDeclaredConstructors()).hasLength(1);
    Constructor<?> ctor = frameClass.getDeclaredConstructors()[0];
    assertThat(ctor.getParameterTypes())
        .isEqualTo(new Class<?>[] {int.class, int.class, long.class, Object.class});

    assertThat(frameClass.getDeclaredFields()).hasLength(3);

    Field f0 = frameClass.getField("f_0");
    assertThat(f0.getModifiers()).isEqualTo(Modifier.PUBLIC | Modifier.FINAL);
    assertThat(f0.getType()).isEqualTo(int.class);

    Field f1 = frameClass.getField("f_1");
    assertThat(f1.getModifiers()).isEqualTo(Modifier.PUBLIC | Modifier.FINAL);
    assertThat(f1.getType()).isEqualTo(long.class);

    Field f2 = frameClass.getField("f_2");
    assertThat(f2.getModifiers()).isEqualTo(Modifier.PUBLIC | Modifier.FINAL);
    assertThat(f2.getType()).isEqualTo(Object.class);
  }

  @Test
  public void testSaveFramesAreSharedAcrossSimilarSignatures() throws Throwable {
    StackFrame frame =
        (StackFrame)
            createSaveFrame(
                    methodType(void.class, RenderContext.class, String.class, int[].class), 20)
                .invokeExact("foo", new int[] {1, 2, 3});
    StackFrame frame2 =
        (StackFrame)
            createSaveFrame(
                    methodType(void.class, RenderContext.class, int[].class, String.class), 50)
                .invokeExact(new int[] {1, 2, 3}, "foo");

    // Thse frames have similar structures so they can share a class.
    assertThat(frame.getClass()).isSameInstanceAs(frame2.getClass());
  }

  @Test
  public void testSaveFramesArentSharedAcrossDisimalSignatures() throws Throwable {
    StackFrame frame =
        (StackFrame)
            createSaveFrame(
                    methodType(void.class, RenderContext.class, int.class, String.class), 20)
                .invokeExact(2, "foo");
    StackFrame frame2 =
        (StackFrame)
            createSaveFrame(
                    methodType(void.class, RenderContext.class, String.class, int.class), 50)
                .invokeExact("foo", 2);

    // Thse frames have different structures so they can't share a class.
    assertThat(frame.getClass()).isNotSameInstanceAs(frame2.getClass());
    assertThat(frame.getClass().getSimpleName()).isEqualTo("StackFrameIA");
    assertThat(frame2.getClass().getSimpleName()).isEqualTo("StackFrameAI");
  }

  @Test
  public void testRestoreState() throws Throwable {
    MethodType saveType =
        methodType(void.class, RenderContext.class, int.class, String.class, boolean.class);
    StackFrame frame = (StackFrame) createSaveFrame(saveType, 20).invokeExact(2, "foo", false);
    assertThat(frame.stateNumber).isEqualTo(20);
    assertThat(
            (int)
                createRestoreState(methodType(int.class, StackFrame.class), saveType, 0)
                    .invokeExact(frame))
        .isEqualTo(2);
    assertThat(
            (String)
                createRestoreState(methodType(String.class, StackFrame.class), saveType, 1)
                    .invokeExact(frame))
        .isEqualTo("foo");
    assertThat(
            (boolean)
                createRestoreState(methodType(boolean.class, StackFrame.class), saveType, 2)
                    .invokeExact(frame))
        .isFalse();
  }

  private MethodHandle createSaveFrame(MethodType type, int number) throws Exception {
    MethodHandle saveState =
        SaveStateMetaFactory.bootstrapSaveState(MethodHandles.lookup(), "save", type, number)
            .getTarget();
    RenderContext ctx = createContext();
    MethodHandle restoreState =
        MethodHandles.insertArguments(
            MethodHandles.lookup()
                .findVirtual(RenderContext.class, "popFrame", methodType(StackFrame.class)),
            0,
            ctx);

    return MethodHandles.collectArguments(
        restoreState, 0, MethodHandles.insertArguments(saveState, 0, ctx));
  }

  private MethodHandle createRestoreState(MethodType restoreType, MethodType saveType, int slot) {
    return SaveStateMetaFactory.bootstrapRestoreState(
            MethodHandles.lookup(), "restore", restoreType, saveType, slot)
        .getTarget();
  }

  private static Field getField(StackFrame frame, int num) throws Exception {
    return frame.getClass().getField("f_" + num);
  }

  private static RenderContext createContext() {
    return new RenderContext.Builder(
            new CompiledTemplates(
                ImmutableSet.of(), SaveStateMetaFactoryTest.class.getClassLoader()),
            ImmutableMap.of(),
            ImmutableMap.of())
        .withActiveDelPackageSelector(arg -> false)
        .withDebugSoyTemplateInfo(false)
        .build();
  }
}
