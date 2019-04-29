# Type System

[TOC]

## What is a type system?

Similar to other programming languages, Soy has a set of rules that assign types
to variables, expressions and template parameters. Soy performs a lot of these
checks automatically.

## How does Soy type check?

Soy performs two types of type checks: static (compile time) and dynamic
(runtime). For more information, see the [types](../reference/types.md) and
[expressions](../reference/expressions.md) reference pages.

### Static type checking

Static type checking is done within the body of a template if the value's type
is known.

For example, if a parameter `a` has type `number`, then expressions like `a[0]`
or `a.fieldName` will produce a compile-time type error. Another example is if a
parameter `a` has type `map<string, string>` and a second parameter `b` has type
`string`, then expressions like `a + b` or `a / b` will throw a type error.

#### Cross template static checking

Static type checking is only done across templates when templates are compiled
together in a single build rule. If templates are compiled individually,
cross-template type checks are not done.

This means that if two templates are compiled individually, but rely on one
another, it could lead to static type errors.

### Dynamic type checking

Template parameters are also type-checked at runtime. For example, when a
template is rendered, if the value being passed does not match the declared type
of the parameter, an exception will be thrown.

## What does type checking look like?

If your types are correct, nothing! These are automatic checks performed by Soy
for your convenience.

<br>

--------------------------------------------------------------------------------

<section class="nextButton"><a href="ij-data.md">CONTINUE >></a></section>

<br>
