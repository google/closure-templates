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

package com.google.template.soy.javasrc.dyncompile;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.javasrc.SoyTemplateRuntime;
import com.google.template.soy.javasrc.SoyTemplateRuntimes;
import com.google.template.soy.shared.SoyCssRenamingMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.Locale;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;


/**
 * Dynamically compiles Soy templates to Java classes that are exposed as
 * {@link SoyTemplateRuntimes}.
 *
 */
public final class SoyToJavaDynamicCompiler {

  /** Name of a Java package for compiled Soy templates. */
  static final String PACKAGE_FOR_COMPILED_SOY = "com.google.template.soy.javasrc.dyncompiled";

  /** A logger that receives messages about compilation problems. */
  private static final Logger LOGGER = Logger.getLogger(SoyToJavaDynamicCompiler.class.getName());


  /**
   * @param bundleName The class name used for the bundle of Soy templates.  This will show up in
   *     Java stack traces.
   * @param javaClassBody Java source code as from
   *     {@link com.google.template.soy.SoyFileSet#compileToJavaSrc}.
   */
  public static ImmutableMap<String, SoyTemplateRuntime> compile(
      String bundleName, String javaClassBody) {
    if (!BaseUtils.isDottedIdentifier(bundleName)) {
      throw new IllegalArgumentException(
          "Bundle name should be a dotted identifier, not " + bundleName);
    }

    // The javaClassBody contains one static method per template and the name is the full template
    // name but with dots ('.') replaces with dollar signs ('$').
    // This class relies on that convention, and the parameter convention.
    String javaSourceCode = Joiner.on('\n').join(
        "package " + PACKAGE_FOR_COMPILED_SOY + ";",

        "public final class " + bundleName + " {",
        "  private final " + SoyMapData.class.getName() + " $$ijData;",
        "  private final " + SoyCssRenamingMap.class.getName() + " $$cssRenamingMap;",

        // Receive the injected data and CSS renaming scheme from the SoyTemplateRuntime instance.
        "  public " + bundleName + "(",
        "      " + SoyMapData.class.getName() + " ijData,",
        "      " + SoyCssRenamingMap.class.getName() + " cssRenamingMap) {",
        "    this.$$ijData = ijData;",
        "    this.$$cssRenamingMap = cssRenamingMap;",
        "  }",

        // GenJavaExprsVisitor generates code that uses this method to handle CSS nodes,
        "  private String $$renameCss(String selectorText) {",
        "    return $$cssRenamingMap.get(selectorText);",
        "  }",

        // TranslateToJavaExprVisitor generates code that uses this method to fetch injected data,
        "  private " + SoyData.class.getName() + " $$getIjData(String key) {",
        "    return $$ijData.get(key);",
        "  }",

          javaClassBody,

        "}");


    // Pipe compiler problems to this class's logger.
    DiagnosticListener<JavaFileObject> diagnosticListener =
        new DiagnosticListener<JavaFileObject>() {

      @Override
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        StringBuilder message = new StringBuilder();
        @Nullable JavaFileObject src = diagnostic.getSource();
        if (src != null) {
          // TODO: Map the line and character back to the source inputs.
          message.append(src.getName()).append(':').append(diagnostic.getLineNumber()).append('+')
              .append(diagnostic.getColumnNumber()).append(": ");
        }
        message.append(diagnostic.getMessage(Locale.getDefault()));
        switch (diagnostic.getKind()) {
          case ERROR:
          case OTHER:
            LOGGER.severe(message.toString());
            break;
          case MANDATORY_WARNING:
          case WARNING:
            LOGGER.warning(message.toString());
            break;
          default:  // OTHER and NOTE are not noteworthy.
            break;
        }
      }

    };


    // Set up a virtual file system for javac that reads the inputs from memory.
    // Temporary files, are slow, error-prone, and can be a vector for injecting malicious code
    // into running systems when an application uses a temporary directory that is accessible by
    // less privileged users than its process owner.
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    DynamicCompilerJavaFileManager fileManager = new DynamicCompilerJavaFileManager(
        javaCompiler.getStandardFileManager(
            diagnosticListener, Locale.getDefault(), Charsets.UTF_8));

