#!/bin/python3
# -*- coding: utf-8 -*-
"""

Main module -- nutshell.nutshell
==================================

This module contains ProductServer class, which receives product
requests and forwards them to product generators. A ProductServer
instance also manages disk resources defined in 
:ref:`configuration`.

The module uses classes of :ref:`nutshell.product` for defining products 
( nutshell.product.Info ) and for generating them using 
nutshell.product.Generator. 

HTTP server provided by :ref:`nutshell.httpd` essentially forwards
HTTP requests to ProductServer.



"""

__version__ = '0.3'
__author__ = 'Markus.Peura@fmi.fi'

import os
import time
import subprocess # for shell escape

from pathlib import Path
from http import HTTPStatus
#import http.server
#HTTPresponses = http.server.SimpleHTTPRequestHandler.responses

import logging
logging.basicConfig(format='%(levelname)s\t %(name)s: %(message)s')
#logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.DEBUG)
#logging.basicConfig(format='%(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p')
#logging.basicConfig(format='%(asctime)s %(levelname)s %(name)s : %(message)s', datefmt='%Y%m%d%H:%M:%S')

from . import nutils
from . import product 



class ProductServer:
    """Service designed for generating image and data products served as files 
    """

    PRODUCT_ROOT = '.'
    CACHE_ROOT = '.'
    TIME_DIR_SYNTAX = '{YEAR}/{MONTH}/{DAY}'
    SHELL_GENERATOR_SCRIPT = 'generate.sh'
    SHELL_INPUT_SCRIPT = 'input.sh'

    # HTTP Server Options (forward defs HTTP server, so perhaps later moved to NutServer )
    HTTP_PORT = 8088
    HTTP_NAME = ''
    HTTP_PATH_PREFIX = 'nutshell/' # TODO
    HTML_ROOT = '.' 
    HTML_TEMPLATE = 'template.html' 

    stdout = subprocess.PIPE
    stderr = subprocess.PIPE

    #verbosity = 5
    logger = None

    counter = 0

    #error_code_regexp = re.compile("^\s*([0-9]+)\\.(zip|gz)$")

    
    def init_path(self, dirname, verify=False):
        """ Expand relative path to absolute, optionally check that exists. """ 
        #if (hasattr(self, dirname)):
        path = Path(getattr(self, dirname)).absolute()
        self.logger.warn('  {0} =>  {1}'.format(dirname, path))
        if (verify) and not (path.exists()):
            raise FileNotFoundError(__name__ + str(path))
        setattr(self, dirname, str(path)) # TODO -> Path obj
#        #else:
        #     raise KeyError   
            
        
    def __init__(self, conffile = ''): 
        self.logger = logging.getLogger("NutShell2")
        if (conffile):
            self.read_conf(conffile)
        if __name__ == '__main__':
            self.stdout = os.sys.stdout # discarded
            self.stderr = os.sys.stderr
        # self._init_dir('PRODUCT_ROOT')
        # self._init_dir('CACHE_ROOT')
        # self._init_dir('HTML_ROOT') # not here!
        # self._init_dir('HTML_ROOT'+'/'+'HTML_TEMPLATE') # not here!
        
    def read_conf(self, conffile = 'nutshell.cnf', strict=True):
        """Read given conf file, if it exists. Raise error, if strict."""
        if (os.path.exists(conffile)):
            self.logger.debug("reading conf file {0} ".format(conffile))
            result = nutils.read_conf(conffile)
            # print result
            nutils.set_entries(self, result)
            return True
        elif strict:
            self.logger.error("Conf file not found: " + conffile)
            raise FileNotFoundError("Conf file not found: ", conffile)
        else:
            self.logger.warning("Conf file not found: " + conffile)
            #print ("Conf file not found: ", conffile)  
        return False

    # Rename... Missleading name.
    def get_status(self):  
        return nutils.get_entries(self)

    def get_cache_root(self):
        """Return the abolute path (Path) to CACHE_ROOT directory. """
        return Path(self.CACHE_ROOT).absolute()
    
    def get_product_dir(self, product_info):
        """Return the directory containing product generator script 
            (generate.sh) and possible configurations etc"""
        return product_info.PRODUCT_ID.replace('.', os.sep)
    
    def get_time_dir(self, timestamp):
        if (type(timestamp) != str):
            timestamp = timestamp.TIMESTAMP # product_info.TIMESTAMP
        if (timestamp):
            if (timestamp == 'LATEST'):
                return ''
            else:
                timevars = product.parse_timestamp2(timestamp)
                # print timevars
                return self.TIME_DIR_SYNTAX.format(**timevars) # + os.sep
        else:
            return ''

    
    
    def get_generator_dir(self, product_info):
        path = Path(self.PRODUCT_ROOT, *product_info.PRODUCT_ID.split('.'))
        return str(path.absolute())
        #return self.PRODUCT_ROOT+os.sep+product_info.PRODUCT_ID.replace('.', os.sep)

   

    # Generalize?
    def ensure_output_dir(self, outdir):
        """Creates a writable directory, if non-existent
        """
        try:
            original_umask = os.umask(0)
            os.makedirs(str(outdir), 0o775, True)
        finally:
            os.umask(original_umask)
        return outdir

