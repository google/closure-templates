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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.internal.base.UnescapeUtils;

/**
 * Propagates {@link Context}s across raw text chunks using a state-machine parser for HTML/CSS/JS.
 *
 * <p>
 * Given some raw HTML text {@code "<b>Hello, World!</b>"} and the
 * {@link Context#HTML_PCDATA HTML_PCDATA} starting context, this class will decompose the rawText
 * into a number of tokens and compute follow on contexts for each.
 * <table>
 * <tr><td>{@code <}</td><td>{@link Context.State#HTML_TAG_NAME}</td></tr>
 * <tr><td>{@code b}</td><td>{@link Context.State#HTML_TAG}</td></tr>
 * <tr><td>{@code >}</td><td>{@link Context.State#HTML_PCDATA}</td></tr>
 * <tr><td>{@code Hello, World!}</td><td>{@link Context.State#HTML_PCDATA}</td></tr>
 * <tr><td>{@code </}</td><td>{@link Context.State#HTML_TAG_NAME}</td></tr>
 * <tr><td>{@code b}</td><td>{@link Context.State#HTML_TAG}</td></tr>
 * <tr><td>{@code >}</td><td>{@link Context.State#HTML_PCDATA}</td></tr>
 * </table>
 *
 * @author Mike Samuel
 */
final class RawTextContextUpdater {

  /**
   * @param rawText A chunk of HTML/CSS/JS.
   * @param context The context before rawText.
   * @return The context after rawText.
   */
  public static Context processRawText(String rawText, Context context)
      throws SoyAutoescapeException {
    while (rawText.length() != 0) {
      // If we are in an attribute value, then decode rawText (except for the delimiter) up to the
      // next occurrence of delimiter.

      // The end of the section to decode.  Either before a delimiter or > symbol that closes an
      // attribute, at the end of the rawText, or -1 if no decoding needs to happen.
      int attrValueEnd = findEndOfAttributeValue(rawText, context.delimType);
      if (attrValueEnd == -1) {
        // Outside an attribute value.  No need to decode.
        RawTextContextUpdater cu = new RawTextContextUpdater();
        cu.processNextToken(rawText, context);
        rawText = rawText.substring(cu.numCharsConsumed);
        context = cu.next;

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
        int rawTextLen = rawText.length();

        // The end of the attribute value.  At attrValueEnd, or attrValueend + 1 if a delimiter
        // needs to be consumed.
        int attrEnd = attrValueEnd < rawTextLen ?
            attrValueEnd + context.delimType.text.length() : -1;

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

        String attrValueTail = UnescapeUtils.unescapeHtml(rawText.substring(0, attrValueEnd));
        // attrValueTail is "!\")" in the example above.

        // Recurse on the decoded value.
        RawTextContextUpdater cu = new RawTextContextUpdater();
        while (attrValueTail.length() != 0) {
          cu.processNextToken(attrValueTail, context);
          attrValueTail = attrValueTail.substring(cu.numCharsConsumed);
          context = cu.next;
        }

        // TODO: Maybe check that context is legal to leave an attribute in.  Throw if the attribute
        // ends inside a quoted string.

        if (attrEnd != -1) {
          rawText = rawText.substring(attrEnd);
          // rawText is now ">" from the example above.

          // When an attribute ends, we're back in the tag.
          context = new Context(
              Context.State.HTML_TAG, context.elType, Context.AttributeType.NONE,
              Context.AttributeEndDelimiter.NONE, Context.JsFollowingSlash.NONE,
              Context.UriPart.NONE);
        } else {
          // Whole tail is part of an unterminated attribute.
          if (attrValueEnd != rawText.length()) {
            throw new IllegalStateException();
          }
          rawText = "";
        }
      }
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
   * Consume a portion of text and compute the next context.
   * Output is stored in member variables.
   * @param text Non empty.
   */
  private void processNextToken(String text, Context context) throws SoyAutoescapeException {
    if (context.isErrorContext()) {  // The ERROR state is infectious.
      this.numCharsConsumed = text.length();
      this.next = context;
      return;
    }

    // Find the transition whose pattern matches earliest in the raw text.
    int earliestStart = Integer.MAX_VALUE;
    int earliestEnd = -1;
    Transition earliestTransition = null;
    Matcher earliestMatcher = null;
    for (Transition transition : TRANSITIONS.get(context.state)) {
      Matcher matcher = transition.pattern.matcher(text);
      if (matcher.find()) {
        int start = matcher.start();
        if (start < earliestStart) {
          int end = matcher.end();
          if (transition.isApplicableTo(context, matcher)) {
            earliestStart = start;
            earliestEnd = end;
            earliestTransition = transition;
            earliestMatcher = matcher;
          }
        }
      }
    }

    if (earliestTransition != null) {
      this.next = earliestTransition.computeNextContext(context, earliestMatcher);
      this.numCharsConsumed = earliestEnd;
    } else {
      this.next = Context.ERROR;
      this.numCharsConsumed = text.length();
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
     * This should not destructively modify the matcher.
     * Specifically, it should not call {@code find()} again.
     * @param prior The context before the start of the token in matcher.
     * @param matcher The token matched by {@code this.pattern}.
     */
    boolean isApplicableTo(Context prior, Matcher matcher) {
      return true;
    }

    /**
     * Computes the context that this production transitions to after rawText[0:matcher.end()].
     * @param prior The context prior to the token in matcher.
     * @param matcher The token matched by {@code this.pattern}.
     * @return The context after the given token.
     */
    abstract Context computeNextContext(Context prior, Matcher matcher)
        throws SoyAutoescapeException;
  }


  /** A transition to a given context. */
  private static Transition makeTransitionTo(String regex, final Context dest) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return dest;
      }
    };
  }

