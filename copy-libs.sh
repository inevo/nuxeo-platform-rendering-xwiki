#!/bin/sh

if [ -z "$1" ]
  then
    echo "No argument supplied"
    echo "Please provide path to Nuxeo's root"
    exit
fi

deps=target/dependency
target=$1/nxserver/lib

mvn dependency:copy-dependencies

cp $deps/xwiki-* $target
cp $deps/validation-api-1.0.0.GA.jar $target
rm $target/commons-beanutils-1.6.jar
cp $deps/commons-beanutils-1.8.3.jar $target
