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

package com.google.template.soy.parsepasses.contextautoesc;

import static org.junit.Assert.assertEquals;

import com.google.common.base.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ContextTest {

  private void assertUnionNoop(String dominating, String other) {
    assertUnion(dominating, dominating, other);
  }

  private static void assertUnion(String expected, String a, String b) {
    Context aContext = Context.parse(a);
    Context bContext = Context.parse(b);
    Context expectedContext = Context.parse(expected);
    assertEquals(
        "Union of " + aContext + " and " + bContext,
        Optional.of(expectedContext),
        Context.union(aContext, bContext));
    assertEquals(
        "Reverse union of " + bContext + " and " + aContext,
        Optional.of(expectedContext),
        Context.union(bContext, aContext));
  }

  private static void assertUnionFails(String a, String b) {
    Context aContext = Context.parse(a);
    Context bContext = Context.parse(b);
    assertEquals(
        "Union of " + aContext + " and " + bContext,
        Optional.absent(),
        Context.union(aContext, bContext));
    assertEquals(
        "Reverse union of " + bContext + " and " + aContext,
        Optional.absent(),
        Context.union(bContext, aContext));
  }

  @Test
  public void testObviousUnions() {
    assertUnionNoop("CSS", "CSS");
    assertUnionNoop("HTML_PCDATA", "HTML_PCDATA");
    assertUnionNoop("JS", "JS");
    assertUnionNoop("HTML_TAG_NAME", "HTML_TAG_NAME");
  }

  @Test
  public void testJsUnions() {
    // Identity cases.
    assertUnionNoop("JS UNKNOWN", "JS UNKNOWN");
    assertUnionNoop("JS DIV_OP", "JS DIV_OP");
    assertUnionNoop("JS REGEX", "JS REGEX");

    // Unknown accepts either.
    assertUnionNoop("JS UNKNOWN", "JS REGEX");
    assertUnionNoop("JS UNKNOWN", "JS DIV_OP");

    // Unknown is the join of the two different JS types.
    assertUnion("JS UNKNOWN", "JS REGEX", "JS DIV_OP");

    // Same, even if it's within a script attribute.
    assertUnion(
        "JS NORMAL SCRIPT DOUBLE_QUOTE UNKNOWN",
        "JS NORMAL SCRIPT DOUBLE_QUOTE REGEX",
        "JS NORMAL SCRIPT DOUBLE_QUOTE DIV_OP");

    // One is in a script, one is not:
    assertUnionFails("JS NORMAL SCRIPT DOUBLE_QUOTE REGEX", "JS REGEX");
  }

  @Test
  public void testUriPartUnions() {
    // The more well-formed states:
    String start = "URI START NORMAL";
    String maybeScheme = "URI MAYBE_SCHEME NORMAL";
    String authorityOrPath = "URI AUTHORITY_OR_PATH NORMAL";
    String query = "URI QUERY NORMAL";
    String fragment = "URI FRAGMENT NORMAL";
    // The more nebulous and sticky states:
    String maybeVariableScheme = "URI MAYBE_VARIABLE_SCHEME NORMAL";
    String unknownPreFragment = "URI UNKNOWN_PRE_FRAGMENT NORMAL";
    // NOTE: "NONE" needed to disambiguate from JsFollowingSlash.UNKNOWN
    String unknown = "URI NONE NONE NONE NONE UNKNOWN NORMAL";
    String dangerousScheme = "URI DANGEROUS_SCHEME NORMAL";

    // Well-formed states, identity:
    assertUnionNoop(start, start);
    assertUnionNoop(maybeScheme, maybeScheme);
    assertUnionNoop(authorityOrPath, authorityOrPath);
    assertUnionNoop(query, query);
    assertUnionNoop(fragment, fragment);

    // Well-formed states before the fragment:
    assertUnion(unknownPreFragment, start, maybeScheme);
    assertUnion(unknownPreFragment, start, authorityOrPath);
    assertUnion(unknownPreFragment, start, query);
    assertUnion(unknownPreFragment, maybeScheme, authorityOrPath);
    assertUnion(unknownPreFragment, maybeScheme, query);
    assertUnion(unknownPreFragment, authorityOrPath, query);

    // Well-formed states, both before and after fragment:
    assertUnion(unknown, start, fragment);
    assertUnion(unknown, maybeScheme, fragment);
    assertUnion(unknown, authorityOrPath, fragment);
    assertUnion(unknown, query, fragment);

    // Variable at the beginning matched with other parts:
    assertUnionNoop(maybeVariableScheme, start);
    assertUnionNoop(maybeVariableScheme, maybeScheme);
    assertUnionNoop(maybeVariableScheme, authorityOrPath);
    assertUnionNoop(maybeVariableScheme, query);
    assertUnionNoop(maybeVariableScheme, maybeVariableScheme);
    assertUnion(unknown, maybeVariableScheme, fragment);

    // Unknown but before fragment:
    assertUnionNoop(unknownPreFragment, start);
    assertUnionNoop(unknownPreFragment, maybeScheme);
    assertUnionNoop(unknownPreFragment, authorityOrPath);
    assertUnionNoop(unknownPreFragment, query);
    assertUnionNoop(unknownPreFragment, maybeVariableScheme);
    assertUnionNoop(unknownPreFragment, unknownPreFragment);
    assertUnion(unknown, unknownPreFragment, fragment);

    // Either before or after fragment:
    assertUnionNoop(unknown, start);
    assertUnionNoop(unknown, maybeScheme);
    assertUnionNoop(unknown, maybeVariableScheme);
    assertUnionNoop(unknown, authorityOrPath);
    assertUnionNoop(unknown, query);
    assertUnionNoop(unknown, unknownPreFragment);
    assertUnionNoop(unknown, fragment);

    // Poison context:
    assertUnionNoop(dangerousScheme, start);
    assertUnionNoop(dangerousScheme, maybeScheme);
    assertUnionNoop(dangerousScheme, maybeVariableScheme);
    assertUnionNoop(dangerousScheme, authorityOrPath);
    assertUnionNoop(dangerousScheme, query);
    assertUnionNoop(dangerousScheme, unknownPreFragment);
    assertUnionNoop(dangerousScheme, fragment);
    assertUnionNoop(dangerousScheme, unknown);
  }

  @Test
  public void testUriTypeUnions() {
    assertUnionNoop("URI START NORMAL", "URI START NORMAL");
    assertUnionNoop("URI START MEDIA", "URI START MEDIA");
    // For now, we don't allow unioning these two types.
    assertUnionFails("URI START MEDIA", "URI START NORMAL");
  }

  @Test
  public void testTagStates() {
    // Identity for HTML_TAG:
    assertUnionNoop("HTML_TAG NORMAL", "HTML_TAG NORMAL");
    // Two very different types of tags: {if $x}<img{else}<script{/if}
    assertUnionFails("HTML_TAG NORMAL", "HTML_TAG SCRIPT");

    // Something like: <a{if $x} foo="bar"{/if} or even <a{if $x} {/if}
    assertUnionNoop("HTML_TAG NORMAL", "HTML_TAG_NAME NORMAL");
    assertUnionNoop("HTML_TAG STYLE", "HTML_TAG_NAME STYLE");

    // Incompatible tag contexts: {if $x}<img src="foo"{else}<script{/if}
    assertUnionFails("HTML_TAG NORMAL", "HTML_TAG_NAME SCRIPT");

    // Case: <input {if $x}checked{/if}
    assertUnionNoop("HTML_TAG SCRIPT", "HTML_ATTRIBUTE_NAME SCRIPT");
    // Same but different element types.
    assertUnionFails("HTML_TAG NORMAL", "HTML_ATTRIBUTE_NAME SCRIPT");

    // Something like: <a {if $x}b=foo{/if}
    assertUnionNoop("HTML_TAG NORMAL", "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END");
    // Similar, but one side is a script and the other isn't:
    assertUnionFails(
        "HTML_TAG SCRIPT", "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SPACE_OR_TAG_END");
    // Or, unclosed quote: <a {if $x}b="foo{/if}
    assertUnionFails("HTML_TAG NORMAL", "HTML_NORMAL_ATTR_VALUE NORMAL PLAIN_TEXT SINGLE_QUOTE");
  }

  @Test
  public void testClearlyFailingUnions() {
    assertUnionFails("TEXT", "HTML_PCDATA");
    assertUnionFails("CSS", "JS");
  }
}
