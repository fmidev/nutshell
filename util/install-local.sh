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

export NUTSHELL_DIR # Python
export HTML_ROOT 
export CACHE_ROOT 


#SCRIPT=$0
DIR=${0%/*}
DIR=`realpath ${DIR}`

if [ -f ./nutshell.cnf ]; then
    source ./nutshell.cnf
else
    echo "# Initial conf file ./nutshell.cnf missing, run util/configure.sh first"
    exit 1
fi


export NUTSHELL_DEFAULT='java'
check_variable NUTSHELL_DEFAULT "$NUTSHELL_DEFAULT" "Default language version"

if [ $NUTSHELL_DEFAULT == 'java' ]; then
    echo '# NutShell JAVA version will serve from $NUTSHELL_DIR/WEB-INF/lib/Nutlet.jar .'
    echo '# This is compliant with Tomcat httpd, installation of which is however optional.'
    check_variable HTTP_ROOT $HTTP_ROOT  "Directory for nutshell.cnf and ./WEB_INF/lib/"
    ls -ltr $HTTP_ROOT/WEB-INF/lib/Nutlet.jar
    if [ $? != 0 ]; then
	echo "# Required JAR file not found. Run util/install-java.sh first."
	exit 1
    fi
else
    check_variable NUTSHELL_DIR ${DIR%/*}  "Directory containing nutshell.cnf and Python files ./nutshell/"
    if [ ! -e $NUTSHELL_DIR/nutshell/ ]; then
	echo "# NUTSHELL_DIR=$NUTSHELL_DIR invalid (contains no ./nutshell/ sub directory)"
	exit 1
    fi
fi






check_variable CMD_SCRIPT_DIR "/usr/local/bin" "Directory for the script "
eval BIN_DIR=$BIN_DIR
mkdir -v --parents --mode u+x  $CMD_SCRIPT_DIR/

CACHE_ROOT=${CACHE_ROOT:-$HTTP_ROOT/cache}
check_variable CACHE_ROOT "$CACHE_ROOT" "Root directory for produced files"




#echo "# Adding alias link 'nutshell/nutshell -> .', to comply with Python httpd"
#ln -s . /opt/nutshell/nutshell  #???

echo "# Creating/linking cache root"
mkdir -v --parents $CACHE_ROOT/
chmod -v    a+rwsx $CACHE_ROOT/

#NUTSHELL_SH=$BIN_DIR/nutshell.sh
NUTSHELL_SH=$BIN_DIR/nutshell

#cat > $NUTSHELL_SH <<EOF
##!/bin/bash
##export NUTSHELL_DIR=$NUTSHELL_DIR
#export PYTHONPATH=$PYTHONPATH:$NUTSHELL_DIR
#python3.6 -m nutshell.nutshell -c $NUTSHELL_DIR/nutshell.cnf \$*
#EOF


cat $DIR/nutshell.sh.tpl | envsubst '$NUTSHELL_DIR $NUTSHELL_DEFAULT $HTTP_ROOT' > $NUTSHELL_SH

chmod -v gu+x $NUTSHELL_SH

echo "# Installed $NUTSHELL_SH"
echo "# Test with: $NUTSHELL_SH --help"
