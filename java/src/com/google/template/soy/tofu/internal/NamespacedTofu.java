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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.tofu.SoyTofu;

import java.util.Map;

import javax.annotation.Nullable;


/**
 * Represents a compiled Soy file set, with a namespace prepended to templates being rendered.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
class NamespacedTofu implements SoyTofu {


  /** The underlying Tofu object. */
  private final BaseTofu baseTofu;

  /** The namespace of this SoyTofu object. */
  private final String namespace;


  /**
   * @param baseTofu The underlying Tofu object.
   * @param namespace The namespace for this SoyTofu object.
   */
  NamespacedTofu(BaseTofu baseTofu, String namespace) {
    Preconditions.checkNotNull(baseTofu);
    this.baseTofu = baseTofu;
    Preconditions.checkArgument(namespace != null && namespace.length() > 0);
    this.namespace = namespace;
  }


  /**
   * {@inheritDoc}
   *
   * <p> For objects of this class, the namespace is always nonempty.
   */
  @Override public String getNamespace() {
    return namespace;
  }


  @Override public SoyTofu forNamespace(@Nullable String namespace) {
    if (namespace == null) {
      return baseTofu;
    } else {
      checkArgument(namespace.charAt(0) != '.' && namespace.charAt(namespace.length() - 1) != '.',
          "Invalid namespace '" + namespace + "' (must not begin or end with a dot).");
      return new NamespacedTofu(baseTofu, namespace);
    }
  }


  /**
   * Translates a template name that may be full or partial to a full template name.
   */
  private String getFullTemplateName(String templateName) {
    return (templateName.charAt(0) == '.') ? namespace + templateName : templateName;
  }


  @Override public boolean isCaching() {
    return baseTofu.isCaching();
  }


  @Override public void addToCache(
      @Nullable SoyMsgBundle msgBundle, @Nullable SoyCssRenamingMap cssRenamingMap) {
    baseTofu.addToCache(msgBundle, cssRenamingMap);
  }


  @Override public Renderer newRenderer(SoyTemplateInfo templateInfo) {
    return baseTofu.newRenderer(templateInfo);
  }


  @Override public Renderer newRenderer(String templateName) {
    return baseTofu.newRenderer(getFullTemplateName(templateName));
  }


  @Override public ImmutableSortedSet<String> getUsedIjParamsForTemplate(
      SoyTemplateInfo templateInfo) {
    return baseTofu.getUsedIjParamsForTemplate(templateInfo);
  }


  @Override public ImmutableSortedSet<String> getUsedIjParamsForTemplate(String templateName) {
    return baseTofu.getUsedIjParamsForTemplate(getFullTemplateName(templateName));
  }


  // -----------------------------------------------------------------------------------------------
  // Old render methods.


  @Deprecated
  @SuppressWarnings({"deprecation"})
  @Override public String render(SoyTemplateInfo templateInfo, @Nullable Map<String, ?> data,
                                 @Nullable SoyMsgBundle msgBundle) {
    return render(templateInfo.getPartialName(), data, msgBundle);
  }


  @Deprecated
  @SuppressWarnings({"deprecation"})
  @Override public String render(SoyTemplateInfo templateInfo, @Nullable SoyMapData data,
                                 @Nullable SoyMsgBundle msgBundle) {
    return render(templateInfo.getPartialName(), data, msgBundle);
  }


  @Deprecated
  @SuppressWarnings({"deprecation"})
  @Override public String render(String templateName, @Nullable Map<String, ?> data,
                                 @Nullable SoyMsgBundle msgBundle) {
    return render(templateName, (data == null) ? null : new SoyMapData(data), msgBundle);
  }


  @Deprecated
  @SuppressWarnings({"deprecation"})
  @Override public String render(String templateName, @Nullable SoyMapData data,
                                 @Nullable SoyMsgBundle msgBundle) {
    if (templateName.charAt(0) == '.') {
      return baseTofu.render(namespace + templateName, data, msgBundle);
    } else {
      return baseTofu.render(templateName, data, msgBundle);
    }
  }

}
