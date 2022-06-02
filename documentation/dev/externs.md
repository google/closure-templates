[TOC]

# Creating an External Function

Soy allows users to write custom functions that templates can call. This is
useful for when there is some logic that is difficult or impossible to express
using Soy language features and for which there is not an existing
[built-in function](../reference/functions.md).

There are two ways to define custom functions: via the `{extern}` command (this
page) and via [plugins](plugins). All new custom functions should use the
`{extern}` command.

## When to define a custom function

Both templates and custom functions allow you to encapsulate logic that can be
called repeatedly from various templates. If the routine needs to produce
`SanitizedContent` -- e.g. `html`, `uri`, `attributes`, etc -- usually a
`{template}` is the best way to define it. Otherwise, if the routine needs to
produce other data types -- e.g. `int`, `float`, `string`, `Message`, etc -- a
custom function is the only mechanism that will work.

Some good use cases for custom functions include:

*   Custom date or number formatting
*   Complex math functions
*   Date calculations
*   Sorting or aggregating datastructures
*   ...

All of the above are difficult or impossible to implement in a plain template.
For these reasons Soy allows for users to write custom functions.

## How do I define an external function?

### 1. Define the function signature in a Soy file

Add an `{extern}` command at the top of a Soy file, above any templates. Usually
you will want to prefix the command with `export`, which makes the function
callable from other Soy files.

Within the `{extern}` command you define the Soy function signature in the form:

`functionName: (paramName: paramType, ....) => returnType`

This signature defines how the external function will interact with the Soy type
checking system. Param names only serve for documentation purposes.

```soy
import {ImageUrlOptions} from 'photos/service/image_url_options.proto';

{export extern imageUrl: (url: uri, options: ImageUrlOptions) => uri}
  ...
{/extern}
```

Function overloads are supported, meaning that you can define multiple external
functions in the same Soy file that have the same function name, as long as the
return types are the same and the parameter count and types are non-ambiguous.
For example, the following example is legal:

```soy
{extern toInt: (s: string) => int}...{/extern}
{extern toInt: (s: string, radix: int) => int}...{/extern}
{extern toInt: (s: float) => int}...{/extern}
```

### 2. Define and connect the Java backend implementation

If you want to call the external function during server side rendering you must
implement the external function in Java.

The most common way to do this is to implement the function as a `public static`
method in some Java class.

```java
package com.google.apps.framework.modulesets.soyplugins;

...

public class StaticRuntime {

  public static SafeUrl imageUrlFromOptions(SafeUrl url, ImageUrlOptions options) {
    ...
  }
}
```

To connect the function definition to the Java implementation add a `{javaimpl}`
command inside the `{extern}` command, matching the `class`, `method`, `params`,
and `return` attributes to the exact values from the implementation class.

```soy
{export extern ...}
  {javaimpl class="com.google.apps.framework.modulesets.soyplugins.StaticRuntime"
      method="imageUrlFromOptions"
      params="com.google.common.html.types.SafeUrl, com.google.photos.proto.photos.proto2api.ImageUrlOptions"
      return="com.google.common.html.types.SafeUrl" /}
{/extern}
```

If the static method is defined on a Java interface you must add
`type="static_interface"` to the `{javaimpl}` command.

