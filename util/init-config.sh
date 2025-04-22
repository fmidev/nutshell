#!/bin/bash
# OLI -e

# Markus.Peura@fmi.fi

# NOTE: this file should be source'd only.

export DATE=`date +'%Y-%m-%d %H:%M'`


source ./utils.sh

# Bash special
trap goodbye EXIT



#NUTSHELL_VERSION=${1:-'tomcat'}
NUTSHELL_VERSION=$1

if [ "$NUTSHELL_VERSION" == '' ]; then
    vt100echo green "Nutshell comes with three installation options:" 
    echo "  'python': Python3 version: command line and library interface (with optional simple WWW server)"
    echo "  'tomcat': Java version for Tomcat 7 or 8 WWW server, with cmd line interface"
    echo "  'java':   Java version with command line interface only"
    echo 
    vt100echo green "Select: "
    ask_variable NUTSHELL_VERSION  "tomcat" "NUTSHELL_VERSION (python|java|tomcat) "
fi

# CONF_FILE="install-$NUTSHELL_VERSION.cnf"
# CONF_FILE=${CONF_FILE:-'nutshell.cnf'}
CONF_FILE=${CONF_FILE:-"nutshell-$NUTSHELL_VERSION-$HOSTNAME.cnf"}






