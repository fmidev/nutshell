#!/bin/bash -e

# Markus.Peura@fmi.fi

CONF_FILE='nutshell.cnf'

echo "Creating/updating NutShell python config file '$CONF_FILE'."
echo "(Modifiable later with text editor, for example.)"
echo

# Bash special
function goodbye {
    echo "# Installation stopped, exiting $0..."
}
trap goodbye EXIT




if [ -f "$CONF_FILE" ]; then
    echo "Saving back-up:"
    BACKUP_INDEX=`ls -1 nutshell.cnf.[0-9]*.bak | tail -1 | tr -cd '[0-9]'` &> /dev/null
    echo $BACKUP_INDEX
    if [ "$BACKUP_INDEX" == '' ]; then
	BACKUP_INDEX='01'
    else
	BACKUP_INDEX=`printf '%02d' $(( 1 + 10#$BACKUP_INDEX ))`
    fi
    cp -vi $CONF_FILE $CONF_FILE.$BACKUP_INDEX.bak
    source $CONF_FILE
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

function warn_if_unfound(){
  if [ ! -d "$1" ]; then
      echo "Warning: $1 not found"
  fi
}

function clone_dir {
    local src_dir=$1/
    local dst_dir=$2/
    if [ "$dst_dir" != "$src_dir" ]; then
	echo "Copying files to $dst_dir"
	cp -vauR  $src_dir $dst_dir
    fi
}

#function exit_if_failed(){
#  if [ ! -d "$1" ]; then
#      echo "Warning: $1 not found"
#  fi
#}



echo "Accept or modify the directories detected above:"
echo 



# Root dir of this installation package
PKG_ROOT=${PWD%/*}

#echo $PKG_ROOT

ask_variable NUTSHELL_DIR "$PWD" "Directory for NutShell Python files:"
mkdir -v --parents $NUTSHELL_DIR

ask_variable CACHE_ROOT "$PKG_ROOT/cache" "Root of cache directory, often on separate resource:"
mkdir -v --parents --mode a+rwx $CACHE_ROOT

ask_variable PRODUCT_ROOT "$PKG_ROOT/products" "Root of product generator (script) directories"
mkdir -v --parents $PRODUCT_ROOT

ask_variable HTML_ROOT "$PKG_ROOT/html" "Root directory for HTTP server (optional)."
mkdir -v --parents $HTML_ROOT
if [ $? == 0 ]; then
    echo "Linking CACHE_ROOT to HTML_ROOT/cache"
    ln -svi $CACHE_ROOT $HTML_ROOT/
fi


ask_variable HTTP_PORT "8088" "Port for HTTP server."

ask_variable HTTP_PREFIX "nutshell/" "URL directory prefix for NutShell services."





echo "Created $CONF_FILE with contents:"
echo
cat  $CONF_FILE

echo "Updated '$CONF_FILE'"
echo 
echo "Continue with ./build.sh"

exit 0