  /** A transition to a context in the body of an open tag for the given element. */
  private static Transition makeTransitionToTag(String regex, final Context.ElementType el) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return new Context(
            Context.State.HTML_TAG, el, Context.AttributeType.NONE,
            Context.AttributeEndDelimiter.NONE, Context.JsFollowingSlash.NONE,
            Context.UriPart.NONE);
      }
    };
  }

  /** A transition back to a context in the body of an open tag. */
  private static Transition makeTransitionBackToTag(String regex) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return new Context(
            Context.State.HTML_TAG, prior.elType, Context.AttributeType.NONE,
            Context.AttributeEndDelimiter.NONE, Context.JsFollowingSlash.NONE,
            Context.UriPart.NONE);
      }
    };
  }

  /**
   * A transition to a context in the name of an attribute whose type is determined from its name.
   * @param regex A regular expression whose group 1 is a prefix of an attribute name.
   */
  private static Transition makeTransitionToAttrName(String regex) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        String attrName = matcher.group(1).toLowerCase(Locale.ENGLISH);
        // Get the local name so we can treat xlink:href and svg:style as per HTML.
        int colon = attrName.lastIndexOf(':');
        String localName = attrName.substring(colon + 1);

        Context.AttributeType attr;
        if (localName.startsWith("on")) {
          attr = Context.AttributeType.SCRIPT;
        } else if ("style".equals(localName)) {
          attr = Context.AttributeType.STYLE;
        } else if (URI_ATTR_NAMES.contains(localName)
                   || CUSTOM_URI_ATTR_NAMING_CONVENTION.matcher(localName).find()
                   || "xmlns".equals(attrName) || attrName.startsWith("xmlns:")) {
          attr = Context.AttributeType.URI;
        } else {
          attr = Context.AttributeType.PLAIN_TEXT;
        }
        return new Context(
            Context.State.HTML_ATTRIBUTE_NAME, prior.elType, attr,
            Context.AttributeEndDelimiter.NONE, Context.JsFollowingSlash.NONE,
            Context.UriPart.NONE);
      }
    };
  }

  /** A transition to a context in the name of an attribute of the given type. */
  private static Transition makeTransitionToAttrValue(
      String regex, final Context.AttributeEndDelimiter delim) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return Context.computeContextAfterAttributeDelimiter(prior.elType, prior.attrType, delim);
      }
    };
  }

  /**
   * Lower case names of attributes whose value is a URI.
   * This does not identify attributes like {@code <meta content>} which is conditionally a URI
   * depending on the value of other attributes.
   * @see <a href="http://www.w3.org/TR/html4/index/attributes.html">HTML4 attrs with type %URI</a>
   */
  private static Set<String> URI_ATTR_NAMES = ImmutableSet.of(
      "action", "archive", "background", "cite", "classid", "codebase", "data", "dsync", "href",
      "longdesc", "src", "usemap",
      // Custom attributes that are reliably URLs in existing code.
      "entity");

  /** Matches lower-case attribute local names that start or end with "url" or "uri". */
  private static Pattern CUSTOM_URI_ATTR_NAMING_CONVENTION = Pattern.compile(
      "\\bur[il]|ur[il]s?$");

  /**
   * A transition to the given state.
   */
  private static Transition makeTransitionToState(String regex, final Context.State state) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return prior.derive(state).derive(Context.UriPart.NONE);
      }
    };
  }

  /**
   * A transition to the given state.
   */
  private static Transition makeTransitionToJsString(
      String regex, final Context.State state) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return new Context(
            state, prior.elType, prior.attrType, prior.delimType, Context.JsFollowingSlash.NONE,
            Context.UriPart.NONE);
      }
    };
  }

  /**
   * A transition that consumes some content without changing state.
   */
  private static Transition makeTransitionToSelf(String regex) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return prior;
      }
    };
  }

  /** Consumes the entire content without change if nothing else matched. */
  private static final Transition TRANSITION_TO_SELF = makeTransitionToSelf("\\z");
  // Matching at the end is lowest possible precedence.

  private static final Transition URI_PART_TRANSITION = new Transition("[?#]|\\z") {
    @Override boolean isApplicableTo(Context prior, Matcher matcher) {
      return true;
    }
    @Override Context computeNextContext(Context prior, Matcher matcher) {
      Context.UriPart uriPart = prior.uriPart;
      if (uriPart == Context.UriPart.START) {
        uriPart = Context.UriPart.PRE_QUERY;
      }
      if (uriPart != Context.UriPart.FRAGMENT) {
        String match = matcher.group(0);
        if ("?".equals(match) && uriPart != Context.UriPart.UNKNOWN) {
          uriPart = Context.UriPart.QUERY;
        } else if ("#".equals(match)) {
          uriPart = Context.UriPart.FRAGMENT;
        }
      }
      return prior.derive(uriPart);
    }
  };

  /**
   * Matches the end of a special tag like {@code script}.
   */
  private static Transition makeEndTagTransition(String tagName) {
    return new Transition("(?i)</" + tagName + "\\b") {
      @Override boolean isApplicableTo(Context prior, Matcher matcher) {
        return prior.attrType == Context.AttributeType.NONE;
      }
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return new Context(
            Context.State.HTML_TAG, Context.ElementType.NORMAL, Context.AttributeType.NONE,
            Context.AttributeEndDelimiter.NONE, Context.JsFollowingSlash.NONE,
            Context.UriPart.NONE);
      }
    };
    // TODO: This transitions to an HTML_TAG state which can accept attributes.
    // So we allow nonsensical constructs like </br foo="bar">.
    // Add another HTML_END_TAG state that just accepts space and >.
  }

  /**
   * Matches the beginning of a CSS URI with the delimiter, if any, in group 1.
   */
  private static Transition makeCssUriTransition(String regex) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        String delim = matcher.group(1);
        Context.State state;
        if ("\"".equals(delim)) {
          state = Context.State.CSS_DQ_URI;
        } else if ("'".equals(delim)) {
          state = Context.State.CSS_SQ_URI;
        } else {
          state = Context.State.CSS_URI;
        }
        return new Context(
            state, prior.elType, prior.attrType, prior.delimType, prior.slashType,
            Context.UriPart.START);
      }
    };
  }

  /**
   * Matches a portion of JavaScript that can precede a division operator.
   */
  private static Transition makeDivPreceder(String regex) {
    return new Transition(regex) {
      @Override Context computeNextContext(Context prior, Matcher matcher) {
        return new Context(
            Context.State.JS, prior.elType, prior.attrType, prior.delimType,
            Context.JsFollowingSlash.DIV_OP, prior.uriPart);
      }
    };
  }

  /** Characters that break a line in JavaScript source suitable for use in a regex charset. */
  private static final String JS_LINEBREAKS = "\r\n\u2028\u2029";

  /**
   * For each state, a group of rules for consuming raw text and how that affects the document
   * context.
   * The rules each have an associated pattern, and the rule whose pattern matches earliest in the
   * text wins.
   */
  private static final Map<Context.State, List<Transition>> TRANSITIONS =
      ImmutableMap.<Context.State, List<Transition>>builder()
      .put(Context.State.HTML_PCDATA, ImmutableList.of(
          makeTransitionTo("<!--", Context.HTML_COMMENT),
          makeTransitionToTag("(?i)<script(?=[\\s>/]|\\z)", Context.ElementType.SCRIPT),
          makeTransitionToTag("(?i)<style(?=[\\s>/]|\\z)", Context.ElementType.STYLE),
          makeTransitionToTag("(?i)<textarea(?=[\\s>/]|\\z)", Context.ElementType.TEXTAREA),
          makeTransitionToTag("(?i)<title(?=[\\s>/]|\\z)", Context.ElementType.TITLE),
          makeTransitionToTag("(?i)<xmp(?=[\\s>/]|\\z)", Context.ElementType.XMP),
          makeTransitionTo("</?", Context.HTML_BEFORE_TAG_NAME),
          makeTransitionToSelf("[^<]+")))
      .put(Context.State.HTML_BEFORE_TAG_NAME, ImmutableList.of(
          makeTransitionTo("^[a-zA-Z]+", Context.HTML_TAG_NAME),
          makeTransitionTo("^(?=[^a-zA-Z])", Context.HTML_PCDATA)))
      .put(Context.State.HTML_TAG_NAME, ImmutableList.of(
          makeTransitionToSelf("^[a-zA-Z0-9:-]*(?:[a-zA-Z0-9]|\\z)"),
          makeTransitionToTag("^(?=[/\\s>])", Context.ElementType.NORMAL)))
      .put(Context.State.HTML_TAG, ImmutableList.of(
          // Allows {@code data-foo} and other dashed attribute names, but intentionally disallows
          // "--" as an attribute name so that a tag ending after a value-less attribute named "--"
          // cannot be confused with an HTML comment end ("-->").
          makeTransitionToAttrName("(?i)^\\s*([a-z](?:[a-z0-9_:\\-]*[a-z0-9])?)"),
          new Transition("^\\s*/?>") {
            @Override
            Context computeNextContext(Context prior, Matcher matcher) {
              switch (prior.elType) {
                case SCRIPT:
                  return Context.JS;
                case STYLE:
                  return Context.CSS;
                case NORMAL:
                  return Context.HTML_PCDATA;
                case NONE:
                  throw new IllegalStateException();
                case LISTING: case TEXTAREA: case TITLE: case XMP:
                  return new Context(
                      Context.State.HTML_RCDATA, prior.elType, Context.AttributeType.NONE,
                      Context.AttributeEndDelimiter.NONE, Context.JsFollowingSlash.NONE,
                      Context.UriPart.NONE);
              }
              throw new AssertionError("Unrecognized state " + prior.elType);
            }
          },
          makeTransitionToSelf("^\\s+\\z")))
      .put(Context.State.HTML_ATTRIBUTE_NAME, ImmutableList.of(
          makeTransitionToState("^\\s*=", Context.State.HTML_BEFORE_ATTRIBUTE_VALUE),
          // For a value-less attribute, make an epsilon transition back to the tag body context to
          // look for a tag end or another attribute name.
          makeTransitionBackToTag("^")))
      .put(Context.State.HTML_BEFORE_ATTRIBUTE_VALUE, ImmutableList.of(
          makeTransitionToAttrValue("^\\s*\"", Context.AttributeEndDelimiter.DOUBLE_QUOTE),
          makeTransitionToAttrValue("^\\s*\'", Context.AttributeEndDelimiter.SINGLE_QUOTE),
          makeTransitionToAttrValue(
              "^(?=[^\"\'\\s>])",  // Matches any unquoted value part.
              Context.AttributeEndDelimiter.SPACE_OR_TAG_END),
          // Epsilon transition back if there is an empty value followed by an obvious attribute
          // name or a tag end.
          // The first branch handles the blank value in:
          //    <input value=>
          // and the second handles the blank value in:
          //    <input value= name=foo>
          makeTransitionBackToTag("^(?=>|\\s+[\\w-]+\\s*=)"),
          makeTransitionToSelf("^\\s+")))
      .put(Context.State.HTML_COMMENT, ImmutableList.of(
          makeTransitionTo("-->", Context.HTML_PCDATA),
          TRANSITION_TO_SELF))
      .put(Context.State.HTML_NORMAL_ATTR_VALUE, ImmutableList.of(
          TRANSITION_TO_SELF))
      // The CSS transitions below are based on http://www.w3.org/TR/css3-syntax/#lexical
      .put(Context.State.CSS, ImmutableList.of(
          makeTransitionToState("/\\*", Context.State.CSS_COMMENT),
          // TODO: Do we need to support non-standard but widely supported C++ style comments?
          makeTransitionToState("\"", Context.State.CSS_DQ_STRING),
          makeTransitionToState("'", Context.State.CSS_SQ_STRING),
          makeCssUriTransition("(?i)\\burl\\s*\\(\\s*([\'\"]?)"),
          makeEndTagTransition("style"),
          TRANSITION_TO_SELF))
      .put(Context.State.CSS_COMMENT, ImmutableList.of(
          makeTransitionToState("\\*/", Context.State.CSS),
          makeEndTagTransition("style"),
          TRANSITION_TO_SELF))
      .put(Context.State.CSS_DQ_STRING, ImmutableList.of(
          makeTransitionToState("\"", Context.State.CSS),
          makeTransitionToSelf("\\\\(?:\r\n?|[\n\f\"])"),  // Line continuation or escape.
          makeTransitionTo("[\n\r\f]", Context.ERROR),
          makeEndTagTransition("style"),  // TODO: Make this an error transition?
          TRANSITION_TO_SELF))
      .put(Context.State.CSS_SQ_STRING, ImmutableList.of(
          makeTransitionToState("'", Context.State.CSS),
          makeTransitionToSelf("\\\\(?:\r\n?|[\n\f'])"),  // Line continuation or escape.
          makeTransitionTo("[\n\r\f]", Context.ERROR),
          makeEndTagTransition("style"),  // TODO: Make this an error transition?
          TRANSITION_TO_SELF))
      .put(Context.State.CSS_URI, ImmutableList.of(
          makeTransitionToState("[\\)\\s]", Context.State.CSS),
          URI_PART_TRANSITION,
          makeTransitionToState("[\"']", Context.State.ERROR),
          makeEndTagTransition("style")))
      .put(Context.State.CSS_SQ_URI, ImmutableList.of(
          makeTransitionToState("'", Context.State.CSS),
          URI_PART_TRANSITION,
          makeTransitionToSelf("\\\\(?:\r\n?|[\n\f'])"),  // Line continuation or escape.
          makeTransitionTo("[\n\r\f]", Context.ERROR),
          makeEndTagTransition("style")))
      .put(Context.State.CSS_DQ_URI, ImmutableList.of(
          makeTransitionToState("\"", Context.State.CSS),
          URI_PART_TRANSITION,
          makeTransitionToSelf("\\\\(?:\r\n?|[\n\f\"])"),  // Line continuation or escape.
          makeTransitionTo("[\n\r\f]", Context.ERROR),
          makeEndTagTransition("style")))
      .put(Context.State.JS, ImmutableList.of(
          makeTransitionToState("/\\*", Context.State.JS_BLOCK_COMMENT),
          makeTransitionToState("//", Context.State.JS_LINE_COMMENT),
          makeTransitionToJsString("\"", Context.State.JS_DQ_STRING),
          makeTransitionToJsString("'", Context.State.JS_SQ_STRING),
          new Transition("/") {
            @Override
            Context computeNextContext(Context prior, Matcher matcher)
                throws SoyAutoescapeException {
              switch (prior.slashType) {
                case DIV_OP:
                  return new Context(
                      Context.State.JS, prior.elType, prior.attrType, prior.delimType,
                      Context.JsFollowingSlash.REGEX, prior.uriPart);
                case REGEX:
                  return new Context(
                      Context.State.JS_REGEX, prior.elType, prior.attrType, prior.delimType,
                      Context.JsFollowingSlash.NONE, prior.uriPart);
                default:
                  StringBuffer rest = new StringBuffer();
                  matcher.appendTail(rest);
                  throw new SoyAutoescapeException(
                      "Slash (/) cannot follow the preceding branches since it is unclear " +
                      "whether the slash is a RegExp literal or division operator.  " +
                      "Please add parentheses in the branches leading to `" + rest + "`");
              }
            }
          },
          // Shuffle words, punctuation (besides /), and numbers off to an analyzer which does a
          // quick and dirty check to update JsUtil.isRegexPreceder.
          new Transition("(?i)(?:[^</\"'\\s\\\\]|<(?!/script))+") {
            @Override
            Context computeNextContext(Context prior, Matcher matcher) {
              return prior.derive(
                  JsUtil.isRegexPreceder(matcher.group()) ?
                  Context.JsFollowingSlash.REGEX : Context.JsFollowingSlash.DIV_OP);
            }
          },
          makeTransitionToSelf("\\s+"),  // Space
          makeEndTagTransition("script")))
      .put(Context.State.JS_BLOCK_COMMENT, ImmutableList.of(
          makeTransitionToState("\\*/", Context.State.JS),
          makeEndTagTransition("script"),
          TRANSITION_TO_SELF))
      // Line continuations are not allowed in line comments.
      .put(Context.State.JS_LINE_COMMENT, ImmutableList.of(
          makeTransitionToState("[" + JS_LINEBREAKS + "]", Context.State.JS),
          makeEndTagTransition("script"),
          TRANSITION_TO_SELF))
      .put(Context.State.JS_DQ_STRING, ImmutableList.of(
          makeDivPreceder("\""),
          makeEndTagTransition("script"),
          makeTransitionToSelf(
              "(?i)^(?:" +                          // Case-insensitively, from start of string
                "[^\"\\\\" + JS_LINEBREAKS + "<]" + // match any chars except newlines, quotes, \s;
                "|\\\\(?:" +                        // or backslash followed by a
                  "\\r\\n?" +                    // line continuation
                  "|[^\\r<]" +                      // or an escape
                  "|<(?!/script)" +                 // or less-than that doesn't close the script.
                ")" +
                "|<(?!/script)" +
              ")+")))
      .put(Context.State.JS_SQ_STRING, ImmutableList.of(
          makeDivPreceder("'"),
          makeEndTagTransition("script"),
          makeTransitionToSelf(
              "(?i)^(?:" +                          // Case-insensitively, from start of string
                "[^'\\\\" + JS_LINEBREAKS + "<]" +  // match any chars except newlines, quotes, \s;
                "|\\\\(?:" +                        // or a backslash followed by a
                  "\\r\\n?" +                       // line continuation
                  "|[^\\r<]" +                      // or an escape;
                  "|<(?!/script)" +                 // or less-than that doesn't close the script.
                ")" +
                "|<(?!/script)" +
              ")+")))
      .put(Context.State.JS_REGEX, ImmutableList.of(
          makeDivPreceder("/"),
          makeEndTagTransition("script"),
          makeTransitionToSelf(
              "(?i)^(?:" +
                // We have to handle [...] style character sets specially since in /[/]/, the
                // second solidus doesn't end the regular expression.
                "[^\\[\\\\/<" + JS_LINEBREAKS + "]" +      // A non-charset, non-escape token;
                "|\\\\[^" + JS_LINEBREAKS + "]" +          // an escape;
                "|\\\\?<(?!/script)" +
                "|\\[" +                                   // or a character set containing
                  "(?:[^\\]\\\\<" + JS_LINEBREAKS + "]" +  // a normal character,
                  "|\\\\(?:[^" + JS_LINEBREAKS + "]))*" +  // or an escape;
                  "|\\\\?<(?!/script)" +                   // or an angle bracket possibly escaped.
                "\\]" +
              ")+")))
      // TODO: Do we need to recognize URI attributes that start with javascript:, data:text/html,
      // etc. and transition to JS instead with a second layer of percent decoding triggered by
      // a protocol in (DATA, JAVASCRIPT, NONE) added to Context?
      .put(Context.State.URI, ImmutableList.of(URI_PART_TRANSITION))
      .put(Context.State.HTML_RCDATA, ImmutableList.of(
          new Transition("</(\\w+)\\b") {
            @Override
            boolean isApplicableTo(Context prior, Matcher matcher) {
              String tagName = matcher.group(1).toUpperCase(Locale.ENGLISH);
              return prior.elType.name().equals(tagName);
            }
            @Override
            Context computeNextContext(Context prior, Matcher matcher) {
              return new Context(
                  Context.State.HTML_TAG, Context.ElementType.NORMAL, Context.AttributeType.NONE,
                  Context.AttributeEndDelimiter.NONE, Context.JsFollowingSlash.NONE,
                  Context.UriPart.NONE);
            }
          },
          TRANSITION_TO_SELF))
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
