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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.TemplateValue;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata.DelTemplateMetadata;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** The result of template compilation. */
public class CompiledTemplates {
  /** Interface for classloaders that can provide additional per-class debugging information. */
  public interface DebuggingClassLoader {
    @Nullable
    String getDebugInfoForClass(String className);
  }

  private static final AtomicInteger idGenerator = new AtomicInteger(0);

  private static final MethodType RENDER_TYPE =
      methodType(
          RenderResult.class,
          ParamStore.class,
          LoggingAdvisingAppendable.class,
          RenderContext.class);

  private final ClassLoader loader;
  private final ConcurrentHashMap<String, TemplateData> templateNameToFactory =
      new ConcurrentHashMap<>();

  // A cache of resolved method handles for consts and externs.  This cache is not just an
  // optimization but also necessary to hold strong references to the MethodHandles so that our
  // generated callsites can use weak references.

  private final ConcurrentHashMap<String, MethodHandle> constOrExternNameToMethod =
      new ConcurrentHashMap<>();

  final DelTemplateSelector<TemplateData> selector;
  private final int id;

  /** Interface for constructor. */
  public interface Factory {
    CompiledTemplates create(ImmutableSet<String> delTemplateNames, ClassLoader loader);
  }

  /**
   * @param delTemplateNames The names of all the compiled deltemplates (the mangled names). This is
   *     needed to construct a valid deltemplate selector.
   * @param loader The classloader that contains the classes
   */
  public CompiledTemplates(ImmutableSet<String> delTemplateNames, ClassLoader loader) {
    this.id = idGenerator.incrementAndGet();
    this.loader = checkNotNull(loader);
    // We need to build the deltemplate selector eagerly.
    DelTemplateSelector.Builder<TemplateData> builder = new DelTemplateSelector.Builder<>();
    for (String delTemplateImplName : delTemplateNames) {
      TemplateData data = getTemplateData(delTemplateImplName);
      if (data.delTemplateName.isEmpty()) {
        throw new IllegalArgumentException(
            "Expected " + delTemplateImplName + " to be a deltemplate");
      }
      String delTemplateName = data.delTemplateName.get();
      if (data.modName.isPresent()) {
        String modName = data.modName.get();
        TemplateData prev = builder.add(delTemplateName, modName, data.variant, data);
        if (prev != null) {
          throw new IllegalArgumentException(
              String.format(
                  "Found multiple deltemplates with the same name (%s) and package (%s). %s and %s",
                  delTemplateName, modName, delTemplateImplName, prev.soyTemplateName()));
        }
      } else {
        TemplateData prev = builder.addDefault(delTemplateName, data.variant, data);
        if (prev != null) {
          throw new IllegalArgumentException(
              String.format(
                  "Found multiple default deltemplates with the same name (%s). %s and %s",
                  delTemplateName, delTemplateImplName, prev.soyTemplateName()));
        }
      }
    }
    this.selector = builder.build();
  }

  ClassLoader getClassLoader() {
    return loader;
  }

  int getId() {
    return id;
  }

  /** Returns a factory for the given fully qualified template name. */
  public CompiledTemplate getTemplate(String name) {
    return getTemplateData(name).template();
  }

  /** Returns a factory for the given fully qualified template name. */
  TemplateValue getTemplateValue(String name) {
    return getTemplateData(name).templateValue();
  }

  /** Returns a factory for the given fully qualified template name. */
  MethodHandle getRenderMethod(String name) {
    return getTemplateData(name).renderMethod();
  }

  /** Returns a factory for the given fully qualified template name. */
  MethodHandle getPositionalRenderMethod(String name, int arity) {
    return getTemplateData(name).positionalRenderMethod(arity);
  }

