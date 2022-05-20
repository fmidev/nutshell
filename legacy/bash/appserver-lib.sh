#!/bin/bash
# Markus.Peura@fmi.fi 2008
# Actually, this
#

# TODO: not too much should be EXPORT'ed !!!


export APS_VERBOSE=${APS_VERBOSE:-'0'}


for i in  {/fmi/conf/appserver,.}/appserver.cnf; do
    if [[ -e $i ]]; then
#	ECHO 1 "#APS: $i"
	source $i
    fi
done

# Syntax: 
# PARSE_FILENAME 
#[<filename>]
# Derives the official variables
# TIMESTAMP  (eg. "200509081630"
# PRODUCT    (eg. "fi.fmi.radar.raw")
# PARAMETERS (eg. "SITE=VAN_ELEV=02")
# FORMAT (eg. "pgm.gz")
#
# Plus from many convenience variables:
# YEAR, MONTH,DAY, HOUR, MINUTE 
#
# Todo: should this be $* => $FILE => $*
# Main thing: PRODUCT
function PARSE_FILENAME(){

    ECHO 1 'Parsing variables'
    
    # FILENAME without extensions (FORMAT and COMPRESSION)
    #FILEBASE

    # Format of the file, including compression (eg. pgm.gz)
    #FORMAT=''

    # FORMAT excluding .COMPRESSION
    #BASEFORMAT

    # Compression (gz, zip)
    #export COMPRESSION


    if [[ "$FILE" != "" ]]; then
	FILEBASE=${FILE%.*}
	EXT=${FILE##*.}

        # First, extract FORMAT and COMPRESSION
	case $EXT in 
	    'gz')
		#COMPRESSION=$EXTENSION
		#BASEFORMAT=${FILEBASE##*.}
		FORMAT=${FILEBASE##*.}.$EXT
		FILEBASE=${FILE%.$FORMAT}
		;;
	    'zip')
		FORMAT=${FILEBASE##*.}.$EXT
		FILEBASE=${FILE%.$FORMAT}
		;;
	esac
	
	SPLIT_ID=( ${FILEBASE/_/ } )
	ECHO 2 FILEBASE-split: ${SPLIT_ID[*]}

     # EXTRACT TIMESTAMP, IF EXISTS
	TIMESTAMP=`echo ${SPLIT_ID[0]} | tr --delete --complement '[0-9]'`
	if [[ TIMESTAMP != "" ]]; then
	    SPLIT_ID=( ${SPLIT_ID[1]/_/ } )
	fi
	PRODUCT=${SPLIT_ID[0]}
	PARAMETERS=${SPLIT_ID[1]}
    fi	

    # PRODUCT
    export PRODUCT_DIR=${PRODUCT//.//}


    ECHO 2 "Parsing TIMESTAMP"
    # TIMESTAMP
    export TIMESTAMP
    TIMESTAMP=${TIMESTAMP:-"$YEAR$MONTH$DAY$HOUR$MINUTE"}
    if (( ${#TIMESTAMP} < 12 )); then
	TIMESTAMP=`date --utc +'%Y%m%d%H%M'`
    fi
    #TIMESTAMP=${TIMESTAMP:-`date --utc +'%Y%m%d%H%M'`}
    YEAR=${YEAR:-${TIMESTAMP:0:4}}
    MONTH=${MONTH:-${TIMESTAMP:4:2}}
    DAY=${DAY:-${TIMESTAMP:6:2}}
    HOUR=${HOUR:-${TIMESTAMP:8:2}}
    MINUTE=${MINUTE:-${TIMESTAMP:10:2}}

    TIMESTAMP_DIR="$YEAR/$MONTH/$DAY"

    MINUTE_RESOLUTION=5


# --------------
    READ_DEFAULTS $APS_PRODUCT_ROOT/$PRODUCT_DIR
# --------------    

    #export TIMESTAMP_DIR=${YEAR:+"$YEAR/$MONTH/$DAY"}     
    MINUTE=`printf '%02d' $(( 10#$MINUTE - 10#$MINUTE%MINUTE_RESOLUTION ))`
    #export TIMESTAMP05="$YEAR$MONTH$DAY$HOUR$MINUTE05"
    TIMESTAMP="$YEAR$MONTH$DAY$HOUR$MINUTE"

    # User parameters
    ECHO 2 "Parsing PARAMETERS"
    eval PARAMETERS=${PARAMETERS:-"$PARAMETER_SYNTAX"}
    ECHO 2 $PARAMETERS
    # Comma separated list of keys
    PARAMETERLIST=''	
    #PARAMETERARRAY=( ${PARAMETERS//_/ } ) 
    # Freezing is needed to avoid shell directory reads!
    PARAMETERS_FREEZE=$PARAMETERS
    PARAMETERS_FREEZE=${PARAMETERS_FREEZE//\*/}
    PARAMETERS_FREEZE=${PARAMETERS_FREEZE//\?/}
    for i in ${PARAMETERS_FREEZE//_/ }; do
	#ECHO 2 $i
	i=${i//\*/}
	i=${i//\?/}
	EXPR=( ${i/=/ } )
	#ECHO 2 ${EXPR[0]}=${EXPR[1]}
	eval "${EXPR[0]}"="'${EXPR[1]}'"
	PARAMETERLIST=${PARAMETERLIST:+$PARAMETERLIST','}
	PARAMETERLIST=$PARAMETERLIST${EXPR[0]}
	eval ECHO 2  ${EXPR[0]}':' \$${EXPR[0]}
	#PARAMETERARRAY=( ${PARAMETERARRAY[*]} $EXPR[0] )
    done

    ECHO 2 "---" $LATEST_FILE_SYNTAX
    eval LATEST_FILE_SYNTAX=$LATEST_FILE_SYNTAX
    ECHO 2 "---" $LATEST_FILE_SYNTAX

    # Convenience variables
    ECHO 2 "Defining convenience variables"


    FORMAT_ARRAY=( ${FORMAT/./ } )
    BASEFORMAT=${FORMAT_ARRAY[0]}
    COMPRESSION=${FORMAT_ARRAY[1]}


    _TIMESTAMP=${TIMESTAMP:+$TIMESTAMP'_'}
    _PARAMETERS=${PARAMETERS:+'_'$PARAMETERS}
    #_FORMAT=${FORMAT:+'.'$FORMAT}
    _COMPRESSION=${COMPRESSION:+'.'$COMPRESSION}



    #FILE_SYNTAX=${FILE_SYNTAX:-'$_TIMESTAMP$PRODUCT$_PARAMETERS.$FORMAT$_COMPRESSION'}
    TARGET_DIR_SYNTAX=${TARGET_DIR_SYNTAX:-'query/$TIMESTAMP_DIR/$PRODUCT_DIR'}


    eval TARGET_DIR="$TARGET_DIR_SYNTAX"    
    eval FILE=${FILE:-$FILE_SYNTAX}

    FILEBASE=${FILEBASE:-${FILE/.$FORMAT/}}
}


function READ_DEFAULTS(){  # dir
    ECHO 1 "READ_DEFAULTS: $1/default.cnf" 
    if [ -e "$1/default.cnf" ]; then 
	for i in `fgrep -v '#' "$1/default.cnf"`; do
	    EXPR=( ${i/=/ } )
	    key=${EXPR[0]}
	    value=${EXPR[1]}
	    eval $key='${'$key:-$value'}'
	    ECHO 1 "$key=$value" 
	done
    fi
}

# 
function CREATE_DIRS(){
    ECHO 0 "Creating dirs"
    dir=''
    for d in ${TARGET_DIR//\/ /}; do
	dir="$dir$d"
	mkdir --verbose --parents -m 2777 $dir 2&>1
	dir="$dir/"
    done
}

# Utility
function ECHO_VARIABLES(){
    for i in  FILE_SYNTAX FILE FILEBASE FORMAT BASEFORMAT COMPRESSION TIMESTAMP PRODUCT PARAMETERS _PARAMETERS PARAMETERLIST ${PARAMETERLIST//,/ } TIMESTAMP_DIR PRODUCT_DIR TARGET_DIR TARGET_DIR_SYNTAX LATEST_FILE_SYNTAX; do
	eval x=$i="\\'"\$$i"\\'"
	ECHO 0 $x
    done 
}

function ECHO(){
    v=${1:-'1'}
    if (( APS_VERBOSE >= v )); then
	shift 
	echo "#APS:: $*"
    fi
}

function QUERY_FILE(){ # DIR FILE
    ECHO 2 "QUERY_FILE $1 $2"
    if [[ -d  $1/ ]]; then
	command="find $1/  -maxdepth 1 -not -type d -name $2"
	FILES=( `$command | sort` ) 2>> $APS_LOG
    else 
	ECHO 1 "Dir does not exist: $1"
    fi
}


function REQUIRE(){ # VARIABLE
    local key=$1
    local value
    eval value=\$$key
    if [[ "$value" == '' ]]; then
	ECHO 0 "Error: Required variable '$key' undefined"
	exit 1
    fi
} 

function RETURN_ON_SUCCESS(){  
    if (( $# > 0)); then
	ECHO 1 "Success."
	for i in $*; do
	    echo $i
	done
	exit 0
    else 
	ECHO 1 "No success."
    fi
}


