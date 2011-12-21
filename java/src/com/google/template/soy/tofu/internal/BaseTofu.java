/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.tofu.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.internal.InsertMsgsVisitor;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.sharedpasses.MarkLocalVarDataRefsVisitor;
import com.google.template.soy.sharedpasses.RenameCssVisitor;
import com.google.template.soy.sharedpasses.opti.SimplifyVisitor;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofuException;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * Represents a compiled Soy file set. This is the result of compiling Soy to a Java object.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class BaseTofu implements SoyTofu {


  /**
   * Injectable factory for creating an instance of this class.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static interface BaseTofuFactory {

    /**
     * @param soyTree The Soy parse tree containing all the files in the Soy file set.
     * @param isCaching Whether this instance caches intermediate Soy trees after substitutions from
     *     the msgBundle and the cssRenamingMap.
     */
    public BaseTofu create(SoyFileSetNode soyTree, boolean isCaching);
  }


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** Factory for creating an instance of TofuRenderVisitor. */
  private final TofuRenderVisitorFactory tofuRenderVisitorFactory;

  /** The instanceof of SimplifyVisitor to use. */
  private final SimplifyVisitor simplifyVisitor;

  /** The Soy parse tree containing all the files in the Soy file set. */
  private final SoyFileSetNode soyTree;

  /** Whether this instance caches intermediate Soy trees after substitutions from the msgBundle
   *  and the cssRenamingMap. */
  private final boolean isCaching;

  /** Map of cached template registries. Only applicable when isCaching is true. */
  private final
  Map<Pair<SoyMsgBundle, SoyCssRenamingMap>, TemplateRegistry> cachedTemplateRegistries;

  /** The template registry used for no-caching mode of rendering. Applicable when isCaching is
   *  false or when isCaching is true but doAddToCache is false. */
  private final TemplateRegistry templateRegistryForNoCaching;


  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param tofuRenderVisitorFactory Factory for creating an instance of TofuRenderVisitor.
   * @param simplifyVisitor The instance of SimplifyVisitor to use.
   * @param soyTree The Soy parse tree containing all the files in the Soy file set.
   * @param isCaching Whether this instance caches intermediate Soy trees after substitutions from
   *     the msgBundle and the cssRenamingMap.
   */
  @AssistedInject
  public BaseTofu(
      @ApiCall GuiceSimpleScope apiCallScope, TofuRenderVisitorFactory tofuRenderVisitorFactory,
      SimplifyVisitor simplifyVisitor, @Assisted SoyFileSetNode soyTree,
      @Assisted boolean isCaching) {

    this.apiCallScope = apiCallScope;
    this.tofuRenderVisitorFactory = tofuRenderVisitorFactory;
    this.simplifyVisitor = simplifyVisitor;
    this.soyTree = soyTree;
    this.isCaching = isCaching;

    if (isCaching) {
      cachedTemplateRegistries = Maps.newHashMap();
      addToCache(null, null);
    } else {
      cachedTemplateRegistries = null;
    }
    templateRegistryForNoCaching = buildTemplateRegistry(soyTree.clone());
  }


  /**
   * {@inheritDoc}
   *
   * <p> For objects of this class, the namespace is always null.
   */
  @Override public String getNamespace() {
    return null;
  }


  @Override public SoyTofu forNamespace(@Nullable String namespace) {
    return (namespace == null) ? this : new NamespacedTofu(this, namespace);
  }


  @Override public boolean isCaching() {
    return isCaching;
  }


  @Override public void addToCache(
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap) {
    if (!isCaching) {
      throw new SoyTofuException("Cannot addToCache() when isCaching is false.");
    }

    apiCallScope.enter();
    try {
      ApiCallScopeUtils.seedSharedParams(
          apiCallScope, msgBundle, 0 /*use msgBundle locale's direction, ltr if null*/);
      getCachedTemplateRegistry(Pair.of(msgBundle, cssRenamingMap), true);
    } finally {
      apiCallScope.exit();
    }
  }


  @Override public Renderer newRenderer(SoyTemplateInfo templateInfo) {
    return new RendererImpl(this, templateInfo.getName());
  }


  @Override public Renderer newRenderer(String templateName) {
    return new RendererImpl(this, templateName);
  }


  /**
   * Builds a template registry for the given Soy tree.
   * @param soyTree The Soy tree to build a template registry for.
   * @return The newly built template registry.
   */
  private TemplateRegistry buildTemplateRegistry(SoyFileSetNode soyTree) {

    (new MarkParentNodesNeedingEnvFramesVisitor()).exec(soyTree);
    (new MarkLocalVarDataRefsVisitor()).exec(soyTree);
    return new TemplateRegistry(soyTree);
  }


  /**
   * Gets the template registry associated with the given key (a key is a pair of SoyMsgBundle and
   * SoyCssRenamingMap), optionally adding the mapping to the cache if it's not already there.
   *
   * <p> Specifically, if doAddToCache is true, then the mapping will be added to the cache if it's
   * not already there. Thus, after calling this method with doAddToCache set to true, the given key
   * is guaranteed to be found in the cache. On the other hand, if doAddToCache is false and the key
   * is not already in the cache, then this method simply returns null without modifying the cache.
   *
   * @param key The pair of SoyMsgBundle and SoyCssRenamingMap for which to retrieve the
   *     corresponding template registry.
   * @param doAddToCache Whether to add this combination to the cache in the case that it's not
   *     found in the cache.
   * @return The corresponding template registry, or null if not found in cache and doAddToCache is
   *     false.
   */
  private TemplateRegistry getCachedTemplateRegistry(
      Pair<SoyMsgBundle, SoyCssRenamingMap> key, boolean doAddToCache) {

    // This precondition check is for SimplifyVisitor, which we use below after making substitutions
    // from the SoyMsgBundle and SoyCssRenamingMap. While SimplifyVisitor will work correctly
    // outside of an active apiCallScope, always running it within the apiCallScope allows it to
    // potentially do more, such as apply bidi functions/directives that require bidiGlobalDir to be
    // in scope.
    Preconditions.checkState(apiCallScope.isActive());

    TemplateRegistry templateRegistry = cachedTemplateRegistries.get(key);
    if (templateRegistry == null) {
      if (!doAddToCache) {
        return null;
      }
      SoyFileSetNode soyTreeClone = soyTree.clone();
      (new InsertMsgsVisitor(key.first, true)).exec(soyTreeClone);
      (new RenameCssVisitor(key.second)).exec(soyTreeClone);
      simplifyVisitor.exec(soyTreeClone);
      templateRegistry = buildTemplateRegistry(soyTreeClone);
      cachedTemplateRegistries.put(key, templateRegistry);
    }
    return templateRegistry;
  }


  /**
   * Main entry point used by all of the API render() calls.
   *
   * @param templateName The full name of the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param ijData The injected data to call the template with. Can be null if not used.
   * @param activeDelPackageNames The set of active delegate package names, or null if none.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap Map for renaming selectors in 'css' tags, or null if not used.
   * @param doAddToCache Whether to add the current combination of msgBundle and cssRenamingMap to
   *     the cache if it's not already there. If set to false, then falls back to the no-caching
   *     mode of rendering when not found in cache. Only applicable if isCaching is true for this
   *     BaseTofu instance.
   * @return The rendered text.
   */
  private String renderMain(
      String templateName, @Nullable SoyMapData data, @Nullable SoyMapData ijData,
      @Nullable Set<String> activeDelPackageNames, @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyCssRenamingMap cssRenamingMap, boolean doAddToCache) {
    StringBuilder outputSb = new StringBuilder();
    renderMain(templateName, data, ijData, activeDelPackageNames, msgBundle, cssRenamingMap,
        doAddToCache, outputSb);
    return outputSb.toString();
  }


  /**
   * @param templateName The full name of the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param ijData The injected data to call the template with. Can be null if not used.
   * @param activeDelPackageNames The set of active delegate package names, or null if none.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap Map for renaming selectors in 'css' tags, or null if not used.
   * @param doAddToCache Whether to add the current combination of msgBundle and cssRenamingMap to
   *     the cache if it's not already there. If set to false, then falls back to the no-caching
   *     mode of rendering when not found in cache. Only applicable if isCaching is true for this
   *     BaseTofu instance.
   * @param outputSb The Appendable to write the output to.
   */
  private void renderMain(
      String templateName, @Nullable SoyMapData data, @Nullable SoyMapData ijData,
      @Nullable Set<String> activeDelPackageNames, @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyCssRenamingMap cssRenamingMap, boolean doAddToCache, Appendable outputSb) {

    if (activeDelPackageNames == null) {
      activeDelPackageNames = Collections.emptySet();
    }

    apiCallScope.enter();

    try {
      // Seed the scoped parameters.
      ApiCallScopeUtils.seedSharedParams(
          apiCallScope, msgBundle, 0 /*use msgBundle locale's direction, ltr if null*/);

      // Do the rendering.
      TemplateRegistry cachedTemplateRegistry = isCaching ?
          getCachedTemplateRegistry(Pair.of(msgBundle, cssRenamingMap), doAddToCache) : null;
      // Note: cachedTemplateRegistry may be null even when isCaching is true (specifically, if
      // doAddToCache is false).
      if (cachedTemplateRegistry != null) {
        // Note: Still need to pass msgBundle because we currently don't cache plural/select msgs.
        renderMainHelper(
            cachedTemplateRegistry, outputSb, templateName, data, ijData, activeDelPackageNames,
            msgBundle, null);
      } else {
        renderMainHelper(
            templateRegistryForNoCaching, outputSb, templateName, data, ijData,
            activeDelPackageNames, msgBundle, cssRenamingMap);
      }

    } finally {
      apiCallScope.exit();
    }
  }


  /**
   * Renders a template and appends the result to a StringBuilder.
   *
   * @param templateRegistry A registry of all templates.
   * @param outputSb The Appendable to append the rendered text to.
   * @param templateName The full name of the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param ijData The injected data to call the template with. Can be null if not used.
   * @param activeDelPackageNames The set of active delegate package names.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap Map for renaming selectors in 'css' tags, or null if not used.
   */
  private void renderMainHelper(
      TemplateRegistry templateRegistry, Appendable outputSb, String templateName,
      @Nullable SoyMapData data, @Nullable SoyMapData ijData, Set<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap) {

    TemplateNode template = templateRegistry.getBasicTemplate(templateName);
    if (template == null) {
      throw new SoyTofuException("Attempting to render undefined template '" + templateName + "'.");
    }

    try {
      RenderVisitor rv = tofuRenderVisitorFactory.create(
          outputSb, templateRegistry, data, ijData, null, activeDelPackageNames, msgBundle,
          cssRenamingMap);
      rv.exec(template);

    } catch (RenderException re) {
      throw new SoyTofuException(re);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Renderer implementation.


  /**
   * Simple implementation of the Renderer interface.
   */
  private static class RendererImpl implements Renderer {

    private final BaseTofu baseTofu;
    private final String templateName;
    private SoyMapData data;
    private SoyMapData ijData;
    private SoyMsgBundle msgBundle;
    private SoyCssRenamingMap cssRenamingMap;
    private Set<String> activeDelPackageNames;
    private boolean doAddToCache;

    /**
     * @param baseTofu The underlying BaseTofu object used to perform the rendering.
     * @param templateName The full template name (including namespace).
     */
    public RendererImpl(BaseTofu baseTofu, String templateName) {
      this.baseTofu = baseTofu;
      this.templateName = templateName;
      this.data = null;
      this.ijData = null;
      this.activeDelPackageNames = null;
      this.msgBundle = null;
      this.cssRenamingMap = null;
      this.doAddToCache = true;
    }

    @Override public Renderer setData(Map<String, ?> data) {
      this.data = (data == null) ? null : new SoyMapData(data);
      return this;
    }

    @Override public Renderer setData(SoyMapData data) {
      this.data = data;
      return this;
    }

    @Override public Renderer setIjData(Map<String, ?> ijData) {
      this.ijData = (ijData == null) ? null : new SoyMapData(ijData);
      return this;
    }

    @Override public Renderer setIjData(SoyMapData ijData) {
      this.ijData = ijData;
      return this;
    }

    @Override public Renderer setActiveDelegatePackageNames(
        Set<String> activeDelegatePackageNames) {
      this.activeDelPackageNames = activeDelegatePackageNames;
      return this;
    }

    @Override public Renderer setMsgBundle(SoyMsgBundle msgBundle) {
      this.msgBundle = msgBundle;
      return this;
    }

    @Override public Renderer setCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      this.cssRenamingMap = cssRenamingMap;
      return this;
    }

    @Override public Renderer setDontAddToCache(boolean dontAddToCache) {
      this.doAddToCache = !dontAddToCache;
      return this;
    }

    @Override public String render() {
      return baseTofu.renderMain(
          templateName, data, ijData, activeDelPackageNames, msgBundle, cssRenamingMap,
          doAddToCache);
    }

    @Override public void render(Appendable out) {
      baseTofu.renderMain(templateName, data, ijData, activeDelPackageNames, msgBundle,
          cssRenamingMap, doAddToCache, out);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Old render methods.


  @Deprecated
  @Override public String render(SoyTemplateInfo templateInfo, @Nullable Map<String, ?> data,
      @Nullable SoyMsgBundle msgBundle) {
    return renderMain(
        templateInfo.getName(), (data == null) ? null : new SoyMapData(data), null, null, msgBundle,
        null, true);
  }


  @Deprecated
  @Override public String render(SoyTemplateInfo templateInfo, @Nullable SoyMapData data,
      @Nullable SoyMsgBundle msgBundle) {
    return renderMain(templateInfo.getName(), data, null, null, msgBundle, null, true);
  }


  @Deprecated
  @Override public String render(String templateName, @Nullable Map<String, ?> data,
      @Nullable SoyMsgBundle msgBundle) {
    return renderMain(
        templateName, (data == null) ? null : new SoyMapData(data), null, null, msgBundle, null,
        true);
  }


  @Deprecated
  @Override public String render(String templateName, @Nullable SoyMapData data,
      @Nullable SoyMsgBundle msgBundle) {
    return renderMain(templateName, data, null, null, msgBundle, null, true);
  }

}
