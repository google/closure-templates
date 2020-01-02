# Rendering a Template in Java

[TOC]


<!--#include file="includes/configuring-java-builders.md"-->


<!-- Open source docs. TODO(b/141946412): Remove this section once we support invocation builders in OS.  -->

### Prerequisite: Compiling the template in Java

The first step is to use the Soy compiler to compile a `.soy` file to a
corresponding Java `jar` file. There are multiple ways to do that.

See [Compiling Templates](dir.md) for more details.

### Creating a `SoySauce` object {#create-soysauce}

Then your application can depend on the generated `jar` files and you can render
templates by generating a `SoySauce` object via the `SoySauceBuilder` API. For
example,

```java
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauceBuilder;
...
SoySauce soySauce = new SoySauceBuilder().build();
```

When using SoySauceBuilder you may need to pass additional parameters:

*   if you are using legacy SoyPrintDirect of SoyFunction plugins, you will need
    to pass them to the `withDirectives` and `withFunctions` methods
*   if you are using SoyJavaSourceFunctions that require plugin instances at
    runtime, you will need to pass `Suppliers` for those instances to the
    `withPluginInstances`
*   if your compiled templates are not available on the standard runtime
    classpath of your JavaProgram, you may need to call `withClassLoader` to
    provide an alternate classloader.

### Getting the Renderer for a given template

You can then obtain the [`Renderer`][renderer-source-link] object for a given
template. Simply call `SoySauce.renderTemplate()` with the full template name.
For example, for the following Soy file,

```soy
// Content of examples.soy
{namespace soy.examples}

{template .foo}
  ...
{/template}
```

Use the following code to get the `Renderer` object.

```java
SoySauce soySauce = ...;

SoySauce.Renderer renderer = soySauce.renderTemplate("soy.examples.foo");
```

### Configuring the Renderer

The `Renderer` has setter methods to let you further configure your render:

-   `setData()`: Takes a `Map<String, ?>` for template data. The template data
    can also be null if the template has no required parameters.
-   `setIj()` Same as `setData()`, but for setting injected data (if
    applicable).
-   `setMsgBundle()` Specifies which `SoyMsgBundle` contains translated messages
    to use instead of the originals in the template.
-   `setExpectedContentKind()` Specifies the expected content kind for this
    template (for details, please see
    [Strict Autoescaping](security#autoescaping)).

Here is an example template:

```soy
{namespace soy.examples}

/** Says hello to a list of persons. */
{template .helloName}
  {@param names: list<string>}
  {for $name in $names}
    Hello {$name}!
  {/for}
{/template}
```

In Java, you can pass the template data to the renderer:

```java
SoySauce.Renderer renderer = soySauce.renderTemplate("soy.examples.helloName");
renderer = renderer.setData(ImmutableMap.of("names", ImmutableList.of("Alice", "Bob")));
```

The table below lists the template data types and their corresponding Java
types:

Template Type             | Java Type
------------------------- | ---------------------
`null`                    | `null`
`bool`                    | `boolean`
`int`                     | `int`
`float`                   | `double`
`string`                  | `java.lang.String`
`list<T>`                 | `java.util.List<T>`
`map<K, V>`               | `java.util.Map<K, V>`
`legacy_object_map<K, V>` | `java.util.Map<K, V>`

[Maps](../reference/types#map) and
[legacy object maps](../reference/types#legacy_object_map) can both be rendered
using `java.util.Map`s. Soy can usually infer which kind of map is intended.
This means that if you change a template parameter from `legacy_object_map` to
`map`, you do not need to change its backing value in Java. (This is different
from the situation in JavaScript, where you *do* need to
[change the backing value](js#template-data).)

Maps can contain non-string keys (ints, proto enums, etc.), while legacy object
maps cannot.

### Synchronous Rendering

The easiest way to render a template is to directly call
`renderer.render().get()`. This gives you a string for the rendered template.

For instance, if you call `renderer.render().get()` for the last example,

```java
String result = renderer.render().get();
```

`result` will be `"Hello Alice!Hello Bob!"`.

You can also use `renderStrict()` method to get a `SanitizedContent` object,
which can then be converted to safe HTML, trusted resource url, or other
sanitized types based on your content kind.

All of these APIs return `Continuation` objects which allow asynchronous
rendering. However, directly calling `get()` on them will throw an exception if
there are any incomplete futures. For asynchronous rendering, please check the
[Advanced Java Rendering](adv-java.md) page.

[renderer-source-link]: https://github.com/google/closure-templates/blob/master/java/src/com/google/template/soy/jbcsrc/api/SoySauce.java#L43

