#!/bin/bash -e

# Markus.Peura@fmi.fi

# NOTE: this file should be source'd only.

export DATE=`date +'%Y-%m-%d %H:%M'`

# Bash special
function goodbye {
    echo "# Exiting $0"
    #echo "Consider next:  util/install.sh $NUTSHELL_VERSION"
    
}
trap goodbye EXIT


function backup_file(){

    local FILE=$1

    vt100echo cyan "Saving back-up:"
    #local BACKUP_INDEX=`ls -1 $FILE.[0-9]*.bak | tail -1 | tr -cd '[0-9]'` &> /dev/null
    local BACKUP_INDEX=`ls -1 $FILE.[0-9]*.bak | tail -1` &> /dev/null
    # echo $BACKUP_INDEX
    #show_variable BACKUP_INDEX
    BACKUP_INDEX=${BACKUP_INDEX%.*}
    BACKUP_INDEX=${BACKUP_INDEX##*.}
    #show_variable BACKUP_INDEX

    if [ "$BACKUP_INDEX" == '' ]; then
	BACKUP_INDEX='01'
    else
	BACKUP_INDEX=`printf '%02d' $(( 1 + 10#$BACKUP_INDEX ))`
    fi
    # show_variable BACKUP_INDEX
    BACKUP_FILE=$FILE.$BACKUP_INDEX.bak
    cp -vi $FILE $FILE.$BACKUP_INDEX.bak
    if [ ! -e $BACKUP_FILE ]; then
	vt100echo yellow "Warning: could not save: $BACKUP_FILE "
    fi

}

function read_and_backup_file(){

    local FILE=$1

    if [ ! -f "$FILE" ]; then

	echo "Creating file '$FILE'."
	echo

    else
	
	echo "Updating file '$FILE'."
	echo
	
	#echo "Reading: '$FILE'"
	source $FILE

	backup_file $FILE
	echo 
    fi

    echo -n "# Conf by $USER, " > $CONF_FILE
    date >> $CONF_FILE
    echo >> $CONF_FILE

}


#READ=true
#function apply_for(){
#    local i
#    READ=false
#    for i in $*; do
#	if [ $i == $NUTSHELL_DEFAULT ]; then
#	   READ=true
#	   return
#	fi
#    done
#}


function show_variable(){
    local value
    eval value=\$$1
    vt100echo cyan "# $1=$value"
}

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
    if [ "$CONF_FILE" != '' ]; then 
	echo "# $*" >> $CONF_FILE
	echo "$key='$X'" >> $CONF_FILE
	echo >> $CONF_FILE
    fi
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
    local dst_dir=$NUTSHELL_ROOT/$dst_subdir
    local dst_DIR=\$NUTSHELL_ROOT/$dst_subdir

    if [ -d $src_dir ]; then
	echo "# Directory exists: $src_dir"
    else
	mkdir -v --parents $src_dir
    fi
    
    if [ $dst_dir -ef $src_dir ]; then
	echo "# Directory/link exists already: $dst_DIR"
	return
    fi
    
    if [ -d $dst_dir ]; then
	echo "# ! Another directory exists: $dst_DIR"
    fi

    if [ -L $dst_dir ]; then
	echo "# ! Another link exists: $dst_DIR"
    fi

    echo "Linking $src_dir to $dst_DIR"
    if ! [ $src_dir -ef $dst_dir ]; then
	ln -sfv $src_dir $dst_dir
	if [ $? != 0 ]; then	
	    echo "# Linking failed!"
	fi
    fi
    
}


#function exit_if_failed(){
#  if [ ! -d "$1" ]; then
#      echo "Warning: $1 not found"
#  fi
#}


#NUTSHELL_VERSION=${1:-'tomcat'}
NUTSHELL_VERSION=$1

if [ "$NUTSHELL_VERSION" == '' ]; then
    vt100echo green "Nutshell comes with three installation options:" 
    echo "  'python': Python3 version: command line and library interface (with optional simple WWW server)"
    echo "  'tomcat': Java version for Tomcat 7 or 8 WWW server, with cmd line interface"
    echo "  'java':   Java version with command line interface only"
    echo 
    vt100echo green "Select: "
    ask_variable NUTSHELL_VERSION  "tomcat" "NUTSHELL_VERSION (python|java|tomcat) "
fi

# CONF_FILE="install-$NUTSHELL_VERSION.cnf"
# CONF_FILE=${CONF_FILE:-'nutshell.cnf'}
CONF_FILE=${CONF_FILE:-"nutshell-$NUTSHELL_VERSION-$HOSTNAME.cnf"}






