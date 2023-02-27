/*
 * Copyright 2012 Google Inc.
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

import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPureFunction;

/**
 * A function that determines if a given string contains another given string.
 *
 * <p>Duplicate functionality as StrContainsFunction but given includes name to allow for automatic
 * translation of TSX includes method. This method should only be used by TSX and not be hand
 * written.
 */
@SoyMethodSignature(
    name = "includes",
    baseType = "string",
    value = @Signature(parameterTypes = "string", returnType = "bool"))
@SoyPureFunction
public final class StrIncludesFunction extends StrContainsBaseFunction {}
