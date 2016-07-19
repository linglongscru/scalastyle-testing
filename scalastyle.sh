#!/bin/bash

set -e

contains () {
  local e
  for e in "${@:2}"; do [[ "$e" == "$1" ]] && return 0; done
  return 1
}

if grep -q 'scalastyle-sbt-plugin' ./project/plugins.sbt; then
    OUTPUT=$(grep -i 'scalastyleTarget' ./build.sbt | grep -oP '(?<=").*(?=")')
    if [ -z $OUTPUT ]; then
	OUTPUT=target/scalastyle-result.xml
    fi
    rm -f $OUTPUT
    sbt scalastyle
    cat $OUTPUT
    rm -f $OUTPUT
else
    if [ -f ./build.sbt ]; then
	version_line=$(grep -i 'scalaVersion' ./build.sbt)
    else
	echo "No build.sbt Found" >&2
	exit 1
    fi

    if [[ $version_line == *"2.10"* ]]; then
	LINK=https://oss.sonatype.org/content/repositories/releases/org/scalastyle/scalastyle_2.10/0.8.0/scalastyle_2.10-0.8.0-batch.jar
    elif [[ $version_line == *"2.11"* ]]; then
	LINK=https://oss.sonatype.org/content/repositories/releases/org/scalastyle/scalastyle_2.11/0.8.0/scalastyle_2.11-0.8.0-batch.jar
    else
	echo "No scalaVersion found, install scalastyle_2.11-0.8.0-batch.jar by default" >&2
	LINK=https://oss.sonatype.org/content/repositories/releases/org/scalastyle/scalastyle_2.11/0.8.0/scalastyle_2.11-0.8.0-batch.jar
    fi

    if [  ! -f ~/.analysis/scalastyle/scalastyle.jar ]; then
	mkdir -p ~/.analysis/scalastyle
	wget  -O ~/.analysis/scalastyle/scalastyle.jar $LINK
	echo "Downloading $LINK" >&2
    fi

    COMMAND=~/.analysis/scalastyle/scalastyle.jar

    rm -f /tmp/analysis_result_scalastyle

    if contains "--config" "$@" ; then
	java -jar $COMMAND --xmlOutput /tmp/analysis_result_scalastyle "$@"  || true
    else
	CONFIG=$(find -type f -name '*.xml' | grep -r -i -l 'Scalastyle standard configuration')
	if [ -z $CONFIG ]; then
	    echo "No scalastyle_config.xml found, please add scalastyle_config.xml in your repository" >&2
	    exit 1
	fi
	java -jar $COMMAND --config $CONFIG --xmlOutput /tmp/analysis_result_scalastyle "$@"  || true
    fi
    echo "Running $COMMAND $@">&2
 
    cat /tmp/analysis_result_scalastyle
    rm -f /tmp/analysis_result_scalastyle
fi


