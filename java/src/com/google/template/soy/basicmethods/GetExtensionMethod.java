/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.basicmethods;

import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;

/**
 * Soy method that gets an extended field of a proto using the fully qualified name of the field.
 * The implementation of the 'getExtension' method requires Soy compiler internals, so it is
 * implemented elsewhere. Because of this, the base, parameter, and return types are also checked
 * independently from other methods.
 */
@SoyMethodSignature(
    name = "getExtension",
    // Proto type.
    baseType = "?",
    value =
        @Signature(
            // Fully qualified extension name that exists on the base proto.
            parameterTypes = "?",
            // Type of the extension field.
            returnType = "?"))
public final class GetExtensionMethod implements SoySourceFunction {}
