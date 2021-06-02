/*
 * Copyright 2021 Google Inc.
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

/**
 * A pass that modifies a SoyFile to add escape directives where necessary based on a contextual
 * examination of template {@code print} commands.
 *
 * <p>The main entry point for this package is {@link
 * com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper}.
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.google.template.soy.parsepasses.contextautoesc;
