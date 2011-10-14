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

package com.google.template.soy.tofu;

import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.shared.SoyCssRenamingMap;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;


/**
 * SoyTofu is the public interface for a Java object that represents a compiled Soy file set.
 *
 * @author Kai Huang
 */
public interface SoyTofu {


  /**
   * Gets the namespace of this SoyTofu object. The namespace is simply a convenience allowing
   * {@code newRenderer()} to be called with a partial template name (e.g. ".fooTemplate").
   * Note: The namespace may be null, in which case {@code newRenderer()} must be called with the
   * full template name.
   *
   * @return The namespace of this SoyTofu object, or null if no namespace.
   */
  public String getNamespace();


  /**
   * Gets a new SoyTofu instance with a different namespace (or no namespace).
   * Note: The new SoyTofu instance will still be backed by the same compiled Soy file set.
   *
   * @param namespace The namespace for the new SoyTofu instance, or null for no namespace.
   * @return A new SoyTofu instance with a different namespace (or no namespace).
   */
  public SoyTofu forNamespace(@Nullable String namespace);


  /**
   * Getter for whether this instance caches intermediate Soy trees after substitutions from the
   * SoyMsgBundle and the SoyCssRenamingMap.
   *
   * @return Whether this instance caches intermediate Soy trees after substitutions from the
   *     SoyMsgBundle and the SoyCssRenamingMap.
   */
  public boolean isCaching();


  /**
   * Primes the cache with the given combination of SoyMsgBundle and SoyCssRenamingMap. Priming the
   * cache will eliminate the slowness for the first render. This method must be called separately
   * for each distinct combination of SoyMsgBundle and SoyCssRenamingMap for which you wish to prime
   * the cache.
   *
   * Only applicable when {@code isCaching()} is true.
   *
   * @param msgBundle The message bundle to prime the cache with.
   * @param cssRenamingMap The CSS renaming map to prime the cache with.
   */
  public void addToCache(
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap);


  /**
   * Gets a new Renderer for a template.
   *
   * <p> The usage pattern is
   *   soyTofu.newRenderer(...).setData(...).setInjectedData(...).setMsgBundle(...).render()
   * where any of the set* parts can be omitted if it's null.
   *
   * @param templateInfo Info for the template to render.
   * @return A new renderer for the given template.
   */
  public Renderer newRenderer(SoyTemplateInfo templateInfo);


  /**
   * Gets a new Renderer for a template.
   *
   * <p> The usage pattern is
   *   soyTofu.newRenderer(...).setData(...).setInjectedData(...).setMsgBundle(...).render()
   * where any of the set* parts can be omitted if it's null.
   *
   * @param templateName The name of the template to render. If this SoyTofu instance is not
   *     namespaced, then this parameter should be the full name of the template including the
   *     namespace. If this SoyTofu instance is namespaced, then this parameter should be a partial
   *     name beginning with a dot (e.g. ".fooTemplate").
   * @return A new renderer for the given template.
   */
  public Renderer newRenderer(String templateName);


  // -----------------------------------------------------------------------------------------------
  // Renderer interface.


  /**
   * Renderer for a template.
   */
  public static interface Renderer {

    /**
     * Sets the data to call the template with. Can be null if the template has no parameters.
     *
     * <p> Note: If you call this method instead of {@link #setData(SoyMapData)}, your template data
     * will be converted to a {@code SoyMapData} object on each call. This may not be a big deal if
     * you only need to use the data object once. But if you need to reuse the same data object for
     * multiple calls, it's more efficient to build your own {@code SoyMapData} object and reuse it
     * with {@link #setData(SoyMapData)}.
     */
    public Renderer setData(Map<String, ?> data);

    /**
     * Sets the data to call the template with. Can be null if the template has no parameters.
     */
    public Renderer setData(SoyMapData data);

    /**
     * Sets the injected data to call the template with. Can be null if not used.
     *
     * <p> Note: If you call this method instead of {@link #setIjData(SoyMapData)}, the data
     * will be converted to a {@code SoyMapData} object on each call. This may not be a big deal if
     * you only need to use the data object once. But if you need to reuse the same data object for
     * multiple calls, it's more efficient to build your own {@code SoyMapData} object and reuse it
     * with {@link #setIjData (SoyMapData)}.
     */
    public Renderer setIjData(Map<String, ?> ijData);

    /**
     * Sets the injected data to call the template with. Can be null if not used.
     */
    public Renderer setIjData(SoyMapData ijData);