    String className = PACKAGE_FOR_COMPILED_SOY + "." + bundleName;
    ReadableInMemoryJavaFileObject inputFile = new ReadableInMemoryJavaFileObject(
        "/src/" + className.replace('.', '/') + ".java", javaSourceCode);

    fileManager.addInput(inputFile);


    // Invoke the compiler.
    boolean wasSuccessfullyCompiled = javaCompiler.getTask(
        null, fileManager, diagnosticListener,
        ImmutableList.<String>of(),  // Compiler options
        ImmutableList.<String>of(),  // Classes,
        ImmutableList.of(inputFile))
        .call();

    if (wasSuccessfullyCompiled) {  // True indicates compilation succeeded.
      // Load the compiled classes (in binary bytecode form) into this JVM.
      SoyTemplateClassLoader templateClassLoader = new SoyTemplateClassLoader();

      for (WritableInMemoryJavaFileObject outputClass : fileManager.getOutputFiles()) {
        templateClassLoader.defineClassCompiledFromSoy(outputClass.getByteContent());
      }

      // Now that we've defined the class, we can generate a list of templates, and create our
      // template runtime objects.
      ImmutableMap.Builder<String, SoyTemplateRuntime> runtimes = ImmutableMap.builder();
      try {
        Class<?> compiledClass = templateClassLoader.loadClass(className);

        // Find a constructor that takes the injected data map and CSS renaming map.
        final Constructor<?> ctor;
        try {
          ctor = compiledClass.getDeclaredConstructor(SoyMapData.class, SoyCssRenamingMap.class);
        } catch (NoSuchMethodException ex) {
          throw new IllegalStateException(
              "Could not find ctor for generated java class " + compiledClass);
        }
        if (!Modifier.isPublic(ctor.getModifiers())) {
          throw new AssertionError(ctor.toString());
        }

        // Look for public methods that take a Map and a buffer.
        // For each of these, create a SoyTemplateRuntime.
        for (final Method method : compiledClass.getDeclaredMethods()) {
          int modifiers = method.getModifiers();
          // The Soy methods in the generated Java have the form
          //   public static void soy$namespace$with$dollars$instead$of$dots$TemplateName(
          //       SoyMapData, StringBuilder)
          // Look for these methods and reverse-engineer the Soy template name.
          if (Modifier.isPublic(modifiers)) {
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length == 2 && paramTypes[0].isAssignableFrom(SoyMapData.class) &&
                paramTypes[1].isAssignableFrom(StringBuilder.class)) {
              for (Class<?> thrownType : method.getExceptionTypes()) {
                if (!(Error.class.isAssignableFrom(thrownType) ||
                      RuntimeException.class.isAssignableFrom(thrownType))) {
                  // SoyTemplateRuntime needs to be updated to throw checked exceptions if the
                  // generated Java starts raising checked exceptions.
                  continue;
                }
              }

              String methodName = method.getName();
              final String templateName = methodName.replace('$', '.');

              runtimes.put(templateName, new AbstractSoyTemplateRuntime() {


                @Override
                protected void renderMain(
                    SoyMapData data, SoyMapData ijData, SoyCssRenamingMap cssRenamingMap,
                    StringBuilder out) {
                  try {
                    method.invoke(ctor.newInstance(ijData, cssRenamingMap), data, out);
                  } catch (InvocationTargetException ex) {
                    // Checked above that there are no checked exceptions.
                    Throwables.propagate(ex.getTargetException());
                  } catch (InstantiationException ex) {
                    // Checked above that there are no checked exceptions.
                    Throwables.propagate(ex);
                  } catch (IllegalAccessException ex) {
                    // Checked isPublic above, and the generated class is a public top-level class.
                    Throwables.propagate(ex);
                  }
                }


                @Override
                public String toString() {
                  return "[SoyTemplateRuntime " + templateName + "]";
                }

              });

            }
          }
        }

      } catch (ClassNotFoundException ex) {
        Throwables.propagate(ex);  // The class we successfully compiled should be present.
      }

      return runtimes.build();
    } else {
      // TODO: Choose an appropriate way to signal failure, and collect the javac output.
      System.err.println("Java code\n" + javaSourceCode + "\n");
      throw new RuntimeException();
    }
  }


  private SoyToJavaDynamicCompiler() {
    // Uninstantiable.  Consists only of static methods.
  }

}
