# Templates

[TOC]

## What are Soy templates?

Templates are the main unit of code in Soy. Like functions or methods in other
languages, templates declare the names and types of their inputs called
parameters (`@param`), and produce an output: a rendered chunk of text or HTML.
Templates can call other templates, and related templates are grouped together
in a `.soy` file.

## What does a template look like?

Here's a simple template that takes a string parameter `name`, and prints a
greeting:

```soy
{template .helloName}
  {@param name: string}
  Hello {$name}!
{/template}
```

See the [template reference page](../reference/templates.md) for more details.

## What are Soy templates used for?

Templates are used to build the structure of a web page or webapp. While it
would be possible to specify the structure of a web page entirely in handwritten
HTML, HTML lacks basic features that are useful for building reusable UIs, such
as looping and variables. Soy adds these features while allowing template
authors to "drop into" regular HTML whenever useful.

It would also be possible to specify the structure of a web page entirely in
JavaScript, using DOM APIs. However, this requires users to download and execute
JavaScript before the page appears. By rendering templates on the server and
sending the rendered HTML to the client, you can construct a UI without
executing any client-side code.

<br>

--------------------------------------------------------------------------------

<section class="nextButton"><a href="commands.md">CONTINUE >></a></section>

<br>
