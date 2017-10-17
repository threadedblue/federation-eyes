#!/usr/bin/env bash

APP_NAME="federation-eyes"
DIR=`pwd`
mkdir -p $APP_NAME

export RTI_FILE="conf/RTI.rid"
cd $APP_NAME
echo "DiR="`pwd`
echo "LS="`ls`
java -jar \
-Djava.net.preferIPv4Stack=true \
$APP_NAME-0.0.1.jar server conf/$APP_NAME.yml
cd $DIR