  /**
   * Returns the transitive closure of all the injected params that might be used by this template.
   */
  public ImmutableSortedSet<String> getTransitiveIjParamsForTemplate(String templateName) {
    TemplateData templateData = getTemplateData(templateName);
    ImmutableSortedSet<String> transitiveIjParams = templateData.transitiveIjParams;
    // racy-lazy init pattern.  We may calculate this more than once, but that is fine because each
    // time should calculate the same value.
    if (transitiveIjParams != null) {
      // early return, we already calculated this.
      return transitiveIjParams;
    }
    Set<TemplateData> all = new HashSet<>();
    collectTransitiveCallees(templateData, all);
    ImmutableSortedSet.Builder<String> ijs = ImmutableSortedSet.naturalOrder();
    for (TemplateData callee : all) {
      ijs.addAll(callee.injectedParams);
    }
    transitiveIjParams = ijs.build();
    // save the results
    templateData.transitiveIjParams = transitiveIjParams;
    return transitiveIjParams;
  }

  /** Returns the immediate css namespaces that might be used by this template. */
  public ImmutableList<String> getRequiredCssNamespaces(String templateName) {
    return getTemplateData(templateName).requiredCssNamespaces.asList();
  }

  /** Returns the immediate css namespaces that might be used by this template. */
  public ImmutableList<String> getRequiredCssPaths(String templateName) {
    return getTemplateData(templateName).requiredCssPaths.asList();
  }

  /**
   * Returns the transitive closure of all the css namespaces that might be used by this template.
   */
  public ImmutableList<String> getAllRequiredCssNamespaces(
      String templateName, Predicate<String> enabledMods, boolean collectCssFromDelvariants) {
    TemplateData templateData = getTemplateData(templateName);
    Set<TemplateData> orderedTemplateCalls = Sets.newLinkedHashSet();
    Set<TemplateData> visited = Sets.newLinkedHashSet();
    collectTransitiveCallees(
        templateData, orderedTemplateCalls, visited, enabledMods, collectCssFromDelvariants);
    LinkedHashSet<String> requiredNamespaces = Sets.newLinkedHashSet();
    for (TemplateData callee : orderedTemplateCalls) {
      requiredNamespaces.addAll(callee.requiredCssNamespaces);
    }
    return ImmutableList.copyOf(requiredNamespaces);
  }

  /**
   * Returns the transitive closure of all the css namespaces that might be used by this template.
   */
  public ImmutableList<String> getAllRequiredCssPaths(
      String templateName, Predicate<String> enabledMods, boolean collectCssFromDelvariants) {
    TemplateData templateData = getTemplateData(templateName);
    Set<TemplateData> orderedTemplateCalls = Sets.newLinkedHashSet();
    Set<TemplateData> visited = Sets.newLinkedHashSet();
    collectTransitiveCallees(
        templateData, orderedTemplateCalls, visited, enabledMods, collectCssFromDelvariants);
    LinkedHashSet<String> requiredPaths = Sets.newLinkedHashSet();
    for (TemplateData callee : orderedTemplateCalls) {
      requiredPaths.addAll(callee.requiredCssPaths);
    }
    return ImmutableList.copyOf(requiredPaths);
  }

  /** Returns an active delegate for the given name, variant and active package selector. */
  @Nullable
  CompiledTemplate selectDelTemplate(
      String delTemplateName, String variant, Predicate<String> activeModSelector) {
    TemplateData selectedTemplate =
        selector.selectTemplate(delTemplateName, variant, activeModSelector);
    if (selectedTemplate == null) {
      return null;
    }
    return selectedTemplate.template();
  }

  private static final Splitter HASH_SPLITTER = Splitter.on('#');

  /**
   * Fetches and caches a method handle for the given fully qualified method reference.
   *
   * <p>The format is `clasName#methodName#descriptor` this allows for a simple value that can be
   * cached and then unambiguously looked up.
   */
  MethodHandle getConstMethod(String fqn) {
    return constOrExternNameToMethod.computeIfAbsent(
        fqn, n -> findConstOrExternMethod(n, /* isConst= */ true));
  }

