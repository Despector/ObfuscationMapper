Despector
===========

A tool for updating obfuscation mappings for new versions of a piece of obfuscated software.

When working with obfuscated software typically it is quite a bit of effort to create a set of mappings
that map the obfuscated names to human readable names. And when the obuscated software receives an
update that changes the underlying obfuscation mappings then this tool can automatically remap your
existing mappings to the updated names of the new version of the software.

# Usage

`java -jar ObfuscationMapper.jar <--config=obfuscationmapper.conf> [old.jar] [old_mappings] [new.jar]`

# Mapping format

...

# Issues

Issues and feature requests can be opened in our [Issue Tracker].

[Gradle]: https://www.gradle.org/
[ASM]: http://asm.ow2.org/
[Development/Support Chat]: https://webchat.esper.net/?channels=decompiler
[Issue Tracker]: https://github.com/Despector/ObfuscationMapper/issues
[HOCON]: https://github.com/typesafehub/config/blob/master/HOCON.md
