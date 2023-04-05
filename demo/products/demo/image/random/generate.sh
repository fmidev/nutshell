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

if (( WIDTH * HEIGHT > 1000000 )); then
    echo "416 Requested image size is too large."
    exit 1
fi

HOUR=${HOUR:-'12'}
MINUTE=${MINUTE:-'0'}
#MINUTEDEC=$(( 10#$MINUTE * 100 / 60 ))
# Percentage of day (24*60 = 1440 minutes)) 
PERCENTAGE=$(( 100 * (60 * 10#$HOUR + 10#$MINUTE ) / 1440 )) 

#HOURDEC=`printf '%s.%02d' $HOUR  $(( 10#$MINUTE * 100 / 60 ))`
#if [ "$MODE" == 'clouds' ]; then
#    #PRECENTAGE=$(( 50*HOUR%2 + MINUTEDEC/2 ))
#    PRECENTAGE=$(( MINUTEDEC ))





SEED=${SEED:-'0'}
PHASE=${PHASE:-$(( PERCENTAGE*720 / 100 ))}
BLUR=${BLUR:-'8'}

phase="-function Sinusoid '1,$PHASE'"
#phase="-function Sinusoid '1,$(( HOUR*60 +  ))'"
roll="-roll +$(( PERCENTAGE * WIDTH / 100 ))+0"
#text="-fill red -draw 'text 10,20 \"$HOUR $MINUTE $PERCENTAGE\"'"

#threshold=${THRESHOLD:+"-threshold '${THRESHOLD}%'"}
threshold=${THRESHOLD:+"\( +clone -threshold '${THRESHOLD}%' \) -compose Multiply -composite "}




cmd="convert -size '${WIDTH}x${HEIGHT}' xc: -seed '$SEED' +noise Random -channel G -separate $phase -virtual-pixel tile -blur '0x$BLUR' -auto-level $threshold -separate $roll $text  $OUTDIR/$OUTFILE"


echo "# cmd"

if [ $FORMAT != 'sh' ]; then
    eval $cmd
else
    echo "# Command invoked by $0 " > $OUTDIR/$OUTFILE
    echo ${cmd%.*}'.png' >> $OUTDIR/$OUTFILE
fi


# convert  202005082115_demo.image.random_SIZE=512_THRESHOLD=50.png 202005082115_demo.image.random_SIZE=512.png -compose Multiply -composite foo.png
