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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.shared.Names.rewriteStackTrace;

import com.google.common.base.Ascii;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyInjector;
import com.google.template.soy.data.SoyTemplate;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyCssTracker;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.SoyJsIdTracker;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** Main entry point for rendering Soy templates on the server. */
public final class SoySauceImpl implements SoySauce {
  private final CompiledTemplates templates;
  private final PluginInstances pluginInstances;
  private final ImmutableMap<String, SoyJavaPrintDirective> printDirectives;

  public SoySauceImpl(
      CompiledTemplates templates,
      ImmutableList<? extends SoyFunction> functions,
      ImmutableList<? extends SoyPrintDirective> printDirectives,
      PluginInstances pluginInstances) {
    this.templates = checkNotNull(templates);
    ImmutableMap.Builder<String, Supplier<Object>> pluginInstanceBuilder = ImmutableMap.builder();

    for (SoyFunction fn : functions) {
      if (fn instanceof SoyJavaFunction) {
        pluginInstanceBuilder.put(fn.getName(), Suppliers.ofInstance(fn));
      }
    }

    // SoySauce has no need for SoyPrintDirectives that are not SoyJavaPrintDirectives.
    // Filter them out.
    ImmutableMap.Builder<String, SoyJavaPrintDirective> soyJavaPrintDirectives =
        ImmutableMap.builder();
    for (SoyPrintDirective printDirective : printDirectives) {
      if (printDirective instanceof SoyJavaPrintDirective) {
        soyJavaPrintDirectives.put(
            printDirective.getName(), (SoyJavaPrintDirective) printDirective);
      }
    }
    this.printDirectives = soyJavaPrintDirectives.buildOrThrow();
    this.pluginInstances = pluginInstances.combine(pluginInstanceBuilder.buildOrThrow());
  }

  @Override
  public ImmutableSortedSet<String> getTransitiveIjParamsForTemplate(String templateName) {
    return templates.getTransitiveIjParamsForTemplate(templateName);
  }

  @Override
  public ImmutableList<String> getAllRequiredCssNamespaces(
      String templateName, Predicate<String> enabledMods, boolean collectCssFromDelvariants) {
    return templates.getAllRequiredCssNamespaces(
        templateName, enabledMods, collectCssFromDelvariants);
  }

  @Override
  public ImmutableList<String> getAllRequiredCssPaths(
      String templateName, Predicate<String> enabledMods, boolean collectCssFromDelvariants) {
    return templates.getAllRequiredCssPaths(templateName, enabledMods, collectCssFromDelvariants);
  }

  @Override
  public boolean hasTemplate(String template) {
    try {
      templates.getTemplate(template);
      return true;
    } catch (IllegalArgumentException iae) {
      return false;
    }
  }

  @Override
  public RendererImpl renderTemplate(String template) {
    CompiledTemplates.TemplateData data = templates.getTemplateData(template);
    return new RendererImpl(template, data.template(), data.kind(), /* data=*/ null);
  }

  @Override
  public RendererImpl newRenderer(SoyTemplate params) {
    String template = params.getTemplateName();
    CompiledTemplates.TemplateData data = templates.getTemplateData(template);
    // getParamsAsMap has a loose type to fix a build cycle.
    var typedParams = (ParamStore) params.getParamsAsRecord();
    return new RendererImpl(template, data.template(), data.kind(), typedParams);
  }

  final class RendererImpl implements Renderer {
    private final String templateName;
    private final CompiledTemplate template;
    private final ContentKind contentKind;
    private Predicate<String> activeModSelector;
    private SoyCssRenamingMap cssRenamingMap;
    private SoyIdRenamingMap xidRenamingMap;
    private PluginInstances pluginInstances = SoySauceImpl.this.pluginInstances;
    private SoyMsgBundle msgBundle;
    private boolean debugSoyTemplateInfo;
    private SoyLogger logger;
    private SoyCssTracker cssTracker;
    private SoyJsIdTracker jsIdTracker;

    private ParamStore data;
    private SoyInjector ij;
    private boolean dataSetInConstructor;

    RendererImpl(
        String templateName,
        CompiledTemplate template,
        ContentKind contentKind,
        @Nullable ParamStore data) {
      this.templateName = templateName;
      this.template = checkNotNull(template);
      this.contentKind = contentKind;
      if (data != null) {
        this.data = data;
        // TODO(lukes): eliminate this and just use the nullness of data to enforce this.
        this.dataSetInConstructor = true;
      }
    }

    private RenderContext makeContext() {
      return new RenderContext(
          templates,
          printDirectives,
          pluginInstances,
          ij,
          activeModSelector,
          cssRenamingMap,
          xidRenamingMap,
          msgBundle,
          debugSoyTemplateInfo,
          cssTracker,
          jsIdTracker);
    }

