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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.MsgPartUtils;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.ApiCallScopeUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope.WithScope;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.tofu.internal.TofuModule.Tofu;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Main entry point for rendering Soy templates on the server.
 */
public final class SoySauceImpl implements SoySauce {
  public static final class Factory {
    // TODO(lukes): switch all of soy to @AutoFactory when its opensource situation is cleaned up
    private final GuiceSimpleScope apiCallScopeProvider;
    private final Provider<SoyValueHelper> converterProvider;
    private final Provider<Map<String, SoyJavaFunction>> functionsProvider;
    private final Provider<Map<String, SoyJavaPrintDirective>> printDirectivesProvider;

    @Inject Factory(
        @ApiCall GuiceSimpleScope apiCallScopeProvider, 
        Provider<SoyValueHelper> converterProvider,
        // TODO(lukes): we rely on the @Tofu bindings here for compatibility with servers using
        // SoyTofuFunction/SoyTofuPrintDirective.  Those interfaces need to be deleted and the 
        // adapters provided by these bindings removed
        @Tofu Provider<Map<String, SoyJavaFunction>> functionsProvider, 
        @Tofu Provider<Map<String, SoyJavaPrintDirective>> printDirectivesProvider) {
      this.apiCallScopeProvider = apiCallScopeProvider;
      this.converterProvider = converterProvider;
      this.functionsProvider = functionsProvider;
      this.printDirectivesProvider = printDirectivesProvider;
    }

    public SoySauceImpl create(
        CompiledTemplates templates, 
        TemplateRegistry registry, 
        SoyMsgBundle defaultMsgBundle, 
        SetMultimap<String, String> templateToTransitiveUsedIjParams) {
      return new SoySauceImpl(templates, registry, defaultMsgBundle,
          templateToTransitiveUsedIjParams, apiCallScopeProvider, converterProvider.get(),
          functionsProvider.get(), printDirectivesProvider.get());
    }
  }
  
  private static final RenderContinuation DONE_CONTINUATION = new RenderContinuation() {
    @Override public RenderResult result() {
      return RenderResult.done();
    }

    @Override public RenderContinuation continueRender() {
      throw new IllegalStateException("Rendering is already complete and cannot be continued");
    }
  };

  private final CompiledTemplates templates;
  private final SoyMsgBundle defaultMsgBundle;
  private final GuiceSimpleScope apiCallScope;
  private final DelTemplateSelectorImpl.Factory factory;
  private final SoyValueHelper converter;
  private final ImmutableMap<String, SoyJavaFunction> functions;
  private final ImmutableMap<String, SoyJavaPrintDirective> printDirectives;
  private final ImmutableSetMultimap<String, String> templateToTransitiveUsedIjParams;

  private SoySauceImpl(
      CompiledTemplates templates,
      TemplateRegistry registry,
      SoyMsgBundle defaultMsgBundle,
      SetMultimap<String, String> templateToTransitiveUsedIjParams,
      GuiceSimpleScope apiCallScope,
      SoyValueHelper converter,
      Map<String, SoyJavaFunction> functions,
      Map<String, SoyJavaPrintDirective> printDirectives) {
    this.templates = checkNotNull(templates);
    this.defaultMsgBundle = replaceLocale(defaultMsgBundle);
    this.templateToTransitiveUsedIjParams = 
        ImmutableSetMultimap.copyOf(templateToTransitiveUsedIjParams);  
    this.apiCallScope = checkNotNull(apiCallScope);
    this.converter = checkNotNull(converter);
    this.functions = ImmutableMap.copyOf(functions);
    this.printDirectives = ImmutableMap.copyOf(printDirectives);
    
    this.factory = new DelTemplateSelectorImpl.Factory(registry, templates);
  }

  // Currently the defaultBundle is coming straight from extractMsgs which means the SoyMsgs are
  // not associated with a locale, however a locale is needed for plurals support.  To compensate
  // we just apply 'en' here.
  // TODO(lukes): technically we don't know that the template author is writing in english (or even
  // an LTR language).  Normally, this is provided as a flag to the msg extractor.  consider adding
  // an explicit flag to the runtime, or allowing the user to pass an explicit default bundle (with
  // an associated locale).  Note, Tofu avoids this problem by having 2 implementations of msg 
  // rendering. 1 that uses the bundle and one that walks the AST directly.  To compensate for a
  // lack of locale the AST implementation doesn't use complex plural rules.  Consider just changing
  // the msg renderer for jbcsrc to deal with missing locale information 'gracefully'.
  private SoyMsgBundle replaceLocale(SoyMsgBundle input) {
    ImmutableList.Builder<SoyMsg> builder = ImmutableList.builder();
    for (SoyMsg msg : input) {
      builder.add(new SoyMsg(msg.getId(), "en", 
          MsgPartUtils.hasPlrselPart(msg.getParts()), msg.getParts()));
    }
    return new SoyMsgBundleImpl("en", builder.build());
  }

