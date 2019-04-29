# Injected Data

[TOC]

## What is injected data?

Injected data is a way to provide a data object to all templates within your
app, without explicitly adding it as a `@param` to every template. Injected data
is also sometimes called ij params.

All data types can be used as injected data, including primitives (strings,
numbers, booleans), lists, maps, and protos.

## When should I use injected data?

Injected data is most useful for data that:

1.  Is used by many unrelated templates
1.  Is not modified by any caller

For example, an application may require the user's gender in rendering multiple
templates. Passing this information in as a template param may require piping
data through many intermediate templates that do not require it. Injecting the
necessary data, on the other hand, would make it available to every template
without adding bloat to your code.

Basically, a template should be able to access this data without requiring its
caller to pass it, and the data should be the same for all templates.

## What does injected data look like?

To inject data in a template, you use the `@inject` param. For instance:

```soy
{@inject myijdata: InjectedDataType}
```

Which is then accessed by calling `$myijdata`.

To render a template using injected data, you use a language-specific API. See
[Rendering a Template in JS](../dev/js.md) and [Rendering a Template in
Java](../dev/java.md) for more information.

<br>

--------------------------------------------------------------------------------

You're done with basic Soy concepts! Next, we suggest working through the
codelabs.

