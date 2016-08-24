#!/bin/bash
rm -rf work/plugins
# set java param for issue https://wiki.jenkins-ci.org/display/JENKINS/Plugins+affected+by+fix+for+SECURITY-170
# only needed for jenkins versions < 1.651.2
export MAVEN_OPTS="-Dhudson.model.ParametersAction.keepUndefinedParameters=true"
mvn -Dmaven.test.skip=true -DskipTests=true clean hpi:run
