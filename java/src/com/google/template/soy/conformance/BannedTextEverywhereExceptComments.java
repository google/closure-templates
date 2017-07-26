/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.regex.Pattern;

/**
 * Conformance rule banning text in Soy files everywhere except in Soy comments (plausible example:
 * the string {@code http://}).
 *
 * <p>(Soy comments are the same as Java comments: <code>//</code> and <code>/*</code>.)
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class BannedTextEverywhereExceptComments extends Rule<SoyFileNode> {

  private final ImmutableSet<String> bannedTexts;

  public BannedTextEverywhereExceptComments(ImmutableSet<String> bannedTexts, SoyErrorKind error) {
    super(error);
    this.bannedTexts = bannedTexts;
  }

  @Override
  public void checkConformance(SoyFileNode node, ErrorReporter errorReporter) {
    // This conformance check relies on the fact that Soy (like many compilers) does not include
    // comments in its AST. Instead of writing a custom AST visitor, the check can just call
    // SoyNode#toSourceString on the root to produce a reasonable serialization, and do
    // string matching on that.
    //
    // There is one exception: SoyDoc comments /** like this */ are preserved in the AST.
    // If this check finds banned text in the AST serialization, it double-checks (using regexes)
    // to make sure the text occurs outside a JsDoc comment.
    String sourceString = node.toSourceString();
    for (String bannedText : bannedTexts) {
      if (sourceString.contains(bannedText)
          && bannedTextOccursOutsideJsDoc(sourceString, bannedText)) {
        // TODO(lukes): this source location is bad, but given this implementation there isn't an
        // alternate option.  This whole check should probably be rethought reimplemented.
        errorReporter.report(new SourceLocation(node.getFilePath()), error);
      }
    }
  }

  private static final Pattern COMMENTS_PATTERN = Pattern.compile("/\\*\\*.*?\\*/", Pattern.DOTALL);

  private static boolean bannedTextOccursOutsideJsDoc(String sourceString, String bannedText) {
    String withoutComments = COMMENTS_PATTERN.matcher(sourceString).replaceAll("");
    return withoutComments.contains(bannedText);
  }
}
