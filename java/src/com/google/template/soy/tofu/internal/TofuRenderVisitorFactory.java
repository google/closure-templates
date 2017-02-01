/*
 * Copyright 2010 Google Inc.
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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.soytree.TemplateRegistry;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of RenderVisitorFactory for Tofu backend.
 *
 */
@Singleton
class TofuRenderVisitorFactory {

  /** Factory for creating an instance of TofuEvalVisitor. */
  private final TofuEvalVisitorFactory tofuEvalVisitorFactory;

  @Inject
  public TofuRenderVisitorFactory(TofuEvalVisitorFactory tofuEvalVisitorFactory) {
    this.tofuEvalVisitorFactory = tofuEvalVisitorFactory;
  }

  /**
   * Creates a TofuRenderVisitor.
   *
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates.
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param activeDelPackageSelector The predicate for testing whether a given delpackage is active.
   *     Allowed to be null when known to be irrelevant. i.e. when not using delegates feature.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param xidRenamingMap The 'xid' renaming map, or null if not applicable.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   * @return The newly created TofuRenderVisitor instance.
   */
  public TofuRenderVisitor create(
      Appendable outputBuf,
      TemplateRegistry templateRegistry,
      ImmutableMap<String, ? extends SoyJavaPrintDirective> printDirectives,
      SoyRecord data,
      SoyRecord ijData,
      @Nullable Predicate<String> activeDelPackageSelector,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap) {

    return new TofuRenderVisitor(
        printDirectives,
        tofuEvalVisitorFactory,
        outputBuf,
        templateRegistry,
        checkNotNull(data),
        checkNotNull(ijData),
        activeDelPackageSelector,
        msgBundle,
        xidRenamingMap,
        cssRenamingMap);
  }
}
