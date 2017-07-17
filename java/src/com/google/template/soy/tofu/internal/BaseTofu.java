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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope.InScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.sharedpasses.render.RenderVisitorFactory;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofuException;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Represents a compiled Soy file set. This is the result of compiling Soy to a Java object.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class BaseTofu implements SoyTofu {

  /**
   * Injectable factory for creating an instance of this class.
   *
   * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public interface BaseTofuFactory {

    /**
     * @param templates The full set of templates.
     * @param templateToIjParamsInfoMap the ij params for each template.
     */
    BaseTofu create(
        TemplateRegistry templates,
        ImmutableMap<String, ImmutableSortedSet<String>> templateToIjParamsInfoMap);
  }

  private final SoyValueConverter valueConverter;

  /** The scope object that manages the API call scope. */
  private final GuiceSimpleScope apiCallScope;

  private final RenderVisitorFactory renderVisitorFactory;

  private final TemplateRegistry templateRegistry;

  private final ImmutableMap<String, ImmutableSortedSet<String>> templateToIjParamsInfoMap;

  /**
   * @param valueConverter Instance of SoyValueConverter to use.
   * @param apiCallScope The scope object that manages the API call scope.
   * @param renderVisitorFactory Factory for creating an instance of RenderVisitor.
   */
  @AssistedInject
  public BaseTofu(
      SoyValueConverter valueConverter,
      @ApiCall GuiceSimpleScope apiCallScope,
      RenderVisitorFactory renderVisitorFactory,
      @Assisted TemplateRegistry templates,
      @Assisted ImmutableMap<String, ImmutableSortedSet<String>> templateToIjParamsInfoMap) {
    this.valueConverter = valueConverter;
    this.apiCallScope = apiCallScope;
    this.renderVisitorFactory = renderVisitorFactory;
    this.templateRegistry = templates;
    this.templateToIjParamsInfoMap = templateToIjParamsInfoMap;
  }

  /**
   * {@inheritDoc}
   *
   * <p>For objects of this class, the namespace is always null.
   */
  @Override
  public String getNamespace() {
    return null;
  }

  @Override
  public SoyTofu forNamespace(@Nullable String namespace) {
    return (namespace == null) ? this : new NamespacedTofu(this, namespace);
  }

  @Override
  public Renderer newRenderer(SoyTemplateInfo templateInfo) {
    return new RendererImpl(this, templateInfo.getName());
  }

  @Override
  public Renderer newRenderer(String templateName) {
    return new RendererImpl(this, templateName);
  }

  @Override
  public ImmutableSortedSet<String> getUsedIjParamsForTemplate(SoyTemplateInfo templateInfo) {
    return getUsedIjParamsForTemplate(templateInfo.getName());
  }

  @Override
  public ImmutableSortedSet<String> getUsedIjParamsForTemplate(String templateName) {
    ImmutableSortedSet<String> ijParams = templateToIjParamsInfoMap.get(templateName);
    if (ijParams == null) {
      throw new SoyTofuException("Template '" + templateName + "' not found.");
    }
    // TODO: Ideally we'd check that there are no external calls, but we find that in practice many
    // users have written templates that conditionally call to undefined templates. Instead,
    // we'll return a best effor set of what we have here, and over time, we'll encourage users to
    // enforce the "assertNoExternalCalls" flag.
    return ijParams;
  }

  // -----------------------------------------------------------------------------------------------
  // Private methods.

  /**
   * @param outputBuf The Appendable to write the output to.
   * @param templateName The full name of the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param ijData The injected data to call the template with. Can be null if not used.
   * @param activeDelPackageNames The set of active delegate package names, or null if none.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap Map for renaming selectors in 'css' tags, or null if not used.
   * @return The template that was rendered.
   */
  private TemplateNode renderMain(
      Appendable outputBuf,
      String templateName,
      @Nullable SoyRecord data,
      @Nullable SoyRecord ijData,
      @Nullable Predicate<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap idRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      boolean debugSoyTemplateInfo) {

    if (activeDelPackageNames == null) {
      activeDelPackageNames = Predicates.alwaysFalse();
    }

    try (InScope inScope = apiCallScope.enter()) {
      // Seed the scoped parameters.
      ApiCallScopeUtils.seedSharedParams(inScope, msgBundle);

      // Do the rendering.
      return renderMainHelper(
          templateRegistry,
          outputBuf,
          templateName,
          data,
          ijData,
          activeDelPackageNames,
          msgBundle,
          idRenamingMap,
          cssRenamingMap,
          debugSoyTemplateInfo);
    }
  }

  /**
   * Renders a template and appends the result to a StringBuilder.
   *
   * @param templateRegistry A registry of all templates.
   * @param outputBuf The Appendable to append the rendered text to.
   * @param templateName The full name of the template to render.
   * @param data The data to call the template with. Can be null if the template has no parameters.
   * @param ijData The injected data to call the template with. Can be null if not used.
   * @param activeDelPackageNames The set of active delegate package names.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param cssRenamingMap Map for renaming selectors in 'css' tags, or null if not used.
   * @return The template that was rendered.
   */
  private TemplateNode renderMainHelper(
      TemplateRegistry templateRegistry,
      Appendable outputBuf,
      String templateName,
      @Nullable SoyRecord data,
      @Nullable SoyRecord ijData,
      Predicate<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap idRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      boolean debugSoyTemplateInfo) {

    TemplateNode template = templateRegistry.getBasicTemplate(templateName);
    if (template == null) {
      throw new SoyTofuException("Attempting to render undefined template '" + templateName + "'.");
    } else if (template.getVisibility() == Visibility.PRIVATE) {
      throw new SoyTofuException("Attempting to render private template '" + templateName + "'.");
    }

    if (data == null) {
      data = SoyValueConverter.EMPTY_DICT;
    }
    if (ijData == null) {
      ijData = SoyValueConverter.EMPTY_DICT;
    }

    try {
      RenderVisitor rv =
          renderVisitorFactory.create(
              outputBuf,
              templateRegistry,
              data,
              ijData,
              activeDelPackageNames,
              msgBundle,
              idRenamingMap,
              cssRenamingMap,
              debugSoyTemplateInfo);
      rv.exec(template);

    } catch (RenderException re) {
      throw new SoyTofuException(re);
    }

    return template;
  }

  // -----------------------------------------------------------------------------------------------
  // Renderer implementation.

  /** Simple implementation of the Renderer interface. */
  private static class RendererImpl implements Renderer {

    private final BaseTofu baseTofu;
    private final String templateName;
    private SoyRecord data;
    private SoyRecord ijData;
    private SoyMsgBundle msgBundle;
    private SoyIdRenamingMap idRenamingMap;
    private SoyCssRenamingMap cssRenamingMap;
    private Predicate<String> activeDelPackageNames;
    private SanitizedContent.ContentKind expectedContentKind;
    private boolean contentKindExplicitlySet;
    private boolean debugSoyTemplateInfo;

    /**
     * Constructs a {@code Renderer} instance for Tofu backends. By default, the content kind should
     * be HTML, but this can also be overridden by {@code setContentKind} method.
     *
     * @param baseTofu The underlying BaseTofu object used to perform the rendering.
     * @param templateName The full template name (including namespace).
     */
    public RendererImpl(BaseTofu baseTofu, String templateName) {
      this.baseTofu = baseTofu;
      this.templateName = templateName;
      this.expectedContentKind = SanitizedContent.ContentKind.HTML;
    }

    @Override
    public Renderer setData(Map<String, ?> data) {
      this.data = (data == null) ? null : baseTofu.valueConverter.newDictFromMap(data);
      return this;
    }

    @Override
    public Renderer setData(SoyRecord data) {
      this.data = data;
      return this;
    }

    @Override
    public Renderer setIjData(Map<String, ?> ijData) {
      this.ijData = (ijData == null) ? null : baseTofu.valueConverter.newDictFromMap(ijData);
      return this;
    }

    @Override
    public Renderer setIjData(SoyRecord ijData) {
      this.ijData = ijData;
      return this;
    }

    @Override
    public Renderer setActiveDelegatePackageSelector(Predicate<String> activeDelegatePackageNames) {
      this.activeDelPackageNames = activeDelegatePackageNames;
      return this;
    }

    @Override
    public Renderer setMsgBundle(SoyMsgBundle msgBundle) {
      this.msgBundle = msgBundle;
      return this;
    }

    @Override
    public Renderer setIdRenamingMap(SoyIdRenamingMap idRenamingMap) {
      this.idRenamingMap = idRenamingMap;
      return this;
    }

    @Override
    public Renderer setCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      this.cssRenamingMap = cssRenamingMap;
      return this;
    }

    @Override
    public Renderer setDebugSoyTemplateInfo(boolean debugSoyTemplateInfo) {
      this.debugSoyTemplateInfo = debugSoyTemplateInfo;
      return this;
    }

    @Override
    public Renderer setContentKind(SanitizedContent.ContentKind contentKind) {
      this.expectedContentKind = Preconditions.checkNotNull(contentKind);
      this.contentKindExplicitlySet = true;
      return this;
    }

    @Override
    public String render() {
      StringBuilder sb = new StringBuilder();
      render(sb);
      return sb.toString();
    }

    @Override
    public SanitizedContent.ContentKind render(Appendable out) {
      TemplateNode template =
          baseTofu.renderMain(
              out,
              templateName,
              data,
              ijData,
              activeDelPackageNames,
              msgBundle,
              idRenamingMap,
              cssRenamingMap,
              debugSoyTemplateInfo);
      if (contentKindExplicitlySet || template.getContentKind() != null) {
        // Enforce the content kind if:
        // - The caller explicitly set a content kind to validate.
        // - The template is strict. This avoids accidentally using a text strict template in a
        // place where HTML was implicitly expected.
        enforceContentKind(template);
      }
      return template.getContentKind() != null
          ? SanitizedContent.ContentKind.valueOf(template.getContentKind().name())
          : null;
    }

    @Override
    public SanitizedContent renderStrict() {
      StringBuilder sb = new StringBuilder();
      TemplateNode template =
          baseTofu.renderMain(
              sb,
              templateName,
              data,
              ijData,
              activeDelPackageNames,
              msgBundle,
              idRenamingMap,
              cssRenamingMap,
              debugSoyTemplateInfo);
      enforceContentKind(template);
      // Use the expected instead of actual content kind; that way, if an HTML template is rendered
      // as TEXT, we will return TEXT.
      return UnsafeSanitizedContentOrdainer.ordainAsSafe(sb.toString(), expectedContentKind);
    }

    private void enforceContentKind(TemplateNode template) {
      if (expectedContentKind == SanitizedContent.ContentKind.TEXT) {
        // Allow any template to be called as text. This is consistent with the fact that
        // kind="text" templates can call any other template.
        return;
      }
      if (template.getContentKind() == null) {
        throw new SoyTofuException(
            "Expected template to be autoescape=\"strict\" "
                + "but was autoescape=\""
                + template.getAutoescapeMode().getAttributeValue()
                + "\": "
                + template.getTemplateName());
      }
      SanitizedContentKind expectedAsSanitizedContentKind =
          SanitizedContentKind.valueOf(expectedContentKind.name());
      if (expectedAsSanitizedContentKind != template.getContentKind()) {
        throw new SoyTofuException(
            "Expected template to be kind=\""
                + expectedAsSanitizedContentKind.asAttributeValue()
                + "\" but was kind=\""
                + template.getContentKind().asAttributeValue()
                + "\": "
                + template.getTemplateName());
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Old render methods.

  @Deprecated
  @Override
  public String render(
      SoyTemplateInfo templateInfo, @Nullable SoyRecord data, @Nullable SoyMsgBundle msgBundle) {
    return (new RendererImpl(this, templateInfo.getName()))
        .setData(data)
        .setMsgBundle(msgBundle)
        .render();
  }

  @Deprecated
  @Override
  public String render(
      String templateName, @Nullable Map<String, ?> data, @Nullable SoyMsgBundle msgBundle) {
    return (new RendererImpl(this, templateName)).setData(data).setMsgBundle(msgBundle).render();
  }

  @Deprecated
  @Override
  public String render(
      String templateName, @Nullable SoyRecord data, @Nullable SoyMsgBundle msgBundle) {
    return (new RendererImpl(this, templateName)).setData(data).setMsgBundle(msgBundle).render();
  }
}
