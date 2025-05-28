/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.basicfunctions;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.plugin.restricted.SoySourceFunction;

/** Lists all functions in this package. */
public class BasicFunctions {

  private BasicFunctions() {}

  public static final SoySourceFunction BUILD_ATTR_FUNCTION = new BuildAttrFunction();
  public static final SoySourceFunction BUILD_ATTR_VALUE_FUNCTION = new BuildAttrValueFunction();
  public static final SoySourceFunction BUILD_CLASS_VALUE_FUNCTION = new BuildClassValueFunction();
  public static final SoySourceFunction BUILD_STYLE_VALUE_FUNCTION = new BuildStyleValueFunction();

  public static ImmutableList<SoySourceFunction> functions() {
    return ImmutableList.of(
        // go/keep-sorted start
        BUILD_ATTR_FUNCTION,
        BUILD_ATTR_VALUE_FUNCTION,
        BUILD_CLASS_VALUE_FUNCTION,
        BUILD_STYLE_VALUE_FUNCTION,
        new AbsFunction(),
        new CeilingFunction(),
        new ConcatListsFunction(),
        new ConcatMapsMethod(),
        new FloorFunction(),
        new GbigintListSortMethod(),
        new HtmlToTextFunction(),
        new IsFiniteFunction(),
        new JoinFunction(),
        new KeysFunction(),
        new LegacyObjectMapToMapFunction(),
        new LengthFunction(),
        new ListFlatMethod(),
        new ListIncludesFunction(),
        new ListIndexOfFunction(),
        new ListReverseMethod(),
        new ListSliceMethod(),
        new ListUniqMethod(),
        new MapEntriesMethod(),
        new MapKeysFunction(),
        new MapLengthMethod(),
        new MapToLegacyObjectMapFunction(),
        new MapValuesMethod(),
        new MaxFunction(),
        new MinFunction(),
        new NumberListSortMethod(),
        new ParseFloatFunction(),
        new ParseIntFunction(),
        new PowFunction(),
        new ProtoEqualsMethod(),
        new ProtoIsDefaultMethod(),
        new RandomIntFunction(),
        new RangeFunction(),
        new RoundFunction(),
        new SetHasFunction(),
        new SetSizeField(),
        new SortMethod(),
        new SqrtFunction(),
        new StrEndsWithMethod(),
        new StrIncludesFunction(),
        new StrIndexOfFunction(),
        new StrLenFunction(),
        new StrReplaceAllMethod(),
        new StrSmsUriToUriFunction(),
        new StrSplitMethod(),
        new StrStartsWithMethod(),
        new StrSubFunction(),
        new StrToAsciiLowerCaseFunction(),
        new StrToAsciiUpperCaseFunction(),
        new StrTrimMethod(),
        new StringListSortMethod(),
        new ThrowFunction(),
        new VeHasSameIdMethod());
    // go/keep-sorted end
  }
}
