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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.plugin.java.restricted.JavaPluginContext;
import com.google.template.soy.plugin.java.restricted.JavaValue;
import com.google.template.soy.plugin.java.restricted.JavaValueFactory;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** statically analyzes {@link SoyJavaSourceFunction} plugins. */
public final class PluginAnalyzer {
  /** Simple metadata about the plugin. */
  @AutoValue
  public abstract static class PluginMetadata {
    static PluginMetadata create(
        boolean accessesContext,
        Iterable<String> pluginInstances,
        Iterable<MethodSignature> instanceMethodSignatures,
        Iterable<MethodSignature> staticMethodSignatures) {
      return new AutoValue_PluginAnalyzer_PluginMetadata(
          accessesContext,
          ImmutableSet.copyOf(pluginInstances),
          ImmutableSet.copyOf(instanceMethodSignatures),
          ImmutableSet.copyOf(staticMethodSignatures));
    }

    /** Whether or not this plugin depends on the {@link JavaPluginContext}. */
    public abstract boolean accessesContext();

    /** The set of plugin instances class names required for this plugin at runtime. */
    public abstract ImmutableSet<String> pluginInstanceNames();

    /** The set of non-null instance method signatures required by this plugin at runtime. */
    public abstract ImmutableSet<MethodSignature> instanceMethodSignatures();

    /** The set of non-null static method signatures required by this plugin at runtime. */
    public abstract ImmutableSet<MethodSignature> staticMethodSignatures();
  }

  private PluginAnalyzer() {}

  /**
   * Calls {@link SoyJavaSourceFunction#applyForJavaSource} with the arity of each supported
   * signature to collect the instance classes used by the function.
   */
  public static PluginMetadata analyze(SoyJavaSourceFunction ssf) {
    FinderFactory factory = new FinderFactory();
    FinderContext context = new FinderContext();
    SoyFunctionSignature fnSig = ssf.getClass().getAnnotation(SoyFunctionSignature.class);
    for (Signature sig : fnSig.value()) {
      List<JavaValue> args = Collections.nCopies(sig.parameterTypes().length, FinderValue.INSTANCE);
      ssf.applyForJavaSource(factory, args, context);
    }
    return PluginMetadata.create(
        context.accessed, factory.instances, factory.instanceMethodSigs, factory.staticMethodSigs);
  }

  /**
   * Calls {@link SoyJavaSourceFunction#applyForJavaSource} with the number of args requested and
   * returns the instances it used.
   */
  public static PluginMetadata analyze(SoyJavaSourceFunction ssf, int argCount) {
    FinderFactory factory = new FinderFactory();
    FinderContext context = new FinderContext();
    List<JavaValue> args = Collections.nCopies(argCount, FinderValue.INSTANCE);
    ssf.applyForJavaSource(factory, args, context);
    return PluginMetadata.create(
        context.accessed, factory.instances, factory.instanceMethodSigs, factory.staticMethodSigs);
  }

  private static class FinderFactory extends JavaValueFactory {
    Set<String> instances = new LinkedHashSet<>();
    Set<MethodSignature> instanceMethodSigs = new LinkedHashSet<>();
    Set<MethodSignature> staticMethodSigs = new LinkedHashSet<>();

    @Override
    public JavaValue callInstanceMethod(MethodSignature methodSig, JavaValue... params) {
      if (methodSig != null) {
        instances.add(methodSig.fullyQualifiedClassName());
        instanceMethodSigs.add(methodSig);
      }
      return FinderValue.INSTANCE;
    }

    @Override
    public FinderValue callInstanceMethod(Method method, JavaValue... params) {
      if (method != null) {
        instances.add(method.getDeclaringClass().getName());
      }
      return FinderValue.INSTANCE;
    }

    @Override
    public JavaValue callStaticMethod(MethodSignature methodSig, JavaValue... params) {
      if (methodSig != null) {
        staticMethodSigs.add(methodSig);
      }
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
    boolean accessed;

    @Override
    public JavaValue getBidiDir() {
      accessed = true;
      return FinderValue.INSTANCE;
    }

    @Override
    public JavaValue getULocale() {
      accessed = true;
      return FinderValue.INSTANCE;
    }

    @Override
    public JavaValue getAllRequiredCssNamespaces(JavaValue template) {
      accessed = true;
      return FinderValue.INSTANCE;
    }

    @Override
    public JavaValue getRenderedCssNamespaces() {
      accessed = true;
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
