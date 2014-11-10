/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.data;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for SoyValueHelper.
 *
 */
public class SoyValueHelperTest extends TestCase {


  private static final SoyValueHelper UNCUSTOMIZED_HELPER = SoyValueHelper.UNCUSTOMIZED_INSTANCE;


  public void testDictCreation() {

    SoyEasyDict dict1 = UNCUSTOMIZED_HELPER.newEasyDict();
    assertEquals(0, dict1.getItemCnt());
    dict1.set("boo", 111);
    dict1.set("foo.goo", 222);

    SoyEasyDict dict2 = UNCUSTOMIZED_HELPER.newEasyDict("foo", 3.14, "too", true);
    assertTrue(dict2.has("foo"));
    assertEquals(3.14, dict2.get("foo").floatValue());
    assertEquals(true, dict2.get("too").booleanValue());

    SoyEasyDict dict3 = UNCUSTOMIZED_HELPER.newEasyDictFromDict(dict1);
    assertEquals(111, dict3.get("boo").integerValue());
    assertEquals(222, ((SoyEasyDict) dict3.get("foo")).get("goo").integerValue());

    SoyEasyDict dict4 =
        UNCUSTOMIZED_HELPER.newEasyDictFromJavaStringMap(ImmutableMap.of("foo", 3.14, "too", true));
    assertTrue(dict4.has("foo"));
    assertEquals(3.14, dict4.get("foo").floatValue());
    assertEquals(true, dict4.get("too").booleanValue());
  }


  public void testListCreation() {

    SoyEasyList list1 = UNCUSTOMIZED_HELPER.newEasyList();
    assertEquals(0, list1.length());
    list1.add("blah");
    list1.add(111);

    SoyEasyList list2 = UNCUSTOMIZED_HELPER.newEasyList(3.14, true);
    assertEquals(3.14, list2.get(0).floatValue());
    assertEquals(true, list2.get(1).booleanValue());

    SoyEasyList list3 = UNCUSTOMIZED_HELPER.newEasyListFromList(list1);
    assertEquals("blah", list3.get(0).stringValue());
    assertEquals(111, list3.get(1).integerValue());

    SoyEasyList list4 =
        UNCUSTOMIZED_HELPER.newEasyListFromJavaIterable(ImmutableList.of(3.14, true));
    assertEquals(3.14, list4.get(0).floatValue());
    assertEquals(true, list4.get(1).booleanValue());
  }


  public void testConvertBasic() {

    assertEquals(NullData.INSTANCE, UNCUSTOMIZED_HELPER.convert(null));
    assertEquals(
        "boo", UNCUSTOMIZED_HELPER.convert(StringData.forValue("boo")).resolve().stringValue());
    assertEquals("boo", UNCUSTOMIZED_HELPER.convert("boo").resolve().stringValue());
    assertEquals(true, UNCUSTOMIZED_HELPER.convert(true).resolve().booleanValue());
    assertEquals(8, UNCUSTOMIZED_HELPER.convert(8).resolve().integerValue());
    assertEquals(
        "foo",
        ((SoyDict) UNCUSTOMIZED_HELPER.convert(ImmutableMap.of("boo", "foo"))).getField("boo")
            .stringValue());
    assertEquals(
        "goo",
        ((SoyList) UNCUSTOMIZED_HELPER.convert(ImmutableList.of("goo"))).get(0).stringValue());
    assertEquals(
        "hoo",
        ((SoyList) UNCUSTOMIZED_HELPER.convert(ImmutableSet.of("hoo"))).get(0).stringValue());
    assertEquals(3.14, UNCUSTOMIZED_HELPER.convert(3.14).resolve().floatValue());
    assertEquals(3.14F, (float) UNCUSTOMIZED_HELPER.convert(3.14F).resolve().floatValue());
  }


  public void testConvertFuture() {
    assertTrue(
        UNCUSTOMIZED_HELPER.convert(Futures.immediateFuture("future"))
            instanceof SoyFutureValueProvider);
    assertEquals(
        "soy",
        UNCUSTOMIZED_HELPER.convert(Futures.immediateFuture("soy")).resolve().stringValue());
  }


  public void testConvertSoyGlobalsValue() {
    assertEquals("foo", UNCUSTOMIZED_HELPER.convert(new SoyGlobalsValue() {
          @Override
          public Object getSoyGlobalValue() {
            return "foo";
          }
        }).resolve().stringValue());
  }


  public void testConvertWithCustomConverter() {

    Module guiceModuleWithSoyCustomValueConverters =
        new Module() {
          @Override public void configure(Binder binder) {
            // Do nothing.
          }
          @Provides List<SoyCustomValueConverter> provideSoyCustomValueConverters() {
            return ImmutableList.<SoyCustomValueConverter>of(
                // This converter converts non-primitive arrays to SoyList.
                new SoyCustomValueConverter() {
                  @Override public SoyValueProvider convert(
                      SoyValueConverter valueConverter, Object obj) {
                    if (obj instanceof Object[]) {
                      return valueConverter.convert(Arrays.asList((Object[]) obj));
                    } else {
                      return null;
                    }
                  }
                });
          }
        };

    Injector injector = Guice.createInjector(guiceModuleWithSoyCustomValueConverters);
    SoyValueHelper valueHelper = injector.getInstance(SoyValueHelper.class);

    // Test convert non-primitive arrays.
    assertEquals(
        "foo", ((SoyList) valueHelper.convert(new String[] {"boo", "foo"})).get(1).stringValue());
    assertEquals(
        5, ((SoyList) valueHelper.convert(new Integer[] {1, 3, 5, 7})).get(2).integerValue());

    // Test convert primitive arrays (expected to error).
    try {
      valueHelper.convert(new int[] {1, 3, 5, 7});
      fail();
    } catch (SoyDataException expected) {}

    // Test that basic conversions still work.
    assertEquals(NullData.INSTANCE, valueHelper.convert(null));
    assertEquals("boo", valueHelper.convert("boo").resolve().stringValue());
    assertEquals(3.14, valueHelper.convert(3.14).resolve().floatValue());
  }

}
