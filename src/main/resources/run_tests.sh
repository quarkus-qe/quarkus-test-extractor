run_tests() {
  local NUMBER_OF_AXES_PER_CATEGORY=$1
  local AXIS_VALUE="$2"
  local OUTER_MVN_ADDITIONAL_ARGS="$3"
  local MVN_INSTALL_ARGS="$4"
  local MODULE_GROUP_NUMBER="${AXIS_VALUE##*-}"
  local MODULE_DIR_NAME=''
  local ADDITIONAL_ARGS=''
  
  # determine which modules should be run based on the scenario postfix
  if [[ $AXIS_VALUE == extensions* ]];
  then
      echo "Testing extension module group number $MODULE_GROUP_NUMBER"
      MODULE_DIR_NAME='extensions'
      echo "Building managed extension dependencies in case we need them during testing"
      mvn clean install -V -B --no-transfer-progress -DskipITs -DskipTests -Dextension-tests-managed-modules $MVN_INSTALL_ARGS
  else
      echo "Testing integration test module group number $MODULE_GROUP_NUMBER"
      MODULE_DIR_NAME='integration-tests'
      echo "Building managed integration test dependencies in case we need them during testing"
      mvn clean install -V -B --no-transfer-progress -DskipITs -DskipTests -Dintegration-tests-managed-modules -Dquarkus.build.skip=false $MVN_INSTALL_ARGS
      ADDITIONAL_ARGS+=' -Dintegration-tests-build'
  fi

  if [[ "$NUMBER_OF_AXES_PER_CATEGORY" != *" -pl "* ]];then
    # determine which modules should be tested
    head -n -1 pom.xml > pom-wip
    echo "    <modules>" >> pom-wip
    local TOTAL_NUMBER_OF_MODULES=$(ls -l $MODULE_DIR_NAME | grep -c ^d)
    (( REMAINDER=TOTAL_NUMBER_OF_MODULES%NUMBER_OF_AXES_PER_CATEGORY, NUMBER_OF_MODULES_PER_GROUP=TOTAL_NUMBER_OF_MODULES/NUMBER_OF_AXES_PER_CATEGORY ))
    echo "Total number of $MODULE_DIR_NAME modules is $TOTAL_NUMBER_OF_MODULES and there is $NUMBER_OF_AXES_PER_CATEGORY module groups"
    local NUMBER_OF_MODULES_IN_GROUP=-1
    if [[ "$MODULE_GROUP_NUMBER" == "$NUMBER_OF_AXES_PER_CATEGORY" ]] && [[ "$REMAINDER" != "0" ]]; then
      NUMBER_OF_MODULES_IN_GROUP=$(( REMAINDER+NUMBER_OF_MODULES_PER_GROUP ))
      echo "This is the last module group, therefore added incremented number of modules by remainder $REMAINDER"
    else
      NUMBER_OF_MODULES_IN_GROUP=$NUMBER_OF_MODULES_PER_GROUP
    fi
    echo "Number of modules in this group is $NUMBER_OF_MODULES_IN_GROUP"
    local MODULES=($( ls $MODULE_DIR_NAME ))
    (( D=MODULE_GROUP_NUMBER-1, MODULES_RANGE_START=NUMBER_OF_MODULES_PER_GROUP*D, MODULES_RANGE_END=MODULES_RANGE_START+NUMBER_OF_MODULES_IN_GROUP ))
    local NUMBER_OF_MODULES=${#MODULES[@]}
    for (( i=0 ; i<$NUMBER_OF_MODULES ; i++ ));
    do
        local MODULE_NUMBER=$(( i+1 ))
        if [[ $MODULE_NUMBER -gt $MODULES_RANGE_START ]]; then
            local MODULE_NAME="$MODULE_DIR_NAME/${MODULES[i]}"
            echo "        <module>$MODULE_NAME</module>" >> pom-wip
            if [[ $MODULE_NUMBER -eq $MODULES_RANGE_END ]]; then
                break
            fi
        fi
    done
    echo "    </modules>" >> pom-wip
    echo "</project>" >> pom-wip
    mv pom-wip pom.xml
  else
      echo "The 'MVN_ADDITIONAL_ARGS' environment variable specifies modules that should be run using the '-pl' option"
  fi
           
  # IMPORTANT: we need to have installed in local repo all the artifacts for all the test modules (and POM parent)
  # simply because some tests like io.quarkus.info.deployment.NoGitProjectInfoTest fail if they don't exist
  echo "Building tested modules"
  mvn clean install -V -B --no-transfer-progress -DskipITs -DskipTests $MVN_INSTALL_ARGS
  
  echo "Running tests"
  mvn clean verify -V -B --no-transfer-progress $OUTER_MVN_ADDITIONAL_ARGS
}

run_tests $1 "$2" "$3" "$4"
