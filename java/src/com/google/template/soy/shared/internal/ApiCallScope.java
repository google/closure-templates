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

package com.google.template.soy.shared.internal;

import com.google.inject.ScopeAnnotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;


/**
 * Guice scope for a Soy API method call. Only used in some API methods where it would be useful
 * to be able to inject API call parameters (such as the message bundle) directly into lower-level
 * objects (such as passes) created during the handling of the API call.
 *
 * <p> Important: This may only be used in implementing plugins (e.g. functions, directives).
 */
@ScopeAnnotation
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface ApiCallScope {}
