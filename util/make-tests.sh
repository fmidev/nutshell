#!/bin/bash

# Test script for nutshell 

LOG='log/nutshell.log'
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

These tests are run prior to publishing a release. 
Most tests involve both command line and http queries.
   

EOF

#CONF_FILE=${1:-"nutshell-tomcat-$USER@$HOSTNAME.cnf"}
CONF_FILE=${1:-"nutshell-tomcat-$HOSTNAME.cnf"}
if [ -f $CONF_FILE ]; then
    echo "Reading conf file"
    export CACHE_ROOT
    source $CONF_FILE
else
    echo "Conf file $CONF_FILE not found"
    echo "Give conf file as argument"
    exit 1
fi



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
	title3)
	    echo    >> $DOC_FILE
	    echo '**'$*'**' >> $DOC_FILE
	    ;;
	text)
	    echo $* >> $DOC_FILE
	    echo    >> $DOC_FILE
	    vt100echo blue    $*
	    ;;
	version)
	    local version=$1
	    local string="${version^} version"
	    shift
	    vt100echo cyan "# NUTSHELL_VERSION=$version"
	    echo >> $DOC_FILE
	    echo $string >> $DOC_FILE
	    echo ${string//?/-} >> $DOC_FILE
	    echo >> $DOC_FILE
	    #${version^} 'version.
	    if [  $version != 'http' ]; then
		echo '*The commands below have been prefixed with* ``'NUTSHELL_VERSION=$version'`` .'  >> $DOC_FILE
	    echo >> $DOC_FILE
	    fi
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

function run_java(){
    run_cmdline java $*
}

function run_python(){
    run_cmdline python $*
}

function run_cmdline(){

    local nutshell_version=$1
    shift

    # globals
    counter=$(( counter + 1 ))
    LOG=`printf "log/nutshell-%02d-${nutshell_version}.log" $counter `

    #local cmd="NUTSHELL_VERSION='$nutshell_version' nutshell $*"
    local cmd="nutshell $*"
    #echo_cmd "nutshell" $*
    echo_cmd "$cmd (test $counter)"
    echo -e "::\n\n  $cmd" >> $DOC_FILE
    cmd="NUTSHELL_VERSION='$nutshell_version' $cmd"
    echo "$cmd" > $LOG.cmd
    eval "$cmd"  &> $LOG
    
}

function run_http(){
    
    counter=$(( counter + 1 ))
    # global, yes!
    LOG=`printf 'log/nutshell-%02d-http.log' $counter `

    #echo_warn params...
    local params=`nutshell $* --http_params 2> /dev/null`
    #echo_warn ...end
    local cmd="${HTTP_GET} -o $LOG '${NUTSHELL_URL}?${params}'"
    echo_cmd $cmd
    #echo -e "\`\$NUTSHELL?${params} <${HTTP_PREFIX}/NutShell?${params}>\`_\n" >> $DOC_FILE
    #echo -e "  ${HTTP_PREFIX}/NutShell?${params}" >> $DOC_FILE
    echo >> $DOC_FILE
    echo -e "- \`\link $counter <${HTTP_PREFIX}/NutShell?${params}>\`_\n" >> $DOC_FILE
    
    echo $cmd > $LOG.cmd
    eval $cmd &>> $LOG
    # cp $LOG $LOG.$counter
}


# TODO: python --parse

function parse(){
    local FILE=$1
    NUTSHELL_VERSION=java nutshell --parse $FILE &> /dev/null  > nutshell.inf 
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

    local STATUS=$?
    #local REQUIRE_STATUS=${1:-0}

    if [ $# != 0 ]; then

	##local REQUIRE_STATUS=${1:-0}
	local REQUIRE_STATUS=$1
	local s=$(( STATUS == 0 ))
	local r=$(( REQUIRE_STATUS == 0 ))
	
	if [ $s != $r ] ; then
	    vt100cmd yellow cat $LOG
 	    echo_error "return value: $STATUS"
	    exit 1
	fi

    fi
    


    echo_ok "return value: $STATUS"
    echo "  # return value: $STATUS" >> $DOC_FILE

    # Further tests (optional)
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

LOOP=${LOOP:-'java,python,http'}

for i in ${LOOP//,/ } ; do

    if [ $i == 'http' ]; then
	continue
    fi

    secho version $i

    cmd=run_$i
    
    secho title3 "Help command"
    $cmd --help 
    check 0 
    
    secho title3 "Unknown command"
    $cmd --foo
    check 1 
    
    secho title3 "Undefined action"
    $cmd --actions FOO
    check 1 
    
    #secho title2 "Undefined verbosity level"
    #run_cmdline --log_level FOO
    #xcheck 1 
    
    secho title3 "Parsing error"
    $cmd 12345_pdf
    check 1 

    secho title3 "Undefined product"
    $cmd foo.pdf
    check 1 

    secho text
    
done




secho title "Testing Cmd and Http interfaces"

#for cmd in run_cmdline run_http; do
for i in ${LOOP//,/ } ; do    

    #secho title "Tests"

    secho version $i
    
    cmd=run_$i
    echo $counter
    
    set_file 201412161845_demo.image.pattern_HEIGHT=200_PATTERN=OCTAGONS_WIDTH=300.png
    # set_file 201012161615_test.ppmforge_DIMENSION=2.5.png

    secho title3 "Default action (MAKE)"
    $cmd $FILE
    check 0 -f $OUTDIR/$FILE
    # check 0 

    secho title3 "Does the product file exist?"
    $cmd --exists $FILE 
    check 0 

    secho title3 "Action: DELETE product file"
    $cmd --delete $FILE 
    check 0 ! -f $OUTDIR/$FILE

    secho title3 "Now, the product file should not exist"
    $cmd --exists $FILE 
    check 1 

    secho title3 "Action: MAKE product (generate, if nonexistent)"
    $cmd --make $FILE 
    check 0 -f $OUTDIR/$FILE

    secho title3 "Action: GENERATE (unconditionally)"
    $cmd --make $FILE 
    check 0 -f $OUTDIR/$FILE


    #echo
    secho title3 "Parameters in wrong order (generated file has them in order)"
    secho text   "Generated file has them in order."
    set_file demo.image.pattern_WIDTH=300_HEIGHT=200_PATTERN=OCTAGONS.png

    $cmd --generate $FILE
    check 0 ! -f $OUTDIR/$FILE

    #secho title3 "Product error messages"
    set_file demo.image.pattern_HEIGHT=200_PATTERN=OCTAGONS_WIDTH=300.png
    parse $FILE
    secho title3 "Initial check - valid generation"
    $cmd --generate $FILE
    check 0  -f $OUTDIR/$FILE
    
    secho title3 "Error test: image too large"
    set_file demo.image.pattern_WIDTH=1200_HEIGHT=1200_PATTERN=OCTAGONS.png
    $cmd --generate $FILE
    check 1
    
    secho title3 "Error test: Illegal (negative) arguments"
    set_file demo.image.pattern_WIDTH=-300_HEIGHT=-200_PATTERN=OCTAGONS.png
    $cmd --generate $FILE
    check 1
    
    secho title3 "Error test: Unsupported feature"
    set_file demo.image.pattern_WIDTH=200_HEIGHT=200_PATTERN=SQUARE.png
    $cmd --generate $FILE
    check 1
    
    secho title3 "Error test: Unsupported file format"
    set_file demo.image.pattern_WIDTH=200_HEIGHT=200_PATTERN=OCTAGONS.pdf
    $cmd --generate $FILE
    check 1
    
    
done



secho title "Local actions: copy, link and move"

set_file 201012161615_test.ppmforge_DIMENSION=2.5.png

for i in ${LOOP//,/ } ; do

    if [ $i == 'http' ]; then
	continue
    fi

    secho version $i
    
    cmd=run_$i

    secho title3 "Move the resulting file to specified location"
    $cmd --move . --make $FILE 
    check 0 ! -f $OUTDIR/$FILE
    check 0   -f ./$FILE
    
    secho title3 "Copy the resulting file to specified location"
    $cmd --copy . --make $FILE 
    check 0  -f $OUTDIR/$FILE
    check 0  -f ./$FILE

    
    secho title3 "Link the resulting file to specified location"
    $cmd --link . --make $FILE 
    check 0  -f $OUTDIR/$FILE
    check 0  -f ./$FILE

    
    secho title3 "Link the resulting file to SHORTCUT (non-timestamped) directory"
    $cmd --shortcut $FILE
    check 0 -L $OUTDIR_SHORT/$FILE
    
    secho title3 "Link the resulting file, as LATEST one"
    $cmd --latest $FILE
    check 0 -L $OUTDIR_SHORT/$LATEST_FILE 

done
    
    
secho title "Cmd and Http interplay test"


set_file demo.image.pattern_HEIGHT=200_PATTERN=OCTAGONS_WIDTH=300.png

for i in ${LOOP//,/ } ; do

    if [ $i == 'http' ]; then
	continue
    fi

    secho version $i
    
    cmd=run_$i

    secho title3 "Generate on command line, delete through HTTP"
    
    $cmd --generate $FILE
    run_http     --delete   $FILE 

    check 0 ! -f $OUTDIR/$FILE


    secho title3 "Generate through HTTP, delete on command line"

    run_http    --generate $FILE
    $cmd --delete   $FILE 
    check 0 ! -f $OUTDIR/$FILE

done


secho title "Meteorological..."
set_file 201708121600_radar.rack.comp_BBOX=18,58,28,64_CTARGET=C_PALETTE=default_SITES=fikor,fiika,fivan_SIZE=800,800.png

echo

for i in ${LOOP//,/ } ; do

    cmd=run_$i

    secho version $i

    secho title3 "${i^} version:"    
    $cmd --generate $FILE
    check 0 -f $OUTDIR/$FILE

done

secho note Finished

