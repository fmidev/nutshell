#!/bin/bash

NUTSHELL_VERSION=${1:-'tomcat'}

source util/vt100utils.sh
source util/init-config.sh


#vt100echo green "Main options"
#ask_variable NUTSHELL_VERSION  "tomcat" "NUTSHELL_VERSION (python|java|tomcat) "
#CONF_FILE="nutshell-$NUTSHELL_VERSION.cnf"

if [ -f $CONF_FILE ]; then
    #vt100echo green "Reading CONF_FILE=$CONF_FILE "
    vt100echo green "Reading conf file"
    show_variable CONF_FILE
    source $CONF_FILE
    show_variable CONF_FILE
else
    vt100echo yellow "Conf file does not exist"
    show_variable CONF_FILE
    vt100echo yellow "Run first: util/configure.sh $NUTSHELL_VERSION"
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
fi
echo

backup_file ${NUTSHELL_ROOT}/nutshell.cnf
cp -v $CONF_FILE ${NUTSHELL_ROOT}/nutshell.cnf




# if [ "$CMD_SCRIPT_DIR" != '' ]; then
# fi

if [ $NUTSHELL_VERSION != 'docker-java' ]; then

    prepare_dir $PRODUCT_ROOT products
    echo 

    prepare_dir $STORAGE_ROOT storage
    echo

    prepare_dir $CACHE_ROOT cache
    echo
else
    
    NUTSHELL_ROOT="/opt/nutshell"
    NUTSHELL_JAR_DIR=$NUTSHELL_ROOT
    
fi


if [ $NUTSHELL_VERSION == 'tomcat' ]; then

    show_variable HTTP_ROOT
    mkdir -v --parents $HTTP_ROOT
    #mkdir -v --parents $HTTP_ROOT/template # OBSOLETE!
    mkdir -v --parents $HTTP_ROOT/nutweb

    for i in html/*.{css,js}; do
	cp -v $i $HTTP_ROOT/
    done

    vt100echo cyan  "# Copying HTML files"	
    export HTTP_PREFIX  # Others?
    for i in html/nutweb/*.html; do
	#DIR=$HTTP_ROOT/
	vt100echo cyan  "# ... $i"	
	if [ ${i##*/} == 'template.html' ]; then
	    cat $i | envsubst > $HTTP_ROOT/nutweb/${i##*/}
	    #cp -v $i $HTTP_ROOT/nutweb/
	else
	    cat $i | envsubst > $HTTP_ROOT/${i##*/}
	    #cp -v $i $HTTP_ROOT/
	fi
    done

    vt100echo cyan  "# Copying images"	
    cp -v html/favicon.ico $HTTP_ROOT/
    mkdir -v --parents $HTTP_ROOT/img
    cp -v html/img/* $HTTP_ROOT/img
    
    #for i in html/nutweb/*.HTML; do	
    #done

    
    NUTSHELL_JAR_DIR=${NUTSHELL_JAR_DIR:-"$HTTP_ROOT/WEB-INF/lib"}

fi

#vt100echo green "# Setting WEB-INF/web.xml"

if [ $NUTSHELL_VERSION == 'python' ]; then

    # This ensures python naming for nutshell.nutshell -> nutshell/nutshell.py
    mkdir -v --parents $NUTSHELL_ROOT/nutshell/
    cp -v python/*.py  $NUTSHELL_ROOT/nutshell/
    
    # unset NUTSHELL_JAR_DIR
    
fi


if [ $NUTSHELL_VERSION == 'java' ] || [ $NUTSHELL_VERSION == 'tomcat' ] || [ $NUTSHELL_VERSION == 'docker-java' ]; then

    #cp -v $CONF_FILE ${NUTSHELL_ROOT}/nutshell.cnf

    show_variable NUTSHELL_JAR_DIR
    mkdir -v --parents $NUTSHELL_JAR_DIR/
    cp -v html/WEB-INF/lib/Nutlet.jar $NUTSHELL_JAR_DIR/
    #NUTSHELL_CP=$NUTSHELL_JAR_DIR/Nutlet.jar
    
fi

if [ $NUTSHELL_VERSION == 'tomcat' ]; then

    vt100echo green "# Setting WEB-INF/web.xml"
    WEB_XML=$HTTP_ROOT/WEB-INF/web.xml
    export NUTSHELL_ROOT
    export HTTP_ROOT
    export HOSTNAME    
    cat html/WEB-INF/web.xml.tpl | envsubst > $WEB_XML.new

    show_variable WEB_XML
    if [ -f $WEB_XML ]; then
	#cp -va $WEB_XML{,.old}
	backup_file $WEB_XML
	cp -v $WEB_XML{,.old}
	pushd $HTTP_ROOT/WEB-INF/ &> /dev/null
	diff web.xml.old web.xml.new
	if [ $? != 0 ]; then
	    vt100echo yellow "Notice above changes in $WEB_XML"
	fi
	#mv -v web.xml.new web.xml
	popd &> /dev/null
	mv -v $WEB_XML.new $WEB_XML
    else
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
    if [ -w $NUTSHELL_XML ]; then
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

    vt100echo green "OK! Now restart Tomcat. E.g. using one of the following commands: "
    echo "#   sudo /etc/init.d/tomcat8 restart"
    echo "#   sudo systemctl restart httpd"
    echo "#   sudo systemctl restart tomcat9"
    echo
    #echo "# For local (script) installation: "
    #echo "#   [sudo] NUTSHELL_VERSION=java util/install-local.sh"
    
fi

if [ "$CMD_SCRIPT_DIR" != '' ]; then

    NUTSHELL_SH=$CMD_SCRIPT_DIR/nutshell
    show_variable NUTSHELL_SH
    #DATE=`date --iso-8601=minutes`
    export USER HOSTNAME HTTP_ROOT NUTSHELL_VERSION NUTSHELL_ROOT NUTSHELL_JAR_DIR
    cat util/nutshell.sh.tpl | envsubst '$DATE $USER $HOSTNAME $HTTP_ROOT $NUTSHELL_VERSION $NUTSHELL_ROOT $NUTSHELL_JAR_DIR' > $NUTSHELL_SH
    if [ $? == 0 ]; then
	chmod -v gu+x $NUTSHELL_SH
	vt100echo green "# Installed $NUTSHELL_SH"
	vt100echo cyan  "# Test with: $NUTSHELL_SH --help"
    else
	vt100echo red "# Failed in writing $NUTSHELL_SH"
    fi

else

    vt100echo cyan  "# CMD_SCRIPT_DIR undefined"
    vt100echo green "# Ok, no executable installed"
    
fi





vt100echo green "# Success"