  /**
   * Fetches and caches a method handle for the given fully qualified method reference.
   *
   * <p>The format is `clasName#methodName#descriptor` this allows for a simple value that can be
   * cached and then unambiguously looked up.
   */
  MethodHandle getExternMethod(String fqn) {
    return constOrExternNameToMethod.computeIfAbsent(
        fqn, n -> findConstOrExternMethod(n, /* isConst= */ false));
  }

  private MethodHandle findConstOrExternMethod(String fqn, boolean isConst) {
    var parts = HASH_SPLITTER.split(fqn).iterator();
    var className = parts.next();
    var methodName = parts.next();
    var descriptor = parts.next();
    checkArgument(!parts.hasNext(), "Expected FQN with exactly 2 hash characters: %s", fqn);
    try {
      var ownerClass = Class.forName(className, /* initialize= */ true, getClassLoader());
      // parse the descriptor in the context of the callee
      var methodType =
          MethodType.fromMethodDescriptorString(descriptor, ownerClass.getClassLoader());

      return ClassLoaderFallbackCallFactory.findStaticWithOrWithoutLeadingRenderContext(
          MethodHandles.publicLookup().in(ownerClass), ownerClass, methodName, methodType, isConst);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("Could not link to " + fqn, e);
    }
  }

  public TemplateData getTemplateData(String name) {
    checkNotNull(name);
    TemplateData template = templateNameToFactory.get(name);
    if (template == null) {
      template = loadTemplate(name, loader);
      TemplateData old = templateNameToFactory.putIfAbsent(name, template);
      if (old != null) {
        return old;
      }
    }
    return template;
  }

  private static TemplateData loadTemplate(String name, ClassLoader loader) {
    Class<?> templateClass;
    String templateName = Names.javaClassNameFromSoyTemplateName(name);
    try {
      templateClass = Class.forName(templateName, /* initialize= */ true, loader);
    } catch (ClassNotFoundException e) {
      String format = "No class was compiled for template: %s.";
      throw new IllegalArgumentException(String.format(format, name), e);
    } catch (LinkageError e) {
      if (loader instanceof DebuggingClassLoader) {
        String debugInfo = ((DebuggingClassLoader) loader).getDebugInfoForClass(templateName);
        if (debugInfo != null) {
          throw new LinkageError(debugInfo, e);
        }
      }
      throw e;
    }
    return new TemplateData(templateClass, name);
  }

  /**
   * Adds all transitively called templates to {@code visited}. {@code templateData} may be null in
   * the case of a stubbed template.
   */
  private void collectTransitiveCallees(
      @Nullable TemplateData templateData, Set<TemplateData> visited) {
    if (templateData == null || visited.contains(templateData)) {
      return; // avoids chasing recursive cycles
    }
    visited.add(templateData);
    for (String callee : templateData.callees) {
      collectTransitiveCallees(getTemplateData(callee), visited);
    }
    for (String delCallee : templateData.delCallees) {
      // for {delcalls} and calls to modifiable templates we consider all possible targets
      for (TemplateData potentialCallee : selector.delTemplateNameToValues().get(delCallee)) {
        collectTransitiveCallees(potentialCallee, visited);
      }
    }
  }

  /**
   * Adds all transitively called templates to {@code visited}. {@code templateData} may be null in
   * the case of a deltemplate with no implementation or a stubbed template.
   */
  private void collectTransitiveCallees(
      @Nullable TemplateData templateData,
      Set<TemplateData> orderedTemplateCalls,
      Set<TemplateData> visited,
      Predicate<String> enabledMods,
      boolean collectCssFromDelvariants) {
    // templateData is null if a deltemplate has no implementation.
    if (templateData == null || visited.contains(templateData)) {
      return; // avoids chasing recursive cycles
    }
    visited.add(templateData);
    // TODO(tomnguyen) It may be important to collect css in lexical order instead of
    // separating templates and deltemplates.
    for (String callee : templateData.callees) {
      collectTransitiveCallees(
          getTemplateData(callee),
          orderedTemplateCalls,
          visited,
          enabledMods,
          collectCssFromDelvariants);
    }
    for (String delCallee : templateData.delCallees) {
      selector.delTemplateNameToValues().get(delCallee).stream()
          .map(tmpl -> tmpl.variant)
          .filter(variant -> collectCssFromDelvariants || variant.isEmpty())
          .distinct()
          .forEach(
              variant ->
                  collectTransitiveCallees(
                      selector.selectTemplate(delCallee, variant, enabledMods),
                      orderedTemplateCalls,
                      visited,
                      enabledMods,
                      collectCssFromDelvariants));
    }
    orderedTemplateCalls.add(templateData);
  }

