#!/bin/bash

#if [ $# == 0 ]; then
#    echo "NutWeb installation script"
    # Markus Peura fmi.fi
 #   echo "Usage: $0 <conf-name> [<conf-file> <conf-file2> ...]"
#    exit 0
#fi


source util/vt100utils.sh
source util/utils.sh

vt100echo green,dim "# This is $0"
vt100echo green "# NutWeb installation script"

vt100echo green "# Reading conf file(s)"
# Default: NutWeb. For NutShell, use that of nutshell.
CONF_NAME=${CONF_NAME:-$1}
CONF_NAME=${CONF_NAME:-'nutweb'}
shift
CONF_FILE=${CONF_FILE:-"${CONF_NAME}-${HOSTNAME}.cnf"} # tomcat version?

read_config $CONF_FILE
#for i in $* $CONF_FILE; do
#    read_config $i
#done
# set
# exit 1
echo

DIR_START="www/$CONF_NAME"
vt100echo green "# Source: ${DIR_START}"
if [ ! -d "$DIR_START" ]; then
    vt100echo red "# Directory '${DIR_START}' does not exist - no installation candiates"
    exit 1
fi


# TODO: add to conf
TOMCAT=${TOMCAT:-'tomcat10'}

source util/install-www.sh



vt100echo green  "# Success"
vt100echo yellow "# TomCat restart required, for example: "
vt100echo cyan   "systemctl restart ${TOMCAT}"

vt100echo green,dim "End of $0"

# These variables must be visible for `envsubst`
# export USER HOSTNAME 
# export NAME HTTP_PREFIX HTML_ROOT HTML_TEMPLATE










