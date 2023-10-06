#!/bin/bash -e

rm -rf build/distributions

./gradlew :demo-plugin:buildPlugin

# Make sure to pull latest image before building new ones to reuse cache
# docker pull nilsbaumgartner1994/idea-parser
docker build . -t nilsbaumgartner1994/idea-parser --progress=plain --cache-from nilsbaumgartner1994/idea-parser
