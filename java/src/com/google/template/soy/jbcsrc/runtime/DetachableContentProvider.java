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

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.NodeBuilder;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A special implementation of {@link SoyValueProvider} to use as a shared base class for the {@code
 * jbcsrc} implementations of the generated {@code LetContentNode} and {@code CallParamContentNode}
 * implementations.
 */
public abstract class DetachableContentProvider extends SoyValueProvider {

  private boolean isDone = false;
  private final MultiplexingAppendable appendable;
  private StackFrame frame;

  protected DetachableContentProvider(ContentKind kind) {
    this.appendable = MultiplexingAppendable.create(kind);
  }

  // This is called for an 'optimistic' evaluation when it fails.
  protected DetachableContentProvider(StackFrame frame, MultiplexingAppendable appendable) {
    this.appendable = appendable;
    this.frame = frame;
  }

  @Override
  public final SoyValue resolve() {
    if (!isDone) {
      JbcSrcRuntime.awaitProvider(this);
    }
    return appendable.getAsSoyValue();
  }

  @Override
  public final RenderResult status() {
    if (isDone) {
      return RenderResult.done();
    }
    var localAppendable = this.appendable;
    StackFrame local = frame = doRender(frame, localAppendable);

    if (local == null) {
      isDone = true;
      return RenderResult.done();
    }
    return local.asRenderResult();
  }

  @Override
  public final RenderResult renderAndResolve(LoggingAdvisingAppendable appendable)
      throws IOException {
    if (isDone) {
      this.appendable.replayFinishedOn(appendable);
      return RenderResult.done();
    }

    var localAppendable = this.appendable;
    int delegateIndex = localAppendable.addDelegate(appendable);
    StackFrame local = frame = doRender(frame, localAppendable);
    if (local == null) {
      isDone = true;
      localAppendable.removeDelegate(delegateIndex, appendable);
      return RenderResult.done();
    }
    return local.asRenderResult();
  }

