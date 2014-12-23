---
title: Concepts
---

<!--#set var="NOLEFTNAV" value=""-->
<!--#include virtual="/eng/engEDU/templates/start-before-head.shtml" -->
<!--#if expr="${COMPOSITE} != true" -->
<!-- gototop.js adds a "back to top" link before each h2 tag -->
<!--#endif -->
<!--#include virtual="/eng/engEDU/templates/start-after-head.shtml" -->

[TOC]

## Compiler Backends

The Soy compiler has one frontend for parsing Soy syntax and constructing an intermediate tree representation, and multiple backends for supporting usage from multiple programming languages. Currently, you can use one of two backends:

* The JavaScript Source backend supports usage in JavaScript. This backend compiles each input Soy file into a corresponding output JS file. Each non-private Soy template maps to a corresponding JS function of the same name.
* The Java Tofu backend supports usage in Java. This backend compiles a bundle of Soy files into a Java object representation (called Tofu) that knows how to render any template in the bundle.

## Template Data

Soy uses a language-neutral data model in which the template data is a tree structure. There are 2 types of internal nodes: map and list. All map keys must be identifiers (i.e. you can use letters, digits, and the underscore character). There are 5 types of leaf nodes (i.e. primitives): null, boolean, integer, float, and string. The root of a template data tree must be a map from parameter names to their values.  

Each Soy compiler backend has its own representation for the template data.  For example, consider a template that takes 2 parameters: the `name` of a person and the list of `destinations` that he or she recently traveled to.

In JavaScript, a valid data object is any JS object (since all JS objects are maps), for instance:

```
var data = {name: 'Melissa', destinations: ['Singapore', 'London', 'New York']};
```

In Java, a valid data object is either a `SoyMapData` instance or a `Map<String, ?>` (see the [Java Usage](./soy_javausage.shtml) chapter on reasons to use one or the other), for example:

```
SoyMapData data = new SoyMapData(
    "name", "Melissa",
    "destinations", new SoyListData("Singapore", "London", "New York"));

Map<String, Object> data = ImmutableMap.<String, Object>of(
    "name", "Melissa",
    "destinations", ImmutableList.of("Singapore", "London", "New York"));
```

For detailed info on each backend, please refer to the [JavaScript Usage](./soy_javascriptusage.shtml) and [Java Usage](./soy_javausage.shtml) chapters.

## File Structure

