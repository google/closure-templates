# Functions

[TOC]

## What is a Soy function?

Similar to other programing languages, Soy functions are a way to perform
particular tasks in your Soy templates. Examples include checking the length of
a list, basic number operations such as rounding and flooring, etc.

Functions are either built in (included in the compiler) or custom (implemented
by the user).

## How is a function called? {#call}

Functions are called from within expressions, which perform operations, and are
evaluated for their results or side-effects (or both). Expressions can be used
pretty much anywhere within a template. (Soy's rules for parsing and evaluating
expressions are actually quite complicated, but the majority of places where
you'd want them to work, they just do.)

For example:

```soy
{param myText: htmlToText($html)) /}
```

The above code calls `htmlToText()` to convert the html value `$html` to text,
and assigns it to a new parameter `$myText`.

## What types of functions are there?

There are three types of functions: built-in functions, external functions, and
plugins. These differ only in the way they are defined. All functions are
[called](#call) in the same way.

### Built-in functions

Built-in functions are defined in the compiler. For a complete list, see
[Methods and Functions](../reference/functions).

### External functions

External functions are written by the user in one or more host languages (Java,
JavaScript, Python) and are made available to Soy templates via the `{extern}`
command. For more information, see
[Creating an External Function](../dev/externs).

### Plugins

**Warning:** For new custom functions, use external functions instead of
plugins.

Users can use the plugin mechanism to write custom functions, print directives,
or other message file formats. (Print directives perform post-processing on the
output of a print command.)

Users implement the plugin in one or more host languages and are made available
to Soy templates via an API. For more information, see
[Creating a Plugin](../dev/plugins).

<br>

--------------------------------------------------------------------------------

<section class="nextButton"><a href="auto-escaping.md">CONTINUE >></a></section>

<br>
