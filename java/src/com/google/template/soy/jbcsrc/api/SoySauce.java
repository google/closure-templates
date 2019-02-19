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

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.logging.SoyLogger;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.shared.SoyIdRenamingMap;
import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.CheckReturnValue;

/**
 * Main entry point for rendering Soy templates on the server.
 */
public interface SoySauce {
  /** Returns a new {@link Renderer} for configuring and rendering the given template. */
  Renderer renderTemplate(String template);

  /** Returns the transitive set of {@code $ij} params needed to render this template. */
  ImmutableSet<String> getTransitiveIjParamsForTemplate(String templateInfo);

  /** A Renderer can configure rendering parameters and render the template. */
  interface Renderer {
    /** Configures the data to pass to template. */
    Renderer setData(Map<String, ?> record);

    /** Configures the {@code $ij} to pass to the template. */
    Renderer setIj(Map<String, ?> record);

    /**
     * Sets the plugin instances that will be used to for plugins that are implemented with {@code
     * SoyJavaSourceFunctions} that use {@code JavaValueFactory.callInstanceMethod}.
     *
     * <p>Most plugin instances should be associated with the SoySauce instance during construction,
     * but this method can be used to add more if that is not feasible.
     */
    Renderer setPluginInstances(Map<String, Supplier<Object>> pluginInstances);

    /** Configures the {@code {css ..}} renaming map. */
    Renderer setCssRenamingMap(SoyCssRenamingMap cssRenamingMap);

    /** Configures the {@code {xid ..}} renaming map. */
    Renderer setXidRenamingMap(SoyIdRenamingMap xidRenamingMap);

    /**
     * Sets the predicate to use for testing whether or not a given {@code delpackage} is active.
     */
    Renderer setActiveDelegatePackageSelector(Predicate<String> active);

    /** Configures the bundle of translated messages to use. */
    Renderer setMsgBundle(SoyMsgBundle msgs);

    /**
     * When passing a value of true, Soy compiler will render additional HTML comments for runtime
     * inspection.
     */
    Renderer setDebugSoyTemplateInfo(boolean debugSoyTemplateInfo);

    /**
     * Sets the expected content kind.
     *
     * <p>An attempt to render a non-strict template or a strict template with a different kind will
     * fail if this has been called.
     */
    Renderer setExpectedContentKind(ContentKind kind);

    /**
     * Configures the {@link SoyLogger} to use.
     */
    Renderer setSoyLogger(SoyLogger logger);

    /**
     * Renders the configured template to the appendable returning a continuation.
     *
     * <p>All rendering operations performed via this API will return a continuation indicating how
     * and when to {@link WriteContinuation#continueRender() continue rendering}. There are 4
     * possibilities for every rendering operation.
     *
     * <p>Checks the content kind of the template. Non-strict and {@code kind="html"} templates are
     * allowed, unless {@link #setExpectedContentKind} was called. The goal is to prevent accidental
     * rendering of unescaped {@code kind="text"} in contexts where that could lead to XSS.
     *
     * <ul>
     *   <li>The render operation may complete successfully. This is indicated by the fact that
     *       {@code continuation.result().isDone()} will return {@code true}.
     *   <li>The render operation may pause because the {@link AdvisingAppendable output buffer}
     *       asked render to stop by returning {@code true} from {@link
     *       AdvisingAppendable#softLimitReached()}. In this case {@code contuation.result().type()}
     *       will be {@code RenderResult.Type#LIMITED}. The caller can {@link
     *       WriteContinuation#continueRender() continue rendering} when the appendable is ready for
     *       additional data.
     *   <li>The render operation may pause because the we encountered an incomplete {@code Future}
     *       parameter. In this case {@code contuation.result().type()} will be {@code
     *       RenderResult.Type#DETACH} and the future in question will be accessible via the {@link
     *       RenderResult#future()} method. The caller can {@link WriteContinuation#continueRender()
     *       continue rendering} when the future is done.
     *   <li>The render operation may throw an {@link IOException} if the output buffer does. In
     *       this case rendering may not be continued and behavior is undefined if it is.
     * </ul>
     *
     * <p>It is safe to call this method multiple times, but each call will initiate a new render of
     * the configured template. To continue rendering a template you must use the returned
     * continuation.
     */
    @CheckReturnValue
    WriteContinuation render(AdvisingAppendable out) throws IOException;

