/*
 * Copyright 2011 Google Inc.
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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.sharedpasses.render.RenderVisitor;
import com.google.template.soy.soytree.TemplateRegistry;
import javax.annotation.Nullable;

/**
 * Version of {@code RenderVisitor} for the Tofu backend.
 *
 * <p>For deprecated directive implementations, uses {@code SoyTofuPrintDirective}s instead of
 * {@code SoyJavaRuntimePrintDirective}s. (For new directives that implement {@code
 * SoyJavaPrintDirective}, there is no difference.)
 *
 */
// TODO: Attempt to remove this class.
final class TofuRenderVisitor extends RenderVisitor {

  /**
   * @param soyJavaDirectivesMap Map of all SoyJavaPrintDirectives (name to directive).
   * @param tofuEvalVisitorFactory Factory for creating an instance of TofuEvalVisitor.
   * @param outputBuf The Appendable to append the output to.
   * @param templateRegistry A registry of all templates. Should never be null (except in some unit
   *     tests).
   * @param data The current template data.
   * @param ijData The current injected data.
   * @param activeDelPackageSelector The predicate for testing whether a given delpackage is active.
   *     Allowed to be null when known to be irrelevant.
   * @param msgBundle The bundle of translated messages, or null to use the messages from the Soy
   *     source.
   * @param xidRenamingMap The 'xid' renaming map, or null if not applicable.
   * @param cssRenamingMap The CSS renaming map, or null if not applicable.
   */
  TofuRenderVisitor(
      ImmutableMap<String, ? extends SoyJavaPrintDirective> soyJavaDirectivesMap,
      TofuEvalVisitorFactory tofuEvalVisitorFactory,
      Appendable outputBuf,
      @Nullable TemplateRegistry templateRegistry,
      SoyRecord data,
      @Nullable SoyRecord ijData,
      @Nullable Predicate<String> activeDelPackageSelector,
      @Nullable SoyMsgBundle msgBundle,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyCssRenamingMap cssRenamingMap) {
    super(
        soyJavaDirectivesMap,
        tofuEvalVisitorFactory,
        outputBuf,
        templateRegistry,
        data,
        ijData,
        activeDelPackageSelector,
        msgBundle,
        xidRenamingMap,
        cssRenamingMap);
  }

  @Override
  protected TofuRenderVisitor createHelperInstance(Appendable outputBuf, SoyRecord data) {
    return new TofuRenderVisitor(
        soyJavaDirectivesMap,
        (TofuEvalVisitorFactory) evalVisitorFactory,
        outputBuf,
        templateRegistry,
        data,
        ijData,
        activeDelPackageSelector,
        msgBundle,
        xidRenamingMap,
        cssRenamingMap);
  }
}
