#!/bin/bash
# -e

# Markus.Peura@fmi.fi


source util/vt100utils.sh
source util/utils.sh

vt100echo green,dim "This is $0"

CONF_NAME=${CONF_NAME:-$1}
CONF_NAME=${CONF_NAME:-'nutweb'}

CONF_FILE="${CONF_NAME}-${HOSTNAME}.cnf"

read_config $CONF_FILE 
backup_file $CONF_FILE 

echo "# Conf by $USER@$HOSTNAME on $DATE " > $CONF_FILE
echo >> $CONF_FILE

# HTTP_PORT=${HTTP_PORT:-'8000'}
ask_variable NUTWEB_NAME $CONF_NAME  "Name (label) for this installation"
NAME_SAFE=${NUTWEB_NAME//[^a-zA-Z0-9_\-]/}
if [ $NAME_SAFE != "$NUTWEB_NAME" ]; then
    vt100echo yellow "Warning: avoid spaces and special chars in name: $NUTWEB_NAME -> $NAME_SAFE"
    NUTWEB_NAME="$NAME_SAFE"
    ask_variable NUTWEB_NAME "$NAME_SAFE"  "Name (label) for this installation"
fi

# ask_variable TOMCAT 'tomcat10' "TomCat version"

source util/configure-www.sh

vt100echo green "Wrote: $CONF_FILE"
vt100echo cyan  "Proceed with: util/install-nutweb.sh $CONF_NAME"
echo

