#!/bin/bash
rm -rf work/plugins
# set java param for remote jvm connection and for
# issue https://wiki.jenkins-ci.org/display/JENKINS/Plugins+affected+by+fix+for+SECURITY-170
# keepUndefinedParameters only needed for jenkins versions < 1.651.2
export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n -Dhudson.model.ParametersAction.keepUndefinedParameters=true"
mvn -Dmaven.test.skip=true -DskipTests=true clean hpi:run