  /** This is mostly a copy of the {@link TemplateMetadata} annotation. */
  public static final class TemplateData {
    final Method templateMethod;

    // These lazy caches are necessary for optimization and correctness.  The hard references stored
    // here are only weakly referenced by the callsites.  Thus they live or die with this object.
    // For that reason it is important to ensure that at most one of these is ever returned from
    // their accessor methods so that we maintain a hard reference to the values returned.
    // If for some reason we have contention on these locks, we can switch to a double-checked
    // locking protocol

    @GuardedBy("this")
    MethodHandle renderMethod;

    @GuardedBy("this")
    MethodHandle positionalRenderMethod;

    final String soyTemplateName;
    // lazily initialized since it is not always needed
    // NOTE: we do not need to guard this with the lock.  Even if multiple threads racily initialize
    // this value they will always calculate the same value because of how the method is
    // implemented.
    @LazyInit CompiledTemplate template;

    // lazily initialized since it is not always needed
    @GuardedBy("this")
    TemplateValue templateValue;

    // many of these fields should probably be only lazily calculated
    final ContentKind kind;
    final Optional<ImmutableList<String>> positionalParameters;
    final ImmutableSet<String> callees;
    final ImmutableSet<String> delCallees;
    final ImmutableSet<String> injectedParams;
    final ImmutableSet<String> requiredCssNamespaces;
    final ImmutableSet<String> requiredCssPaths;

    // If this is a deltemplate then delTemplateName will be present
    final Optional<String> delTemplateName;
    final Optional<String> modName;
    final String variant;

    // Lazily initialized by getTransitiveIjParamsForTemplate.  We initialize lazily because in
    // general this is only needed for relatively few templates.
    @LazyInit ImmutableSortedSet<String> transitiveIjParams;

    public TemplateData(Class<?> fileClass, String soyTemplateName) {
      this.templateMethod = getTemplateMethod(fileClass, soyTemplateName);
      this.soyTemplateName = soyTemplateName;

      // We pull the content kind off the TemplateMetadata eagerly since the parsing+reflection each
      // time is expensive.
      TemplateMetadata annotation = templateMethod.getAnnotation(TemplateMetadata.class);
      this.kind = annotation.contentKind();
      this.positionalParameters =
          annotation.hasPositionalSignature()
              ? Optional.of(ImmutableList.copyOf(annotation.positionalParams()))
              : Optional.empty();
      this.callees = ImmutableSet.copyOf(annotation.callees());
      this.delCallees = ImmutableSet.copyOf(annotation.delCallees());
      this.injectedParams = ImmutableSet.copyOf(annotation.injectedParams());
      this.requiredCssNamespaces = ImmutableSet.copyOf(annotation.requiredCssNames());
      this.requiredCssPaths = ImmutableSet.copyOf(annotation.requiredCssPaths());
      DelTemplateMetadata deltemplateMetadata = annotation.deltemplateMetadata();
      variant = deltemplateMetadata.variant();
      if (!deltemplateMetadata.name().isEmpty()) {
        delTemplateName = Optional.of(deltemplateMetadata.name());
        modName =
            deltemplateMetadata.modName().isEmpty()
                ? Optional.empty()
                : Optional.of(deltemplateMetadata.modName());
      } else {
        this.delTemplateName = Optional.empty();
        this.modName = Optional.empty();
      }
    }

