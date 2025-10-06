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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Keep;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.SoyInjector;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates.TemplateData;
import com.google.template.soy.logging.LoggableElementMetadata;
import com.google.template.soy.msgs.GrammaticalGender;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsgRawParts;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.plugin.java.RenderCssHelper;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyCssTracker;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.SoyJsIdTracker;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A collection of contextual rendering data. Each top level rendering operation will obtain a
 * single instance of this object and it will be propagated throughout the render tree.
 */
public final class RenderContext {
  // TODO(lukes):  within this object most of these fields are constant across all renders while
  // some are expected to change frequently (the renaming maps, msgBundle and activeModSelector).
  // Consider splitting this into two objects to represent the changing lifetimes.  We are kind of
  // doing this now by having SoySauceImpl reuse the Builder, but this is a little strange and could
  // be theoretically made more efficient to construct.

  private final Predicate<String> activeModSelector;
  private final CompiledTemplates templates;
  private final SoyCssRenamingMap cssRenamingMap;
  private final SoyIdRenamingMap xidRenamingMap;
  private final PluginInstances pluginInstances;
  private final ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap;
  private final SoyInjector ijData;

  /** The bundle of translated messages */
  private final SoyMsgBundle msgBundle;

  private final SoyCssTracker cssTracker;
  private final SoyJsIdTracker jsIdTracker;
  private final ContextStore contextStore = new ContextStore();

  /**
   * Stores memoized {const} values, which in SSR are actually request-scoped values, not Java
   * static values.
   *
   * <p>Lazily initialized when setting the first const variable
   */
  private IdentityHashMap<String, Object> constValues;

  private final boolean debugSoyTemplateInfo;

  private List<ThrowingSoyValueProvider> deferredErrors;

  @Keep private GrammaticalGender viewerGrammaticalGender = GrammaticalGender.UNSPECIFIED;

  public RenderContext(
      CompiledTemplates templates,
      ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap,
      PluginInstances pluginInstances,
      SoyInjector ijData,
      @Nullable Predicate<String> activeModSelector,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyMsgBundle msgBundle,
      boolean debugSoyTemplateInfo,
      @Nullable SoyCssTracker cssTracker,
      @Nullable SoyJsIdTracker jsIdTracker,
      GrammaticalGender viewerGrammaticalGender) {
    this.templates = templates;
    this.soyJavaDirectivesMap = soyJavaDirectivesMap;
    this.pluginInstances = pluginInstances;
    this.ijData = ijData == null ? SoyInjector.EMPTY : ijData;
    this.activeModSelector = activeModSelector != null ? activeModSelector : mod -> false;
    this.cssRenamingMap = cssRenamingMap == null ? SoyCssRenamingMap.EMPTY : cssRenamingMap;
    this.xidRenamingMap = xidRenamingMap == null ? SoyCssRenamingMap.EMPTY : xidRenamingMap;
    this.msgBundle = msgBundle == null ? SoyMsgBundle.EMPTY : msgBundle;
    this.debugSoyTemplateInfo = debugSoyTemplateInfo;
    this.cssTracker = cssTracker;
    this.jsIdTracker = jsIdTracker;
    this.viewerGrammaticalGender = viewerGrammaticalGender;
    verify(viewerGrammaticalGender != null);
  }

  @Nullable
  public ULocale getLocale() {
    return msgBundle.getLocale();
  }

  public RenderCssHelper getRenderCssHelper() {
    return (delTemplate, variant) -> {
      TemplateData data =
          templates.selector.selectTemplate(delTemplate, variant, activeModSelector);
      return data != null ? data.soyTemplateName : null;
    };
  }

  public ImmutableList<String> getAllRequiredCssNamespaces(String template) {
    return templates.getAllRequiredCssNamespaces(template, activeModSelector, false);
  }

  public ImmutableList<String> getAllRequiredCssPaths(String template) {
    return templates.getAllRequiredCssPaths(template, activeModSelector, false);
  }

  public BidiGlobalDir getBidiGlobalDir() {
    return BidiGlobalDir.forStaticIsRtl(msgBundle.isRtl());
  }

