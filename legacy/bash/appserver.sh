#!/bin/bash
# Markus.Peura@fmi.fi 2008

# TODO: argument/option parsing

if [[ $1 == '-h' ]]; then
    echo "AppServer - a product/data retrieval facility."
    echo "Markus.Peura@fmi.fi 2008"
    echo ""
    echo "Assumes that (data) products appear as files in directories or as urls."
    echo "In typical usage, returns a string containing the existing (perhaps succesfully generated) product URLs."
    echo "Configuration in appserver.cnf"
    echo ""
    echo "Example: "
    echo " `basename $0` 200608091630_fmi.radar.raw_SITE=VAN_ELEV=01_DATA=Z.pgm.gz"
    exit
fi

source appserver-lib.sh

PARSE_FILENAME
REQUIRE PRODUCT


# infrastructure
export TMP_DIR=${TMP_DIR:-tmp}
export TARGET_DIR=${TARGET_DIR:-$TMP_DIR}
export PID=${PID:-$$}

export APS_LOG="log/appserver-$PRODUCT.log"

APS_METHODS=${APS_METHODS:-latest,cache,generate}
APS_MATCH=${APS_MATCH:-$FILE}

date > $APS_LOG

ECHO 1 "APS_METHODS=$APS_METHODS"
for method in ${APS_METHODS//,/ }; do
	case $method in 
	    'latest') 
		ECHO 1 "Searching for latest...'$LATEST_FILE_SYNTAX'"
		if [[ "$LATEST_FILE_SYNTAX" != '' ]]; then
		    QUERY_FILE $LATEST_DIR_SYNTAX `echo $LATEST_FILE_SYNTAX`
		else
		    ECHO 1 "No syntax defined."
		fi
		RETURN_ON_SUCCESS ${FILES[*]}
		;;
	    'cache')
		ECHO 1 "Querying..."
		QUERY_FILE "$TARGET_DIR" "$APS_MATCH"
		RETURN_ON_SUCCESS ${FILES[*]}
		;;
	    'generate')
		ECHO 1 "Generating..."
		ECHO 2 "CWD -> $APS_PRODUCT_ROOT/$PRODUCT_DIR"
		pushd $APS_PRODUCT_ROOT/$PRODUCT_DIR 2>&1 >> $APS_LOG
		PRODUCT=$PRODUCT ./generate.sh 2>&1 | cat >> $APS_LOG
		popd 2>&1 >> $APS_LOG
		QUERY_FILE "$TARGET_DIR" "$APS_MATCH"
		RETURN_ON_SUCCESS ${FILES[*]}
		;;
	    'http')
		ECHO 1 "Retrieving with http"
		command="wget --proxy=off --spider $HTTP_EXEC_SYNTAX"
		ECHO 1 $command
		eval $command  2>> $APS_LOG
		command="wget --proxy=off --spider $HTTP_QUERY_SYNTAX"
		ECHO 1 $command
		eval $command  2>> $APS_LOG
		if (( $? == 0 )); then
		    RETURN_ON_SUCCESS $HTTP_QUERY;
		fi
		;;
	    'debug')	
		ECHO_VARIABLES
		;;
	esac
done










