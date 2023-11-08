#!/bin/bash

if [ -z ${USER_UID:+x} ]
then
  export USER_UID=1000
  export GROUP_GID=1000
fi

MVN_OPTS="-Duser.home=/var/maven"

init() {
  me=`id -u`:`id -g`
  echo "DEFAULT_DOCKER_USER=$me" > .env
}

clean () {
  docker compose run --rm maven mvn $MVN_OPTS clean
}

buildGradle () {
  docker compose run --rm maven mvn $MVN_OPTS install -DskipTests
}

publish () {
  version=`docker compose run --rm maven mvn $MVN_OPTS help:evaluate -Dexpression=project.version -q -DforceStdout`
  level=`echo $version | cut -d'-' -f3`
  case "$level" in
    *SNAPSHOT) export nexusRepository='snapshots' ;;
    *)         export nexusRepository='releases' ;;
  esac

  docker compose run --rm  maven mvn -DrepositoryId=ode-$nexusRepository -DskiptTests --settings /var/maven/.m2/settings.xml deploy
}

test () {
  docker compose run --rm maven mvn $MVN_OPTS test
}

for param in "$@"
do
  case $param in
    init)
      init
      ;;
    clean)
      clean
      ;;
    buildGradle)
      buildGradle
      ;;
    test)
      test
      ;;
    install)
      buildGradle
      ;;
    publish)
      publish
      ;;
    *)
      echo "Invalid argument : $param"
  esac
  if [ ! $? -eq 0 ]; then
    exit 1
  fi
done