    /**
     * Renders the template to a string.
     *
     * <p>The rendering semantics are the same as for {@link #render()} with the following 2 caveats
     *
     * <ul>
     *   <li>The returned continuation will never have a result of {@code RenderResult.Type#LIMITED}
     *   <li>This api doesn't throw {@link IOException}
     * </ul>
     *
     * <p>Checks the content kind of the template. Non-strict and {@code kind="html"} templates are
     * allowed, unless {@link #setExpectedContentKind} was called. The goal is to prevent accidental
     * rendering of unescaped {@code kind="text"} in contexts where that could lead to XSS.
     *
     * <p>It is safe to call this method multiple times, but each call will initiate a new render of
     * the configured template. To continue rendering a template you must use the returned
     * continuation.
     */
    @CheckReturnValue
    Continuation<String> render();

    /**
     * Renders the template to a string.
     *
     * <p>The rendering semantics are the same as for {@link #render()} with the following 2 caveats
     *
     * <ul>
     *   <li>The returned continuation will never have a result of {@code RenderResult.Type#LIMITED}
     *   <li>This api doesn't throw {@link IOException}
     * </ul>
     *
     * <p>Checks the content kind of the template. Non-strict and {@code kind="html"} templates are
     * allowed, unless {@link #setExpectedContentKind} was called. The goal is to prevent accidental
     * rendering of unescaped {@code kind="text"} in contexts where that could lead to XSS.
     *
     * <p>It is safe to call this method multiple times, but each call will initiate a new render of
     * the configured template. To continue rendering a template you must use the returned
     * continuation.
     */
    @CheckReturnValue
    Continuation<SanitizedContent> renderStrict();
  }

  /**
   * A write continuation is the result of rendering to an output stream.
   *
   * <p>See {@link SoySauce.Renderer#render()}, {@link SoySauce.Renderer#renderStrict()}, and {@link
   * Continuation} for similar APIs designed for rendering to strings.
   */
  interface WriteContinuation {
    /** The result of the prior rendering operation. */
    RenderResult result();

    /**
     * Continues rendering if the prior render operation didn't complete.
     *
     * <p>This method has the same contract as {@link Renderer#render(AdvisingAppendable)} for the
     * return value.
     *
     * @throws IllegalStateException if this continuation is already complete, i.e. {@code
     *     result().isDone()} is {@code true}, or if this method has already been called.
     */
    @CheckReturnValue
    WriteContinuation continueRender() throws IOException;
  }

  /**
   * A render continuation that has a final result.
   *
   * <p>See {@link SoySauce.Renderer#render(AdvisingAppendable)}, and {@link WriteContinuation} for
   * similar APIs designed for rendering to output streams.
   *
   * @param <T> Either a {@link String} or a {@link SanitizedContent} object.
   */
  interface Continuation<T> {
    /** The result of the prior rendering operation. */
    RenderResult result();

    /**
     * The final value of the rendering operation.
     *
     * @throws IllegalStateException
     */
    T get();

    /**
     * Continues rendering if the prior render operation didn't complete.
     *
     * <p>This method has the same contract as {@link Renderer#render(AdvisingAppendable)} for the
     * return value.
     *
     * @throws IllegalStateException if this continuation is already complete, i.e. {@code
     *     result().isDone()} is {@code true}, or if this method has already been called.
     */
    @CheckReturnValue
    Continuation<T> continueRender();
  }
}
