# ToxiFiles architecture boundaries

## Gradle modules

- `:app` is the Android application boundary. It owns activities, Android framework integration, storage providers, UI, and application lifecycle.
- `:core` is a Java 17 library for platform-neutral primitives shared by the application. Its extracted components are operation-scoped cancellation, strict ZIP-entry and filesystem child-path validation, read-only Developer Studio parsing/analysis, PIN-verifier primitives, and canonical filesystem path resolution. These utilities remain Android-free and have JVM regression tests in the core module. The bounded offline tool engine also lives here: strict text decoding, bounded reads, hashes, binary/hex conversion, folder comparison, and safe ZIP rewriting.

The core module intentionally has no Android SDK dependency. Android-specific services remain in `:app` and depend on `:core`, never the reverse.

## Migration rule

Move a component into `:core` only after verifying that it has no Android framework, AndroidX, application-context, or resource dependency. Preserve its package name during extraction where this avoids unnecessary call-site churn. Keep pure-Java tests in `:core` and Android integration tests in `:app`; CI executes both test tasks and Android lint after each extraction.

## Current boundary limitation

`MainActivity` remains a large legacy composition root. It is not split automatically because its UI, lifecycle, navigation, and storage state are tightly coupled. Further extraction must proceed by cohesive services with explicit ownership and tests, not by moving arbitrary methods into empty packages.
