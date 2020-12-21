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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.jbcsrc.shared.TemplateMetadata.DelTemplateMetadata;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** The result of template compilation. */
public class CompiledTemplates {
  private final ClassLoader loader;
  private final ConcurrentHashMap<String, TemplateData> templateNameToFactory =
      new ConcurrentHashMap<>();
  private final DelTemplateSelector<TemplateData> selector;

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
    this.loader = checkNotNull(loader);
    // We need to build the deltemplate selector eagerly.
    DelTemplateSelector.Builder<TemplateData> builder = new DelTemplateSelector.Builder<>();
    for (String delTemplateImplName : delTemplateNames) {
      TemplateData data = getTemplateData(delTemplateImplName);
      if (!data.delTemplateName.isPresent()) {
        throw new IllegalArgumentException(
            "Expected " + delTemplateImplName + " to be a deltemplate");
      }
      String delTemplateName = data.delTemplateName.get();
      if (data.delPackage.isPresent()) {
        String delpackage = data.delPackage.get();
        TemplateData prev = builder.add(delTemplateName, delpackage, data.variant, data);
        if (prev != null) {
          throw new IllegalArgumentException(
              String.format(
                  "Found multiple deltemplates with the same name (%s) and package (%s). %s and %s",
                  delTemplateName, delpackage, delTemplateImplName, prev.soyTemplateName()));
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

  /** Returns a factory for the given fully qualified template name. */
  public CompiledTemplate.Factory getTemplateFactory(String name) {
    return getTemplateData(name).factory();
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

  /**
   * Returns the transitive closure of all the css namespaces that might be used by this template.
   */
  public ImmutableList<String> getAllRequiredCssNamespaces(
      String templateName,
      Predicate<String> enabledDelpackages,
      boolean collectCssFromDelvariants) {
    TemplateData templateData = getTemplateData(templateName);
    Set<TemplateData> orderedTemplateCalls = Sets.newLinkedHashSet();
    Set<TemplateData> visited = Sets.newLinkedHashSet();
    collectTransitiveCallees(
        templateData, orderedTemplateCalls, visited, enabledDelpackages, collectCssFromDelvariants);
    LinkedHashSet<String> requiredNamespaces = Sets.newLinkedHashSet();
    for (TemplateData callee : orderedTemplateCalls) {
      requiredNamespaces.addAll(callee.requiredCssNamespaces);
    }
    return ImmutableList.copyOf(requiredNamespaces);
  }

  /** Returns an active delegate for the given name, variant and active package selector. */
  @Nullable
  CompiledTemplate.Factory selectDelTemplate(
      String delTemplateName, String variant, Predicate<String> activeDelPackageSelector) {
    TemplateData selectedTemplate =
        selector.selectTemplate(delTemplateName, variant, activeDelPackageSelector);
    if (selectedTemplate == null) {
      return null;
    }
    return selectedTemplate.factory();
  }

  public TemplateData getTemplateData(String name) {
    checkNotNull(name);
    TemplateData template = templateNameToFactory.get(name);
    if (template == null) {
      template = loadFactory(name, loader);
      TemplateData old = templateNameToFactory.putIfAbsent(name, template);
      if (old != null) {
        return old;
      }
    }
    return template;
  }

  private static TemplateData loadFactory(String name, ClassLoader loader) {
    Class<? extends CompiledTemplate> templateClass;
    try {
      String templateName = Names.javaClassNameFromSoyTemplateName(name);
      templateClass =
          Class.forName(templateName, /* initialize= */ true, loader)
              .asSubclass(CompiledTemplate.class);
    } catch (ClassNotFoundException e) {
      String format = "No class was compiled for template: %s.";
      throw new IllegalArgumentException(String.format(format, name), e);
    }
    return new TemplateData(templateClass);
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
      // for {delcalls} we consider all possible targets
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
      Predicate<String> enabledDelpackages,
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
          enabledDelpackages,
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
                      selector.selectTemplate(delCallee, variant, enabledDelpackages),
                      orderedTemplateCalls,
                      visited,
                      enabledDelpackages,
                      collectCssFromDelvariants));
    }
    orderedTemplateCalls.add(templateData);
  }

  /** This is mostly a copy of the {@link TemplateMetadata} annotation. */
  @Immutable
  public static final class TemplateData {
    final Class<? extends CompiledTemplate> templateClass;
    // lazily initialized since it is not always needed
    @LazyInit CompiledTemplate.Factory factory;

    // many of these fields should probably be only lazily calculated
    final ContentKind kind;
    final ImmutableSet<String> callees;
    final ImmutableSet<String> delCallees;
    final ImmutableSet<String> injectedParams;
    final ImmutableSet<String> requiredCssNamespaces;

    // If this is a deltemplate then delTemplateName will be present
    final Optional<String> delTemplateName;
    final Optional<String> delPackage;
    final String variant;

    // Lazily initialized by getTransitiveIjParamsForTemplate.  We initialize lazily because in
    // general this is only needed for relatively few templates.
    @LazyInit ImmutableSortedSet<String> transitiveIjParams;

    public TemplateData(Class<? extends CompiledTemplate> template) {
      this.templateClass = template;
      // We pull the content kind off the templatemetadata eagerly since the parsing+reflection each
      // time is expensive.
      TemplateMetadata annotation = template.getAnnotation(TemplateMetadata.class);
      this.kind = annotation.contentKind();
      this.callees = ImmutableSet.copyOf(annotation.callees());
      this.delCallees = ImmutableSet.copyOf(annotation.delCallees());
      this.injectedParams = ImmutableSet.copyOf(annotation.injectedParams());
      this.requiredCssNamespaces = ImmutableSet.copyOf(annotation.requiredCssNames());
      DelTemplateMetadata deltemplateMetadata = annotation.deltemplateMetadata();
      variant = deltemplateMetadata.variant();
      if (!deltemplateMetadata.name().isEmpty()) {
        delTemplateName = Optional.of(deltemplateMetadata.name());
        delPackage =
            deltemplateMetadata.delPackage().isEmpty()
                ? Optional.empty()
                : Optional.of(deltemplateMetadata.delPackage());
      } else {
        this.delTemplateName = Optional.empty();
        this.delPackage = Optional.empty();
      }
    }

    @VisibleForTesting
    public Class<? extends CompiledTemplate> templateClass() {
      return templateClass;
    }

    public ContentKind kind() {
      return kind;
    }

    @VisibleForTesting
    public void setFactory(CompiledTemplate.Factory factory) {
      this.factory = factory;
    }

    public CompiledTemplate.Factory factory() {
      CompiledTemplate.Factory local = factory;
      if (local == null) {
        Method method;
        try {
          method = templateClass.getMethod("factory");
        } catch (NoSuchMethodException nsme) {
          // for private templates the factory() method is package private and so getMethod will
          // fail.
          throw new IllegalArgumentException(
              "cannot get a factory for the private template: " + soyTemplateName(), nsme);
        }
        try {
          local = (CompiledTemplate.Factory) method.invoke(null);
        } catch (ReflectiveOperationException e) {
          // this should be impossible since our factories are public with a default constructor.
          // TODO(lukes): failures of bytecode verification will propagate as Errors, we should
          // consider catching them here to add information about our generated types. (e.g. add
          // the
          // class trace and a pointer on how to file a soy bug)
          throw new AssertionError(e);
        }
        factory = local;
      }
      return local;
    }

    String soyTemplateName() {
      return Names.soyTemplateNameFromJavaClassName(templateClass.getName());
    }
  }
}