Soy templates appear in Soy files, which have the extension `.soy`. Each Soy file should contain a logical package of related templates and must be encoded in UTF-8 (note that ASCII is valid because it's a proper subset of UTF-8).

Every Soy file must, at minimum, have the following components:

* A namespace declaration.
* One or more template definitions.

#### The Namespace Declaration

A Soy file must have exactly one `namespace` declaration, which  must appear before the templates. A valid namespace is one or more identifiers joined with dots, for example: 

```
{namespace examples.simple}
```

Note that for backends that generate source code (currently just the JavaScript Source backend), the namespace is used in the generated code. See the [JavaScript Usage chapter](soy_javascriptusage.shtml) for more details.

The `namespace` tag has an optional attribute for establishing the default `[autoescape](soy_commands.shtml#autoescape)` mode for the templates in the file: `{namespace examples.simple autoescape="strict"}`. Note however that `strict` is the general default for this attribute, and does not need to be explicitly specified.

#### Template Definitions

Define one or more templates after the namespace declaration, using one  `template` tag per template.  You must create a unique name for each template and begin the template name with a dot, which indicates that the template name is relative to the file's namespace. Put the template's body between the `template` tag and the corresponding `/template` tag. Here is an example template:

```
/**
 * Says hello to a person.
 */
{template .helloName}
  {@param name: string} /** The name of the person to say hello to. */
  Hello {$name}!
{/template}
```

Precede each template with a SoyDoc comment that explains the purpose of the template, in the same style as JavaDoc. Add a `@param` declaration for each required parameter and a `@param?` declaration for each optional parameter of the template.

**Note**: You must put `namespace`, `template`, and `/template` commands, as well as the `/**` that starts a SoyDoc comment, at the start of their own lines.

## Command Syntax

Soy commands are directions for Soy that you write inside of Soy tags, which are delimited by braces. The first item in a Soy tag is the command name. The tag can end there or can contain command text in a format that is specific to the command. For some commands that mirror standard programming constructs, the command text is in a similar style. Here are 3 example Soy tags:

```
{/foreach}
{if length($items) > 5}
{msg desc="Says hello to the user."}
```

If a command has a corresponding end command, then the end command's name is a `/` followed by the name of the start command, e.g. `foreach` and `/foreach`.

The most common command is `print`, which prints a value. You write either `{print $userName}` or the short form `{$userName}`. Because `print` is so common, it is the implied command for all Soy tags that don't begin with a command.  

If you need to include brace characters within a Soy tag, use double braces to delimit the Soy tag, e.g.

```
{% raw %}
{{msg desc="Example: The set of prime numbers is {2, 3, 5, 7, 11, 13, ...}."}}
{% endraw %}
```

You can find explanations of all the commands in [Commands](./soy_commands.shtml).

## Expressions

You can write expressions within templates to reference template data, variables, or globals. Soy use a language-neutral expression syntax. This section describes how to reference data, write literals for all the primitive types, and use basic operators.

### Referencing Data

To reference template data, use a dollar sign followed by the parameter name, e.g. `$userName`. To reference deeper data, you can string multiple keys together using dots. For example, `$folders.0.name` evaluates to the value of the key `name` within the first element (index 0) of the `folders` list. However, this reference would only succeed if `folders` is a nonempty list of maps, and the maps contain the key `name`. Other equivalent ways to write this data reference are: `$folders[0].name` or even `$folders[0]['name']`.

The special prefix `$ij` is used for [injected data](#injecteddata), which is automatically injected into all subtemplates during a render. For example, `$ij.foo` references the injected data key `foo`.

Certain template constructs create local variables, which are referenced the same way as parameters from template data. Thus, the reference `$user.email` is correct whether `user` is a parameter or a local variable.

All of the above types of references can optionally access keys using `?.` instead of `.` or using `?[…]` instead of `[…]`. These are called null-safe accesses. A null-safe access will evaluate to null when the left side (the portion before the `?`) is either undefined or null, without attempting to continue evaluation. The two types of accesses (basic and null-safe) can be mixed. For example, the reference `$aaa?.bbb.ccc?[0]` is protected against `$aaa` being undefined or null, and is also protected against `$aaa.bbb.ccc` being undefined or null, but is not protected against `$aaa.bbb` being undefined or null.

A name without a preceding dollar sign is a global, e.g. `app.css.CSS_NAV_LINK`. A global is data that is not explicitly passed into the template as a parameter, but instead is expected to be provided some other way (e.g. either directly to the compiler during template compilation or defined in the environment in the case of JavaScript). Use globals judiciously, and reserve them for true global constants that should not sensibly be passed to the template as data. In JavaScript usage, globals can be bound either at compile time or at render time (or a combination of the two). In Java usage, there is no concept of render-time globals, so all globals must be bound at compile time.

### Basic Types

This table describes how to write literals for primitive types, lists, and maps:

<table summary="This table describes how to write literals for the primitive types.">
<tr>
  <th>Type</th>
  <th>Literal</th>
</tr>
<tr>
  <td>Null</td>
  <td>`null`</td>
</tr>
<tr>
  <td>Boolean</td>
  <td>`false` or `true`</td>
</tr>
<tr>
  <td>Integer</td>
  <td>Decimal (e.g. `-827`) or hexadecimal (must begin with `0x` and must use capital `A-F`, e.g. `0x1A2B`).</td>
</tr>
<tr>
  <td>Float</td>
  <td>Must be in decimal and must either:

* Have digits both before and after the decimal point (both can be a single `0`), e.g. `0.5`, `-100.0`, or
* Have a lower-case `e` that represents scientific notation, e.g. `-3e-3`, `6.02e23`.
  Even though the primitive type is named Float, it has the precision of a `number` in JavaScript or
  a `double` in Java.
  </td>
</tr>
<tr>
  <td>String</td>
  <td>Delimited by single quotes only. Supports these escape sequences:  `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u####` where the `####` can be any 4 hex digits to denote a Unicode code point.
  </td>
</tr>
<tr>
  <td>List</td>
  <td>
    `[<expr1>, <expr2>, ...]`

    For example: `[1, 'two', [3, false]]`

    `[]` is the empty list.
  </td>
</tr>
<tr>
  <td>Map</td>
  <td>
    `[<keyExpr1>: <valueExpr1>, <keyExpr2>: <valueExpr2>, ...]`

    For example: `['aaa': 42, 'bbb': 'hello']`

    `[:]` is the empty map.

    Note: Square brackets (`[…]`) delimit both lists and maps because braces
    (`{…}`) delimit [commands](soy_commands.shtml).
  </td>
</tr>
<tr>
</table>

### Operators

Here are the supported operators, listed in decreasing order of precedence (highest precedence at the top):

*   &nbsp; `-` (unary) &nbsp; `not`
*   &nbsp; `*` &nbsp; `/` &nbsp; `%`
*   &nbsp; `+` &nbsp; `-` (binary)
*   &nbsp; `<` &nbsp; `>` &nbsp; `<=` &nbsp; `>=`
*   &nbsp; `==` &nbsp; `!=`
*   &nbsp; `and`
*   &nbsp; `or`
*   &nbsp; `?:` (binary) &nbsp; `? :` (ternary)

**Note:** Use parentheses to override precedence rules.

You can use the operator `+` for either adding numbers or concatenating strings. When one of the two operands is a string, the other value is coerced to a string. All primitive values have string representations. The string representation of a map or list is not well-defined, so don't print a map or list unless you're debugging.

The `/` operator performs floating point division. To divide two integers to get an integer, use the `floor` function or a similar function in tandem with the division operator.

Boolean operators are short-circuiting. When a non-boolean value is used in a boolean context, it is coerced to a boolean. Each primitive type has exactly one falsy value: `null` is falsy for null, `false` is falsy for booleans, `0` is falsy for integers, `0.0` is falsy for floats, and `''` (empty string) is falsy for strings. All other primitive values are truthy. Maps and lists are always truthy even if they're empty. Undefined data keys are falsy. This allows you to check the presence of a data key before using it.

The binary operator `?:` is called the null-coalescing operator (also known as the elvis operator). It evaluates to the left operand if the left operand is defined and not null (same check as the function `isNonnull`), else it evaluates to the right operand.

The ternary operator `? :` evaluates the first operand as a boolean, and either evaluates to the second operand if the first operand is truthy, or evaluates to the third operand if the first operand is falsy.

**Note:** The checks done by the binary operator `?:` and the ternary operator `? :` are different. Specifically, `$a ?: $b` is not equivalent to `$a ? $a : $b`. Rather, the former expression is equivalent to `isNonnull($a) ? $a : $b`.

### Functions

You can use some simple functions in Soy expressions. For a list of functions, refer to the chapter [Functions and Print Directives](soy_functionsanddirectives.shtml).

## Injected Data

Injected data is data that is available to every template.  You don't need to use the `@param` declaration for injected data, and you don't need to manually pass it to called subtemplates.

Given the template:
<pre class="prettyprint lang-c">
{namespace ns}

/** Example. */
{template .example}
  foo is {**$ij.foo**}
{/template}
</pre>

In JavaScript, you can pass injected data via the third parameter.

<pre class="prettyprint lang-js">
// The output is 'foo is injected foo'.
output = ns.example(
    {},  // data
    null,  // optional output buffer
    **{'foo': 'injected foo'}**)  // injected data
</pre>

In Java, using the Tofu backend, you can inject data by using the `setIjData` method on the `Renderer`.

<pre class="prettyprint lang-java">
SoyMapData ijData = new SoyMapData();
ijData.put("foo", "injected foo");

SoyTofu tofu = ...;
String output = tofu.newRenderer("ns.example")
    **.setIjData(ijData)**
    .render();
</pre>

Injected data is not scoped to a function like parameters.  The templates below
behave in the same way as the ".example" template above despite the lack of any
`data` attribute on the `call` tag.

<pre class="prettyprint lang-c">
{namespace ns}

/** Example. */
{template .example}
  {call .helper /}
{/template}

/** Helper. */
{template .helper private="true"}
  foo is {**$ij.foo**}
{/template}
</pre>

## Comments

Comments within Soy templates follow the same syntax as Java or JavaScript:

*   `//` begins a rest-of-line comment
*   `/* comment */` delimit an arbitrary comment (can be multiline)

Note that `//` only begins a comment if the preceding character is whitespace. This is so that strings like URLs are not interpreted as comments.

Comments do not appear in the rendered output, but they also do not appear in the generated code for backends that generate source code (which is currently just the JavaScript Source backend). Note that HTML comments (i.e. `<!-- ... -->`) are not processed by Soy, and do appear in the rendered output.   

## Raw Text

Everything in a template that's not part of a Soy tag is considered raw text, including all HTML tags. There are also a few special character commands and a `literal` command that generate raw text. Other than joining lines and removing indentation, the Soy compiler does not attempt to parse raw text so the raw text simply appears in the rendered output exactly as written. The one exception is the text within a `msg` block, which you can learn about in [the `msg` section](./soy_commands.shtml#msg) of the Commands chapter.

## Line Joining

Within the body of a template, you can indent the lines as much as you want because Soy removes all line terminators and whitespace at the beginning and end of lines, including spaces preceding a rest-of-line comment. Soy completely removes empty lines that consist of only whitespace. Then, consecutive lines are joined by the following heuristic: if the join location borders a Soy or HTML tag on either side, the lines are joined with no space. If the join location does not border a Soy or HTML tag on either side, the lines are joined with exactly one space. 

The line joining heuristic is conservative in that it usually doesn't add a space where one is not wanted, but it sometimes doesn't add a space where one is needed. In the latter case, simply use the [special character](./soy_commands.shtml#specialcharacters) command `{sp}` to add the space you need. Note that in the rare former case, you can use `{nil}`.

<!--#include virtual="/eng/engEDU/templates/end.shtml" -->
