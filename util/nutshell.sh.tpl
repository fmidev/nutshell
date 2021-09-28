#!/bin/bash

# NutShelll wrapper script
#
# Markus.Peura@fmi.fi
#

export NUTSHELL_DIR=$NUTSHELL_DIR
export PYTHONPATH=$PYTHONPATH:$NUTSHELL_DIR

NUTSHELL=${NUTSHELL:-'$NUTSHELL_DEFAULT'}

case $NUTSHELL in
    python)
	NUTSHELL="python3.6 -m nutshell.nutshell --log_level DEBUG"
	;;
    java)
	NUTSHELL="java -cp Nutlet.jar nutshell.ProductServer --log_level DEBUG"
	;;
    *)
	#echo "NUTSHELL must be 'java' or 'python'"
	;;
esac     

if [ $# == 0 ]; then
    ${NUTSHELL} --help
else
    ${NUTSHELL} --conf $NUTSHELL_DIR/nutshell.cnf $*
fi


