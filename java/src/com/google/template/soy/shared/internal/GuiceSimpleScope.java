/*
 * Copyright 2009 Google Inc.
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

import com.google.auto.value.AutoValue;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import java.util.ArrayDeque;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Stores thread-local data for Soy usage.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class GuiceSimpleScope implements SoyScopedData {
  /**
   * An autoclosable object that can be used to seed and exit scopes.
   *
   * <p>Obtain an instance with {@link GuiceSimpleScope#enter}.
   */
  public final class InScope implements AutoCloseable {
    private boolean isClosed;
    private final Thread openThread = Thread.currentThread();
    private final ArrayDeque<Data> deque;

    InScope(ArrayDeque<Data> deque) {
      this.deque = deque;
    }

    /** Exits the scope */
    @Override
    public void close() {
      checkOpenAndOnCorrectThread();
      isClosed = true;
      deque.pop();
    }

    private void checkOpenAndOnCorrectThread() {
      if (isClosed) {
        throw new IllegalStateException("called close() more than once!");
      }
      if (Thread.currentThread() != openThread) {
        throw new IllegalStateException("cannot move the scope to another thread");
      }
    }
  }

  /** The ThreadLocal holding all the values in scope. */
  private static final ThreadLocal<ArrayDeque<Data>> scopedValuesTl = new ThreadLocal<>();

  /** Enters an occurrence of this scope. */
  @CheckReturnValue
  public InScope enter(@Nullable SoyMsgBundle msgBundle) {
    return enter(msgBundle, null);
  }

  /** Enters an occurrence of this scope. */
  @CheckReturnValue
  public InScope enter(@Nullable SoyMsgBundle msgBundle, @Nullable BidiGlobalDir bidiGlobalDir) {
    return enter(
        bidiGlobalDir == null
            ? BidiGlobalDir.forStaticIsRtl(msgBundle == null ? false : msgBundle.isRtl())
            : bidiGlobalDir,
        msgBundle != null ? msgBundle.getLocaleString() : null);
  }

  /** Enters an occurrence of this scope. */
  @CheckReturnValue
  public InScope enter(BidiGlobalDir bidiGlobalDir, @Nullable String locale) {
    ArrayDeque<Data> stack = scopedValuesTl.get();
    if (stack == null) {
      stack = new ArrayDeque<>();
      scopedValuesTl.set(stack);
    }
    stack.push(Data.create(locale, bidiGlobalDir));
    return new InScope(stack);
  }

  private Data getScopedData() {
    ArrayDeque<Data> arrayDeque = scopedValuesTl.get();
    if (arrayDeque == null || arrayDeque.isEmpty()) {
      throw new IllegalStateException("Cannot access scoped data outside of a scoping block");
    }
    return arrayDeque.peek();
  }

  @Override
  @Nullable
  public String getLocale() {
    return getScopedData().locale();
  }

  @Override
  public BidiGlobalDir getBidiGlobalDir() {
    return getScopedData().bidiGlobalDir();
  }

  @AutoValue
  abstract static class Data {
    @Nullable
    abstract String locale();

    abstract BidiGlobalDir bidiGlobalDir();

    static Data create(@Nullable String locale, BidiGlobalDir bidiGlobalDir) {
      return new AutoValue_GuiceSimpleScope_Data(locale, bidiGlobalDir);
    }
  }
}
