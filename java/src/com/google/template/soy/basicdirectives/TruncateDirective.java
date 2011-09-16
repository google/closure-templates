/*
 * Copyright 2011 Google Inc.
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
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.javasrc.restricted.JavaCodeUtils;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.tofu.restricted.SoyAbstractTofuPrintDirective;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * A directive that truncates a string to a maximum length if it is too long, optionally adding
 * ellipsis. 
 *
 * @author Kai Huang
 */
@Singleton
public class TruncateDirective extends SoyAbstractTofuPrintDirective
    implements SoyJsSrcPrintDirective, SoyJavaSrcPrintDirective {


  @Inject
  public TruncateDirective() {}


  @Override public String getName() {
    return "|truncate";
  }


  @Override public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1, 2);
  }


  @Override public boolean shouldCancelAutoescape() {
    return false;
  }


  @Override public String apply(SoyData value, List<SoyData> args) {

    int maxLen;
    try {
      maxLen = args.get(0).integerValue();
    } catch (SoyDataException sde) {
      throw new IllegalArgumentException(
          "Could not parse first parameter of '|truncate' as integer (value was \"" +
              args.get(0).stringValue() + "\").");
    }

    String str = value.toString();
    if (str.length() <= maxLen) {
      return str;  // no need to truncate
    }

    boolean doAddEllipsis;
    if (args.size() == 2) {
      try {
        doAddEllipsis = args.get(1).booleanValue();
      } catch (SoyDataException sde) {
        throw new IllegalArgumentException(
            "Could not parse second parameter of '|truncate' as boolean.");
      }
    } else {
      doAddEllipsis = true;  // default to true
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


  @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {

    String maxLenExprText = args.get(0).getText();
    String doAddEllipsisExprText = (args.size() == 2) ? args.get(1).getText() : "true" /*default*/;

    return new JsExpr(
        "soy.$$truncate(" +
            value.getText() + ", " + maxLenExprText + ", " + doAddEllipsisExprText + ")",
        Integer.MAX_VALUE);
  }


  @Override public JavaExpr applyForJavaSrc(JavaExpr value, List<JavaExpr> args) {

    String valueExprText = JavaCodeUtils.genCoerceString(value);
    String maxLenExprText = JavaCodeUtils.genIntegerValue(args.get(0));
    String doAddEllipsisExprText =
        (args.size() == 2) ? JavaCodeUtils.genBooleanValue(args.get(1)) : "true" /*default*/;

    return new JavaExpr(
        JavaCodeUtils.genFunctionCall(
            JavaCodeUtils.UTILS_LIB + ".$$truncate",
            valueExprText, maxLenExprText, doAddEllipsisExprText),
        String.class, Integer.MAX_VALUE);
  }

}
