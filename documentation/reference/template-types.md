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

<section class="polyglot">

###### Call Command {.pg-tab}

```soy
{template .foo}
  {@param renderer: (s:string)=>html} // Accept a template as a parameter
  {call $renderer}                    // Call the passed-in template
    {param s: 'hello' /}              // Use the parameter from the template type
  {/call}
{/template}
```

###### Element Composition {.pg-tab}

```soy
{template .foo}
  {@param renderer: (s:string)=> html<?>} // Accept a template as a parameter

  <{$renderer.bind(record(s: 'hello'))} /> // See below for info on .bind()
{/template}
```

</section>

Note that the call uses the same parameter names as the template type. For
example, the template type declares a parameter named `s` and the call uses a
parameter named `s`.

## How do you pass in a template?

To pass a template as a parameter, directly use a template as a value. If a
template is locally declared in the file, wrap the reference in a `template()`
call.

<section class="polyglot">

###### Call Command {.pg-tab}

```soy
{template foo}
  {@param content: html}
  {$content}
{/template}

{template bar}
  {@param tpl: (content:html) => html}
  {call $tpl}
    {param}<div></div>{/param}
  {/call}
{/template}

{template baz}
  {call bar}
    {param tpl: template(foo) /}
  {/call}
{/template}
```

###### Element Composition {.pg-tab}

```soy
{template foo}
  {@param content: html}
  {$content}
{/template}

{template bar kind="html<?>"}
  {@param tpl: (content:html) => html}
 <{$tpl}>
    <parameter slot="content">
      <div></div>
    </parameter>
  </>
{/template}

{template baz}
  <{bar(tpl: template(foo))} />
{/template}
```

</section>

If a template is imported, use the value directly

<section class="polyglot">

###### Call Command {.pg-tab}

```soy
import {foo} from '<PATH>';

{template bar}
  {@param tpl: (content: html) => html}
{/template}

{template baz}
  {call bar}
    {param tpl: foo /}
  {/call}
{/template}
```

###### Element Composition {.pg-tab}

```soy
import {foo} from '<PATH>';

{template bar}
  {@param tpl: (content: html) => html}
{/template}

{template baz}
  <{bar(tpl: $foo)} />
{/template}
```

</section>

A template can be bound to a template type so long as all of its required
parameters appear in the type. In short the following templates can be bound to
`(content:html)=>html`.

```soy
{template <TEMPLATE_NAME>}
  {@param tpl: (content:html) => html}
{/template}

{template <TEMPLATE_NAME2>}
  {@param content: html}
  {$content}
{/template}

{template <TEMPLATE_NAME3>}
  {@param? content: html}
  {$content}
{/template}
```

but the following template cannot.

```soy
{template <TEMPLATE_NAME2>}
  {@param content: html}
  {@param contentTwo: html}
  {$content}{$contentTwo}
{/template}
```

## How do you partially bind a template?

You can bind some of the parameters in a passed-in template to fixed values by
using the builtin `bind` method. This method accepts a `record` literal and
returns a template whose type depends on the unbound parameters.

In the following example, the type of the template returned by `bind` is
`(s:string)=>html`. As a result, this template can be passed to `.foo`.

<section class="polyglot">

###### Call Command {.pg-tab}

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

###### Element Composition {.pg-tab}

```soy
{template .bar}
  <{foo(input: template(.input2).bind(record(s2:'world')))} />
{/template}

{template .input2}
  {@param s: string}
  {@param s2: string}
  ...
{/template}
```

</section>

<br>

