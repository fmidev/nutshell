#!/bin/bash


OUTFILE=${OUTFILE:-'test.png'}
OUTDIR=${OUTDIR:-'.'}

WIDTH=${WIDTH:-'512'}
HEIGHT=${HEIGHT:-$WIDTH}

if (( WIDTH * HEIGHT > 1000000 )); then
     # 413 Payload Too Large
    echo "416 Resulting array too large ($WIDTH x $HEIGHT) = $(( WIDTH * HEIGHT / 1000 )) kB"
    exit 1
fi

#SEED=${SEED:-'1'$TIMESTAMP}
SEED=${SEED:-'1'$YEAR$MONTH$DAY}
DIMENSION=${DIMENSION:-'2.2'}
POWER=${POWER:-'1.0'}
INCLINATION=${INCLINATION:-0}

# TODO case
clouds=${CLOUDS:+"-clouds"}
ice="-ice ${ICE:-'0.5'}"
glaciers="-glaciers ${GLACIERS:-'0.5'}"


POWER=${POWER:-'1'}
HOUR=${HOUR:-'12'}
MINUTE=${MINUTE:-'0'}
HOURDEC=$HOUR.$(( MINUTE * 100 / 60 ))

command="ppmforge -seed '100$SEED' -inclination $INCLINATION  -dimension '$DIMENSION' -power '$POWER' ${TYPE:+-$TYPE} $glaciers $ice  -hour '$HOURDEC' -width '$WIDTH' -height '$HEIGHT'"

echo "LOG: $command"
eval $command | convert ppm:- $OUTDIR/$OUTFILE

if [ $? == 0 ]; then
   exit 0
else
  echo "409 Conflict (ppmforge or convert failed)"
  exit 1
fi

#exit $?

