/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.basicdirectives;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A directive that inserts word breaks as necessary.
 *
 * <p>It takes a single argument : an integer specifying the max number of characters between
 * breaks.
 *
 */
@Singleton
@SoyPurePrintDirective
final class InsertWordBreaksDirective
    implements SanitizedContentOperator,
        SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective {

  @Inject
  InsertWordBreaksDirective() {}

  @Override
  public String getName() {
    return "|insertWordBreaks";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public boolean shouldCancelAutoescape() {
    return false;
  }

  @Override
  @Nonnull
  public SanitizedContent.ContentKind getContentKind() {
    // This directive expects HTML as input and produces HTML as output.
    return SanitizedContent.ContentKind.HTML;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {

    int maxCharsBetweenWordBreaks;
    try {
      maxCharsBetweenWordBreaks = args.get(0).integerValue();
    } catch (SoyDataException sde) {
      throw new IllegalArgumentException(
          "Could not parse 'insertWordBreaks' parameter as integer.");
    }

    StringBuilder result = new StringBuilder();

    // These variables keep track of important state while looping through the string below.
    boolean isInTag = false; // whether we're inside an HTML tag
    boolean isMaybeInEntity = false; // whether we might be inside an HTML entity
    int numCharsWithoutBreak = 0; // number of characters since the last word break

    String str = value.coerceToString();
    for (int codePoint, i = 0, n = str.length(); i < n; i += Character.charCount(codePoint)) {
      codePoint = str.codePointAt(i);

      // If hit maxCharsBetweenWordBreaks, and next char is not a space, then add <wbr>.
      if (numCharsWithoutBreak >= maxCharsBetweenWordBreaks && codePoint != ' ') {
        result.append("<wbr>");
        numCharsWithoutBreak = 0;
      }

      if (isInTag) {
        // If inside an HTML tag and we see '>', it's the end of the tag.
        if (codePoint == '>') {
          isInTag = false;
        }

      } else if (isMaybeInEntity) {
        switch (codePoint) {
            // If maybe inside an entity and we see ';', it's the end of the entity. The entity
            // that just ended counts as one char, so increment numCharsWithoutBreak.
          case ';':
            isMaybeInEntity = false;
            ++numCharsWithoutBreak;
            break;
            // If maybe inside an entity and we see '<', we weren't actually in an entity. But
            // now we're inside an HTML tag.
          case '<':
            isMaybeInEntity = false;
            isInTag = true;
            break;
            // If maybe inside an entity and we see ' ', we weren't actually in an entity. Just
            // correct the state and reset the numCharsWithoutBreak since we just saw a space.
          case ' ':
            isMaybeInEntity = false;
            numCharsWithoutBreak = 0;
            break;
        }

      } else { // !isInTag && !isInEntity
        switch (codePoint) {
            // When not within a tag or an entity and we see '<', we're now inside an HTML tag.
          case '<':
            isInTag = true;
            break;
            // When not within a tag or an entity and we see '&', we might be inside an entity.
          case '&':
            isMaybeInEntity = true;
            break;
            // When we see a space, reset the numCharsWithoutBreak count.
          case ' ':
            numCharsWithoutBreak = 0;
            break;
            // When we see a non-space, increment the numCharsWithoutBreak.
          default:
            ++numCharsWithoutBreak;
            break;
        }
      }

      // In addition to adding <wbr>s, we still have to add the original characters.
      result.appendCodePoint(codePoint);
    }

    // Make sure to transmit the known direction, if any, to any downstream directive that may need
    // it, e.g. BidiSpanWrapDirective. Since a known direction is carried only by SanitizedContent,
    // and the transformation we make is only valid in HTML, we only transmit the direction when we
    // get HTML SanitizedContent.
    // TODO(user): Consider always returning HTML SanitizedContent.
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == ContentKind.HTML) {
        return UnsafeSanitizedContentOrdainer.ordainAsSafe(
            result.toString(), ContentKind.HTML, sanitizedContent.getContentDirection());
      }
    }

    return StringData.forValue(result.toString());
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {

    return new JsExpr(
        "soy.$$insertWordBreaks(" + value.getText() + ", " + args.get(0).getText() + ")",
        Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

}
