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

package com.google.template.soy.jbcsrc;

import com.google.auto.value.AutoValue;

import org.objectweb.asm.Type;

/** 
 * A wrapper around {@link Type} that provides some additional methods and accessor caching. 
 * 
 * <p>Also, unlike {@link Type} this only represents the name of a class, it doesn't attempt to 
 * represent primitive or array types or method descriptors.
 */
@AutoValue abstract class TypeInfo {
  static TypeInfo create(Class<?> clazz) {
    Type type = Type.getType(clazz);
    return new AutoValue_TypeInfo(clazz.getName(), type.getInternalName(), type);
  }

  static TypeInfo create(String className) {
    // translates a java class name (foo.bar.Baz$Quux) to a java 'internal' name and then translates
    // that to a Type object
    Type type = Type.getObjectType(className.replace('.', '/'));
    return new AutoValue_TypeInfo(
        className,
        type.getInternalName(),
        type);
  }

  abstract String className();
  abstract String internalName();
  abstract Type type();
}
