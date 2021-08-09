#!/bin/bash

export NUTSHELL_DIR=$NUTSHELL_DIR
export PYTHONPATH=$PYTHONPATH:$NUTSHELL_DIR

python3.6 -m nutshell.nutshell--log_level DEBUG -c $NUTSHELL_DIR/nutshell.cnf \$*
