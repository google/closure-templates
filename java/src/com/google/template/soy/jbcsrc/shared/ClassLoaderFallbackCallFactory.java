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

import static com.google.common.base.Preconditions.checkState;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ObjectArrays;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.internal.ParamStore;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.WeakReference;
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
  // A testing hook to force all bootstraps to take the slowpaths.
  private static final boolean FORCE_SLOWPATH =
      Boolean.getBoolean("soy_jbcsrc_take_classloader_fallback_slowpath");

  /**
   * A testonly marker interface that may be implemented by classloaders to force this class to
   * always select slowpaths.
   */
  @VisibleForTesting
  public interface AlwaysSlowPath {}

  /**
   * Put all the handles in an inner class since they are only referenced when slowpaths are
   * triggered which is rare.
   */
  private static final class SlowPathHandles {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static MethodHandle findLocalStaticOrDie(String name, MethodType type) {
      try {
        return LOOKUP.findStatic(ClassLoaderFallbackCallFactory.class, name, type);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
      }
    }

    private static final MethodHandle SLOWPATH_RENDER_RECORD =
        findLocalStaticOrDie(
            "slowPathRenderRecord",
            methodType(
                StackFrame.class,
                SoyCallSite.class,
                String.class,
                StackFrame.class,
                ParamStore.class,
                LoggingAdvisingAppendable.class,
                RenderContext.class));

    private static final MethodHandle SLOWPATH_RENDER_POSITIONAL =
        findLocalStaticOrDie(
            "slowPathRenderPositional",
            methodType(
                StackFrame.class,
                SoyCallSite.class,
                String.class,
                StackFrame.class,
                SoyValueProvider[].class,
                LoggingAdvisingAppendable.class,
                RenderContext.class));

    private static final MethodHandle SLOWPATH_TEMPLATE =
        findLocalStaticOrDie(
            "slowPathTemplate",
            methodType(
                CompiledTemplate.class, SoyCallSite.class, String.class, RenderContext.class));

    private static final MethodHandle SLOWPATH_TEMPLATE_VALUE =
        findLocalStaticOrDie(
            "slowPathTemplateValue",
            methodType(TemplateValue.class, SoyCallSite.class, String.class, RenderContext.class));
    private static final MethodHandle SLOWPATH_CONST =
        findLocalStaticOrDie(
            "slowPathConst",
            methodType(Object.class, SoyCallSite.class, String.class, RenderContext.class));
    private static final MethodHandle SLOWPATH_EXTERN =
        findLocalStaticOrDie(
            "slowPathExtern",
            methodType(
                Object.class,
                SoyCallSite.class,
                String.class,
                RenderContext.class,
                Object[].class));

    private static final MethodHandle IS_CACHE_VALID =
        findLocalStaticOrDie(
            "isCacheValid", methodType(boolean.class, int.class, RenderContext.class));

    private static final MethodType RENDER_TYPE =
        methodType(
            StackFrame.class,
            StackFrame.class,
            ParamStore.class,
            LoggingAdvisingAppendable.class,
            RenderContext.class);

    private static final MethodType TEMPLATE_ACCESSOR_TYPE = methodType(CompiledTemplate.class);
    private static final MethodType METHOD_HANDLE_TYPE = methodType(MethodHandle.class);

    private static final MethodHandle WEAK_REF_GET;

    static {
      try {
        WEAK_REF_GET = LOOKUP.findVirtual(WeakReference.class, "get", methodType(Object.class));
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
      }
    }

    private SlowPathHandles() {}
  }

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
    if (!FORCE_SLOWPATH) {
      Optional<Class<?>> templateClass = findTemplateClass(lookup, templateName);
      if (templateClass.isPresent()) {
        CompiledTemplate template = getTemplate(lookup, templateClass.get(), templateName);
        // the initial renderContext is ignored in this case
        MethodHandle getter = constant(CompiledTemplate.class, template);
        getter = dropArguments(getter, 0, RenderContext.class);
        return new ConstantCallSite(getter);
      }
    }
    MethodHandle slowPath = SlowPathHandles.SLOWPATH_TEMPLATE;
    slowPath = insertArguments(slowPath, 1, templateName);
    return new SoyCallSite(type, slowPath);
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
    if (!FORCE_SLOWPATH) {
      Optional<Class<?>> templateClass = findTemplateClass(lookup, templateName);
      if (templateClass.isPresent()) {
        CompiledTemplate template = getTemplate(lookup, templateClass.get(), templateName);
        TemplateValue value = TemplateValue.create(templateName, template);
        // the initial renderContext is ignored in this case
        MethodHandle getter = constant(TemplateValue.class, value);
        getter = dropArguments(getter, 0, RenderContext.class);
        return new ConstantCallSite(getter);
      }
    }
    MethodHandle slowPath = SlowPathHandles.SLOWPATH_TEMPLATE_VALUE;
    slowPath = insertArguments(slowPath, 1, templateName);
    return new SoyCallSite(type, slowPath);
  }

  private static CompiledTemplate getTemplate(
      MethodHandles.Lookup lookup, Class<?> templateClass, String templateName)
      throws NoSuchMethodException, IllegalAccessException {
    // Use the lookup to find and invoke the method.  This ensures that we can access the
    // factory using the permissions of the caller instead of the permissions of this class.
    // This is needed because template() methods for private templates are package private.
    String methodName = Names.renderMethodNameFromSoyTemplateName(templateName);

    MethodHandle templateAccessor =
        lookup.findStatic(templateClass, methodName, SlowPathHandles.TEMPLATE_ACCESSOR_TYPE);
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
   *     method it is either the main render signature or a positional signature
   * @param templateName A constant that is used for bootstrapping. this is the fully qualified soy
   *     template name of the template being referenced.
   */
  public static CallSite bootstrapCall(
      MethodHandles.Lookup lookup, String name, MethodType type, String templateName)
      throws IllegalAccessException, NoSuchMethodException {
    if (!FORCE_SLOWPATH) {
      Optional<Class<?>> templateClass = findTemplateClass(lookup, templateName);
      String methodName = Names.renderMethodNameFromSoyTemplateName(templateName);
      if (templateClass.isPresent()) {
        return new ConstantCallSite(lookup.findStatic(templateClass.get(), methodName, type));
      }
    }

    MethodHandle slowPathRenderHandle =
        type.equals(SlowPathHandles.RENDER_TYPE)
            ? SlowPathHandles.SLOWPATH_RENDER_RECORD
            : SlowPathHandles.SLOWPATH_RENDER_POSITIONAL;
    slowPathRenderHandle = insertArguments(slowPathRenderHandle, 1, templateName);
    if (!type.equals(SlowPathHandles.RENDER_TYPE)) {
      // target type has a signature like (SVP,SVP,...SVP,ParamStore,Appendable,RenderContext)
      // we want to collect the leading SVPs into an array at the end of the slowpath
      int numParams = type.parameterCount();
      int numPositionalParams =
          numParams - 3; // 4 for the CallSite,ij params, appenable and context

      // Turn slowPath from accepting a SoyValueProvider[] to a fixed number of
      // SoyValueProvider arguments at position 2
      slowPathRenderHandle =
          slowPathRenderHandle.asCollector(2, SoyValueProvider[].class, numPositionalParams);
    }
    return new SoyCallSite(type, slowPathRenderHandle);
  }

  /**
   * Returns a method handle matching the given type and name, where `type` has a leading
   * `RenderContext` parameter. If the actual method does not have a `RenderContext` parameter
   * return that instead.
   */
  static MethodHandle findStaticWithOrWithoutLeadingRenderContext(
      MethodHandles.Lookup lookup, Class<?> context, String name, MethodType type, boolean isConst)
      throws NoSuchMethodException, IllegalAccessException {
    checkState(type.parameterType(0).equals(RenderContext.class));
    try {
      MethodHandle withoutRenderContext =
          lookup.findStatic(context, name, type.dropParameterTypes(0, 1));
      if (isConst) {
        Object result;
        try {
          result = withoutRenderContext.invoke();
        } catch (Throwable t) {
          throw new AssertionError("const methods should not throw", t);
        }
        withoutRenderContext =
            MethodHandles.constant(withoutRenderContext.type().returnType(), result);
      }
      return MethodHandles.dropArguments(withoutRenderContext, 0, RenderContext.class);
    } catch (NoSuchMethodException nsme) {
      return lookup.findStatic(context, name, type);
    }
  }

  /**
   * A JVM bootstrap method for resolving references to constants..
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext)->CompiledTemplate.
   * @param constClassName The FQN of the class containing the constant.
   * @param constName The local name of the constant.
   */
  public static CallSite bootstrapConstLookup(
      MethodHandles.Lookup lookup,
      String name,
      MethodType type,
      String constClassName,
      String constName)
      throws Throwable {
    if (!FORCE_SLOWPATH) {
      ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
      try {
        Class<?> constClass = callerClassLoader.loadClass(constClassName);
        MethodHandle handle =
            findStaticWithOrWithoutLeadingRenderContext(
                lookup, constClass, constName, type, /* isConst= */ true);
        return new ConstantCallSite(handle);
      } catch (ClassNotFoundException classNotFoundException) {
        // Fall back to using the RenderContext class loader.
      }
    }

    MethodHandle slowPath = SlowPathHandles.SLOWPATH_CONST;
    slowPath =
        insertArguments(
            slowPath, 1, constClassName + '#' + constName + '#' + type.toMethodDescriptorString());
    return new SoyCallSite(
        // adapt the return type of the slowpath to the current constant type
        type, slowPath.asType(slowPath.type().changeReturnType(type.returnType())));
  }

  /**
   * A JVM bootstrap method for resolving references to extern functions.
   *
   * <p>This translates as a direct static call to the extern, if the callee class cannot be
   * resolved we redispatch through a SoyCallSite to allow a cross classloader call.
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext,...arguments)->return value according to the signature
   *     of the extern function
   * @param externName the name of the class that defines the extern
   * @param externName the name of the extern
   */
  public static CallSite bootstrapExternCall(
      MethodHandles.Lookup lookup,
      String name,
      MethodType type,
      String externClassName,
      String externName)
      throws NoSuchMethodException, IllegalAccessException {
    if (!FORCE_SLOWPATH) {
      ClassLoader callerClassLoader = lookup.lookupClass().getClassLoader();
      try {
        Class<?> externClass = callerClassLoader.loadClass(externClassName);
        MethodHandle handle =
            findStaticWithOrWithoutLeadingRenderContext(
                lookup, externClass, externName, type, /* isConst= */ false);
        return new ConstantCallSite(handle);
      } catch (ClassNotFoundException classNotFoundException) {
        // Fall back to using the RenderContext class loader.
      }
    }
    MethodHandle slowPath = SlowPathHandles.SLOWPATH_EXTERN;
    slowPath =
        insertArguments(
            slowPath,
            1,
            externClassName + '#' + externName + '#' + type.toMethodDescriptorString());
    // collect all arguments except the RenderContext into an array
    slowPath = slowPath.asCollector(Object[].class, type.parameterCount() - 1);
    return new SoyCallSite(type, slowPath.asType(type.insertParameterTypes(0, SoyCallSite.class)));
  }

  /**
   * The "slow path" for resolving constants. When the default class loader can't resolve the
   * constant's static method, the bootstrap method returns this one instead. It delegates to
   * RenderContext, which uses the CompiledTemplate's class loader to find the method.
   */
  public static Object slowPathConst(
      SoyCallSite callSite, String constantFqn, RenderContext context) throws Throwable {
    CompiledTemplates templates = context.getTemplates();
    MethodHandle constMethod = templates.getConstMethod(constantFqn);
    // Update the callsite so future calls dispatch directly to constMethod
    callSite.update(templates, constMethod);
    return constMethod.invoke(context);
  }

  /**
   * The "slow path" for resolving constants. When the default class loader can't resolve the
   * constant's static method, the bootstrap method returns this one instead. It delegates to
   * RenderContext, which uses the CompiledTemplate's class loader to find the method.
   */
  public static Object slowPathExtern(
      SoyCallSite callSite, String externFqn, RenderContext context, Object[] args)
      throws Throwable {
    CompiledTemplates templates = context.getTemplates();
    MethodHandle externMethod = templates.getExternMethod(externFqn);
    // Update the callsite so future calls dispatch directly to externMethod
    callSite.update(templates, externMethod);

    // use a slower dynamic call for the slowpath.  On subsequent calls the fastpath will be
    // selected.
    return externMethod.invokeWithArguments(ObjectArrays.concat(context, args));
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
      // which typically dispatch the call through RenderContext
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
  public static CompiledTemplate slowPathTemplate(
      SoyCallSite callSite, String templateName, RenderContext context) {
    CompiledTemplates templates = context.getTemplates();
    CompiledTemplate template = templates.getTemplate(templateName);
    callSite.updateWithConstant(templates, CompiledTemplate.class, template);
    return template;
  }

  /**
   * The slow path for resolving template values, this is called whenever the set of compiled
   * templates changes.
   */
  public static TemplateValue slowPathTemplateValue(
      SoyCallSite callSite, String templateName, RenderContext context) {
    CompiledTemplates templates = context.getTemplates();
    TemplateValue value = templates.getTemplateValue(templateName);
    callSite.updateWithConstant(templates, TemplateValue.class, value);
    return value;
  }

  /** The slow path for a call using positional call style. */
  public static StackFrame slowPathRenderPositional(
      SoyCallSite callSite,
      String templateName,
      StackFrame frame,
      SoyValueProvider[] params,
      LoggingAdvisingAppendable appendable,
      RenderContext context)
      throws Throwable {
    CompiledTemplates templates = context.getTemplates();
    MethodHandle renderMethod = templates.getPositionalRenderMethod(templateName, params.length);
    // Set the target so future calls will go here directly
    callSite.update(templates, renderMethod);
    // NOTE: we don't need to handle any state since if we are detaching on the next re-attach we
    // will call back into the same function directly
    Object[] args = new Object[params.length + 3];
    args[0] = frame;
    System.arraycopy(params, 0, args, 1, params.length);
    args[params.length + 1] = appendable;
    args[params.length + 2] = context;
    // This call style involves some boxing and should be equivalent to a reflective call in terms
    // of speed.
    return (StackFrame) renderMethod.invokeWithArguments(args);
  }

  /** The slow path for a call. */
  public static StackFrame slowPathRenderRecord(
      SoyCallSite callSite,
      String templateName,
      StackFrame frame,
      ParamStore params,
      LoggingAdvisingAppendable appendable,
      RenderContext context)
      throws Throwable {
    CompiledTemplates templates = context.getTemplates();
    MethodHandle renderMethod = templates.getRenderMethod(templateName);
    callSite.update(
        templates,
        // Set the target so future calls will go here directly
        renderMethod);
    // NOTE: we don't need to handle any state since if we are detaching on the next re-attach we
    // will call back into the same function directly
    return (StackFrame) renderMethod.invokeExact(frame, params, appendable, context);
  }

  /**
   * A mutable callsite that we can update whenever we observe that the CompiledTemplates changes.
   *
   * <p>If there are multiple versions of CompiledTemplates being used concurrently then there might
   * be some thrashing, however, the cost of that will just be some churn in methodhandle creation
   * which should be cheap.
   */
  private static final class SoyCallSite extends MutableCallSite {

    // The condition for the fast path
    private final MethodHandle test;
    private final MethodHandle slowPath;

    SoyCallSite(MethodType type, MethodHandle slowPath) {
      super(type);
      checkState(slowPath.type().parameterType(0).equals(SoyCallSite.class));
      slowPath = insertArguments(slowPath, 0, this);
      int renderContextIndex = slowPath.type().parameterList().indexOf(RenderContext.class);
      checkState(renderContextIndex != -1);
      this.slowPath = slowPath;
      // We want to adapt test to have the signature
      // (int,..type.args,RenderContext,...args)boolean so we add dummy arguments to match
      this.test =
          MethodHandles.dropArgumentsToMatch(
              SlowPathHandles.IS_CACHE_VALID, 1, type.parameterList(), renderContextIndex);
      // Always start calling directly to the slowpath
      setTarget(slowPath);
    }

    void update(CompiledTemplates newTemplates, MethodHandle newTarget) {
      // rebuild the target to use the newTarget, we guard calls on the templates pointer still
      // being correct.
      // the gencode looks like
      // newTemplates == context.getTemplates() ? newTarget(...) : slowPath(....)
      // However, because newTarget by definition points at symbols in another classloader, we use
      // weak references to preserve collectability.  This means that all instances of newTarget
      // should be strongly referenced by CompiledTemplates so that when it goes away so do our
      // weak references.
      newTarget =
          MethodHandles.foldArguments(
              MethodHandles.exactInvoker(newTarget.type()),
              SlowPathHandles.WEAK_REF_GET
                  .bindTo(new WeakReference<>(newTarget))
                  .asType(SlowPathHandles.METHOD_HANDLE_TYPE));

      this.setTarget(
          MethodHandles.guardWithTest(
              insertArguments(this.test, 0, newTemplates.getId()), newTarget, slowPath));
    }

    void updateWithConstant(CompiledTemplates newTemplates, Class<?> type, Object value) {
      var fastPathHandle =
          MethodHandles.dropArgumentsToMatch(
              SlowPathHandles.WEAK_REF_GET
                  .bindTo(new WeakReference<>(value))
                  .asType(methodType(type)),
              0,
              type().parameterList(),
              0);
      this.setTarget(
          MethodHandles.guardWithTest(
              insertArguments(this.test, 0, newTemplates.getId()), fastPathHandle, slowPath));
    }
  }

  public static boolean isCacheValid(int currentTemplatesId, RenderContext context) {
    return currentTemplatesId == context.getTemplates().getId();
  }
}
