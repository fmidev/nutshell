#!/bin/bash

set -e

function goodbye {
    echo "consider: sudo /etc/init.d/tomcat8  restart"
    echo "# Exiting installation"
}
trap goodbye EXIT

trap "echo virhe" ILL

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

export HTTP_PREFIX HTML_ROOT CACHE_ROOT TOMCAT_CONF_DIR 
if [ -e ./nutshell.cnf ]; then
    source ./nutshell.cnf
fi


check_variable HTTP_PREFIX "/nutshell" "Give prefix for all HTTP requests for NutShell"

check_variable HTML_ROOT "/opt/nutshell" "Give HTML servlet installation root"

CACHE_ROOT=${CACHE_ROOT:-$HTML_ROOT/cache}
check_variable CACHE_ROOT "$CACHE_ROOT" "Give product file cache root"

TOMCAT_CONF_DIR=${TOMCAT_CONF_DIR:-/etc/tomcat8/Catalina/localhost}
check_variable TOMCAT_CONF_DIR "$TOMCAT_CONF_DIR" "Tomcat8 Catalina dir for nutshell.xml"
NUTSHELL_XML=$TOMCAT_CONF_DIR/nutshell.xml


mkdir -v --parents $HTML_ROOT
mkdir -v --parents $HTML_ROOT/WEB-INF
mkdir -v --parents $HTML_ROOT/WEB-INF/lib
cp -vi {./tomcat8,$HTML_ROOT}/WEB-INF/lib/Nutlet.jar

WEB_XML=$HTML_ROOT/WEB-INF/web.xml
cp -v $WEB_XML{,.bak} 
cat ./tomcat8/WEB-INF/web.xml.tpl | envsubst > $WEB_XML


pushd $HTML_ROOT/WEB-INF &> /dev/null
diff web.xml.bak web.xml
if [ $? != 0 ]; then
    echo "Notice above changes in $HTML_ROOT/WEB-INF/web.xml"
fi
popd &> /dev/null

echo "# Updating HTML structure"
rsync -vau --exclude '*~' --exclude '*.HTML' html/  $HTML_ROOT/
#pushd ./html &> /dev/null
#cp -vaux . $HTML_ROOT/
#mkdir --parents  $HTML_ROOT/template
#cp -vau template/*.html $HTML_ROOT/template/
#popd &> /dev/null

echo "# Adding alias link 'nutshell/nutshell -> .', to comply with Python httpd"
ln -s . /opt/nutshell/nutshell

echo "# Creating/linking cache root"
mkdir -v --parents $CACHE_ROOT/
chmod -v    a+rwsx $CACHE_ROOT/

echo "# Linking CACHE_ROOT under HTML_ROOT"
CACHE_LINK=$HTML_ROOT/cache
if [ -e $CACHE_LINK ]; then
    echo "# Ok, exists: $CACHE_LINK"
else
    ln -sv $CACHE_ROOT $CACHE_LINK
fi

echo "# Optional: Linking PRODUCT_ROOT under HTML_ROOT"
PRODUCT_LINK=$HTML_ROOT/products
if [ -e $PRODUCT_LINK ]; then
    echo "# Ok, exists: $PRODUCT_LINK"
else
    ln -sv $PRODUCT_ROOT $PRODUCT_LINK
fi

#if [ ! -f $TOMCAT_CONF_DIR/ ]; then
if [ ! -w $NUTSHELL_XML/ ]; then
    NUTSHELL_XML_NEW=./tomcat8/nutshell.xml.new
    echo "# WARNING: cannot write: $NUTSHELL_XML"
    echo "# WARNING: writing to:   $NUTSHELL_XML_NEW"
    echo "# Consider: diff $NUTSHELL_XML  $NUTSHELL_XML_NEW"
    NUTSHELL_XML=$NUTSHELL_XML_NEW
fi

cat ./tomcat8/nutshell.xml.tpl | envsubst > $NUTSHELL_XML

#echo "consider: sudo /etc/init.d/tomcat8  restart"
