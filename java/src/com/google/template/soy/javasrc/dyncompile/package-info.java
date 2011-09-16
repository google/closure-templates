/*
 * Copyright 2010 Google Inc.
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
 * A dynamic Soy -> Java compiler similar to the dynamic compiler for JSP.
 *
 * <p>
 * This depends on the
 * <a href="http://download.oracle.com/javase/6/docs/api/javax/tools/package-summary.html"
 *  >Java tools</a>
 * interface to <code>javac</code> that became standard in JDK 6.
 *
 * <p>
 * It also requires that the Soy Java runtime support classes under
 * {@link com.google.template.soy.data} and {@link com.google.template.soy.javasrc.codedeps}
 * are on the current classpath.
 * It may also fail when those classes are split amongst multiple JARs or directories.
 *
 * <p>
 * This may interact badly when those support classes are found via exotic classloaders and
 * {@code URLClassLoader}s that fetch those classes remotely.
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.google.template.soy.javasrc.dyncompile;
