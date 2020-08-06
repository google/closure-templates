/*
 * Copyright 2020 Google Inc.
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

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.insertArguments;

import com.google.template.soy.data.SoyRecord;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bootstrap methods for handling template calls.
 *
 * <p>In order to support a flexible classloader set up we need to avoid generating direct bytecode
 * references from one soy template to another. Instead we defer the linkage decision until runtime
 * using {@code invokedynamic}.
 *
 * <p>See https://wiki.openjdk.java.net/display/HotSpot/Method+handles+and+invokedynamic for brutal
 * detail.
 *
 * <p>The main idea is that when the JVM first sees a given {@code invokedynamic} instruction it
 * calls the corresponding 'bootstrap method'. The method then returns a {@link CallSite} object
 * that tells the JVM how to actually invoke the method. The {@code CallSite} is primarily a wrapper
 * around a {@link MethodHandle} which is a flexible and JVM transparent object that can be used to
 * define the method to call.
 *
 * <p>In the case of Soy the dynamic behavior we want is relatively simple. If the target of the
 * {@code call} is defined by a different classloader than the callee, then dispatch to a slowpath
 * that goes through our {@link RenderContext} lookup class. Because this decision is deferred until
 * runtime we can ensure that the slowpath only occurs across classloader boundaries.
 *
 * <p>Other Ideas:
 *
 * <ul>
 *   <li>It might be possible to optimize {@code delcalls}, presumably many delcalls only have a
 *       single target or two possible targets, we could use MethodHandles.guardWithTest to allow
 *       inlining across such boundaries.
 *   <li>We could use a MutableCallsite for cross classloader calls and invalidate them when
 *       classloaders change. This would allow us to take advantage of JVM
 *       deoptimization/reoptimization.
 * </ul>
 */
public final class TemplateCallFactory {

  private static final MethodType SLOWPATH_TYPE =
      MethodType.methodType(
          CompiledTemplate.class,
          String.class,
          RenderContext.class,
          SoyRecord.class,
          SoyRecord.class);

  private static final MethodType SLOWPATH_FACTORY_TYPE =
      MethodType.methodType(CompiledTemplate.Factory.class, String.class, RenderContext.class);

  private static final MethodType NORMAL_CONSTRUCTOR =
      MethodType.methodType(void.class, SoyRecord.class, SoyRecord.class);

  private TemplateCallFactory() {}

  /**
   * A JVM bootstrap method for resolving {@code template(...)} references.
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext)->CompiledTemplate.Factory.
   * @param templateName A constant that is used for bootstrapping. this is the fully qualified soy
   *     template name of the template being referenced.
   */
  public static CallSite bootstrapFactoryLookup(
      MethodHandles.Lookup lookup, String name, MethodType type, String templateName) {
    ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    try {
      Class<?> factoryClass = callerClassLoader.loadClass(className + "$Factory");
      MethodHandle getter = lookup.findStaticGetter(factoryClass, "INSTANCE", factoryClass);
      // the initial renderContext is ignored in this case
      getter = dropArguments(getter, 0, RenderContext.class);
      // adapt the return value from the specific subtype to the generic CompiledTemplate.Factory
      getter = getter.asType(type);
      return new ConstantCallSite(getter);
    } catch (NoSuchFieldException | IllegalAccessException nsme) {
      throw new AssertionError(nsme);
    } catch (ClassNotFoundException classNotFoundException) {
      // expected if this happens we need to resolve by dispatching through rendercontext
    }
    try {
      MethodHandle handle =
          lookup.findStatic(TemplateCallFactory.class, "slowPathFactory", SLOWPATH_FACTORY_TYPE);
      handle = insertArguments(handle, 0, templateName);
      return new ConstantCallSite(handle);
    } catch (ReflectiveOperationException roe) {
      throw new AssertionError("impossible, can't find out slowPathFactory method", roe);
    }
  }

  /**
   * A JVM bootstrap method for resolving {@code call} commands.
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext)->CompiledTemplate.Factory.
   * @param templateName A constant that is used for bootstrapping. this is the fully qualified soy
   *     template name of the template being referenced.
   */
  public static CallSite bootstrapConstruction(
      MethodHandles.Lookup lookup, String name, MethodType type, String templateName) {
    ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    try {
      Class<?> templateClass = callerClassLoader.loadClass(className);
      MethodHandle handle = lookup.findConstructor(templateClass, NORMAL_CONSTRUCTOR);
      // the initial renderContext is ignored in this case
      handle = dropArguments(handle, 0, RenderContext.class);
      // adapt the return value from the specific subtype to the generic CompiledTemplate
      handle = handle.asType(type);
      return new ConstantCallSite(handle);
    } catch (NoSuchMethodException | IllegalAccessException nsme) {
      throw new AssertionError(nsme);
    } catch (ClassNotFoundException classNotFoundException) {
      // expected if this happens we need to resolve by dispatching through rendercontext
    }
    try {
      MethodHandle handle = lookup.findStatic(TemplateCallFactory.class, "slowPath", SLOWPATH_TYPE);
      handle = insertArguments(handle, 0, templateName);
      return new ConstantCallSite(handle);
    } catch (ReflectiveOperationException roe) {
      throw new AssertionError("impossible, can't find our slowPath method", roe);
    }
  }

  /** The slow path for resolving a factory. */
  public static CompiledTemplate.Factory slowPathFactory(
      String templateName, RenderContext context) {
    return context.getTemplateFactory(templateName);
  }

  /** The slow path for resolving a call. */
  public static CompiledTemplate slowPath(
      String templateName, RenderContext context, SoyRecord params, SoyRecord ijParams) {
    return context.getTemplateFactory(templateName).create(params, ijParams);
  }
}
