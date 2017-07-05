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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.internal.base.UnescapeUtils;
import com.google.template.soy.parsepasses.contextautoesc.Context.UriPart;
import com.google.template.soy.parsepasses.contextautoesc.Context.UriType;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.RawTextNode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Propagates {@link Context}s across raw text chunks using a state-machine parser for HTML/CSS/JS.
 *
 * <p>Given some raw JS text {@code var x = "foo";} and the {@link Context#JS JS} starting context,
 * this class will decompose the rawText into a number of tokens and compute follow on contexts for
 * each.
 *
 * <table>
 * <tr><td>{@code var x = "}</td><td>{@link HtmlContext#JS}</td></tr>
 * <tr><td>{@code foo}</td><td>{@link HtmlContext#JS_DQ_STRING}</td></tr>
 * <tr><td>{@code ";}</td><td>{@link HtmlContext#JS}</td></tr>
 * </table>
 *
 * <h2>A note on regular expressions.
 *
 * <p>This class uses a number of regular expressions to detect context transition boundaries and it
 * uses the Java builtin regex engine. This is a backtracking regex engine and so has the
 * possibility of failing with stack overflow errors on large inputs. This is normally triggered by
 * the following:
 *
 * <ul>
 *   <li>A regex containing a repeated alternation e.g. {@code (A|B)+}
 *   <li>A large input string, that matches with many repetitions.
 * </ul>
 *
 * <p>To cope with this you can do a few things
 *
 * <ul>
 *   <li>Move repetition inside the alternation where possible e.g. {@code (A+|B+)+}
 *   <li>Make the repetition quantifiers possesive e.g. {@code (A|B)++}. This causes the engine to
 *       'commit' to a choice and thus avoid recursion.
 * </ul>
 *
 * <p>The other option would be to switch to a different regex engine less prone to these issues
 * like RE2. However, there are some downsides
 *
 * <ul>
 *   <li>The java implementations are not as performant or require the use of native libraries.
 *   <li>It would add a new open source dependency.
 * </ul>
 *
 * <p>So, for the time being we should just be careful.
 *
 */
final class RawTextContextUpdater {

  /**
   * @param rawTextNode A chunk of HTML/CSS/JS.
   * @param context The context before rawText.
   * @return the next context transition.
   */
  public static Context processRawText(RawTextNode rawTextNode, Context context)
      throws SoyAutoescapeException {
    String rawText = rawTextNode.getRawText();
    int offset = 0;
    int length = rawText.length();
    while (offset < length) {
      String unprocessedRawText = rawText.substring(offset);
      int endOffset;
      Context startContext = context;
      Context endContext;

      // If we are in an attribute value, then decode the remaining text
      // (except for the delimiter) up to the next occurrence of delimiter.

      // The end of the section to decode.  Either before a delimiter or > symbol that closes an
      // attribute, at the end of the rawText, or -1 if no decoding needs to happen.
      int attrValueEnd = findEndOfAttributeValue(unprocessedRawText, context.delimType);
      if (attrValueEnd == -1) {
        // Outside an attribute value.  No need to decode.
        RawTextContextUpdater cu = new RawTextContextUpdater();
        cu.processNextToken(rawTextNode, offset, unprocessedRawText, context);
        endOffset = offset + cu.numCharsConsumed;
        endContext = cu.next;

      } else {
        // Inside an attribute value.  Find the end and decode up to it.

        // All of the languages we deal with (HTML, CSS, and JS) use quotes as delimiters.
        // When one language is embedded in the other, we need to decode delimiters before trying
        // to parse the content in the embedded language.
        //
        // For example, in
        //       <a onclick="alert(&quot;Hello {$world}&quot;)">
        // the decoded value of the event handler is
        //       alert("Hello {$world}")
        // so to determine the appropriate escaping convention we decode the attribute value
        // before delegating to processNextToken.
        //
        // We could take the cross-product of two languages to avoid decoding but that leads to
        // either an explosion in the number of states, or the amount of lookahead required.
        int unprocessedRawTextLen = unprocessedRawText.length();

        // The end of the attribute value relative to offset.
        // At attrValueEnd, or attrValueend + 1 if a delimiter
        // needs to be consumed.
        int attrEnd =
            attrValueEnd < unprocessedRawTextLen
                ? attrValueEnd + context.delimType.text.length()
                : -1;

        // Decode so that the JavaScript rules work on attribute values like
        //     <a onclick='alert(&quot;{$msg}!&quot;)'>
        // If we've already processed the tokens "<a", " onclick='" to get into the
        // single quoted JS attribute context, then we do three things:
        //   (1) This class will decode "&quot;" to "\"" and work below to go from State.JS to
        //       State.JS_DQ_STRING.
        //   (2) Then the caller checks {$msg} and realizes that $msg is part of a JS string.
        //   (3) Then, the above will identify the "'" as the end, and so we reach here with:
        //       r a w T e x t = " ! & q u o t ; ) ' > "
        //                                         ^ ^
        //                              attrValueEnd attrEnd

        // We use this example more in the comments below.

        String attrValueTail =
            UnescapeUtils.unescapeHtml(unprocessedRawText.substring(0, attrValueEnd));
        // attrValueTail is "!\")" in the example above.

        // Recurse on the decoded value.
        RawTextContextUpdater cu = new RawTextContextUpdater();
        Context attrContext = startContext;
        while (attrValueTail.length() != 0) {
          cu.processNextToken(rawTextNode, offset, attrValueTail, attrContext);
          attrValueTail = attrValueTail.substring(cu.numCharsConsumed);
          attrContext = cu.next;
        }

        // TODO: Maybe check that context is legal to leave an attribute in.  Throw if the attribute
        // ends inside a quoted string.

        if (attrEnd != -1) {
          endOffset = offset + attrEnd;
          // rawText.charAt(endOffset) is now ">" in the example above.

          // When an attribute ends, we're back in the tag.
          endContext =
              context.toBuilder().withState(HtmlContext.HTML_TAG).withoutAttrContext().build();
        } else {
          // Whole tail is part of an unterminated attribute.
          if (attrValueEnd != unprocessedRawTextLen) {
            throw new IllegalStateException();
          }
          endOffset = length;
          endContext = attrContext;
        }
      }

      context = endContext;
      offset = endOffset;
    }
    return context;
  }

  /**
   * @return The end of the attribute value of -1 if delim indicates we are not in an attribute.
   *     {@code rawText.length()} if we are in an attribute but the end does not appear in rawText.
   */
  private static int findEndOfAttributeValue(String rawText, Context.AttributeEndDelimiter delim) {
    int rawTextLen = rawText.length();
    switch (delim) {
      case DOUBLE_QUOTE:
      case SINGLE_QUOTE:
        int quote = rawText.indexOf(delim.text.charAt(0));
        return quote >= 0 ? quote : rawTextLen;

      case SPACE_OR_TAG_END:
        for (int i = 0; i < rawTextLen; ++i) {
          char ch = rawText.charAt(i);
          if (ch == '>' || Character.isWhitespace(ch)) {
            return i;
          }
        }
        return rawTextLen;

      case NONE:
        return -1;
    }
    throw new AssertionError("Unrecognized delimiter " + delim);
  }

  /** The amount of rawText consumed. */
  private int numCharsConsumed;

  /** The context to which we transition. */
  private Context next;

  private RawTextContextUpdater() {
    // NOP
  }

  /**
   * Consume a portion of text and compute the next context. Output is stored in member variables.
   *
   * @param node The node currently being processed
   * @param offset The offset into the node where text starts
   * @param text Non empty.
   * @param context the current contex
   */
  private void processNextToken(RawTextNode node, int offset, String text, Context context) {
    // Find the transition whose pattern matches earliest in the raw text (and is applicable)
    int earliestStart = Integer.MAX_VALUE;
    int earliestEnd = -1;
    Transition earliestTransition = null;
    Matcher earliestMatcher = null;
    List<Transition> ts = TRANSITIONS.get(context.state);
    if (ts == null) {
      throw new NullPointerException(
          "no transitions for state: "
              + context.state
              + " @"
              + node.substringLocation(offset, offset + 1));
    }
    for (Transition transition : ts) {
      Matcher matcher = transition.pattern.matcher(text);
      try {
        // For each transition:
        // look for matches, if the match is later than the current earliest match, give up
        // otherwise if the match is applicable, store it.
        // NOTE: matcher.find() returns matches in sequential order.
        while (matcher.find() && matcher.start() < earliestStart) {
          int start = matcher.start();
          int end = matcher.end();
          if (transition.isApplicableTo(context, matcher)) {
            earliestStart = start;
            earliestEnd = end;
            earliestTransition = transition;
            earliestMatcher = matcher;
            break;
          }
        }
      } catch (StackOverflowError soe) {
        // catch and annotate with the pattern.
        throw new RuntimeException(
            String.format(
                "StackOverflow while trying to match: '%s' in context %s starting @ %s",
                transition.pattern, context, node.substringLocation(offset, offset + 1)),
            soe);
      }
    }

    if (earliestTransition != null) {
      int transitionOffset = offset;
      // the earliest start might be at the end for null transitions.
      if (earliestStart < text.length()) {
        transitionOffset += earliestStart;
      }
      this.next =
          earliestTransition.computeNextContext(node, transitionOffset, context, earliestMatcher);
      this.numCharsConsumed = earliestEnd;
    } else {
      throw SoyAutoescapeException.createWithNode(
          "Error determining next state when encountering \"" + text + "\" in " + context,
          // calculate a raw text node that points at the beginning of the string that couldn't
          // bet matched.
          node.substring(Integer.MAX_VALUE /* bogus id */, offset));
    }
    if (numCharsConsumed == 0 && this.next.state == context.state) {
      throw new IllegalStateException("Infinite loop at `" + text + "` / " + context);
    }
  }

  /**
   * Encapsulates a grammar production and the context after that production is seen in a chunk of
   * HTML/CSS/JS input.
   */
  private abstract static class Transition {
    /** Matches a token. */
    final Pattern pattern;

    Transition(Pattern pattern) {
      this.pattern = pattern;
    }

    Transition(String regex) {
      this(Pattern.compile(regex, Pattern.DOTALL));
    }

    /**
     * True iff this transition can produce a context after the text in rawText[0:matcher.end()].
     * This should not destructively modify the matcher. Specifically, it should not call {@code
     * find()} again.
     *
     * @param prior The context before the start of the token in matcher.
     * @param matcher The token matched by {@code this.pattern}.
     */
    boolean isApplicableTo(Context prior, Matcher matcher) {
      return true;
    }

    /**
     * Computes the context that this production transitions to after rawText[0:matcher.end()].
     *
     * @param originalNode The original raw text node
     * @param offset The current offset into the node, useful for calculating better locations for
     *     error messages
     * @param prior The context prior to the token in matcher.
     * @param matcher The token matched by {@code this.pattern}.
     * @return The context after the given token.
     */
    Context computeNextContext(
        RawTextNode originalNode, int offset, Context prior, Matcher matcher) {
      return computeNextContext(prior, matcher);
    }

    /**
     * Computes the context that this production transitions to after rawText[0:matcher.end()].
     *
     * @param prior The context prior to the token in matcher.
     * @param matcher The token matched by {@code this.pattern}.
     * @return The context after the given token.
     */
    Context computeNextContext(Context prior, Matcher matcher) {
      throw new AbstractMethodError();
    }
  }


  /** A transition to the given state. */
  private static Transition makeTransitionToState(String regex, final HtmlContext state) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        return prior.transitionToState(state);
      }
    };
  }

  /** A transition to an state. */
  private static Transition makeTransitionToError(String regex, final String message) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(RawTextNode node, int offset, Context prior, Matcher matcher) {
        throw SoyAutoescapeException.createWithNode(
            message, node.substring(Integer.MAX_VALUE, offset));
      }
    };
  }

  /** A transition to the given JS string start state. */
  private static Transition makeTransitionToJsString(String regex, final HtmlContext state) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        return prior
            .toBuilder()
            .withState(state)
            .withSlashType(Context.JsFollowingSlash.NONE)
            .withUriPart(UriPart.NONE)
            .build();
      }
    };
  }

  /** A transition that consumes some content without changing state. */
  private static Transition makeTransitionToSelf(String regex) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        return prior;
      }
    };
  }

  /** Consumes the entire content without change if nothing else matched. */
  private static final Transition TRANSITION_TO_SELF = makeTransitionToSelf("\\z");
  // Matching at the end is lowest possible precedence.

  private static UriPart getNextUriPart(
      RawTextNode node, int offset, UriPart uriPart, char matchChar) {
    // This switch statement is designed to process a URI in order via a sequence of fall throughs.
    switch (uriPart) {
      case MAYBE_SCHEME:
      case MAYBE_VARIABLE_SCHEME:
        // From the RFC: https://tools.ietf.org/html/rfc3986#section-3.1
        // scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
        // At this point, our goal is to try to prove that we've safely left the scheme, and then
        // transition to a more specific state.

        if (matchChar == ':') {
          // Ah, it looks like we might be able to conclude we've set the scheme, but...
          if (uriPart == UriPart.MAYBE_VARIABLE_SCHEME) {
            // At the start of a URL, and we already saw a print statement, and now we suddenly
            // see a colon. While this could be relatively safe if it's a {$host}:{$port} pair,
            // at compile-time, we can't be sure that "$host" isn't something like "javascript"
            // and "$port" isn't "deleteMyAccount()".
            throw SoyAutoescapeException.createWithNode(
                "Soy can't safely process a URI that might start with a variable scheme. "
                    + "For example, {$x}:{$y} could have an XSS if $x is 'javascript' and $y is "
                    + "attacker-controlled. Either use a hard-coded scheme, or introduce "
                    + "disambiguating characters (e.g. http://{$x}:{$y}, ./{$x}:{$y}, or "
                    + "{$x}?foo=:{$y})",
                node.substring(Integer.MAX_VALUE, offset));
          } else {
            // At the start of the URL, and we just saw some hard-coded characters and a colon,
            // like http:. This is safe (assuming it's a good scheme), and now we're on our way to
            // the authority. Note if javascript: was seen, we would have scanned it already and
            // entered a separate state (unless the developer is malicious and tries to obscure it
            // via a conditional).
            return UriPart.AUTHORITY_OR_PATH;
          }
        }

        if (matchChar == '/') {
          // Upon seeing a slash, it's impossible to set a valid scheme anymore. Either we're in the
          // path, or we're starting a protocol-relative URI. (For all we know, we *could* be
          // in the query, e.g. {$base}/foo if $base has a question mark, but sadly we have to go
          // by what we know statically. However, usually query param groups tend to contain
          // ampersands and equal signs, which we check for later heuristically.)
          return UriPart.AUTHORITY_OR_PATH;
        }

        if ((matchChar == '=' || matchChar == '&') && uriPart == UriPart.MAYBE_VARIABLE_SCHEME) {
          // This case is really special, and is only seen in cases like href="{$x}&amp;foo={$y}" or
          // href="{$x}foo={$y}".  While in this case we can never be sure that we're in the query
          // part, we do know two things:
          //
          // 1) We can't possibly set a dangerous scheme, since no valid scheme contains = or &
          // 2) Within QUERY, all print statements are encoded as a URI component, which limits
          // the damage that can be done; it can't even break into another path segment.
          // Therefore, it is secure to assume this.
          //
          // Note we can safely handle ampersand even in HTML contexts because attribute values
          // are processed unescaped.
          return UriPart.QUERY;
        }
        // fall through
      case AUTHORITY_OR_PATH:
      case UNKNOWN_PRE_FRAGMENT:
        if (matchChar == '?') {
          // Upon a ? we can be pretty sure we're in the query. While it's possible for something
          // like {$base}?foo=bar to be in the fragment if $base contains a #, it's safe to assume
          // we're in the query, because query params are escaped more strictly than the fragment.
          return UriPart.QUERY;
        }
        // fall through
      case QUERY:
      case UNKNOWN:
        if (matchChar == '#') {
          // A # anywhere proves we're in the fragment, even if we're already in the fragment.
          return UriPart.FRAGMENT;
        }
        // fall through
      case FRAGMENT:
        // No transitions for fragment.
        return uriPart;
      case DANGEROUS_SCHEME:
        // Dangerous schemes remain dangerous.
        return UriPart.DANGEROUS_SCHEME;
      default:
        throw new AssertionError("Unanticipated URI part: " + uriPart);
    }
  }

  /**
   * Transition between different parts of an http-like URL.
   *
   * <p>This happens on the first important URI character, or upon seeing the end of the raw text
   * segment and not seeing anything else.
   */
  private static final Transition URI_PART_TRANSITION =
      new Transition("([:./&?=#])|\\z") {
        @Override
        boolean isApplicableTo(Context prior, Matcher matcher) {
          return true;
        }

        @Override
        Context computeNextContext(RawTextNode node, int offset, Context prior, Matcher matcher) {
          UriPart uriPart = prior.uriPart;
          if (uriPart == UriPart.START) {
            uriPart = UriPart.MAYBE_SCHEME;
          }
          String match = matcher.group(1);
          if (match != null) {
            uriPart = getNextUriPart(node, offset, uriPart, match.charAt(0));
          }
          return prior.derive(uriPart);
        }
      };

  /** Transition to detect dangerous URI schemes. */
  private static final Transition URI_START_TRANSITION =
      new Transition("(?i)^(javascript|data|blob|filesystem):") {
        @Override
        boolean isApplicableTo(Context prior, Matcher matcher) {
          return prior.uriPart == UriPart.START;
        }

        @Override
        Context computeNextContext(Context prior, Matcher matcher) {
          // TODO(gboyer): Ban all but whitelisted schemes.
          return prior.derive(UriPart.DANGEROUS_SCHEME);
        }
      };

  /** Matches the beginning of a CSS URI with the delimiter, if any, in group 1. */
  private static Transition makeCssUriTransition(String regex, final UriType uriType) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        String delim = matcher.group(1);
        HtmlContext state;
        if ("\"".equals(delim)) {
          state = HtmlContext.CSS_DQ_URI;
        } else if ("'".equals(delim)) {
          state = HtmlContext.CSS_SQ_URI;
        } else {
          state = HtmlContext.CSS_URI;
        }
        return prior
            .toBuilder()
            .withState(state)
            .withUriType(uriType)
            .withUriPart(UriPart.START)
            .build();
      }
    };
  }

  /** Matches a portion of JavaScript that can precede a division operator. */
  private static Transition makeDivPreceder(String regex) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        return prior
            .toBuilder()
            .withState(HtmlContext.JS)
            .withSlashType(Context.JsFollowingSlash.DIV_OP)
            .build();
      }
    };
  }

  /** Characters that break a line in JavaScript source suitable for use in a regex charset. */
  private static final String JS_LINEBREAKS = "\\r\\n\u2028\u2029";

  /**
   * For each state, a group of rules for consuming raw text and how that affects the document
   * context. The rules each have an associated pattern, and the rule whose pattern matches earliest
   * in the text wins.
   */
  private static final ImmutableMap<HtmlContext, List<Transition>> TRANSITIONS =
      ImmutableMap.<HtmlContext, List<Transition>>builder()
          // All edges in or out of pcdata, comment or attr value are triggered by nodes and thus
          // handled by the InferenceEngine.
          .put(HtmlContext.HTML_PCDATA, ImmutableList.of(TRANSITION_TO_SELF))
          .put(HtmlContext.HTML_COMMENT, ImmutableList.of(TRANSITION_TO_SELF))
          .put(HtmlContext.HTML_NORMAL_ATTR_VALUE, ImmutableList.of(TRANSITION_TO_SELF))
          // The CSS transitions below are based on http://www.w3.org/TR/css3-syntax/#lexical
          .put(
              HtmlContext.CSS,
              ImmutableList.of(
                  makeTransitionToState("/\\*", HtmlContext.CSS_COMMENT),
                  // TODO: Do we need to support non-standard but widely supported C++ style
                  // comments?
                  makeTransitionToState("\"", HtmlContext.CSS_DQ_STRING),
                  makeTransitionToState("'", HtmlContext.CSS_SQ_STRING),
                  // Although we don't contextually parse CSS, certain property names are only used
                  // in conjunction with images.  This pretty basic regexp does a decent job on CSS
                  // that is not attempting to be malicious (for example, doesn't handle comments).
                  // Note that this can be fooled with {if 1}foo-{/if}background, but it's not worth
                  // really worrying about.
                  makeCssUriTransition(
                      "(?i)(?:[^a-z0-9-]|^)\\s*"
                          + "(?:background|background-image|border-image|content"
                          + "|cursor|list-style|list-style-image)"
                          + "\\s*:\\s*url\\s*\\(\\s*(['\"]?)",
                      UriType.MEDIA),
                  // TODO(gboyer): We should treat @import, @font-face src, etc as trusted
                  // resources, once trusted URLs are implemented.
                  makeCssUriTransition("(?i)\\burl\\s*\\(\\s*(['\"]?)", UriType.NORMAL),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_COMMENT,
              ImmutableList.of(makeTransitionToState("\\*/", HtmlContext.CSS), TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_DQ_STRING,
              ImmutableList.of(
                  makeTransitionToState("\"", HtmlContext.CSS),
                  makeTransitionToSelf("\\\\(?:\r\n?|[\n\f\"])"), // Line continuation or escape.
                  makeTransitionToError("[\n\r\f]", "Newlines not permitted in string literals."),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_SQ_STRING,
              ImmutableList.of(
                  makeTransitionToState("'", HtmlContext.CSS),
                  makeTransitionToSelf("\\\\(?:\r\n?|[\n\f'])"), // Line continuation or escape.
                  makeTransitionToError("[\n\r\f]", "Newlines not permitted in string literals."),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_URI,
              ImmutableList.of(
                  makeTransitionToState("[\\)\\s]", HtmlContext.CSS),
                  URI_PART_TRANSITION,
                  URI_START_TRANSITION,
                  makeTransitionToError("[\"']", "Quotes not permitted in CSS URIs.")))
          .put(
              HtmlContext.CSS_SQ_URI,
              ImmutableList.of(
                  makeTransitionToState("'", HtmlContext.CSS),
                  URI_PART_TRANSITION,
                  URI_START_TRANSITION,
                  makeTransitionToSelf("\\\\(?:\r\n?|[\n\f'])"), // Line continuation or escape.
                  makeTransitionToError("[\n\r\f]", "Newlines not permitted in string literal.")))
          .put(
              HtmlContext.CSS_DQ_URI,
              ImmutableList.of(
                  makeTransitionToState("\"", HtmlContext.CSS),
                  URI_PART_TRANSITION,
                  URI_START_TRANSITION,
                  makeTransitionToSelf("\\\\(?:\r\n?|[\n\f\"])"), // Line continuation or escape.
                  makeTransitionToError("[\n\r\f]", "Newlines not permitted in string literal.")))
          .put(
              HtmlContext.JS,
              ImmutableList.of(
                  makeTransitionToState("/\\*", HtmlContext.JS_BLOCK_COMMENT),
                  makeTransitionToState("//", HtmlContext.JS_LINE_COMMENT),
                  makeTransitionToJsString("\"", HtmlContext.JS_DQ_STRING),
                  makeTransitionToJsString("'", HtmlContext.JS_SQ_STRING),
                  new Transition(Pattern.quote("`")) {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      return prior
                          .toBuilder()
                          .withState(HtmlContext.JS_TEMPLATE_LITERAL)
                          .withJsTemplateLiteralNestDepth(prior.jsTemplateLiteralNestDepth + 1)
                          .build();
                    }
                  },
                  new Transition(Pattern.quote("}")) {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      // if we are in a template, then this puts us back into the template string
                      // e.g.  `foo${bar}`
                      if (prior.jsTemplateLiteralNestDepth > 0) {
                        return prior.toBuilder().withState(HtmlContext.JS_TEMPLATE_LITERAL).build();
                      }
                      // stay in js, this must be part of some control flow character
                      return prior;
                    }
                  },
                  new Transition("/") {
                    @Override
                    Context computeNextContext(
                        RawTextNode node, int offset, Context prior, Matcher matcher) {
                      switch (prior.slashType) {
                        case DIV_OP:
                          return prior
                              .toBuilder()
                              .withState(HtmlContext.JS)
                              .withSlashType(Context.JsFollowingSlash.REGEX)
                              .build();
                        case REGEX:
                          return prior
                              .toBuilder()
                              .withState(HtmlContext.JS_REGEX)
                              .withSlashType(Context.JsFollowingSlash.NONE)
                              .build();
                        default:
                          StringBuffer rest = new StringBuffer();
                          matcher.appendTail(rest);
                          throw SoyAutoescapeException.createWithNode(
                              "Slash (/) cannot follow the preceding branches since it is unclear "
                                  + "whether the slash is a RegExp literal or division operator.  "
                                  + "Please add parentheses in the branches leading to `"
                                  + rest
                                  + "`",
                              node.substring(Integer.MAX_VALUE, offset));
                      }
                    }
                  },
                  /**
                   * Shuffle words, punctuation (besides /), and numbers off to an analyzer which
                   * does a quick and dirty check to update JsUtil.isRegexPreceder.
                   *
                   * <p>NOTE: every character that is matched by a transition above should be in the
                   * negative character set in this regex. Otherwise this transition will swallow
                   * the special characters
                   */
                  new Transition("[^/\"'}`\\s\\\\]+") {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      return prior.derive(
                          JsUtil.isRegexPreceder(matcher.group())
                              ? Context.JsFollowingSlash.REGEX
                              : Context.JsFollowingSlash.DIV_OP);
                    }
                  },
                  // Space
                  makeTransitionToSelf("\\s+")))
          .put(
              HtmlContext.JS_BLOCK_COMMENT,
              ImmutableList.of(makeTransitionToState("\\*/", HtmlContext.JS), TRANSITION_TO_SELF))
          // Line continuations are not allowed in line comments.
          .put(
              HtmlContext.JS_LINE_COMMENT,
              ImmutableList.of(
                  makeTransitionToState("[" + JS_LINEBREAKS + "]", HtmlContext.JS),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.JS_DQ_STRING,
              ImmutableList.of(
                  makeDivPreceder("\""),
                  makeTransitionToSelf(
                      "(?i)^(?:"
                          + // Case-insensitively, from start of string
                          "[^\"\\\\"
                          + JS_LINEBREAKS
                          + "]++"
                          + // match any chars except newlines, quotes, \s;
                          "|\\\\(?:"
                          + // or backslash followed by a
                          "\\r\\n?"
                          + // line continuation
                          "|[^\\r]"
                          + // or an escape
                          ")"
                          + ")++")))
          .put(
              HtmlContext.JS_SQ_STRING,
              ImmutableList.of(
                  makeDivPreceder("'"),
                  makeTransitionToSelf(
                      "(?i)^(?:"
                          + // Case-insensitively, from start of string
                          "[^'\\\\"
                          + JS_LINEBREAKS
                          + "]++"
                          + // match any chars except newlines, quotes, \s;
                          "|\\\\(?:"
                          + // or a backslash followed by a
                          "\\r\\n?"
                          + // line continuation
                          "|[^\\r]"
                          + // or an escape;
                          ")"
                          + ")++")))
          .put(
              HtmlContext.JS_TEMPLATE_LITERAL,
              ImmutableList.of(
                  new Transition(Pattern.quote("`")) {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      return prior
                          .toBuilder()
                          .withState(HtmlContext.JS)
                          .withJsTemplateLiteralNestDepth(prior.jsTemplateLiteralNestDepth - 1)
                          .withSlashType(Context.JsFollowingSlash.REGEX)
                          .build();
                    }
                  },
                  // ignore slash escaped '$' and ` chars
                  makeTransitionToSelf("\\\\[$`]"),
                  // ${ puts us into js context
                  makeTransitionToState(Pattern.quote("${"), HtmlContext.JS),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.JS_REGEX,
              ImmutableList.of(
                  makeDivPreceder("/"),
                  makeTransitionToSelf(
                      "(?i)^(?:"
                          +
                          /**
                           * We have to handle [...] style character sets specially since in /[/]/,
                           * the second solidus doesn't end the regular expression.
                           */
                          "[^\\[\\\\/"
                          + JS_LINEBREAKS
                          + "]++"
                          + // A non-charset, non-escape token;
                          "|\\\\[^"
                          + JS_LINEBREAKS
                          + "]"
                          // an escape;
                          + "|\\["
                          + // or a character set containing
                          "(?:[^\\]\\\\"
                          + JS_LINEBREAKS
                          + "]"
                          + // a normal character,
                          "|\\\\(?:[^"
                          + JS_LINEBREAKS
                          + "]))*+"
                          + // or an escape;
                          "\\]"
                          + ")+")))
          .put(HtmlContext.URI, ImmutableList.of(URI_PART_TRANSITION, URI_START_TRANSITION))
          // All edges out of rcdata are triggered by tags which are handled in the InferenceEngine
          .put(HtmlContext.HTML_RCDATA, ImmutableList.of(TRANSITION_TO_SELF))
          // Text context has no edges except to itself.
          .put(HtmlContext.TEXT, ImmutableList.of(TRANSITION_TO_SELF))
          .build();

  // TODO: If we need to deal with untrusted templates, then we need to make sure that tokens like
  // <!--, </script>, etc. are never split with empty strings.
  // We could do this by walking all possible paths through each template (both branches for ifs,
  // each case for switches, and the 0,1, and 2+ iteration case for loops).
  // For each template, tokenize the original's rawText nodes using RawTextContextUpdater and then
  // tokenize one single rawText node made by concatenating all rawText.
  // If one contains a sensitive token, e.g. <!--/ and the other doesn't, then we have a potential
  // splitting attack.
  // That and disallow unquoted attributes, and be paranoid about prints especially in the TAG_NAME
  // productions.
}
