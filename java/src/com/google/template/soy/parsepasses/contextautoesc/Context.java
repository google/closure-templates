/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Encapsulates the context in which a Soy node appears.
 * This helps us distinguish Soy nodes that can only be preceded by fully formed HTML tags and text
 * chunks from ones that appear inside JavaScript, from ones that appear inside URIs, etc.
 *
 * <p>
 * This is an immutable bit-packable struct that contains a number of enums.
 * These enums have their own nullish values like {@link Context.ElementType#NONE} so should always
 * be non-null.
 *
 * <p>
 * The contextual autoescape rewriter propagates contexts so that it can infer an appropriate
 * {@link EscapingMode escaping function} for each <code>{print ...}</code> command.
 *
 * <p>
 * To make sure it can correctly identify a unique escape convention for all paths to a particular
 * print command, it may clone a template for each context in which it is called, using the
 * {@link Context#packedBits bitpacked} form of the context to generate a unique template name.
 *
 */
public final class Context {

  /** The state the text preceding the context point describes. */
  public final State state;

  /**
   * Describes the innermost element that the text preceding the context point is in.
   * An element is considered entered once its name has been seen in the start tag and is considered
   * closed once the name of its end tag is seen.
   * E.g. the open point is marked with O below and C marks the close point.
   * {@code
   * <b id="boldly-going">Hello, World!</b >
   *   ^                                  ^
   *   O                                  C
   * }
   * Outside an element, or in PCDATA text, this will be the nullish value {@link ElementType#NONE}.
   */
  public final ElementType elType;

  /**
   * Describes the attribute whose value the context point is in.
   * Outside an attribute value, this will be the nullish value {@link AttributeType#NONE}.
   */
  public final AttributeType attrType;

  /**
   * Describes the quoting convention for the attribute value that the context point is in.
   * Outside an attribute value, this will be the nullish value {@link AttributeEndDelimiter#NONE}.
   */
  public final AttributeEndDelimiter delimType;

  /**
   * Determines what we will do with a slash token {@code /}.  This is irrelevant outside JavaScript
   * contexts, but inside JavaScript, it helps us distinguish the contexts of <code>{$bar}</code> in
   * <code>"foo".replace(/{$bar}/i)</code> and <code>x/{$bar}/i</code>
   */
  public final JsFollowingSlash slashType;

  /** Determines how we encode interpolations in URI attributes and CSS {@code uri(...)}. */
  public final UriPart uriPart;

  /** The count of {@code <template>} elements entered and not subsequently exited. */
  public final int templateNestDepth;


  /** Use {@link Builder} to construct instances. */
  private Context(
      State state, ElementType elType, AttributeType attrType,
      AttributeEndDelimiter delimType, JsFollowingSlash slashType, UriPart uriPart,
      int templateNestDepth) {
    this.state = state;
    this.elType = elType;
    this.attrType = attrType;
    this.delimType = delimType;
    this.slashType = slashType;
    this.uriPart = uriPart;
    this.templateNestDepth = templateNestDepth;
  }

  /**
   * A context in the given state outside any element, attribute, or Javascript content.
   */
  private Context(State state) {
    this(state, ElementType.NONE, AttributeType.NONE, AttributeEndDelimiter.NONE,
         JsFollowingSlash.NONE, UriPart.NONE, 0);
  }

  /**
   * The normal context for HTML where a less than opens a tag and an ampersand starts an HTML
   * entity.
   */
  public static final Context HTML_PCDATA = new Context(State.HTML_PCDATA);


  /** A special state transitioned to if the CSS/HTML/JS parser cannot compute the next context. */
  public static final Context ERROR = new Context(State.ERROR);


  /** Returns a context that differs only in the state. */
  public Context derive(State state) {
    return state == this.state ? this : toBuilder().withState(state).build();
  }

  /** Returns a context that differs only in the following slash. */
  public Context derive(JsFollowingSlash slashType) {
    return slashType == this.slashType ? this : toBuilder().withSlashType(slashType).build();
  }

  /** Returns a context that differs only in the uri part. */
  public Context derive(UriPart uriPart) {
    return uriPart == this.uriPart ? this : toBuilder().withUriPart(uriPart).build();
  }

  /** A mutable builder that allows deriving variant contexts. */
  Builder toBuilder() {
    return new Builder(this);
  }


  /**
   * The context reached after escaping content using the given mode from this context.
   * This makes an optimistic assumption that the escaped string is not empty, but in practice this
   * makes no difference except to minor differences such as that between {@link UriPart#START} and
   * {@link UriPart#PRE_QUERY}.
   */
  public Context getContextAfterEscaping(@Nullable EscapingMode mode) {
    if (mode == null) {
      return ERROR;
    }
    if (mode == EscapingMode.ESCAPE_JS_VALUE) {
      switch (slashType) {
        case DIV_OP:
        case UNKNOWN:
          return this;
        case REGEX:
          return derive(JsFollowingSlash.DIV_OP);
        case NONE:
          break;  // Error out below.
      }
      throw new IllegalStateException(slashType.name());
    } else if (state == State.HTML_BEFORE_TAG_NAME) {
      return derive(State.HTML_TAG_NAME);
    } else if (state == State.HTML_TAG) {
      // To handle a substitution that starts an attribute name <tag {$attrName}=...>
      return toBuilder()
          .withState(State.HTML_ATTRIBUTE_NAME)
          .withAttrType(AttributeType.PLAIN_TEXT)
          .build();
    } else if (uriPart == UriPart.START) {
      return derive(UriPart.PRE_QUERY);
    }
    return this;
  }


  /**
   * Returns a context that can be used to compute the escaping mode for a dynamic value.
   */
  Context getContextBeforeDynamicValue() {
    // Some epsilon transitions need to be delayed until we get into a branch.
    // For example, we do not transition into an unquoted attribute value context just because
    // the raw text node that contained the "=" did not contain a quote character because the
    // quote character may appear inside branches as in
    //     <a href={if ...}"..."{else}"..."{/if}>
    // which was derived from production code.

    // But we need to force epsilon transitions to happen consistentky before a dynamic value is
    // considered as in
    //    <a href={print $x}>
    // where we consider $x as happening in an unquoted attribute value context, not as occuring
    // before an attribute value.
    if (state == State.HTML_BEFORE_ATTRIBUTE_VALUE) {
      return computeContextAfterAttributeDelimiter(
          elType, attrType, AttributeEndDelimiter.SPACE_OR_TAG_END, templateNestDepth);
    }
    return this;
  }


  /**
   * Computes the context after an attribute delimiter is seen.
   *
   * @param elType The type of element whose tag the attribute appears in.
   * @param attrType The type of attribute whose value the delimiter starts.
   * @param delim The type of delimiter that will mark the end of the attribute value.
   * @param templateNestDepth The number of (@code <template>} elements on the open element stack.
   * @return A context suitable for the start of the attribute value.
   */
  static Context computeContextAfterAttributeDelimiter(
      ElementType elType, AttributeType attrType, AttributeEndDelimiter delim,
      int templateNestDepth) {
    Context.State state;
    Context.JsFollowingSlash slash = Context.JsFollowingSlash.NONE;
    Context.UriPart uriPart = Context.UriPart.NONE;
    switch (attrType) {
      case PLAIN_TEXT:
        state = Context.State.HTML_NORMAL_ATTR_VALUE;
        break;
      case SCRIPT:
        state = Context.State.JS;
        // Start a JS block in a regex state since
        //   /foo/.test(str) && doSideEffect();
        // which starts with a regular expression literal is a valid and possibly useful program,
        // but there is no valid program which starts with a division operator.
        slash = Context.JsFollowingSlash.REGEX;
        break;
      case STYLE:
        state = Context.State.CSS;
        break;
      case URI:
        state = Context.State.URI;
        uriPart = Context.UriPart.START;
        break;
      // NONE is not a valid AttributeType inside an attribute value.
      default: throw new AssertionError("Unexpected attribute type " + attrType);
    }
    return new Context(state, elType, attrType, delim, slash, uriPart, templateNestDepth);
  }


  /**
   * Returns the escaping mode appropriate for dynamic content inserted in this context.
   * @return Empty if there is no appropriate escaping convention to use,
   *     e.g. for comments which do not have escaping conventions.
   */
  public ImmutableList<EscapingMode> getEscapingModes() {
    EscapingMode escapingMode = state.escapingMode;

    // Short circuit on the error return case first.
    if (escapingMode == null) {
      return ImmutableList.<EscapingMode>of();
    }

    // Any additional mode that allows the primary escaping mode's output language to be
    // embedded in the specific quoting context in which it appears.
    EscapingMode extraEscapingMode = null;

    // Make sure we're using the right part for a URI context.
    switch (uriPart) {
      case QUERY:
        escapingMode = EscapingMode.ESCAPE_URI;
        break;
      case START:
        // We need to filter substitutions at the start of a URL since they can switch the protocol
        // to a code loading protocol like javascript:.
        if (escapingMode != EscapingMode.NORMALIZE_URI) {
          extraEscapingMode = escapingMode;
        }
        escapingMode = EscapingMode.FILTER_NORMALIZE_URI;
        break;
      case UNKNOWN:
      case UNKNOWN_PRE_FRAGMENT:
        // We can't choose an appropriate escaping convention if we're in a URI but don't know which
        // part.  E.g. in
        //   <a href="
        //     {if ...}
        //       ?foo=
        //     {else}
        //       /bar/
        //     {/else}
        //     {$baz}">
        // Is {$baz} part of a query or part of a path?
        return ImmutableList.<EscapingMode>of();
      default:
        break;
    }

    // Check the quote embedding mode.
    switch (delimType) {
      case SPACE_OR_TAG_END:
        // Also escape any spaces that could prematurely end the attribute value.
        // E.g. when the value of $s is "was checked" in
        //     <input value={$s}>
        // then we want to emit
        //     <input name=was&#32;checked>
        // instead of
        //     <input name=was checked>
        if (escapingMode == EscapingMode.ESCAPE_HTML_ATTRIBUTE ||
            escapingMode == EscapingMode.NORMALIZE_URI) {
          escapingMode = EscapingMode.ESCAPE_HTML_ATTRIBUTE_NOSPACE;
        } else {
          extraEscapingMode = EscapingMode.ESCAPE_HTML_ATTRIBUTE_NOSPACE;
        }
        break;
      case SINGLE_QUOTE:
      case DOUBLE_QUOTE:
        if (escapingMode == EscapingMode.NORMALIZE_URI) {
          // URI's should still be HTML-escaped to escape ampersands, quotes, and other characters.
          // Normalizing a URI (which mostly percent-encodes quotes) is unnecessary if it's going
          // to be escaped as an HTML attribute, so as a performance optimization, we simply
          // replace the escaper.
          escapingMode = EscapingMode.ESCAPE_HTML_ATTRIBUTE;
        } else if (!escapingMode.isHtmlEmbeddable) {
          // Some modes, like JS and CSS value modes, might insert quotes to make
          // a quoted string, so make sure to escape those as HTML.
          // E.g. when the value of $s is " onmouseover=evil() foo=", in
          //    <a onclick='alert({$s})'>
          // we want to produce
          //    <a onclick='alert(&#39; onmouseover=evil() foo=&#39;)'>
          // instead of
          //    <a onclick='alert(' onmouseover=evil() foo=')'>
          extraEscapingMode = EscapingMode.ESCAPE_HTML_ATTRIBUTE;
        }
        break;
      case NONE:
        break;
    }

    return extraEscapingMode == null ?
        ImmutableList.of(escapingMode) : ImmutableList.of(escapingMode, extraEscapingMode);
  }


  /**
   * Policy for how to handle escaping of a translatable message.
   */
  static final class MsgEscapingStrategy {

    /**
     * The context in which to parse the message itself. This affects how print nodes are escaped.
     */
    final Context childContext;

    /**
     * The escaping directives for the entire message after all print nodes have been substituted.
     */
    final ImmutableList<EscapingMode> escapingModesForFullMessage;

    MsgEscapingStrategy(
        Context childContext, ImmutableList<EscapingMode> escapingModesForFullMessage) {
      this.childContext = childContext;
      this.escapingModesForFullMessage = escapingModesForFullMessage;
    }
  }


  /**
   * Determines the strategy to escape Soy msg tags.
   *
   * <p>Importantly, this determines the context that the message should be considered in, how the
   * print nodes will be escaped, and how the entire message will be escaped.  We need different
   * strategies in different contexts because messages in general aren't trusted, but we also need
   * to be able to include markup interspersed in an HTML message; for example, an anchor that Soy
   * factored out of the message.
   *
   * <p>Note that it'd be very nice to be able to simply escape the strings that came out of the
   * translation database, and distribute the escaping entirely over the print nodes. However, the
   * translation machinery, especially in Javascript, doesn't offer a way to escape just the bits
   * that come from the translation database without also re-escaping the substitutions.
   *
   * @return relevant strategy, or absent in case there's no valid strategy and it is an error to
   *     have a message in this context
   */
  Optional<MsgEscapingStrategy> getMsgEscapingStrategy() {
    switch (state) {
      case HTML_PCDATA:
        // In normal HTML PCDATA context, it makes sense to escape all of the print nodes, but not
        // escape the entire message.  This allows Soy to support putting anchors and other small
        // bits of HTML in messages.
        return Optional.of(new MsgEscapingStrategy(this, ImmutableList.<EscapingMode>of()));

      case CSS_DQ_STRING:
      case CSS_SQ_STRING:
      case JS_DQ_STRING:
      case JS_SQ_STRING:
      case TEXT:
      case URI:
        if (state == State.URI && uriPart != UriPart.QUERY) {
          // NOTE: Only support the query portion of URIs.
          return Optional.<MsgEscapingStrategy>absent();
        }
        // In other contexts like JS and CSS strings, it makes sense to treat the message's
        // placeholders as plain text, but escape the entire result of message evaluation.
        return Optional.of(new MsgEscapingStrategy(new Context(State.TEXT), getEscapingModes()));

      case HTML_RCDATA:
      case HTML_NORMAL_ATTR_VALUE:
      case HTML_COMMENT:
        // The weirdest case is HTML attributes. Ideally, we'd like to treat these as a text string
        // and escape when done.  However, many messages have HTML entities such as &raquo; in them.
        // A good way around this is to escape the print nodes in the message, but normalize
        // (escape except for ampersands) the final message.
        // Also, content inside <title>, <textarea>, and HTML comments have a similar requirement,
        // where any entities in the messages are probably intended to be preserved.
        return Optional.of(
            new MsgEscapingStrategy(this, ImmutableList.of(EscapingMode.NORMALIZE_HTML)));

      default:
        // Other contexts, primarily source code contexts, don't have a meaningful way to support
        // natural language text.
        return Optional.<MsgEscapingStrategy>absent();
    }
  }


  /**
   * True if the given escaping mode could make sense in this context.
   */
  public boolean isCompatibleWith(EscapingMode mode) {
    // TODO: Come up with a compatibility matrix.
    if (mode == EscapingMode.ESCAPE_JS_VALUE) {
      // Don't introduce quotes inside a string.
      switch (state) {
        case JS_SQ_STRING: case JS_DQ_STRING:
        case CSS_SQ_STRING: case CSS_DQ_STRING:
          return false;
        default:
          return true;
      }
    } else if (mode == EscapingMode.TEXT) {
      // The TEXT directive may only be used in TEXT mode; in any other context, it would act as
      // autoescape-cancelling.
      return state == State.TEXT;
    } else if (delimType == AttributeEndDelimiter.SPACE_OR_TAG_END) {
      // Need ESCAPE_HTML_ATTRIBUTE_NOSPACE instead.
      if (mode == EscapingMode.ESCAPE_HTML || mode == EscapingMode.ESCAPE_HTML_ATTRIBUTE ||
          mode == EscapingMode.ESCAPE_HTML_RCDATA) {
        return false;
      }
    }
    return true;
  }


  /**
   * True if this context is in the {@link State#ERROR error} state.
   * @see #ERROR
   */
  public boolean isErrorContext() {
    return state == State.ERROR;
  }


  /**
   * @deprecated Prefer comparing states or predicates like isValidEndContext
   */
  @Deprecated
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Context)) {
      return false;
    }
    Context that = (Context) o;
    return this.state == that.state
        && this.elType == that.elType
        && this.attrType == that.attrType
        && this.delimType == that.delimType
        && this.slashType == that.slashType
        && this.uriPart == that.uriPart
        && this.templateNestDepth == that.templateNestDepth;
  }

  @Override
  public int hashCode() {
    return packedBits();
  }

  /**
   * An integer form that uniquely identifies this context.
   * This form is not guaranteed to be stable across versions,
   * so do not use as a long-lived serialized form.
   */
  public int packedBits() {
    return ((((((((((((
        templateNestDepth
        << N_URI_PART_BITS) | uriPart.ordinal())
        << N_JS_SLASH_BITS) | slashType.ordinal())
        << N_DELIM_BITS) | delimType.ordinal())
        << N_ATTR_BITS) | attrType.ordinal())
        << N_ELEMENT_BITS) | elType.ordinal())
        << N_STATE_BITS) | state.ordinal());
  }

  /** The number of bits needed to store a {@link State} value. */
  private static final int N_STATE_BITS = 5;

  /** The number of bits needed to store a {@link ElementType} value. */
  private static final int N_ELEMENT_BITS = 3;

  /** The number of bits needed to store a {@link AttributeType} value. */
  private static final int N_ATTR_BITS = 3;

  /** The number of bits needed to store a {@link AttributeEndDelimiter} value. */
  private static final int N_DELIM_BITS = 2;

  /** The number of bits needed to store a {@link JsFollowingSlash} value. */
  private static final int N_JS_SLASH_BITS = 2;

  /** The number of bits needed to store a {@link UriPart} value. */
  private static final int N_URI_PART_BITS = 3;

  static {
    // We'd better have enough bits in an int.
    if ((N_STATE_BITS + N_ELEMENT_BITS + N_ATTR_BITS + N_DELIM_BITS + N_JS_SLASH_BITS +
         N_URI_PART_BITS) > 32) {
      throw new AssertionError();
    }
    // And each enum's ordinals must fit in the bits allocated.
    if ((1 << N_STATE_BITS) < State.values().length ||
        (1 << N_ELEMENT_BITS) < ElementType.values().length ||
        (1 << N_ATTR_BITS) < AttributeType.values().length ||
        (1 << N_DELIM_BITS) < AttributeEndDelimiter.values().length ||
        (1 << N_JS_SLASH_BITS) < JsFollowingSlash.values().length ||
        (1 << N_URI_PART_BITS) < UriPart.values().length) {
      throw new AssertionError();
    }
  }

  /**
   * A context which is consistent with both contexts.
   * This should be used when multiple execution paths join, such as the path through the
   * then-clause of an <code>{if}</code> command and the path through the else-clause.
   * @return {@link #ERROR} when there is no such context consistent with both.
   */
  public static Context union(Context a, Context b) {
    // TODO(gboyer): Add a test that TEXT doesn't union with any other type.
    if (a.equals(b)) {
      return a;
    }

    if (a.templateNestDepth != b.templateNestDepth) {
      return ERROR;
    }

    if (a.equals(b.derive(a.slashType))) {
      return a.derive(JsFollowingSlash.UNKNOWN);
    }

    if (a.equals(b.derive(a.uriPart))) {
      return a.derive(
          // If the parts differ but neither could be in the fragment then a ? will conclusively
          // transition into the query state, so use UKNNOWN_PRE_FRAGMENT to allow {print} commands
          // after '?'.  With unknown, {print}s are only allowed after a '#'.
          a.uriPart != UriPart.FRAGMENT && b.uriPart != UriPart.FRAGMENT &&
          a.uriPart != UriPart.UNKNOWN && b.uriPart != UriPart.UNKNOWN ?
          UriPart.UNKNOWN_PRE_FRAGMENT : UriPart.UNKNOWN);
    }

    // Order by state so that we don't have to duplicate tests below.
    if (a.state.compareTo(b.state) > 0) {
      Context swap = a;
      a = b;
      b = swap;
    }

    // If we start in a tag name and end between attributes, then treat us as between attributes.
    // This handles <b{if $bool} attrName="value"{/if}>.
    if (a.state == State.HTML_TAG_NAME && b.state == State.HTML_TAG) {
      // We do not need to compare a.elType and b.elType since in HTML_TAG_NAME,
      // there is no tag name, so no loss of information.
      return b;
    }

    if (a.state == State.HTML_TAG && a.elType == b.elType) {
      // If one branch is waiting for an attribute name and the other is waiting for an equal sign
      // before an attribute value, then commit to the view that the attribute name was a valueless
      // attribute and transition to a state waiting for another attribute name or the end of a tag.
      if (b.state == State.HTML_ATTRIBUTE_NAME ||
          // In an attribute value ended by a delimiter.
          b.delimType == AttributeEndDelimiter.SPACE_OR_TAG_END) {
        // TODO: do we need to require a space before any new attribute name?
        return a;
      }
    }

    return ERROR;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("(Context ").append(state.name());
    if (elType != ElementType.NONE) {
      sb.append(' ').append(elType.name());
    }
    if (attrType != AttributeType.NONE) {
      sb.append(' ').append(attrType.name());
    }
    if (delimType != AttributeEndDelimiter.NONE) {
      sb.append(' ').append(delimType.name());
    }
    if (slashType != JsFollowingSlash.NONE) {
      sb.append(' ').append(slashType.name());
    }
    if (uriPart != UriPart.NONE) {
      sb.append(' ').append(uriPart.name());
    }
    if (templateNestDepth != 0) {
      sb.append(" templateNestDepth=").append(templateNestDepth);
    }
    return sb.append(')').toString();
  }


  /**
   * Returns the autoescape {@link Context} that produces sanitized content of the given
   * {@link ContentKind}.
   *
   * <p>
   * Given a {@link ContentKind}, returns the corresponding {@link Context} such that contextual
   * autoescaping of a block of Soy code with that context as the start context results in a value
   * that adheres to the contract of {@link com.google.template.soy.data.SanitizedContent} of the
   * given kind.
   */
  public static Context getStartContextForContentKind(ContentKind contentKind) {
    return HTML_PCDATA.toBuilder().withStartKind(contentKind).build();
  }


  /**
   * Determines whether a particular context is valid at the start of a block of a particular
   * content kind.
   */
  public boolean isValidStartContextForContentKind(ContentKind contentKind) {
    if (templateNestDepth != 0) {
      return false;
    }
    switch (contentKind) {
      case ATTRIBUTES:
        // Allow HTML attribute names, regardless of the kind of attribute (e.g. plain text)
        // or immediately after an open tag.
        return state == State.HTML_ATTRIBUTE_NAME || state == State.HTML_TAG;
      default:
        // NOTE: For URI's, we need to be picky that the context has no attribute type, since we
        // don't want to forget to escape ampersands.
        return this.equals(getStartContextForContentKind(contentKind));
    }
  }


  /**
   * Determines whether a particular context is allowed for contextual to strict calls.
   *
   * <p>This is slightly more relaxed, and used to help piecemeal transition of templates from
   * contextual to strict.
   */
  public boolean isValidStartContextForContentKindLoose(ContentKind contentKind) {
    switch (contentKind) {
      case URI:
        // Allow contextual templates directly call URI templates, even if we technically need to
        // do HTML-escaping for correct output.  Supported browsers recover gracefully when
        // ampersands are underescaped, as long as there are no nearby semicolons.  However, this
        // special case is limited ONLY to transitional cases, where the caller is contextual and
        // the callee is strict.
        return state == State.URI;
      default:
        return isValidStartContextForContentKind(contentKind);
    }
  }


  private static final ImmutableMap<State, ContentKind> STATE_TO_CONTENT_KIND;
  static {
    Map<State, ContentKind> stateToContextKind = new EnumMap<>(State.class);
    stateToContextKind.put(State.CSS, ContentKind.CSS);
    stateToContextKind.put(State.HTML_PCDATA, ContentKind.HTML);
    stateToContextKind.put(State.HTML_TAG, ContentKind.ATTRIBUTES);
    stateToContextKind.put(State.JS, ContentKind.JS);
    stateToContextKind.put(State.URI, ContentKind.URI);
    stateToContextKind.put(State.TEXT, ContentKind.TEXT);
    STATE_TO_CONTENT_KIND = ImmutableMap.copyOf(stateToContextKind);
  }


  /**
   * Returns the most sensible content kind for a context.
   *
   * <p>This is primarily for error messages, indicating to the user what content kind can be used
   * to mostly null out the escaping. Returns TEXT if no useful match was detected.
   */
  public ContentKind getMostAppropriateContentKind() {
    ContentKind kind = STATE_TO_CONTENT_KIND.get(state);
    if (kind != null && isValidStartContextForContentKindLoose(kind)) {
      return kind;
    }
    return ContentKind.TEXT;
  }


  /**
   * Determines whether a particular context is valid for the end of a block of a particular
   * content kind.
   */
  public final boolean isValidEndContextForContentKind(ContentKind contentKind) {
    if (templateNestDepth != 0) {
      return false;
    }
    switch (contentKind) {
      case CSS:
        return state == State.CSS && elType == ElementType.NONE;
      case HTML:
        return state == State.HTML_PCDATA && elType == ElementType.NONE;
      case ATTRIBUTES:
        // Allow any html attribute context or html tag this. HTML_TAG is needed for constructs
        // like "checked" that don't require an attribute value. Explicitly disallow
        // HTML_NORMAL_ATTR_VALUE (e.g. foo={$x} without quotes) to help catch cases where
        // attributes aren't safely composable (e.g. foo={$x}checked would end up with one long
        // attribute value, whereas foo="{$x}"checked would be parsed as intended).
        return state == State.HTML_ATTRIBUTE_NAME || state == State.HTML_TAG;
      case JS:
        // Just ensure the state is JS -- don't worry about whether a regex is coming or not.
        return state == State.JS && elType == ElementType.NONE;
      case URI:
        // Ensure that the URI content is non-empty.
        return state == State.URI && uriPart != UriPart.START;
      case TEXT:
        return state == State.TEXT;
      default:
        throw new IllegalArgumentException("Specified content kind has no associated end context.");
    }
  }


  /**
   * Returns a plausible human-readable description of a context mismatch;
   * <p>
   * This assumes that the provided context is an invalid end context for the particular content
   * kind.
   */
  public final String getLikelyEndContextMismatchCause(ContentKind contentKind) {
    Preconditions.checkArgument(!isValidEndContextForContentKind(contentKind));
    if (contentKind == ContentKind.ATTRIBUTES) {
      // Special error message for ATTRIBUTES since it has some specific logic.
      return "an unterminated attribute value, or ending with an unquoted attribute";
    }
    switch (state) {
      case HTML_TAG_NAME:
      case HTML_TAG:
      case HTML_ATTRIBUTE_NAME:
      case HTML_NORMAL_ATTR_VALUE:
        return "an unterminated HTML tag or attribute";

      case CSS:
        return "an unclosed style block or attribute";

      case JS:
      case JS_LINE_COMMENT:  // Line comments are terminated by end of input.
        return "an unclosed script block or attribute";

      case CSS_COMMENT:
      case HTML_COMMENT:
      case JS_BLOCK_COMMENT:
        return "an unterminated comment";

      case CSS_DQ_STRING:
      case CSS_SQ_STRING:
      case JS_DQ_STRING:
      case JS_SQ_STRING:
        return "an unterminated string literal";

      case URI:
      case CSS_URI:
      case CSS_DQ_URI:
      case CSS_SQ_URI:
        return "an unterminated or empty URI";

      case JS_REGEX:
        return "an unterminated regular expression";

      default:
        if (templateNestDepth != 0) {
          return "an unterminated <template> element";
        } else {
          return "unknown to compiler";
        }
    }
  }


  /**
   * A state in the parse of an HTML document.
   */
  @SuppressWarnings("hiding")  // Enum value names mask corresponding Contexts declared above.
  public enum State {

    /** Outside an HTML tag, directive, or comment.  (Parsed character data). */
    HTML_PCDATA(EscapingMode.ESCAPE_HTML),

    /**
     * Inside an element whose content is RCDATA where text and entities can appear but where nested
     * elements cannot.
     * The content of {@code <title>} and {@code <textarea>} fall into this category since they
     * cannot contain nested elements in HTML.
     */
    HTML_RCDATA(EscapingMode.ESCAPE_HTML_RCDATA),

    /** Just before a tag name. */
    HTML_BEFORE_TAG_NAME(EscapingMode.FILTER_HTML_ELEMENT_NAME),

    /** Inside a tag name. */
    HTML_TAG_NAME(EscapingMode.FILTER_HTML_ELEMENT_NAME),

    /** Before an HTML attribute or the end of a tag. */
    HTML_TAG(EscapingMode.FILTER_HTML_ATTRIBUTES),
    // TODO: Do we need to filter out names that look like JS/CSS/URI attribute names.

    /** Inside an HTML attribute name. */
    HTML_ATTRIBUTE_NAME(EscapingMode.FILTER_HTML_ATTRIBUTES),

    /** Following an equals sign (<tt>=</tt>) after an attribute name in an HTML tag. */
    HTML_BEFORE_ATTRIBUTE_VALUE,

    /** Inside an HTML comment. */
    HTML_COMMENT(EscapingMode.ESCAPE_HTML_RCDATA),

    /** Inside a normal (non-CSS, JS, or URI) HTML attribute value. */
    HTML_NORMAL_ATTR_VALUE(EscapingMode.ESCAPE_HTML_ATTRIBUTE),

    /** In CSS content outside a comment, string, or URI. */
    CSS(EscapingMode.FILTER_CSS_VALUE),

    /** In CSS inside a comment. */
    CSS_COMMENT,

    /** In CSS inside a double quoted string. */
    CSS_DQ_STRING(EscapingMode.ESCAPE_CSS_STRING),

    /** In CSS inside a single quoted string. */
    CSS_SQ_STRING(EscapingMode.ESCAPE_CSS_STRING),

    /** In CSS in a URI terminated by the first close parenthesis. */
    CSS_URI(EscapingMode.NORMALIZE_URI),

    /** In CSS in a URI terminated by the first double quote. */
    CSS_DQ_URI(EscapingMode.NORMALIZE_URI),

    /** In CSS in a URI terminated by the first single quote. */
    CSS_SQ_URI(EscapingMode.NORMALIZE_URI),

    /** In JavaScript, outside a comment, string, or Regexp literal. */
    JS(EscapingMode.ESCAPE_JS_VALUE),

    /** In JavaScript inside a line comment. */
    JS_LINE_COMMENT,

    /** In JavaScript inside a block comment. */
    JS_BLOCK_COMMENT,

    /** In JavaScript inside a double quoted string. */
    JS_DQ_STRING(EscapingMode.ESCAPE_JS_STRING),

    /** In JavaScript inside a single quoted string. */
    JS_SQ_STRING(EscapingMode.ESCAPE_JS_STRING),

    /** In JavaScript inside a regular expression literal. */
    JS_REGEX(EscapingMode.ESCAPE_JS_REGEX),

    /** In a URI, which may or may not be in an HTML attribute. */
    URI(EscapingMode.NORMALIZE_URI),

    /** Plain text; no escaping. */
    TEXT(EscapingMode.TEXT),

    /** Not inside any valid HTML/CSS/JS construct. */
    ERROR,
    ;

    /**
     * The escaping mode appropriate for dynamic content inserted at this state.
     * Null if there is no appropriate escaping convention to use as for comments or plain text
     * which do not have escaping conventions.
     */
    private final @Nullable EscapingMode escapingMode;

    State(EscapingMode escapingMode) {
      this.escapingMode = escapingMode;
    }

    State() {
      this.escapingMode = null;
    }
  }


  /**
   * A type of HTML element.
   */
  public enum ElementType {

    /** No element. */
    NONE,

    /** A script element whose content is raw JavaScript. */
    SCRIPT,

    /** A style element whose content is raw CSS. */
    STYLE,

    /** A textarea element whose content is encoded HTML but which cannot contain elements. */
    TEXTAREA,

    /** A title element whose content is encoded HTML but which cannot contain elements. */
    TITLE,

    /** A listing element whose content is raw CDATA. */
    LISTING,

    /** An XMP element whose content is raw CDATA. */
    XMP,

    /** An element whose content is normal mixed PCDATA and child elements. */
    NORMAL,
    ;
  }


  /**
   * Describes the content of an HTML attribute.
   */
  public enum AttributeType {

    /** No attribute. */
    NONE,

    /** Mime-type text/javascript. */
    SCRIPT,

    /** Mime-type text/css. */
    STYLE,

    /** A URI or URI reference. */
    URI,

    /** Other content.  Human readable or other non-structured plain text or keyword values. */
    PLAIN_TEXT,
    ;
  }


  /**
   * Describes the content that will end the current HTML attribute.
   */
  public enum AttributeEndDelimiter {

    /** Not in an attribute. */
    NONE,

    /** {@code "}
     */
    DOUBLE_QUOTE("\""),

    /** {@code '}
     */
    SINGLE_QUOTE("'"),

    /** A space or {@code >} symbol. */
    SPACE_OR_TAG_END(""),
    ;

    /**
     * The suffix of the attribute that is not part of the attribute value.
     * E.g. in {@code href="foo"} the trailing double quote is part of the attribute but not part of
     * the value.
     * Whereas for space delimited attributes like {@code width=32}, there is no non-empty suffix
     * that is part of the attribute but not part of the value.
     */
    public final @Nullable String text;

    AttributeEndDelimiter(String text) {
      this.text = text;
    }

    AttributeEndDelimiter() {
      this.text = null;
    }
  }


  /**
   * Describes what a slash ({@code /}) means when parsing JavaScript source code.
   * A slash that is not followed by another slash or an asterisk (<tt>*</tt>) can either start a
   * regular expression literal or start a division operator.
   * This determination is made based on the full grammar, but Waldemar defined a very close to
   * accurate grammar for a JavaScript 1.9 draft based purely on a regular lexical grammar which is
   * what we use in the autoescaper.
   *
   * @see JsUtil#isRegexPreceder
   */
  public enum JsFollowingSlash {

    /** Not in JavaScript. */
    NONE,

    /** A slash as the next token would start a regular expression literal. */
    REGEX,

    /** A slash as the next token would start a division operator. */
    DIV_OP,

    /**
     * We do not know what a slash as the next token would start so it is an error for the next
     * token to be a slash.
     */
    UNKNOWN,
    ;
  }


  /**
   * Describes the part of a URI reference that the context point is in.
   *
   * <p>
   * We need to distinguish these so that we can<ul>
   *   <li>normalize well-formed URIs that appear before the query,</li>
   *   <li>encode raw values interpolated as query parameters or keys,</li>
   *   <li>filter out values that specify a scheme like {@code javascript:}.
   * </ul>
   */
  public enum UriPart {

    /** Not in a URI. */
    NONE,

    /** Where a scheme might be seen.  At ^ in {@code ^http://host/path?k=v#frag}. */
    START,

    /** In the scheme, authority, or path.  Between ^s in {@code h^ttp://host/path^?k=v#frag}. */
    PRE_QUERY,

    /** In the query portion.  Between ^s in {@code http://host/path?^k=v^#frag}*/
    QUERY,

    /** In the fragment.  After ^ in {@code http://host/path?k=v#^frag}*/
    FRAGMENT,

    /** Not {@link #NONE} or {@link #FRAGMENT}, but unknown.  Used to join different contexts. */
    UNKNOWN_PRE_FRAGMENT,

    /** Not {@link #NONE}, but unknown.  Used to join different contexts. */
    UNKNOWN,
    ;
  }


  /**
   * A mutable builder for {@link Context}s.
   */
  static final class Builder {
    private State state;
    private ElementType elType;
    private AttributeType attrType;
    private AttributeEndDelimiter delimType;
    private JsFollowingSlash slashType;
    private UriPart uriPart;
    private int templateNestDepth;

    private Builder(Context context) {
      this.state = context.state;
      this.elType = context.elType;
      this.attrType = context.attrType;
      this.delimType = context.delimType;
      this.slashType = context.slashType;
      this.uriPart = context.uriPart;
      this.templateNestDepth = context.templateNestDepth;
    }

    Builder withState(State state) {
      this.state = Preconditions.checkNotNull(state);
      return this;
    }

    Builder withElType(ElementType elType) {
      this.elType = Preconditions.checkNotNull(elType);
      return this;
    }

    Builder withAttrType(AttributeType attrType) {
      this.attrType = Preconditions.checkNotNull(attrType);
      return this;
    }

    Builder withDelimType(AttributeEndDelimiter delimType) {
      this.delimType = Preconditions.checkNotNull(delimType);
      return this;
    }

    Builder withSlashType(JsFollowingSlash slashType) {
      this.slashType = Preconditions.checkNotNull(slashType);
      return this;
    }

    Builder withUriPart(UriPart uriPart) {
      this.uriPart = Preconditions.checkNotNull(uriPart);
      return this;
    }

    Builder withTemplateNestDepth(int templateNestDepth) {
      Preconditions.checkArgument(templateNestDepth >= 0);
      this.templateNestDepth = templateNestDepth;
      return this;
    }

    Builder withoutAttrContext() {
      return this
          .withAttrType(Context.AttributeType.NONE)
          .withDelimType(Context.AttributeEndDelimiter.NONE)
          .withSlashType(Context.JsFollowingSlash.NONE)
          .withUriPart(Context.UriPart.NONE);
    }

    /**
     * Reset to a {@link Context} such that contextual autoescaping of a block of Soy code with
     * the corresponding {@link ContentKind} results in a value that adheres to the contract of
     * {@link com.google.template.soy.data.SanitizedContent} of this kind.
     */
    Builder withStartKind(ContentKind contentKind) {
      boolean inTag = false;
      withoutAttrContext();
      switch (contentKind) {
        case CSS:
          withState(State.CSS);
          break;
        case HTML:
          withState(State.HTML_PCDATA);
          break;
        case ATTRIBUTES:
          withState(State.HTML_TAG);
          inTag = true;
          break;
        case JS:
          withState(State.JS);
          withSlashType(JsFollowingSlash.REGEX);
          break;
        case URI:
          withState(State.URI);
          withUriPart(UriPart.START);
          break;
        case TEXT:
          withState(State.TEXT);
          break;
        default:
          break;
      }
      if (!inTag) {
        withElType(ElementType.NONE);
      }
      return this;
    }

    Context build() {
      return new Context(state, elType, attrType, delimType, slashType, uriPart, templateNestDepth);
    }
  }
}
