#!/bin/bash

source util/vt100utils.sh
source util/init-config.sh

vt100echo green "Hello! This is $0"

TOMCAT=${TOMCAT:-'TOMCAT8'}

# These variables must be visible for `envsubst`
export USER HOSTNAME 
export NAME HTTP_PREFIX HTML_ROOT HTML_TEMPLATE


# Directory where simple dir mapping rules like mypages.xml will be written
CATALINA_ROOT="/etc/tomcat9/Catalina/localhost"

NAME='kokkeilu'

# Appearing in URL, starting the server path
HTTP_PREFIX="/$NAME"

# Comma-separated prefixes bypassing NutWeb and directed to default HTML handler
HTTP_EXCLUDES="/raw,/data"

# 
HTML_ROOT="/opt/$NAME"
# warn if not containing $HTTP_PREFIX
# chown -vR --changes .tomcat .


HTML_TEMPLATE="nutweb/tpl/template.html"
# Store full path of the source
HTML_TEMPLATE_SRC="$HTML_TEMPLATE"
# Leave basename
HTML_TEMPLATE=${HTML_TEMPLATE##*/}


vt100echo green "# Creating HTML_ROOT=${HTML_ROOT}"
# mkdir -v --parents --mode gu+rwx $HTML_ROOT
mkdir -v --parents --mode g+rwx ${HTML_ROOT}

vt100echo green "# Creating \$HTML_ROOT/nutweb/ for templates etc."
mkdir -v --mode g+rwx ${HTML_ROOT}/nutweb 

# Fixed
NUTWEB_LIBDIR=${HTML_ROOT}/WEB-INF/lib
mkdir -v --parents --mode g+rwx $NUTWEB_LIBDIR/



case $TOMCAT in
    # TOMCAT6)
    # backup!
    # cat nutweb/tpl/tomcat6-nutweb.xml | envsubst > ${CATALINA_ROOT}/$NAME.xml
    # ;;
    TOMCAT8|TOMCAT9)
	# mkdir -v ${CATALINA_ROOT}/$NAME
	# backup! 
	# envsubst html/nutweb.xml.tpl > ${CATALINA_ROOT}/$NAME/$NAME.xml
	cat nutweb/tpl/tomcat8-nutweb.xml | envsubst > ${CATALINA_ROOT}/$NAME.xml
	;;
    *)
	echo "# Tomcat version not found: $TOMCAT"
	exit 2
esac    


vt100echo green "# Setting up: ${HTML_ROOT}/WEB-INF/web.xml"
backup_file ${HTML_ROOT}/WEB-INF/web.xml '10' # rotate 10 backups
cat nutweb/tpl/web.xml | envsubst > ${HTML_ROOT}/WEB-INF/web.xml

vt100echo green "# Copying JAR file: ${NUTWEB_LIBDIR}/Nutlet.jar" # consider
backup_file $NUTWEB_LIBDIR/Nutlet.jar '10' # rotate 10 backups
# '%1d'  # <- BACKUP INDEX (one digit)
# Consider rename, to separate WWW servlet NutWeb from NutShell?
cp -v html/WEB-INF/lib/Nutlet.jar $NUTWEB_LIBDIR/

vt100echo green "# Copying template file: ${HTML_TEMPLATE}" # consider
if [ -f ${HTML_TEMPLATE_SRC} ]; then
    backup_file ${HTML_ROOT}/nutweb/${HTML_TEMPLATE} '3'
    cat ${HTML_TEMPLATE_SRC} | envsubst > ${HTML_ROOT}/nutweb/${HTML_TEMPLATE}
else
    vt100echo yellow "# Template file not found: ${HTML_TEMPLATE_SRC}"
    vt100echo yellow "# Remember to put a HTML template file in ${HTML_ROOT}/nutweb/ "
fi

vt100echo green  "# Success"
vt100echo yellow "# TomCat restart required, for example: "
