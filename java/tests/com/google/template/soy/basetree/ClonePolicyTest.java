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

package com.google.template.soy.basetree;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

import junit.framework.TestCase;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tests for how {@link Node} subtypes implement {@link Node#clone()}.
 * 
 * <p>{@link Node#clone()} specifies a policy for how {@code clone} is implemented.  This tries to
 * test for certain violations of that policy.
 */
public class ClonePolicyTest extends TestCase  {
  
  public void testClone() throws IOException, SecurityException, NoSuchMethodException {
    // We use top level classes to ignore node types defined as inner classes for tests.
    ImmutableSet<ClassInfo> topLevelClasses = 
        ClassPath.from(ClassLoader.getSystemClassLoader()).getTopLevelClasses();
    Set<String> errors = new LinkedHashSet<>();
    for (ClassInfo clazz : topLevelClasses) {
      if (clazz.getPackageName().startsWith("com.google.template.soy")) {
        Class<?> cls = clazz.load();
        if (Node.class.isAssignableFrom(cls)) {
          if (cls.isInterface()) {
            continue;
          }
          if (Modifier.isAbstract(cls.getModifiers())) {
            checkAbstractNode(cls, errors);
          } else {
            checkConcreteNode(cls, errors);
          }
        }
      }
    }
    if (!errors.isEmpty()) {
      fail("Clone policy failure:\n" + Joiner.on('\n').join(errors));
    }
  }

  private void checkConcreteNode(Class<?> node, Set<String> errors) 
      throws NoSuchMethodException, SecurityException {
    if (!Modifier.isFinal(node.getModifiers())) {
      errors.add("Non abstract Node types should be final. " + node.getName());
    }
    try {
      Constructor<?> copyConstructor = node.getDeclaredConstructor(node);
      if (!Modifier.isPrivate(copyConstructor.getModifiers())) {
        errors.add("Node class: "  + node.getName() 
            + " should have a private copy constructor. Found " + copyConstructor 
            + " with incompatible modifiers");
      }
    } catch (NoSuchMethodException e) {
      errors.add("Node class: "  + node.getName() + " should have a private copy constructor");
    }
    Method m = node.getDeclaredMethod("clone");
    // the method exists
    if (!Modifier.isPublic(m.getModifiers())) {
      errors.add("Node type: " + node.getName() + " has a clone() method that is not public");
    }
    if (m.getExceptionTypes().length != 0) {
      errors.add("node clone() methods should not throw checked exceptions: " + m);
    }
  }

  private void checkAbstractNode(Class<?> node, Set<String> errors) {
    // should have a protected copy constructor
    try {
      Constructor<?> copyConstructor = node.getDeclaredConstructor(node);
      if (!Modifier.isProtected(copyConstructor.getModifiers())) {
        errors.add("Node class: "  + node.getName() 
            + " should have a protected copy constructor. Found " + copyConstructor 
            + " with incompatible modifiers");  
      }
    } catch (NoSuchMethodException e) {
      errors.add("Node class: "  + node.getName() + " should have a protected copy constructor");
    }
    // if it has a clone method, it should be abstract
    try {
      Method m = node.getDeclaredMethod("clone");
      // the method exists
      if (!Modifier.isAbstract(m.getModifiers())) {
        errors.add("Abstract node type: " + node.getName() 
            + " has a clone() method that is not abstract");
      }
      if (m.getExceptionTypes().length != 0) {
        errors.add("node clone() methods should not throw checked exceptions: " + m);
      }
    } catch (NoSuchMethodException e) {
      // fine
    }
  }

}
