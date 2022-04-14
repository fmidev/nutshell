#!/bin/bash

# NutShell wrapper script
# Markus.Peura@fmi.fi
#
# Installed on ${DATE} by ${USER}@${HOSTNAME}
# 

# Workarounds for envsubst
NUTSHELL=${NUTSHELL_VERSION:-$NUTSHELL_VERSION}
NUTSHELL_DIR=${NUTSHELL_DIR:-$NUTSHELL_ROOT}

case $NUTSHELL in
    python)
	export PYTHONPATH=$PYTHONPATH:$NUTSHELL_ROOT
	# NUTSHELL="python3.6 -m nutshell.nutshell"
	NUTSHELL="python3.8 -m nutshell.nutshell"
	;;
    java|docker-java)
	# Modify this to use a JAR file separate from Tomcat
	NUTSHELL_JAR=${NUTSHELL_JAR:-$NUTSHELL_JAR_DIR/Nutlet.jar}
	NUTSHELL="java -cp $NUTSHELL_JAR nutshell.ProductServer"
	;;
    tomcat)
	# This may be same as above
	NUTSHELL_JAR=${NUTSHELL_JAR:-"$HTTP_ROOT/WEB-INF/lib/Nutlet.jar"}
	NUTSHELL="java -cp $NUTSHELL_JAR nutshell.ProductServer"
	;;
    *)
	echo "NUTSHELL must be 'java', 'tomcat' or 'python'"
	#NUTSHELL='echo'
	exit 1
	;;
esac     

RESULT=0

if [ $# != 0 ]; then

    # --log_level DEBUG
    # Notice: PYTHON and JAVA have different labels
    # Common ones: ERROR,WARNING,INFO,DEBUG
    # Double quotes are needed to keep excplicit empty '' agruments.
    ${NUTSHELL} --log_level INFO --conf ${NUTSHELL_DIR}/nutshell.cnf "$@"  # $*
    RESULT=$?
    if [ $RESULT != 0 ]; then
	#echo !!
	echo "# Error: something went wrong, return code: $RESULT "
	echo "# Rerun '$0' without arguments for help. "
	exit $RESULT
    else
	exit 0
    fi
    
fi

echo "Nutshell (default version: $NUTSHELL_VERSION )"
echo "Markus Peura 2021 Finnish Meteorological Institute"
echo
echo "# This script: $0"
echo "# Invoked by $USER@$HOSTNAME"
echo "# NUTSHELL_DIR=$NUTSHELL_DIR"
echo "# NUTSHELL_JAR=$NUTSHELL_JAR"
echo "# HTTP_ROOT=$HTTP_ROOT"
echo "# NUTSHELL=$NUTSHELL"
echo
echo "Usage: nutshell <arguments>"
echo "Arguments:  those of this $NUTSHELL_VERSION version, listed with: "
echo "  $0 --help"
echo "or"
echo "  $NUTSHELL --help"
echo
echo "For debugging, invoke this ($NUTSHELL_VERSION) version directly, for example:"
echo "  $NUTSHELL --version"
echo

exit $RESULT
