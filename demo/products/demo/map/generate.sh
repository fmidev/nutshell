#!/bin/bash


OUTFILE=${OUTFILE:-'out.png'}
OUTDIR=${OUTDIR:-'.'}
#DIR=${DIR:-'.'}
#DIR=${DIR:-$OUTDIR}

# Open log file
echo "# executing $0 PID=$$" 
date --utc +'#  %A, %d %m %Y %H:%M %Z' 
date       +'# (%A, %d %m %Y %H:%M %Z)'

echo "# FORMAT=$FORMAT"

#CONVERT="../convert.sh"
#if [ -f 'mapserver.cnf' ]; then
#  source 'mapserver.cnf'
#fi

if [ "$CONF" == '' ]; then  
    echo "Warning: no \$CONF variable given" >&2 
fi
    
for i in ${CONF//,/ }; do
    CONF_FILE="./$i.cnf"
    if [ -f $CONF_FILE ]; then
	source $CONF_FILE
    else
	echo "501 Warning: conf '$CONF_FILE' not found" >&2
    fi
done

if [ "$WMS_URLBASE" == '' ]; then  
    echo "501 No WMS server defined by CONF='$CONF'" >&2
    exit 1
fi




#BBOX=${IBBOX:+"${IBBOX[1]},${IBBOX[0]},${IBBOX[3]},${IBBOX[2]}"}


BBOX=${BBOX:-"18.600,57.930,34.903,69.005"}
BBOX_CONF="bbox-${BBOX%%:*}.cnf"   # eg. radar:fivan => radar
if [ -f $BBOX_CONF ]; then
    source $BBOX_CONF
    eval BBOX=\$${BBOX#*:}
fi

BBox=( ${BBOX//,/ } )


SIZE=${SIZE:-"256,256"}
SIZE=( ${SIZE/,/ } )
WIDTH=${WIDTH:-${SIZE[0]}}   
HEIGHT=${HEIGHT:-${SIZE[1]}} 
HEIGHT=${HEIGHT:-$WIDTH} 
SIZE="${WIDTH},${HEIGHT}"

#WIDTH=${WIDTH:-'600'}
#HEIGHT=${HEIGHT:-$WIDTH}
#SIZE=${SIZE:-"$WIDTH,$HEIGHT"}
#
#Size=( ${SIZE/,/ } )
#WIDTH=${WIDTH:-${Size[0]}}
#HEIGHT=${HEIGHT:-${Size[1]}}
#HEIGHT=${HEIGHT:-$WIDTH}

#BGCOLOR=${BGCOLOR:-'0xc0d0f0'}
# If not given, will be transparent


# START
EPSG=${EPSG:-${PROJ//[a-zA-Z]/}}
EPSG=${EPSG:-'4326'} # lon-lat

if [ "$EPSG" != '4326' ]; then 
    PROJ="+init=epsg:$EPSG -f '%.4f'"
    X="${BBox[0]} ${BBox[1]} \n ${BBox[2]}  ${BBox[3]}"
    command="echo -e $X | cs2cs +proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs  +to +init=epsg:$EPSG -f '%.4f"
    echo $command
    # BBox2=( `echo -e $X | cs2cs +proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs  +to +init=epsg:$EPSG -f '%.4f'` )
    BBox2=( `echo -e $X | cs2cs +proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs  +to +init=epsg:$EPSG -f '%.0f'` )
    if [ $? != 0 ]; then
	echo "Warning: BBOX conversion failed, checking cs2cs"
	cs2cs >&2
	if [ $? != 0 ]; then
	    echo "Failed. Check that 'proj' or 'proj-bin' is installed"
	    echo "Consider: apt-get install proj-bin "
	    echo "501 Could not execute 'cs2cs' " >&2
	    exit 1
	fi
	echo "501 Desired projection not supported" >&2
	exit 1
    fi
    BBOX="${BBox2[0]},${BBox2[1]},${BBox2[3]},${BBox2[4]}"
    echo BBOX=$BBOX
fi

# END



bgcolor=${BGCOLOR:+"&bgcolor=$BGCOLOR"}
_BGCOLOR=${BGCOLOR:+"_BGCOLOR=$BGCOLOR"}

#http_proxy=${'off'
#http_proxy=${http_proxy:-'off'}


if [ "${TRANSPARENT^^}" == 'TRUE' ]; then
    WMS_TRANSPARENT='TRANSPARENT=TRUE&'
    _WMS_TRANSPARENT='_TRANSPARENT=TRUE'
    unset TRANSPARENT # for 'convert'
    TYPE=TrueColorMatte
fi



wget_cmd="wget ${proxy:+--proxy=$proxy} --no-clobber --tries=2 '$WMS_URLBASE${MAP:+map=$MAP&}${WMS_TRANSPARENT}layers=$LAYERS&srs=EPSG:$EPSG&bbox=$BBOX&width=$WIDTH&height=$HEIGHT$bgcolor$TRAILER'"

echo $wget_cmd

OUTFILE0="${PRODUCT}_BBOX=${BBOX}_EPSG=${EPSG}_SIZE=${SIZE}_LAYERS=${LAYERS}${_WMS_TRANSPARENT}${_BGCOLOR}.png"

rm -vf $OUTDIR/tmp.$OUTFILE0

command="$wget_cmd -O '$OUTDIR/tmp.$OUTFILE0'"  # /tmp.$OUTFILE
echo $command

echo "FORMAT=$FORMAT"

if [ "$FORMAT" == 'sh' ]; then
    echo "# Retrieval command: " > $OUTDIR/$OUTFILE
    echo $command >> $OUTDIR/$OUTFILE 
    #echo "# set" >> $OUTDIR/$OUTFILE
    #set >> $OUTDIR/$OUTFILE
else
    eval $command &>> $OUTDIR/tmp.$OUTFILE0.wms.log
    if [ $? == 0 ]; then
	FILESIZE=`find $OUTDIR/tmp.$OUTFILE0 -printf '%s'`
	if (( $FILESIZE < 1000 )); then
	    echo -n "Warning: File info: "
	    file -b $OUTDIR/tmp.$OUTFILE0
	    cat $OUTDIR/tmp.$OUTFILE0
	    echo "501 Suspiciously small file ($FILESIZE bytes), deleting and exiting"
	    exit 1
	fi
	mv -v $OUTDIR/tmp.$OUTFILE0 $OUTDIR/$OUTFILE0
    else
	echo "501 Retrieval from '$WMS_URLBASE' failed"
	exit 1
    fi
fi






#CONVERT="../convert.sh"
if [ -f '../init-convert.sh' ]; then
  source '../init-convert.sh'
fi
convert="${Convert[*]}" 
echo $convert

if [ "$convert" == "" ]; then
    if [ $FORMAT == 'sh' ]; then
	exit 0
    fi
    #mv -v $OUTDIR/{tmp.,}$OUTFILE
    cp -v $OUTDIR/$OUTFILE0 $OUTDIR/$OUTFILE  # ln / rename?
else
    command="convert '$OUTDIR/$OUTFILE0' $convert '$OUTDIR/$OUTFILE'"
    if [ $FORMAT == 'sh' ]; then
	echo "# Image post-processing: " >> $OUTDIR/$OUTFILE
  	echo ${command%.*}".png' # or jpg, etc"  >> $OUTDIR/$OUTFILE
	echo >> $OUTDIR/$OUTFILE
	exit 0
    else
	eval $command
	if [ $? == 0 ]; then
	    exit 0
	else
	    rm -f $OUTDIR/$OUTFILE0
	    echo "501 Image conversion failed"
	    exit 1
	fi
	# rm -v $OUTDIR/tmp.$OUTFILE
    fi
fi

exit 










#-------------------------------------------------------


case "s$DATA" in
  euroc84)
     PARAM="layers=KAP:europe_country_wgs84&srs=EPSG:4326$bgcolor"
  ;;
  etop)
     PARAM="layers=KAP:world_etop2_100_wgs84&srs=EPSG:4326$bgcolor"
  ;;
  europl)
     PARAM="layers=KAP:europe_places_eureffin&srs=EPSG:3067"
     echo -e "Converting coordinates:\n$BBOX"
     #BBox2=( `echo -e ${BBox[0]} ${BBox[1]} \n ${BBox[2]}  ${BBox[3]} | cs2cs +proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs  +to  +proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs` )
     X="${BBox[0]} ${BBox[1]} \n ${BBox[2]}  ${BBox[3]}"
     echo -e $X
     BBox2=( `echo -e $X | cs2cs +proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs  +to  +proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs` )
     BBOX="${BBox2[0]},${BBox2[1]},${BBox2[3]},${BBox2[4]}"
  ;;
  maasto)
     echo -e "Converting coordinates:\n$BBOX"
     PARAM="layers=KAP:Maastokarttarasteri_50k_EPSG3067_25&srs=EPSG:3067&bgcolor=$BGCOLOR"
     X="${BBox[0]} ${BBox[1]} \n ${BBox[2]}  ${BBox[3]}"
     echo -e $X
     BBox2=( `echo -e $X | cs2cs +proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs  +to  +proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs` )
     BBOX="${BBox2[0]},${BBox2[1]},${BBox2[3]},${BBox2[4]}"
     echo -e "Got:\n$BBOX"
     echo 511
     #exit 1
  ;;
  *)
     PARAM="layers=KAP:${LAYERS//,/,KAP:}&srs=EPSG:4326$bgcolor"
  ;;
esac

