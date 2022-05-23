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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassVisitor;

/**
 * Tracks a collection of inner classes and aids in name management and calling {@link
 * ClassVisitor#visitInnerClass(String, String, String, int)} to ensure that they are registered
 * correctly.
 */
public final class InnerClasses {
  private final TypeInfo outer;
  private final Map<TypeInfo, ClassData> innerClasses = new LinkedHashMap<>();
  private final Map<TypeInfo, Integer> innerClassesAccessModifiers = new LinkedHashMap<>();
  private final UniqueNameGenerator classNames = JbcSrcNameGenerators.forClassNames();

  public InnerClasses(TypeInfo outer) {
    this.outer = outer;
  }

  /** Returns all the {@link ClassData} for every InnerClass registered. */
  public ImmutableList<ClassData> getInnerClassData() {
    return ImmutableList.copyOf(innerClasses.values());
  }

  /**
   * Register the given name as an inner class with the given access modifiers.
   *
   * @return A {@link TypeInfo} with the full class name
   */
  public TypeInfo registerInnerClass(String simpleName, int accessModifiers) {
    classNames.claimName(simpleName);
    TypeInfo innerClass = outer.innerClass(simpleName);
    innerClassesAccessModifiers.put(innerClass, accessModifiers);
    return innerClass;
  }

  /**
   * Register the name (or a simpl mangling of it) as an inner class with the given access
   * modifiers.
   *
   * @return A {@link TypeInfo} with the full (possibly mangled) class name
   */
  public TypeInfo registerInnerClassWithGeneratedName(String simpleName, int accessModifiers) {
    simpleName = classNames.generateName(simpleName);
    TypeInfo innerClass = outer.innerClass(simpleName);
    innerClassesAccessModifiers.put(innerClass, accessModifiers);
    return innerClass;
  }

  /**
   * Adds the data for an inner class.
   *
   * @throws java.lang.IllegalArgumentException if the class wasn't previous registered via {@link
   *     #registerInnerClass(String, int)} or {@link #registerInnerClassWithGeneratedName(String,
   *     int)}.
   */
  public void add(ClassData classData) {
    checkRegistered(classData.type());
    innerClasses.put(classData.type(), classData);
  }

  private void checkRegistered(TypeInfo type) {
    if (!classNames.hasName(type.simpleName())) {
      throw new IllegalArgumentException(type + " wasn't registered");
    }
  }

  /**
   * Registers this factory as an inner class on the given class writer.
   *
   * <p>Registering an inner class is confusing. The inner class needs to call this and so does the
   * outer class. Confirmed by running ASMIfier. Also, failure to call visitInnerClass on both
   * classes either breaks reflective apis (like class.getSimpleName()/getEnclosingClass), or causes
   * verifier errors (like IncompatibleClassChangeError).
   */
  public void registerAsInnerClass(ClassVisitor visitor, TypeInfo innerClass) {
    checkRegistered(innerClass);
    doRegister(visitor, innerClass);
  }

  /** Registers all inner classes to the given outer class. */
  public void registerAllInnerClasses(ClassVisitor visitor) {
    for (Map.Entry<TypeInfo, Integer> entry : innerClassesAccessModifiers.entrySet()) {
      TypeInfo innerClass = entry.getKey();
      doRegister(visitor, innerClass);
    }
  }

  private void doRegister(ClassVisitor visitor, TypeInfo innerClass) {
    visitor.visitInnerClass(
        innerClass.internalName(),
        outer.internalName(),
        innerClass.simpleName(),
        innerClassesAccessModifiers.get(innerClass));
  }
}
