#!/bin/bash


source ./defaults.sh

# Fractal clouds
echo CLOUD="${TIMESTAMP}_demo.image.ppmforge_MODE=clouds_GRAY=True_DIMENSION=2_SIZE=${SIZE}.png"

# Diagonal hatch pattern
echo HATCH="demo.image.pattern_SIZE=${SIZE}_PATTERN=LEFT45.png"

# European Map
echo MAP="demo.map_BBOX=${BBOX}_SIZE=${SIZE}_CONF=geoserver_PROJ=4326.png"
