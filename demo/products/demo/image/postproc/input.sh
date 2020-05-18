

INPUT_ID=${PRODUCT_ID%.anim}
INPUT_PARAMS=${OUTFILE#*__}

# Number of frames
FRAMES=${FRAMES:-'8'}

TIMESTAMP=${TIMESTAMP:-`date +'%Y%m%d%H00' --utc`}

UNIX_SECONDS=`date +'%s' -d "${TIMESTAMP:0:4}/${TIMESTAMP:4:2}/${TIMESTAMP:6:2} ${TIMESTAMP:8:2}:${TIMESTAMP:10:2} UTC"` 

TIMESTEP=${TIMESTEP:-'15'}

if (( FRAMES < 0 )); then
    N=$(( -FRAMES ))
else
    N=$(( +FRAMES ))    
fi
echo "# N=$N"


if (( N > 30 )); then
    echo "413 Request Entity Too Large (too many frames, abs(FRAMES)=$N > 30 )"
    # Or just limit?
    exit 1
fi

# TODO: generalize format png
INPUT_FILE_SYNTAX="%s_${INPUT_ID}_${INPUT_PARAMS}.png"
INPUT_DECL_SYNTAX="FRAME_%02d=${INPUT_FILE_SYNTAX}"

i=0
while (( i < N )); do
    
    echo "# i=$i"
    if (( FRAMES < 0 )); then
	TIMESTAMP=`date +'%Y%m%d%H%M' -d "@$(( UNIX_SECONDS - i * 60 * TIMESTEP ))" --utc`
	printf "${INPUT_DECL_SYNTAX} \n" $(( N - i ))  $TIMESTAMP
    else
	TIMESTAMP=`date +'%Y%m%d%H%M' -d "@$(( UNIX_SECONDS + i * 60 * TIMESTEP ))" --utc`
	printf "${INPUT_DECL_SYNTAX} \n" $i            $TIMESTAMP
    fi

    i=$(( i + 1 ))

done