  public Dir getBidiGlobalDirDir() {
    return getBidiGlobalDir().toDir();
  }

  @Nonnull
  public String renameCssSelector(String selector) {
    String string = cssRenamingMap.get(selector);
    if (string == null) {
      string = Preconditions.checkNotNull(selector);
    }
    if (cssTracker != null) {
      cssTracker.trackRequiredCssSelector(string);
    }
    return string;
  }

  @Nonnull
  @CanIgnoreReturnValue
  public String recordJsId(String xid) {
    if (jsIdTracker != null) {
      jsIdTracker.trackJsXid(xid);
    }
    return xid;
  }

  @Nonnull
  @CanIgnoreReturnValue
  public String renameXidAndRecordJsId(String rawId) {
    String xid = renameXid(rawId);
    if (jsIdTracker != null) {
      jsIdTracker.trackRawJsId(/* rawId= */ rawId, /* xid= */ xid);
    }
    return xid;
  }

  public boolean evalToggle(String toggleName) {
    return activeModSelector.test(toggleName);
  }

  @Nonnull
  public String renameXid(String id) {
    String string = xidRenamingMap.get(id);
    return string == null ? id + "_" : string;
  }

  public Object getPluginInstance(String name) {
    Supplier<Object> instanceSupplier = pluginInstances.get(name);
    if (instanceSupplier == null) {
      // This is the path a user will hit if they call JavaValueFactory.callInstanceMethod without
      // having supplied a runtime for that function.
      throw new MissingPluginInstanceException(
          name, String.format("No plugin instance registered for function with name '%s'.", name));
    }
    return instanceSupplier.get();
  }

  public SoyJavaPrintDirective getPrintDirective(String name) {
    SoyJavaPrintDirective printDirective = soyJavaDirectivesMap.get(name);
    if (printDirective == null) {
      throw new IllegalStateException(
          "Failed to find Soy print directive with name '" + name + "'");
    }
    return printDirective;
  }

  public Function<String, String> getEscapingDirectiveAsFunction(String name) {
    SoyJavaPrintDirective printDirective = soyJavaDirectivesMap.get(name);
    if (printDirective == null) {
      throw new IllegalStateException(
          "Failed to find Soy print directive with name '" + name + "'");
    }
    if (!printDirective.getValidArgsSizes().contains(0)) {
      throw new IllegalStateException(
          "Soy print directive with name '" + name + "' is not an escaping directive");
    }
    // TODO(lukes): this adapter is lame.  there should just be a way to get the print directive to
    // hand us an escaper or a function rather than writing this adapter.
    return input ->
        printDirective.applyForJava(StringData.forValue(input), ImmutableList.of()).stringValue();
  }

  /**
   * Returns a boolean that is used by other parts of the compiler. In particular, if this returns
   * true, Soy compiler will render additional HTML comments for runtime inspections (debug only).
   */
  public boolean getDebugSoyTemplateInfo() {
    return debugSoyTemplateInfo;
  }

  public CompiledTemplate getTemplate(String calleeName) {
    return templates.getTemplate(calleeName);
  }

  CompiledTemplates getTemplates() {
    return templates;
  }

  public CompiledTemplate getDelTemplate(String calleeName, String variant) {
    CompiledTemplate callee = templates.selectDelTemplate(calleeName, variant, activeModSelector);
    if (callee == null) {
      throw new IllegalArgumentException(
          "Found no active impl for delegate call to \""
              + calleeName
              + (variant.isEmpty() ? "" : ":" + variant)
              + "\".");
    }
    return callee;
  }

  public StackFrame renderModifiable(
      String delCalleeName,
      StackFrame frame,
      ParamStore params,
      LoggingAdvisingAppendable appendable)
      throws IOException {
    SoyValueProvider value = params.getFieldProvider(Names.VARIANT_VAR_PROPERTY);
    String variant;
    if (value == null) {
      variant = "";
    } else {
      // This is always fully resolved by our caller
      variant = ((SoyValue) value).coerceToString();
    }
    CompiledTemplate template = getDelTemplate(delCalleeName, variant);
    return template.render(frame, params, appendable, this);
  }

