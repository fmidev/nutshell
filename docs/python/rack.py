#/!bin/python


#from optparse import OptionParser
import argparse
#os.environ["http_proxy"]="" #
# Change this to bool?
#parser.add_argument("-V", "--verbose", dest="VERBOSE", type=int, default=0, metavar="<level>", help="Print status messages to stdout")


# for shell escape
import subprocess 



parser = argparse.ArgumentParser()
# parser = OptionParser()
parser.add_argument("-o", "--outfile", dest="OUTFILE",
                  default="outfile.h5",
                  help="write output to file", metavar="<file>")

parser.add_argument("-i", "--infile", dest="INFILE",
                  default="infile.h5",
                  help="read input from file", metavar="<file>")

parser.add_argument("-v", "--verbose", dest="verbose", default=False, metavar="<level>",
                  help="Print status messages to stdout")

#(options, args) = parser.parse_args()
options = parser.parse_args()

if (not options):
    parser.print_help()
    exit(1)
    
if options.verbose:
    print options
    print args


#p = subprocess.Popen('ls -ltr *.h5', stdout=subprocess.PIPE, shell=True)
p = subprocess.Popen('rack '+options.INFILE, stdout=subprocess.PIPE, shell=True)

output, err = p.communicate()
#print output

# tai tulostus rivi kerrallaan
print "Stdout\n";
for i in output.split('\n'):
    print i

print "Errors\n";
print err
#for i in err.split('\n'):
#    print i
