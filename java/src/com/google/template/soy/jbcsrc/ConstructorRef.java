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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * A reference to a type that can be constructed at runtime.
 */
@AutoValue abstract class ConstructorRef {
  private static ConstructorRef create(Class<?> clazz, Class<?> ...argTypes) {
    TypeInfo type = TypeInfo.create(clazz);
    Constructor<?> c;
    try {
      c = clazz.getConstructor(argTypes);
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
    Type constructorType = Type.getType(c);
    
    return new AutoValue_ConstructorRef(
        type, 
        Method.getMethod(c),
        ImmutableList.copyOf(constructorType.getArgumentTypes()));
  }

  static final ConstructorRef ARRAY_LIST_SIZE = create(ArrayList.class, int.class);
  static final ConstructorRef LINKED_HASH_MAP_SIZE = create(LinkedHashMap.class, int.class);

  abstract TypeInfo instanceClass();
  abstract Method method();
  abstract ImmutableList<Type> argTypes();

  /** 
   * Returns an expression that constructs a new instance of {@link #instanceClass()} by calling
   * this constructor.
   */
  Expression construct(final Expression ...args) {
    Expression.checkTypes(argTypes(), args);
    return new Expression() {
      @Override void doGen(GeneratorAdapter mv) {
        mv.newInstance(instanceClass().type());
        // push a second reference onto the stack so there is still a reference to the new object
        // after invoking the constructor (constructors are void methods)
        mv.dup();
        for (Expression arg : args) {
          arg.gen(mv);
        }
        mv.invokeConstructor(instanceClass().type(), method());
      }

      @Override Type resultType() {
        return instanceClass().type();
      }
    };
  }
}