  /** Returns {@code true} if the primary msg should be used instead of the fallback. */
  public boolean usePrimaryMsgIfFallback(long msgId, long fallbackId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use hasMsg() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return msgBundle.hasMsg(msgId) || !msgBundle.hasMsg(fallbackId);
  }

  /**
   * Returns {@code true} if the primary or alternate msg should be used instead of the fallback.
   */
  public boolean usePrimaryOrAlternateIfFallback(long msgId, long alternateId, long fallbackId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use hasMsg() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return msgBundle.hasMsg(msgId)
        || msgBundle.hasMsg(alternateId)
        || !msgBundle.hasMsg(fallbackId);
  }

  /**
   * Returns {@code true} if the primary msg should be used instead of the fallback or the fallback
   * alternate.
   */
  public boolean usePrimaryIfFallbackOrFallbackAlternate(
      long msgId, long fallbackId, long fallbackAlternateId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use hasMsg() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return msgBundle.hasMsg(msgId)
        || (!msgBundle.hasMsg(fallbackId) && !msgBundle.hasMsg(fallbackAlternateId));
  }

  /**
   * Returns {@code true} if the primary or alternate msg should be used instead of the fallback or
   * the fallback alternate.
   */
  public boolean usePrimaryOrAlternateIfFallbackOrFallbackAlternate(
      long msgId, long alternateId, long fallbackId, long fallbackAlternateId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use hasMsg() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return msgBundle.hasMsg(msgId)
        || msgBundle.hasMsg(alternateId)
        || (!msgBundle.hasMsg(fallbackId) && !msgBundle.hasMsg(fallbackAlternateId));
  }

  /**
   * Returns the {@link SoyMsg} associated with the {@code msgId} or the fallback (aka english)
   * translation if there is no such message.
   */
  public SoyMsgRawParts getSoyMsgParts(long msgId, SoyMsgRawParts defaultMsgParts) {
    SoyMsgRawParts msgParts = msgBundle.getMsgPartsForRendering(msgId, viewerGrammaticalGender);
    if (msgParts == null) {
      return defaultMsgParts;
    }
    return msgParts;
  }

  /**
   * Returns the {@link SoyMsg} associated with the {@code msgId} or throws if there is no such
   * message.
   */
  public SoyMsgRawParts getSoyMsgParts(long msgId) {
    SoyMsgRawParts msgParts = msgBundle.getMsgPartsForRendering(msgId, viewerGrammaticalGender);
    if (msgParts == null) {
      throw new AssertionError();
    }
    return msgParts;
  }

  /**
   * Returns the {@link SoyMsg} associated with the {@code msgId}, the {@code alternateId} or the
   * fallback (aka english) translation if there is no such message.
   */
  public SoyMsgRawParts getSoyMsgPartsWithAlternateId(
      long msgId, SoyMsgRawParts defaultMsgParts, long alternateId) {
    SoyMsgRawParts msgParts = msgBundle.getMsgPartsForRendering(msgId, viewerGrammaticalGender);
    if (msgParts == null) {
      SoyMsgRawParts msgPartsByAlternateId =
          msgBundle.getMsgPartsForRendering(alternateId, viewerGrammaticalGender);
      if (msgPartsByAlternateId == null) {
        return defaultMsgParts;
      }
      return msgPartsByAlternateId;
    }
    return msgParts;
  }

  /**
   * Returns the {@link SoyMsg} associated with the {@code msgId}, the {@code alternateId} or throws
   * if there is no such message.
   */
  public SoyMsgRawParts getSoyMsgPartsWithAlternateId(long msgId, long alternateId) {
    SoyMsgRawParts msgParts = msgBundle.getMsgPartsForRendering(msgId, viewerGrammaticalGender);
    if (msgParts == null) {
      SoyMsgRawParts msgPartsByAlternateId =
          msgBundle.getMsgPartsForRendering(alternateId, viewerGrammaticalGender);
      if (msgPartsByAlternateId == null) {
        throw new AssertionError();
      }
      return msgPartsByAlternateId;
    }
    return msgParts;
  }

