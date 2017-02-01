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

import javax.annotation.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.TableSwitchGenerator;

/**
 * A {@link MethodVisitor} that acts as a substitute for {@link GeneratorAdapter}.
 *
 * <p>{@link GeneratorAdapter} has lots of convenient methods (like {@link
 * GeneratorAdapter#push(int)}), however it is a subtype of {@link LocalVariablesSorter} which
 * automatically renumbers local variables. This is actually fine (i think), but the problem is that
 * it makes our debugging information (like {@link ClassData#toString()} or {@link
 * Expression#trace()}) very misleading since none of the local variable indices in the debug
 * information match the indexes in the generated code, so the debug data just looks wrong.
 *
 * <p>So instead we use forwarding to reuse the safe subset of the {@link GeneratorAdapter} api and
 * this allows us to skip past all the local variable munging.
 */
final class CodeBuilder extends MethodVisitor {
  private final GeneratorAdapter adapter;

  CodeBuilder(int access, Method method, MethodVisitor mv) {
    this(mv, access, method.getName(), method.getDescriptor());
  }

  CodeBuilder(int access, Method method, @Nullable Type[] exceptions, ClassVisitor cv) {
    this(
        access,
        method,
        cv.visitMethod(
            access,
            method.getName(),
            method.getDescriptor(),
            null /* generic signature */,
            getInternalNames(exceptions)));
  }

  CodeBuilder(MethodVisitor mv, int access, String name, String desc) {
    super(Opcodes.ASM5, mv);
    this.adapter = new GeneratorAdapter(mv, access, name, desc);
  }

  private static String[] getInternalNames(@Nullable Type[] types) {
    if (types == null) {
      return null;
    }
    String[] names = new String[types.length];
    for (int i = 0; i < names.length; ++i) {
      names[i] = types[i].getInternalName();
    }
    return names;
  }

  /** See {@link GeneratorAdapter#push(boolean)} */
  public void pushBoolean(boolean value) {
    adapter.push(value);
  }

  /** See {@link GeneratorAdapter#push(int)} */
  public void pushInt(int value) {
    adapter.push(value);
  }

  /** See {@link GeneratorAdapter#push(long)} */
  public void pushLong(long value) {
    adapter.push(value);
  }

  /** See {@link GeneratorAdapter#push(float)} */
  public void pushFloat(float value) {
    adapter.push(value);
  }

  /** See {@link GeneratorAdapter#push(double)} */
  public void pushDouble(double value) {
    adapter.push(value);
  }

  /** See {@link GeneratorAdapter#push(String)} */
  public void pushString(String value) {
    adapter.push(value);
  }

  /** See {@link GeneratorAdapter#push(Type)} */
  public void pushType(Type value) {
    adapter.push(value);
  }

  /** See {@link GeneratorAdapter#push(Type)} */
  public void pushNull() {
    adapter.visitInsn(Opcodes.ACONST_NULL);
  }

  /** See {@link GeneratorAdapter#loadThis()} */
  public void loadThis() {
    adapter.loadThis();
  }

  /** See {@link GeneratorAdapter#loadArgs()} */
  public void loadArgs() {
    adapter.loadArgs();
  }

  /** See {@link GeneratorAdapter#pop()} */
  public void pop() {
    adapter.pop();
  }

  /** See {@link GeneratorAdapter#pop2()} */
  public void pop2() {
    adapter.pop2();
  }

  /** See {@link GeneratorAdapter#dup()} */
  public void dup() {
    adapter.dup();
  }

  /** See {@link GeneratorAdapter#dupX1()} */
  public void dupX1() {
    adapter.dupX1();
  }

  /** See {@link GeneratorAdapter#dupX2()} */
  public void dupX2() {
    adapter.dupX2();
  }

  /** See {@link GeneratorAdapter#dup2()} */
  public void dup2() {
    adapter.dup2();
  }

  /** See {@link GeneratorAdapter#dup2X1()} */
  public void dup2X1() {
    adapter.dup2X1();
  }

  /** See {@link GeneratorAdapter#dup2X2()} */
  public void dup2X2() {
    adapter.dup2X2();
  }

  /** See {@link GeneratorAdapter#iinc(int, int)} */
  public void iinc(int local, int amount) {
    adapter.iinc(local, amount);
  }

  /** See {@link GeneratorAdapter#cast} */
  public void cast(Type from, Type to) {
    adapter.cast(from, to);
  }

  /** See {@link GeneratorAdapter#box(Type)} */
  public void box(Type type) {
    adapter.box(type);
  }

