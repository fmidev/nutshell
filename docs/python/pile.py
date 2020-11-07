#!/usr/bin/python3

# Python image processing based utility.
# Enter "pile.py -help" for info.

# Markus.Peura@fmi.fi
# 2006 (c) Finnish Meteorological Institute
# V1.2 (Mar 2008)
# Formerly "composite.py", now renamed for avoiding confusions.

import logging
logging.basicConfig(format='%(levelname)s\t %(name)s: %(message)s')

log = logging.getLogger('Pile')
# log = logging.getLogger(__name__)
log.setLevel(logging.DEBUG)

#import Image
import os   # for environment variables 
import sys  # for command-line arguments
import math # for gamma correction

from PIL import Image
from argparse import ArgumentParser

#os.environ["http_proxy"]="" #
log.debug("Registering arguments")

parser = ArgumentParser(usage="usage: %(prog)s img [img2 ...] args ")

parser.add_argument("IMAGE", nargs='*', 
    help="Input image file",
    metavar="<filename> [<filename>]")

#parser.add_argument('foo', nargs='+')
#parser.parse_args(['a', 'b'])

parser.add_argument("-o", "--output_filename", 
    dest="output_filename",
    help="write resulting image to file", 
    metavar="<file>")

#                  default="test.jpg",

parser.add_argument("-I", "--interpolation", 
    dest="INTERPOLATION",
    default=Image.BILINEAR, #Image.ANTIALIAS,
    help="Interpolation method, 1=bilinear etc, see PIL manual. ", 
    metavar="<index>")

parser.add_argument("-Q", "--quad", 
    dest="QUAD",
    help="Transform area of four cornerpoints to rectangle.", 
    metavar="<x1,y1,...,x8,y8>")

parser.add_argument("-g", "--gamma", 
    dest="GAMMA",
    help="Gamma correction.", 
    metavar="<gamma-value>")

parser.add_argument("-r", "--rescale", 
    dest="RESCALE",
    type=str,
    help="Rescale intensities x = a+bx.", 
    metavar="<a:b,a:b,...>")

parser.add_argument("-m", "--remap", 
    dest="REMAP",
    help="Map intensity x to y.", 
    metavar="<x,y>")

parser.add_argument("-c", "--crop", 
    dest="CROP",
    help="Crop image", 
    metavar="<dx,dy,width,height>")

parser.add_argument("-s", "--size", 
    dest="SIZE",
    help="Resize image", 
    metavar="<width [,height]>")


parser.add_argument("-p", "--palette", 
    dest="PALETTE",
    help="Apply palette", 
    metavar="<imagefile>")

# NEW
#parser.add_argument("-w", "--width", dest="WIDTH",
#                  help="Resize image width", metavar="width")

# NEW
parser.add_argument("-a", "--aspect_ratio", 
    dest="ASPECT_RATIO",
    default = 1.0,
    type = float,
    help="For deriving image HEIGHT from WIDTH.", 
    metavar="<aspect_ratio>")

parser.add_argument("-A", "--alpha", 
    dest="ALPHA",
    help="Create alpha channel (polynomial).", 
    metavar="<x1,x2>")

parser.add_argument("-b", "--background", 
    dest="BACKGROUND",
    help="Add backround.",
    metavar="<file>") 

#parser.add_argument("-T", "--alphafile", dest="ALPHAFILE",
#                  help="Create alpha channel (polynomial).", metavar="<x1,x2>")

parser.add_argument("-f", "--fill", 
    dest="FILL",
    help="Fill with color.",
    metavar="<r,g,b>") 
    #type="int"

parser.add_argument("-M", "--mask", 
    dest="MASK",
    help="Use alpha as mask to mix image with a color.",
    metavar="<r,g,b>") 


parser.add_argument("-R", "--rotate", 
    dest="ROTATE",
    help="Rotate image", 
    metavar="<degrees>")

# Change this...
parser.add_argument("-v", "--verbose", 
    type=int,
    dest="verbose", 
    default=0, 
    metavar="<level>",
    help="Print status messages to stdout")
    #    action="store_false", dest="verbose", default=True,

parser.add_argument("-t", "--test", 
    dest="TEST",
    type=str,
    help="Parse command string", 
    metavar="command args")



#(args, args) = parser.parse_args()
args = parser.parse_args()

if args.verbose:
    print (args)
    #print (args)
    #    print args[0]

if args.TEST:
    s = args.TEST
    log.warn(f"Replacing args: {s}")
    s = s.replace('&', ' -') #.replace('=', ' ')
    log.warn(f"Replacing args: {s}")
    args = parser.parse_args(s.split())

image_count = len(args.IMAGE)

if image_count == 0:
    log.warn("No images given (as positional arguments)")
    parser.print_help()
    print ("\n Examples:\n")
    print (" alpha.png --alpha 255,-1 --fill 255,128,0   # Creates orange image")
    exit



# Global switch
interpolation = int( args.INTERPOLATION )

# Image list.
Im = []

# The bounding box of the first image loaded.
Bbox=()

# Load images, ensuring same size with the first image.
for i in range( image_count ):
    if args.verbose:
        print ('Loading image', args.IMAGE[i])
    if (image_count < 3) and (i==0) :
        Im.append( Image.open( args.IMAGE[i] ))
    else:
        Im.append( Image.open( args.IMAGE[i] ).convert('L') )
#    Im.append( Image.open( args[i] ).split() )
    if Bbox == ():
        Bbox = Im[i].size 
    else:
        Im[i] = Im[i].resize( Bbox, interpolation )




