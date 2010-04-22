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

import com.google.common.collect.Maps;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofuException;
import com.google.template.soy.tofu.internal.RenderVisitor.RenderVisitorFactory;

import java.util.Map;

import javax.annotation.Nullable;


/**
 * Represents a compiled Soy file set. This is the result of compiling Soy to a Java object.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class BaseTofu implements SoyTofu {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface BaseTofuFactory {

    /**
     * @param soyTree The Soy parse tree containing all the files in the Soy file set.
     */
    public BaseTofu create(SoyFileSetNode soyTree);
  }


  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  /** Factory for creating an instance of RenderVisitor. */
  private final RenderVisitorFactory renderVisitorFactory;

  /** The Soy parse tree containing all the files in the Soy file set. */
  @SuppressWarnings({"unused", "UnusedDeclaration"})
  private final SoyFileSetNode soyTree;  // good to keep even if not currently used

  /** Map from template name to template node. */
  private final Map<String, TemplateNode> templateMap;


  /**
   * @param apiCallScope The scope object that manages the API call scope.
   * @param renderVisitorFactory Factory for creating an instance of RenderVisitor.
   * @param soyTree The Soy parse tree containing all the files in the Soy file set.
   */
  @AssistedInject
  public BaseTofu(@ApiCall GuiceSimpleScope apiCallScope, RenderVisitorFactory renderVisitorFactory,
                  @Assisted SoyFileSetNode soyTree) {

    this.apiCallScope = apiCallScope;
    this.renderVisitorFactory = renderVisitorFactory;
    this.soyTree = soyTree;

    templateMap = Maps.newHashMap();
    for (SoyFileNode soyFile : soyTree.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        templateMap.put(template.getTemplateName(), template);
      }
    }
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


  @Override public String render(SoyTemplateInfo templateInfo, @Nullable Map<String, ?> data,
                                 @Nullable SoyMsgBundle msgBundle) {
    return render(templateInfo.getName(), data, msgBundle);
  }


  @Override public String render(SoyTemplateInfo templateInfo, @Nullable SoyMapData data,
                                 @Nullable SoyMsgBundle msgBundle) {
    return render(templateInfo.getName(), data, msgBundle);
  }


  /**
   * {@inheritDoc}
   *
   * @param templateName The full name of the template to render.
   */
  @Override public String render(String templateName, @Nullable Map<String, ?> data,
                                 @Nullable SoyMsgBundle msgBundle) {
    return render(templateName, (data == null) ? null : new SoyMapData(data), msgBundle);
  }


  /**
   * {@inheritDoc}
   *
   * @param templateName The full name of the template to render.
   */
  @Override public String render(String templateName, @Nullable SoyMapData data,
                                 @Nullable SoyMsgBundle msgBundle) {
    return renderMain(templateName, data, msgBundle);
  }


  /**
   * Main entry point used by all of the API render() calls.
   *
   * @param templateName The full name of the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the
   *     Soy source.
   * @return The rendered text.
   */
  private String renderMain(
      String templateName, @Nullable SoyMapData data, @Nullable SoyMsgBundle msgBundle) {

    StringBuilder outputSb = new StringBuilder();

    apiCallScope.enter();
    try {
      // Seed the scoped parameters.
      // TODO: Maybe change API to let users specify bidiGlobalDir.
      ApiCallScopeUtils.seedSharedParams(apiCallScope, msgBundle, 0 /* to be changed */);

      // Do the rendering.
      renderInternal(outputSb, templateName, data);

    } finally {
      apiCallScope.exit();
    }

    return outputSb.toString();
  }


  /**
   * Package-private helper to render a template and append the result to a StringBuilder.
   *
   * @param outputSb The StringBuilder to append the rendered text to.
   * @param templateName The full name of the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   */
  void renderInternal(StringBuilder outputSb, String templateName, @Nullable SoyMapData data) {

    TemplateNode template = templateMap.get(templateName);
    if (template == null) {
      throw new SoyTofuException("Attempting to render undefined template '" + templateName + "'.");
    }

    renderVisitorFactory.create(outputSb, this, data, null).exec(template);
  }

}
