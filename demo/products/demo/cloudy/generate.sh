#!/bin/bash

echo "HATCH=$HATCH"
echo "CLOUD=$CLOUD"
echo "MAP=$MAP"

TMP_FILEBASE="$OUTDIR/${OUTFILE%.*}-$$"

echo "# TMP_FILEBASE"

function run_image_op(){
    local cmd="$*"
    echo $cmd
    eval $cmd
    if [ $? != 0 ]; then
	echo "500 Command failed: $cmd"
	exit 1
    fi
}

date >  $OUTDIR/$OUTFILE

echo "# Pale map"
MAP2=$TMP_FILEBASE-map.png
run_image_op "convert $MAP -modulate 120,50 $MAP2"

echo "# Hatched cloud"
MASK=$TMP_FILEBASE-MASK.png
run_image_op "convert $CLOUD -evaluate Threshold 12  $HATCH -negate -compose Multiply -composite $MASK"

echo "# Hatched cloud"
CLOUD2=$TMP_FILEBASE-graphic.png
run_image_op "convert $MASK -negate -fill red -colorize 80,10,0 -separate $MASK -channel RGBA -combine $CLOUD2"

echo "# Overlay"
run_image_op "composite -compose Over $CLOUD2 $MAP2  $OUTDIR/$OUTFILE"


#cmd="convert $HATCH $CLOUD $CLOUD $HATCH -channel RGBA -combine -channel alpha -negate  $TMP_FILE"

#echo $cmd
#eval $cmd

# cmd2= "composite -compose Over $TMP_FILE  $MAP $OUTDIR/$OUTFILE"


