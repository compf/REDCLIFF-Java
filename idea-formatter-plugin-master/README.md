# Idea Parser Plugin

[![Build](https://github.com/nilsbaumgartner1994/idea-parser-plugin/actions/workflows/ci.yaml/badge.svg?branch=master)](https://github.com/nilsbaumgartner1994/idea-parser-plugin/actions/workflows/ci.yaml)

Tool to parse a java project into an AST using IntelliJ IDEA. The AST can be then used for the data-clumps-doctor

## How to use

This repository contains an Intellij IDEA plugin that adds new command to CLI - formatter:

To use it more conveniently (without downloading proper version of IDEA), plugin and IDE are packaged into
executable docker image: TODO: [nilsbaumgartner1994/idea-parser](https://hub.docker.com/r/nilsbaumgartner1994/idea-parser).

To use it you can either execute docker container or create container beforehand and execute `formatter` inside it.

### Docker-Compose

Just pass project files via volume and run image:

```shell
./build_image.sh && docker-compose up
```

## Arguments

|     Option      | Example           | Description                                                                    |
|:---------------:|-------------------|--------------------------------------------------------------------------------|
|    -d, --dry    |                   | Perform a dry run: no file modifications, only exit status                     |
|       -h        |                   | Show this help message and exit.                                               |
|   -m, --mask    | \*.java,\*.groovy | A comma-separated list of file masks. Use quotes to prevent wildcard expansion |
| -r, --recursive |                   | Scan directories recursively                                                   |
|   -s, --style   | CodeStyle.xml     | A path to Intellij IDEA code style settings .xml file                          |

## Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you, as defined in the Apache-2.0 license, shall be
dual licensed as above, without any additional terms or conditions.
