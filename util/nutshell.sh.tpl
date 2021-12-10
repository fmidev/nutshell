#!/bin/bash

# NutShell wrapper script
#
# Markus.Peura@fmi.fi
#

#export NUTSHELL_ROOT
export PYTHONPATH=$PYTHONPATH:$NUTSHELL_ROOT
#export HTTP_ROOT=$HTTP_ROOT

# Workarounds for envsubst
NUTSHELL=${NUTSHELL_VERSION:-$NUTSHELL_VERSION}
NUTSHELL_DIR=${NUTSHELL_DIR:-$NUTSHELL_ROOT}
NUTSHELL_JAR=${NUTSHELL_JAR:-$NUTSHELL_JAR_DIR/Nutlet.jar}

case $NUTSHELL in
    python)
	NUTSHELL="python3.6 -m nutshell.nutshell"
	;;
    java)
	NUTSHELL="java -cp $NUTSHELL_JAR nutshell.ProductServer"
	;;
    tomcat)
	# This may be same as above
	NUTSHELL="java -cp $HTTP_ROOT/WEB-INF/lib/Nutlet.jar nutshell.ProductServer"
	;;
    *)
	echo "NUTSHELL_VERSION must be 'java', 'tomcat' or 'python'"
	#NUTSHELL='echo'
	exit
	;;
esac     

if [ $# == 0 ]; then
    ${NUTSHELL} --help
else
    ${NUTSHELL} --log_level DEBUG --conf ${NUTSHELL_DIR}/nutshell.cnf $*
fi


