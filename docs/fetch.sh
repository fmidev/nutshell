scp mpeura@virga:/fmi/dev/products/cache/radar/rack/comp/LATEST_radar.rack.comp_CONF=FMIPPN*.h5 .
rack LATEST_radar.rack.comp_CONF=FMIPPN.h5       -Q DBZH --iResize 1078,1186 -o 1.png
rack LATEST_radar.rack.comp_CONF=FMIPPN,ANDRE.h5 -Q DBZH --iResize 1078,1186 -o 2.png
#xv koe.png koe2.png
convert -loop 0 -delay 40 1.png 2.png ppn-andre-anim.gif
