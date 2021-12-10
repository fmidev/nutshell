#!/bin/bash

NUTSHELL_VERSION=${1:-'tomcat'}

source util/vt100utils.sh
source util/config-init.sh

show_variable CONF_FILE

#vt100echo green "Main options"
#ask_variable NUTSHELL_VERSION  "tomcat" "NUTSHELL_VERSION (python|java|tomcat) "
#CONF_FILE="nutshell-$NUTSHELL_VERSION.cnf"



if [ -f $CONF_FILE ]; then
    vt100echo green "Reading CONF_FILE=$CONF_FILE "
    source $CONF_FILE
    show_variable TOMCAT_CONF_DIR
else
    vt100echo yellow "CONF_FILE=$CONF_FILE does not exist"
    CONF_SCRIPT="util/configure-$NUTSHELL_VERSION.sh"
    if [ -x $CONF_SCRIPT ]; then
	$CONF_SCRIPT
    else
	vt100echo red "CONF_SCRIPT=$CONF_SCRIPT does not exist"
	exit 1
    fi
fi


vt100echo green "Checking Nutshell root directory"

if [ ! -v NUTSHELL_ROOT ]; then
    vt100echo red "NUTSHELL_ROOT undefined, exiting..."
    exit 2
fi

show_variable NUTSHELL_ROOT
if [ -d $NUTSHELL_ROOT ]; then
    ls -dl $NUTSHELL_ROOT
else
    mkdir -v --parent $NUTSHELL_CONF_DIR
fi
echo



prepare_dir $PRODUCT_ROOT products
echo 

prepare_dir $STORAGE_ROOT storage
echo

prepare_dir $CACHE_ROOT cache
echo 
    

if [ $NUTSHELL_VERSION == 'tomcat' ]; then

    show_variable HTTP_ROOT
    mkdir -v --parents $HTTP_ROOT
    mkdir -v --parents $HTTP_ROOT/template
    mkdir -v --parents $HTTP_ROOT/img

    NUTSHELL_JAR_DIR=$HTTP_ROOT/WEB-INF/lib

    #mkdir -v --parents $HTTP_ROOT/WEB-INF
    #mkdir -v --parents $HTTP_ROOT/WEB-INF/lib
    #cp -vi {./tomcat8,$HTTP_ROOT}/WEB-INF/lib/Nutlet.jar
    #cp -v ./nutshell.cnf ${HTTP_ROOT}/
    
    # cp -v $CONF_FILE ${HTTP_ROOT}/nutshell.cnf
    #fi
    #if [ $NUTSHELL_VERSION == 'java' ]; then
else
    NUTSHELL_JAR_DIR=$NUTSHELL_ROOT/jar

    

fi

if [ $NUTSHELL_VERSION == 'java' ] || [ $NUTSHELL_VERSION == 'tomcat' ]; then

    cp -v $CONF_FILE ${NUTSHELL_ROOT}/nutshell.cnf

    show_variable NUTSHELL_JAR_DIR
    mkdir -v --parents $NUTSHELL_JAR_DIR/
    cp -vi html/WEB-INF/lib/Nutlet.jar $NUTSHELL_JAR_DIR/
    #NUTSHELL_CP=$NUTSHELL_JAR_DIR/Nutlet.jar
    
fi

if [ $NUTSHELL_VERSION == 'tomcat' ]; then

    vt100echo green "# Setting WEB-INF/web.xml"
    export DATE=`date +'%Y-%m-%d %H:%M'`
    WEB_XML=$HTTP_ROOT/WEB-INF/web.xml
    cat html/WEB-INF/web.xml.tpl | envsubst > $WEB_XML.new

    show_variable WEB_XML
    if [ -f $WEB_XML ]; then
	#cp -va $WEB_XML{,.old}
	cp -v $WEB_XML{,.old}
	pushd $HTTP_ROOT/WEB-INF/ &> /dev/null
	diff web.xml.old web.xml.new
	if [ $? != 0 ]; then
	    vt100echo yellow "Notice above changes in $WEB_XML"
	fi
	#mv -v web.xml.new web.xml
	popd &> /dev/null
	mv -v $WEB_XML.new $WEB_XML
    fi

    #<!-- Tomcat 7: -->
    #<Context allowLinking="true" />

    #<!-- Tomcat 8: -->
    #<Context>
    #<Resources allowLinking="true" />
    #</Context>
    show_variable TOMCAT_CONF_DIR
   
    NUTSHELL_XML=$TOMCAT_CONF_DIR/nutshell.xml
    show_variable NUTSHELL_XML
    if [ -w $NUTSHELL_XML/ ]; then
	cat ./html/nutshell.xml.tpl | envsubst > $NUTSHELL_XML
    else
	NUTSHELL_XML_NEW=./html/nutshell.xml.new
	vt100echo yellow "# WARNING: cannot write directly to: $NUTSHELL_XML"
	ls -ld  ${NUTSHELL_XML%/*}
	ls -l   $NUTSHELL_XML
	vt100echo yellow "# WARNING: writing a draft to:   $NUTSHELL_XML_NEW"
	vt100echo yellow "# Consider: diff $NUTSHELL_XML  $NUTSHELL_XML_NEW"
	NUTSHELL_XML=$NUTSHELL_XML_NEW
    fi

    vt100echo green "Try Tomcat restart: "
    echo "#   sudo /etc/init.d/tomcat8 restart"
    echo "#   sudo systemctl restart httpd"
    echo
    #echo "# For local (script) installation: "
    #echo "#   [sudo] NUTSHELL_VERSION=java util/install-local.sh"
    
fi



NUTSHELL_SH=$CMD_SCRIPT_DIR/nutshell
show_variable NUTSHELL_SH
export HTTP_ROOT NUTSHELL_VERSION NUTSHELL_ROOT NUTSHELL_JAR_DIR
cat util/nutshell.sh.tpl | envsubst '$HTTP_ROOT $NUTSHELL_VERSION $NUTSHELL_ROOT $NUTSHELL_JAR_DIR' > $NUTSHELL_SH
if [ $? == 0 ]; then
    chmod -v gu+x $NUTSHELL_SH
    vt100echo green "# Installed $NUTSHELL_SH"
    vt100echo cyan  "# Test with: $NUTSHELL_SH --help"
else
    vt100echo red "# Failed in writing $NUTSHELL_SH"
fi





vt100echo green "# Success"