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
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Access to scoped data for Soy.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public interface SoyScopedData {

  @Nullable
  String getLocale();

  BidiGlobalDir getBidiGlobalDir();

  Enterable enterable();

  /** Allows entering a portion of code from which SoyScopedData can be retrieved. */
  public interface Enterable {
    /** Enters an occurrence of this scope. */
    @CheckReturnValue
    InScope enter(@Nullable SoyMsgBundle msgBundle);

    /** Enters an occurrence of this scope. */
    @CheckReturnValue
    InScope enter(@Nullable SoyMsgBundle msgBundle, @Nullable BidiGlobalDir bidiGlobalDir);

    /** Enters an occurrence of this scope. */
    @CheckReturnValue
    InScope enter(BidiGlobalDir bidiGlobalDir, @Nullable String locale);
  }

  /** A subtype of {@link AutoCloseable} that can be closed without an IOException. */
  public interface InScope extends AutoCloseable {

    @Nullable
    String getLocale();

    BidiGlobalDir getBidiGlobalDir();

    @Override
    void close();
  }
}
