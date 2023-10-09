#!/usr/bin/env bash
echo "Running runDemoCLI.sh"

# https://stackoverflow.com/a/246128
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
if uname -s | grep -iq cygwin ; then
    DIR=$(cygpath -w "$DIR")
    PWD=$(cygpath -w "$PWD")
fi


echo "$DIR/gradlew"

# stop gradle daemon
echo "Stopping gradle daemon"
"$DIR/gradlew" --stop

echo "Building DemoPlugin"
./gradlew :demo-plugin:buildPlugin --console=plain --info
echo "Finished building DemoPlugin"

echo "Running DemoPluginCLI with input $1 and output $2"
"$DIR/gradlew" --console=plain --info -p "$DIR" runDemoPluginCLI -Prunner=DemoPluginCLI -Pinput="$1" -Poutput="$2"
echo "Finished runDemoCLI.sh"