#        
                   
    def get_input_list(self, product_info, directives, log):
        """ Used for reading dynamic input configuration generated by input.sh.
        directives determine how the product is generated. 
        """

        # TODO: directives

        input_query = product.InputQuery(self, product_info) # TODO: directives
        
        if (not input_query.script.exists()):
            log.debug("No input script: {0}".format(input_query.script))         
            return input_query   
        
        # TODO generalize (how)
        log.debug(input_query.env)
        
        input_query.run(log_basename = input_query) # ??
     
        if (input_query.returncode == 0): 
            log.info(type(input_query.stdout))
            if (input_query.stdout == ''):
                log.warning("empty stdout of input declaration script {0}:".format(input_query.script))
            else:
                nutils.read_conf_text(input_query.stdout.split('\n'), input_query.inputs)
                log.info(input_query.inputs)
        else:
            log.warning("executing failed with error code={0}: {1} ".format(input_query.returncode, input_query.script))
            log.warning(input_query.stdout)
            log.warning(input_query.stderr)
            log.warning(input_query.log)
        #    else:
        #        log.critical("input script reported no error info")
                       
        return input_query
    
    def retrieve_inputs(self, product_generator):
        inputs = {}
        if (product_generator.inputs):
            product_generator.log.debug('Retrieving inputs for: ' + str(product_generator.path.name))
            for i in product_generator.inputs:
                #product_generator.log.info('INPUTFILE: ' + i)
                input = product_generator.inputs[i] # <filename>.h5
                #product_generator.error(i, input)
                product_generator.log.info('Make input: {0} ({1})'.format(i, input))
                input_prod_info = product.Info(filename = input)
                product_generator.log.info('Make input: {0} ({1})'.format(i, input_prod_info.PRODUCT_ID))
                r = self.make_request(input_prod_info, ['MAKE'], [], product_generator.log.getChild("input[{0}]".format(i)))
                if (r.path):
                    inputs[i] = str(r.path) # sensitive
                    product_generator.log.debug('Success: ' + str(r.path))
                else:
                    product_generator.log.warning('SKIPPED: ' + i) 
            if (not inputs):
                product_generator.log.warning('All input queries returned empty') 
        product_generator.inputs = inputs
        product_generator.env['INPUTKEYS'] = ','.join(sorted(product_generator.inputs.keys()))
        product_generator.env.update(product_generator.inputs)


    def make_request(self, product_info, actions = ['MAKE'], directives = None, log = None):
        """
        Main function.

        :param product_info: description of the product (string or nutshell.product.Info)
        :param actions: what should be done about the product 
            (``MAKE``, ``DELETE``, ``CHECK`` , ``RETURN``), see :ref:`commands` .
        :param directives: how the product is generated etc 
        :param log: optional logging.logger 

        :returns: Instance of product.Generator that contains the path of 
            the file (if succefully generated) and information about the process.
        
        """

        if (type(actions) == str):
            actions = actions.split(',')

        if (type(actions) == list):
            actions = set(actions)
            
        if (type(directives) == str):
            directives = directives.split(',')

        if (type(directives) == list):
            directives = nutils.read_conf_text(directives)
            
        #product_request = self.ProductRequest(self, product_info, actions, directives, log)
        # Consider rename to Generator
        pr = product.Generator(self, product_info, log) #, actions, directives, log)

        if ('GENERATE' in actions):
            actions.add('DELETE')
            actions.add('MAKE')
            
        LATEST = ('LATEST' in actions)
        LINK =   ('LINK'   in actions)  
        if (LINK or LATEST):
            actions.add('MAKE')          
        
        MAKE   = ('MAKE'   in actions)     
        
        INPUTS = ('INPUTS' in actions)     
        DELETE = ('DELETE' in actions)     
        CHECK  = ('CHECK'  in actions) 


       
        # LOG =    ('LOG'    in pr.directives)        
        # DEBUG =  ('DEBUG'  in pr.directives)        
        
        if (pr.path.exists()):  
            pr.log.debug('File exists: {0}'.format(pr.path))
            if (DELETE):
                pr.remove_files()
                pr.set_status(HTTPStatus.ACCEPTED)  #202 # Accepted
            elif (MAKE): # PATH_ONLY
                stat = pr.path.stat()
                age_mins = round((time.time() - stat.st_mtime) / 60)
                if (stat.st_size > 0): # Non-empty
                    pr.product_obj = pr.path
                    pr.log.info('File found (age {1}mins): {0}'.format(pr.path, age_mins))
                    if (not CHECK):
                        pr.set_status(HTTPStatus.OK)
                        return pr
                elif (age_mins > 10): # 10mins
                    pr.log.warn("Empty file found, but over 10 mins old...")
                else:    
                    pr.product_obj  = '' # BUSY
                    pr.log.warning('BUSY (empty file found)') # TODO riase (prevent deletion)
                    #pr.set_status(HTTPStatus.ACCEPTED)  # 202 Accepted
                    if (not CHECK):
                        pr.set_status(HTTPStatus.SERVICE_UNAVAILABLE)  # 503
                        return pr
        else:
            pr.log.debug('File not found: {0}'.format(pr.path))
            if (product_info.TIMESTAMP == 'LATEST'):
                pr.log.warn("LATEST-file not found (cannot generate it)")
                if (not CHECK):
                    pr.set_status(HTTPStatus.NOT_FOUND) 
                    return pr

        # only check at this point
        if (pr.script.exists()):
            pr.log.debug('Generator script ok: {0}'.format(pr.script))
        else:
            pr.log.warning('Generator script not found: {0}'.format(pr.script))
            # Consider case of copied valid product (without local generator)            
            pr.path = ''
            if (not CHECK):
                pr.set_status(HTTPStatus.NOT_IMPLEMENTED) # Not Implemented
                return pr
        

        # TODO: if not stream?
        if (MAKE):
            pr.log.debug('Ensuring cache dir for: {0}'.format(pr.path))
            self.ensure_output_dir(pr.path_tmp.parent)
            self.ensure_output_dir(pr.path.parent)
            pr.path.touch()
            
        # Runs input.sh
        if (MAKE or INPUTS or CHECK):
            pr.log.debug('Querying input list (dependencies)')
            input_info = pr.get_input_list(directives)
            if (input_info.returncode != 0):
                pr.log.debug('Input scipt problem, return code: {0}'.format(input_info.returncode))
                if (not CHECK):
                    pr.set_status(HTTPStatus.PRECONDITION_FAILED)
                    pr.remove_files()
                    return pr
            if (MAKE): 
                self.retrieve_inputs(pr)

        # MAIN
        if (MAKE):
            pr.log.info('Generating:  {0}'.format(pr.path))
            pr.log.debug('Environment: {0}'.format(pr.env))

            #logfile_level = logging.DEBUG #ERROR
            #if (DEBUG) or (LOG): 
            #    logfile_level = logging.DEBUG
            pr.run2(directives)

            if (pr.returncode != 0):
                pr.log.error("Error ({0}): '{1}'".format(pr.returncode, pr.error_info))
                pr.remove_files()
                return pr
                
            if (not pr.path_tmp.exists()):
                pr.log.error("generator did not create desired file")
                pr.remove_files()
                return pr
 
            if (pr.path_tmp.stat().st_size == 0):
                pr.log.error("generator failed (empty file intact)")
                pr.remove_files()
                return pr
            
            pr.log.debug("Final move from tmp")
            if (pr.path_tmp.is_symlink()):
                pr.path.unlink()
                pr.path.symlink_to(pr.path_tmp.resolve())
            else:    
                pr.path_tmp.replace(pr.path)
            pr.set_status(HTTPStatus.OK)
                
            try:
                if (LINK): #and pr.product_info.TIMESTAMP:
                    pr.log.info('Linking: {0}'.format(pr.path_static))
                    self.ensure_output_dir(pr.path_static.parent)
                    nutils.symlink(pr.path_static, pr.path)
         
                if (LATEST):
                    pr.log.info('LATEST: {0} '.format(pr.path_latest))
                    self.ensure_output_dir(pr.path_latest.parent)
                    nutils.symlink(pr.path_latest, pr.path, True)
            except:
                 pr.log.warn("Linking file failed")               
 
            
            pr.log.info('Success: {0}'.format(pr.path))
              
        return pr
    


   
    @classmethod
    def get_arg_parser(cls, parser = None):
        """Populates parser with options of this class"""

        parser = product.Info.get_arg_parser(parser)
        # parser = argparse.ArgumentParser()
 
        parser.add_argument("-c", "--conf", dest="CONF",
                            default=None, # "nutshell.cnf", #ProductServer.CONF_FILE?
                            help="read config file", 
                            metavar="<file>")
     
        parser.add_argument("-r", "--request", metavar="<string>",
                            dest="REQUEST",
                            default="",
                            help="comma-separated string of [DELETE|MAKE|INPUTS]")
    
        parser.add_argument("-d", "--delete",
                            dest="DELETE",
                            action="store_true",
                            #default=False,
                            help="delete product file, same as -r DELETE")
    
        parser.add_argument("-m", "--make",
                            dest="MAKE",
                            action="store_true",
                            #default=False,
                            help="make product, same as -r MAKE")
    
        parser.add_argument("-i", "--inputList",
                            dest="INPUTS",
                            action="store_true",
                            help="list input for a product, same as -r INPUTS")
    
        parser.add_argument("-D", "--directives",
                            dest="DIRECTIVES",
                            default='',
                            help="additional instructions: LOG,LINK,LATEST")
    
        return parser    


        
