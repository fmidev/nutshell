#!/bin/bash

# NutShell wrapper script
#
# Markus.Peura@fmi.fi
#

export NUTSHELL_DIR
export PYTHONPATH=$PYTHONPATH:$NUTSHELL_DIR
export HTML_ROOT=$HTML_ROOT

NUTSHELL=${NUTSHELL:-'$NUTSHELL_DEFAULT'}

case $NUTSHELL in
    python)
	NUTSHELL_DIR=${NUTSHELL_DIR:-$NUTSHELL_DIR}
	NUTSHELL="python3.6 -m nutshell.nutshell"
	;;
    java)
	NUTSHELL_DIR=${NUTSHELL_DIR:-$HTML_ROOT}
	NUTSHELL="java -cp $HTML_ROOT/WEB-INF/lib/Nutlet.jar nutshell.ProductServer"
	;;
    *)
	#echo "NUTSHELL must be 'java' or 'python'"
	;;
esac     

if [ $# == 0 ]; then
    ${NUTSHELL} --help
else
    ${NUTSHELL} --log_level DEBUG --conf ${NUTSHELL_DIR:-'.'}/nutshell.cnf $*
fi


