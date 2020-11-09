#!/bin/python3
# -*- coding: utf-8 -*-
"""

Product definitions and requests -- nutshell.product
====================================================


"""

__version__ = '0.2'
__author__ = 'Markus.Peura@fmi.fi'

#import os
import re
#import subprocess # for shell escape

import argparse

#from pathlib import Path
#from http import HTTPStatus
#import http.server
#HTTPresponses = http.server.SimpleHTTPRequestHandler.responses

import logging
logging.basicConfig(format='%(levelname)s\t %(name)s: %(message)s')
#logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.DEBUG)
#logging.basicConfig(format='%(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p')
#logging.basicConfig(format='%(asctime)s %(levelname)s %(name)s : %(message)s', datefmt='%Y%m%d%H:%M:%S')

from . import nutils
#from . import shell


def parse_timestamp2(timestamp, result = None):
    
    if (result == None):
        result = {}
        
    if (timestamp):
        t = re.sub(r"\W", "", timestamp)
        result['TIMESTAMP'] = t[0:12] # empty ok?
        result['YEAR']      = t[0:4]
        result['MONTH']     = t[4:6]
        result['DAY']       = t[6:8]
        result['HOUR']      = t[8:10]
        result['MINUTE']    = t[10:12]
        
    return result


"""
nutshell.product.Info!!
-----------------------
"""
class Info:
    """Stores product information, especially ``PRODUCT_ID``, 
    ``TIMESTAMP`` (if applicable), file ``FORMAT``, 
    and product-specific free parameters.
    
    **Parameters** handled as with Info.set_product(). Key parameters are 
    mutually exclusive.
          
    Examples of allowed calls, with ``Info = nutshell.product.Info`` :
        >>> p = Info(filename="my.image.product.png")
        >>> p = Info(product_id="my.image.product")
        >>> p = Info("my.image.product", **{"SIZE": "640,400"})

    A plain string argument is ambiguous, and raises an exception:
        >>> p = Info("my.image.product")       # Looks understandable, but...
        >>> p = Info("my.image.product.jpeg")  # ... is this a product ID or file?
        >>> p = Info("my.image.product_.jpeg") # This could be parsed as a file.
       
    This class is essentially a string parser and storage for parsed variables.
    It is technically independent of server configurations 
    like system side paths. Especially, it does not check if 
    product generation directories or scripts exist.

    """
    
    log = None

    TIMESTAMP = ''
    PRODUCT_ID = ''
    PARAMETERS = None #{}
    INPUT_PARAMETERS = None
    #PARAMS = None #[]
    FORMAT = ''
    COMPRESSION = ''
    EXTENSION = ''

    # \w = [a-zA-Z0-9_]

    # Resolve compression, if any. Plain compression not accepted, base format has to appear.
    # compressionRe = re.compile("^(.*\\.[a-z][a-z0-9]*)\\.(zip|gz)$")
    compressionRe = re.compile(r"^(.*)\.(zip|gz)$")

    # Resolve extension = <format>.<compression>
    # extensionRe = re.compile("^([a-z][a-z0-9]*)(\\.(zip|gz))?$")
    extensionRe = re.compile(r"^((.*)\.)?([a-z][a-z0-9]*)$")    

    # Resolve TIMESTAMP, PRODUCT_ID, PARAMETERS
    # filenameRe = re.compile("^((LATEST|[0-9]+)_)?([^_]+)(_(.*))?\\.([a-z][a-z0-9]*)$")
    filenameRe = re.compile(r"^((LATEST|TIMESTAMP|[0-9]*)_)?([^_]+)(_(.*))?\.([a-z][a-z0-9]*)$")

    prodRe = re.compile(r"^([a-z][a-z0-9]*)(\.[a-z][a-z0-9]*)*$")


    def set_product(self, product=None, filename=None, product_id=None, **kwargs):
        """Configure a product.

        :param product: product id string, requires explicit kwargs
        :param product_id: product id string.
        :param filename: product description presented as a filename
        
        :returns: instance (possibly incomplete, to be adjusted with separate 
             method calls) 
    
        See :ref:`product.Info`. Parameters equivalent at initialisation.
        """
        
        self.FORMAT = 'xxx'
        self.PARAMETERS = {}
        
        if (product):

            if (filename): # easiest
                # self.log.warn
                if (self.prodRe.match(product)): # 2020/10/19, .match(filename) was: match(product) 
                    print("first argument ({0}) looks like a product_id")
                raise KeyError("first argument ({0}) excludes explicit 'filename' arg")                

            if (product_id): # easiest
                # self.log.warn
                if (self.prodRe.match(product)):
                    print("first argument ({0}) could be a product_id")
                else:
                    print("first argument looks like a file?")
                print("first argument ({0}) excludes explicit 'product_id' arg")

            if (not kwargs):
                raise KeyError("first argument requires explicit kwargs") #reconsider this

            # Ok...
            product_id = product  # Note!
            
            
        if (filename) and (product_id): 
            raise KeyError("both 'filename' and 'product_id' given")
            #return
            
        if (filename):  # easiest
            self. _parse_filename(filename)
        elif (product_id):
            self.set_id(product_id)
            
        self.set_parameters(kwargs)
        return self
  

    def set_id(self, product_id):
        """Set the product ID string consisting of alphanumeric chars and periods.""" 
        if (self.prodRe.match(product_id)):
            self.PRODUCT_ID = product_id
        else:
            raise NameError('Value not accepted as product id: {}'.format(product_id))
   
    def set_timestamp(self, timestamp):
        """
        Set UTC time in numeric format '%Y%m%d%H%M', 'LATEST', or 'TIMESTAMP'
        
        The 12 digit-numeric format may contain punctuation as long as the
        order of the time units is not changed.
        Non-digits will be simply removed.
        For example, 2020/03/29 18:45 is pruned to 202003291845.
        Consequently, possible *time zones* will be also discarded. 
        
        Future versions may support for time object, unix seconds and date string parsing.
        """
        if (timestamp != 'LATEST') and (timestamp != 'TIMESTAMP'):
            timestamp = re.sub("[^0-9]*", "", timestamp)
        self.TIMESTAMP = timestamp  # re.sub("\W", "", timestamp)

    def set_format(self, extension):
        """Sets file format (png, txt, pgm.gz, txt.zip, ...)."""
        
        m = Info.compressionRe.match(extension)
        if (m):
            # replace None's with empty string
            m = m.groups('')
            # redo with compression stripped
            self.set_format(m[0])
            self.COMPRESSION = m[1]
            self.EXTENSION = self.FORMAT+'.'+self.COMPRESSION
        else:
            m = Info.extensionRe.match(extension)
            # print (m)
            if (m):
                # replace None's with empty string
                m = m.groups('')
                self.FORMAT = m[2]
                self.EXTENSION = self.FORMAT
                self.COMPRESSION = ''
            else:
                raise SyntaxError("could nut parse: " + extension)
        # print (self.__dict__)

    # NOTE: remove PARAMS!
    # Consider typing (int, str)
    def set_parameter(self, key, value=''):
        """Set any parameter, including FORMAT, excluding TIMESTAMP and ID
        
            :param key: name (string) of the parameter
            :param value: the value of the parameter, possibly not 
                string but hopefully stringifiable
        """

        if (key == 'FORMAT') or (key == 'EXTENSION'):
            self.set_format(value)
            return
            
        if (key == 'COMPRESSION'):
            self.EXTENSION = str(self.FORMAT)+'.' + value
            return

        if (key == 'TIMESTAMP'):
            self.set_timestamp(value)
            return
                                    
        if (key == 'PRODUCT_ID'):
            # WARN about
            raise KeyError("Special member PRODUCT_ID cannot be set with this function")
 
        if (self.PARAMETERS == None):
            self.PARAMETERS = {}

        self.PARAMETERS[key] = value

    def set_parameters(self, params):
        """Given a dictionary of parameters, set values.
        
        """
        for k,v in params.items():
            self.set_parameter(k, v)


    def  _parse_filename(self, filename):
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
                if (m.group(1)):
                    self.log.warn("Missing TIMESTAMP? (Filename with leading '_'.)")
                self.set_timestamp('')

            # Product id
            self.PRODUCT_ID = m.group(3)

            # Product specific parameters
            #if (len(m.groups()) > 5):
            if (m.group(5)):
                # pindex = 0
                for e in m.group(5).split('_'): #self.PARAMS:
                    entry = e.split('=')
                    if (entry[0] == ''):
                        print ("INPUT_PARAMETERS => {0}".format(self.PARAMETERS))
                        self.INPUT_PARAMETERS = self.PARAMETERS                        
                        self.PARAMETERS = {}
                    else:
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
            #print ("{0}: ERROR in parsing {1}").format(__name__, filename)
            print ("ERROR in parsing {0}".format(filename))

        return self


    def get_filename(self):
        body = []
        if (self.TIMESTAMP):
            body.append(self.TIMESTAMP)
        body.append(self.PRODUCT_ID)

        if (self.INPUT_PARAMETERS):
            for key,value in sorted(self.INPUT_PARAMETERS.items()):
                if (value == ''):
                    body.append(key)
                else:
                    body.append("{0}={1}".format(key, value))
            if (self.PARAMETERS):
                body.append(':')

        if (self.PARAMETERS):
            for key,value in sorted(self.PARAMETERS.items()):
                if (value == ''):
                    body.append(key)
                else:
                    body.append("{0}={1}".format(key, value))

        return("_".join(body) + '.' + self.EXTENSION) # FORMAT)

    def get_static_filename(self):
        body = [self.PRODUCT_ID]
        if (self.PARAMETERS):
            for key,value in sorted(self.PARAMETERS.items()):
                if (value == ''):
                    body.append(key)
                else:
                    body.append("{0}={1}".format(key, value))
        return("_".join(body) + '.' + self.EXTENSION) # FORMAT)

    def get_filename_latest(self):
        body = ['LATEST', self.PRODUCT_ID]
        if (self.PARAMETERS):
            for key,value in sorted(self.PARAMETERS.items()):
                if (value == ''):
                    body.append(key)
                else:
                    body.append("{0}={1}".format(key, value))
        return("_".join(body) + '.' + self.EXTENSION) #FORMAT)
 
    #MEMBER_ENV_RE = re.compile("[A-Z]+[A-Z_]*")
    MEMBER_ENV_RE = re.compile("[A-Z]+[A-Z_]*")

    def get_param_env(self):
        env = nutils.get_entries(self, self.MEMBER_ENV_RE, str)   #Tasklet.MEMBER_ENV_RE
        env['PARAMETERS'] = '' # overwrite kludge
        if (self.INPUT_PARAMETERS):
            env.update(self.INPUT_PARAMETERS) 
        if (self.PARAMETERS):
            env.update(self.PARAMETERS)  # overrides INPUT_PARAMETERS
        if (self.TIMESTAMP):
            parse_timestamp2(self.TIMESTAMP, env)
        env['PRODUCT'] = self.PRODUCT_ID # temporary (rack-tile)
        return env
    

    # Todo: params
    def __init__(self, product=None, filename=None, product_id=None, **kwargs):
        """See :ref:`product.Info`. Parameters equivalent with those of set_product()."""
        self.set_product(product, filename, product_id, **kwargs)
        self.log = logging.getLogger('Info')
        self.log.setLevel(logging.WARN)
            


    @classmethod
    def get_arg_parser(cls, parser = None):
        """Populates parser with options of this class"""

        if (not parser):
            parser = argparse.ArgumentParser()


        parser.add_argument("-l", "--log_level", metavar='DEBUG|INFO|ERROR|CRITICAL',
                            dest="LOG_LEVEL",
                            type=str,
                            default="",
                            help="Print status messages to stdout")
 
        parser.add_argument("-v", "--verbose",
                            dest="VERBOSE",
                            action="store_true",
                            help="Same as --log_level 0 : print all status messages")

        #        parser.add_argument("-f", "--product_filename", metavar="<filename>",
        #                            dest="OUTFILE",
        #                            default="",
        #                            help="product to be handled")

        parser.add_argument("-p", "--product", metavar="[<filename>|<id>]",
                            dest="PRODUCT",
                            default="",
                            help="product to be handled")
        # consider -o OUTFILE!

        return parser
        



