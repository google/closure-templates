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

import com.google.common.annotations.Beta;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyTemplate;
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

  /**
   * Returns a new {@link Renderer} for configuring and rendering the given template. The returned
   * renderer will have its data set and may not allow additional calls to {@link Renderer#setData}.
   */
  @Beta
  default Renderer newRenderer(SoyTemplate params) {
    return renderTemplate(params.getTemplateName()).setData(params.getParamsAsMap());
  }

  /**
   * Returns the transitive set of {@code $ij} params needed to render this template.
   *
   * <p>NOTE: this will return a super-set of the parameters that will actually be used at runtime,
   * this is because it doesn't take delpackages or conditional logic inside templates into account.
   */
  ImmutableSet<String> getTransitiveIjParamsForTemplate(String templateInfo);

  /**
   * Returns all css module namespaces needed to render this template. This follows css through
   * deltemplate mods and optionally follows delvariants.
   */
  ImmutableList<String> getAllRequiredCssNamespaces(
      String templateName, Predicate<String> enabledDelpackages, boolean collectCssFromDelvariants);

  /**
   * Indicates whether the current {@link SoySauce} instance holds a given template.
   *
   * @return `true` if the template is valid and `false` if it is unrecognized.
   */
  Boolean hasTemplate(String template);

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
     * <p>An attempt to render a template with a different kind will fail if this has
     * been called.
     *
     * @deprecated Use type-specific render methods instead of setting an expected
     *     content kind before rendering (e.g. {@link #renderHtml()},
     *     {@link #renderCss()}, etc. will verify the content kind at render time).
     * TODO(b/138750285): Delete this method in July 2020.
     */
    @Deprecated Renderer setExpectedContentKind(ContentKind kind);

    /** Configures the {@link SoyLogger} to use. */
    Renderer setSoyLogger(SoyLogger logger);

    /**
     * Renders the configured html template to the given appendable, returning a continuation (more
     * details below). Verifies that the content type is {@link ContentKind.HTML} (corresponding to
     * kind="html" in the template).
     *
     * <p>All of the #renderFoo(Appendable) methods in this API return a continuation indicating how
     * and when to {@link WriteContinuation#continueRender() continue rendering}. There are 4
     * possibilities for every rendering operation.
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
    WriteContinuation renderHtml(AdvisingAppendable out) throws IOException;

    /**
     * Renders the configured html template to a {@link SanitizedContent}. Verifies that the content
     * type is {@link ContentKind.HTML} (corresponding to kind="html" in the template).
     *
     * <p>For all of the #renderFoo() methods in this API (e.g. {@link #renderCss()}, {@link
     * #renderUri()}, etc.), the rendering semantics are the same as for {@link
     * #renderFoo(AdvisingAppendable)} with the following 2 caveats.
     *
     * <ul>
     *   <li>The returned continuation will never have a result of {@code RenderResult.Type#LIMITED}
     *   <li>This api doesn't throw {@link IOException}
     * </ul>
     *
     * <p>It is safe to call this method multiple times, but each call will initiate a new render of
     * the configured template. To continue rendering a template you must use the returned
     * continuation.
     */
    @CheckReturnValue
    Continuation<SanitizedContent> renderHtml();

    /**
     * Renders the configured js template to the given appendable, returning a continuation.
     *
     * <p>Verifies that the content type is {@link ContentKind.JS} (corresponding to kind="js" in
     * the template).
     *
     * <p>See {@link #renderHtml(AdvisingAppendable out)} for more details).
     */
    @CheckReturnValue
    WriteContinuation renderJs(AdvisingAppendable out) throws IOException;

    /**
     * Renders the configured js template to a {@link SanitizedContent}.
     *
     * <p>Verifies that the content type is {@link ContentKind.JS} (corresponding to kind="js" in
     * the template).
     *
     * <p>See {@link #renderHtml()} for more details.
     */
    @CheckReturnValue
    Continuation<SanitizedContent> renderJs();

    /**
     * Renders the configured uri template to the given appendable, returning a continuation.
     *
     * <p>Verifies that the content type is {@link ContentKind.URI} (corresponding to kind="uri" in
     * the template).
     *
     * <p>See {@link #renderHtml(AdvisingAppendable out)} for more details).
     */
    @CheckReturnValue
    WriteContinuation renderUri(AdvisingAppendable out) throws IOException;

    /**
     * Renders the configured uri template to a {@link SanitizedContent}.
     *
     * <p>Verifies that the content type is {@link ContentKind.URI} (corresponding to kind="uri" in
     * the template).
     *
     * <p>See {@link #renderHtml()} for more details.
     */
    @CheckReturnValue
    Continuation<SanitizedContent> renderUri();

    /**
     * Renders the configured trusted resource uri template to the given appendable, returning a
     * continuation.
     *
     * <p>Verifies that the content type is {@link ContentKind.TRUSTED_RESOURCE_URI} (corresponding
     * to kind="trusted_resource_uri" in the template).
     *
     * <p>See {@link #renderHtml(AdvisingAppendable out)} for more details.
     */
    @CheckReturnValue
    WriteContinuation renderTrustedResourceUri(AdvisingAppendable out) throws IOException;

    /**
     * Renders the configured template to a {@link SanitizedContent}.
     *
     * <p>Verifies that the content type is {@link ContentKind.TRUSTED_RESOURCE_URI} (corresponding
     * to kind="trusted_resource_uri" in the template).
     *
     * <p>See {@link #renderHtml()} for more details.
     */
    @CheckReturnValue
    Continuation<SanitizedContent> renderTrustedResourceUri();

    /**
     * Renders the configured template to the given appendable, returning a continuation.
     *
     * <p>Verifies that the content type is {@link ContentKind.ATTRIBUTES} (corresponding to
     * kind="attributes" in the template).
     *
     * <p>See {@link #renderHtml(AdvisingAppendable out)} for more details.
     */
    @CheckReturnValue
    WriteContinuation renderAttributes(AdvisingAppendable out) throws IOException;

    /**
     * Renders the configured template to a {@link SanitizedContent}.
     *
     * <p>Verifies that the content type is {@link ContentKind.ATTRIBUTES} (corresponding to
     * kind="attributes" in the template).
     *
     * <p>See {@link #renderHtml()} for more details.
     */
    @CheckReturnValue
    Continuation<SanitizedContent> renderAttributes();

    /**
     * Renders the configured template to the given appendable, returning a continuation.
     *
     * <p>Verifies that the content type is {@link ContentKind.CSS} (corresponding to kind="css" in
     * the template).
     *
     * <p>See {@link #renderHtml(AdvisingAppendable out)} for more details.
     */
    @CheckReturnValue
    WriteContinuation renderCss(AdvisingAppendable out) throws IOException;

    /**
     * Renders the configured template to a {@link SanitizedContent} of kind {@link
     * ContentKind.CSS}.
     *
     * <p>Verifies that the content type is css (corresponding to kind="css" in the template).
     *
     * <p>See {@link #renderHtml()} for more details.
     */
    @CheckReturnValue
    Continuation<SanitizedContent> renderCss();

    /**
     * Renders the configured template to the given appendable, returning a continuation.
     *
     * <p>This method does not verify the template {@link ContentKind}, since any template can be
     * rendered as text. Prefer {@link #renderCss()}, {@link #renderUri()}, etc. for type checking.
     *
     * <p>See {@link #renderHtml(AdvisingAppendable out)} for more details.
     */
    @CheckReturnValue
    WriteContinuation renderText(AdvisingAppendable out) throws IOException;

    /**
     * Renders the configured template to a {@link String}.
     *
     * <p>This method does not verify the template {@link ContentKind}, since any template can be
     * rendered as text. Prefer {@link #renderCss()}, {@link #renderUri()}, etc. for type checking,
     * and then call toString() to get a string.
     *
     * <p>See {@link #renderHtml()} for more details.
     */
    @CheckReturnValue
    Continuation<String> renderText();

    /**
     * Renders the template to a string.
     *
     * <p>The rendering semantics are the same as for {@link #render(AdvisingAppendable out)} with
     * the following 2 caveats.
     *
     * <ul>
     *   <li>The returned continuation will never have a result of {@code RenderResult.Type#LIMITED}
     *   <li>This api doesn't throw {@link IOException}
     * </ul>
     *
     * <p>Checks the content kind of the template. {@code kind="html"} templates are allowed, unless
     * {@link #setExpectedContentKind} was called. The goal is to prevent accidental rendering of
     * unescaped {@code kind="text"} in contexts where that could lead to XSS.
     *
     * <p>It is safe to call this method multiple times, but each call will initiate a new render of
     * the configured template. To continue rendering a template you must use the returned
     * continuation.
     *
     * @deprecated For text content, use {@link #renderText()} directly. Otherwise, use {@link
     *     #renderHtml()}, {@link #renderCss()}, etc. to verify the content type, and then call
     *     toString() to get a string.
     */
    @CheckReturnValue
    @Deprecated
    Continuation<String> render();

    /**
     * Renders the configured template to the appendable returning a continuation.
     *
     * <p>All rendering operations performed via this API will return a continuation indicating how
     * and when to {@link WriteContinuation#continueRender() continue rendering}. There are 4
     * possibilities for every rendering operation.
     *
     * <p>Checks the content kind of the template. {@code kind="html"} templates are allowed, unless
     * {@link #setExpectedContentKind} was called. The goal is to prevent accidental rendering of
     * unescaped {@code kind="text"} in contexts where that could lead to XSS.
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
     *
     * @deprecated Use {@link #renderHtml(AdvisingAppendable)}, {@link
     *     #renderJs(AdvisingAppendable)}, etc. directly.
     */
    @CheckReturnValue
    @Deprecated
    WriteContinuation render(AdvisingAppendable out) throws IOException;

    /**
     * Renders the template to a {@link SanitizedContent}.
     *
     * <p>The rendering semantics are the same as for {@link #render(AdvisingAppendable)} with the
     * following 2 caveats.
     *
     * <ul>
     *   <li>The returned continuation will never have a result of {@code RenderResult.Type#LIMITED}
     *   <li>This api doesn't throw {@link IOException}
     * </ul>
     *
     * <p>Checks the content kind of the template. {@code kind="html"} templates are allowed, unless
     * {@link #setExpectedContentKind} was called. The goal is to prevent accidental rendering of
     * unescaped {@code kind="text"} in contexts where that could lead to XSS.
     *
     * <p>It is safe to call this method multiple times, but each call will initiate a new render of
     * the configured template. To continue rendering a template you must use the returned
     * continuation.
     *
     * @deprecated Use {@link #renderHtml()}, {@link #renderJs()}, {@link #renderCss()}, etc.
     *     directly.
     */
    @CheckReturnValue
    @Deprecated
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
  interface Continuation< T> {
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
