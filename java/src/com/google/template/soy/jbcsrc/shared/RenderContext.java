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
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyProtoValue;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.ibm.icu.util.ULocale;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A collection of contextual rendering data. Each top level rendering operation will obtain a
 * single instance of this object and it will be propagated throughout the render tree.
 */
public final class RenderContext {
  private static final CompiledTemplate EMPTY_TEMPLATE =
      new CompiledTemplate() {
        @Override
        public RenderResult render(AdvisingAppendable appendable, RenderContext context) {
          return RenderResult.done();
        }

        @Override
        @Nullable
        public ContentKind kind() {
          // The kind doesn't really matter, since the empty string can always be safely escaped
          return null;
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
  private final ImmutableMap<String, SoyJavaFunction> soyJavaFunctionsMap;
  private final ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap;
  private final SoyValueConverter converter;
  /** The bundle of translated messages */
  private final SoyMsgBundle msgBundle;

  private RenderContext(Builder builder) {
    this.activeDelPackageSelector = checkNotNull(builder.activeDelPackageSelector);
    this.templates = checkNotNull(builder.templates);
    this.cssRenamingMap = builder.cssRenamingMap;
    this.xidRenamingMap = builder.xidRenamingMap;
    this.soyJavaFunctionsMap = builder.soyJavaFunctionsMap;
    this.soyJavaDirectivesMap = builder.soyJavaDirectivesMap;
    this.converter = builder.converter;
    this.msgBundle = builder.msgBundle;
  }
  
  @Nullable
  public ULocale getLocale() {
    return msgBundle.getLocale();
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

  /**
   * Helper for boxing protos. We cannot currently box protos without calling out to the value
   * converter because the SoyProtoValue has a package private constructor and even if it was public
   * it would be hard/impossible to call it.
   *
   * <p>The difficulty is because SoyProtoValue currently depends on its SoyType for field
   * interpretation. In theory we could drop this and have it just use the descriptor directly
   * (since it has a Message instance it could just call message.getDescriptor()), but this may add
   * some overhead. This could all be made much easier if we had perfect type information (then we
   * would ~never need to box or rely on the SoyValue implementation).
   */
  public SoyProtoValue box(Message proto) {
    if (proto == null) {
      return null;
    }
    return (SoyProtoValue) converter.convert(proto);
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
          "Found no active impl for delegate call to '"
              + calleeName
              + "' (and no attribute allowemptydefault=\"true\").");
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
        .withSoyFunctions(soyJavaFunctionsMap)
        .withSoyPrintDirectives(soyJavaDirectivesMap)
        .withCssRenamingMap(cssRenamingMap)
        .withXidRenamingMap(xidRenamingMap)
        .withConverter(converter)
        .withMessageBundle(msgBundle);
  }

  /** A builder for configuring the context. */
  public static final class Builder {
    private CompiledTemplates templates;
    private Predicate<String> activeDelPackageSelector = Predicates.alwaysFalse();
    private SoyCssRenamingMap cssRenamingMap = SoyCssRenamingMap.EMPTY;
    private SoyIdRenamingMap xidRenamingMap = SoyCssRenamingMap.EMPTY;
    private ImmutableMap<String, SoyJavaFunction> soyJavaFunctionsMap = ImmutableMap.of();
    private ImmutableMap<String, SoyJavaPrintDirective> soyJavaDirectivesMap = ImmutableMap.of();
    private SoyValueConverter converter = SoyValueConverter.UNCUSTOMIZED_INSTANCE;
    private SoyMsgBundle msgBundle = SoyMsgBundle.EMPTY;

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

    public Builder withMessageBundle(SoyMsgBundle msgBundle) {
      this.msgBundle = checkNotNull(msgBundle);
      return this;
    }

    public RenderContext build() {
      return new RenderContext(this);
    }
  }
}
