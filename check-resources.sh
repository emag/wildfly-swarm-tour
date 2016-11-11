#!/bin/sh

gitbook build
resources=`find _book/ -name "*.html" | xargs sed -n 's/.*href="\([^"]*\).*/\1/p' | grep http | grep -v github.com/emag/wildfly-swarm-tour | grep -v localhost | sort | uniq`
cp /dev/null check-resources.result
for resource in $resources
do
  echo -n "Check $resource ... "
  status=`curl -sLI $resource -o /dev/null -w '%{http_code}\n'`
  echo $status
  if [ $status != "200" ]; then
    echo $resource $status >> check-resources.result
  fi
done
