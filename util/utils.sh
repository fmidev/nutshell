#!/bin/bash
# OLI -e

# Markus.Peura@fmi.fi

# NOTE: this file should be source'd only.

export DATE=`date +'%Y-%m-%d %H:%M'`

# Bash special
function goodbye { 
    echo "# Exiting $0"
    #echo "Consider next:  util/install.sh $NUTSHELL_VERSION"
}

function critical_check {
    local error_code=$?
    if [ $error_code != 0 ]; then
	vt100echo yellow "$*"
	vt100echo red "Command failed (code $error_code), quitting..."
	exit $error_code
    fi
}



function backup_file(){

    local FILE=$1
    local BACKUP_COUNT=${2:-'12'}
   
    local BACKUP_FILE

    # First, check LATEST (-r)
    BACKUP_FILE=`ls -1tr $FILE.[0-9]*.bak | tail -1` &> /dev/null
    if [ -f "$BACKUP_FILE" ]; then
	diff --brief $FILE $BACKUP_FILE &> /dev/null
	if [ $? == 0 ]; then
	    vt100echo green,dim "Skipping backup - the latest is equal: $BACKUP_FILE"
	    return
	fi
    fi
    
    #local BACKUP_FILE    
    local C=`ls -1 $FILE.[0-9]*.bak | wc -l` &> /dev/null

    if (( $C < $BACKUP_COUNT )); then
	# Compute new index
	# BACKUP_FILE=`ls -1 $FILE.[0-9]*.bak | tail -1`
	# BACKUP_INDEX=${BACKUP_FILE%.*}
	# BACKUP_INDEX=${BACKUP_INDEX##*.}
	# BACKUP_INDEX=`printf "$INDEX_FMT" $(( 1 + 10#$BACKUP_INDEX ))`
	# if (( $BACKUP_INDEX == 0 )); then
	local BACKUP_INDEX=`printf '%02d' $(( 1 + 10#$C ))`
	if [ $? != 0 ] ; then
	    vt100echo red "Failed in formatting index: ${BACKUP_INDEX}"
	    return
	fi
	vt100echo green,dim "New backup index: ${BACKUP_INDEX}"
	
	BACKUP_FILE=${FILE}.${BACKUP_INDEX}.bak
	
    else
    	# Full backup file set exists, overwrite the OLDEST:
	BACKUP_FILE=`ls -1t $FILE.[0-9]*.bak | tail -1` 	
    fi

    # Check similarity of current file and previous backup
    # Note: file with derived $BACKUP_INDEX may exist (if indexed files missing)
    # if [ -f $BACKUP_FILE ]; then
    #   diff --brief $FILE $BACKUP_FILE &> /dev/null
    #   if [ $? == 0 ]; then
    #     vt100echo green "Latest backup is equal: $BACKUP_FILE - skipping backup"
    #     return
    #   fi
    # fi

    
    vt100echo cyan "Saving Backup:" #  $BACKUP_FILE"	    
    # BACKUP_FILE=`ls -1 $FILE.[0-9]*.bak | tail -1` &> /dev/null
    # Counter of many
    # local BACKUP_INDEX='01'
    
    cp -vi $FILE $BACKUP_FILE
    # $FILE.$BACKUP_INDEX.bak
  
    if [ $? == 0 ]; then # && [ ! -e $BACKUP_FILE ]; then
	vt100echo green "Saved $BACKUP_FILE"
    else
	vt100echo yellow "Warning: could not save: $BACKUP_FILE "
    fi

}

# Bash special
# Args: <filepath>
function read_config {

    if [ $# == 0 ]; then
	vt100echo red "Config key required for filename missing"
	exit 1
	return
    fi

    local file_path=$1
    #local config_key=$1
    #local file_path="./${config_key}-$HOSTNAME.cnf"

    if [ ! -f ${file_path} ]; then
	vt100echo red "Config file '${file_path}' not found"
	exit 2
	return
    fi

    vt100echo green "Reading '${file_path}'"
    for i in `cut -d'#' ${file_path} -f1 | grep '[A-Z][A-Z0-9_]\+=' `; do
	#echo "MIKA $i"
	export "$i"
    done

    
    #echo "# Exiting $0"
    #echo "Consider next:  util/install.sh $NUTSHELL_VERSION"
    
}

# Note: reading means "source"
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

# write_variable <key> <value>  <comment>
#
function write_variable(){
    local conf_file=${CONF_FILE:-/dev/tty}
    local key=$1
    local value=$2
    shift 2
    echo "# $*" >> $conf_file
    #echo "$key='$value'" >> $conf_file
    # Quotes removed, for simple conf reader (`read_conf`)
    echo "$key=$value" >> $conf_file
    echo >> $conf_file
}

# Utility to change default variables (above)
# ARGS: <variable-name> <prompt-text>
function ask_variable(){
    echo
    local key=$1
    local default=$2
    local value
    eval X="\${$key:-'$default'}"
    shift 2
    echo $*
    read -e  -i "$X" -p "  $key=" $key
    eval value=\$$key
    write_variable $key "$value"  $*
    # if [ "$CONF_FILE" != '' ]; then 
    #	echo "# $*" >> $CONF_FILE
    #	echo "$key='$X'" >> $CONF_FILE
    #	echo >> $CONF_FILE
    # fi
}

# Add leading '/' and remove trailing '/'
function check_dir_syntax(){
    local key=$1

    if [ "$key" == '' ]; then
	return
    fi
    
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
	vt100echo green "# Directory exists: $src_dir"
    else
	mkdir -v --parents $src_dir
    fi
    
    if [ $dst_dir -ef $src_dir ]; then
	vt100echo green "# Directory/link exists already: $dst_DIR"
	return
    fi
    
    if [ -d $dst_dir ]; then
	vt100echo cyan "# ! Another directory exists: $dst_DIR"
    fi

    if [ -L $dst_dir ]; then
	vt100echo cyan "# ! Another link exists: $dst_DIR"
    fi

    #echo "Supressed: Linking $src_dir to $dst_DIR"
    vt100echo cyan "# Consider soft linking: "    
    if ! [ $src_dir -ef $dst_dir ]; then
	## ln -sfv $src_dir $dst_dir
	vt100echo cyan "ln -sfv $src_dir $dst_dir"
	#if [ $? != 0 ]; then	
	#    echo "# Linking failed!"
	#fi
    fi
    
}


#function exit_if_failed(){
#  if [ ! -d "$1" ]; then
#      echo "Warning: $1 not found"
#  fi
#}





