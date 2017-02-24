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

package com.google.template.soy.jbcsrc.runtime;

import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * A special implementation of {@link SoyValueProvider} to use as a shared base class for the {@code
 * jbcsrc} implementations of the generated {@code LetContentNode} and {@code CallParamContentNode}
 * implementations.
 */
public abstract class DetachableContentProvider implements SoyValueProvider {
  @Nullable private final ContentKind contentKind;

  // Will be either a SanitizedContent or a StringData.
  private SoyString resolvedValue;

  // Will be either an AdvisingStringBuilder or a TeeAdvisingAppendable depending on whether we are
  // being resolved via 'status()' or via 'renderAndResolve()'
  private AdvisingAppendable builder;

  protected DetachableContentProvider(@Nullable ContentKind contentKind) {
    this.contentKind = contentKind;
  }

  @Override
  public final SoyValue resolve() {
    SoyString local = resolvedValue;
    checkState(local != null, "called resolve() before status() returned ready.");
    checkState(
        local != TombstoneValue.INSTANCE,
        "called resolve() after calling renderAndResolve with isLast == true");
    return local;
  }

  @Override
  public final RenderResult status() {
    if (resolvedValue != null) {
      return RenderResult.done();
    }
    AdvisingStringBuilder currentBuilder = (AdvisingStringBuilder) builder;
    if (currentBuilder == null) {
      builder = currentBuilder = new AdvisingStringBuilder();
    }
    return doRenderIntoBufferingAppendable(currentBuilder);
  }

  @Override
  public RenderResult renderAndResolve(AdvisingAppendable appendable, boolean isLast)
      throws IOException {
    SoyValue value = resolvedValue;
    if (value != null) {
      value.render(appendable);
      return RenderResult.done();
    }
    if (isLast) {
      RenderResult result = doRender(appendable);
      if (result.isDone()) {
        resolvedValue = TombstoneValue.INSTANCE;
      }
      return result;
    }
    TeeAdvisingAppendable currentBuilder = (TeeAdvisingAppendable) builder;
    if (currentBuilder == null) {
      builder = currentBuilder = new TeeAdvisingAppendable(appendable);
    }
    return doRenderIntoBufferingAppendable(currentBuilder);
  }

  private RenderResult doRenderIntoBufferingAppendable(AdvisingAppendable target) {
    RenderResult result = doRender(target);
    if (result.isDone()) {
      if (contentKind != null) {
        resolvedValue = UnsafeSanitizedContentOrdainer.ordainAsSafe(target.toString(), contentKind);
      } else {
        resolvedValue = StringData.forValue(target.toString());
      }
    }
    return result;
  }

  /** Overridden by generated subclasses to implement lazy detachable resolution. */
  protected abstract RenderResult doRender(AdvisingAppendable appendable);

  /**
   * An {@link AdvisingAppendable} that forwards to a delegate appendable but also saves all the
   * same forwarded content into a buffer.
   *
   * <p>See: <a href="http://en.wikipedia.org/wiki/Tee_%28command%29">Tee command for the unix
   * command on which this is based.
   */
  private static final class TeeAdvisingAppendable implements AdvisingAppendable {
    final StringBuilder buffer = new StringBuilder();
    final AdvisingAppendable delegate;

    TeeAdvisingAppendable(AdvisingAppendable delegate) {
      this.delegate = delegate;
    }

    @Override
    public AdvisingAppendable append(CharSequence csq) throws IOException {
      delegate.append(csq);
      buffer.append(csq);
      return this;
    }

    @Override
    public AdvisingAppendable append(CharSequence csq, int start, int end) throws IOException {
      delegate.append(csq, start, end);
      buffer.append(csq, start, end);
      return this;
    }

    @Override
    public AdvisingAppendable append(char c) throws IOException {
      delegate.append(c);
      buffer.append(c);
      return this;
    }

    @Override
    public boolean softLimitReached() {
      return delegate.softLimitReached();
    }

    @Override
    public String toString() {
      return buffer.toString();
    }
  }
}
