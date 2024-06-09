#!/bin/bash
cd /usr/local/jenkins-service
# Just in case we would have upgraded the controller, we need to make sure that the agent is using the latest version of the agent.jar
curl -sO <вставить данные из Jenkins>
java -jar <вставить данные из Jenkins> -name agent -workDir "/var/jenkins"
exit 0