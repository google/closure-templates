# Logging

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

Example:

```soy
{log}Found {$numWidgets} widgets{/log}
```
