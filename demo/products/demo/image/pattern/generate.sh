#!/bin/bash


SIZE=${SIZE:-"256,256"}
SIZE=( ${SIZE/,/ } )
WIDTH=${WIDTH:-${SIZE[0]}}   
HEIGHT=${HEIGHT:-${SIZE[1]}} 
HEIGHT=${HEIGHT:-$WIDTH} 
SIZE="${WIDTH},${HEIGHT}"

# Check 1: size limit
if (( WIDTH * HEIGHT > 1000000 )); then
    # 413 Payload Too Large
    echo "416 Resulting array too large ($WIDTH x $HEIGHT) = $(( WIDTH * HEIGHT / 1000 )) kB"
    exit 1
fi

# Check 1: size limit
if (( WIDTH <= 0 )) || (( HEIGHT <= 0 )); then
    # 400 Bad request
    # 416 Range Not Satisfiable [ wget returns 0! ]
    echo "400 Negative image dimensions: ($WIDTH x $HEIGHT)"
    exit 1
fi

# Check 2: format 
FORMAT=${FORMAT:-'png'}
if [ $FORMAT != 'png' ] && [ $FORMAT != 'jpg' ] && [ $FORMAT != 'sh' ]; then
    # 415 Unsupported Media Type
    echo "415 Format not 'png' or 'jpg' (or .sh)"
    exit 1
fi
    

PATTERN=${PATTERN:-'HEXAGONS'}

PATTERN_INFO=`cat patterns.txt  | grep "^$PATTERN\s" `
echo "DEBUG: $PATTERN_INFO"
if [ "$PATTERN_INFO" == '' ]; then
    # 501 Not Implemented (strictly speaking a server problem)
    echo "501 Undefined pattern '$PATTERN'"
    exit 1
fi

echo "# $OUTDIR/$OUTFILE "

OUTDIR=${OUTDIR:-'.'}
OUTFILE=${OUTFILE:-"image.pattern.png"}

#cmd="convert -size 8x8 pattern:gray50 -filter Point -resize ${WIDTH},${HEIGHT} $OUTDIR/$OUTFILE"
cmd="convert -size ${WIDTH}x${HEIGHT} pattern:${PATTERN//-/_} $OUTDIR/$OUTFILE"

if [ "$FORMAT" == 'sh' ]; then
    echo ${cmd%.*}.png > $OUTDIR/$OUTFILE
    exit 0
fi

echo "LOG: executing: $cmd"

eval $cmd
CODE=$?
if (( CODE != 0 )); then
    # 409 Conflict    
    echo "409 Command execution (convert) failed, return code=$CODE"
    exit $CODE
fi

