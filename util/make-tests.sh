#!/bin/bash

# Test script for nutshell 

LOG='nutshell.log'
# INFO='nutshell.inf'

echo "Reading conf file"
export CACHE_ROOT 
source nutshell.cnf

echo $CACHE_ROOT


function run_nutshell(){
    echo "# nutshell" $*
    nutshell $* &> $LOG
}

function parse(){
    local FILE=$1
    nutshell --parse $FILE &> /dev/null  > nutshell.inf 
    export PRODUCT_ID TIMESTAMP='' YEAR='' MONTH='' DAY=''
    source nutshell.inf
    OUTDIR=$CACHE_ROOT/$YEAR/$MONTH/$DAY/${PRODUCT_ID//.//}
    OUTDIR_SHORT=$CACHE_ROOT/${PRODUCT_ID//.//}
    LATEST_FILE=${OUTFILE/$TIMESTAMP/LATEST}
}

function check(){

    STATUS=$?

    local REQUIRE_STATUS=${1:-0}

    if [ $STATUS == 0 ] && [ $REQUIRE_STATUS != 0 ] ; then
	cat $LOG
	echo
 	echo "[ERROR] Wrong exit code: $STATUS"
	exit 1
    else
	echo "[OK] Exit code: $STATUS"
    fi

    
    shift
    
    if [ $# != 0 ]; then
	echo "# test" $*
	test $*
	if [ $? != 0 ]; then
	    cat $LOG
	    echo
	    echo '[ERROR] Test failed' 
	    exit 1
	fi
	echo "[OK]"
    fi


    echo
}

FILE=201012161615_test.ppmforge_DIMENSION=2.5.png


echo
echo "Basic tests"

run_nutshell --help 
check 0 

run_nutshell --foo
check 1 

run_nutshell foo.pdf
check 1 

run_nutshell $FILE 
check 0 

run_nutshell --exists $FILE 
check 0 

run_nutshell --delete $FILE 
parse $FILE
check 0 ! -f $OUTDIR/$FILE

run_nutshell --exists $FILE 
check 1 

run_nutshell --make $FILE 
parse $FILE
check 0 -f $OUTDIR/$FILE

run_nutshell --move . --make $FILE 
parse $FILE
check 0 ! -f $OUTDIR/$FILE
check 0   -f ./$FILE

run_nutshell --copy . --make $FILE 
parse $FILE
check 0  -f $OUTDIR/$FILE
check 0  -f ./$FILE


run_nutshell --latest --shortcut $FILE
check 0 -f $OUTDIR/$FILE
check 0 -L $OUTDIR_SHORT/$FILE
check 0 -L $OUTDIR_SHORT/$LATEST_FILE 


echo
echo "Order test"
FILE=demo.image.pattern_WIDTH=300_HEIGHT=200_PATTERN=OCTAGONS.png
run_nutshell --generate $FILE
parse $FILE
check 0 ! -f $OUTDIR/$FILE

echo 
echo "Product error message tests"
FILE=demo.image.pattern_HEIGHT=200_PATTERN=OCTAGONS_WIDTH=300.png
run_nutshell --generate $FILE
parse $FILE
check 0  -f $OUTDIR/$FILE

FILE=demo.image.pattern_WIDTH=1200_HEIGHT=1200_PATTERN=OCTAGONS.png
run_nutshell --generate $FILE
check 1

FILE=demo.image.pattern_WIDTH=-300_HEIGHT=-200_PATTERN=OCTAGONS.png
run_nutshell --generate $FILE
check 1

FILE=demo.image.pattern_WIDTH=200_HEIGHT=200_PATTERN=SQUARE.png
run_nutshell --generate $FILE
check 1

FILE=demo.image.pattern_WIDTH=200_HEIGHT=200_PATTERN=OCTAGONS.pdf
run_nutshell --generate $FILE
check 1
