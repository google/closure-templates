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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import org.objectweb.asm.Type;

/**
 * A wrapper around {@link Type} that provides some additional methods and accessor caching.
 *
 * <p>Also, unlike {@link Type} this only represents the name of a class, it doesn't attempt to
 * represent primitive or array types or method descriptors.
 */
@AutoValue
public abstract class TypeInfo {
  public static TypeInfo create(Class<?> clazz) {
    Type type = Type.getType(clazz);
    return new AutoValue_TypeInfo(
        clazz.getName(), clazz.getSimpleName(), type.getInternalName(), type, clazz.isInterface());
  }

  public static TypeInfo create(String className, boolean isInterface) {
    // Translates a java class name (foo.bar.Baz$Quux) to a java 'internal' name and then translates
    // that to a Type object
    Type type = Type.getObjectType(className.replace('.', '/'));
    // This logic is specified by Class.getSimpleName()
    String packageLessName = className.substring(className.lastIndexOf('.') + 1);
    String simpleName = packageLessName.substring(packageLessName.lastIndexOf('$') + 1);
    return new AutoValue_TypeInfo(className, simpleName, type.getInternalName(), type, isInterface);
  }

  public static TypeInfo createClass(String className) {
    return create(className, false);
  }

  public static TypeInfo createInterface(String className) {
    return create(className, true);
  }

  public abstract String className();

  public abstract String simpleName();

  public abstract String internalName();

  public abstract Type type();

  public abstract boolean isInterface();

  /** Returns a new {@link TypeInfo} for an inner class of this class (not an inner interface). */
  public final TypeInfo innerClass(String simpleName) {
    checkArgument(
        simpleName.indexOf('$') == -1, "Simple names shouldn't contain '$': %s", simpleName);
    String className = className() + '$' + simpleName;
    String internalName = internalName() + '$' + simpleName;
    Type type = Type.getObjectType(internalName);
    return new AutoValue_TypeInfo(className, simpleName, internalName, type, false);
  }
}
