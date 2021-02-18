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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.logging.LoggableElementMetadata;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A collection of contextual rendering data. Each top level rendering operation will obtain a
 * single instance of this object and it will be propagated throughout the render tree.
 */
public final class RenderContext {
  private static RenderResult emptyTemplate(
      SoyRecord params, SoyRecord ij, LoggingAdvisingAppendable appendable, RenderContext context) {
    return RenderResult.done();
  }

  // TODO(lukes):  within this object most of these fields are constant across all renders while
  // some are expected to change frequently (the renaming maps, msgBundle and activeDelPackages).
  // Consider splitting this into two objects to represent the changing lifetimes.  We are kind of
  // doing this now by having SoySauceImpl reuse the Builder, but this is a little strange and could
  // be theoretically made more efficient to construct.

  private final Predicate<String> activeDelPackageSelector;
  private final CompiledTemplates templates;
  private final SoyCssRenamingMap cssRenamingMap;
  private final SoyIdRenamingMap xidRenamingMap;
  private final ImmutableMap<String, Supplier<Object>> pluginInstances;
  private final ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap;
  /** The bundle of translated messages */
  private final SoyMsgBundle msgBundle;

  private final boolean debugSoyTemplateInfo;
  private final SoyLogger logger;

  // This stores the stack frame for restoring state after a detach operation.  It is initialised to
  // a special state 0 that represents the first call to any detachable method.
  // TODO(lukes): ideally this would not be stored in RenderContext, but instead would be a method
  // parameter to every detachable method and would be encoded in RenderResult for when methods
  // return.  This is a little difficult right now because RenderResult is a public type.  For now,
  // storing a mutable field on RenderContext is simpler.
  private StackFrame topFrame = StackFrame.INIT;

  private RenderContext(
      CompiledTemplates templates,
      ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap,
      ImmutableMap<String, Supplier<Object>> pluginInstances,
      @Nullable Predicate<String> activeDelPackageSelector,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyMsgBundle msgBundle,
      boolean debugSoyTemplateInfo,
      @Nullable SoyLogger logger) {
    this.templates = templates;
    this.soyJavaDirectivesMap = soyJavaDirectivesMap;
    this.pluginInstances = pluginInstances;
    this.activeDelPackageSelector =
        activeDelPackageSelector != null ? activeDelPackageSelector : delPackage -> false;
    this.cssRenamingMap = cssRenamingMap == null ? SoyCssRenamingMap.EMPTY : cssRenamingMap;
    this.xidRenamingMap = xidRenamingMap == null ? SoyCssRenamingMap.EMPTY : xidRenamingMap;
    this.msgBundle = msgBundle == null ? SoyMsgBundle.EMPTY : msgBundle;
    this.debugSoyTemplateInfo = debugSoyTemplateInfo;
    this.logger = logger == null ? SoyLogger.NO_OP : logger;
  }

  @Nullable
  public ULocale getLocale() {
    return msgBundle.getLocale();
  }

  public ImmutableList<String> getAllRequiredCssNamespaces(String template) {
    return templates.getAllRequiredCssNamespaces(template, activeDelPackageSelector, false);
  }

  public BidiGlobalDir getBidiGlobalDir() {
    return BidiGlobalDir.forStaticIsRtl(msgBundle.isRtl());
  }

  public String renameCssSelector(String selector) {
    String string = cssRenamingMap.get(selector);
    return string == null ? selector : string;
  }

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
    final SoyJavaPrintDirective printDirective = soyJavaDirectivesMap.get(name);
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

  /** Returns a boolean indicating whether or not there is a logger configured. */
  public boolean hasLogger() {
    return logger != SoyLogger.NO_OP;
  }

  public SoyLogger getLogger() {
    return logger;
  }

  public CompiledTemplate getTemplate(String calleeName) {
    return templates.getTemplate(calleeName);
  }

  public CompiledTemplate getDelTemplate(String calleeName, String variant, boolean allowEmpty) {
    CompiledTemplate callee =
        templates.selectDelTemplate(calleeName, variant, activeDelPackageSelector);
    if (callee == null) {
      if (allowEmpty) {
        return RenderContext::emptyTemplate;
      }
      throw new IllegalArgumentException(
          "Found no active impl for delegate call to \""
              + calleeName
              + (variant.isEmpty() ? "" : ":" + variant)
              + "\" (and delcall does not set allowemptydefault=\"true\").");
    }
    return callee;
  }

  /** Returns {@code true} if the primary msg should be used instead of the fallback. */
  public boolean usePrimaryMsgIfFallback(long msgId, long fallbackId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use getMsgParts() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return !msgBundle.getMsgParts(msgId).isEmpty() || msgBundle.getMsgParts(fallbackId).isEmpty();
  }

  /**
   * Returns {@code true} if the primary or alternate msg should be used instead of the fallback.
   */
  public boolean usePrimaryOrAlternateIfFallback(long msgId, long alternateId, long fallbackId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use getMsgParts() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return !msgBundle.getMsgParts(msgId).isEmpty()
        || !msgBundle.getMsgParts(alternateId).isEmpty()
        || msgBundle.getMsgParts(fallbackId).isEmpty();
  }

  /**
   * Returns {@code true} if the primary msg should be used instead of the fallback or the fallback
   * alternate.
   */
  public boolean usePrimaryIfFallbackOrFallbackAlternate(
      long msgId, long fallbackId, long fallbackAlternateId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use getMsgParts() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return !msgBundle.getMsgParts(msgId).isEmpty()
        || (msgBundle.getMsgParts(fallbackId).isEmpty()
            && msgBundle.getMsgParts(fallbackAlternateId).isEmpty());
  }

