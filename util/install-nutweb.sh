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

SRC="nutweb/tpl/${TOMCAT}-nutweb.xml"
DST="${CATALINA_DIR}/$NAME.xml"
vt100echo green "# Writing Deployment Descriptor: ${SRC} -> ${DST}"
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


SRC="nutweb/tpl/${TOMCAT}-web.xml"
DST="${HTML_ROOT}/WEB-INF/web.xml"
vt100echo green "# Writing web site conf: $SRC -> $DST"
xmllint --noout "$SRC"
critical_check
backup_file $DST '10' # rotate 10 backups
cat $SRC | envsubst > $DST
critical_check "!!"

SRC=html/WEB-INF/lib/${JAR_FILE}
DST=${NUTWEB_LIBDIR}/${JAR_FILE}
vt100echo green "# Copying JAR file: ${SRC} => ${DST}" # consider
backup_file $DST '10' # rotate 10 backups
# '%1d'  # <- BACKUP INDEX (one digit)
# Consider rename, to separate WWW servlet NutWeb from NutShell?
cp -v $SRC $DST

SRC=${HTML_TEMPLATE_SRC}
DST=${HTML_ROOT}/nutweb/${HTML_TEMPLATE}
vt100echo green "# Copying template file: ${SRC} -> ${DST}" # consider
if [ -f ${SRC} ]; then
    backup_file ${DST} '3'
    cat ${SRC} | envsubst > ${DST}
else
    vt100echo yellow "# Template file not found: ${SRC}"
    #vt100echo yellow "# Remember to put a HTML template file in ${HTML_ROOT}/nutweb/ "
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










