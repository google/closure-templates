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

package com.google.template.soy.sharedpasses.render;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.plugin.java.PluginInstances;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Default implementation of EvalVisitorFactory.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class EvalVisitorFactoryImpl implements EvalVisitorFactory {

  @Override
  public EvalVisitor create(
      Environment env,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      @Nullable SoyMsgBundle msgBundle,
      boolean debugSoyTemplateInfo,
      PluginInstances pluginInstances,
      ImmutableTable<SourceFilePath, String, ImmutableList<ExternNode>> externs,
      DelTemplateSelector<TemplateDelegateNode> deltemplates,
      Predicate<String> activeDelPackageSelector) {
    return new EvalVisitor(
        env,
        cssRenamingMap,
        xidRenamingMap,
        msgBundle,
        debugSoyTemplateInfo,
        pluginInstances,
        /*
         * Use BUGGED mode for backwards compatibility.  The default tofu renderer always had a
         * buggy implementation of data access nodes and this makes that behavior a little more
         * explicit.
         */
        EvalVisitor.UndefinedDataHandlingMode.BUGGED,
        externs,
        deltemplates,
        activeDelPackageSelector);
  }
}
