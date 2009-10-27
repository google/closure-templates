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

import java.util.Map;

import javax.annotation.Nullable;


/**
 * SoyTofu is the public interface for a Java object that represents a compiled Soy file set.
 *
 * @author Kai Huang
 */
public interface SoyTofu {


  /**
   * Gets the namespace of this SoyTofu object. The namespace is simply a convenience allowing
   * {@code render()} to be called with a partial template name (e.g. ".fooTemplate").
   * Note: The namespace may be null, in which case {@code render()} must be called with the full
   * template name.
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
   */
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
   */
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
   */
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
   */
  public String render(String templateName, @Nullable SoyMapData data,
                       @Nullable SoyMsgBundle msgBundle);

}
