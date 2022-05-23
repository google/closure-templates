/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;

/** A NoOp {@link SoyScopedData}. */
public final class NoOpScopedData
    implements SoyScopedData, SoyScopedData.Enterable, SoyScopedData.InScope {

  @Override
  public void close() {}

  @Override
  public InScope enter(BidiGlobalDir bidiGlobalDir) {
    return this;
  }

  @Override
  public InScope enter(SoyMsgBundle msgBundle) {
    return this;
  }

  @Override
  public InScope enter(SoyMsgBundle msgBundle, BidiGlobalDir bidiGlobalDir) {
    return this;
  }

  @Override
  public Enterable enterable() {
    return this;
  }

  @Override
  public BidiGlobalDir getBidiGlobalDir() {
    throw new UnsupportedOperationException();
  }
}
