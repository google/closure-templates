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

package com.google.template.soy.javasrc.codedeps;

import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.internal.AugmentedSoyMapData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.shared.restricted.Sanitizers;

import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nullable;


/**
 * Library of utilities needed by the generated code. Do not use these from hand-written code.
 *
 * @author Kai Huang
 */
public class SoyUtils {


  private static final Pattern NEWLINE_PATTERN = Pattern.compile("(\\r\\n|\\r|\\n)");


  // -----------------------------------------------------------------------------------------------
  // Basics.


  public static SoyData $$getData(SoyData collectionData, String keyStr) {

    SoyData value = ((CollectionData) collectionData).get(keyStr);
    return (value != null) ? value : UndefinedData.INSTANCE;
  }


  // TODO: Use this in generated Java code instead of $$getData(), whenever possible.
  public static SoyData $$getDataSingle(SoyData collectionData, String key) {

    SoyData value = ((CollectionData) collectionData).getSingle(key);
    return (value != null) ? value : UndefinedData.INSTANCE;
  }


  public static SoyMapData $$augmentData(SoyMapData baseData, SoyMapData additionalData) {

    AugmentedSoyMapData augmentedData = new AugmentedSoyMapData(baseData);
    for (Map.Entry<String, SoyData> entry : additionalData.asMap().entrySet()) {
      augmentedData.putSingle(entry.getKey(), entry.getValue());
    }
    return augmentedData;
  }


  // -----------------------------------------------------------------------------------------------
  // Print directives.
  // See BasicDirectivesModule for details of how the escaping directives end up invoking these
  // concrete java implementations.


  public static String $$escapeHtml(SoyData value) {
    return Sanitizers.escapeHtml(value);
  }


  public static String $$escapeHtmlRcdata(SoyData value) {
    return Sanitizers.escapeHtmlRcdata(value);
  }


  public static String $$normalizeHtml(SoyData value) {
    return Sanitizers.normalizeHtml(value);
  }


  public static String $$normalizeHtmlNospace(SoyData value) {
    return Sanitizers.normalizeHtmlNospace(value);
  }


  public static String $$escapeHtmlAttribute(SoyData value) {
    return Sanitizers.escapeHtmlAttribute(value);
  }


  public static String $$escapeHtmlAttributeNospace(SoyData value) {
    return Sanitizers.escapeHtmlAttributeNospace(value);
  }


  public static String $$escapeJsString(SoyData value) {
    return Sanitizers.escapeJsString(value);
  }


  public static String $$escapeJsValue(SoyData value) {
    return Sanitizers.escapeJsValue(value);
  }


  public static String $$escapeJsRegex(SoyData value) {
    return Sanitizers.escapeJsRegex(value);
  }


  public static String $$escapeCssString(SoyData value) {
    return Sanitizers.escapeCssString(value);
  }


  public static String $$filterCssValue(SoyData value) {
    return Sanitizers.filterCssValue(value);
  }


  public static String $$escapeUri(SoyData value) {
    return Sanitizers.escapeUri(value);
  }


  public static String $$normalizeUri(SoyData value) {
    return Sanitizers.normalizeUri(value);
  }


  public static String $$filterNormalizeUri(SoyData value) {
    return Sanitizers.filterNormalizeUri(value);
  }


  public static String $$filterHtmlAttributes(SoyData value) {
    return Sanitizers.filterHtmlAttributes(value);
  }


  public static String $$filterHtmlElementName(SoyData value) {
    return Sanitizers.filterHtmlElementName(value);
  }


  public static String $$changeNewlineToBr(String value) {
    return NEWLINE_PATTERN.matcher(value).replaceAll("<br>");
  }


