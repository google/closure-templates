# Compiling Templates


## Invoking the compiler directly


For open source users, we release maven artifacts that are executable jars, and
users can directly invoke these binaries to compile the templates.

### Java {#java}

<!--TODO(lukes): rewrite the docs on command line flags to talk about common flags vs backend specific flags -->

Soy has 2 Java backends: `Tofu` which is an interpreter and `SoySauce` which is
a compiler.

For `Tofu` you need to use the `SoyFileSet.compileToTofu()` method at runtime,
`SoySauce` also supports a similar mode via the `SoyFileSet.compileTemplates()`
though this is discouraged since the best way is to use the
`SoyToJbcSrcCompiler` binary.

In the command line arguments to the `SoyToJbcSrcCompiler`, you must include the
paths to all of the Soy files that you want to be compiled together as one
bundle. The binary also accepts a number of command-line flags, some of which
are required.

To see all of the available command-line flags, run the binary with `--help`.

The table below lists the most important command-line flags:

| Flag                       | Usage                                           |
| -------------------------- | ----------------------------------------------- |
| `--srcs`                   | **[Required]** The list of source Soy files.    |
| `--deps`                   | The list of dependency Soy files (if            |
:                            : applicable). The compiler needs deps for        :
:                            : analysis/checking, but does not generate code   :
:                            : for dep files.                                  :
| `--indirecteDeps`          | The list of indirected dependency Soy files (if |
:                            : applicable). The compiler needs indirect deps   :
:                            : for analysis/checking, but does not generate    :
:                            : code for dep files.                             :
| `--output`                 | **[Required]** The name of the Jar file to      |
:                            : write                                           :
| `--outputSrcJar`           | The name of a Jar file containing source files  |
:                            : to write. This places the source files into a   :
:                            : directory structure that will enable some IDEs  :
:                            : like eclipse to do basic line level debugging   :
:                            : of your Soy template source files               :
| `--compileTimeGlobalsFile` | The path of the file that contains the mappings |
:                            : for global names to be bound at compile time.   :
:                            : Please see the [Globals](#globals) section in   :
:                            : this chapter for more information.              :
| `--pluginModules`          | See the section on [function or print directive |
:                            : plugins](./plugins) in Plugins.                 :

Here's an example of how the compiler might be invoked from the command line:

```shell
$ java -jar SoyToJbcSrcCompiler.jar \
           --output=soy.jar \
           --srcs=simple.soy,features.soy
```

This sample usage generates a `jar` file including the generated code for the
two source files. Place this jar on the classpath of your application to use the
generated code at runtime.

### JavaScript

JavaScript usage is supported by the Soy compiler's JavaScript Source backend.
You have two options for invoking the compiler to generate JavaScript source:
you can build and run the executable binary (more common) or you can call the
compiler from Java. This section discusses how to use both options.

#### How to build and run the executable binary


In the command line arguments to the `SoyToJsSrcCompiler`, you must include the
paths to all of the Soy files that you want to be compiled together as one
bundle. The binary also accepts a number of command-line flags, some of which
are required.

To see all of the available command-line flags, run the binary with `--help`.

The table below lists the most important command-line flags:

Flag                             | Usage
-------------------------------- | -----
`--srcs`                         | **[Required]** The list of source Soy files.
`--deps`                         | The list of dependency Soy files (if applicable). The compiler needs deps for analysis/checking, but does not generate code for dep files.
`--allowExternalCalls`           | Whether to allow external calls. New projects should set this to false, and existing projects should remove existing external calls and then set this to false. External calls are calls to templates that aren't defined in the same bundle of `.soy` files being compiled together. Their existence prevents the compiler from being able to accurately analyze your code. Currently defaults to true for backward compatibility.
`--locales`                      | **[Required for generating localized JavaScript]** The list of locales for which to generate localized JS. Soy generates one output JS file for each combination of input Soy file and locale.
`--messageFilePathFormat`        | **[Required for generating localized JavaScript]** A format string that specifies how to build the path to each message file. The format string can include literal characters as well as the placeholders `{LOCALE}`, and `{LOCALE_LOWER_CASE}`. Note `{LOCALE_LOWER_CASE}` also turns dashes into underscores, e.g. `pt-BR` becomes `pt_br`. For most projects, this format string is of the form `/home/build/xtb/<tc_project_name>/{LOCALE}.xtb`.
`--outputPathFormat`             | **[Required]** A format string that specifies how to build the path to each output file. The format string can include literal characters as well as the placeholders `{INPUT_DIRECTORY}`, `{INPUT_FILE_NAME}`, `{INPUT_FILE_NAME_NO_EXT}`, `{LOCALE}`, `{LOCALE_LOWER_CASE}`. If you are not generating localized JavaScript, then Soy outputs one JavaScript file (encoded in UTF-8) for each input Soy file. If you are generating localized JavaScript, then Soy outputs one JavaScript file for each combination of input Soy file and locale.
`--shouldGenerateGoogMsgDefs`    | Set this flag if you want `goog.getMsg` definitions to be generated for all `msg` blocks. For more information, see the section on [Letting JS Compiler Handle Translation](localization#closurecompiler) in the Translation chapter.
`--bidiGlobalDir`                | **[Required if and only if generating `goog.getMsg` definitions]** For more information, see the section on [Letting JS Compiler Handle Translation](localization#closurecompiler) in the Translation chapter.
`--useGoogIsRtlForBidiGlobalDir` | [Only applicable if both `shouldGenerateGoogMsgDefs` is true] Set this flag to determine the bidi global direction at template render time by evaluating `goog.i18n.bidi.IS_RTL`. Do not combine with `bidiGlobalDir`.
`--compileTimeGlobalsFile`       | The path of the file that contains the mappings for global names to be bound at compile time. Please see the [Globals](#globals) section in this chapter for more information.
`--googMsgsAreExternal`          | Use this flag if you want your project to use Soy to extract messages (e.g. with `SoyMsgExtractor`) but use JS Compiler to insert the translated messages. Using Soy to extract messages is recommended. If you use Soy from both Java and JavaScript, then it's essential for correctness.
`--pluginModules`                | See the section on [function or print directive plugins](./plugins) in Plugins.

Here's an example of how the compiler might be invoked from the command line:

```shell
$ java -jar SoyToJsSrcCompiler.jar \
           --locales=en,xx-YY,xx-ZZ \
           --messageFilePathFormat=/home/build/SoyExamples/{LOCALE}.xtb \
           --outputPathFormat='{INPUT_FILE_NAME}__{LOCALE_LOWER_CASE}.js' \
           --srcs=template/soy/examples/simple.soy,template/soy/examples/features.soy
```

This sample usage generates 6 total JS files in your directory — one for each
combination of input `.soy` file (`simple.soy`, `features.soy`) and locale
(`en`, `xx-YY`, `xx-ZZ`).

