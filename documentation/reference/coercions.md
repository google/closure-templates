# Coercions

Some Soy operations will take an expression of a given type and coerce it to
another type. This page will describe how these operations behave and when they
trigger.

[TOC]

TODO(lukes): this page is insufficiently exhaustive, but it is a start

## String coercions {#string}

There are a few ways to cause an implicit string coercion in Soy.

*   [print commands](print.md) implicitly coerce their expression to a string
*   [concatenation](expressions.md#plus) with a string will coerce a value to a
    string

While every value in Soy can be coerced to a string, not every value has a
useful string representation, or a string representation that is consistent in
all the backends.

### number, string, bool, safe string types

These mostly do what you expect, but for numbers consider using a number
formatting library to make I18n appropriate text.

### list, record, map, proto, null, undefined, ve, ve_data

These mostly _do not do what you expect_. Avoid doing this other than for
debugging purposes.

## Boolean coercions

There are a few ways to coerce a value to a boolean.

*   use it in an [if-expression](control-flow.md#if)
*   use it in a [ternary expression](expressions.md#ternary-operator)
*   use the built-in function [`Boolean()`](functions.md#Boolean)

All values have a boolean coercion (sometimes referred to as a 'truthiness'
check), these mostly follow JavaScript semantics:

*   '0' values are falsy, e.g. `null`, `""`, `0`
*   all other values are truthy. This include the sanitized content types
    (`html`, `attributes`, `uri`, `trusted_resource_uri`, `css`, `js`), even
    when the content is empty string. Use the
    [`hasContent()`](go/soy-functions#hasContent) function if you need to check
    if the contents are empty.

NOTE: there are some inconsistencies in how these work across backends. For
example, in Python, the empty list, empty record, and empty maps are all
currently falsy (b/19271140).
