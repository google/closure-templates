# Functions

[TOC]

## What is a Soy function?

Similar to other programing languages, Soy functions are a way to perform
particular tasks in your Soy templates. Examples include checking the length of
a list, basic number operations such as rounding and flooring, etc.

Since Soy is a template language, functions are *not* defined/declared in
templates. Built-in functions are included in the compiler, and custom functions
(aka, plugins) can be implemented by developers in multiple backends.

## What does a function look like?

Functions are called from within expressions, which perform operations, and are
evaluated for their results or side-effects (or both). Expressions can be used
pretty much anywhere within a template. (Soy's rules for parsing and evaluating
expressions are actually quite complicated, but the majority of places where
you'd want them to work, they just do.)

For example:

```soy
{param myStringLength: (strLen($myString)) /}
```

The above param finds the length of `$myString`, and assigns it to a new
parameter `$myStringLength`.

## What types of functions are there?

There are two main types of functions in Soy, ones included in the compiler,
simply known as functions, and custom functions known as plugins.

See [reference](../reference/functions) for full list of built-in functions.

### Plugins

A plugin is a custom Soy function that isn't hard-coded in the compiler.
Developers can write plugins for any additional functions, print directives, or
other message file formats. (Print directives are post-processing on the output
of a print command.)

Developers implement the plugin outside of the Soy codebase, and Soy provides an
API to connect the implementations so that the custom functions can be used in
templates. To learn how to setup a plugin, see [the dev guide](../dev/plugins)
section for details.

<br>

--------------------------------------------------------------------------------

<section class="nextButton"><a href="auto-escaping.md">CONTINUE >></a></section>

<br>

