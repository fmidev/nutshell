#!/bin/bash -e

# Markus.Peura@fmi.fi

CONF_FILE='nutshell.cnf'

echo "Creating/updating NutShell config file '$CONF_FILE'."
echo "Modifiable later with text editor.)"
echo

# Bash special
function goodbye {
    echo "# Exiting $0"
}
trap goodbye EXIT




if [ -f "$CONF_FILE" ]; then

    echo "Reading: '$CONF_FILE'"
    source $CONF_FILE

    echo "Saving back-up:"
    BACKUP_INDEX=`ls -1 nutshell.cnf.[0-9]*.bak | tail -1 | tr -cd '[0-9]'` &> /dev/null
    echo $BACKUP_INDEX
    if [ "$BACKUP_INDEX" == '' ]; then
	BACKUP_INDEX='01'
    else
	BACKUP_INDEX=`printf '%02d' $(( 1 + 10#$BACKUP_INDEX ))`
    fi
    cp -vi $CONF_FILE $CONF_FILE.$BACKUP_INDEX.bak

    echo 
fi



echo -n "# Conf by $USER, " > $CONF_FILE
date >> $CONF_FILE
echo >> $CONF_FILE




# Utility to change default variables (above)
# ARGS: <variable-name> <prompt-text>
function ask_variable(){
    echo
    local key=$1
    local default=$2
    local X
    eval X="\${$key:-'$default'}"
    shift 2
    echo $*
    read -e  -i "$X" -p "  $key=" $key
    eval X=\$$key
    echo "# $*" >> $CONF_FILE
    echo "$key='$X'" >> $CONF_FILE
    echo >> $CONF_FILE
}

# Add leading '/' and remove trailing '/'
function check_dir_syntax(){
    local key=$1
    eval value=\${$key}
    value=${value/\~/$HOME}
    value_orig=$value
    if [ ${value:0:1} != '/' ]; then
	value="/$value"
	echo "# NOTE: adding leading '/'" >> $CONF_FILE
    fi
    if [ ${value} != ${value%/} ]; then
	value=${value%/}
	echo "# NOTE: removing trailing '/'" >> $CONF_FILE
    fi
    if [ ${value} != ${value_orig} ]; then
	eval "$key='$value'" # needed?
	echo "$key='$value'" >> $CONF_FILE
    fi
}

function warn_if_unfound(){
  if [ ! -d "$1" ]; then
      echo "Warning: $1 not found"
  fi
}

#function clone_dir {
#    local src_dir=$1/
#    local dst_dir=$2/
#    if [ "$dst_dir" != "$src_dir" ]; then
#	echo "Copying files to $dst_dir"
#	cp -vauR  $src_dir $dst_dir
#    fi
#}

function prepare_dir {

    local src_dir=$1
    local dst_subdir=$2
    local dst_dir=$HTTP_ROOT/$dst_subdir

    if [ -d $src_dir ]; then
	echo "# Directory exists: $src_dir"
    else
	mkdir -v --parents $src_dir
    fi
    
    if [[ $dst_dir -ef $src_dir ]]; then
	echo "# Directory/link exists already: \$HTTP_ROOT/$dst_subdir"
	return
    fi
    
    if [ -d $dst_dir ]; then
	echo "# ! Another directory exists: \$HTTP_ROOT/$dst_subdir"
    fi

    if [ -l $dst_dir ]; then
	echo "# ! Another link exists: \$HTTP_ROOT/$dst_subdir"
    fi

    echo "Linking $src_dir to \$HTTP_ROOT/$dst_dir"
    ln -sfv $src_dir $dst_dir
    if [ $? != 0 ]; then	
	echo "# Linking failed!"
    fi
    
}


#function exit_if_failed(){
#  if [ ! -d "$1" ]; then
#      echo "Warning: $1 not found"
#  fi
#}



echo "Accept or modify the directories detected above."
echo "Directory and url paths must have a leading but no trailing slash '/'."
echo 


ask_variable NUTSHELL_DEFAULT "python" "Default language version: 'java' or 'python'"


# Root dir of this installation package
#PKG_ROOT=${PWD%/*}
#PKG_ROOT=${PWD}
PKG_ROOT=`pwd -P`


if [ ${NUTSHELL_DEFAULT} == 'java' ]; then
    echo " Optional:"
fi
ask_variable NUTSHELL_DIR "$PKG_ROOT/nutshell" "Directory for Python files:"
check_dir_syntax NUTSHELL_DIR
ls -d $NUTSHELL_DIR

ask_variable NUTSHELL_CONF_DIR "$PKG_ROOT" "Directory for primary configuration file:"
check_dir_syntax NUTSHELL_CONF_DIR
if [ ! -d $NUTSHELL_CONF_DIR ]; then
    mkdir -v --parent $NUTSHELL_CONF_DIR
fi

if [ ${NUTSHELL_DEFAULT} == 'python' ]; then
    echo "The following HTTP settings are optional for Python version"
fi

ask_variable HTTP_PORT "8088" "Port for HTTP server, Python version (optional)"

ask_variable HTTP_ROOT "/opt/nutshell" "Root directory for HTTP server (including ./WEB-INF/lib/Nutlet.jar):"
check_dir_syntax HTTP_ROOT
mkdir -v --parents $HTTP_ROOT

ask_variable HTTP_PREFIX "/nutshell" "URL prefix (with leading '/' but without trailing '/')"
check_dir_syntax HTTP_PREFIX


# /usr/local/tomcat/conf/Catalina/localhost/
# /etc/tomcat8/Catalina/localhost
ask_variable TOMCAT_CONF_DIR "/usr/local/tomcat/conf/Catalina/localhost/" "Optional: directory for nutshell.xml"
check_dir_syntax TOMCAT_CONF_DIR


ask_variable CACHE_ROOT "$PKG_ROOT/cache" "Root of cache directory, often on separate resource:"
check_dir_syntax CACHE_ROOT
prepare_dir $CACHE_ROOT cache


ask_variable STORAGE_ROOT "$PKG_ROOT/storage" "Root of a rolling archive, externally maintained:"
check_dir_syntax STORAGE_ROOT
prepare_dir $STORAGE_ROOT storage



ask_variable DIR_PERMS  "rwxrwxr-x" "Permissions for cache sub-directories"
ask_variable FILE_PERMS "rwxrwxr--" "Permissions for cached files"
#ask_variable FILE_GROUP "132"    "Group id shared with http and command line"

ask_variable PRODUCT_ROOT "$PKG_ROOT/products" "Root of product generator directories"
check_dir_syntax PRODUCT_ROOT
prepare_dir $PRODUCT_ROOT products


ask_variable CMD_SCRIPT_DIR '/usr/local/bin' "Optional: directory for 'nutshell' wrapper script"
check_dir_syntax PRODUCT_ROOT
mkdir -v --parents $PRODUCT_ROOT



# ask_variable PATH2 "" "Additional command search path appended to PATH."

echo "Created $CONF_FILE with contents:"
echo "---------------------------------"
echo
cat $CONF_FILE

echo "Updated '$CONF_FILE'"
echo 
#echo "Continue with ./build.sh"
#exit 0