GAMMA=()
if args.GAMMA:
    GAMMA=args.GAMMA.split(",")
    for i in range( len(GAMMA) ):
        if i < image_count:

            g = float(GAMMA[i])

            # If negative, invert first.
            if (g < 0):
                Im[i] = Im[i].point(lambda x: 255 - x)

            # Then, apply gamma correction.
            g = abs(g)
            if (g != 1.0) and (g != 0.0):
                if args.verbose:
                    print ('Gamma correcting image ',i, args.IMAGE[i])
                Im[i] = Im[i].point(lambda x: 255.0*math.pow(x/255.0,1.0/g) )
             

# todo: check with ALPHA
RESCALE=()
if args.RESCALE:
    RESCALE = args.RESCALE.split(",")
    for i in range( len(RESCALE) ):
        if i < image_count:

            r = RESCALE[i].split(":")
            a = float(r[0])
            b = float(r[1])

            if (a > 0) | (b != 1):
                Im[i] = Im[i].point(lambda x: a + b*float(x))


#if args.verbose:
log.info(f'image_count: {image_count}')
    
if image_count == 1:
    im = Im.pop().convert("RGBA")
    #im = Im[0]

if image_count == 2:
    # pick reference to alpha
    alpha = Im.pop()
#    if args.verbose:
#        print 'Read alpha ', alpha
    im = Im.pop().convert("RGB")
    im.putalpha(alpha)
#    if args.verbose:
#        print 'Composed RGBA image ', im

if image_count == 3:
    im = Image.merge("RGB", Im )

if image_count == 4:
    im  = Image.merge("RGBA", Im )



REMAP = ()
if args.REMAP:
    REMAP = args.REMAP.split(',')
    if (len(REMAP) == 2):
        x = int(REMAP[0])
        y = int(REMAP[1])
        im = im.point(lambda i: (i==x) * y + (i!=x)*i )
        

if args.CROP:
    CROP = args.CROP.split(',')
    if ( CROP ):
        for i in range(len(CROP)):
            CROP[i] = int( CROP[i] )
        CROP[2] += CROP[0]
        CROP[3] += CROP[1]
        print (CROP)
        im = im.crop( CROP )
        # Can be further resized, see below.



if args.SIZE:
    SIZE = args.SIZE.split(',')
    if (len(SIZE) == 2):
        SIZE = ( int(SIZE[0]), int(SIZE[1]) )
    else:
        SIZE = ( int(SIZE[0]), int(int(SIZE[0])*float(args.ASPECT_RATIO)) )
else:
    SIZE = Bbox
    # Resizing is finally carried out in transform() or resize(), see below.


if args.QUAD:
    QUAD = args.QUAD.split(',')
    if (QUAD):
        for i in range(len(QUAD)):
            QUAD[i] = int( QUAD[i] )
        # PIL 1.1.3 Limitation (take second best then):
        if interpolation == Image.ANTIALIAS:
            im = im.transform( SIZE, Image.QUAD, QUAD, Image.BICUBIC ) 
        else:
            im = im.transform( SIZE, Image.QUAD, QUAD, interpolation ) 
else:        
    if args.SIZE:
        im = im.resize( SIZE, interpolation ) 


# Move up?
if args.ROTATE:
# Future extension (interpolation as a second argument)
#    ROTATE = args.ROTATE.split(',')
    if (args.ROTATE):
        angle = int( args.ROTATE )
# Future extension     
#        if len(ROTATE) == 2:
#            interpolation = int(ROTATE[1])
#        else:
#            interpolation = Image.ANTIALIAS
        im = im.rotate( angle , interpolation )


# See below
if args.ALPHA:
    gray = im.convert('L')

    #gray = im.convert('L')

# not sure if correct place here
if args.PALETTE:
    palette = Image.open(args.PALETTE).convert("RGB")
    if (palette.size[0] > palette.size[1]):
        palette = palette.resize((256,1))
    else :
        palette = palette.resize((1,256))

    palette = list(palette.getdata())
    palette2 = []
    for i in range( len(palette) ):
        palette2.extend(palette[i])
# NEW        
    im = im.convert('L')
    im.putpalette(palette2)

# change later? NOtice im[0]
ALPHA=()
if args.ALPHA:
    ALPHA = args.ALPHA.split(',')
    a0 = float(ALPHA[0])
    a1 = float(ALPHA[1])
    #gray = im.convert('L')
    alpha = gray.point(lambda i: a0 + a1*i )
    im.putalpha(alpha)

# See below
if args.FILL:
    c = args.FILL.split(',')
    c = (int(c[0]),int(c[1]),int(c[2]))
    #log.warn(im)
    #if (im.size) > 0:
    if (im):
        im.paste(c)
    if (alpha):
        im.putalpha(alpha)

#
if args.MASK:
    c = args.MASK.split(',')
    #print col1
    base1 = Image.new('RGB', im.size, (int(c[0]),int(c[1]),int(c[2])))
    base2 = Image.new('RGB', im.size, (int(c[3]),int(c[4]),int(c[5])))
    im = Image.composite(base1,base2,im.convert('L'))
#    im.composite(im,im,im)

if args.BACKGROUND:
    #
    bg = Image.open(args.BACKGROUND)
    bg.paste(im, mask=im.split()[-1])
    im = bg
    #im = im.split()
    #im2 = [i for i in im]
    #mask = im2.pop()
    #im = Image.open(args.BACKGROUND)
    #im.paste(Image.merge("RGB", im2), mask=mask)

if args.output_filename:
    im.save(args.output_filename)

if __name__ == '__main__':
    pass