  public String getBasicSoyMsgPart(long msgId, String defaultPart) {
    String translation = msgBundle.getBasicTranslation(msgId, viewerGrammaticalGender);
    return translation == null ? defaultPart : translation;
  }

  public String getBasicSoyMsgPart(long msgId) {
    return msgBundle.getBasicTranslation(msgId, viewerGrammaticalGender);
  }

  public String getBasicSoyMsgPartWithAlternateId(
      long msgId, String defaultPart, long alternateId) {
    String translation = msgBundle.getBasicTranslation(msgId, viewerGrammaticalGender);
    if (translation == null) {
      translation = msgBundle.getBasicTranslation(alternateId, viewerGrammaticalGender);
      if (translation == null) {
        return defaultPart;
      }
    }
    return translation;
  }

  public String getBasicSoyMsgPartWithAlternateId(long msgId, long alternateId) {
    String translation = msgBundle.getBasicTranslation(msgId, viewerGrammaticalGender);
    if (translation == null) {
      translation = msgBundle.getBasicTranslation(alternateId, viewerGrammaticalGender);
      if (translation == null) {
        throw new AssertionError();
      }
    }
    return translation;
  }

  /**
   * Returns the VE metadata in the given class with the given method name. This uses the same
   * ClassLoader as is used to load template references.
   */
  public LoggableElementMetadata getVeMetadata(String metadataClassName, long veId) {
    try {
      return (LoggableElementMetadata)
          Class.forName(metadataClassName, /* initialize= */ true, templates.getClassLoader())
              .getMethod("getMetadata", long.class)
              .invoke(null, veId);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  @Nullable
  public Object getConst(String key) {
    var local = constValues;
    return local == null ? null : local.get(key);
  }

  public void storeConst(String key, Object value) {
    Preconditions.checkNotNull(value);
    var local = constValues;
    if (local == null) {
      local = new IdentityHashMap<>();
      constValues = local;
    }
    Object lastValue = local.put(key, value);
    Preconditions.checkArgument(lastValue == null, "Cannot overwrite value %s", key);
  }

  /**
   * Aggregate parameter object.
   *
   * <p>The gencode produces these as constants.
   */
  public static final class CssToTrack {
    // store arrays since they are faster to iterate over
    final String[] cssPaths;
    final String[] cssNamespaces;

    // this is simpler and faster for our condy bootstrap. See ExtraConstantBootstraps.
    @SuppressWarnings("AvoidObjectArrays")
    public CssToTrack(String[] cssPaths, String[] cssNamespaces) {
      this.cssPaths = cssPaths;
      this.cssNamespaces = cssNamespaces;
    }
  }

  /**
   * Reports that a CSS path was required by a file used during rendering (if a {@code
   * SoyCssTracker} was registered).
   */
  public void trackRequiredCss(CssToTrack css) {
    var tracker = cssTracker;
    if (tracker != null) {
      // TODO: if performance becomes a concern, we could use a simple bitset to track whether or
      // not we have called `traceRequiredCss` on this object before.  Each CssToTrack object is
      // allocated as a constant and aggressively interned, so it is likely that we will call
      // `trackRequiredCss` on the same object many times.
      for (String cssPath : css.cssPaths) {
        tracker.trackRequiredCssPath(cssPath);
      }
      for (String cssNamespace : css.cssNamespaces) {
        tracker.trackRequiredCssNamespace(cssNamespace);
      }
    }
  }

  /** Retrieves an injected parameter. */
  public SoyValueProvider getInjectedValue(RecordProperty key) {
    var value = ijData.get(key);
    return value == null ? UndefinedData.INSTANCE : value;
  }

  /** Retrieves an injected parameter with a default if unset. */
  public SoyValueProvider getInjectedValue(RecordProperty key, SoyValue defaultValue) {
    return SoyValueProvider.withDefault(ijData.get(key), defaultValue);
  }

  /** Catches a deferred error */
  public SoyValueProvider catchAsProvider(Throwable t) throws Throwable {
    if (debugSoyTemplateInfo) {
      throw t;
    }
    var provider = new ThrowingSoyValueProvider(t);
    var deferredErrors = this.deferredErrors;
    if (deferredErrors == null) {
      deferredErrors = this.deferredErrors = new ArrayList<>();
    }
    deferredErrors.add(provider);
    return provider;
  }

  public void logDeferredErrors() {
    var deferredErrors = this.deferredErrors;
    if (deferredErrors != null) {
      Set<Throwable> logged = Sets.newIdentityHashSet();
      for (var provider : deferredErrors) {
        provider.maybeLog(logged);
      }
    }
  }

  public void suppressDeferredErrorsOnto(Throwable t) {
    var deferredErrors = this.deferredErrors;
    if (deferredErrors != null) {
      Set<Throwable> suppressed = Sets.newIdentityHashSet();
      suppressed.add(t);
      for (var provider : deferredErrors) {
        provider.maybeSuppressOnto(t, suppressed);
      }
    }
  }

  @VisibleForTesting
  public Builder toBuilder() {
    return new Builder(templates, soyJavaDirectivesMap, pluginInstances)
        .withActiveModSelector(this.activeModSelector)
        .withPluginInstances(pluginInstances)
        .withCssRenamingMap(cssRenamingMap)
        .withXidRenamingMap(xidRenamingMap)
        .withMessageBundle(msgBundle)
        .withIj(ijData);
  }

  public ContextStore getContextStore() {
    return contextStore;
  }

  /** A builder for configuring the context. */
  @VisibleForTesting
  public static final class Builder {
    private final CompiledTemplates templates;
    private final ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap;
    private PluginInstances pluginInstances;
    private Predicate<String> activeModSelector;
    private SoyCssRenamingMap cssRenamingMap;
    private SoyIdRenamingMap xidRenamingMap;
    private SoyMsgBundle msgBundle;
    private boolean debugSoyTemplateInfo;
    private SoyCssTracker cssTracker;
    private SoyJsIdTracker jsIdTracker;
    private SoyInjector ijData;
    private GrammaticalGender viewerGrammaticalGender = GrammaticalGender.UNSPECIFIED;

    public Builder(
        CompiledTemplates templates,
        ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap,
        PluginInstances pluginInstances) {
      this.templates = templates;
      this.soyJavaDirectivesMap = soyJavaDirectivesMap;
      this.pluginInstances = pluginInstances;
    }

    @CanIgnoreReturnValue
    public Builder withActiveModSelector(Predicate<String> activeModSelector) {
      this.activeModSelector = checkNotNull(activeModSelector);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      this.cssRenamingMap = checkNotNull(cssRenamingMap);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withXidRenamingMap(SoyIdRenamingMap xidRenamingMap) {
      this.xidRenamingMap = checkNotNull(xidRenamingMap);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withPluginInstances(PluginInstances pluginInstances) {
      this.pluginInstances = checkNotNull(pluginInstances);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withMessageBundle(SoyMsgBundle msgBundle) {
      this.msgBundle = checkNotNull(msgBundle);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withDebugSoyTemplateInfo(boolean debugSoyTemplateInfo) {
      this.debugSoyTemplateInfo = debugSoyTemplateInfo;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withCssTracker(SoyCssTracker cssTracker) {
      this.cssTracker = cssTracker;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withJsIdTracker(SoyJsIdTracker jsIdTracker) {
      this.jsIdTracker = jsIdTracker;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withIj(SoyInjector ijData) {
      this.ijData = checkNotNull(ijData);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder withViewerGrammaticalGender(GrammaticalGender viewerGrammaticalGender) {
      this.viewerGrammaticalGender = checkNotNull(viewerGrammaticalGender);
      return this;
    }

    public RenderContext build() {
      return new RenderContext(
          templates,
          soyJavaDirectivesMap,
          pluginInstances,
          ijData,
          activeModSelector,
          cssRenamingMap,
          xidRenamingMap,
          msgBundle,
          debugSoyTemplateInfo,
          cssTracker,
          jsIdTracker,
          viewerGrammaticalGender);
    }
  }
}
