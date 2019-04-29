# Logging


<!--#include file="commands-blurb-include.md"-->

This chapter describes the Log command.

[TOC]

### Log {#log}

There are two commands for logging: `log` and `/log`.

Syntax:

```soy
{log}...{/log}
```

Use this to log a message for debugging.

*   In JavaScript, this writes to the `console`.
*   In Java, this writes to `System.err`.
*   In Python, this generates a `print` statement.
*   In Boq, this writes to the `STDOUT` for your Boq node in Hexa, found
    [here](http://screen/LVXOgC4jaqB.png).

**NOTE**: You shouldn't see many of these in Code Search; they shouldn't be
checked in!

Example:

```soy
{log}Found {$numWidgets} widgets{/log}
```