    /**
     * Sets the set of active delegate package names.
     */
    public Renderer setActiveDelegatePackageNames(Set<String> activeDelegatePackageNames);

    /**
     * Sets the bundle of translated messages, or null to use the messages from the Soy source.
     */
    public Renderer setMsgBundle(SoyMsgBundle msgBundle);

    /**
     * Sets the CSS renaming map.
     */
    public Renderer setCssRenamingMap(SoyCssRenamingMap cssRenamingMap);

    /**
     * If set to true, indicates that we should not add the current combination of
     * {@code SoyMsgBundle} and {@code SoyCssRenamingMap} to the cache if it's not already there.
     * Only applicable when the associated {@code SoyTofu} instance uses caching. Default value is
     * false, i.e. by default we always add to cache when not already present.
     *
     * <p> Specifically, if {@code dontAddToCache} is set to true, then after checking the cache for
     * the current combination of {@code SoyMsgBundle} and {@code SoyCssRenamingMap}:
     * (a) if found in cache, we will use the cached intermediate results for faster rendering,
     * (b) if not found in cache, we will fall back to the no-caching method of rendering.
     *
     * <p> If your app uses many different {@code SoyMsgBundle}s or {@code SoyCssRenamingMap}s and
     * you're finding that the caching mode of {@code SoyTofu} is using too much memory, one
     * strategy may be to first prime the cache with the most common combinations by calling
     * {@link SoyTofu#addToCache}, and then when rendering, always {@code setDontAddToCache(true)}.
     * This way, most of your renders will use the cached results, yet your cache will never grow
     * beyond the size that you initially primed it to be.
     */
    public Renderer setDontAddToCache(boolean dontAddToCache);

    /**
     * Renders the template using the data, injected data, and message bundle previously set.
     */
    public String render();
  }


  // -----------------------------------------------------------------------------------------------
  // Old render methods.


  /**
   * Renders a template.
   *
   * <p> Note: If you call this method instead of
   * {@link #render(SoyTemplateInfo, SoyMapData, SoyMsgBundle)},
   * your template data will be converted to a {@code SoyMapData} object on each call. This may not
   * be a big deal if you only need to use the data object once. But if you need to reuse the same
   * data object for multiple calls, it's more efficient to build your own {@code SoyMapData} object
   * and reuse it with {@link #render(SoyTemplateInfo, SoyMapData, SoyMsgBundle)}.
   *
   * @param templateInfo Info for the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @return The rendered text.
   * @deprecated Use {@link #newRenderer(SoyTemplateInfo)}.
   */
  @Deprecated
  public String render(SoyTemplateInfo templateInfo, @Nullable Map<String, ?> data,
                       @Nullable SoyMsgBundle msgBundle);


  /**
   * Renders a template.
   *
   * @param templateInfo Info for the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @return The rendered text.
   * @deprecated Use {@link #newRenderer(SoyTemplateInfo)}.
   */
  @Deprecated
  public String render(SoyTemplateInfo templateInfo, @Nullable SoyMapData data,
                       @Nullable SoyMsgBundle msgBundle);


  /**
   * Renders a template.
   *
   * <p> Note: If you call this method instead of {@link #render(String, SoyMapData, SoyMsgBundle)},
   * your template data will be converted to a {@code SoyMapData} object on each call. This may not
   * be a big deal if you only need to use the data object once. But if you need to reuse the same
   * data object for multiple calls, it's more efficient to build your own {@code SoyMapData} object
   * and reuse it with {@link #render(String, SoyMapData, SoyMsgBundle)}.
   *
   * @param templateName The name of the template to render. If this SoyTofu instance is namespaced,
   *     then this parameter should be a partial name beginning with a dot (e.g. ".fooTemplate").
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @return The rendered text.
   * @deprecated Use {@link #newRenderer(String)}.
   */
  @Deprecated
  public String render(String templateName, @Nullable Map<String, ?> data,
                       @Nullable SoyMsgBundle msgBundle);


  /**
   * Renders a template.
   *
   * @param templateName The name of the template to render. If this SoyTofu instance is namespaced,
   *     then this parameter should be a partial name beginning with a dot (e.g. ".fooTemplate").
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @return The rendered text.
   * @deprecated Use {@link #newRenderer(String)}.
   */
  @Deprecated
  public String render(String templateName, @Nullable SoyMapData data,
                       @Nullable SoyMsgBundle msgBundle);

}