  /**
   * Returns {@code true} if the primary or alternate msg should be used instead of the fallback or
   * the fallback alternate.
   */
  public boolean usePrimaryOrAlternateIfFallbackOrFallbackAlternate(
      long msgId, long alternateId, long fallbackId, long fallbackAlternateId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use getMsgParts() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return !msgBundle.getMsgParts(msgId).isEmpty()
        || !msgBundle.getMsgParts(alternateId).isEmpty()
        || (msgBundle.getMsgParts(fallbackId).isEmpty()
            && msgBundle.getMsgParts(fallbackAlternateId).isEmpty());
  }

  /**
   * Returns the {@link SoyMsg} associated with the {@code msgId} or the fallback (aka english)
   * translation if there is no such message.
   */
  public ImmutableList<SoyMsgPart> getSoyMsgParts(
      long msgId, ImmutableList<SoyMsgPart> defaultMsgParts) {
    ImmutableList<SoyMsgPart> msgParts = msgBundle.getMsgParts(msgId);
    if (msgParts.isEmpty()) {
      return defaultMsgParts;
    }
    return msgParts;
  }

  /**
   * Returns the {@link SoyMsg} associated with the {@code msgId}, the {@code alternateId} or the
   * fallback (aka english) translation if there is no such message.
   */
  public ImmutableList<SoyMsgPart> getSoyMsgPartsWithAlternateId(
      long msgId, ImmutableList<SoyMsgPart> defaultMsgParts, long alternateId) {
    ImmutableList<SoyMsgPart> msgParts = msgBundle.getMsgParts(msgId);
    if (msgParts.isEmpty()) {
      ImmutableList<SoyMsgPart> msgPartsByAlternateId = msgBundle.getMsgParts(alternateId);
      if (msgPartsByAlternateId.isEmpty()) {
        return defaultMsgParts;
      }
      return msgPartsByAlternateId;
    }
    return msgParts;
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
      throw new AssertionError(e);
    }
  }

  /**
   * Save the contents of the frame into the stack.
   *
   * <p>This method is called when detaching a render operation by our `invokedynamic`
   * infrastructure in SaveStateMetaFactory.
   */
  public void pushFrame(StackFrame state) {
    state.child = topFrame;
    this.topFrame = state;
  }

  /**
   * Restore the stack frame for the next template.
   *
   * <p>This method is called at the top of every detachable class that is generated by the
   * compiler.
   */
  public StackFrame popFrame() {
    StackFrame next = topFrame;
    // NOTE: the special frame StackFrame.INIT is linked to itself, so we don't need to test for a
    // basecase.
    this.topFrame = next.child;
    return next;
  }

  @VisibleForTesting
  public Builder toBuilder() {
    return new Builder(templates, soyJavaDirectivesMap, pluginInstances)
        .withActiveDelPackageSelector(this.activeDelPackageSelector)
        .withPluginInstances(pluginInstances)
        .withCssRenamingMap(cssRenamingMap)
        .withXidRenamingMap(xidRenamingMap)
        .withMessageBundle(msgBundle);
  }

  /** A builder for configuring the context. */
  public static final class Builder {
    private final CompiledTemplates templates;
    private final ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap;
    private ImmutableMap<String, Supplier<Object>> pluginInstances;
    private Predicate<String> activeDelPackageSelector;
    private SoyCssRenamingMap cssRenamingMap;
    private SoyIdRenamingMap xidRenamingMap;
    private SoyMsgBundle msgBundle;
    private boolean debugSoyTemplateInfo;
    private SoyLogger logger;

    public Builder(
        CompiledTemplates templates,
        ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap,
        ImmutableMap<String, Supplier<Object>> pluginInstances) {
      this.templates = templates;
      this.soyJavaDirectivesMap = soyJavaDirectivesMap;
      this.pluginInstances = pluginInstances;
    }

    public Builder withActiveDelPackageSelector(Predicate<String> activeDelPackageSelector) {
      this.activeDelPackageSelector = checkNotNull(activeDelPackageSelector);
      return this;
    }

    public Builder withCssRenamingMap(SoyCssRenamingMap cssRenamingMap) {
      this.cssRenamingMap = checkNotNull(cssRenamingMap);
      return this;
    }

    public Builder withXidRenamingMap(SoyIdRenamingMap xidRenamingMap) {
      this.xidRenamingMap = checkNotNull(xidRenamingMap);
      return this;
    }

    public Builder withPluginInstances(ImmutableMap<String, Supplier<Object>> pluginInstances) {
      this.pluginInstances = checkNotNull(pluginInstances);
      return this;
    }

    public Builder withMessageBundle(SoyMsgBundle msgBundle) {
      this.msgBundle = checkNotNull(msgBundle);
      return this;
    }

    public Builder withDebugSoyTemplateInfo(boolean debugSoyTemplateInfo) {
      this.debugSoyTemplateInfo = debugSoyTemplateInfo;
      return this;
    }

    public Builder withLogger(SoyLogger logger) {
      this.logger = logger;
      return this;
    }

    public RenderContext build() {
      return new RenderContext(
          templates,
          soyJavaDirectivesMap,
          pluginInstances,
          activeDelPackageSelector,
          cssRenamingMap,
          xidRenamingMap,
          msgBundle,
          debugSoyTemplateInfo,
          logger);
    }
  }
}
