#!/bin/bash

export NUTSHELL_DIR=$NUTSHELL_DIR
export PYTHONPATH=$PYTHONPATH:$NUTSHELL_DIR

NUTSHELL=${NUTSHELL:-'java'}

case $NUTSHELL in
    python)
	NUTSHELL="python3.6 -m nutshell.nutshell"
	;;
    java)
	NUTSHELL="java -cp Nutlet.jar nutshell.ProductServer"
	;;
    *)
	#echo "NUTSHELL must be 'java' or 'python'"
	;;
esac     


${NUTSHELL} --log_level DEBUG --conf $NUTSHELL_DIR/nutshell.cnf $*