    private ParamStore mapAsParamStore(Map<String, ?> source) {
      var params = new ParamStore(source.size());
      for (Map.Entry<String, ?> entry : source.entrySet()) {
        String key = entry.getKey();
        SoyValueProvider value;
        try {
          value = SoyValueConverter.INSTANCE.convert(entry.getValue());
        } catch (RuntimeException e) {
          throw new IllegalArgumentException(
              "Unable to convert param " + key + " to a SoyValue", e);
        }
        params.setField(RecordProperty.get(key), value);
      }
      return params.freeze();
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setIj(SoyInjector ij) {
      this.ij = checkNotNull(ij);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setPluginInstances(
        Map<String, ? extends Supplier<Object>> pluginInstances) {
      this.pluginInstances = SoySauceImpl.this.pluginInstances.combine(pluginInstances);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setData(Map<String, ?> record) {
      checkState(
          !dataSetInConstructor,
          "May not call setData on a Renderer created from a TemplateParams");

      this.data = mapAsParamStore(record);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setActiveModSelector(Predicate<String> active) {
      this.activeModSelector = checkNotNull(active);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      this.cssRenamingMap = checkNotNull(cssRenamingMap);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setXidRenamingMap(SoyIdRenamingMap xidRenamingMap) {
      this.xidRenamingMap = checkNotNull(xidRenamingMap);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setMsgBundle(SoyMsgBundle msgs) {
      this.msgBundle = checkNotNull(msgs);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setDebugSoyTemplateInfo(boolean debugSoyTemplateInfo) {
      this.debugSoyTemplateInfo = debugSoyTemplateInfo;
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setSoyLogger(SoyLogger logger) {
      this.logger = checkNotNull(logger);
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setCssTracker(SoyCssTracker cssTracker) {
      this.cssTracker = cssTracker;
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public RendererImpl setJsIdTracker(SoyJsIdTracker jsIdTracker) {
      this.jsIdTracker = jsIdTracker;
      return this;
    }

    @Override
    public WriteContinuation renderHtml(AdvisingAppendable out) throws IOException {
      return startRender(out, ContentKind.HTML);
    }

    @Override
    public WriteContinuation renderHtml(Appendable out) throws IOException {
      return startRender(out, ContentKind.HTML);
    }

    @Override
    public Continuation<SanitizedContent> renderHtml() {
      return startRenderToSanitizedContent(ContentKind.HTML);
    }

    @Override
    public WriteContinuation renderJs(AdvisingAppendable out) throws IOException {
      return startRender(out, ContentKind.JS);
    }

    @Override
    public WriteContinuation renderJs(Appendable out) throws IOException {
      return startRender(out, ContentKind.JS);
    }

    @Override
    public Continuation<SanitizedContent> renderJs() {
      return startRenderToSanitizedContent(ContentKind.JS);
    }

    @Override
    public WriteContinuation renderUri(AdvisingAppendable out) throws IOException {
      return startRender(out, ContentKind.URI);
    }

    @Override
    public WriteContinuation renderUri(Appendable out) throws IOException {
      return startRender(out, ContentKind.URI);
    }

    @Override
    public Continuation<SanitizedContent> renderUri() {
      return startRenderToSanitizedContent(ContentKind.URI);
    }

    @Override
    public WriteContinuation renderTrustedResourceUri(AdvisingAppendable out) throws IOException {
      return startRender(out, ContentKind.TRUSTED_RESOURCE_URI);
    }

    @Override
    public WriteContinuation renderTrustedResourceUri(Appendable out) throws IOException {
      return startRender(out, ContentKind.TRUSTED_RESOURCE_URI);
    }

    @Override
    public Continuation<SanitizedContent> renderTrustedResourceUri() {
      return startRenderToSanitizedContent(ContentKind.TRUSTED_RESOURCE_URI);
    }

    @Override
    public WriteContinuation renderAttributes(AdvisingAppendable out) throws IOException {
      return startRender(out, ContentKind.ATTRIBUTES);
    }

    @Override
    public WriteContinuation renderAttributes(Appendable out) throws IOException {
      return startRender(out, ContentKind.ATTRIBUTES);
    }

    @Override
    public Continuation<SanitizedContent> renderAttributes() {
      return startRenderToSanitizedContent(ContentKind.ATTRIBUTES);
    }

    @Override
    public WriteContinuation renderCss(AdvisingAppendable out) throws IOException {
      return startRender(out, ContentKind.CSS);
    }

    @Override
    public WriteContinuation renderCss(Appendable out) throws IOException {
      return startRender(out, ContentKind.CSS);
    }

    @Override
    public Continuation<SanitizedContent> renderCss() {
      return startRenderToSanitizedContent(ContentKind.CSS);
    }

    @Override
    public WriteContinuation renderText(AdvisingAppendable out) throws IOException {
      return startRender(out, ContentKind.TEXT);
    }

    @Override
    public WriteContinuation renderText(Appendable out) throws IOException {
      return startRender(out, ContentKind.TEXT);
    }

    @Override
    public Continuation<String> renderText() {
      return startRenderToValue(ContentKind.TEXT);
    }

    private Continuation<SanitizedContent> startRenderToSanitizedContent(ContentKind kind) {
      enforceContentKind(kind);
      return startRenderToValue(kind);
    }

    private < T>
        Continuation<T> startRenderToValue(ContentKind contentKind) {
      StringBuilder sb = new StringBuilder();
      ParamStore params = data == null ? ParamStore.EMPTY_INSTANCE : data;
      RenderContext context = makeContext();
      OutputAppendable output = OutputAppendable.create(sb, logger);
      return doRenderToValue(contentKind, sb, template, null, params, output, context);
    }

    private WriteContinuation startRender(Appendable out, ContentKind contentKind)
        throws IOException {
      enforceContentKind(contentKind);

      ParamStore params = data == null ? ParamStore.EMPTY_INSTANCE : data;
      RenderContext context = makeContext();
      OutputAppendable output = OutputAppendable.create(out, logger);
      return doRender(template, null, params, output, context);
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
      @Nullable StackFrame frame,
      ParamStore params,
      OutputAppendable output,
      RenderContext context)
      throws IOException {
    try {
      frame = template.render(frame, params, output, context);
    } catch (Throwable t) {
      context.suppressDeferredErrorsOnto(t);
      rewriteStackTrace(t);
      Throwables.throwIfInstanceOf(t, IOException.class);
      throw t;
    }
    if (frame == null) {
      context.logDeferredErrors();
      return Continuations.done();
    }
    return new WriteContinuationImpl(template, frame, params, output, context);
  }

  abstract static class ContinuationImpl {
    static final VarHandle HAS_CONTINUE_CALLED_HANDLE;

    static {
      try {
        HAS_CONTINUE_CALLED_HANDLE =
            MethodHandles.lookup()
                .findVarHandle(ContinuationImpl.class, "hasContinueBeenCalled", boolean.class);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("impossible", e);
      }
    }

    // ThreadSafety guaranteed by the guarded write on hasContinueBeenCalled
    final CompiledTemplate template;
    final StackFrame frame;
    final ParamStore params;
    final OutputAppendable output;
    final RenderContext context;

    boolean hasContinueBeenCalled;

    // implements the result() method on the Continuation and WriteContinuation interfaces
    public RenderResult result() {
      return frame.asRenderResult();
    }

    ContinuationImpl(
        CompiledTemplate template,
        StackFrame frame,
        ParamStore params,
        OutputAppendable output,
        RenderContext context) {
      this.template = checkNotNull(template);
      this.frame = checkNotNull(frame);
      this.params = checkNotNull(params);
      this.output = checkNotNull(output);
      this.context = checkNotNull(context);
    }

    void doContinue() {
      if (!HAS_CONTINUE_CALLED_HANDLE.compareAndSet(this, false, true)) {
        throw new IllegalStateException("continueRender() has already been called.");
      }
    }
  }

  private static final class WriteContinuationImpl extends ContinuationImpl
      implements WriteContinuation {

    WriteContinuationImpl(
        CompiledTemplate template,
        StackFrame frame,
        ParamStore params,
        OutputAppendable output,
        RenderContext context) {
      super(template, frame, params, output, context);
    }

    @Override
    public WriteContinuation continueRender() throws IOException {
      doContinue();
      return doRender(template, frame, params, output, context);
    }
  }

  private static < T>
      Continuation<T> doRenderToValue(
          ContentKind targetKind,
          StringBuilder underlying,
          CompiledTemplate template,
          @Nullable StackFrame frame,
          ParamStore params,
          OutputAppendable output,
          RenderContext context) {

    try {
      frame = template.render(frame, params, output, context);
    } catch (IOException t) {
      throw new AssertionError("impossible", t);
    } catch (Throwable t) {
      context.suppressDeferredErrorsOnto(t);
      rewriteStackTrace(t);
      throw t;
    }
    if (frame == null) {
      context.logDeferredErrors();
      String content = underlying.toString();
      if (targetKind == ContentKind.TEXT) {
        // these casts are lame, the way to resolve is simply to fork this method
        // based on String vs SanitizedContent
        @SuppressWarnings("unchecked")
        Continuation<T> c = (Continuation) Continuations.done(content);
        return c;
      }
      @SuppressWarnings("unchecked")
      Continuation<T> c =
          (Continuation)
              Continuations.done(UnsafeSanitizedContentOrdainer.ordainAsSafe(content, targetKind));
      return c;
    }
    return new ValueContinuationImpl<T>(
        targetKind, underlying, template, frame, params, output, context);
  }

  private static final class ValueContinuationImpl<
          T>
      extends ContinuationImpl implements Continuation<T> {

    final ContentKind targetKind;

    final StringBuilder underlying;

    ValueContinuationImpl(
        ContentKind targetKind,
        StringBuilder underlying,
        CompiledTemplate template,
        StackFrame frame,
        ParamStore params,
        OutputAppendable output,
        RenderContext context) {
      super(template, frame, params, output, context);
      this.targetKind = checkNotNull(targetKind);
      this.underlying = checkNotNull(underlying);
    }

    @Override
    public T get() {
      throw new IllegalStateException("Rendering is not complete: " + result());
    }

    @Override
    public Continuation<T> continueRender() {
      doContinue();
      return doRenderToValue(targetKind, underlying, template, frame, params, output, context);
    }
  }
}
