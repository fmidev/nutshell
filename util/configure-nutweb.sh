#!/bin/bash
# -e

# Markus.Peura@fmi.fi


source util/vt100utils.sh
source util/utils.sh

vt100echo green,dim "This is $0"

CONF_FILE="nutweb-${HOSTNAME}.cnf"

read_config $CONF_FILE 
backup_file $CONF_FILE 

echo "# Conf by $USER@$HOSTNAME on $DATE " > $CONF_FILE
echo >> $CONF_FILE

# HTTP_PORT=${HTTP_PORT:-'8000'}
ask_variable NAME 'nutweb'  "Name (label) for this installation"
NAME_SAFE=${NAME//[^a-zA-Z0-9_\-]/}
if [ $NAME_SAFE != "$NAME" ]; then
    vt100echo yellow "Warning: avoid spaces and special chars in name: $NAME -> $NAME_SAFE"
    NAME="$NAME_SAFE"
    ask_variable NAME "$NAME_SAFE"  "Name (label) for this installation"
fi

ask_variable TOMCAT 'tomcat10' "TomCat version"

ask_variable HTTP_PORT '8080'  "Port for HTTP server"

# /etc/tomcat10/Catalina/localhost
# /var/lib/tomcat10/conf -> /etc/tomcat10
ask_variable CATALINA_DIR '/etc/tomcat10/Catalina/localhost'  "Directory for deployment descriptor nutweb.xml"
if [ -d $CATALINA_DIR ]; then
    if [ -w $CATALINA_DIR ]; then
	vt100echo green,dim   "OK - directory exists and is writable: $CATALINA_DIR"
    else
	vt100echo yellow,dim  "Directory exists but is not writable: $CATALINA_DIR"
	vt100echo cyan  "Consider contacting your admin. You can also reconfigure or edit $CONF_FILE later"
    fi
else
    vt100echo yellow,dim  "Directory does not exist: $CATALINA_DIR"
fi

ask_variable HTTP_PREFIX "/$NAME" 'Path prefix appearing in URLs '

ask_variable HTTP_EXCLUDES '/nutweb,/raw,/data,/cache' 'Comma-separated prefixes bypassing NutWeb and directed to default HTML handler'

#  /var/lib/tomcat10/webapps/ROOT/index.html
# Used to be HTML_ROOT also ...
ask_variable HTML_ROOT "/opt${HTTP_PREFIX}"  'Directory for web documents'


ask_variable  HTML_TEMPLATE 'nutweb/template.html' "Layout file that embeds any HTML page requested"

# Store full path of the source
# ask_variable  HTML_TEMPLATE_SRC "./nutweb/tpl/$HTML_TEMPLATE"  'Source file for layout template'

# Leave basename
# HTML_TEMPLATE=${HTML_TEMPLATE##*/}

vt100echo green "Wrote: $CONF_FILE"
vt100echo cyan  "Proceed with: util/install-nutweb.sh"
echo

