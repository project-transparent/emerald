![Emerald](https://github.com/project-transparent/emerald/blob/main/emerald.png)

**Emerald** is a Gradle plugin that provides an easy, automatic configuration for Javac plugins.

## Overview

Javac plugins are a very useful, albeit underused feature. They allow for modification of a compiler's AST tree at any point in the compilation process, and therefore are very powerful. Due to said plugin's obscure nature, Gradle lacks specific support for them. This makes it a little confusing for people who are new to Javac plugin projects to use or test them, they can also be very verbose. This project allows you to configure a Javac plugin in *just one* line.

### Usage

- Install the plugin (instructions below).
- Add your dependency via the `compilerPlugin` configuration.
- Done!

## Installation (Gradle - Local)

1. Clone the repo (https://github.com/project-transparent/emerald).
2. Run `gradlew publishToMavenLocal` in the root directory of the repo.
3. Add `mavenLocal()` to your plugin repositories.
4. Add `id 'org.transparent.emerald' version '<version>'` to your plugins.
