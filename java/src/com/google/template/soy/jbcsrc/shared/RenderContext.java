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
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;

import java.util.Map;

/**
 * A collection of contextual rendering data.  Each top level rendering operation will obtain a
 * single instance of this object and it will be propagated throughout the render tree.
 */
public final class RenderContext {
  private final DelTemplateSelector templateSelector;
  private final SoyCssRenamingMap cssRenamingMap;
  private final SoyIdRenamingMap xidRenamingMap;
  private final ImmutableMap<String, SoyJavaFunction> soyJavaFunctionsMap;
  private final ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap;
  private final SoyValueConverter converter;
  /** The bundle of translated messages */
  private final SoyMsgBundle msgBundle;
  private final SoyMsgBundle defaultBundle;

  private RenderContext(Builder builder) {
    this.templateSelector = checkNotNull(builder.templateSelector);
    this.cssRenamingMap = builder.cssRenamingMap;
    this.xidRenamingMap = builder.xidRenamingMap;
    this.soyJavaFunctionsMap = builder.soyJavaFunctionsMap;
    this.soyJavaDirectivesMap = builder.soyJavaDirectivesMap;
    this.converter = builder.converter;
    this.msgBundle = builder.msgBundle;
    this.defaultBundle = builder.defaultBundle;
  }

  public String renameCssSelector(String selector) {
    String string = cssRenamingMap.get(selector);
    return string == null ? selector : string;
  }

  public String renameXid(String id) {
    String string = xidRenamingMap.get(id);
    return string == null ? id + "_" : string;
  }

  public SoyJavaFunction getFunction(String name) {
    SoyJavaFunction fn = soyJavaFunctionsMap.get(name);
    if (fn == null) {
      throw new IllegalStateException("Failed to find Soy function with name '" + name + "'");
    }
    return fn;
  }

  public SoyJavaPrintDirective getPrintDirective(String name) {
    SoyJavaPrintDirective printDirective = soyJavaDirectivesMap.get(name);
    if (printDirective == null) {
      throw new IllegalStateException(
          "Failed to find Soy print directive with name '" + name + "'");
    }
    return printDirective;
  }

  public CompiledTemplate getDelTemplate(
      String calleeName, String variant, boolean allowEmpty, SoyRecord params, SoyRecord ij) {
    return templateSelector.selectDelTemplate(calleeName, variant, allowEmpty).create(params, ij);
  }

  /**
   * Returns {@code true} if the primary msg should be used instead of the fallback.
   */
  public boolean usePrimaryMsg(long msgId, long fallbackId) {
    // Note: we need to make sure the fallback msg is actually present if we are going to fallback.
    return msgBundle.getMsg(msgId) != null || msgBundle.getMsg(fallbackId) == null;
  }

  /**
   * Returns the {@link SoyMsg} associated with the {@code msgId} or the fallback (aka english)
   * translation if there is no such message.
   */
  public SoyMsg getSoyMsg(long msgId) {
    SoyMsg msg = msgBundle.getMsg(msgId);
    if (msg == null) {
      msg = defaultBundle.getMsg(msgId);
      if (msg == null) {
        throw new IllegalArgumentException("unknown messageId: " + msgId);
      }
    }
    return msg;
  }

  @VisibleForTesting
  public Builder toBuilder() {
    return new Builder()
        .withTemplateSelector(templateSelector)
        .withSoyFunctions(soyJavaFunctionsMap)
        .withSoyPrintDirectives(soyJavaDirectivesMap)
        .withCssRenamingMap(cssRenamingMap)
        .withXidRenamingMap(xidRenamingMap)
        .withConverter(converter)
        .withMessageBundles(msgBundle, defaultBundle);
  }

  /** A builder for configuring the context. */
  public static final class Builder {
    private DelTemplateSelector templateSelector;
    private SoyCssRenamingMap cssRenamingMap = SoyCssRenamingMap.EMPTY;
    private SoyIdRenamingMap xidRenamingMap = SoyCssRenamingMap.EMPTY;
    private ImmutableMap<String, SoyJavaFunction> soyJavaFunctionsMap = ImmutableMap.of();
    private ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap = ImmutableMap.of();
    private SoyValueConverter converter = SoyValueHelper.UNCUSTOMIZED_INSTANCE;
    private SoyMsgBundle msgBundle = SoyMsgBundle.EMPTY;
    private SoyMsgBundle defaultBundle = SoyMsgBundle.EMPTY;

    public Builder withTemplateSelector(DelTemplateSelector selector) {
      this.templateSelector = checkNotNull(selector);
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

    public Builder withSoyFunctions(ImmutableMap<String, SoyJavaFunction> functions) {
      this.soyJavaFunctionsMap = functions;
      return this;
    }

    public Builder withSoyPrintDirectives(Map<String, ? extends SoyJavaPrintDirective> directives) {
      this.soyJavaDirectivesMap = ImmutableMap.copyOf(directives);
      return this;
    }

    public Builder withConverter(SoyValueConverter converter) {
      this.converter = checkNotNull(converter);
      return this;
    }

    public Builder withMessageBundles(
        SoyMsgBundle msgBundle,
        SoyMsgBundle defaultBundle) {
      this.msgBundle = checkNotNull(msgBundle);
      this.defaultBundle = checkNotNull(defaultBundle);
      return this;
    }

    public RenderContext build() {
      return new RenderContext(this);
    }
  }
}
