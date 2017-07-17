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
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

/** Main entry point for rendering Soy templates on the server. */
public final class SoySauceImpl implements SoySauce {
  public static final class Factory {
    // TODO(lukes): switch all of soy to @AutoFactory when its opensource situation is cleaned up
    private final GuiceSimpleScope apiCallScopeProvider;
    private final Provider<SoyValueConverter> converterProvider;

    @Inject
    Factory(
        @ApiCall GuiceSimpleScope apiCallScopeProvider,
        Provider<SoyValueConverter> converterProvider) {
      this.apiCallScopeProvider = apiCallScopeProvider;
      this.converterProvider = converterProvider;
    }

    public SoySauceImpl create(
        CompiledTemplates templates,
        ImmutableMap<String, ? extends SoyFunction> soyFunctionMap,
        ImmutableMap<String, ? extends SoyPrintDirective> printDirectives) {
      // SoySauce has no need for SoyFunctions that are not SoyJavaFunctions
      // (it generates Java source code implementing BuiltinFunctions).
      // Filter them out.
      ImmutableMap.Builder<String, SoyJavaFunction> soyJavaFunctions = ImmutableMap.builder();
      for (Map.Entry<String, ? extends SoyFunction> entry : soyFunctionMap.entrySet()) {
        SoyFunction function = entry.getValue();
        if (function instanceof SoyJavaFunction) {
          soyJavaFunctions.put(entry.getKey(), (SoyJavaFunction) function);
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
      return new SoySauceImpl(
          templates,
          apiCallScopeProvider,
          converterProvider.get(),
          soyJavaFunctions.build(),
          soyJavaPrintDirectives.build());
    }
  }

  private final CompiledTemplates templates;
  private final GuiceSimpleScope apiCallScope;
  private final SoyValueConverter converter;
  private final ImmutableMap<String, SoyJavaFunction> functions;
  private final ImmutableMap<String, SoyJavaPrintDirective> printDirectives;

  private SoySauceImpl(
      CompiledTemplates templates,
      GuiceSimpleScope apiCallScope,
      SoyValueConverter converter,
      ImmutableMap<String, ? extends SoyJavaFunction> functions,
      ImmutableMap<String, ? extends SoyJavaPrintDirective> printDirectives) {
    this.templates = checkNotNull(templates);
    this.apiCallScope = checkNotNull(apiCallScope);
    this.converter = checkNotNull(converter);
    this.functions = ImmutableMap.copyOf(functions);
    this.printDirectives = ImmutableMap.copyOf(printDirectives);
  }

  @Override
  public ImmutableSortedSet<String> getTransitiveIjParamsForTemplate(String templateName) {
    return templates.getTransitiveIjParamsForTemplate(templateName);
  }

  @Override
  public RendererImpl renderTemplate(String template) {
    CompiledTemplate.Factory factory = templates.getTemplateFactory(template);
    return new RendererImpl(template, factory, templates.getTemplateContentKind(template));
  }

  private final class RendererImpl implements Renderer {
    private final String templateName;
    private final CompiledTemplate.Factory templateFactory;
    private final Optional<ContentKind> contentKind;
    private Predicate<String> activeDelegatePackages = Predicates.alwaysFalse();
    private SoyMsgBundle msgs = SoyMsgBundle.EMPTY;
    private final RenderContext.Builder contextBuilder =
        new RenderContext.Builder()
            .withCompiledTemplates(templates)
            .withSoyFunctions(functions)
            .withSoyPrintDirectives(printDirectives);

    private SoyRecord data = SoyValueConverter.EMPTY_DICT;
    private SoyRecord ij = SoyValueConverter.EMPTY_DICT;
    private ContentKind expectedContentKind = ContentKind.HTML;
    private boolean contentKindExplicitlySet;

    RendererImpl(
        String templateName,
        CompiledTemplate.Factory templateFactory,
        Optional<ContentKind> contentKind) {
      this.templateName = templateName;
      this.templateFactory = checkNotNull(templateFactory);
      this.contentKind = contentKind;
    }

    @Override
    public RendererImpl setIj(Map<String, ?> record) {
      this.ij = converter.newDictFromMap(checkNotNull(record));
      return this;
    }

    @Override
    public RendererImpl setData(Map<String, ?> record) {
      this.data = converter.newDictFromMap(checkNotNull(record));
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
    public Renderer setExpectedContentKind(ContentKind expectedContentKind) {
      checkNotNull(contentKind);
      this.contentKindExplicitlySet = true;
      this.expectedContentKind = expectedContentKind;
      return this;
    }

    @Override
    public WriteContinuation render(AdvisingAppendable out) throws IOException {
      if (contentKindExplicitlySet || contentKind.isPresent()) {
        enforceContentKind();
      }
      return startRender(out);
    }

    @Override
    public Continuation<String> render() {
      if (contentKindExplicitlySet || contentKind.isPresent()) {
        enforceContentKind();
      }
      AdvisingStringBuilder buf = new AdvisingStringBuilder();
      try {
        return Continuations.stringContinuation(startRender(buf), buf);
      } catch (IOException e) {
        throw new AssertionError("impossible", e);
      }
    }

    @Override
    public Continuation<SanitizedContent> renderStrict() {
      enforceContentKind();
      AdvisingStringBuilder buf = new AdvisingStringBuilder();
      try {
        return Continuations.strictContinuation(startRender(buf), buf, expectedContentKind);
      } catch (IOException e) {
        throw new AssertionError("impossible", e);
      }
    }

    private <T> WriteContinuation startRender(AdvisingAppendable out) throws IOException {
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

    private void enforceContentKind() {
      if (expectedContentKind == SanitizedContent.ContentKind.TEXT) {
        // Allow any template to be called as text. This is consistent with the fact that
        // kind="text" templates can call any other template.
        return;
      }
      if (!contentKind.isPresent()) {
        throw new IllegalStateException(
            "Cannot render a non strict template as '"
                + Ascii.toLowerCase(expectedContentKind.name())
                + "'");
      }
      if (expectedContentKind != contentKind.get()) {
        throw new IllegalStateException(
            "Expected template to be kind=\""
                + Ascii.toLowerCase(expectedContentKind.name())
                + "\" but was kind=\""
                + Ascii.toLowerCase(contentKind.get().name())
                + "\": "
                + templateName);
      }
    }
  }

  private static WriteContinuation doRender(
      CompiledTemplate template, Scoper scoper, AdvisingAppendable out, RenderContext context)
      throws IOException {
    RenderResult result;
    try (GuiceSimpleScope.InScope scope = scoper.enter()) {
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
    final Scoper scoper;
    final RenderContext context;
    final AdvisingAppendable out;
    final CompiledTemplate template;
    boolean hasContinueBeenCalled;

    WriteContinuationImpl(
        RenderResult result,
        Scoper scoper,
        RenderContext context,
        AdvisingAppendable out,
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
      if (hasContinueBeenCalled) {
        throw new IllegalStateException("continueRender() has already been called.");
      }
      hasContinueBeenCalled = true;
      return doRender(template, scoper, out, context);
    }
  }

  private static final class Scoper {
    final GuiceSimpleScope scope;
    final BidiGlobalDir dir;
    final String localeString;

    Scoper(GuiceSimpleScope scope, BidiGlobalDir dir, String localeString) {
      this.scope = scope;
      this.dir = dir;
      this.localeString = localeString;
    }

    GuiceSimpleScope.InScope enter() {
      // TODO(lukes): this isn't right, re-entering the scope shouldn't retrigger injection of
      // items, we need an explicit detach api.  This happens to be fine because these are the only
      // 2 keys available in this scope
      GuiceSimpleScope.InScope withScope = scope.enter();
      ApiCallScopeUtils.seedSharedParams(withScope, dir, localeString);
      return withScope;
    }
  }
}
