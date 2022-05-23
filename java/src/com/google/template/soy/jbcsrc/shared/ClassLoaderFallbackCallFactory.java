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
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.annotations.VisibleForTesting;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.SoyVisualElement;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.logging.LoggableElementMetadata;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

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
  /**
   * A testonly marker interface that may be implemented by classloaders to force this class to
   * always select slowpaths.
   */
  @VisibleForTesting
  public interface AlwaysSlowPath {}

  private static final MethodType SLOWPATH_RENDER_TYPE =
      methodType(
          RenderResult.class,
          String.class,
          SoyRecord.class,
          SoyRecord.class,
          LoggingAdvisingAppendable.class,
          RenderContext.class);
  private static final MethodType POSITIONAL_TO_RECORD_TYPE =
      methodType(SoyRecord.class, String[].class, SoyValueProvider[].class);

  private static final MethodType SLOWPATH_TEMPLATE_TYPE =
      methodType(CompiledTemplate.class, String.class, RenderContext.class);

  private static final MethodType SLOWPATH_TEMPLATE_VALUE_TYPE =
      methodType(CompiledTemplate.TemplateValue.class, String.class, RenderContext.class);

  private static final MethodType RENDER_TYPE =
      methodType(
          RenderResult.class,
          SoyRecord.class,
          SoyRecord.class,
          LoggingAdvisingAppendable.class,
          RenderContext.class);

  private static final MethodType VE_METADATA_SLOW_PATH_TYPE =
      methodType(LoggableElementMetadata.class, String.class, RenderContext.class, long.class);

  private static final MethodType CREATE_VE_TYPE =
      methodType(SoyVisualElement.class, long.class, String.class, LoggableElementMetadata.class);

  private static final MethodType TEMPLATE_ACCESSOR_TYPE = methodType(CompiledTemplate.class);

  private ClassLoaderFallbackCallFactory() {}

  /**
   * A JVM bootstrap method for resolving references to templates in {@code {call ..}} commands.
   * This is used when streaming escaping directives cannot be applied.
   *
   * <p>This roughly generates code that looks like {@code <callee-class>.template()} where {@code
   * <callee-class>} is derived from the {@code templateName} parameter, additionally this supports
   * a fallback where the callee class cannot be found directly (because it is owned by a different
   * classloader).
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext)->CompiledTemplate.
   * @param templateName A constant that is used for bootstrapping. this is the fully qualified soy
   *     template name of the template being referenced.
   */
  public static CallSite bootstrapTemplateLookup(
      MethodHandles.Lookup lookup, String name, MethodType type, String templateName)
      throws NoSuchMethodException, IllegalAccessException {
    Optional<Class<?>> templateClass = findTemplateClass(lookup, templateName);
    if (templateClass.isPresent()) {
      CompiledTemplate template = getTemplate(lookup, templateClass.get(), templateName);
      // the initial renderContext is ignored in this case
      MethodHandle getter = constant(CompiledTemplate.class, template);
      getter = dropArguments(getter, 0, RenderContext.class);
      return new ConstantCallSite(getter);
    }
    MethodHandle handle =
        lookup.findStatic(
            ClassLoaderFallbackCallFactory.class, "slowpathTemplate", SLOWPATH_TEMPLATE_TYPE);
    handle = insertArguments(handle, 0, templateName);
    return new ConstantCallSite(handle);
  }

  /**
   * A JVM bootstrap method for resolving {@code template(...)} references.
   *
   * <p>This roughly generates code that looks like {@code
   * TemplateValue.create(<callee-class>.template())} where {@code <callee-class>} is derived from
   * the {@code templateName} parameter, additionally this supports a fallback where the callee
   * class cannot be found directly (because it is owned by a different classloader).
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext)->CompiledTemplate.TemplateValue.
   * @param templateName A constant that is used for bootstrapping. this is the fully qualified soy
   *     template name of the template being referenced.
   */
  public static CallSite bootstrapTemplateValueLookup(
      MethodHandles.Lookup lookup, String name, MethodType type, String templateName)
      throws NoSuchMethodException, IllegalAccessException {
    Optional<Class<?>> templateClass = findTemplateClass(lookup, templateName);
    if (templateClass.isPresent()) {
      CompiledTemplate template = getTemplate(lookup, templateClass.get(), templateName);
      CompiledTemplate.TemplateValue value =
          CompiledTemplate.TemplateValue.create(templateName, template);
      // the initial renderContext is ignored in this case
      MethodHandle getter = constant(CompiledTemplate.TemplateValue.class, value);
      getter = dropArguments(getter, 0, RenderContext.class);
      return new ConstantCallSite(getter);
    }
    MethodHandle handle =
        lookup.findStatic(
            ClassLoaderFallbackCallFactory.class,
            "slowPathTemplateValue",
            SLOWPATH_TEMPLATE_VALUE_TYPE);
    handle = insertArguments(handle, 0, templateName);
    return new ConstantCallSite(handle);
  }

  private static CompiledTemplate getTemplate(
      MethodHandles.Lookup lookup, Class<?> templateClass, String templateName)
      throws NoSuchMethodException, IllegalAccessException {
    // Use the lookup to find and invoke the method.  This ensures that we can access the
    // factory using the permissions of the caller instead of the permissions of this class.
    // This is needed because template() methods for private templates are package private.
    String methodName = Names.renderMethodNameFromSoyTemplateName(templateName);

    MethodHandle templateAccessor =
        lookup.findStatic(templateClass, methodName, TEMPLATE_ACCESSOR_TYPE);
    try {
      return (CompiledTemplate) templateAccessor.invokeExact();
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  /**
   * A JVM bootstrap method for resolving {@code call} commands.
   *
   * <p>This roughly generates code that looks like {@code <callee-class>.render(...)} where {@code
   * <callee-class>} is derived from the {@code templateName} parameter and {@code ...} corresponds
   * to the dynamically resolved parameters, additionally this supports a fallback where the callee
   * class cannot be found directly (because it is owned by a different classloader).
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext)->CompiledTemplate.
   * @param templateName A constant that is used for bootstrapping. this is the fully qualified soy
   *     template name of the template being referenced.
   */
  public static CallSite bootstrapCall(
      MethodHandles.Lookup lookup,
      String name,
      MethodType type,
      String templateName,
      String... paramNames)
      throws IllegalAccessException, NoSuchMethodException {
    Optional<Class<?>> templateClass = findTemplateClass(lookup, templateName);
    String methodName = Names.renderMethodNameFromSoyTemplateName(templateName);
    if (templateClass.isPresent()) {
      return new ConstantCallSite(lookup.findStatic(templateClass.get(), methodName, type));
    }
    MethodHandle slowPathRenderHandle =
        lookup.findStatic(
            ClassLoaderFallbackCallFactory.class, "slowPathRender", SLOWPATH_RENDER_TYPE);
    slowPathRenderHandle = insertArguments(slowPathRenderHandle, 0, templateName);

    // This means we must be being called using positional style.  To satisfy a cross classloader
    // call we will pack all arguments into a SoyRecord using the positionalToRecord helper and
    // then pass along to the slowpathRender
    // If we are interested in avoiding this overhead, we could take a number of approaches. For
    // example,
    // 1. we know in this case that the callee is using positional style, which means it is using
    // the adapter generated by the MapToPositionalCallFactory.  So we can rely on it calling
    // `getFieldProvider` in a particular order and create a SoyRecord that just returns values
    // from the set of parameters
    // 2. we could create a second method on CompileTemplate that accepts an Array/list

    // However, this is of course the 'slowPath' so it probably isn't very important.
    if (!type.equals(RENDER_TYPE)) {
      MethodHandle positionalToRecordHandle =
          lookup.findStatic(
              ClassLoaderFallbackCallFactory.class,
              "positionalToRecord",
              POSITIONAL_TO_RECORD_TYPE);
      positionalToRecordHandle = insertArguments(positionalToRecordHandle, 0, (Object) paramNames);
      // collect trailing arguments into an array
      positionalToRecordHandle =
          positionalToRecordHandle.asCollector(SoyValueProvider[].class, paramNames.length);
      slowPathRenderHandle =
          MethodHandles.collectArguments(slowPathRenderHandle, 0, positionalToRecordHandle);
    }
    return new ConstantCallSite(slowPathRenderHandle);
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
      MethodHandles.Lookup lookup, String name, MethodType type, String metadataClassName)
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle metadataGetter = getMetadataGetter(lookup, metadataClassName);
    MethodHandle createVe = lookup.findStatic(SoyVisualElement.class, "create", CREATE_VE_TYPE);
    // Pass the metadata (returned from metadataGetter) to createVe.
    MethodHandle handle = collectArguments(createVe, 2, metadataGetter);
    return new ConstantCallSite(handle);
  }

  private static MethodHandle getMetadataGetter(
      MethodHandles.Lookup lookup, String metadataClassName)
      throws NoSuchMethodException, IllegalAccessException {
    ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
    try {
      Class<?> metadataClass = callerClassLoader.loadClass(metadataClassName);
      MethodHandle handle =
          lookup.findStatic(
              metadataClass, "getMetadata", methodType(LoggableElementMetadata.class, long.class));
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

  private static Optional<Class<?>> findTemplateClass(
      MethodHandles.Lookup lookup, String templateName) throws NoSuchMethodException {
    ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
    String className = Names.javaClassNameFromSoyTemplateName(templateName);
    Class<?> clazz;
    try {
      clazz = callerClassLoader.loadClass(className);
    } catch (ClassNotFoundException classNotFoundException) {
      // expected, if this happens we need to resolve by falling back to one of our slowpaths
      // which typically disptach the call through RenderContext
      return Optional.empty();
    }
    // Test if we should send this class through the slowpath anyway
    if (clazz.getClassLoader() instanceof AlwaysSlowPath) {
      Method method =
          clazz.getDeclaredMethod(Names.renderMethodNameFromSoyTemplateName(templateName));
      // We can't take the slowpath for private templates.  Private templates are represented as
      // default access methods.
      if (Modifier.isPublic(method.getModifiers())) {
        return Optional.empty();
      }
    }
    return Optional.of(clazz);
  }

  /** The slow path for resolving template. */
  public static CompiledTemplate slowPathTemplate(String templateName, RenderContext context) {
    return context.getTemplate(templateName);
  }

  /** The slow path for resolving template values. */
  public static CompiledTemplate.TemplateValue slowPathTemplateValue(
      String templateName, RenderContext context) {
    return CompiledTemplate.TemplateValue.create(templateName, context.getTemplate(templateName));
  }

  private static class SaveRestoreState {
    static final MethodHandle saveStateMethodHandle;
    static final MethodHandle restoreTemplateHandle;

    static {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodType saveMethodType =
          methodType(void.class, RenderContext.class, CompiledTemplate.class);
      saveStateMethodHandle =
          SaveStateMetaFactory.bootstrapSaveState(lookup, "saveState", saveMethodType, 1)
              .getTarget();
      restoreTemplateHandle =
          SaveStateMetaFactory.bootstrapRestoreState(
                  lookup,
                  "restoreLocal",
                  methodType(CompiledTemplate.class, StackFrame.class),
                  saveMethodType,
                  0)
              .getTarget();
    }
  }

  /** Adapts a set of positional parameters to a SoyRecord */
  public static SoyRecord positionalToRecord(String[] names, SoyValueProvider[] values) {
    ParamStore paramStore = new ParamStore(names.length);
    for (int i = 0; i < names.length; i++) {
      if (values[i] != null) {
        paramStore.setField(names[i], values[i]);
      }
    }
    return paramStore;
  }

  /** The slow path for a call. */
  public static RenderResult slowPathRender(
      String templateName,
      SoyRecord params,
      SoyRecord ij,
      LoggingAdvisingAppendable appendable,
      RenderContext context)
      throws IOException {
    StackFrame frame = context.popFrame();
    CompiledTemplate template;
    switch (frame.stateNumber) {
      case 0:
        template = context.getTemplate(templateName);
        break;
      case 1:
        try {
          template = (CompiledTemplate) SaveRestoreState.restoreTemplateHandle.invokeExact(frame);
        } catch (Throwable t) {
          throw new AssertionError(t);
        }
        break;
      default:
        throw new AssertionError("unexpected state: " + frame);
    }
    RenderResult result = template.render(params, ij, appendable, context);
    if (!result.isDone()) {
      try {
        SaveRestoreState.saveStateMethodHandle.invokeExact(context, template);
      } catch (Throwable t) {
        throw new AssertionError(t);
      }
    }
    return result;
  }

  /** The slow path for resolving VE metadata. */
  public static LoggableElementMetadata veMetadataSlowPath(
      String metadataClassName, RenderContext renderContext, long veId) {
    return renderContext.getVeMetadata(metadataClassName, veId);
  }
}
