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

package com.google.template.soy.jbcsrc.api;

import java.io.IOException;

/**
 * An {@link Appendable} that can inform the writer that a buffer limit has been reached or
 * exceeded.
 *
 * <p>When {@link #softLimitReached} returns {@code true}, the writer should attempt to pause
 * writing operations and return control to the calling thread as soon as possible. This is a
 * <em>cooperative</em> rate limiting mechanism and is best effort.
 *
 * <p>Implementers should note that if {@link #softLimitReached} returns {@code true}, they should
 * continue to accept writes via the {@code .append(...)} methods. Throwing an exception when in
 * this state is a violation of the contract.
 */
public interface AdvisingAppendable extends Appendable {
  @Override
  AdvisingAppendable append(CharSequence csq) throws IOException;

  @Override
  AdvisingAppendable append(CharSequence csq, int start, int end) throws IOException;

  @Override
  AdvisingAppendable append(char c) throws IOException;

  /**
   * Indicates that an internal limit has been reached or exceeded and that write operations should
   * be suspended soon.
   */
  boolean softLimitReached();
}
