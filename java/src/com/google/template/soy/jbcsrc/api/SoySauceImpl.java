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

package com.google.template.soy.jbcsrc.api;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.jbcsrc.shared.Names.rewriteStackTrace;

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyTemplate;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate.Factory;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.LegacyFunctionAdapter;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Main entry point for rendering Soy templates on the server. */
public final class SoySauceImpl implements SoySauce {
  private final CompiledTemplates templates;
  private final SoyScopedData.Enterable apiCallScope;
  private final ImmutableMap<String, Supplier<Object>> pluginInstances;
  private final ImmutableMap<String, SoyJavaPrintDirective> printDirectives;

  public SoySauceImpl(
      CompiledTemplates templates,
      SoyScopedData.Enterable apiCallScope,
      ImmutableMap<String, ? extends SoyFunction> functions,
      ImmutableMap<String, ? extends SoyPrintDirective> printDirectives,
      ImmutableMap<String, Supplier<Object>> pluginInstances) {
    this.templates = checkNotNull(templates);
    this.apiCallScope = checkNotNull(apiCallScope);
    ImmutableMap.Builder<String, Supplier<Object>> pluginInstanceBuilder = ImmutableMap.builder();
    pluginInstanceBuilder.putAll(pluginInstances);

    for (Map.Entry<String, ? extends SoyFunction> entry : functions.entrySet()) {
      String fnName = entry.getKey();
      if (entry.getValue() instanceof SoyJavaFunction) {
        SoyJavaFunction fn = (SoyJavaFunction) entry.getValue();
        pluginInstanceBuilder.put(fnName, Suppliers.ofInstance(new LegacyFunctionAdapter(fn)));
      }
    }

    // SoySauce has no need for SoyPrintDirectives that are not SoyJavaPrintDirectives.
    // Filter them out.
    ImmutableMap.Builder<String, SoyJavaPrintDirective> soyJavaPrintDirectives =
        ImmutableMap.builder();
    for (Map.Entry<String, ? extends SoyPrintDirective> entry : printDirectives.entrySet()) {
      SoyPrintDirective printDirective = entry.getValue();
      if (printDirective instanceof SoyJavaPrintDirective) {
        soyJavaPrintDirectives.put(entry.getKey(), (SoyJavaPrintDirective) printDirective);
      }
    }
    this.printDirectives = soyJavaPrintDirectives.build();
    this.pluginInstances = pluginInstanceBuilder.build();
  }

  @Override
  public ImmutableSortedSet<String> getTransitiveIjParamsForTemplate(String templateName) {
    return templates.getTransitiveIjParamsForTemplate(templateName);
  }

  @Override
  public ImmutableList<String> getAllRequiredCssNamespaces(
      String templateName,
      Predicate<String> enabledDelpackages,
      boolean collectCssFromDelvariants) {
    return templates.getAllRequiredCssNamespaces(
        templateName, enabledDelpackages, collectCssFromDelvariants);
  }

  @Override
  public Boolean hasTemplate(String template) {
    try {
      templates.getTemplateFactory(template);
      return true;
    } catch (IllegalArgumentException iae) {
      return false;
    }
  }

  @Override
  public RendererImpl renderTemplate(String template) {
    CompiledTemplate.Factory factory = templates.getTemplateFactory(template);
    return new RendererImpl(template, factory, templates.getTemplateContentKind(template), null);
  }

  @Override
  public RendererImpl newRenderer(SoyTemplate params) {
    String template = params.getTemplateName();
    CompiledTemplate.Factory factory = templates.getTemplateFactory(template);
    return new RendererImpl(
        template, factory, templates.getTemplateContentKind(template), params.getParamsAsMap());
  }

  final class RendererImpl implements Renderer {
    private final String templateName;
    private final CompiledTemplate.Factory templateFactory;
    private final ContentKind contentKind;
    private Predicate<String> activeDelegatePackages = arg -> false;
    private SoyMsgBundle msgs = SoyMsgBundle.EMPTY;
    private SoyLogger logger = SoyLogger.NO_OP;
    private final RenderContext.Builder contextBuilder =
        new RenderContext.Builder()
            .withCompiledTemplates(templates)
            .withSoyPrintDirectives(printDirectives)
            .withPluginInstances(SoySauceImpl.this.pluginInstances);

    private SoyRecord data = SoyValueConverter.EMPTY_DICT;
    private SoyRecord ij = SoyValueConverter.EMPTY_DICT;
    // TODO(b/129547159): Clean up this variable.
    private ContentKind expectedContentKind = ContentKind.HTML;
    private Map<String, Supplier<Object>> perRenderPluginInstances = null;
    private boolean dataSetInConstructor;

