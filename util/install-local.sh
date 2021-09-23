#!/bin/bash

#set -e

function goodbye {
    echo "# Exiting installation"
}
trap goodbye EXIT


function check_variable(){
    local var_name=$1
    local def_value=$2
    shift 2
    if [ -v $var_name ]; then
	eval "echo $var_name=\$$var_name"
    else
	echo "$*"
	read -e  -i "$def_value" -p "$var_name=" $var_name
    fi
}

export CACHE_ROOT 
export NUTSHELL_DIR

#SCRIPT=$0
DIR=${0%/*}
DIR=`realpath ${DIR}`
#NUTSHELL_DIR=${NUTSHELL_DIR:-${CUR_DIR%/*}}
check_variable NUTSHELL_DIR ${DIR%/*}  "Directory containing nutshell.cnf and ./nutshell/*.py"

if [ ! -e $NUTSHELL_DIR/nutshell/ ]; then
    echo "# NUTSHELL_DIR=$NUTSHELL_DIR invalid (contains no ./nutshell/ sub directory)"
    exit 1
fi

if [ -f $NUTSHELL_DIR/nutshell.cnf ]; then
    source $NUTSHELL_DIR/nutshell.cnf
else
    echo "# Main conf file $NUTSHELL_DIR/nutshell.cnf missing, run util/configure.sh first"
    exit 1
fi


check_variable BIN_DIR "/usr/local/bin" "Directory for the script "
eval BIN_DIR=$BIN_DIR
mkdir -v --parents --mode u+x  $BIN_DIR/

CACHE_ROOT=${CACHE_ROOT:-$HTML_ROOT/cache}
check_variable CACHE_ROOT "$CACHE_ROOT" "Give product file cache root"


#echo "# Adding alias link 'nutshell/nutshell -> .', to comply with Python httpd"
#ln -s . /opt/nutshell/nutshell  #???

echo "# Creating/linking cache root"
mkdir -v --parents $CACHE_ROOT/
chmod -v    a+rwsx $CACHE_ROOT/

NUTSHELL_SH=$BIN_DIR/nutshell.sh

#cat > $NUTSHELL_SH <<EOF
##!/bin/bash
##export NUTSHELL_DIR=$NUTSHELL_DIR
#export PYTHONPATH=$PYTHONPATH:$NUTSHELL_DIR
#python3.6 -m nutshell.nutshell -c $NUTSHELL_DIR/nutshell.cnf \$*
#EOF

cat $DIR/nutshell.sh.tpl | envsubst '$NUTSHELL_DIR' > $NUTSHELL_SH

chmod -v gu+x $NUTSHELL_SH
echo "# Installed $NUTSHELL_SH"