  @Override public ImmutableSet<String> getTransitiveIjParamsForTemplate(
      SoyTemplateInfo templateInfo) {
    return templateToTransitiveUsedIjParams.get(templateInfo.getName());
  }

  @Override public RendererImpl renderTemplate(SoyTemplateInfo template) {
    CompiledTemplate.Factory factory = templates.getTemplateFactory(template.getName());
    return new RendererImpl(factory);
  }

  private final class RendererImpl implements Renderer {
    private final CompiledTemplate.Factory templateFactory;
    private ImmutableSet<String> activeDelegatePackages = ImmutableSet.of();
    private SoyMsgBundle msgs = SoyMsgBundle.EMPTY;
    private final RenderContext.Builder contextBuilder = new RenderContext.Builder()
        .withSoyFunctions(functions)
        .withSoyPrintDirectives(printDirectives)
        .withConverter(converter);
    private SoyRecord data = SoyValueHelper.EMPTY_DICT;
    // Unlike Tofu we default to empty, not null.
    private SoyRecord ij = SoyValueHelper.EMPTY_DICT;

    RendererImpl(CompiledTemplate.Factory templateFactory) {
      this.templateFactory = checkNotNull(templateFactory);
    }

    @Override public RendererImpl setIj(Map<String, ?> record) {
      this.ij = (SoyRecord) converter.convert(checkNotNull(record));
      return this;
    }

    @Override public RendererImpl setData(Map<String, ?> record) {
      this.data = (SoyRecord) converter.convert(checkNotNull(record));
      return this;
    }

    @Override public RendererImpl setActiveDelegatePackageNames(
        Set<String> activeDelegatePackages) {
      this.activeDelegatePackages = ImmutableSet.copyOf(activeDelegatePackages);
      return this;
    }

    @Override public RendererImpl setCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      contextBuilder.withCssRenamingMap(cssRenamingMap);
      return this;
    }

    @Override public RendererImpl setXidRenamingMap(SoyIdRenamingMap xidRenamingMap) {
      contextBuilder.withXidRenamingMap(xidRenamingMap);
      return this;
    }

    @Override public RendererImpl setMsgBundle(SoyMsgBundle msgs) {
      this.msgs = checkNotNull(msgs);
      return this;
    }

    @Override public RenderContinuation render(AdvisingAppendable out) throws IOException {
      RenderContext context = contextBuilder
          .withMessageBundles(msgs, defaultMsgBundle)
          .withTemplateSelector(factory.create(activeDelegatePackages))
          .build();
      BidiGlobalDir dir = BidiGlobalDir.forStaticLocale(msgs.getLocaleString());
      Scoper scoper = new Scoper(apiCallScope, dir, msgs.getLocaleString());
      CompiledTemplate template = templateFactory.create(data, ij);
      return doRender(template, scoper, out, context);
    }
  }

  private static RenderContinuation doRender(
      CompiledTemplate template, Scoper scoper, AdvisingAppendable out, RenderContext context) 
          throws IOException {
    RenderResult result;
    try (WithScope scope = scoper.enter()) {
      result = template.render(out, context);
    }
    if (result.isDone()) {
      return DONE_CONTINUATION;
    }
    return new Continuation(result, scoper, context, out, template);
  }

  private static final class Continuation implements RenderContinuation {
    final RenderResult result;
    final Scoper scoper;
    final RenderContext context;
    final AdvisingAppendable out;
    final CompiledTemplate template;
    boolean hasContinueBeenCalled;

    Continuation(RenderResult result, Scoper scoper, RenderContext context, AdvisingAppendable out,
        CompiledTemplate template) {
      this.result = checkNotNull(result);
      this.scoper = checkNotNull(scoper);
      this.context = checkNotNull(context);
      this.out = checkNotNull(out);
      this.template = checkNotNull(template);
    }

    @Override public RenderResult result() {
      return result;
    }

    @Override public RenderContinuation continueRender() throws IOException {
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

    WithScope enter() {
      WithScope withScope = scope.enter();
      ApiCallScopeUtils.seedSharedParams(scope, dir, localeString);
      return withScope;
    }
  }
}
