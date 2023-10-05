#!/bin/bash -e

rm -rf build/distributions

./gradlew buildPlugin

unzip -o build/distributions/formatter-plugin-1.0-SNAPSHOT.zip -d build/distributions

# Make sure to pull latest image before building new ones to reuse cache
# docker pull nilsbaumgartner1994/idea-parser
docker build . -t nilsbaumgartner1994/idea-parser --progress=plain --cache-from nilsbaumgartner1994/idea-parser
