#!/bin/bash

if [ $# == 0 ]; then
    echo "Convert nutshell Java code to earlier version"
    echo "Current settings: TomCat10 -> TomCat9"
    echo "Usage:"
    echo "  $0 nutshell10/\*.java"
    echo "  DIR_DST=/tmp  $0 nutshell10/\*.java"
    exit 0 
fi

#DIR_DST='/home/mpeura/eclipse-java/nutshell9/src/main/java/nutshell9'
DIR_DST=${DIR_DST:-'./nutshell9'}



for i in $*; do

    # echo ${i%/*} / ${i##*/}
    FILE=${i##*/}
    echo ${FILE}
    cp $i file.tmp
    for REP in jakarta:javax nutshell10:nutshell9; do
	REP=( ${REP/:/ } )
	replace.py -i file.tmp --source ${REP[0]} --target ${REP[1]}  -o $DIR_DST/${FILE}
	#ln -sf $DIR_DST/${FILE} file.tmp
	cp $DIR_DST/${FILE} file.tmp
    done
    # cp file.tmp $DIR_DST/${FILE}
    #    replace.py --regexp jakarta --format javax --source nutshell --target nutshell9   -i $i -o $DIR_DST/${FILE}
    diff $i $DIR_DST/${FILE}
    
done

