#!/bin/bash -e

# Markus.Peura@fmi.fi

prefix=${prefix:-'/usr/local'}

source util/vt100utils.sh
source util/config-init.sh


#CONF_FILE="install-$NUTSHELL_VERSION.cnf"
read_and_backup_file  $CONF_FILE 


#if [ -f $CONF_FILE ]; then
#    vt100echo green "Reading existing conf file: $CONF_FILE"
#    source $CONF_FILE
#else
#    vt100echo yellow "CONF_FILE=$CONF_FILE does not exist"
#    #CONF_SCRIPT="util/configure-$NUTSHELL_VERSION.sh"
#fi

#if [ ! -v NUTSHELL_ROOT ]; then
#    vt100echo red "NUTSHELL_ROOT undefined"
#    exit 2
#fi




echo -n "# Conf by $USER, " > $CONF_FILE
date >> $CONF_FILE
echo >> $CONF_FILE


# Inits

if [ $NUTSHELL_VERSION == 'python' ]; then
    HTTP_PORT=${HTTP_PORT:-'8080'}
fi

if [ $NUTSHELL_VERSION == 'tomcat' ]; then
    HTTP_PORT=${HTTP_PORT:-'8000'}
fi


vt100echo green "Accept or modify the following directories"
# echo "Directory and url paths must have a leading but no trailing slash '/'."
echo 


# Root dir of this installation package
PKG_ROOT=`pwd -P`

if [ $NUTSHELL_VERSION == 'python' ] || [ $NUTSHELL_VERSION == 'tomcat' ]; then

   
    vt100echo blue "HTTP server configuration"

    ask_variable HTTP_PORT '8080'  "Port for HTTP server"

    ask_variable HTTP_ROOT "/usr/local/nutshell" "Root directory for HTTP server "  #(including ./WEB-INF/lib/Nutlet.jar):"
    check_dir_syntax HTTP_ROOT
    NUTSHELL_ROOT=${NUTSHELL_ROOT:-$HTTP_ROOT}
    
    ask_variable HTTP_PREFIX "/nutshell" "URL prefix"   # (with leading '/' but without trailing '/')"
    check_dir_syntax HTTP_PREFIX

    echo
    
fi

if [ $NUTSHELL_VERSION == 'tomcat' ]; then

    vt100echo blue "Tomcat configuration"

    # /usr/local/tomcat/conf/Catalina/localhost/
    # /etc/tomcat8/Catalina/localhost
    # /var/cache/tomcat8/Catalina
    # /var/cache/tomcat8/Catalina/localhost/nutshell
    ask_variable TOMCAT_CONF_DIR "$prefix/tomcat/conf/Catalina/localhost/nutshell" "Optional: directory for nutshell.xml"
    check_dir_syntax TOMCAT_CONF_DIR

    echo
    
fi


vt100echo blue "Nutshell product server configuration"

ask_variable NUTSHELL_ROOT "$prefix/nutshell" "Directory for nutshell.cnf, default dir for cache, storage, products"


ask_variable PRODUCT_ROOT "$NUTSHELL_ROOT/products" "Root directory for product generators"
check_dir_syntax PRODUCT_ROOT
#prepare_dir $PRODUCT_ROOT products

ask_variable STORAGE_ROOT "$NUTSHELL_ROOT/storage" "Root directory for rolling archive (optional, externally maintained)"
check_dir_syntax STORAGE_ROOT

ask_variable CACHE_ROOT "$NUTSHELL_ROOT/cache" "Root directory for cache"
check_dir_syntax CACHE_ROOT
#prepare_dir $CACHE_ROOT cache

ask_variable DIR_PERMS  "rwxrwxr-x" "Permissions for cache directories"
ask_variable FILE_PERMS "rwxrwxr--" "Permissions for cache files"


#prepare_dir $STORAGE_ROOT storage

ask_variable CMD_SCRIPT_DIR "$prefix/bin" "Directory for command line wrapper script 'nutshell'"
check_dir_syntax CMD_SCRIPT_DIR

#ask_variable NUTSHELL_CONF_DIR "$PKG_ROOT" "Directory for primary configuration file:"
#check_dir_syntax NUTSHELL_CONF_DIR




# ask_variable PATH2 "" "Additional command search path appended to PATH."

echo
vt100echo green "Resulting configuration: "
echo "---------------------------------"
cat $CONF_FILE
echo "---------------------------------"

vt100echo green "Wrote: $CONF_FILE "

vt100echo green "Continue with:"
echo "util/install.sh $NUTSHELL_VERSION "

