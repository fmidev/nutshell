
if [ "$CONF_NAME" == '' ]; then
    vt100echo yellow "This file should be source'd, not invoked directly"
    vt100echo red "No '$CONF_NAME' defined, exiting"
    exit 1
fi

DIR_START="www/$CONF_NAME"
vt100echo green "# Source: ${DIR_START}"
if [ ! -d "$DIR_START" ]; then
    vt100echo red "# Directory '${DIR_START}' does not exist - no installation candiates"
    exit 1
fi


# TODO: add to conf?
# TOMCAT=${TOMCAT:-'tomcat10'}

vt100echo green "# Creating directories under HTML_ROOT=${HTML_ROOT}"
echo

# %P without starting-point directory
for i in `find "www/$CONF_NAME" -type d -printf '%P\n'`; do
    vt100echo green,dim "# ${HTML_ROOT}/$i"
    #mkdir --parents --mode ug=rwx ${HTML_ROOT}/$i
    mkdir --parents ${HTML_ROOT}/$i
    # mkdir --mode is faulty!
    chmod ug=rwx ${HTML_ROOT}/$i
done
echo

# exit 0

vt100echo green "# Installing files..."
echo


for i in `find $DIR_START \( -type f -or -type l \) -printf '%P\n'`; do

    DIR_BASE=${i%%/*}

    if [ "$DIR_BASE" == 'conf' ]; then
	# configured later, below
	continue
    fi

    #  DST_DIR=${HTML_ROOT}/${i}
    #  DST_DIR=${DST_DIR%/*}
    #  if [ ! -d ${DST_DIR} ]; then
    #    vt100echo green "# Creating directory ${DST_DIR}"
    #	 mkdir -v --parents --mode g+rwx ${DST_DIR}
    #	 continue
    #  fi
    #  continue

    #  vt100echo green,dim "# ${HTML_ROOT}/$i"
    SRC=$DIR_START/$i
    DST=${HTML_ROOT}/$i
    FORMAT=${i##*.}
    # echo ${HTML_ROOT}/$i $FORMAT $DIR_NAME
    case $FORMAT in
	html|xml)

	    vt100echo green,dim "# Syntax check for XML (${FORMAT})"
	    xmllint --noout $SRC
	    critical_check
	    
	    backup_file $DST '10' # rotate 10 backups

	    vt100echo green "# ${SRC} -> ${DST}"
	    cat ${SRC} | envsubst > ${DST}
	    
	    ;;
	*~)
	    vt100echo cyan,dim "# Skip: ${SRC}"
	    ;;
	*)
	    vt100echo green "# ${SRC} -> ${DST}"
	    backup_file $DST '10' # rotate 10 backups
	    cp -u $SRC $DST
	    ;;
    esac
    
    echo
    
done




vt100echo green "# Configuring:"
echo


vt100echo green "# Writing Deployment Descriptor"
SRC="$DIR_START/conf/${TOMCAT}-${CONF_NAME}.xml"
DST="${CATALINA_DIR}/${CONF_NAME}.xml"
vt100echo green "# ${SRC} -> ${DST}"
xmllint --noout "$SRC"
critical_check
backup_file $DST '5' # rotate 5 backups
cat ${SRC} | envsubst > ${DST}
# Note: does not check syntax AFTER variable expansion -> todo?
echo


