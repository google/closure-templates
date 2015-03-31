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
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;

import java.io.IOException;

/**
 * Runtime utilities uniquely for the {@code jbcsrc} backend.
 */
public final class Runtime {
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
