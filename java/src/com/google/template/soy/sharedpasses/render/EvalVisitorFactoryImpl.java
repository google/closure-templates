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

import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default implementation of EvalVisitorFactory.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Singleton
public final class EvalVisitorFactoryImpl implements EvalVisitorFactory {

  @Inject
  public EvalVisitorFactoryImpl() {}

  @Override
  public EvalVisitor create(
      Environment env,
      @Nullable SoyRecord ijData,
      @Nullable SoyCssRenamingMap cssRenamingMap,
      @Nullable SoyIdRenamingMap xidRenamingMap,
      boolean debugSoyTemplateInfo) {
    return new EvalVisitor(env, ijData, cssRenamingMap, xidRenamingMap, debugSoyTemplateInfo);
  }
}
