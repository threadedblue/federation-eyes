#!/usr/bin/env bash

APP_HOME="federation-eyes"
DIR=`pwd`
mkdir -p $APP_HOME

export RTI_FILE="conf/RTI.rid"
cp -r conf $APP_HOME
cd $APP_HOME
java -jar \
-Djava.net.preferIPv4Stack=true \
$APP_HOME.jar server conf/$APP_HOME.yml
cd $DIR