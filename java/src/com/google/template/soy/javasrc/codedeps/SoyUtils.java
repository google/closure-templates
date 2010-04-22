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
import com.google.template.soy.internal.base.CharEscapers;

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


  public static SoyData $$getData(CollectionData collectionData, String keyStr) {

    SoyData data = collectionData.get(keyStr);
    return (data != null) ? data : NullData.INSTANCE;
  }


  public static SoyMapData $$augmentData(SoyMapData baseData, SoyMapData additionalData) {

    AugmentedSoyMapData augmentedData = new AugmentedSoyMapData(baseData);
    for (Map.Entry<String, SoyData> entry : additionalData.asMap().entrySet()) {
      augmentedData.put(entry.getKey(), entry.getValue());
    }
    return augmentedData;
  }


  // -----------------------------------------------------------------------------------------------
  // Print directives.


  public static String $$escapeHtml(String value) {
    return CharEscapers.asciiHtmlEscaper().escape(value);
  }


  public static String $$escapeJs(String value) {
    return CharEscapers.javascriptEscaper().escape(value);
  }


  public static String $$escapeUri(String value) {
    return CharEscapers.uriEscaper(false).escape(value);
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
            // now we're inside and HTML tag.
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


  // -----------------------------------------------------------------------------------------------
  // Operators.


  public static NumberData $$negative(NumberData operand) {

    if (operand instanceof IntegerData) {
      return new IntegerData( - operand.integerValue() );
    } else {
      return new FloatData( - operand.floatValue() );
    }
  }


  public static NumberData $$times(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return new IntegerData(operand0.integerValue() * operand1.integerValue());
    } else {
      return new FloatData(operand0.numberValue() * operand1.numberValue());
    }
  }


  public static SoyData $$plus(SoyData operand0, SoyData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return new IntegerData(operand0.integerValue() + operand1.integerValue());
    } else if (operand0 instanceof StringData || operand1 instanceof StringData) {
      // String concatenation. Note we're calling toString() instead of stringValue() in case one
      // of the operands needs to be coerced to a string.
      return new StringData(operand0.toString() + operand1.toString());
    } else {
      return new FloatData(operand0.numberValue() + operand1.numberValue());
    }
  }


  public static NumberData $$minus(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return new IntegerData(operand0.integerValue() - operand1.integerValue());
    } else {
      return new FloatData(operand0.numberValue() - operand1.numberValue());
    }
  }


  public static BooleanData $$lessThan(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return new BooleanData(operand0.integerValue() < operand1.integerValue());
    } else {
      return new BooleanData(operand0.numberValue() < operand1.numberValue());
    }
  }


  public static BooleanData $$greaterThan(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return new BooleanData(operand0.integerValue() > operand1.integerValue());
    } else {
      return new BooleanData(operand0.numberValue() > operand1.numberValue());
    }
  }


  public static BooleanData $$lessThanOrEqual(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return new BooleanData(operand0.integerValue() <= operand1.integerValue());
    } else {
      return new BooleanData(operand0.numberValue() <= operand1.numberValue());
    }
  }


  public static BooleanData $$greaterThanOrEqual(NumberData operand0, NumberData operand1) {

    if (operand0 instanceof IntegerData && operand1 instanceof IntegerData) {
      return new BooleanData(operand0.integerValue() >= operand1.integerValue());
    } else {
      return new BooleanData(operand0.numberValue() >= operand1.numberValue());
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Functions.


  public static NumberData $$round(
      NumberData valueData, @Nullable IntegerData numDigitsAfterPtData) {

    int numDigitsAfterPt = (numDigitsAfterPtData != null) ?
                           numDigitsAfterPtData.integerValue() : 0 /* default */;

    if (numDigitsAfterPt == 0) {
      if (valueData instanceof IntegerData) {
        return (IntegerData) valueData;
      } else {
        return new IntegerData((int) Math.round(valueData.numberValue()));
      }
    } else if (numDigitsAfterPt > 0) {
      double value = valueData.numberValue();
      double shift = Math.pow(10, numDigitsAfterPt);
      return new FloatData(Math.round(value * shift) / shift);
    } else {
      double value = valueData.numberValue();
      double shift = Math.pow(10, -numDigitsAfterPt);
      return new IntegerData((int) (Math.round(value / shift) * shift));
    }
  }


  public static NumberData $$min(NumberData arg0, NumberData arg1) {

    if (arg0 instanceof IntegerData && arg1 instanceof IntegerData) {
      return new IntegerData(Math.min(arg0.integerValue(), arg1.integerValue()));
    } else {
      return new FloatData(Math.min(arg0.numberValue(), arg1.numberValue()));
    }
  }


  public static NumberData $$max(NumberData arg0, NumberData arg1) {

    if (arg0 instanceof IntegerData && arg1 instanceof IntegerData) {
      return new IntegerData(Math.max(arg0.integerValue(), arg1.integerValue()));
    } else {
      return new FloatData(Math.max(arg0.numberValue(), arg1.numberValue()));
    }
  }

}