  /** See {@link GeneratorAdapter#valueOf(Type)} */
  public void valueOf(Type type) {
    adapter.valueOf(type);
  }

  /** See {@link GeneratorAdapter#unbox(Type)} */
  public void unbox(Type type) {
    adapter.unbox(type);
  }

  /** See {@link GeneratorAdapter#newLabel()} */
  public Label newLabel() {
    return adapter.newLabel();
  }

  /** See {@link GeneratorAdapter#mark(Label)} */
  public void mark(Label label) {
    adapter.mark(label);
  }

  /** See {@link GeneratorAdapter#mark()} */
  public Label mark() {
    return adapter.mark();
  }

  /** See {@link GeneratorAdapter#ifCmp} */
  public void ifCmp(Type type, int mode, Label label) {
    adapter.ifCmp(type, mode, label);
  }

  /** See {@link GeneratorAdapter#ifICmp(int, Label)} */
  public void ifICmp(int mode, Label label) {
    adapter.ifICmp(mode, label);
  }

  /** See {@link GeneratorAdapter#ifZCmp(int, Label)} */
  public void ifZCmp(int mode, Label label) {
    adapter.ifZCmp(mode, label);
  }

  /** See {@link GeneratorAdapter#ifNull(Label)} */
  public void ifNull(Label label) {
    adapter.ifNull(label);
  }

  /** See {@link GeneratorAdapter#ifNonNull(Label)} */
  public void ifNonNull(Label label) {
    adapter.ifNonNull(label);
  }

  /** See {@link GeneratorAdapter#goTo(Label)} */
  public void goTo(Label label) {
    adapter.goTo(label);
  }

  /** See {@link GeneratorAdapter#tableSwitch(int[], TableSwitchGenerator)} */
  public void tableSwitch(int[] keys, TableSwitchGenerator generator) {
    adapter.tableSwitch(keys, generator);
  }

  /** See {@link GeneratorAdapter#tableSwitch(int[], TableSwitchGenerator, boolean)} */
  public void tableSwitch(int[] keys, TableSwitchGenerator generator, boolean useTable) {
    adapter.tableSwitch(keys, generator, useTable);
  }

  /** See {@link GeneratorAdapter#returnValue()} */
  public void returnValue() {
    adapter.returnValue();
  }

  /** See {@link GeneratorAdapter#getStatic(Type, String, Type)} */
  public void getStatic(Type owner, String name, Type type) {
    adapter.getStatic(owner, name, type);
  }

  /** See {@link GeneratorAdapter#getField(Type, String, Type)} */
  public void getField(Type owner, String name, Type type) {
    adapter.getField(owner, name, type);
  }

  /** See {@link GeneratorAdapter#putField(Type, String, Type)} */
  public void putField(Type owner, String name, Type type) {
    adapter.putField(owner, name, type);
  }

  /** See {@link GeneratorAdapter#putStatic(Type, String, Type)} */
  public void putStatic(Type owner, String name, Type type) {
    adapter.putStatic(owner, name, type);
  }

  /** See {@link GeneratorAdapter#invokeVirtual(Type, Method)} */
  public void invokeVirtual(Type owner, Method method) {
    adapter.invokeVirtual(owner, method);
  }

  /** See {@link GeneratorAdapter#invokeConstructor(Type, Method)} */
  public void invokeConstructor(Type type, Method method) {
    adapter.invokeConstructor(type, method);
  }

  /** See {@link GeneratorAdapter#newInstance(Type)} */
  public void newInstance(Type type) {
    adapter.newInstance(type);
  }

  /** See {@link GeneratorAdapter#newArray(Type)} */
  public void newArray(Type type) {
    adapter.newArray(type);
  }

  /** See {@link GeneratorAdapter#arrayLength()} */
  public void arrayLength() {
    adapter.arrayLength();
  }

  /** See {@link GeneratorAdapter#throwException()} */
  public void throwException() {
    adapter.throwException();
  }

  /** See {@link GeneratorAdapter#throwException(Type, String)} */
  public void throwException(Type type, String msg) {
    adapter.throwException(type, msg);
  }

  /** See {@link GeneratorAdapter#checkCast(Type)} */
  public void checkCast(Type type) {
    adapter.checkCast(type);
  }

  /** See {@link GeneratorAdapter#endMethod()} */
  public void endMethod() {
    adapter.endMethod();
  }

  /** See {@link GeneratorAdapter#swap()} */
  public void swap() {
    adapter.swap();
  }
}
