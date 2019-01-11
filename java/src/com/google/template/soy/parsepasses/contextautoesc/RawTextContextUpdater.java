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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.internal.base.UnescapeUtils;
import com.google.template.soy.parsepasses.contextautoesc.Context.AttributeEndDelimiter;
import com.google.template.soy.parsepasses.contextautoesc.Context.HtmlHtmlAttributePosition;
import com.google.template.soy.parsepasses.contextautoesc.Context.UriPart;
import com.google.template.soy.parsepasses.contextautoesc.Context.UriType;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.RawTextNode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

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
  public static Context processRawText(RawTextNode rawTextNode, Context context) {
    String rawText = rawTextNode.getRawText();
    // If we are in an attribute value, then decode the text.
    if (context.delimType != AttributeEndDelimiter.NONE) {
      // this text is part of an attribute value,  so we should unescape it.
      // NOTE: our caller guarantees (by way of the html parser) that this text cannot exceed the
      // bounds of the attribute, so we can just unescape the whole thing.
      rawText = UnescapeUtils.unescapeHtml(rawText);
    }
    int offset = 0;
    int length = rawText.length();
    RawTextContextUpdater cu = new RawTextContextUpdater(context);
    while (offset < length) {
      offset += cu.processNextToken(rawTextNode, offset, rawText.substring(offset));
    }
    return cu.context;
  }

  private Context context;

  private RawTextContextUpdater(Context context) {
    this.context = checkNotNull(context);
  }

  /**
   * Consume a portion of text and compute the next context. Output is stored in member variables.
   *
   * @param node The node currently being processed
   * @param offset The offset into the node where text starts
   * @param text Non empty.
   * @return the number of characters consumed
   */
  private int processNextToken(RawTextNode node, final int offset, String text) {
    // Find the transition whose pattern matches earliest in the raw text (and is applicable)
    int numCharsConsumed;
    Context next;
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
      if (transition.pattern != null) {
        Matcher matcher = transition.pattern.matcher(text);
        // For each transition:
        // look for matches, if the match is later than the current earliest match, give up
        // otherwise if the match is applicable, store it.
        // NOTE: matcher.find() returns matches in sequential order.
        try {
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
      } else if (transition.literal != null) {
        int start = 0;
        int index;
        String needle = transition.literal;
        while ((index = text.indexOf(needle, start)) != -1 && index < earliestStart) {
          if (transition.isApplicableTo(context, null)) {
            earliestStart = index;
            earliestEnd = index + needle.length();
            earliestTransition = transition;
            earliestMatcher = null;
            break;
          }
        }
      } else {
        if (text.length() < earliestStart && transition.isApplicableTo(context, null)) {
          earliestStart = text.length();
          earliestEnd = text.length();
          earliestTransition = transition;
          earliestMatcher = null;
        }
      }
    }

    if (earliestTransition != null) {
      int transitionOffset = offset;
      // the earliest start might be at the end for null transitions.
      if (earliestStart < text.length()) {
        transitionOffset += earliestStart;
      }
      next =
          earliestTransition.computeNextContext(node, transitionOffset, context, earliestMatcher);
      numCharsConsumed = earliestEnd;
    } else {
      throw SoyAutoescapeException.createWithNode(
          "Error determining next state when encountering \"" + text + "\" in " + context,
          // calculate a raw text node that points at the beginning of the string that couldn't
          // bet matched.
          node.substring(Integer.MAX_VALUE /* bogus id */, offset));
    }
    if (numCharsConsumed == 0 && next.state == context.state) {
      throw new IllegalStateException("Infinite loop at `" + text + "` / " + context);
    }
    this.context = next;
    return numCharsConsumed;
  }

  /**
   * Encapsulates a grammar production and the context after that production is seen in a chunk of
   * HTML/CSS/JS input.
   */
  private abstract static class Transition {
    // If both fields are null, then this is a special 'self transition' that matches the end of
    // input.  This is used to create a base case in matching multiple transitions.

    /** Matches a pattern. */
    @Nullable final Pattern pattern;

    /** For matching a literal string. */
    @Nullable final String literal;

    Transition(Pattern pattern) {
      this.pattern = pattern;
      this.literal = null;
    }

    Transition(String literal) {
      this.pattern = null;
      this.literal = literal;
    }

    Transition() {
      this.pattern = null;
      this.literal = null;
    }

    /**
     * True iff this transition can produce a context after the text in rawText[0:matcher.end()].
     * This should not destructively modify the matcher. Specifically, it should not call {@code
     * find()} again.
     *
     * @param prior The context before the start of the token in matcher.
     * @param matcher The token matched by {@code this.pattern} or {@code null} if this transition
     *     uses a {@code literal}
     */
    boolean isApplicableTo(Context prior, @Nullable Matcher matcher) {
      return true;
    }

    /**
     * Computes the context that this production transitions to after rawText[0:matcher.end()].
     *
     * @param originalNode The original raw text node
     * @param offset The current offset into the node, useful for calculating better locations for
     *     error messages
     * @param prior The context prior to the token in matcher.
     * @param matcher The token matched by {@code this.pattern} or {@code null} if this transition
     *     uses a {@code literal}
     * @return The context after the given token.
     */
    Context computeNextContext(
        RawTextNode originalNode, int offset, Context prior, @Nullable Matcher matcher) {
      return computeNextContext(prior, matcher);
    }

    /**
     * Computes the context that this production transitions to after rawText[0:matcher.end()].
     *
     * @param prior The context prior to the token in matcher.
     * @param matcher The token matched by {@code this.pattern} or {@code null} if this transition
     *     uses a {@code literal}
     * @return The context after the given token.
     */
    Context computeNextContext(Context prior, @Nullable Matcher matcher) {
      throw new AbstractMethodError();
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("pattern", pattern)
          .add("literal", literal)
          .omitNullValues()
          .toString();
    }
  }

  /** A transition to the given state. */
  private static Transition makeTransitionToStateLiteral(String literal, final HtmlContext state) {
    return new Transition(literal) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        return prior.transitionToState(state);
      }
    };
  }

  private static Transition makeTransitionToState(Pattern regex, final HtmlContext state) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        return prior.transitionToState(state);
      }
    };
  }

  /** A transition to an state. */
  private static Transition makeTransitionToError(Pattern regex, final String message) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(RawTextNode node, int offset, Context prior, Matcher matcher) {
        throw SoyAutoescapeException.createWithNode(
            message, node.substring(Integer.MAX_VALUE, offset));
      }
    };
  }

  /** A transition to the given JS string start state. */
  private static Transition makeTransitionToJsStringLiteral(
      String literal, final HtmlContext state) {
    return new Transition(literal) {
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
  private static Transition makeTransitionToSelf(Pattern regex) {
    return new Transition(regex) {
      @Override
      Context computeNextContext(Context prior, Matcher matcher) {
        return prior;
      }
    };
  }

  /** Consumes the entire content without change if nothing else matched. */
  private static final Transition TRANSITION_TO_SELF =
      new Transition() {
        @Override
        Context computeNextContext(Context prior, Matcher matcher) {
          return prior;
        }
      };

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
                node.substring(/* newId= */ Integer.MAX_VALUE, offset));
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
        // fall through
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
      case TRUSTED_RESOURCE_URI_END:
        throw new AssertionError("impossible");
      case NONE:
        // generally impossible
      case START:
        // fall-through.  this should have been handled by our callers
    }
    throw new AssertionError("Unanticipated URI part: " + uriPart);
  }

  /**
   * Transition between different parts of an http-like URL.
   *
   * <p>This happens on the first important URI character, or upon seeing the end of the raw text
   * segment and not seeing anything else.
   */
  private static final Transition URI_PART_TRANSITION =
      new Transition(Pattern.compile("([:./&?=#])|\\z")) {
        @Override
        boolean isApplicableTo(Context prior, Matcher matcher) {
          return prior.uriType != UriType.TRUSTED_RESOURCE;
        }

        @Override
        Context computeNextContext(RawTextNode node, int offset, Context prior, Matcher matcher) {
          UriPart uriPart = prior.uriPart;
          if (uriPart == UriPart.START) {
            uriPart = UriPart.MAYBE_SCHEME;
          }
          String match = matcher.group(1);
          if (match != null) {
            checkState(match.length() == 1);
            uriPart = getNextUriPart(node, offset, uriPart, match.charAt(0));
          }
          return prior.derive(uriPart);
        }
      };

  /** Transition to detect dangerous URI schemes. */
  private static final Transition URI_START_TRANSITION =
      new Transition(Pattern.compile("(?i)^(javascript|data|blob|filesystem):")) {
        @Override
        boolean isApplicableTo(Context prior, Matcher matcher) {
          return prior.uriPart == UriPart.START && prior.uriType != UriType.TRUSTED_RESOURCE;
        }

        @Override
        Context computeNextContext(Context prior, Matcher matcher) {
          // TODO(gboyer): Ban all but whitelisted schemes.
          return prior.derive(UriPart.DANGEROUS_SCHEME);
        }
      };

  /**
   * Transition between different parts of a trusted resource uri http-like URL.
   *
   * <p>We don't use the normal URI derivation algorithm because for trusted_resource_uris we have
   * stricter rules.
   *
   * <ul>
   *   <li>If a scheme is present, it must be {@code https}
   *   <li>We don't allow partial scheme or hosts
   *   <li>from URI START we must end up in AUTHORITY_OR_PATH, though in our case it really just
   *       means PATH
   * </ul>
   */
  private static class TrustedResourceUriPartTransition extends Transition {
    private static final Pattern BASE_URL_PATTERN =
        Pattern.compile(
            "^((https:)?//[0-9a-z.:\\[\\]-]+/" // Origin.
                + "|/[^/\\\\]" // Absolute path.
                + "|[^:/\\\\]+/" // Relative path.
                + "|[^:/\\\\]*[?#]" // Query string or fragment.
                + "|about:blank#" // about:blank with fragment.
                + ")",
            Pattern.CASE_INSENSITIVE);

    TrustedResourceUriPartTransition(Pattern pattern) {
      super(pattern);
    }

    /** Matches the whole string. */
    TrustedResourceUriPartTransition() {
      super();
    }

    @Override
    boolean isApplicableTo(Context prior, @Nullable Matcher matcher) {
      return prior.uriType == UriType.TRUSTED_RESOURCE;
    }

    @Override
    Context computeNextContext(
        RawTextNode node, int offset, Context context, @Nullable Matcher matcher) {
      String match = matcher == null ? node.getRawText().substring(offset) : matcher.group();
      switch (context.uriPart) {
        case START:
          // Most of the work is here.  We expect the match to be one of the following forms:
          // - https://foo/  NOTYPO
          // - //foo/
          // - Absolute or relative path.
          // This emulates the behavior of goog.html.TrustedResourceUrl.format
          // NOTE: In all cases we require that the fixed portion of the URL ends in path context.
          // This is important to make sure that neither scheme nor host are potentially attacker
          // controlled.
          // Additionally, we will escapeUri all dynamic parts of the URL after this point which
          // allows some things like query parameters to be set using untrusted content.
          if (!BASE_URL_PATTERN.matcher(match).find()) {
            // If the prefix is not allowed then we switch to UriPart.END meaning that we don't
            // allow anything after this node (the whole URI must be fixed).
            context = context.derive(UriPart.TRUSTED_RESOURCE_URI_END);
            break;
          } else {
            context = context.derive(UriPart.AUTHORITY_OR_PATH);
          }
          // and fall-through
        case AUTHORITY_OR_PATH:
          int queryIndex = match.indexOf('?');
          if (queryIndex == -1) {
            // this might occur if a dynamic node ended with some query parameters
            queryIndex = match.indexOf('&');
          }
          if (queryIndex != -1) {
            context = context.derive(UriPart.QUERY);
          }
          // fall-through
        case QUERY:
          if (match.indexOf('#') == -1) {
            // still in the query
            return context;
          }
          context = context.derive(UriPart.FRAGMENT);
          break;
        case FRAGMENT:
          // fragment is the end
          return context;
        case TRUSTED_RESOURCE_URI_END:
          return context;
        case DANGEROUS_SCHEME:
        case MAYBE_SCHEME:
        case MAYBE_VARIABLE_SCHEME:
        case UNKNOWN:
        case UNKNOWN_PRE_FRAGMENT:
          throw SoyAutoescapeException.createWithNode(
              "Cannot safely process this TrustedResourceUri at compile time. "
                  + "TrustedResourceUris must have a statically identifiable scheme and host. "
                  + "Either use a hard-coded scheme, or move the calculation of this URL outside "
                  + "of the template and use an ordaining API.",
              node.substring(/* newId= */ Integer.MAX_VALUE, offset));
        case NONE:
          throw new AssertionError("impossible");
      }
      return context;
    }
  }

  /** Matches the beginning of a CSS URI with the delimiter, if any, in group 1. */
  private static Transition makeCssUriTransition(Pattern regex, final UriType uriType) {
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
  private static Transition makeDivPreceder(String literal) {
    return new Transition(literal) {
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
          .put(
              HtmlContext.HTML_META_REFRESH_CONTENT,
              ImmutableList.of(
                  new Transition(
                      Pattern.compile("[,;] *(URL *=? *)?['\"]?", Pattern.CASE_INSENSITIVE)) {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      return prior
                          .toBuilder()
                          .withState(HtmlContext.URI)
                          .withUriType(UriType.REFRESH)
                          .withUriPart(UriPart.START)
                          .build();
                    }
                  },
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.HTML_HTML_ATTR_VALUE,
              ImmutableList.of(
                  new Transition() {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      return prior.derive(HtmlHtmlAttributePosition.NOT_START);
                    }
                  },
                  TRANSITION_TO_SELF))
          // The CSS transitions below are based on http://www.w3.org/TR/css3-syntax/#lexical
          .put(
              HtmlContext.CSS,
              ImmutableList.of(
                  makeTransitionToStateLiteral("/*", HtmlContext.CSS_COMMENT),
                  // TODO: Do we need to support non-standard but widely supported C++ style
                  // comments?
                  makeTransitionToStateLiteral("\"", HtmlContext.CSS_DQ_STRING),
                  makeTransitionToStateLiteral("'", HtmlContext.CSS_SQ_STRING),
                  // Although we don't contextually parse CSS, certain property names are only used
                  // in conjunction with images.  This pretty basic regexp does a decent job on CSS
                  // that is not attempting to be malicious (for example, doesn't handle comments).
                  // Note that this can be fooled with {if 1}foo-{/if}background, but it's not worth
                  // really worrying about.
                  makeCssUriTransition(
                      Pattern.compile(
                          "(?i)(?:[^a-z0-9-]|^)\\s*"
                              + "(?:background|background-image|border-image|content"
                              + "|cursor|list-style|list-style-image)"
                              + "\\s*:\\s*url\\s*\\(\\s*(['\"]?)"),
                      UriType.MEDIA),
                  makeCssUriTransition(
                      Pattern.compile("@import\\b(?:\\s+url\\s*\\()?\\s*(['\"]?)"),
                      UriType.TRUSTED_RESOURCE),
                  makeCssUriTransition(
                      Pattern.compile("(?i)\\burl\\s*\\(\\s*(['\"]?)"), UriType.NORMAL),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_COMMENT,
              ImmutableList.of(
                  makeTransitionToStateLiteral("*/", HtmlContext.CSS), TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_DQ_STRING,
              ImmutableList.of(
                  makeTransitionToStateLiteral("\"", HtmlContext.CSS),
                  makeTransitionToSelf(
                      Pattern.compile("\\\\(?:\r\n?|[\n\f\"])")), // Line continuation or escape.
                  makeTransitionToError(
                      Pattern.compile("[\n\r\f]"), "Newlines not permitted in string literals."),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_SQ_STRING,
              ImmutableList.of(
                  makeTransitionToStateLiteral("'", HtmlContext.CSS),
                  makeTransitionToSelf(
                      Pattern.compile("\\\\(?:\r\n?|[\n\f'])")), // Line continuation or escape.
                  makeTransitionToError(
                      Pattern.compile("[\n\r\f]"), "Newlines not permitted in string literals."),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.CSS_URI,
              ImmutableList.of(
                  makeTransitionToState(Pattern.compile("[\\)\\s]"), HtmlContext.CSS),
                  URI_PART_TRANSITION,
                  URI_START_TRANSITION,
                  new TrustedResourceUriPartTransition(Pattern.compile("[^);\n\r\f]+")),
                  makeTransitionToError(
                      Pattern.compile("[\"']"), "Quotes not permitted in CSS URIs.")))
          .put(
              HtmlContext.CSS_SQ_URI,
              ImmutableList.of(
                  makeTransitionToStateLiteral("'", HtmlContext.CSS),
                  URI_PART_TRANSITION,
                  URI_START_TRANSITION,
                  new TrustedResourceUriPartTransition(Pattern.compile("[^'\n\r\f]+")),
                  makeTransitionToSelf(
                      Pattern.compile("\\\\(?:\r\n?|[\n\f'])")), // Line continuation or escape.
                  makeTransitionToError(
                      Pattern.compile("[\n\r\f]"), "Newlines not permitted in string literal.")))
          .put(
              HtmlContext.CSS_DQ_URI,
              ImmutableList.of(
                  makeTransitionToStateLiteral("\"", HtmlContext.CSS),
                  URI_PART_TRANSITION,
                  URI_START_TRANSITION,
                  new TrustedResourceUriPartTransition(Pattern.compile("[^\n\r\f\"]+")),
                  makeTransitionToSelf(
                      Pattern.compile("\\\\(?:\r\n?|[\n\f\"])")), // Line continuation or escape.
                  makeTransitionToError(
                      Pattern.compile("[\n\r\f]"), "Newlines not permitted in string literal.")))
          .put(
              HtmlContext.JS,
              ImmutableList.of(
                  makeTransitionToStateLiteral("/*", HtmlContext.JS_BLOCK_COMMENT),
                  makeTransitionToStateLiteral("//", HtmlContext.JS_LINE_COMMENT),
                  makeTransitionToJsStringLiteral("\"", HtmlContext.JS_DQ_STRING),
                  makeTransitionToJsStringLiteral("'", HtmlContext.JS_SQ_STRING),
                  new Transition("`") {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      return prior
                          .toBuilder()
                          .withState(HtmlContext.JS_TEMPLATE_LITERAL)
                          .withJsTemplateLiteralNestDepth(prior.jsTemplateLiteralNestDepth + 1)
                          .build();
                    }
                  },
                  new Transition("}") {
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
                        case NONE:
                        case UNKNOWN:
                          RawTextNode suffixNode =
                              node.substring(/* new node id= */ Integer.MAX_VALUE, offset);
                          throw SoyAutoescapeException.createWithNode(
                              "Slash (/) cannot follow the preceding branches since it is unclear "
                                  + "whether the slash is a RegExp literal or division operator.  "
                                  + "Please add parentheses in the branches leading to `"
                                  + suffixNode.getRawText()
                                  + "`",
                              suffixNode);
                      }
                      throw new AssertionError(prior.slashType);
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
                  new Transition(Pattern.compile("[^/\"'}`\\s\\\\]+")) {
                    @Override
                    Context computeNextContext(Context prior, Matcher matcher) {
                      return prior.derive(
                          JsUtil.isRegexPreceder(matcher.group())
                              ? Context.JsFollowingSlash.REGEX
                              : Context.JsFollowingSlash.DIV_OP);
                    }
                  },
                  // Space
                  makeTransitionToSelf(Pattern.compile("\\s+"))))
          .put(
              HtmlContext.JS_BLOCK_COMMENT,
              ImmutableList.of(
                  makeTransitionToStateLiteral("*/", HtmlContext.JS), TRANSITION_TO_SELF))
          // Line continuations are not allowed in line comments.
          .put(
              HtmlContext.JS_LINE_COMMENT,
              ImmutableList.of(
                  makeTransitionToState(Pattern.compile("[" + JS_LINEBREAKS + "]"), HtmlContext.JS),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.JS_DQ_STRING,
              ImmutableList.of(
                  makeDivPreceder("\""),
                  makeTransitionToSelf(
                      Pattern.compile(
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
                              + ")++"))))
          .put(
              HtmlContext.JS_SQ_STRING,
              ImmutableList.of(
                  makeDivPreceder("'"),
                  makeTransitionToSelf(
                      Pattern.compile(
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
                              + ")++"))))
          .put(
              HtmlContext.JS_TEMPLATE_LITERAL,
              ImmutableList.of(
                  new Transition("`") {
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
                  makeTransitionToSelf(Pattern.compile("\\\\[$`]")),
                  // ${ puts us into js context
                  makeTransitionToStateLiteral("${", HtmlContext.JS),
                  TRANSITION_TO_SELF))
          .put(
              HtmlContext.JS_REGEX,
              ImmutableList.of(
                  makeDivPreceder("/"),
                  makeTransitionToSelf(
                      Pattern.compile(
                          "(?i)^(?:"
                              +
                              /**
                               * We have to handle [...] style character sets specially since in
                               * /[/]/, the second solidus doesn't end the regular expression.
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
                              + ")+"))))
          .put(
              HtmlContext.URI,
              ImmutableList.of(
                  URI_PART_TRANSITION,
                  URI_START_TRANSITION,
                  new TrustedResourceUriPartTransition()))
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
