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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.api.RenderResult.Type;

import java.io.IOException;

/**
 * A renderable <a href="http://en.wikipedia.org/wiki/Thunk">thunk</a>.
 *
 * <p>Subclasses should override {@link #doRender(Appendable)} or  
 * {@link #doRender(AdvisingAppendable)} methods to implement the rendering logic.
 * 
 * <p>The {@link #doRender(AdvisingAppendable)} is an optional method that is exposed for use by the
 * {@code jbcsrc} backend.  Tofu callers are neither required, nor expected to implement it.
 */
public abstract class RenderableThunk {
  private String content;
  private TeeAdvisingAppendable partialRenderOutput;

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

  /** 
   * Renders the thunk directly to the {@link AdvisingAppendable} potentially detaching partway
   * through the render operation.
   */
  public final RenderResult render(AdvisingAppendable appendable, boolean isLast) 
      throws IOException {
    if (content == null) {
      if (isLast) {
        return doRender(appendable);
      } else {
        TeeAdvisingAppendable teeAppendable = partialRenderOutput;
        if (teeAppendable == null) {
          teeAppendable = partialRenderOutput = new TeeAdvisingAppendable(appendable);
        }
        RenderResult result = doRender(teeAppendable);
        if (result.type() == Type.DONE) {
          content = teeAppendable.buffer.toString();
          partialRenderOutput = null;
        }
        return result;
      }
    } else {
      appendable.append(content);
      return RenderResult.done();
    }
  }

  /**
   * Renders this thunk to the given {@link AdvisingAppendable} potentially detaching part of the
   * way through.
   *
   * @param appendable The output target
   * @return An object representing whether or not rendering completed or was halted halfway through
   * @throws IOException If there is an error writing to the output stream.
   */
  protected RenderResult doRender(AdvisingAppendable appendable) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * @param appendable
   * @throws IOException
   */
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
   * <p>See: <a href="http://en.wikipedia.org/wiki/Tee_%28command%29">Tee command</p> for the unix
   * command on which this is based.
   */
  private static final class TeeAppendable implements Appendable {
    final StringBuilder buffer = new StringBuilder();
    final Appendable delegate;

    TeeAppendable(Appendable delegate) {
      this.delegate = delegate;
    }

    @Override public Appendable append(CharSequence csq) throws IOException {
      delegate.append(csq);
      buffer.append(csq);
      return this;
    }

    @Override public Appendable append(CharSequence csq, int start, int end) throws IOException {
      delegate.append(csq, start, end);
      buffer.append(csq, start, end);
      return this;
    }

    @Override public Appendable append(char c) throws IOException {
      delegate.append(c);
      buffer.append(c);
      return this;
    }
  }

  /**
   * An {@link AdvisingAppendable} that delegates to a StringBuilder.
   * 
   * <p>NOTE: {@link #softLimitReached()} is hard coded to return {@code false}, since it is assumed
   * that users will not care about limiting buffer usage.
   */
  private static final class TeeAdvisingAppendable implements AdvisingAppendable {
    private final StringBuilder buffer = new StringBuilder();
    private final AdvisingAppendable delegate;
    
    public TeeAdvisingAppendable(AdvisingAppendable delegate) {
      this.delegate = checkNotNull(delegate);
    }

    @Override public TeeAdvisingAppendable append(CharSequence s) throws IOException {
      delegate.append(s);
      buffer.append(s);
      return this;
    }

    @Override public TeeAdvisingAppendable append(CharSequence s, int start, int end) 
        throws IOException {
      delegate.append(s, start, end);
      buffer.append(s, start, end);
      return this;
    }

    @Override public TeeAdvisingAppendable append(char c) throws IOException {
      delegate.append(c);
      buffer.append(c);
      return this;
    }

    @Override public boolean softLimitReached() {
      return delegate.softLimitReached();
    }
  }
}
