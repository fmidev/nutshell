#!/bin/bash

# Test script for nutshell 

LOG='tests/nutshell.log'
mkdir --parents tests

# RST doc file
DOC_FILE=sphinx/source/nutshell-tests.rst

cat >  $DOC_FILE <<EOF
.. NutShell test demo doc file
   generated automatically by
   ${0}
   ${USER}@${HOSTNAME}
   (do not edit!)

.. _tests:

=====
Tests
=====

This tests are run prior to publishing a release. The tests apply both command line invocations
and http queries.
   

EOF

echo "Reading conf file"
export CACHE_ROOT 
source nutshell.cnf

# Todo: in conf file URL_BASE?


HTTP_GET='wget --proxy=off --spider'
NUTSHELL_SERVER=${NUTSHELL_SERVER='http://localhost:8080'}
echo "NUTSHELL_SERVER=${NUTSHELL_SERVER}"

# NUTSHELL_URL='http://localhost:8080/nutshell/NutShell'
NUTSHELL_URL="${NUTSHELL_SERVER}${HTTP_PREFIX}/NutShell"

#SCRIPT_NAME=$0
VT100UTILS=${0%/*}'/vt100utils.sh'
echo $VT100UTILS
source $VT100UTILS




shopt -s expand_aliases
alias echo_title='vt100echo blue-bg'  # ,underline'
alias echo_comment='vt100echo blue -'

alias echo_note='vt100echo cyan'
alias echo_cmd='vt100echo bright'
alias echo_debug='vt100echo dark'
alias echo_error='vt100echo red [ERROR] '
alias echo_warn='vt100echo yellow [WARNING] '
alias echo_ok='vt100echo green [OK] '


# Super Echo
function secho(){
    local style=$1
    shift
    
    case $style in
	title)
	    echo    >> $DOC_FILE
	    echo $* >> $DOC_FILE
	    echo ${*//?/=} >> $DOC_FILE
	    echo    >> $DOC_FILE
	    vt100echo blue-bg $*
	    ;;
	title2)
	    echo    >> $DOC_FILE
	    echo $* >> $DOC_FILE
	    echo ${*//?/-} >> $DOC_FILE
	    echo    >> $DOC_FILE
	    vt100echo blue    $*
	    ;;
	text)
	    echo $* >> $DOC_FILE
	    echo    >> $DOC_FILE
	    vt100echo blue    $*
	    ;;
	*)
	    vt100echo dark $*
	    ;;
    esac
    #echo_$style $*
}


echo_note CACHE_ROOT=$CACHE_ROOT
#secho title CACHE_ROOT=$CACHE_ROOT
#secho comment CACHE_ROOT=$CACHE_ROOT



counter=0

function set_file(){
    FILE=$1
    echo_note "FILE=$FILE" 
    #echo_debug "$FILE"
    parse $FILE
}


function run_cmdline(){

    counter=$(( counter + 1 ))
    LOG=`printf 'tests/nutshell-%02d-cmd.log' $counter `

    local cmd="nutshell $*"
    echo_cmd "nutshell" $*
    echo $cmd > $LOG.cmd
    echo -e "Test #$counter ::\n\n  $cmd\n" >> $DOC_FILE
    #echo "  $cmd" >> $DOC_FILE
    eval $cmd &> $LOG
    #fgrep 'Final status:' $LOG > $LOG.$counter
    
}

function run_http(){
    
    counter=$(( counter + 1 ))
    LOG=`printf 'tests/nutshell-%02d-http.log' $counter `

    #echo_warn params...
    local params=`nutshell $* --http_params 2> /dev/null`
    #echo_warn ...end
    local cmd="${HTTP_GET} -o $LOG '${NUTSHELL_URL}?${params}'"
    echo_cmd $cmd
    echo -e "  ${NUTSHELL_URL}?${params}\n" >> $DOC_FILE
    echo $cmd > $LOG.cmd
    eval $cmd &>> $LOG
    # cp $LOG $LOG.$counter
}