    // A constructor just used by the stubbing implementation
    @VisibleForTesting
    public TemplateData(TemplateData copy, CompiledTemplate template) {
      this.template = template;
      // Infer some of the state from the source
      this.templateMethod = copy.templateMethod;
      this.soyTemplateName = copy.soyTemplateName;
      this.kind = copy.kind;
      this.positionalParameters = copy.positionalParameters;
      this.callees = ImmutableSet.of();
      this.delCallees = ImmutableSet.of();
      this.requiredCssNamespaces = ImmutableSet.of();
      this.requiredCssPaths = ImmutableSet.of();
      this.injectedParams = ImmutableSet.of();
      this.delTemplateName = Optional.empty();
      this.modName = Optional.empty();
      this.variant = "";

      // Pre-initialize our method handles based on the template
      this.renderMethod = HandlesForTesting.COMPILED_TEMPLATE_RENDER.bindTo(template);
      if (this.positionalParameters.isPresent()) {
        // Build a method handle that redirects positional style calls to the template object.
        var positionalParameters = this.positionalParameters.get();
        MethodHandle positionalRenderMethod = this.renderMethod;
        // Replace the initial SoyRecord argument with a call through positionalToRecord so the
        // signature becomes (SoyValueProvider[],SoyRecord,LoggingAdvisingAppendable,RenderContext)
        positionalRenderMethod =
            MethodHandles.filterArguments(
                positionalRenderMethod,
                0,
                MethodHandles.insertArguments(
                    HandlesForTesting.POSITIONAL_TO_RECORD,
                    0,
                    positionalParameters.stream()
                        .map(RecordProperty::get)
                        .collect(toImmutableList())));
        // Collect the positional parameters into an array in the first position to match the
        // positional signature.
        this.positionalRenderMethod =
            positionalRenderMethod.asCollector(
                0, SoyValueProvider[].class, positionalParameters.size());
      }
    }

    private static Method getTemplateMethod(Class<?> fileClass, String soyTemplateName) {
      String templateMethodName = Names.renderMethodNameFromSoyTemplateName(soyTemplateName);
      try {
        return fileClass.getDeclaredMethod(templateMethodName);
      } catch (NoSuchMethodException nsme) {
        // This may be caused by:
        //   1. Trying to call a private template. The factory() method is package private and so
        //      getMethod will fail.
        //   2. Two Soy files with the same namespace, without the necessary exemption. You should
        //      also see a build breakage related to go/java-one-version.
        throw new IllegalArgumentException(
            "cannot find the " + templateMethodName + "() method for " + soyTemplateName, nsme);
      }
    }

    synchronized MethodHandle renderMethod() {
      var renderMethod = this.renderMethod;
      if (renderMethod == null) {
        String templateMethodName = Names.renderMethodNameFromSoyTemplateName(soyTemplateName);
        try {
          renderMethod =
              MethodHandles.publicLookup()
                  .findStatic(
                      this.templateMethod.getDeclaringClass(), templateMethodName, RENDER_TYPE);
        } catch (ReflectiveOperationException e) {
          // This may be caused by:
          //   1. Trying to call a private template. The factory() method is package private and so
          //      getMethod will fail.
          //   2. Two Soy files with the same namespace, without the necessary exemption. You should
          //      also see a build breakage related to go/java-one-version.
          throw new IllegalArgumentException(
              "cannot find the "
                  + templateMethodName
                  + "(SoyRecord,SoyRecord,LoggingAdvisingAppendable,RenderContext) method for "
                  + soyTemplateName,
              e);
        }
        this.renderMethod = renderMethod;
      }
      return renderMethod;
    }