    RendererImpl(
        String templateName,
        Factory templateFactory,
        ContentKind contentKind,
        @Nullable Map<String, SoyValueProvider> data) {
      this.templateName = templateName;
      this.templateFactory = checkNotNull(templateFactory);
      this.contentKind = contentKind;
      if (data != null) {
        setData(data);
        this.dataSetInConstructor = true;
      }
    }

    @Override
    public RendererImpl setIj(Map<String, ?> record) {
      this.ij = SoyValueConverter.INSTANCE.newDictFromMap(checkNotNull(record));
      return this;
    }

    @Override
    public RendererImpl setPluginInstances(Map<String, Supplier<Object>> pluginInstances) {
      this.perRenderPluginInstances = checkNotNull(pluginInstances);
      return this;
    }

    @Override
    public RendererImpl setData(Map<String, ?> record) {
      Preconditions.checkState(
          !dataSetInConstructor,
          "May not call setData on a Renderer created from a TemplateParams");

      this.data = SoyValueConverter.INSTANCE.newDictFromMap(checkNotNull(record));
      return this;
    }

    @Override
    public RendererImpl setActiveDelegatePackageSelector(Predicate<String> active) {
      this.activeDelegatePackages = checkNotNull(active);
      return this;
    }

    @Override
    public RendererImpl setCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      contextBuilder.withCssRenamingMap(cssRenamingMap);
      return this;
    }

    @Override
    public RendererImpl setXidRenamingMap(SoyIdRenamingMap xidRenamingMap) {
      contextBuilder.withXidRenamingMap(xidRenamingMap);
      return this;
    }

    @Override
    public RendererImpl setMsgBundle(SoyMsgBundle msgs) {
      this.msgs = checkNotNull(msgs);
      return this;
    }

    @Override
    public RendererImpl setDebugSoyTemplateInfo(boolean debugSoyTemplateInfo) {
      contextBuilder.withDebugSoyTemplateInfo(debugSoyTemplateInfo);
      return this;
    }

    @Override
    public RendererImpl setSoyLogger(SoyLogger logger) {
      this.logger = checkNotNull(logger);
      this.contextBuilder.hasLogger(true);
      return this;
    }

    @Override @Deprecated
    public Renderer setExpectedContentKind(ContentKind expectedContentKind) {
      checkNotNull(expectedContentKind);
      this.expectedContentKind = expectedContentKind;
      return this;
    }

