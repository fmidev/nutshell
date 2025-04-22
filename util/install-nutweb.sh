#!/bin/bash

TOMCAT=${TOMCAT:-'tomcat8'}

source util/vt100utils.sh
source util/utils.sh

vt100echo green,dim "This is $0"

CONF_FILE="nutweb-${HOSTNAME}.cnf"
read_config $CONF_FILE
# read_config nutweb-$HOSTNAME.cnf
# note: all vars exported

vt100echo green "# Creating HTML_ROOT=${HTML_ROOT}"
# mkdir -v --parents --mode gu+rwx $HTML_ROOT
mkdir -v --parents --mode g+rwx ${HTML_ROOT}

vt100echo green "# Creating \$HTML_ROOT/nutweb/ for templates etc."
# --parents means here: ok-if-exists-already
mkdir -v --parents --mode g+rwx ${HTML_ROOT}/nutweb 

# Fixed
NUTWEB_LIBDIR=${HTML_ROOT}/WEB-INF/lib
mkdir -v --parents --mode g+rwx $NUTWEB_LIBDIR/

# TODO: add to conf
TOMCAT=${TOMCAT:-'tomcat10'}

CATALINA_XML="${CATALINA_DIR}/$NAME.xml"
vt100echo green "# Writing top-level config file"
backup_file $CATALINA_XML '5' # rotate 5 backups

JAR_FILE=Nutlet.jar
case $TOMCAT in
    tomcat10)
	JAR_FILE=Nutlet10.jar
	cat nutweb/tpl/${TOMCAT}-nutweb.xml | envsubst > ${CATALINA_XML}
	;;
    tomcat8|tomcat9|tomcat10)
	cat nutweb/tpl/${TOMCAT}-nutweb.xml | envsubst > ${CATALINA_XML}
	;;
    *)
	vt100echo red "# Tomcat version not found: $TOMCAT"
	exit 2
esac    
critical_check 


vt100echo green "# Writing Deployment Descriptor: ${HTML_ROOT}/WEB-INF/web.xml"
backup_file ${HTML_ROOT}/WEB-INF/web.xml '10' # rotate 10 backups
cat nutweb/tpl/${TOMCAT}-web.xml | envsubst > ${HTML_ROOT}/WEB-INF/web.xml
critical_check "!!"

vt100echo green "# Copying JAR file: ${NUTWEB_LIBDIR}/$JAR_FILE" # consider
backup_file $NUTWEB_LIBDIR/$JAR_FILE '10' # rotate 10 backups
# '%1d'  # <- BACKUP INDEX (one digit)
# Consider rename, to separate WWW servlet NutWeb from NutShell?
cp -v html/WEB-INF/lib/$JAR_FILE $NUTWEB_LIBDIR/

vt100echo green "# Copying template file: ${HTML_TEMPLATE_SRC}" # consider
if [ -f ${HTML_TEMPLATE_SRC} ]; then
    backup_file ${HTML_ROOT}/nutweb/${HTML_TEMPLATE} '3'
    cat ${HTML_TEMPLATE_SRC} | envsubst > ${HTML_ROOT}/nutweb/${HTML_TEMPLATE}
else
    vt100echo yellow "# Template file not found: ${HTML_TEMPLATE_SRC}"
    vt100echo yellow "# Remember to put a HTML template file in ${HTML_ROOT}/nutweb/ "
fi

vt100echo green  "# Success"
vt100echo yellow "# TomCat restart required, for example: "
vt100echo cyan "systemctl restart ${TOMCAT}"


# 
# /var/lib/tomcat10/webapps/ROOT/index.html

exit 0

# These variables must be visible for `envsubst`
export USER HOSTNAME 
export NAME HTTP_PREFIX HTML_ROOT HTML_TEMPLATE