    synchronized MethodHandle positionalRenderMethod(int arity) {
      var positionalRenderMethod = this.positionalRenderMethod;
      if (positionalRenderMethod == null) {
        String templateMethodName = Names.renderMethodNameFromSoyTemplateName(soyTemplateName);
        Class<?>[] paramTypes = new Class<?>[arity + 2];
        Arrays.fill(paramTypes, 0, arity, SoyValueProvider.class);
        paramTypes[paramTypes.length - 2] = LoggingAdvisingAppendable.class;
        paramTypes[paramTypes.length - 1] = RenderContext.class;
        try {
          positionalRenderMethod =
              MethodHandles.publicLookup()
                  .findStatic(
                      this.templateMethod.getDeclaringClass(),
                      templateMethodName,
                      methodType(RenderResult.class, paramTypes));
        } catch (ReflectiveOperationException e) {
          // This may be caused by:
          //   1. Trying to call a private template. The factory() method is package private and so
          //      getMethod will fail.
          //   2. Two Soy files with the same namespace, without the necessary exemption. You should
          //      also see a build breakage related to go/java-one-version.
          //   3. A unsupported change in class signatures
          throw new IllegalArgumentException(
              "cannot find the "
                  + templateMethodName
                  + "("
                  + Arrays.toString(paramTypes)
                  + ") method for "
                  + soyTemplateName,
              e);
        }
        this.positionalRenderMethod = positionalRenderMethod;
      }
      return positionalRenderMethod;
    }

    @VisibleForTesting
    public Class<?> templateClass() {
      return templateMethod.getDeclaringClass();
    }

    @VisibleForTesting
    public Method templateMethod() {
      return templateMethod;
    }

    public ContentKind kind() {
      return kind;
    }

    @VisibleForTesting
    public boolean isPublicTemplate() {
      try {
        template();
        return false;
      } catch (IllegalArgumentException expected) {
        return false;
      }
    }

    synchronized TemplateValue templateValue() {
      TemplateValue local = templateValue;
      if (local == null) {
        this.templateValue = local = TemplateValue.create(this.soyTemplateName, template());
      }
      return local;
    }

    public synchronized CompiledTemplate template() {
      CompiledTemplate local = template;
      if (local == null) {
        try {
          local = (CompiledTemplate) templateMethod.invoke(null);
        } catch (IllegalAccessException iae) {
          throw new IllegalArgumentException(
              "cannot get a factory for the private template: " + soyTemplateName(), iae);
        } catch (ReflectiveOperationException e) {
          // this should be impossible since our factories are public with a default constructor.
          // TODO(lukes): failures of bytecode verification will propagate as Errors, we should
          // consider catching them here to add information about our generated types. (e.g. add
          // the
          // class trace and a pointer on how to file a soy bug)
          throw new LinkageError(e.getMessage(), e);
        }
        template = local;
      }
      return local;
    }

    String soyTemplateName() {
      return soyTemplateName;
    }
  }

  private static final class HandlesForTesting {
    private static final MethodHandle COMPILED_TEMPLATE_RENDER;
    private static final MethodHandle POSITIONAL_TO_RECORD;

    static {
      try {
        COMPILED_TEMPLATE_RENDER =
            MethodHandles.publicLookup().findVirtual(CompiledTemplate.class, "render", RENDER_TYPE);

        POSITIONAL_TO_RECORD =
            MethodHandles.lookup()
                .findStatic(
                    HandlesForTesting.class,
                    "positionalToRecord",
                    methodType(ParamStore.class, ImmutableList.class, SoyValueProvider[].class));
      } catch (ReflectiveOperationException e) {
        throw new LinkageError(e.getMessage(), e);
      }
    }

    /** Adapts a set of positional parameters to a SoyRecord */
    private static ParamStore positionalToRecord(
        ImmutableList<RecordProperty> names, SoyValueProvider[] values) {
      ParamStore paramStore = new ParamStore(names.size());
      for (int i = 0; i < names.size(); i++) {
        if (values[i] != UndefinedData.INSTANCE) {
          paramStore.setField(names.get(i), values[i]);
        }
      }
      return paramStore.freeze();
    }
  }
}
