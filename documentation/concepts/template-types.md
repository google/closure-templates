# Passing Templates as Parameters

You can pass templates to other templates. To do this, you declare a parameter
whose type is a template signature. The calling template passes a template with
this signature and the receiving template calls the passed-in template.

[TOC]

## What is a template type?

A _template type_ is the signature of a template. It consists of a set of
parameter names and types, and a return type. For example:

```soy
(s:string)=>html
```

## How do you declare a parameter that accepts a template?

To declare a parameter that accepts a template, simply declare a parameter and
use the type of the template being passed in. For example:

```soy
  {@param renderer: (s:string)=>html}
```

## How do you call a passed-in template?

To call a passed-in template, use a `call` statement with the name of the
parameter to which the template was passed. For example:

```soy
{template .foo}
  {@param renderer: (s:string)=>html} // Accept a template as a parameter
  {call $renderer}                    // Call the passed-in template
    {param s: 'hello' /}              // Use the parameter from the template type
  {/call}
{/template}
```

Note that the call uses the same parameter names as the template type. For
example, the template type declares a parameter named `s` and the call uses a
parameter named `s`.

## How do you pass in a template?

A template can be passed in as a parameter to another template by wrapping it in
the builtin function `template`. For example:

```soy
{template .renderer}
  {@param s: string}
  ...
{/template}

{template .bar}
   {call .foo}
    {param renderer: template(.renderer) /} // Pass the .renderer template
   {/call}
{/template}
```

## How do you partially bind a template?

You can bind some of the parameters in a passed-in template to fixed values by
using the builtin `bind` method. This method accepts a `record` literal and
returns a template whose type depends on the unbound parameters.

In the following example, the type of the template returned by `bind` is
`(s:string)=>html`. As a result, this template can be passed to `.foo`.

```soy
{template .bar}
  {call .foo}
    {param input: template(.input2).bind(record(s2:'world')) /}
  {/call}
{/template}

{template .input2}
  {@param s: string}
  {@param s2: string}
  ...
{/template}
```

<br>

--------------------------------------------------------------------------------

You're done with basic Soy concepts! Next, we suggest working through the
codelabs.


<br>
