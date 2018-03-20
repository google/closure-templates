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

package com.google.template.soy.data.internal;

import java.io.IOException;

/**
 * A renderable <a href="http://en.wikipedia.org/wiki/Thunk">thunk</a>.
 *
 * <p>Subclasses should override {@link #doRender(Appendable)} method to implement the rendering
 * logic.
 */
public abstract class RenderableThunk {
  private String content;

  /** Renders the thunk directly to the appendable. */
  public final void render(Appendable appendable) throws IOException {
    if (content == null) {
      TeeAppendable teeAppendable = new TeeAppendable(appendable);
      doRender(teeAppendable);
      content = teeAppendable.buffer.toString();
    } else {
      appendable.append(content);
    }
  }

  protected abstract void doRender(Appendable appendable) throws IOException;

  /**
   * Renders the thunk to the given {@link Appendable} (via {@link #render}) and also stores the
   * result to a String.
   */
  public final String renderAsString() {
    if (content != null) {
      return content;
    }
    StringBuilder sb = new StringBuilder();
    try {
      render(sb);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return sb.toString();
  }

  /**
   * An {@link Appendable} that forwards to a delegate appenable but also saves all the same
   * forwarded content into a buffer.
   *
   * <p>See: <a href="http://en.wikipedia.org/wiki/Tee_%28command%29">Tee command for the unix
   * command on which this is based.
   */
  private static final class TeeAppendable implements Appendable {
    final StringBuilder buffer = new StringBuilder();
    final Appendable delegate;

    TeeAppendable(Appendable delegate) {
      this.delegate = delegate;
    }

    @Override
    public Appendable append(CharSequence csq) throws IOException {
      delegate.append(csq);
      buffer.append(csq);
      return this;
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException {
      delegate.append(csq, start, end);
      buffer.append(csq, start, end);
      return this;
    }

    @Override
    public Appendable append(char c) throws IOException {
      delegate.append(c);
      buffer.append(c);
      return this;
    }
  }
}