    @Override
    public WriteContinuation renderHtml(AdvisingAppendable out) throws IOException {
      enforceContentKind(ContentKind.HTML);
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    public Continuation<SanitizedContent> renderHtml() {
      return renderSanitizedContent(ContentKind.HTML);
    }

    @Override
    public WriteContinuation renderJs(AdvisingAppendable out) throws IOException {
      enforceContentKind(ContentKind.JS);
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    public Continuation<SanitizedContent> renderJs() {
      return renderSanitizedContent(ContentKind.JS);
    }

    @Override
    public WriteContinuation renderUri(AdvisingAppendable out) throws IOException {
      enforceContentKind(ContentKind.URI);
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    public Continuation<SanitizedContent> renderUri() {
      return renderSanitizedContent(ContentKind.URI);
    }

    @Override
    public WriteContinuation renderTrustedResourceUri(AdvisingAppendable out) throws IOException {
      enforceContentKind(ContentKind.TRUSTED_RESOURCE_URI);
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    public Continuation<SanitizedContent> renderTrustedResourceUri() {
      return renderSanitizedContent(ContentKind.TRUSTED_RESOURCE_URI);
    }

    @Override
    public WriteContinuation renderAttributes(AdvisingAppendable out) throws IOException {
      enforceContentKind(ContentKind.ATTRIBUTES);
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    public Continuation<SanitizedContent> renderAttributes() {
      return renderSanitizedContent(ContentKind.ATTRIBUTES);
    }

    @Override
    public WriteContinuation renderCss(AdvisingAppendable out) throws IOException {
      enforceContentKind(ContentKind.CSS);
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    public Continuation<SanitizedContent> renderCss() {
      return renderSanitizedContent(ContentKind.CSS);
    }

    @Override
    public WriteContinuation renderText(AdvisingAppendable out) throws IOException {
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    public Continuation<String> renderText() {
      StringBuilder sb = new StringBuilder();
      OutputAppendable buf = OutputAppendable.create(sb, logger);
      try {
        return Continuations.stringContinuation(startRender(buf), sb);
      } catch (IOException e) {
        throw new AssertionError("impossible", e);
      }
    }

    @Override
    @Deprecated
    public WriteContinuation render(AdvisingAppendable out) throws IOException {
      enforceContentKind(expectedContentKind);
      return startRender(OutputAppendable.create(out, logger));
    }

    @Override
    @Deprecated
    public Continuation<String> render() {
      enforceContentKind(expectedContentKind);
      return renderText();
    }

    @Override
    @Deprecated
    public Continuation<SanitizedContent> renderStrict() {
      return renderSanitizedContent(expectedContentKind);
      // TODO(b/129547159): prevent calling renderStrict with ContentKind.TEXT
    }

    /**
     * Renders sanitized content, enforcing that the content matches the given {@link ContentKind}.
     */
    private Continuation<SanitizedContent> renderSanitizedContent(ContentKind contentKind) {
      enforceContentKind(contentKind);
      StringBuilder sb = new StringBuilder();
      OutputAppendable buf = OutputAppendable.create(sb, logger);
      try {
        return Continuations.strictContinuation(startRender(buf), sb, contentKind);
      } catch (IOException e) {
        throw new AssertionError("impossible", e);
      }
    }

    private <T> WriteContinuation startRender(OutputAppendable out) throws IOException {
      if (perRenderPluginInstances != null) {
        contextBuilder.withPluginInstances(
            ImmutableMap.<String, Supplier<Object>>builder()
                .putAll(SoySauceImpl.this.pluginInstances)
                .putAll(perRenderPluginInstances)
                .build());
      }
      RenderContext context =
          contextBuilder
              .withMessageBundle(msgs)
              .withActiveDelPackageSelector(activeDelegatePackages)
              .build();
      Scoper scoper =
          new Scoper(
              apiCallScope, BidiGlobalDir.forStaticIsRtl(msgs.isRtl()), msgs.getLocaleString());
      CompiledTemplate template = templateFactory.create(data, ij);
      return doRender(template, scoper, out, context);
    }

    private void enforceContentKind(ContentKind expectedContentKind) {
      if (expectedContentKind == ContentKind.TEXT) {
        // Allow any template to be called as text.
        return;
      }
      if (expectedContentKind != contentKind) {
        throw new IllegalStateException(
            "Expected template '"
                + templateName
                + "' to be kind=\""
                + Ascii.toLowerCase(expectedContentKind.name())
                + "\" but was kind=\""
                + Ascii.toLowerCase(contentKind.name())
                + "\"");
      }
    }
  }

  private static WriteContinuation doRender(
      CompiledTemplate template,
      Scoper scoper,
      LoggingAdvisingAppendable out,
      RenderContext context)
      throws IOException {
    RenderResult result;
    try (SoyScopedData.InScope scope = scoper.enter()) {
      result = template.render(out, context);
    } catch (Throwable t) {
      rewriteStackTrace(t);
      Throwables.throwIfInstanceOf(t, IOException.class);
      throw t;
    }
    if (result.isDone()) {
      return Continuations.done();
    }
    return new WriteContinuationImpl(result, scoper, context, out, template);
  }

  private static final class WriteContinuationImpl implements WriteContinuation {
    final RenderResult result;
    final Object lock = new Object();

    @GuardedBy("lock")
    final Scoper scoper;

    @GuardedBy("lock")
    final RenderContext context;

    @GuardedBy("lock")
    final LoggingAdvisingAppendable out;

    @GuardedBy("lock")
    final CompiledTemplate template;

    @GuardedBy("lock")
    boolean hasContinueBeenCalled;

    WriteContinuationImpl(
        RenderResult result,
        Scoper scoper,
        RenderContext context,
        LoggingAdvisingAppendable out,
        CompiledTemplate template) {
      checkArgument(!result.isDone());
      this.result = checkNotNull(result);
      this.scoper = checkNotNull(scoper);
      this.context = checkNotNull(context);
      this.out = checkNotNull(out);
      this.template = checkNotNull(template);
    }

    @Override
    public RenderResult result() {
      return result;
    }

    @Override
    public WriteContinuation continueRender() throws IOException {
      synchronized (lock) {
        if (hasContinueBeenCalled) {
          throw new IllegalStateException("continueRender() has already been called.");
        }
        hasContinueBeenCalled = true;
        return doRender(template, scoper, out, context);
      }
    }
  }

  private static final class Scoper {
    final SoyScopedData.Enterable scope;
    final BidiGlobalDir dir;
    final String localeString;

    Scoper(SoyScopedData.Enterable scope, BidiGlobalDir dir, String localeString) {
      this.scope = scope;
      this.dir = dir;
      this.localeString = localeString;
    }

    SoyScopedData.InScope enter() {
      return scope.enter(dir, localeString);
    }
  }
}
