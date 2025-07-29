#!/bin/bash

NUTSHELL_VERSION=${1:-'tomcat10'}
ARG=$2

source util/vt100utils.sh
source util/init-config.sh

# vt100echo green "Main options"
# ask_variable NUTSHELL_VERSION  "tomcat" "NUTSHELL_VERSION (python|java|tomcat) "
CONF_FILE="nutshell-${NUTSHELL_VERSION}-${HOSTNAME}.cnf"
show_variable CONF_FILE

if [ -f $CONF_FILE ]; then
    #vt100echo green "Reading CONF_FILE=$CONF_FILE "
    vt100echo green "Reading conf file"
    source $CONF_FILE
else
    vt100echo yellow "Conf file does not exist"
    vt100echo red    "Run first: util/configure.sh $NUTSHELL_VERSION"
    exit 1
fi



vt100echo green "Checking Nutshell root directory"

if [ $NUTSHELL_VERSION == 'docker-java' ]; then
    NUTSHELL_ROOT="$PWD/docker"
    NUTSHELL_JAR_DIR=$NUTSHELL_ROOT
    CMD_SCRIPT_DIR=$NUTSHELL_ROOT
fi

if [ ! -v NUTSHELL_ROOT ]; then
    vt100echo red "NUTSHELL_ROOT undefined, exiting..."
    exit 2
fi


show_variable NUTSHELL_ROOT
if [ -d $NUTSHELL_ROOT ]; then
    ls -dl $NUTSHELL_ROOT
else
    mkdir -v --parent $NUTSHELL_ROOT
    if [ $? != 0 ]; then
	vt100echo red "Failed in creating NUTSHELL_ROOT=$NUTSHELL_ROOT"
	exit 3
    fi
fi

if [ ! -w $NUTSHELL_ROOT ]; then
    vt100echo red "Cannot write to NUTSHELL_ROOT=$NUTSHELL_ROOT"
    exit 4
fi

echo


# if [ "$CMD_SCRIPT_DIR" != '' ]; then
# fi

if [ $NUTSHELL_VERSION == 'docker-java' ]; then

    NUTSHELL_ROOT="/opt/nutshell"
    NUTSHELL_JAR_DIR=$NUTSHELL_ROOT

else
    
    prepare_dir $PRODUCT_ROOT products
    echo 

    prepare_dir $STORAGE_ROOT storage
    echo

    prepare_dir $CACHE_ROOT cache
    echo
    
fi


if [ "$ARG" == 'demo' ]; then
    vt100echo cyan  "# Install demo products (product generators) only"	
    # cp -vaux  demo/products/demo /opt/products/
    cp -vaux  demo/products/demo $PRODUCT_ROOT/
    exit $?
fi

# Taking back-up
backup_file ${NUTSHELL_ROOT}/nutshell-$NUTSHELL_VERSION.cnf 10 # rotate 10 backups
cp -v $CONF_FILE ${NUTSHELL_ROOT}/nutshell-$NUTSHELL_VERSION.cnf


#if [ $NUTSHELL_VERSION == 'tomcat' ]; then
if [ "$TOMCAT" != '' ]; then

    show_variable HTTP_ROOT
    
    util/install-nutweb.sh nutshell
    
    #for i in html/nutweb/*.HTML; do	
    #done
    PRODUCT_EXAMPLES="product-examples-$HOSTNAME.json"
    if [ -f $PRODUCT_EXAMPLES ]; then
	vt100echo cyan  "# Updating product menu from '$PRODUCT_EXAMPLES':"
	cp -viu ${PRODUCT_EXAMPLES} $HTTP_ROOT/product-examples.json
    fi

fi

#vt100echo green "# Setting WEB-INF/web.xml"



if [ $NUTSHELL_VERSION == 'java' ] || [ $NUTSHELL_VERSION == 'docker-java' ]; then

    #cp -v $CONF_FILE ${NUTSHELL_ROOT}/nutshell.cnf

    show_variable NUTSHELL_JAR_DIR
    mkdir -v --parents $NUTSHELL_JAR_DIR/
    backup_file $NUTSHELL_JAR_DIR/Nutlet.jar '10' # rotate 10 backups
    # '%1d'  # <- BACKUP INDEX (one digit)
    cp -v Nutlet10.jar $NUTSHELL_JAR_DIR/
    #NUTSHELL_CP=$NUTSHELL_JAR_DIR/Nutlet.jar
    
fi

if [ $NUTSHELL_VERSION == 'python' ]; then

    # This ensures python naming for nutshell.nutshell -> nutshell/nutshell.py
    mkdir -v --parents $NUTSHELL_ROOT/nutshell/
    cp -v python/*.py  $NUTSHELL_ROOT/nutshell/
    
    # unset NUTSHELL_JAR_DIR
    
fi


if [ "$CMD_SCRIPT_DIR" != '' ]; then

    NUTSHELL_SH=$CMD_SCRIPT_DIR/nutshell-${NUTSHELL_VERSION}
    vt100echo green "# Installing $NUTSHELL_SH"
    show_variable NUTSHELL_SH
    #DATE=`date --iso-8601=minutes`
    export USER HOSTNAME HTTP_ROOT NUTSHELL_VERSION NUTSHELL_ROOT NUTSHELL_JAR_DIR # GROUP_ID $GROUP_ID
    cat util/nutshell.sh.tpl | envsubst '$DATE $USER $HOSTNAME $HTTP_ROOT $NUTSHELL_VERSION $NUTSHELL_ROOT $NUTSHELL_JAR_DIR' > $NUTSHELL_SH
    if [ $? == 0 ]; then
	chmod -v gu+x $NUTSHELL_SH
	ln -svf $NUTSHELL_SH $CMD_SCRIPT_DIR/nutshell
	vt100echo cyan  "# Test with: $NUTSHELL_SH --help"
    else
	vt100echo red "# Failed in writing $NUTSHELL_SH"
    fi

else

    vt100echo cyan  "# CMD_SCRIPT_DIR undefined"
    vt100echo green "# Ok, no executable installed"
    
fi





vt100echo green "# Success"