function parse(){
    local FILE=$1
    nutshell --parse $FILE &> /dev/null  > nutshell.inf 
    export PRODUCT_ID TIMESTAMP='' YEAR='' MONTH='' DAY=''
    source nutshell.inf
    OUTDIR=$CACHE_ROOT/$YEAR/$MONTH/$DAY/${PRODUCT_ID//.//}
    OUTDIR_SHORT=$CACHE_ROOT/${PRODUCT_ID//.//}
    if [ $OUTDIR -ef $OUTDIR_SHORT ]; then
	OUTDIR=$OUTDIR_SHORT
	echo_note OUTDIR=$OUTDIR
    else
	echo_note OUTDIR=$OUTDIR
	echo_note OUTDIR_SHORT=$OUTDIR_SHORT
    fi
    LATEST_FILE=${OUTFILE/$TIMESTAMP/LATEST}
}

function check(){

    STATUS=$?

    local REQUIRE_STATUS=${1:-0}

    if [ $STATUS == 0 ] && [ $REQUIRE_STATUS != 0 ] ; then
	vt100cmd yellow cat $LOG
 	echo_error "return value: $STATUS"
	exit 1
    else
	echo_ok "return value: $STATUS"
	echo "*Return value: $STATUS *" >> $DOC_FILE
    fi

    
    shift
    
    if [ $# != 0 ]; then
	test $*
	if [ $? != 0 ]; then
	    echo_warn "test" $*
	    vt100cmd yellow cat $LOG
	    echo
	    echo_error 'Test failed' 
	    exit 2
	fi
	echo_cmd "test" $*
	echo_ok 'Passed'
    fi


    echo
}




echo
secho title "Initial tests"

secho text  "The following commands are for command line only."


secho title2 "Help command"
run_cmdline --help 
check 0 

secho title2 "Unknown command"
run_cmdline --foo
check 1 

secho title2 "Undefined action"
$cmd --actions FOO
check 1 

secho title2 "Undefined verbosity level"
$cmd --log_level FOO
check 1 
    
secho title2 "Parsing error"
$cmd foo.pdf
check 1 


secho title "Testing Cmd and Http interfaces"

#for cmd in run_cmdline run_http; do
LOOP=${LOOP:-'cmdline,http'}
for i in ${LOOP//,/ } ; do    

    secho title "Tests ($i)"
    
    cmd=run_$i
    echo $counter
    
    set_file 201412161845_demo.image.pattern_HEIGHT=200_PATTERN=OCTAGONS_WIDTH=300.png
    # set_file 201012161615_test.ppmforge_DIMENSION=2.5.png


    secho title2 "Default action (MAKE)"
    $cmd $FILE
    check 0 -f $OUTDIR/$FILE
    # check 0 

    secho title2 "Does product file exist?"
    $cmd --exists $FILE 
    check 0 

    secho title2 "Action: DELETE product file"
    $cmd --delete $FILE 
    check 0 ! -f $OUTDIR/$FILE

    #secho title2 "Now, product file should not exist"
    $cmd --exists $FILE 
    check 1 

    secho title2 "Action: MAKE product (generate, if nonexistent)"
    $cmd --make $FILE 
    check 0 -f $OUTDIR/$FILE

    secho title2 "Action: GENERATE (unconditionally)"
    $cmd --make $FILE 
    check 0 -f $OUTDIR/$FILE


    #echo
    secho title2 "Parameters in wrong order (generated file has them in order)"
    secho text   "Generated file has them in order."
    set_file demo.image.pattern_WIDTH=300_HEIGHT=200_PATTERN=OCTAGONS.png

    run_cmdline --generate $FILE
    check 0 ! -f $OUTDIR/$FILE

    secho title2 "Product error messages"
    set_file demo.image.pattern_HEIGHT=200_PATTERN=OCTAGONS_WIDTH=300.png
    parse $FILE
    secho title2 "Initial check - valid generation"
    $cmd --generate $FILE
    check 0  -f $OUTDIR/$FILE
    
    secho title2 "Error test: image too large"
    set_file demo.image.pattern_WIDTH=1200_HEIGHT=1200_PATTERN=OCTAGONS.png
    $cmd --generate $FILE
    check 1
    
    secho title2 "Error test: Illegal (negative) arguments"
    set_file demo.image.pattern_WIDTH=-300_HEIGHT=-200_PATTERN=OCTAGONS.png
    $cmd --generate $FILE
    check 1
    
    secho title2 "Error test: Unsupported feature"
    set_file demo.image.pattern_WIDTH=200_HEIGHT=200_PATTERN=SQUARE.png
    $cmd --generate $FILE
    check 1
    
    secho title2 "Error test: Unsupported file format"
    set_file demo.image.pattern_WIDTH=200_HEIGHT=200_PATTERN=OCTAGONS.pdf
    $cmd --generate $FILE
    check 1
    
    
done

set_file 201012161615_test.ppmforge_DIMENSION=2.5.png

secho title "Local actions: copy, link and move"

secho title2 "Move the resulting file to specified location"
run_cmdline --move . --make $FILE 
check 0 ! -f $OUTDIR/$FILE
check 0   -f ./$FILE

secho title2 "Copy the resulting file to specified location"
run_cmdline --copy . --make $FILE 
check 0  -f $OUTDIR/$FILE
check 0  -f ./$FILE
#rm -v ./$FILE

secho title2 "Link the resulting file to specified location"
run_cmdline --link . --make $FILE 
check 0  -f $OUTDIR/$FILE
check 0  -f ./$FILE
#rm -v ./$FILE

secho title2 "Link the resulting file to SHORTCUT (non-timestamped) directory"
run_cmdline --shortcut $FILE
#check 0 -f $OUTDIR/$FILE
check 0 -L $OUTDIR_SHORT/$FILE
#check 0 -L $OUTDIR_SHORT/$LATEST_FILE 

secho title2 "Link the resulting file, as LATEST one"
run_cmdline --latest $FILE
#check 0 -f $OUTDIR/$FILE
#check 0 -L $OUTDIR_SHORT/$FILE
check 0 -L $OUTDIR_SHORT/$LATEST_FILE 





secho title "Cmd and Http interplay test"

set_file demo.image.pattern_HEIGHT=200_PATTERN=OCTAGONS_WIDTH=300.png

run_cmdline --generate $FILE
run_http     --delete   $FILE 
check 0 ! -f $OUTDIR/$FILE


secho title "Meteorological..."
set_file 201708121600_radar.rack.comp_BBOX=18,58,28,64_CTARGET=C_PALETTE=default_SITES=fikor,fiika,fivan_SIZE=800,800.png
run_cmdline --generate $FILE
check 0 -f $OUTDIR/$FILE


secho note Finished