  public static String $$insertWordBreaks(String value, int maxCharsBetweenWordBreaks) {

    StringBuilder result = new StringBuilder();

    // These variables keep track of important state while looping through the string below.
    boolean isInTag = false;  // whether we're inside an HTML tag
    boolean isMaybeInEntity = false;  // whether we might be inside an HTML entity
    int numCharsWithoutBreak = 0;  // number of characters since the last word break

    for (int codePoint, i = 0; i < value.length(); i += Character.charCount(codePoint)) {
      codePoint = value.codePointAt(i);

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

      } else {  // !isInTag && !isInEntity
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

    return result.toString();
  }


  public static String $$truncate(String str, int maxLen, boolean doAddEllipsis) {

    if (str.length() <= maxLen) {
      return str;  // no need to truncate
    }

    // If doAddEllipsis, either reduce maxLen to compensate, or else if maxLen is too small, just
    // turn off doAddEllipsis.
    if (doAddEllipsis) {
      if (maxLen > 3) {
        maxLen -= 3;
      } else {
        doAddEllipsis = false;
      }
    }

    // Make sure truncating at maxLen doesn't cut up a unicode surrogate pair.
    if (Character.isHighSurrogate(str.charAt(maxLen - 1)) &&
        Character.isLowSurrogate(str.charAt(maxLen))) {
      maxLen -= 1;
    }

    // Truncate.
    str = str.substring(0, maxLen);

    // Add ellipsis.
    if (doAddEllipsis) {
      str += "...";
    }

    return str;
  }


  // -----------------------------------------------------------------------------------------------
  // Operators.


  public static NumberData $$negative(NumberData operand) {

    if (operand instanceof IntegerData) {
      return IntegerData.forValue( - operand.integerValue() );
    } else {
      return FloatData.forValue( - operand.floatValue() );
    }
  }


  public static NumberData $$times(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(operand0.integerValue() * operand1.integerValue());
    } else {
      return FloatData.forValue(operand0.numberValue() * operand1.numberValue());
    }
  }


  public static SoyData $$plus(SoyData operand0, SoyData operand1) {

    if (operand0 instanceof NumberData && operand1 instanceof NumberData) {
      if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
        return IntegerData.forValue(operand0.integerValue() + operand1.integerValue());
      } else {
        return FloatData.forValue(operand0.numberValue() + operand1.numberValue());
      }
    } else {
      // String concatenation. Note we're calling toString() instead of stringValue() in case one
      // of the operands needs to be coerced to a string.
      return StringData.forValue(operand0.toString() + operand1.toString());
    }
  }


  public static NumberData $$minus(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return IntegerData.forValue(operand0.integerValue() - operand1.integerValue());
    } else {
      return FloatData.forValue(operand0.numberValue() - operand1.numberValue());
    }
  }


  public static BooleanData $$lessThan(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return BooleanData.forValue(operand0.integerValue() < operand1.integerValue());
    } else {
      return BooleanData.forValue(operand0.numberValue() < operand1.numberValue());
    }
  }


  public static BooleanData $$greaterThan(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return BooleanData.forValue(operand0.integerValue() > operand1.integerValue());
    } else {
      return BooleanData.forValue(operand0.numberValue() > operand1.numberValue());
    }
  }


  public static BooleanData $$lessThanOrEqual(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return BooleanData.forValue(operand0.integerValue() <= operand1.integerValue());
    } else {
      return BooleanData.forValue(operand0.numberValue() <= operand1.numberValue());
    }
  }


  public static BooleanData $$greaterThanOrEqual(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return BooleanData.forValue(operand0.integerValue() >= operand1.integerValue());
    } else {
      return BooleanData.forValue(operand0.numberValue() >= operand1.numberValue());
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Functions.


  public static BooleanData $$isNonnull(SoyData value) {
    return BooleanData.forValue(! (value instanceof UndefinedData || value instanceof NullData));
  }


  public static NumberData $$round(
      NumberData valueData, @Nullable IntegerData numDigitsAfterPtData) {

    int numDigitsAfterPt = (numDigitsAfterPtData != null) ?
                           numDigitsAfterPtData.integerValue() : 0 /* default */;

    if (numDigitsAfterPt == 0) {
      if (valueData instanceof IntegerData) {
        return valueData;
      } else {
        return IntegerData.forValue((int) Math.round(valueData.numberValue()));
      }
    } else if (numDigitsAfterPt > 0) {
      double value = valueData.numberValue();
      double shift = Math.pow(10, numDigitsAfterPt);
      return FloatData.forValue(Math.round(value * shift) / shift);
    } else {
      double value = valueData.numberValue();
      double shift = Math.pow(10, -numDigitsAfterPt);
      return IntegerData.forValue((int) (Math.round(value / shift) * shift));
    }
  }


  public static NumberData $$min(NumberData arg0, NumberData arg1) {

    if (arg0 instanceof IntegerData && arg1 instanceof IntegerData) {
      return IntegerData.forValue(Math.min(arg0.integerValue(), arg1.integerValue()));
    } else {
      return FloatData.forValue(Math.min(arg0.numberValue(), arg1.numberValue()));
    }
  }


  public static NumberData $$max(NumberData arg0, NumberData arg1) {

    if (arg0 instanceof IntegerData && arg1 instanceof IntegerData) {
      return IntegerData.forValue(Math.max(arg0.integerValue(), arg1.integerValue()));
    } else {
      return FloatData.forValue(Math.max(arg0.numberValue(), arg1.numberValue()));
    }
  }
}
