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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a class annotated with this annotation contains constants that should be exported
 * as Soy Globals.
 *
 * <p>There are several restrictions imposed on such a class with constants:
 *
 * <ol>
 *   <li>a class cannot be an interface or an annotation;
 *   <li>inheritance of such classes is prohibited and, thus, classes must be declared with the
 *       "final" declared class;
 *   <li>constructors or methods are not allowed;
 *   <li>only constant fields are allowed;
 *   <li>only primitive and string types are allowed for constant values.
 * </ol>
 *
 * <p>The constants are exported in the namespace of the implementing class.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SoyGlobalsStatics {}
