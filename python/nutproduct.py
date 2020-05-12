#!/bin/python
# -*- coding: utf-8 -*-
"""
Information container of a product generated with nutshell.ProductServer
"""

__version__ = '0.2'
__author__ = 'Markus.Peura@fmi.fi'


import re
import argparse

#import nutshell.nutils



def parse_timestamp(timestamp, result = {}):
    if (timestamp):
        t = re.sub("\W", "", timestamp)
        result['TIMESTAMP'] = t[0:12] # empty ok?
        result['YEAR']      = t[0:4]
        result['MONTH']     = t[4:6]
        result['DAY']       = t[6:8]
        result['HOUR']      = t[8:10]
        result['MINUTE']    = t[10:12]
    return result



class ProductInfo:
    """Stores basic product information: product id, timestamp (if applicable)
    file format, and specific product parameters.
    
    Technically independent of environment.
    Does not store local information or configurations (system side paths etc.).

    Attributes:
        previous -- state at beginning of transition
        next -- attempted new state
        message -- explanation of why the specific transition is not allowed

    """

    TIMESTAMP = ''
    ID = ''
    PARAMETERS = None #{}
    #PARAMS = None #[]
    FORMAT = ''
    COMPRESSION = ''
    EXTENSION = ''

    # \w = [a-zA-Z0-9_]

    # Resolve compression, if any. Plain compression not accepted, base format has to appear.
    # compressionRe = re.compile("^(.*\\.[a-z][a-z0-9]*)\\.(zip|gz)$")
    compressionRe = re.compile("^(.*)\\.(zip|gz)$")

    # Resolve extension = <format>.<compression>
    # extensionRe = re.compile("^([a-z][a-z0-9]*)(\\.(zip|gz))?$")
    extensionRe = re.compile("^((.*)\\.)?([a-z][a-z0-9]*)$")    

    # Resolve TIMESTAMP, PRODUCT_ID, PARAMETERS
    filenameRe = re.compile("^(([0-9]+)_)?([^_]+)(_(.*))?\\.([a-z][a-z0-9]*)$")

    prodRe = re.compile("^([a-z][a-z0-9]*)(\.[a-z][a-z0-9]*)*$")

    @classmethod
    def get_arg_parser(cls, parser = None):
        """Populates parser with options of this class"""

        if (not parser):
            parser = argparse.ArgumentParser()

        
        #verbose_keys = '|'.join(nutshell.nutils.VERBOSITY_LEVELS.keys())
        #Out[70]: 'NOTSET,CRITICAL,INFO,WARNING,ERROR,DEBUG'

        parser.add_argument("-l", "--log_level", metavar='DEBUG|INFO|ERROR|CRITICAL',
                            dest="LOG_LEVEL",
                            type=str,
                            default="",
                            help="Print status messages to stdout")
 
        parser.add_argument("-v", "--verbose",
                            dest="VERBOSE",
                            action="store_true",
                            help="Same as --log_level 0 : print all status messages")

        parser.add_argument("-f", "--product_filename", metavar="<filename>",
                            dest="OUTFILE",
                            default="",
                            help="product to be handled")

        parser.add_argument("-p", "--product_id", metavar="<id>",
                            dest="PRODUCT",
                            default="",
                            help="product to be handled")

        # Consider leaving "rare" or specific params in-class
        parser.add_argument("-s", "--set", metavar="[<id>|<filename>]",
                            dest="SET",
                            default="",
                            help="product to be handled, recognises argument type")

        parser.add_argument("-F", "--format", metavar="[<file_format>]",
                            dest="FORMAT",
                            default="",
                            help="sets product format explicitly, called with -p")

        return parser


    def set_id(self, product_id):
        """Set the product ID string consisting of alphanumeric chars and periods.""" 
        if (self.prodRe.match(product_id)):
            self.ID = product_id
        else:
            raise NameError('Value not accepted as product id: {}'.format(product_id))

    def set(self, product, **kwargs):
        """Configure a product. 
        
        Arguments:
        product -- a product ID string or a filename
        kwargs  -- keyword arguments completing the product definition
        """
        if (self.prodRe.match(product)):
            self.ID = product
            self.set_parameters(kwargs)
        else:
            self.parse_filename(product)
            # todo warn if duplicates in kwargs?
            self.set_parameters(kwargs)
    
    def set_timestamp(self, timestamp):
        """Set UTC time in numeric format '%Y%m%d%H%M' or its punctuated variants.
        
        Essentially, the timestamp will be stored as a string of 
        12 digits, "202003291845" for example.
        
        The numbers have to be in order year, month, day, hour, and minute.
        Non-digits will be simply removed.
        For example, 2020/03/29 18:45 is pruned to 202003291845.
        Consequently, possible _time zones_ will be also discarded. 
        
        Todo: support for time object, unix seconds and date string parsing.
        """
        
        self.TIMESTAMP = re.sub("\W", "", timestamp)

    def set_format(self, extension):
        """Sets file format (png, txt, pgm.gz, txt.zip, ...)."""
        
        m = ProductInfo.compressionRe.match(extension)
        if (m):
            # replace None's with empty string
            m = m.groups('')
            # redo with compression stripped
            self.set_format(m[0])
            self.COMPRESSION = m[1]
            self.EXTENSION = self.FORMAT+'.'+self.COMPRESSION
        else:
            m = ProductInfo.extensionRe.match(extension)
            print (m)
            if (m):
                # replace None's with empty string
                m = m.groups('')
                self.FORMAT = m[2]
                self.EXTENSION = self.FORMAT
                self.COMPRESSION = ''
            else:
                raise SyntaxError("could nut parse: " + extension)
        print (self.__dict__)

    # NOTE: remove PARAMS!
    # Consider typing (int, str)
    def set_parameter(self, key, value=''):
        """Set any parameter, including FORMAT, excluding TIMESTAMP and ID
        
            Arguments:
                key -- a string identfying of a parameter or the product
                value -- the value of the parameter, possibly not 
                    string but stringifiable
        """

        if (key == 'FORMAT'):
            # warn?
            self.FORMAT = value
            # todo: EXTENSION, COMPR, TIMESTAMP?
            return

        if (self.PARAMETERS == None):
            self.PARAMETERS = {}

        self.PARAMETERS[key] = value

    def set_parameters(self, params):
        """Given a dictionary of parameters, set values.
        
        """
        for k,v in params.items():
            self.set_parameter(k, v)


    def parse_filename(self, filename):
        """Derive product parameters from a filename."""

        m = self.compressionRe.match(filename)
        if (m):
            # print 'Compression: {0}'.format(m.group(3))
            self.COMPRESSION = m.group(2)
            filename = m.group(1)

        m = self.filenameRe.match(filename)
        if (m):

            #print (m.groups()) # !DEBUG

            # Time variables
            if (m.group(2)):
                self.set_timestamp(m.group(2))
                #self.TIMESTAMP =  re.sub("\W", "", m.group(2))
            else:
                self.set_timestamp('')

            # Product id
            self.ID = m.group(3)

            # Product specific parameters
            #if (len(m.groups()) > 5):
            if (m.group(5)):
                # pindex = 0
                for e in m.group(5).split('_'): #self.PARAMS:
                    entry = e.split('=')
                    self.set_parameter(*entry)
                    #if (len(entry) == 1):
                    #    self.set_parameter('P'+str(pindex), entry[0])

            # Format, excluding optional COMPRESSION (parsed above)
            self.FORMAT = m.group(6)
            if (self.COMPRESSION):
                self.EXTENSION = self.FORMAT + '.' + self.COMPRESSION
            else:
                self.EXTENSION = self.FORMAT
            
        else:
            print ("{0}: ERROR in parsing {1}").format(__name__, filename)

        return self


    def filename(self):
        body = []
        if (self.TIMESTAMP):
            body.append(self.TIMESTAMP)
        body.append(self.ID)

        #if (self.PARAMS):
        #    body.append("_".join(self.PARAMS))

        if (self.PARAMETERS):
            for key,value in sorted(self.PARAMETERS.items()):
                if (value == ''):
                    body.append(key)
                else:
                    body.append("{0}={1}".format(key, value))
        return("_".join(body) + '.' + self.FORMAT)

    def filename_latest(self):
        body = ['LATEST', self.ID]
        if (self.PARAMETERS):
            for key,value in sorted(self.PARAMETERS.items()):
                if (value == ''):
                    body.append(key)
                else:
                    body.append("{0}={1}".format(key, value))
        return("_".join(body) + '.' + self.FORMAT)


    def get_param_env(self, env=None):
        """ Append or create dict with TIMESTAMP and params"""
        """ Uses PRODUCT instead of ID which is too general (example: "radar.rack.comp")"""
        if (env == None):
            env = {}

        # Start with these (and override with 'system' variables below).
        env.update(self.PARAMETERS)

        if (self.TIMESTAMP):
            parse_timestamp(self.TIMESTAMP, env)

        env['PRODUCT'] = self.ID

        for i in ['FORMAT', 'COMPRESSION', 'EXTENSION']:
            env[i] = getattr(self, i, "")

        return env

    # Todo: params
    def __init__(self, product = ''):
        self.PARAMETERS = {}
        if (product):
            self.set(product)

    def __call__(self):
        print('Print Something')


if __name__ == '__main__':

    #print()

    # parser = argparse.ArgumentParser()
    parser = ProductInfo.get_arg_parser()

    options = parser.parse_args()

    if (not options):
        parser.print_help()
        exit(1)

    
    product_info = ProductInfo()

    if (options.OUTFILE):
        product_info.parse_filename(options.OUTFILE)

    if (options.PRODUCT):
        product_info.set_id(options.PRODUCT)

    if (options.SET):
        args = {'SIZE': [640,400], 'FORMAT': 'png', 'VERSION': 2.0}
        product_info.set(options.SET, **args)

    if (product_info.ID):
        if (options.FORMAT):
            product_info.set_format(options.FORMAT)
        print(product_info.filename())
        print(product_info.get_param_env()) # contains ID
        #print(product_info.__dict__) # contains ID
        exit(0)
    else:
        print('Error: Product not given')
        parser.print_help()
        exit(1)