See below for the [complete list of supported types](#javatypes).

It is also possible to implement the Java code as an instance method. If `class`
is the name of a concrete class and `method` is an instance method add
`type="instance"` to the `{javaimpl}` command. If `class` is the name of an
interface then add `type="interface"`.

Both of these options require that an instance is provided to the Soy compiler
whose name is equal to the `class` attribute. When using the `SoySauce` or
`SoyTofu` APIs directly, this can be done by calling the renderer's
`setPluginInstances` method.

### 3. Define and connect the JavaScript backend implementation

If you want to call the external function during client side rendering you must
implement the external function in JavaScript.

Define the JavaScript function inside any JavaScript file. The JavaScript
namespace must be defined with either `goog.provide` or `goog.module`.

<section class="polyglot">

###### JavaScript {.pg-tab}

```javascript
goog.module('apps.photos.imageurl');
goog.module.declareLegacyNamespace();

exports.formatForSoyFromOptions = function(url, options) {
  ...
}
```

The `{jsimpl}` command specifies the namespace and function name.

```soy
{export extern ...}
  {jsimpl namespace="apps.photos.imageurl" function="formatForSoyFromOptions" /}
{/extern}
```

###### TypeScript {.pg-tab}

```typescript
import SafeUrl from 'goog:goog.html.SafeUrl';

export function formatForSoyFromOptions(url: SafeUrl, options: ImageUrlOptionsProto) : SafeUrl {
  ...
}
```

The `{jsimpl}` command specifies the TypeScript path namespace and function
name.

```soy
{export extern ...}
  {jsimpl namespace="google3.java.apps.photos.imageurl" function="formatForSoyFromOptions" /}
{/extern}
```

</section>

The parameter values are passed directly from the Soy JavaScript runtime to the
function implementation. For information about how Soy types correspond to
JavaScript data types, see [Types](../reference/types.md).

Note that while overloads are supported in external function definitions, all
overloaded external functions must be implemented with the same JavaScript
function.

### 4. Use It!

At this point, you can start calling your external function from your templates.
For example:

```soy
import {imageUrlFromOptions} from 'path/to/functions.soy';

{template foo}
  {@param uri: uri}
  <img src="{imageUrlFromOptions($uri, ImageUrlOptions())}" alt="My Image"/>
{/template}
```

## Supported type mappings between Soy and Java {#javatypes}

Soy type               | Allowed Java types                                                                                        | Notes
---------------------- | --------------------------------------------------------------------------------------------------------- | -----
`int`                  | `int`\*, `java.lang.Integer`, `long`\*, `java.lang.Long`                                                  | Integer overflow throws a runtime error.
`float`                | `double`\*, `java.lang.Double`, `float`\*, `java.lang.Float`                                              |
`number`               | `double`\*, `java.lang.Double`, `java.lang.Number`                                                        | `number` is an alias for `int\|float`.
`string`               | `java.lang.String`                                                                                        |
`bool`                 | `boolean`*, `java.lang.Boolean`                                                                           |
`Message`              | `com.google.protobuf.Message`                                                                             |
protos                 | the proto message Java type                                                                               |
proto enums            | the proto enum Java type                                                                                  |
`uri`                  | `com.google.common.html.types.SafeUrl`, `com.google.common.html.types.SafeUrlProto`                       |
`trusted_resource_uri` | `com.google.common.html.types.TrustedResourceUrl`, `com.google.common.html.types.TrustedResourceUrlProto` |
`html`                 | `com.google.common.html.types.SafeHtml`, `com.google.common.html.types.SafeHtmlProto`                     |
`list<?>`              | `java.util.List`, `com.google.common.collect.ImmutableList`                                               | Supported element types are: `int`, `float`, `string`, `bool`, proto, and proto enum.
`map<?,?>`             | `java.util.Map`, `com.google.common.collect.ImmutableMap`                                                 | Same supported element types as list.
records                | `java.util.Map`, `com.google.common.collect.ImmutableMap`                                                 | Supports all value types other than `list`, `map`, records, and unions.
unions                 | `java.lang.Object`, `com.google.template.soy.data.SoyValue`                                               | All unions other than `int\|float`. Supported union members are: `int`, `float`, `string`, `bool`, proto, proto enum, `uri`, `trusted_resource_uri`, and `html`.
`any`                  | `java.lang.Object`, `com.google.template.soy.data.SoyValue`                                               |

\* If the Soy type is nullable then the primitive Java type is not allowed.

### Implicit Java Parameters

Some values passed to the Java implementation of an extern function come from
the rendering context rather than from values explicitly passed from the
template. Such values are called implicit parameters. Implicit parameters must
appear at the end of the Java implementation parameter list, after all normal
parameters.

The following implicit parameter types are supported:

Java type                          | Notes
---------------------------------- | --------------------------
`com.google.template.soy.data.Dir` | The global bidi direction.
`com.ibm.icu.util.ULocale`         | The end user's locale.
