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

import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.insertArguments;

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.logging.LoggableElementMetadata;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bootstrap methods for handling calls that should fallback to a ClassLoader if necessary.
 *
 * <p>In order to support a flexible classloader set up we need to avoid generating direct bytecode
 * references. Instead we defer the linkage decision until runtime using {@code invokedynamic}.
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
 * <p>In the case of Soy calls the dynamic behavior we want is relatively simple. If the target of
 * the {@code call} is defined by a different classloader than the callee, then dispatch to a
 * slowpath that goes through our {@link RenderContext} lookup class. Because this decision is
 * deferred until runtime we can ensure that the slowpath only occurs across classloader boundaries.
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
public final class ClassLoaderFallbackCallFactory {

  private static final MethodType SLOWPATH_TYPE =
      MethodType.methodType(
          CompiledTemplate.class,
          String.class,
          RenderContext.class,
          SoyRecord.class,
          SoyRecord.class);

  private static final MethodType SLOWPATH_FACTORY_VALUE_TYPE =
      MethodType.methodType(CompiledTemplate.FactoryValue.class, String.class, RenderContext.class);

  private static final MethodType NORMAL_CONSTRUCTOR =
      MethodType.methodType(void.class, SoyRecord.class, SoyRecord.class);

  private static final MethodType VE_METADATA_SLOW_PATH_TYPE =
      MethodType.methodType(
          LoggableElementMetadata.class, String.class, RenderContext.class, long.class);

  private static final MethodType CREATE_VE_TYPE =
      MethodType.methodType(
          SoyVisualElement.class, long.class, String.class, LoggableElementMetadata.class);

  private ClassLoaderFallbackCallFactory() {}

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
  public static CallSite bootstrapFactoryValueLookup(
      MethodHandles.Lookup lookup, String name, MethodType type, String templateName) {
    ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    try {
      Class<?> targetTemplateClass = callerClassLoader.loadClass(className);

      CompiledTemplate.Factory factory;
      try {
        // Use the lookup to find and invoke the method.  This ensures that we can access the
        // factory using the permissions of the caller instead of the permissions of this class.
        // This is needed because factory() methods for private templates are package private.
        factory =
            (CompiledTemplate.Factory)
                lookup
                    .findStatic(
                        targetTemplateClass,
                        "factory",
                        MethodType.methodType(CompiledTemplate.Factory.class))
                    .invokeExact();
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
      CompiledTemplate.FactoryValue value =
          CompiledTemplate.FactoryValue.create(templateName, factory);
      // the initial renderContext is ignored in this case
      MethodHandle getter = MethodHandles.constant(CompiledTemplate.FactoryValue.class, value);
      getter = dropArguments(getter, 0, RenderContext.class);
      return new ConstantCallSite(getter);
    } catch (ClassNotFoundException classNotFoundException) {
      // expected if this happens we need to resolve by dispatching through rendercontext
    }
    try {
      MethodHandle handle =
          lookup.findStatic(
              ClassLoaderFallbackCallFactory.class,
              "slowPathFactoryValue",
              SLOWPATH_FACTORY_VALUE_TYPE);
      handle = insertArguments(handle, 0, templateName);
      return new ConstantCallSite(handle);
    } catch (ReflectiveOperationException roe) {
      throw new AssertionError("impossible, can't find our slowPathFactoryValue method", roe);
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
      MethodHandle handle =
          lookup.findStatic(ClassLoaderFallbackCallFactory.class, "slowPath", SLOWPATH_TYPE);
      handle = insertArguments(handle, 0, templateName);
      return new ConstantCallSite(handle);
    } catch (ReflectiveOperationException roe) {
      throw new AssertionError("impossible, can't find our slowPath method", roe);
    }
  }

  /**
   * A JVM bootstrap method for creating VE objects with metadata.
   *
   * <p>Accesses the metadata static method directly if possible, otherwise dispatches through the
   * given ClassLoader (via RenderContext).
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (long, String, RenderContext)->SoyVisualElement.
   * @param metadataClassName A constant that is used for bootstrapping. This is the name of the
   *     class containing the VE metadata.
   */
  public static CallSite bootstrapVeWithMetadata(
      MethodHandles.Lookup lookup, String name, MethodType type, String metadataClassName) {
    try {
      MethodHandle metadataGetter = getMetadataGetter(lookup, metadataClassName);
      MethodHandle createVe = lookup.findStatic(SoyVisualElement.class, "create", CREATE_VE_TYPE);
      // Pass the metadata (returned from metadataGetter) to createVe.
      MethodHandle handle = collectArguments(createVe, 2, metadataGetter);
      return new ConstantCallSite(handle);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static MethodHandle getMetadataGetter(
      MethodHandles.Lookup lookup, String metadataClassName) throws ReflectiveOperationException {
    ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
    try {
      Class<?> metadataClass = callerClassLoader.loadClass(metadataClassName);
      MethodHandle handle =
          lookup.findStatic(
              metadataClass,
              "getMetadata",
              MethodType.methodType(LoggableElementMetadata.class, long.class));
      // the initial renderContext is ignored in this case
      return dropArguments(handle, 0, RenderContext.class);
    } catch (ClassNotFoundException classNotFoundException) {
      // expected, if this happens we need to resolve by dispatching through RenderContext
    }
    MethodHandle handle =
        lookup.findStatic(
            ClassLoaderFallbackCallFactory.class, "veMetadataSlowPath", VE_METADATA_SLOW_PATH_TYPE);
    return insertArguments(handle, 0, metadataClassName);
  }

  /** The slow path for resolving a factory value. */
  public static CompiledTemplate.FactoryValue slowPathFactoryValue(
      String templateName, RenderContext context) {
    return CompiledTemplate.FactoryValue.create(
        templateName, context.getTemplateFactory(templateName));
  }

  /** The slow path for resolving a call. */
  public static CompiledTemplate slowPath(
      String templateName, RenderContext context, SoyRecord params, SoyRecord ijParams) {
    return context.getTemplateFactory(templateName).create(params, ijParams);
  }

  /** The slow path for resolving VE metadata. */
  public static LoggableElementMetadata veMetadataSlowPath(
      String metadataClassName, RenderContext renderContext, long veId) {
    return renderContext.getVeMetadata(metadataClassName, veId);
  }
}
