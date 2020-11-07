#!/bin/bash

OUTFILE=${OUTFILE:-'test.png'}
OUTDIR=${OUTDIR:-'.'}

EXTENSION=${EXTENSION:-${OUTFILE#*.}}

#WIDTH=${WIDTH:-'512'}
#HEIGHT=${HEIGHT:-$WIDTH}
SIZE=${SIZE:-"256,256"}
SIZE=( ${SIZE/,/ } )
WIDTH=${WIDTH:-${SIZE[0]}}   
HEIGHT=${HEIGHT:-${SIZE[1]}} 
HEIGHT=${HEIGHT:-$WIDTH} 
SIZE="${WIDTH},${HEIGHT}"

echo "# SIZE=$SIZE"

#if (( WIDTH * HEIGHT > 250000 )); then
if (( WIDTH * HEIGHT > 1000000 )); then
    echo "416 Requested image size is too large."
    exit 1
fi

mode=${MODE:+"-$MODE"}


SEED=${SEED:-'1'$YEAR$MONTH$DAY}
DIMENSION=${DIMENSION:-'2.2'}
POWER=${POWER:-'1.0'}
INCLINATION=${INCLINATION:-0}

ICE=${ICE:-0}
if [ $ICE != 0 ]; then
    ice="-ice ${ICE}"
fi

GLACIERS=${GLACIERS:-0}
if [ $GLACIERS != 0 ]; then
    glaciers="-glaciers ${GLACIERS}"
fi

HOUR=${HOUR:-'12'}
MINUTE=${MINUTE:-'0'}
MINUTEDEC=$(( 10#$MINUTE * 100 / 60 ))
#MINUTEDEC=
HOURDEC=`printf '%s%02d' $HOUR  $(( 10#$MINUTE * 100 / 60 ))`
if [ "$MODE" == 'clouds' ]; then
    #PRECENTAGE=$(( 50*HOUR%2 + MINUTEDEC/2 ))
    PRECENTAGE=$(( MINUTEDEC ))
    roll="-roll +$(( PRECENTAGE * WIDTH / 100 ))+0"
fi

#grayscale=${GRAY:+"-type GrayScale -modulate 100,150"}
grayscale=${GRAY:+"-channel R -separate "}

#modulate="-modulate 100,150"


cmd="ppmforge $mode -seed '100$SEED' -inclination $INCLINATION  -dimension '$DIMENSION' -power '$POWER' ${TYPE:+-$TYPE} $glaciers $ice  -hour '$HOURDEC' -width '$WIDTH' -height '$HEIGHT'"
cmd2="convert - -crop ${WIDTH}x${HEIGHT}+0+0 $grayscale $roll $OUTDIR/$OUTFILE"

echo "# $cmd"
case $EXTENSION in
    sh)
	echo "$cmd | ${cmd2%.*}.png" > $OUTDIR/$OUTFILE
	;;
    ppm)
	echo "$cmd > $OUTDIR/$OUTFILE"
	eval "$cmd > $OUTDIR/$OUTFILE"
	;;
    *)
	echo "$cmd | $cmd2"
	eval "$cmd | $cmd2"
esac

if [ $? != 0 ]; then
    echo "500 Command failed: $cmd"
    exit 1
fi