if __name__ == '__main__':

    NUTSHELL_DIR = None
    if ("NUTSHELL_DIR" in os.environ):
        NUTSHELL_DIR = os.environ["NUTSHELL_DIR"]      
            
    product_server = ProductServer()

    #print()
    #logger = logging.getLogger(__name__ + str(++product_server.counter))
    logger = logging.getLogger('NutShell')
    #logger.setLevel(30)
    logger.setLevel(logging.INFO)
    #logger.debug("parsing arguments")
    
    parser = ProductServer.get_arg_parser() # ProductInfo.get_arg_parser(parser)
    
    parser.add_argument("-M", "--move", metavar="<path>",
                            dest="MOVE",
                            default='',
                            help="move output file from cache")
    
    #(options, args) = parser.parse_args()
    options = parser.parse_args()
    #logger.warning(*options.__dict__)  
    
    if (not options):
        parser.print_help()
        exit(1)

    
    if (options.VERBOSE):
        options.LOG_LEVEL = "DEBUG"
        
    if (options.LOG_LEVEL):
        if hasattr(logging, options.LOG_LEVEL):
            #nutils.VERBOSITY_LEVELS[options.VERBOSE]
            logger.setLevel(getattr(logging, options.LOG_LEVEL))
        else:
            logger.setLevel(int(options.LOG_LEVEL))
    
    logger.debug(options)   
    logger.debug('NUTSHELL_DIR={0}'.format(NUTSHELL_DIR))   
    
    product_server.verbosity = int(options.VERBOSE)
    product_server.logger = logger # NEW
    product_info = product.Info()

    if (options.PRODUCT):
        product_info.set_product(filename = options.PRODUCT)
    else:
        logger.warning('Product not defined')
    
        

    if (options.CONF):
        product_server.read_conf(options.CONF)
    else:
        if not product_server.read_conf("nutshell.cnf", False):  # Local, lenient
            if NUTSHELL_DIR:
                logger.warning('Reading ' + NUTSHELL_DIR + "/nutshell.cnf")
                product_server.read_conf(NUTSHELL_DIR + "/nutshell.cnf", False)
                
    #if (options.VERBOSE > 4):
    #nutils.print_dict(product_server.get_status())
    logger.debug(product_server.get_status())
     
    actions = []

    if (options.REQUEST):
        actions.extend(options.REQUEST.split(','))

    if (options.MAKE) or not (actions):
        actions.append('MAKE')
    
    if (options.INPUTS):
        actions.append('INPUTS')
        
    if (options.DELETE):
        actions.append('DELETE')


    if (product_info.PRODUCT_ID and product_info.FORMAT):
        
        logger.info('Requests: {0}'.format(str(actions)))

        directives = []
        if (options.DIRECTIVES):
            #directives = nutils.read_conf_text(options.DIRECTIVES.split(',')) # whattabout comma in arg?
            directives = options.DIRECTIVES.split(',') # whattabout comma in arg?
            print(nutils.read_conf_text(directives))

        product_request = product_server.make_request(product_info, actions, directives) #, logger.getChild("make_request")
        #logger.debug(product_request)

        if ('INPUTS' in actions): # or (options.VERBOSE > 6):
            #nutils.print_dict(product_request.inputs)
            logger.info(product_request.inputs)
    
        logger.info(product_request.status)    
        
        if (options.MOVE):
            # product_request.path.exists()
            logger.info('Moving: {1} <=  {0}'.format(product_request.path, options.MOVE) )    
            product_request.path.rename(options.MOVE)
            
    else:
        logger.warning('Could not parse product')
        exit(1)
        #print('Could not parse product')
        
    exit(0)
