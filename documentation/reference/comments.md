# Comments


This page describes how to comment your Soy file.

**NOTE**: No type of comment is ever rendered to Soy's output.

[TOC]

## Single line comments

`//` single line comments example:

```soy
{template .example}
  {@param exampleParam: example_source}

  // This is a single line Soy comment.
  <div>
    Example HTML content.
  </div>
{/template}
```

## Multiline comments

`/* ... */` multiline comments example:

```soy
{template .example}
  {@param exampleParam: example_source}  /** Long doc-comment for param
      declarations split over multiple lines. */

  /* This is a one star Soy comment. */
  <div>
    Example HTML content.
  </div>
  /*
   * This is a longer
   * multiline comment
   */
{/template}
```
