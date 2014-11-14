/*
 * Copyright 2014 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.data.SoyFutureValueProvider.FutureBlockCallback;

import java.io.Flushable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Counts the characters that have been appended to the wrapped appenable since it was last flushed.
 */
public final class CountingFlushableAppendable
    implements Appendable, Flushable, FutureBlockCallback {

  private static final Logger logger = Logger.getLogger(
      CountingFlushableAppendable.class.getName());

  private int count;
  private final Appendable appendable;
  private final Flushable flushable;


  public CountingFlushableAppendable(Appendable appendable) {
    Preconditions.checkState(appendable instanceof Flushable);
    this.appendable = appendable;
    this.flushable = (Flushable) appendable;
  }

  CountingFlushableAppendable(Appendable appendable, Flushable flushable) {
    this.appendable = appendable;
    this.flushable = flushable;
  }

  public int getAppendedCountSinceLastFlush() {
    return count;
  }

  @Override public Appendable append(CharSequence csq) throws IOException {
    count += csq.length();
    return appendable.append(csq);
  }

  @Override public Appendable append(CharSequence csq, int start, int end) throws IOException {
     count += end - start;
    return appendable.append(csq, start, end);
  }

  @Override public Appendable append(char c) throws IOException {
    count += 1;
    return appendable.append(c);
  }

  @Override public void flush() throws IOException {
    count = 0;
    flushable.flush();
  }

  /**
   * Soy is about to block on a future. Flush the output stream if there is anything to flush,
   * so we use the time we are blocking to transfer as many bytes as possible.
   */
  @Override public void beforeBlock() {
    if (count > 0) {
      try {
        flush();
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Flush from soy failed", e);
      }
    }
  }
}
