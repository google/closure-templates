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

package com.google.template.soy.plugin.java.internal;

import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Finds the classes that {@link SoyJavaSourceFunction} plugins use as plugin instances. */
public final class PluginInstanceFinder {
  private PluginInstanceFinder() {}

  /**
   * Calls {@link SoyJavaSourceFunction#applyForJavaSource} with the arity of each supported
   * signature to collect the instance classes used by the function.
   */
  public static Set<Class<?>> find(SoyJavaSourceFunction ssf) {
    FinderFactory factory = new FinderFactory();
    SoyFunctionSignature fnSig = ssf.getClass().getAnnotation(SoyFunctionSignature.class);
    for (Signature sig : fnSig.value()) {
      List<JavaValue> args = Collections.nCopies(sig.parameterTypes().length, FinderValue.INSTANCE);
      ssf.applyForJavaSource(factory, args, FinderContext.INSTANCE);
    }
    return factory.instances;
  }

  /**
   * Calls {@link SoyJavaSourceFunction#applyForJavaSource} with the number of args requested and
   * returns the instances it used.
   */
  public static Set<Class<?>> find(SoyJavaSourceFunction ssf, int argCount) {
    FinderFactory factory = new FinderFactory();
    List<JavaValue> args = Collections.nCopies(argCount, FinderValue.INSTANCE);
    ssf.applyForJavaSource(factory, args, FinderContext.INSTANCE);
    return factory.instances;
  }

  private static class FinderFactory extends JavaValueFactory {
    Set<Class<?>> instances = new LinkedHashSet<>();

    @Override
    public FinderValue callInstanceMethod(Method method, JavaValue... params) {
      instances.add(method.getDeclaringClass());
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue callStaticMethod(Method method, JavaValue... params) {
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue listOf(List<JavaValue> args) {
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue constant(boolean value) {
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue constant(double value) {
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue constant(long value) {
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue constant(String value) {
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue constantNull() {
      return FinderValue.INSTANCE;
    }
  }

  private static final class FinderContext implements JavaPluginContext {
    static final FinderContext INSTANCE = new FinderContext();

    @Override
    public JavaValue getBidiDir() {
      return FinderValue.INSTANCE;
    }

    @Override
    public JavaValue getULocale() {
      return FinderValue.INSTANCE;
    }
  }

  private static final class FinderValue implements JavaValue {
    static final FinderValue INSTANCE = new FinderValue();

    @Override
    public JavaValue isNonNull() {
      return this;
    }

    @Override
    public JavaValue isNull() {
      return this;
    }

    @Override
    public JavaValue asSoyBoolean() {
      return this;
    }

    @Override
    public JavaValue asSoyString() {
      return this;
    }

    @Override
    public JavaValue asSoyInt() {
      return this;
    }

    @Override
    public JavaValue asSoyFloat() {
      return this;
    }

    @Override
    public JavaValue coerceToSoyBoolean() {
      return this;
    }

    @Override
    public JavaValue coerceToSoyString() {
      return this;
    }
  }
}
