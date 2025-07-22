#!/bin/bash

# TOMCAT=${TOMCAT:-'tomcat8'}
# consider renaming  

source util/vt100utils.sh
source util/utils.sh

vt100echo green,dim "This is $0"

# For NutShell, use that of nutshell...
CONF_FILE=${CONF_FILE:-"nutweb-${HOSTNAME}.cnf"}
read_config $CONF_FILE

# read_config nutweb-$HOSTNAME.cnf
# note: all vars exported

vt100echo green "# Creating HTML_ROOT=${HTML_ROOT}"
mkdir -v --parents --mode g+rwx ${HTML_ROOT}

vt100echo green "# Creating \$HTML_ROOT/nutweb/ for templates etc."
mkdir -v --parents --mode g+rwx ${HTML_ROOT}/nutweb 

# Fixed
NUTWEB_LIBDIR=${HTML_ROOT}/WEB-INF/lib
mkdir -v --parents --mode g+rwx $NUTWEB_LIBDIR/

# TODO: add to conf
TOMCAT=${TOMCAT:-'tomcat10'}

vt100echo green "# Writing Deployment Descriptor"
SRC="nutweb/tpl/${TOMCAT}-nutweb.xml"
DST="${CATALINA_DIR}/$NAME.xml"
vt100echo green,dim "# ${SRC} -> ${DST}"
xmllint --noout "$SRC"
critical_check
backup_file $DST '5' # rotate 5 backups

JAR_FILE=Nutlet.jar
case $TOMCAT in
    tomcat10)
	JAR_FILE=Nutlet10.jar
	;;
    tomcat8|tomcat9|tomcat10)
	echo "?"
	;;
    *)
	vt100echo red "# Tomcat version not found: $TOMCAT"
	exit 2
esac
cat ${SRC} | envsubst > ${DST}
critical_check 


vt100echo green "# Writing web site configuration"
SRC="nutweb/tpl/${TOMCAT}-web.xml"
DST="${HTML_ROOT}/WEB-INF/web.xml"
vt100echo green,dim "# $SRC -> $DST"
xmllint --noout "$SRC"
critical_check
backup_file $DST '10' # rotate 10 backups
cat $SRC | envsubst > $DST
critical_check "!!"


vt100echo green "# Copying JAR file"
SRC=html/WEB-INF/lib/${JAR_FILE}
DST=${NUTWEB_LIBDIR}/${JAR_FILE}
vt100echo green,dim "# ${SRC} -> ${DST}"
backup_file $DST '10' # rotate 10 backups
# Consider rename, to separate WWW servlet NutWeb from NutShell?
cp -v $SRC $DST


vt100echo green "# Copying template file"
# Revised 22.7.: now src path imitates final one, like nutweb/template.html => ${HTML_ROOT}/nutweb/template.html
SRC=${HTML_TEMPLATE} 
DST=${HTML_ROOT}/${HTML_TEMPLATE}
# SRC=${HTML_TEMPLATE_SRC}
# DST=${HTML_ROOT}/nutweb/${HTML_TEMPLATE}
vt100echo green "# ${SRC} -> ${DST}"
if [ -f ${SRC} ]; then
    backup_file ${DST} '3'
    cat ${SRC} | envsubst > ${DST}
else
    if [ -f ${DST} ]; then
	vt100echo cyan   "# Ensure emember to put a HTML template file in ${DST}"
    else
	vt100echo yellow "# Source for template file not found: ${SRC}"	
    fi
fi

vt100echo green  "# Success"
vt100echo yellow "# TomCat restart required, for example: "
vt100echo cyan   "systemctl restart ${TOMCAT}"

vt100echo green,dim "End of $0"

# These variables must be visible for `envsubst`
# export USER HOSTNAME 
# export NAME HTTP_PREFIX HTML_ROOT HTML_TEMPLATE










