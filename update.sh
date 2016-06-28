#!/bin/sh

DIRNAME=$(cd $(dirname $0); pwd)
PROGNAME=`basename "$0"`

if [ "$#" -ne 1 ]; then
  echo "Illegal number of arguments. Use '$PROGNAME <version>'"
  exit 1
fi

projects=`find $DIRNAME/code -name pom.xml | awk '{sub("pom.xml", ""); print $0}'`
for project in $projects
do
  cd $project
  ./mvnw versions:set -DnewVersion=$1
  ./mvnw clean package
  rm pom.xml.versionsBackup
done
