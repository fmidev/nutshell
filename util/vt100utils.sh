#!/bin/bash

# Source '.' this file to get aliases of fancy text highlighting
# 
#
# Markus Peura fmi.fi
#

# Example:
# vt100echo green "Hello world"
function vt100echo(){

    # TODO: loop combined style?
    local style=$1
    
    local i=''
    # Split 1st arg with ','
    for i in ${style//,/ } ; do
	style=`vt100style $i`
	echo -ne "\033[1;${style}m"
    done
    
    shift
    echo -e $*"\033[0m"
    
}

# Example:
# vt100cmd underline,red,white-bg ls -ltr 
function vt100cmd(){

    # Start
    local style=$1
    local i=''
    for i in ${style//,/ } ; do
	style=`vt100style $i`
	echo -ne "\033[1;${style}m"
    done
    
    shift

    # Run command, with all its arguments
    $*

    # End
    echo -e "\033[0m"
    
}


function vt100demo(){
    local phrase=' Hello! '
    local i=0
    local style=0
    for i in {1..50} ; do
	#style=`vt100style $i`
	vt100echo $i "$i:\t $phrase"
    done
}

function vt100style(){

    case $1 in
	bright)
	    # Blue prompt
	    echo 39
	    ;;
	dim)
	    # Gray
	    echo 2
	    ;;
	italic)
	    echo 3
	    ;;
	underline)
	    echo 4
	    ;;
	underline2)
	    # Double unnderline
	    echo 21
	    ;;
	dark)
	    # Dark (green?)
	    echo 30
	    ;;
	red)
	    # Red 
	    echo 31    
	;;
	green)
	    # Red 
	    echo 32    
	;;
	yellow)
	    # Orange prompt
	    echo 33
	;;
	blue)
	    # Blue 
	    echo 34
	    ;;
	magenta)
	    # Green
	    echo 35
	    ;;
	cyan)
	    # Green
	    echo 36
	    ;;
	white)
	    # Gray
	    echo 37
	    ;;
	dark-bg)
	    # Dark (green?)
	    echo 40
	    ;;
	red-bg)
	    # Red 
	    echo 41    
	;;
	green-bg)
	    # Red 
	    echo 42    
	;;
	yellow-bg)
	    # Orange prompt
	    echo 43
	;;
	blue-bg)
	    # Blue 
	    echo 44
	    ;;
	magenta-bg)
	    # Green
	    echo 45
	    ;;
	cyan-bg)
	    # Green
	    echo 46
	    ;;
	white-bg)
	    # Gray
	    echo 47
	    ;;
	*)
	    # Unchanged
	    echo $1
	    ;;
    esac

    #shift
    # echo "\[\033[1;${COLOR}m\]"$*"\[\033[0m\]"
    #echo -e "\033[1;${COLOR}m"$*"\033[0m"
}
