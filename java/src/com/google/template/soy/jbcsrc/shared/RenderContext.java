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
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * A collection of contextual rendering data. Each top level rendering operation will obtain a
 * single instance of this object and it will be propagated throughout the render tree.
 */
public final class RenderContext {
  private static final CompiledTemplate EMPTY_TEMPLATE =
      new CompiledTemplate() {
        @Override
        public RenderResult render(LoggingAdvisingAppendable appendable, RenderContext context) {
          return RenderResult.done();
        }

        @Override
        public ContentKind kind() {
          // The kind doesn't really matter, since the empty string can always be safely escaped
          return ContentKind.TEXT;
        }
      };

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
  private final List<String> renderedCssNamespaces = new ArrayList<>();
  /**
   * Whenever we visit a template call, we know that it will be rendered. The main exception is in
   * the case of logOnly. Whenever logOnly is true, we do execute the templates with the knowledge
   * that it *won't* be rendered. To emulate that, we keep a stack of shouldRender booleans (the
   * opposite of logOnly). It starts off at [true] and if we encounter a logOnly, it changes to
   * [true, false]. Subsequent evaluations may make it [true, false, true]. In order to decide if a
   * template should collect CSS, we take the union of the stack, so [true, false, true] would
   * evaluate to true & false & true, which is false. When we exit a log statement, we pop off the
   * top and eventually we will end up back at [true].
   */
  private final ArrayDeque<Boolean> renderCounter = new ArrayDeque<>();

  private RenderContext(Builder builder) {
    this.activeDelPackageSelector = checkNotNull(builder.activeDelPackageSelector);
    this.templates = checkNotNull(builder.templates);
    this.cssRenamingMap = builder.cssRenamingMap;
    this.xidRenamingMap = builder.xidRenamingMap;
    this.soyJavaDirectivesMap = builder.soyJavaDirectivesMap;
    this.pluginInstances = builder.pluginInstances;
    this.msgBundle = builder.msgBundle;
    this.debugSoyTemplateInfo = builder.debugSoyTemplateInfo;
    this.logger = builder.logger;
  }

  @Nullable
  public ULocale getLocale() {
    return msgBundle.getLocale();
  }

  public ImmutableList<String> getAllRequiredCssNamespaces(Object templateOrList) {
    if (templateOrList instanceof StringData || templateOrList instanceof String) {
      String template;
      if (templateOrList instanceof StringData) {
        template = ((StringData) templateOrList).stringValue();
      } else {
        template = (String) templateOrList;
      }
      return templates.getAllRequiredCssNamespaces(template, activeDelPackageSelector, false);
    } else {
      @SuppressWarnings("unchecked")
      SoyList templateList = ((SoyList) templateOrList);
      return templateList.asJavaList().stream()
          .flatMap(
              template ->
                  templates
                      .getAllRequiredCssNamespaces(
                          template.resolve().stringValue(), activeDelPackageSelector, false)
                      .stream())
          .distinct()
          .collect(ImmutableList.toImmutableList());
    }
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

  public LogStatement enterLogOnly(LogStatement logStatement) {
    renderCounter.push(!logStatement.logOnly());
    return logStatement;
  }

  public void exitLogOnly() {
    renderCounter.pop();
  }

  public void addRenderedTemplate(String template) {
    if (!shouldRender()) {
      return;
    }
    try {
      this.renderedCssNamespaces.addAll(templates.getRequiredCssNamespaces(template));
    } catch (Exception e) {
      // This is possible because you can call templates that don't exist...
      return;
    }
  }

  public List<String> getRenderedCssNamespaces() {
    return renderedCssNamespaces;
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

  public CompiledTemplate getDelTemplate(
      String calleeName, String variant, boolean allowEmpty, SoyRecord params, SoyRecord ij) {
    CompiledTemplate.Factory callee =
        templates.selectDelTemplate(calleeName, variant, activeDelPackageSelector);
    if (callee == null) {
      if (allowEmpty) {
        return EMPTY_TEMPLATE;
      }
      throw new IllegalArgumentException(
          "Found no active impl for delegate call to \""
              + calleeName
              + (variant.isEmpty() ? "" : ":" + variant)
              + "\" (and delcall does not set allowemptydefault=\"true\").");
    }
    return callee.create(params, ij);
  }

  /** Returns {@code true} if the primary msg should be used instead of the fallback. */
  public boolean usePrimaryMsg(long msgId, long fallbackId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    // use getMsgParts() since if the bundle is a RenderOnlySoyMsgBundleImpl then this will be
    // allocation free.
    return !msgBundle.getMsgParts(msgId).isEmpty() || msgBundle.getMsgParts(fallbackId).isEmpty();
  }

  private boolean shouldRender() {
    if (renderCounter.isEmpty()) {
      return true;
    }
    if (renderCounter.size() == 1) {
      return renderCounter.peek();
    }
    return renderCounter.stream().reduce((a, b) -> a && b).orElse(true);
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

  @VisibleForTesting
  public Builder toBuilder() {
    return new Builder()
        .withActiveDelPackageSelector(this.activeDelPackageSelector)
        .withPluginInstances(pluginInstances)
        .withSoyPrintDirectives(soyJavaDirectivesMap)
        .withCssRenamingMap(cssRenamingMap)
        .withXidRenamingMap(xidRenamingMap)
        .withMessageBundle(msgBundle)
        .withCompiledTemplates(templates);
  }

  /** A builder for configuring the context. */
  public static final class Builder {
    private CompiledTemplates templates;
    private Predicate<String> activeDelPackageSelector = arg -> false;
    private SoyCssRenamingMap cssRenamingMap = SoyCssRenamingMap.EMPTY;
    private SoyIdRenamingMap xidRenamingMap = SoyCssRenamingMap.EMPTY;
    private ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap = ImmutableMap.of();
    private ImmutableMap<String, Supplier<Object>> pluginInstances = ImmutableMap.of();
    private SoyMsgBundle msgBundle = SoyMsgBundle.EMPTY;
    private boolean debugSoyTemplateInfo = false;
    private SoyLogger logger;

    public Builder withCompiledTemplates(CompiledTemplates templates) {
      this.templates = checkNotNull(templates);
      return this;
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

    public Builder withPluginInstances(Map<String, Supplier<Object>> pluginInstances) {
      this.pluginInstances = ImmutableMap.copyOf(pluginInstances);
      return this;
    }

    public Builder withSoyPrintDirectives(Map<String, ? extends SoyJavaPrintDirective> directives) {
      this.soyJavaDirectivesMap = ImmutableMap.copyOf(directives);
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
      return new RenderContext(this);
    }
  }
}
