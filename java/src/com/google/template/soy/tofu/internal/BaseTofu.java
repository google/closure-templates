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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyTemplate;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.sharedpasses.render.EvalVisitorFactoryImpl;
import com.google.template.soy.sharedpasses.render.RenderException;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.tofu.SoyTofu;
import com.google.template.soy.tofu.SoyTofuException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Represents a compiled Soy file set. This is the result of compiling Soy to a Java object.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class BaseTofu implements SoyTofu {

  /** The scope object that manages the API call scope. */
  private final SoyScopedData.Enterable apiCallScope;

  private final ImmutableMap<String, TemplateNode> basicTemplates;
  private final DelTemplateSelector<TemplateDelegateNode> delTemplates;

  private final ImmutableMap<String, ImmutableSortedSet<String>> templateToIjParamsInfoMap;

  private final ImmutableMap<String, Supplier<Object>> pluginInstances;

  /** @param apiCallScope The scope object that manages the API call scope. */
  public BaseTofu(
      SoyScopedData.Enterable apiCallScope,
      SoyFileSetNode fileSet,
      Map<String, Supplier<Object>> pluginInstances) {
    this.apiCallScope = apiCallScope;
    ImmutableMap.Builder<String, TemplateNode> basicTemplates = ImmutableMap.builder();
    DelTemplateSelector.Builder<TemplateDelegateNode> delTemplates =
        new DelTemplateSelector.Builder<>();
    for (SoyFileNode fileNode : fileSet.getChildren()) {
      for (TemplateNode template : fileNode.getTemplates()) {
        if (template instanceof TemplateDelegateNode) {
          TemplateDelegateNode delegateNode = (TemplateDelegateNode) template;
          String delTemplateName = delegateNode.getDelTemplateName();
          String delPackageName = delegateNode.getDelPackageName();
          String variant = delegateNode.getDelTemplateVariant();
          if (delPackageName == null) {
            delTemplates.addDefault(delTemplateName, variant, delegateNode);
          } else {
            delTemplates.add(delTemplateName, delPackageName, variant, delegateNode);
          }
        } else {
          basicTemplates.put(template.getTemplateName(), template);
        }
      }
    }
    this.basicTemplates = basicTemplates.build();
    this.delTemplates = delTemplates.build();
    this.templateToIjParamsInfoMap =
        buildTemplateToIjParamsInfoMap(this.basicTemplates, this.delTemplates);
    this.pluginInstances = ImmutableMap.copyOf(pluginInstances);
  }

  private static ImmutableMap<String, ImmutableSortedSet<String>> buildTemplateToIjParamsInfoMap(
      ImmutableMap<String, TemplateNode> basicTemplates,
      DelTemplateSelector<TemplateDelegateNode> delTemplates) {
    Map<String, ImmutableSortedSet<String>> templateNameToIjs = new LinkedHashMap<>();
    Set<TemplateNode> soFar = Sets.newIdentityHashSet();
    ArrayDeque<TemplateNode> toVisit = new ArrayDeque<>();
    Set<String> ijsForTemplate = new HashSet<>();
    // this loop will be faster if we execute in a reverse topological order. But it probably
    // doesn't mattter too much.
    for (TemplateNode initialTemplate :
        Iterables.concat(
            basicTemplates.values(), delTemplates.delTemplateNameToValues().values())) {
      toVisit.add(initialTemplate);
      TemplateNode currentTemplate;
      while ((currentTemplate = toVisit.poll()) != null) {
        if (!soFar.add(currentTemplate)) {
          continue; // avoid revisiting recursion
        }
        ImmutableSortedSet<String> alreadyCalculated =
            templateNameToIjs.get(currentTemplate.getTemplateName());
        if (alreadyCalculated != null) {
          ijsForTemplate.addAll(alreadyCalculated);
          continue;
        }
        // otherwise we need to add these ijs and then push all if its direct callees
        collectIjParams(currentTemplate, ijsForTemplate);
        for (TemplateLiteralNode templateLiteralNode :
            SoyTreeUtils.getAllNodesOfType(currentTemplate, TemplateLiteralNode.class)) {
          TemplateNode callee = basicTemplates.get(templateLiteralNode.getResolvedName());
          if (callee != null) {
            toVisit.add(callee);
          }
        }
        for (CallDelegateNode callDelegateNode :
            SoyTreeUtils.getAllNodesOfType(currentTemplate, CallDelegateNode.class)) {
          toVisit.addAll(
              delTemplates.delTemplateNameToValues().get(callDelegateNode.getDelCalleeName()));
        }
      }
      // when we exit the loop we have calculated everything for this template.
      templateNameToIjs.put(
          initialTemplate.getTemplateName(), ImmutableSortedSet.copyOf(ijsForTemplate));
      // reset datastructures for next iteration
      ijsForTemplate.clear();
      toVisit.clear();
      soFar.clear();
    }

    return ImmutableMap.copyOf(templateNameToIjs);
  }

  private static void collectIjParams(TemplateNode template, Set<String> into) {
    for (TemplateParam param : template.getInjectedParams()) {
      into.add(param.name());
    }
  }

  /**
   * Queries the current SoyTofu instance to see if it holds a given template. If the requested
   * template is found, `true` is returned, otherwise, `false`.
   *
   * @param namespace Namespace to check for a template.
   * @return Whether the template exists or not.
   */
  @Override
  public Boolean hasTemplate(String namespace) {
    return this.basicTemplates.containsKey(namespace);
  }

  @Override
  public SoyTofu forNamespace(@Nullable String namespace) {
    return (namespace == null) ? this : new NamespacedTofu(this, namespace);
  }

  @Override
  public RendererImpl newRenderer(SoyTemplateInfo templateInfo) {
    return new RendererImpl(this, templateInfo.getName(), null);
  }

  @Override
  public RendererImpl newRenderer(String templateName) {
    return new RendererImpl(this, templateName, null);
  }

  @Override
  public RendererImpl newRenderer(SoyTemplate params) {
    return new RendererImpl(this, params.getTemplateName(), params.getParamsAsMap());
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
   * @param pluginInstances The instances used for evaluating functions that call instance methods.
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
      boolean debugSoyTemplateInfo,
      ImmutableMap<String, Supplier<Object>> pluginInstances) {

    if (activeDelPackageNames == null) {
      activeDelPackageNames = arg -> false;
    }

    try (SoyScopedData.InScope inScope = apiCallScope.enter(msgBundle)) {
      // Do the rendering.
      return renderMainHelper(
          outputBuf,
          templateName,
          data,
          ijData,
          activeDelPackageNames,
          msgBundle,
          idRenamingMap,
          cssRenamingMap,
          debugSoyTemplateInfo,
          pluginInstances);
    }
  }

  /**
   * Renders a template and appends the result to a StringBuilder.
   *
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
      Appendable outputBuf,
      String templateName,
      @Nullable SoyRecord data,
      @Nullable SoyRecord ijData,
      Predicate<String> activeDelPackageNames,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap idRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      boolean debugSoyTemplateInfo,
      ImmutableMap<String, Supplier<Object>> pluginInstances) {

    // templateNode is always guaranteed to be non-null because for a tofu compile all templates are
    // considered source files
    TemplateNode template = basicTemplates.get(templateName);
    if (template == null) {
      throw new SoyTofuException("Attempting to render undefined template '" + templateName + "'.");
    } else if (template.getVisibility() == Visibility.PRIVATE) {
      throw new SoyTofuException("Attempting to render private template '" + templateName + "'.");
    }

    if (data == null) {
      data = ParamStore.EMPTY_INSTANCE;
    }
    if (ijData == null) {
      ijData = ParamStore.EMPTY_INSTANCE;
    }

    try {
      RenderVisitor rv =
          new RenderVisitor(
              new EvalVisitorFactoryImpl(),
              outputBuf,
              basicTemplates,
              delTemplates,
              data,
              ijData,
              activeDelPackageNames,
              msgBundle,
              idRenamingMap,
              cssRenamingMap,
              debugSoyTemplateInfo,
              pluginInstances);
      rv.exec(template);

    } catch (RenderException re) {
      throw new SoyTofuException(re);
    }

    return template;
  }

  // -----------------------------------------------------------------------------------------------
  // Renderer implementation.

  /** Simple implementation of the Renderer interface. */
  @SuppressWarnings("deprecation")
  static class RendererImpl implements Renderer {

    private final BaseTofu baseTofu;
    private final String templateName;
    private SoyRecord data;
    private SoyRecord ijData;
    private SoyMsgBundle msgBundle;
    private SoyIdRenamingMap idRenamingMap;
    private SoyCssRenamingMap cssRenamingMap;
    private Predicate<String> activeDelPackageNames;
    private SanitizedContent.ContentKind expectedContentKind;
    private boolean debugSoyTemplateInfo;
    private Map<String, Supplier<Object>> perRenderPluginInstances;
    private boolean dataSetInConstructor;

    /**
     * Constructs a {@code Renderer} instance for Tofu backends.
     *
     * @param baseTofu The underlying BaseTofu object used to perform the rendering.
     * @param templateName The full template name (including namespace).
     * @param data Optionally provided template data.
     */
    RendererImpl(BaseTofu baseTofu, String templateName, Map<String, ?> data) {
      this.baseTofu = baseTofu;
      this.templateName = templateName;
      this.expectedContentKind = SanitizedContent.ContentKind.HTML;
      if (data != null) {
        setData(data);
        this.dataSetInConstructor = true;
      }
    }

    private static ParamStore mapAsParamStore(Map<String, ?> source) {
      ParamStore dest = new ParamStore(source.size());
      for (Map.Entry<String, ?> entry : source.entrySet()) {
        String key = entry.getKey();
        SoyValueProvider value;
        try {
          value = SoyValueConverter.INSTANCE.convert(entry.getValue());
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "Unable to convert param " + key + " to a SoyValue", e);
        }
        dest.setField(key, value);
      }
      return dest;
    }

    @Override
    public RendererImpl setData(Map<String, ?> data) {
      Preconditions.checkState(
          !dataSetInConstructor,
          "May not call setData on a Renderer created from a TemplateParams");

      this.data = data != null ? mapAsParamStore(data) : null;
      return this;
    }

    @Override
    public RendererImpl setData(SoyRecord data) {
      Preconditions.checkState(
          !dataSetInConstructor,
          "May not call setData on a Renderer created from a TemplateParams");
      this.data = data;
      return this;
    }

    @Override
    public RendererImpl setIjData(Map<String, ?> ijData) {
      this.ijData = (ijData == null) ? null : mapAsParamStore(ijData);
      return this;
    }

    @Override
    public RendererImpl setIjData(SoyRecord ijData) {
      this.ijData = ijData;
      return this;
    }

    @Override
    public RendererImpl setPluginInstances(Map<String, Supplier<Object>> pluginInstances) {
      this.perRenderPluginInstances = checkNotNull(pluginInstances);
      return this;
    }

    @Override
    public RendererImpl setActiveDelegatePackageSelector(
        Predicate<String> activeDelegatePackageNames) {
      this.activeDelPackageNames = activeDelegatePackageNames;
      return this;
    }

    @Override
    public RendererImpl setMsgBundle(SoyMsgBundle msgBundle) {
      this.msgBundle = msgBundle;
      return this;
    }

    @Override
    public RendererImpl setIdRenamingMap(SoyIdRenamingMap idRenamingMap) {
      this.idRenamingMap = idRenamingMap;
      return this;
    }

    @Override
    public RendererImpl setCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      this.cssRenamingMap = cssRenamingMap;
      return this;
    }

    @Override
    public RendererImpl setDebugSoyTemplateInfo(boolean debugSoyTemplateInfo) {
      this.debugSoyTemplateInfo = debugSoyTemplateInfo;
      return this;
    }

    @Override
    @Deprecated
    public String render() {
      StringBuilder sb = new StringBuilder();
      render(sb);
      return sb.toString();
    }

    private ImmutableMap<String, Supplier<Object>> getPluginInstances() {

      if (perRenderPluginInstances != null) {
        return ImmutableMap.<String, Supplier<Object>>builder()
            .putAll(baseTofu.pluginInstances)
            .putAll(perRenderPluginInstances)
            .build();
      }
      return baseTofu.pluginInstances;
    }

    @Override
    public void renderHtml(Appendable out) {
      renderSanitizedContent(ContentKind.HTML, out);
    }

    @Override
    public SanitizedContent renderHtml() {
      return renderSanitizedContent(ContentKind.HTML);
    }

    @Override
    public void renderJs(Appendable out) {
      renderSanitizedContent(ContentKind.JS, out);
    }

    @Override
    public SanitizedContent renderJs() {
      return renderSanitizedContent(ContentKind.JS);
    }

    @Override
    public void renderUri(Appendable out) {
      renderSanitizedContent(ContentKind.URI, out);
    }

    @Override
    public SanitizedContent renderUri() {
      return renderSanitizedContent(ContentKind.URI);
    }

    @Override
    public void renderTrustedResourceUri(Appendable out) {
      renderSanitizedContent(ContentKind.TRUSTED_RESOURCE_URI, out);
    }

    @Override
    public SanitizedContent renderTrustedResourceUri() {
      return renderSanitizedContent(ContentKind.TRUSTED_RESOURCE_URI);
    }

    @Override
    public void renderAttributes(Appendable out) {
      renderSanitizedContent(ContentKind.ATTRIBUTES, out);
    }

    @Override
    public SanitizedContent renderAttributes() {
      return renderSanitizedContent(ContentKind.ATTRIBUTES);
    }

    @Override
    public void renderCss(Appendable out) {
      renderSanitizedContent(ContentKind.CSS, out);
    }

    @Override
    public SanitizedContent renderCss() {
      return renderSanitizedContent(ContentKind.CSS);
    }

    @Override
    public void renderText(Appendable out) {
      renderMain(out);
    }

    @Override
    public String renderText() {
      StringBuilder sb = new StringBuilder();
      renderMain(sb);
      return sb.toString();
    }

    @Override
    @Deprecated
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
              debugSoyTemplateInfo,
              getPluginInstances());
      enforceContentKind(template);
      return SanitizedContent.ContentKind.valueOf(template.getContentKind().name());
    }

    @Override
    @Deprecated
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
              debugSoyTemplateInfo,
              getPluginInstances());
      enforceContentKind(template);
      // Use the expected instead of actual content kind; that way, if an HTML template is rendered
      // as TEXT, we will return TEXT.
      return UnsafeSanitizedContentOrdainer.ordainAsSafe(sb.toString(), expectedContentKind);
    }

    /**
     * Renders the configured template to the given output buffer, and verifies that the template
     * has the expected content kind.
     */
    private void renderSanitizedContent(ContentKind contentKind, Appendable out) {
      TemplateNode template = renderMain(out);
      enforceContentKind(template, contentKind);
    }

    /**
     * Renders the configured template to a {@link SanitizedContent}, and verifies that the template
     * has the expected content kind.
     */
    private SanitizedContent renderSanitizedContent(ContentKind contentKind) {
      StringBuilder sb = new StringBuilder();
      TemplateNode template = renderMain(sb);
      enforceContentKind(template, contentKind);
      return UnsafeSanitizedContentOrdainer.ordainAsSafe(sb.toString(), contentKind);
    }

    private TemplateNode renderMain(Appendable out) {
      return baseTofu.renderMain(
          out,
          templateName,
          data,
          ijData,
          activeDelPackageNames,
          msgBundle,
          idRenamingMap,
          cssRenamingMap,
          debugSoyTemplateInfo,
          getPluginInstances());
    }

    private static void enforceContentKind(
        TemplateNode template, SanitizedContent.ContentKind expectedContentKind) {
      if (expectedContentKind == SanitizedContent.ContentKind.TEXT) {
        // Allow any template to be called as text.
        return;
      }
      checkNotNull(template.getContentKind());
      SanitizedContentKind expectedAsSanitizedContentKind =
          SanitizedContentKind.valueOf(expectedContentKind.name());
      if (expectedAsSanitizedContentKind != template.getContentKind()) {
        throw new SoyTofuException(
            "Expected template '"
                + template.getTemplateName()
                + "' to be kind=\""
                + expectedAsSanitizedContentKind.asAttributeValue()
                + "\" but was kind=\""
                + template.getContentKind().asAttributeValue()
                + "\"");
      }
    }

    private void enforceContentKind(TemplateNode template) {
      enforceContentKind(template, expectedContentKind);
    }
  }
}
