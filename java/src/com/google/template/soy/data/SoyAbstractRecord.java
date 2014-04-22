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

import javax.annotation.ParametersAreNonnullByDefault;


/**
 * Abstract implementation of SoyRecord.
 *
 * <p>Important: Until this API is more stable and this note is removed, users must not define
 * classes that extend this class.
 *
 */
@ParametersAreNonnullByDefault
public abstract class SoyAbstractRecord extends SoyAbstractValue implements SoyRecord {


  @Override public SoyValue getField(String name) {
    SoyValueProvider valueProvider = getFieldProvider(name);
    return (valueProvider != null) ? valueProvider.resolve() : null;
  }

}
