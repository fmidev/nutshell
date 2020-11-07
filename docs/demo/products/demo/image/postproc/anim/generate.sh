#!/bin/bash

# Convert -loop argument (0 = infinite loop)
LOOPS=${LOOPS:-'0'};

# Convert -delay argument (1/100 secs between iamges)
DELAY=${DELAY:-'100'};


OUTFILE=${OUTFILE:-'anim.gif'}
OUTDIR=${OUTDIR:-'.'}


FORMAT=${FORMAT:-${OUTFILE##*.}}
if [ "$FORMAT" != 'gif' ]; then
    # echo "415 Warning Unsupported Media Type (only GIF anims currently available)"
    echo "415 Only GIF format currently supported"
    exit 1
fi

#filekeys="\$
#echo "# ${filekeys}"
#files=( `echo ${filekeys}` )

INPUTS=()
for i in ${INPUTKEYS//,/ } ; do
    eval i=\$$i
    INPUTS+=( $i )
done

echo "# INPUTS: ${#INPUTS[*]}"

cmd="convert -loop '$LOOPS' -delay '$DELAY' ${INPUTS[*]} $OUTDIR/$OUTFILE"
echo "$cmd"
eval "$cmd"

if [ $? != 0 ]; then
    echo "500 Command failed: $cmd "
    exit 1
fi    
