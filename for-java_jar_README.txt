// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


Contents:

+ soy.jar
    Jar containing all classes and dependencies. Usually downloaded for Java
    usage. However, note that the programmatic API supports all functionality.
    Besides Java usage, the programmatic API can also be used for compiling Soy
    files to JS, message extraction, etc.

+ SoyParseInfoGenerator.jar
    Executable jar that generates Java files containing info/constants parsed
    from template files.

+ separate-jars
    Directory containing separate jars for Closure Templates classes and for
    dependencies. If your project depends on some of the same libraries that
    Closure Templates depend on, e.g. Guava or Guice, then then you may need to
    mix-and-match separate jars for Java usage of Closure Templates (as opposed
    to using the standalone soy.jar). The Closure Templates classes are
    packaged into soy-excluding-deps.jar, while the dependencies are in the
    subdirectory 'lib'. All dependencies are open source. Also included are
    javadoc (lite version) and a zip file of the sources.


Instructions:

+ A simple Hello World for Java:
    http://code.google.com/closure/templates/docs/helloworld_java.html

+ Complete documentation:
    http://code.google.com/closure/templates/

+ Closure Templates project on Google Code:
    http://code.google.com/p/closure-templates/


Notes:

+ Closure Templates requires Java 6 or higher:
    http://www.java.com/