if __name__ == '__main__':

    # parser = argparse.ArgumentParser()
    parser = Info.get_arg_parser()

    # Consider leaving "rare" or specific params in-class
    parser.add_argument("-s", "--set", metavar="[<id>|<filename>]",
                        dest="SET",
                        default="",
                        help="product to be handled, recognises argument type")

    parser.add_argument("-T", "--timestamp", metavar="[<%Y%m%d%H%M>|'LATEST']",
                        dest="TIMESTAMP",
                        default="",
                        help="Adjust time stamp")

    parser.add_argument("-F", "--format", metavar="[<file_format>]",
                        dest="FORMAT",
                        default="",
                        help="sets product format explicitly, called with -p")

    options = parser.parse_args()

    if (not options):
        parser.print_help()
        exit(1)

    logger = logging.getLogger(__name__)
    logger.setLevel(30)
    
    product_info = Info()

    if (options.VERBOSE):
        options.LOG_LEVEL = "DEBUG"
        
    if (options.LOG_LEVEL):
        if hasattr(logging, options.LOG_LEVEL):
            #nutils.VERBOSITY_LEVELS[options.VERBOSE]
            logger.setLevel(getattr(logging, options.LOG_LEVEL))
        else:
            logger.setLevel(int(options.LOG_LEVEL))
    
    logger.debug(options)   
    
 
    #if (options.OUTFILE):
    #    product_info. _parse_filename(options.OUTFILE)

    #if (options.PRODUCT):
    #    product_info.set_id(options.PRODUCT)
    if (options.PRODUCT):
        product_info.set_product(filename = options.PRODUCT)
    #    product_info. _parse_filename(options.PRODUCT)
    else:
        logger.warning("product not defined")
 

    if (options.SET):
        args = {'SIZE': [640,400], 'FORMAT': 'png', 'VERSION': 2.0}
        #product_info.set_product(options.SET, **args)
        product_info.set_product(options.SET, **args)

    if (options.TIMESTAMP):
        product_info.set_timestamp(options.TIMESTAMP)

    if (options.FORMAT):
        product_info.set_format(options.FORMAT)

    if (product_info.PRODUCT_ID):
        print(product_info.get_filename())
        print(product_info.get_static_filename())
        print(product_info.get_param_env()) # contains ID
        #print(product_info.__dict__) # contains ID
        exit(0)
    else:
        print('Error: Product not given')
        parser.print_help()
        exit(1)

    
    