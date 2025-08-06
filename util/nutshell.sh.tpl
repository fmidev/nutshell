#!/bin/bash

# NutShell wrapper script
# Markus.Peura@fmi.fi
#
# Installed on ${DATE} by ${USER}@${HOSTNAME}
# 

# Workarounds for envsubst
#NUTSHELL_VERSION=${NUTSHELL_VERSION:-'java'}
NUTSHELL=${NUTSHELL_VERSION:-$NUTSHELL_VERSION}
# NUTSHELL_DIR=${NUTSHELL_DIR:-$NUTSHELL_ROOT}
# NUTSHELL_CONF=${NUTSHELL_CONF:-''}
# GROUP_ID=$GROUP_ID

case $NUTSHELL in
    python)
	export PYTHONPATH=$PYTHONPATH:$NUTSHELL_ROOT
	# NUTSHELL="python3.6 -m nutshell.nutshell"
	NUTSHELL="python3.8 -m nutshell.nutshell"
	# TODO: python version selection 3.6...3.10
	;;
    java|docker-java)
	# Modify this to use a JAR file separate from Tomcat
	NUTSHELL_JAR=${NUTSHELL_JAR:-$NUTSHELL_JAR_DIR/Nutlet.jar}
	NUTSHELL="java -cp $NUTSHELL_JAR nutshell.ProductServer"
	;;
    tomcat*)
	VERSION=${NUTSHELL/tomcat/}
	NUTSHELL_JAR=${NUTSHELL_JAR:-"$HTML_ROOT/WEB-INF/lib/Nutlet${VERSION}.jar"}
	NUTSHELL="java -cp $NUTSHELL_JAR nutshell${VERSION}.ProductServer"
	;;
    *)
	echo "NUTSHELL must be 'java', 'tomcat' or 'python'"
	#NUTSHELL='echo'
	exit 1
	;;
esac     

RESULT=0

if [ $# != 0 ]; then

    # Override default log, if user starts with --log... command
    LOG_CMD='--log_level'
    LOG_ARG='INFO'
    if [ ${1:0:5} == '--log' ]; then
	LOG_CMD="$1"
	LOG_ARG="$2"
    fi
    
    # --log_level DEBUG
    # Notice: PYTHON and JAVA have different labels. Common ones: ERROR,WARNING,INFO,DEBUG
    # Double quotes "" are needed to keep explicit empty '' arguments.
    ${NUTSHELL} "${LOG_CMD}" "${LOG_ARG}" --conf "${NUTSHELL_CONF}" "$@"  
   
    # ${NUTSHELL} --log_level INFO --conf ${NUTSHELL_DIR}/nutshell-$NUTSHELL_VERSION.cnf "$@"  # $*
    RESULT=$?
    if [ $RESULT != 0 ]; then
	#echo !!
	echo '#' ${NUTSHELL} "${LOG_CMD}" "${LOG_ARG}" --conf "${NUTSHELL_CONF}" "$@"
	echo "# Error: something went wrong, return code: $RESULT "
	echo "# Rerun '$0' without arguments for help. "
	exit $RESULT
    else
	exit 0
    fi
    
fi



echo "Nutshell (version: $NUTSHELL_VERSION )"
echo "Markus Peura 2025 Finnish Meteorological Institute"
echo
echo "# This script: $0"
echo "# Invoked by $USER@$HOSTNAME"
#echo "# NUTSHELL_DIR=$NUTSHELL_DIR"
echo "# NUTSHELL_JAR=$NUTSHELL_JAR"
#echo "# HTTP_ROOT=$HTTP_ROOT"
echo "# NUTSHELL=$NUTSHELL"
echo "# Primary conf: ${NUTSHELL_DIR}/nutshell-${NUTSHELL_VERSION}.cnf"
echo
echo "Usage: nutshell <arguments>"
echo "Arguments:  those of this $NUTSHELL_VERSION version, listed with: "
echo "  $0 --help"
echo "or"
echo "  $NUTSHELL --help"
echo
echo "For debugging, invoke this ($NUTSHELL_VERSION) version directly, for example:"
echo "  $0 --version"
echo "  $NUTSHELL --version"
echo "  $NUTSHELL --conf ${NUTSHELL_DIR}/nutshell-${NUTSHELL_VERSION}.cnf --version"
echo

exit $RESULT