  @Override
  public SoyValueProvider coerceToBooleanProvider() {
    if (isDone) {
      // If already resolved, coerce to boolean.
      return BooleanData.forValue(appendable.getAsSoyValue().coerceToBoolean());
    }

    var kind = appendable.getSanitizedContentKind();
    if (kind != ContentKind.TEXT) {
      // SanitizedContent is always truthy.
      return BooleanData.TRUE;
    }
    if (!appendable.isEmpty()) {
      return BooleanData.TRUE;
    }

    var delegate = this;

    // Fall back to evaluating truthy as soon as there's any content.
    return new SoyValueProvider() {
      private BooleanData resolvedValue;

      @Override
      public SoyValue resolve() {
        if (resolvedValue != null) {
          return resolvedValue;
        }
        JbcSrcRuntime.awaitProvider(this);
        return resolvedValue;
      }

      @Override
      public RenderResult status() {
        var status = delegate.status();

        if (status.isDone()) {
          resolvedValue = BooleanData.forValue(delegate.resolve().coerceToBoolean());
          return RenderResult.done();
        }
        if (!delegate.appendable.isEmpty()) {
          resolvedValue = BooleanData.TRUE;
          return RenderResult.done();
        }
        return status;
      }

      @Override
      public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable)
          throws IOException {
        RenderResult result = status();
        if (result.isDone()) {
          resolve().render(appendable);
        }
        return result;
      }
    };
  }

  /** Overridden by generated subclasses to implement lazy detachable resolution. */
  @Nullable
  protected abstract StackFrame doRender(
      @Nullable StackFrame frame, MultiplexingAppendable appendable);

  /**
   * An {@link AdvisingAppendable} that forwards to a set of delegate appendables but also saves all
   * the same forwarded content into a buffer.
   *
   * <p>There are two cases:
   *
   * <p>A let/param is printed using renderAndResolve(), which will add the output buffer as a
   * delegate, which will execute any NodeBuilder.
   *
   * <p>A let/param is coerced to a SoyValue using status()/resolve(). In this case, if there are
   * any NodeBuilders, appendNodeBuilder() will add a delegate to execute the NodeBuilder, and this
   * delegate is used for the first getAsSoyValue() call.
   *
   * <p>In both cases, any further accesses will use the non-delegate/"local" appendable which will
   * contain the unflattened NodeBuilders and will re-execute. These won't block though since we
   * know the NodeBuilder has been successfully executed at least once already so all Futures will
   * be resolved.
   */
  public static final class MultiplexingAppendable extends BufferingAppendable {

    public static MultiplexingAppendable create(ContentKind kind) {
      return new MultiplexingAppendable(kind);
    }

    private MultiplexingAppendable(ContentKind kind) {
      super(kind);
    }

    // Lazily initialized to avoid allocations in the common case of optimistic evaluation
    // succeeding or there being no delegates (because we're capturing as a SoyValue with no lazy
    // calls). The second most common case is a single delegate, so we could consider special casing
    // that but it is probably not worth it and would add a lot of complexity.
    List<LoggingAdvisingAppendable> delegates;

    // The resolved SoyValue. Will be either a SanitizedContent or a StringData. If there are any
    // NodeBuilders, they will remain unflattened, as a command in a CommandBuffer, so coercing to
    // a SoyValue multiple times will re-exeute the NodeBuilder each time.
    private SoyValue resolvedSoyValue = null;

    // A delegate that is created when a NodeBuilder is found and there is no existing delegate. It
    // causes NodeBuilders to be invoked and flattened. It is only used for the first
    // getAsSoyValue() call.
    private BufferingAppendable flatteningAppendable = null;

    @Override
    @Nonnull
    public SoyValue getAsSoyValue() {
      if (resolvedSoyValue != null) {
        return resolvedSoyValue;
      }
      if (flatteningAppendable != null) {
        SoyValue result = flatteningAppendable.getAsSoyValue();
        // Clear this.flatteningAppendable so that subsequent getAsSoyValue() calls return an
        // unflattened version and so will re-execute any NodeBuilders.
        flatteningAppendable = null;
        return result;
      }
      resolvedSoyValue = super.getAsSoyValue();
      return resolvedSoyValue;
    }

    int addDelegate(LoggingAdvisingAppendable delegate) throws IOException {
      // We don't want to add the same delegate multiple times.
      // a linear scan is sufficient since typically there is only one delegate
      var delegates = this.delegates;
      if (delegates == null) {
        delegates = new ArrayList<>();
        delegates.add(delegate);
        this.delegates = delegates;
        super.replayOn(delegate);
        return 0;
      }
      int index = delegates.indexOf(delegate);
      if (index == -1) {
        index = delegates.size();
        super.replayOn(delegate);
        delegates.add(delegate);
      }
      return index;
    }

    void replayFinishedOn(LoggingAdvisingAppendable appendable) throws IOException {
      var delegates = this.delegates;
      if (delegates != null && delegates.remove(appendable)) {
        return;
      }
      super.replayOn(appendable);
    }

    void removeDelegate(int delegateIndex, LoggingAdvisingAppendable expected) {
      var actual = delegates.remove(delegateIndex);
      if (actual != expected) {
        throw new AssertionError("impossible");
      }
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
      super.append(csq);
      var delegates = this.delegates;
      if (delegates == null) {
        return this;
      }
      int size = delegates.size();
      for (int i = 0; i < size; i++) {
        delegates.get(i).append(csq);
      }
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
        throws IOException {
      super.append(csq, start, end);
      var delegates = this.delegates;
      if (delegates == null) {
        return this;
      }
      int size = delegates.size();
      for (int i = 0; i < size; i++) {
        delegates.get(i).append(csq, start, end);
      }
      return this;
    }

    @Override
    public LoggingAdvisingAppendable append(char c) throws IOException {
      super.append(c);
      var delegates = this.delegates;
      if (delegates == null) {
        return this;
      }
      int size = delegates.size();
      for (int i = 0; i < size; i++) {
        delegates.get(i).append(c);
      }
      return this;
    }

    @Override
    public boolean softLimitReached() {
      // don't need to query our base class, it always returns false
      var delegates = this.delegates;
      if (delegates == null) {
        return false;
      }
      int size = delegates.size();
      for (int i = 0; i < size; i++) {
        if (delegates.get(i).softLimitReached()) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void flushBuffers(int depth) {
      // This is a 'root' appendable so while we have a delegate there should never be a case where
      // we need to be flushed through.
      throw new AssertionError("should not be called");
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      super.enterLoggableElement(statement);
      var delegates = this.delegates;
      if (delegates == null) {
        return this;
      }
      int size = delegates.size();
      for (int i = 0; i < size; i++) {
        delegates.get(i).enterLoggableElement(statement);
      }
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      super.exitLoggableElement();
      var delegates = this.delegates;
      if (delegates == null) {
        return this;
      }
      int size = delegates.size();
      for (int i = 0; i < size; i++) {
        delegates.get(i).exitLoggableElement();
      }
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      super.appendLoggingFunctionInvocation(funCall, escapers);
      var delegates = this.delegates;
      if (delegates == null) {
        return this;
      }
      int size = delegates.size();
      for (int i = 0; i < size; i++) {
        delegates.get(i).appendLoggingFunctionInvocation(funCall, escapers);
      }
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    @Nullable
    public StackFrame appendNodeBuilder(NodeBuilder nodeBuilder) throws IOException {
      if (delegates == null) {
        // No delegates, we must be capturing as a SoyValue, e.g. to pass to an extern. Add a
        // delegate that will execute the NodeBuilder.
        flatteningAppendable = LoggingAdvisingAppendable.buffering(getSanitizedContentKind());
        var unusedIndex = addDelegate(flatteningAppendable);
      }
      int size = delegates.size();
      if (size > 1 || size == 0) {
        throw new VerifyException(
            "MultiplexingAppendable.appendNodeBuilder() called with multiple delegates. Multiple"
                + " delegates should only happen with eager/optimisitc evaluation which"
                + " kind=\"html\" is not eligible for.");
      }
      StackFrame delegateFrame = delegates.get(0).appendNodeBuilder(nodeBuilder);
      if (delegateFrame == null) {
        // Add the NodeBuilder to the local appendable only once the delegate is finished, so that
        // we don't add it multiple times if the delegate detaches.
        getCommandsAndAddPendingStringData().add(nodeBuilder);
        return null;
      }
      return delegateFrame;
    }
  }
}
