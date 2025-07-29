# This file should be source'd, not invoked
#
# Markus.Peura@fmi.fi


vt100echo green,dim "WWW server configuration"

if [ "$CONF_FILE" == '' ]; then
    vt100echo yellow "This file should be source'd, not invoked directly"
    vt100echo red "No \$CONF_FILE defined, exiting"
    exit 1
fi

if [ "$NUTWEB_NAME" == '' ]; then
    vt100echo red "No '$NUTWEB_NAME' defined, exiting"
    exit 2
fi

if [ "$TOMCAT" == '' ]; then
    ask_variable TOMCAT 'tomcat10'  "TomCat (major) version: 8,9,10"
fi


# /etc/tomcat10/Catalina/localhost
# /var/lib/tomcat10/conf -> /etc/tomcat10
ask_variable CATALINA_DIR "/etc/$TOMCAT/Catalina/localhost"  "Directory for deployment descriptor *.xml"
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

ask_variable HTTP_PREFIX "/$NUTWEB_NAME" 'Path prefix appearing in URLs '

ask_variable HTTP_EXCLUDES '/nutweb' 'Comma-separated prefixes for default HTTP handling'

#  /var/lib/tomcat10/webapps/ROOT/index.html
# Used to be HTML_ROOT also ...
ask_variable HTML_ROOT "/opt${HTTP_PREFIX}"  'Directory for web documents'


ask_variable  HTML_TEMPLATE 'style/template.html' "Layout file that embeds any HTML page requested"


