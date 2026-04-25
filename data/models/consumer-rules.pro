# Consumer ProGuard rules for :data:models.
#
# This module ships plain Kotlin data classes; no reflection, no service
# discovery, no Class.forName. R8 is free to obfuscate everything; consumers
# don't need to keep any names. File exists to satisfy the convention
# established by the other library modules.
