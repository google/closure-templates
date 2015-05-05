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

package com.google.template.soy.jbcsrc.runtime;

import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.shared.restricted.SoyJavaFunction;

import java.io.IOException;
import java.util.List;

/**
 * Runtime utilities uniquely for the {@code jbcsrc} backend.
 */
public final class Runtime {
  public static final SoyValueProvider NULL_PROVIDER = new SoyValueProvider() {
    @Override public RenderResult status() {
      return RenderResult.done();
    }

    @Override public SoyValue resolve() {
      return null;
    }

    @Override public RenderResult renderAndResolve(AdvisingAppendable appendable, boolean isLast)
        throws IOException {
      appendable.append("null");
      return RenderResult.done();
    }

    @Override public String toString() {
      return "NULL_PROVIDER";
    }
  };

  public static AssertionError unexpectedStateError(int state) {
    return new AssertionError("Unexpected state requested: " + state);
  }

  public static boolean stringEqualsAsNumber(String expr, double number) {
    try {
      return Double.parseDouble(expr) == number;
    } catch (NumberFormatException nfe) {
      return false;
    }
  }

  /**
   * Helper function to translate NullData -> null when resolving a SoyValueProvider that may
   * have come from SoyValueProvider.
   */
  public static SoyValue resolveSoyValueProvider(SoyValueProvider provider) {
    SoyValue value = provider.resolve();
    if (value == NullData.INSTANCE) {
      return null;
    }
    return value;
  }

  /**
   * Helper function to translate null -> NullData when calling SoyJavaFunctions that may expect it.
   * 
   * <p>In the long run we should either fix ToFu (and all SoyJavaFunctions) to not use NullData or
   * we should introduce custom SoyFunction implementations for 
   * have come from SoyValueProvider.
   */
  public static SoyValue callSoyFunction(SoyJavaFunction function, List<SoyValue> args) {
    for (int i = 0; i < args.size(); i++) {
      if (args.get(i) == null) {
        args.set(i, NullData.INSTANCE);
      }
    }
    return function.computeForJava(args);
  }

  public static SoyValueProvider getSoyListItem(List<SoyValueProvider> list, long index) {
    int size = list.size();
    if (index < size & index >= 0) {
      SoyValueProvider soyValueProvider = list.get((int) index);
      return soyValueProvider == null ? NULL_PROVIDER : soyValueProvider;
    }
    return NULL_PROVIDER;
  }

  public static SoyValueProvider getSoyMapItem(SoyMap soyMap, SoyValue key) {
    SoyValueProvider soyValueProvider = soyMap.getItemProvider(key);
    return soyValueProvider == null ? NULL_PROVIDER : soyValueProvider;
  }

  public void checkRequiredParam(SoyRecord params, String paramName) {
    if (!params.hasField(paramName)) {
      throw new SoyDataException("required param '$" + paramName + "' is undefined");
    }
  }

  private static final AdvisingAppendable LOGGER = new AdvisingAppendable() {
    @Override public boolean softLimitReached() {
      return false;
    }
    
    @Override public AdvisingAppendable append(char c) throws IOException {
      System.out.append(c);
      return this;
    }
    
    @Override public AdvisingAppendable append(CharSequence csq, int start, int end) {
      System.out.append(csq, start, end);
      return this;
    }
    
    @Override
    public AdvisingAppendable append(CharSequence csq) {
      System.out.append(csq);
      return this;
    }
  };
  
  public static AdvisingAppendable logger() {
    return LOGGER;
  }
  
  public static boolean coerceToBoolean(double v) {
    // only NaN is != to itself so this ensures that v is not NaN and not == 0.0
    return v != 0.0 & v == v;
  }
}
