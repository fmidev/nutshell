#!/bin/bash

# set -e

function goodbye {
    echo "# Exiting installation"
    echo
    echo "# Tomcat users, consider restart: "
    echo "#   sudo /etc/init.d/tomcat8 restart"
    echo "#   sudo systemctl restart httpd"
    echo
    echo "# For local (script) installation: "
    echo "#   [sudo] NUTSHELL_VERSION=java util/install-local.sh"
}
trap goodbye EXIT

trap "#echo Undefined error" ILL



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

export HTTP_PREFIX HTTP_ROOT CACHE_ROOT NUTSHELL_CONF_DIR TOMCAT_CONF_DIR 
if [ -e ./nutshell.cnf ]; then
    echo "# Using ./nutshell.cnf as basis"
    source ./nutshell.cnf
    echo
else
    echo "# Main conf file ./nutshell.cnf missing, run util/configure.sh first"
    exit 1
fi

answer='y'
read -e  -i "$answer" -p "Install NutShell (NutLet.jar) under $HTTP_ROOT [Y/n]? " answer
echo

if [ ${answer,} != 'y' ]; then
    echo "# Installation quitted"
    exit 0
fi



check_variable HTTP_PREFIX "/nutshell" "Give prefix for all HTTP requests for NutShell"

check_variable HTTP_ROOT "/opt/nutshell" "Give HTTP servlet installation root"

#CACHE_ROOT=${CACHE_ROOT:-$HTTP_ROOT/cache}
#check_variable CACHE_ROOT "$CACHE_ROOT" "Give product file cache root"

TOMCAT_CONF_DIR=${TOMCAT_CONF_DIR:-/etc/tomcat8/Catalina/localhost}
check_variable TOMCAT_CONF_DIR "$TOMCAT_CONF_DIR" "Tomcat8 Catalina dir for nutshell.xml"
NUTSHELL_XML=$TOMCAT_CONF_DIR/nutshell.xml


mkdir -v --parents $HTTP_ROOT
mkdir -v --parents $HTTP_ROOT/template
mkdir -v --parents $HTTP_ROOT/img
mkdir -v --parents $HTTP_ROOT/WEB-INF
mkdir -v --parents $HTTP_ROOT/WEB-INF/lib
#cp -vi {./tomcat8,$HTTP_ROOT}/WEB-INF/lib/Nutlet.jar
cp -v ./nutshell.cnf ${HTTP_ROOT}/
echo
#unset -e


echo "# Setting WEB-INF/web.xml"
export DATE=`date +'%Y-%m-%d %H:%M'`
WEB_XML=$HTTP_ROOT/WEB-INF/web.xml
cat html/WEB-INF/web.xml.tpl | envsubst > $WEB_XML.new
if [ -f $WEB_XML ]; then
    #cp -va $WEB_XML{,.old}
    cp -v $WEB_XML{,.old}
    pushd $HTTP_ROOT/WEB-INF/ &> /dev/null
    diff web.xml.old web.xml.new
    if [ $? != 0 ]; then
	echo "Notice above changes in $WEB_XML"
    fi
    #mv -v web.xml.new web.xml
    popd &> /dev/null
    mv -v $WEB_XML.new $WEB_XML
fi

echo

echo "# Updating HTTP structure $HTTP_ROOT"
# rsync -vau --exclude '*~' --exclude '*.HTML html/  $HTTP_ROOT/
pushd html &> /dev/null
cp -vau favicon.ico nutshell.css $HTTP_ROOT/
cp -vau img/*.png $HTTP_ROOT/img/
cp -vau template/*.html $HTTP_ROOT/template/
ln -vfs template/main.html $HTTP_ROOT/index.html
cp -vau {.,$HTTP_ROOT}/WEB-INF/web.xml
cp -vau {.,$HTTP_ROOT}/WEB-INF/lib/Nutlet.jar
popd &> /dev/null
echo

#echo "# Adding alias link 'nutshell/nutshell -> .', for optional Python httpd"
#ln -s . /opt/nutshell/nutshell  #???
#echo

echo "# Creating/linking cache root"
mkdir -v --parents $CACHE_ROOT/
chmod -v    a+rwsx $CACHE_ROOT/
echo

echo "# Linking CACHE_ROOT under HTTP_ROOT"
CACHE_LINK=$HTTP_ROOT/cache
if [ -e $CACHE_LINK ]; then
    echo "# Ok, exists: $CACHE_LINK"
else
    ln -sv $CACHE_ROOT $CACHE_LINK
fi
echo

echo "# Optional: Linking PRODUCT_ROOT under HTTP_ROOT"
PRODUCT_LINK=$HTTP_ROOT/products
if [ -e $PRODUCT_LINK ]; then
    echo "# Ok, exists: $PRODUCT_LINK"
else
    ln -sv $PRODUCT_ROOT $PRODUCT_LINK
fi
echo

if [ -w $NUTSHELL_XML/ ]; then
    cat ./html/nutshell.xml.tpl | envsubst > $NUTSHELL_XML
else
    NUTSHELL_XML_NEW=./html/nutshell.xml.new
    echo "# WARNING: cannot write directly to: $NUTSHELL_XML"
    echo "# WARNING: writing a draft to:   $NUTSHELL_XML_NEW"
    echo "# Consider: diff $NUTSHELL_XML  $NUTSHELL_XML_NEW"
    NUTSHELL_XML=$NUTSHELL_XML_NEW
fi
echo

#echo "consider: sudo /etc/init.d/tomcat8  restart"
