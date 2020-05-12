#!/bin/python3
# -*- coding: utf-8 -*-
"""
Product generator service appicable on the command line or as a library

Command line usage
==================

Basic format for all the command line invocations is::

    python3 -m nutshell.nutshell <nutshell-args>
    
Online help is optained with ``-h``::

    python3 -m nutshell.nutshell -h
    
Simple query using configuration file and product definition::

    python3 -m nutshell.nutshell -c nutshell/nutshell.cnf -m \\
      -p 201708121500_radar.rack.comp_SIZE=800,800_SITES=fiika_BBOX=20,62,30,70.png 



More details in `Overall Scheme`_


Using NutShell within python
============================

Simple example::

    import nutshell.nutshell as nuts

    # Initilize service
    server = nuts.ProductServer('nutshell/nutshell.cnf')

    # Retrieve / generate a product
    response = server.make_request("201708121600_radar.rack.comp_SITES=fikor,fivan,fiika_SIZE=800,800.png", "MAKE")

    # Results:
    print("Return code: {0} ".format(response.returncode))
    print("Status (HTTP code): {0}:  ".format(response.status))
    print("File path: {0} ".format(response.path))

    # Example: further processing (image data)
    from PIL import Image
    file = Image.open(response.path)
    print(file.info)


Status codes
============

NutShell recycles HTTP status codes in communicating success or failure
on operations. Especially:

=====  ============
Code   Enum Name
=====  ============
102    PROCESSING
200    OK
=====  ============

References:
- https://docs.python.org/3/library/http.html

Code documentation
==================

"""

__version__ = '0.2'
__author__ = 'Markus.Peura@fmi.fi'

import os
import re
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
#from . import nutproduct

from . import product as nprod



def parse_timestamp2(timestamp, result = {}):
    if (timestamp):
        t = re.sub("\W", "", timestamp)
        result['TIMESTAMP'] = t[0:12] # empty ok?
        result['YEAR']      = t[0:4]
        result['MONTH']     = t[4:6]
        result['DAY']       = t[6:8]
        result['HOUR']      = t[8:10]
        result['MINUTE']    = t[10:12]
    return result




#ProductInfo = nutproduct.ProductInfo


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
   
    @classmethod
    def get_arg_parser(cls, parser = None):
        """Populates parser with options of this class"""

        parser = nprod.Info.get_arg_parser(parser)
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

    
    def init_path(self, dirname, verify=False):
        """ Expand relative path to absolute, optionally check that exists. """ 
        #if (hasattr(self, dirname)):
        path = Path(getattr(self, dirname)).absolute()
        self.logger.warn('  {0} =>  {1}'.format(dirname, path))
        if (verify) and not (path.exists()):
            raise FileNotFoundError(__name__ + str(path))
        setattr(self, dirname, str(path)) # TODO -> Path obj
        #else:
        #     raise KeyError   
            
        
    def __init__(self, conffile = ''): 
        self.logger = logging.getLogger("NutShell2")
        if (conffile):
            self.read_conf(conffile)
        if __name__ == '__main__':
            self.stdout = os.sys.stdout # discarded
            self.stderr = os.sys.stderr
        #self._init_dir('PRODUCT_ROOT')
        #self._init_dir('CACHE_ROOT')
        # self._init_dir('HTML_ROOT') # not here!
        # self._init_dir('HTML_ROOT'+'/'+'HTML_TEMPLATE') # not here!
        
    def read_conf(self, conffile = 'nutshell.cnf', strict=True):
        if (os.path.exists(conffile)):
            self.logger.debug("reading conf file {0} ".format(conffile))
            result = nutils.read_conf(conffile)
            # print result
            nutils.set_entries(self, result)
        elif strict:
            self.logger.error("Conf file not found: " + conffile)
            raise FileNotFoundError("Conf file not found: ", conffile)
        else:
            self.logger.warning("Conf file not found: " + conffile)
            #print ("Conf file not found: ", conffile)  

    def get_status(self):  
        return nutils.get_entries(self)
    
    def get_product_dir(self, product_info):
        """Returns directory containing product generator script (generate.sh) and possible configurations etc"""
        return product_info.ID.replace('.', os.sep)
    
    def get_time_dir(self, timestamp):
        if (type(timestamp) != str):
            timestamp = timestamp.TIMESTAMP # product_info.TIMESTAMP
        if (timestamp):
            if (timestamp == 'LATEST'):
                return ''
            else:
                timevars = nprod.parse_timestamp2(timestamp)
                # print timevars
                return self.TIME_DIR_SYNTAX.format(**timevars) # + os.sep
        else:
            return ''

    
    
    def get_generator_dir(self, product_info):
        path = Path(self.PRODUCT_ROOT, *product_info.ID.split('.'))
        return str(path.absolute())
        #return self.PRODUCT_ROOT+os.sep+product_info.ID.replace('.', os.sep)

   

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

    def run_task(self, task, log):
        """Runs a task object containing task.script and task.stdout"""
        
        
        p = subprocess.Popen(str(task.script),
                             cwd=str(task.script.parent),
                             stdout=subprocess.PIPE, # always
                             stderr=task.stderr, # stdout for cmd-line and subprocess.PIPE (separate) for http usage
                             shell=True,
                             env=task.env)
                             
 # >       return self.run_process(p, task, log)
 # <  def run_process(self, p, task, log):
 
        if (not p):
            log.warn('No process') 
            task.returncode = -1
            task.status = HTTPStatus.NOT_FOUND
            return

        stdout,stderr = p.communicate()
        task.returncode = p.returncode        

        if (stdout):
            stdout = stdout.decode(encoding='UTF-8')
            if (p.returncode != 0):
                lines = stdout.strip().split('\n')
                task.error_info = lines.pop()
                log.warn(task.error_info)
                try:             
                    status = int(task.error_info.split(' ')[0])
                    task.status = HTTPStatus(status)
                except ValueError:
                    log.warn('Not HTTP error code: {0} '.format(task.status))
                    task.status = HTTPStatus.CONFLICT
        if (stderr):
            stderr = stderr.decode(encoding='UTF-8')
        task.stdout = stdout  
        task.stderr = stderr
        
                   
    def get_input_list(self, product_info, directives, log):
        """ Used for reading dynamic input configuration generated by input.sh.
        directives determine how the product is generated. 
        """

        #input_info = self.InputInfo(product_info)
        input_query = nprod.InputQuery(self, product_info)
        
        if (not input_query.script.exists()):
            log.debug("No input script: {0}".format(input_query.script))         
            return input_query   
        
        # TODO generalize (how)
        #env = product_info.get_param_env()
        log.debug(input_query.env)
        
        self.run_task(input_query, log)
        
#        p = subprocess.Popen(str(input_query.script),
#                             cwd=str(input_query.script.parent),
#                             stdout=subprocess.PIPE, # always
#                             stderr=self.stderr, # stdout for cmd-line and subprocess.PIPE (separate) for http usage
#                             shell=True,
#                             env=input_query.env)
#
#        self.run_process(p, input_query, log)  # log    

        if (input_query.returncode == 0): 
            #log.warning("inputsss")
            nutils.read_conf_text(input_query.stdout.split('\n'), input_query.inputs)
            log.info(input_query.inputs)
        else:
            log.warning("executing failed with error code={0}: {1} ".format(input_query.returncode, input_query.script))
            log.warning(input_query.error_info)
        #    else:
        #        log.critical("input script reported no error info")
                       
        return input_query
    

         
    def run_generator(self, product_request, params=None):
        """ Run shell script to generate a product. 
        
         stdout and stderr are used for output.
        
        Attributes:
          product_request -- state at beginning of transition.
          params -- attempted new state.
        """

        if (params == None):
            params = {}


        product_request.log.info('run_generator: ' + product_request.product_info.ID)
        product_request.log.debug(params)
    
        product_request.env = params
        self.run_task(product_request, product_request.log)

#        p = subprocess.Popen(str(product_request.script), #'./'+self.SHELL_GENERATOR_SCRIPT,
#                             cwd=str(product_request.script.parent),
#                             stdout=subprocess.PIPE,  #self.stdout,
#                             stderr=subprocess.STDOUT, # Use same stream as stdout, be it os.stdout or subprocess.STDOUT
#                             shell=True,
#                             env=params)
#              
#        self.run_process(p, product_request, product_request.log)              
        if (product_request.returncode != 0):
            if (product_request.stdout):
                log_file = Path(str(product_request.path)+'.stdout.log')
                product_request.log.warn('Writing STDOUT log: {0}'.format(log_file))            
                log_file.write_text(product_request.stdout)
            if (product_request.stderr):
                log_file = Path(str(product_request.path)+'.stderr.log')
                product_request.log.warn('Writing STDERR log: {0}'.format(log_file))            
                log_file.write_text(product_request.stderr)
            
        return product_request.returncode


    def make_request(self, product_info, actions = ['MAKE'], directives = None, log = None):
        """" Return path or log
        'MAKE'   - return the product, if in cache, else generate it and return
        'DELETE' - delete the product in cache
        'INPUTS' - generate and store the product, also regenerate even if already exists
        """
        #product_request = self.ProductRequest(self, product_info, actions, directives, log)
        pr = nprod.Request(self, product_info, actions, directives, log)
#        return self.handle_request(product_request)
#   def handle_request(self, pr):
        """" Return path or log
        'MAKE'   - return the product, if in cache, else generate it and return
        'DELETE' - delete the product in cache
        'INPUTS' - generate and store the product, also regenerate even if already exists
        """
        
        if (pr.path.exists()):  
            pr.log.debug('File exists: {0}'.format(pr.path))
            if ('DELETE' in pr.actions):
                pr.log.info('Deleting...')
                pr.path.unlink()
                pr.set_status(HTTPStatus.ACCEPTED)  #202 # Accepted
            elif ('MAKE' in pr.actions): # PATH_ONLY
                if (pr.path.stat().st_size > 0):
                    pr.product = pr.path
                    pr.log.info('Non-empty result file found: ' + str(pr.path))
                    pr.set_status(HTTPStatus.OK)
                else:
                    pr.product = '' # BUSY
                    pr.log.warning('BUSY') # TODO riase (prevent deletion)
                    pr.set_status(HTTPStatus.ACCEPTED)  #202 # Accepted
                return pr
        else:
            pr.log.debug('File not found: {0}'.format(pr.path))

        # only check at this point
        #if (os.path.exists(pr.generator_script)):
        if (pr.script.exists()):
            pr.log.debug('Generator script ok: {0}'.format(pr.script))
        else:
            pr.log.warning('Generator script not found: {0}'.format(pr.script))
            # Consider case of copied valid product (without local generator)            
            pr.path = ''
            pr.set_status(HTTPStatus.NOT_IMPLEMENTED) # Not Implemented
            return pr
        

        # TODO: if not stream?
        params = {}
        if ('MAKE' in pr.actions):
            pr.log.debug('Ensuring cache dir for: {0}'.format(pr.path))
            self.ensure_output_dir(pr.path_tmp.parent)
            self.ensure_output_dir(pr.path.parent)

            # what about true ENV?
            params = pr.product_info.get_param_env() # {})
            params['OUTDIR']  = str(pr.path_tmp.parent)
            params['OUTFILE'] = pr.path.name
            #os.mknod(pr.path) # = touch
            pr.path.touch()
            
        # Runs input.sh
        if ('MAKE' in pr.actions) or ('INPUTS' in pr.actions):
            input_info = self.get_input_list(pr.product_info, pr.directives, pr.log.getChild('get_input_list'))
            if (input_info.returncode == 0):
                pr.inputs = input_info.inputs
            else:
                #         pr.log.warn('Not HTTP error code: {0} '.format(status))
                pr.set_status(HTTPStatus.CONFLICT)
                pr.log.info('Removing: {0} '.format(pr.path))
                pr.path.unlink()
                return pr

        if ('MAKE' in pr.actions): 
            pr.log.debug('Retrieving inputs for: ' + str(pr.path.name))
            inputs = {}
            for i in pr.inputs:
                #pr.log.info('INPUTFILE: ' + i)
                input = pr.inputs[i] # <filename>.h5
                input_prod_info = nprod.Info(input)
                pr.log.info('Make input: {0} ({1})'.format(i, input_prod_info.ID))
                r = self.make_request(input_prod_info, ['MAKE'], [], pr.log.getChild("input[{0}]".format(i)))
                if (r.path):
                    inputs[i] = str(r.path) # sensitive
                    pr.log.debug('Success: ' + str(r.path))
                else:
                    pr.log.warning('SKIPPED: ' + i) 
            # warn if  none succeeded?
            pr.inputs = inputs
            #pr.log.extend2(pr.inputs , 'INPUTPATH: ')
            params['INPUTKEYS'] = ','.join(pr.inputs.keys())
            params.update(pr.inputs)

        # MAIN
        if ('MAKE' in pr.actions):
            pr.log.info('Generating: {0}'.format(pr.path))
            self.run_generator(pr, params)

            if (pr.returncode != 0):
                pr.log.error("generator failed")
                return pr
                
            if (pr.path_tmp.stat().st_size == 0):
                pr.log.error("generator failed")
                return pr
            
            pr.log.debug("Final move from tmp")
            pr.path_tmp.replace(pr.path)
            pr.set_status(HTTPStatus.OK)
                
            try:
                if ('LINK' in pr.directives): #and pr.product_info.TIMESTAMP:
                    pr.log.info('LINK: {0} '.format(pr.path_static))
                    self.ensure_output_dir(pr.path_static.parent)
                    nutils.symlink(pr.path_static, pr.path)
         
                if ('LATEST' in pr.directives):
                    pr.log.info('LATEST: {0} '.format(pr.path_latest))
                    self.ensure_output_dir(pr.path_latest.parent)
                    nutils.symlink(pr.path_latest, pr.path, True)
            except:
                 pr.log.warn("Linking file failed")               
 
            if ('DEBUG' in pr.directives) or ('LOG' in pr.directives):
                logfile = Path(str(pr.path) + '.log')
                pr.log.info('Saving log: {0} '.format(logfile))
                try:
                    logfile.write_text(pr.stdout) #.decode(encoding='UTF-8'))            
                except:
                    pr.log.warn("Saving log failed")               
                
        return pr
    




        
if __name__ == '__main__':

    product_server = ProductServer()

    #print()
    #logger = logging.getLogger(__name__ + str(++product_server.counter))
    logger = logging.getLogger('NutShell')
    #logger.setLevel(30)
    logger.setLevel(logging.INFO)
    #logger.debug("parsing arguments")
    
    parser = ProductServer.get_arg_parser() # ProductInfo.get_arg_parser(parser)
    
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
    
    product_server.verbosity = int(options.VERBOSE)
    product_server.logger = logger # NEW
    product_info = nprod.Info()

    if (options.PRODUCT):
        product_info.parse_filename(options.PRODUCT)
    else:
        logger.warning('Product not defined')
    
        

    if (options.CONF):
        product_server.read_conf(options.CONF)

    #if (options.VERBOSE > 4):
    #nutils.print_dict(product_server.get_status())
    logger.debug(product_server.get_status())
     
    request = []
    
    if (options.INPUTS):
        request.append('INPUTS')

    if (options.REQUEST):
        request.append(options.REQUEST.split(','))
        
    if (options.DELETE):
        request.append('DELETE')


    if (options.MAKE) or not (request):
        request.append('MAKE')

    if (product_info.ID and product_info.FORMAT):
        
        logger.info('Requests: {0}'.format(str(request)))

        directives = []
        if (options.DIRECTIVES):
            #directives = nutils.read_conf_text(options.DIRECTIVES.split(',')) # whattabout comma in arg?
            directives = options.DIRECTIVES.split(',') # whattabout comma in arg?

        product_request = product_server.make_request(product_info, request, directives, logger.getChild("make_request"))
        #logger.debug(product_request)

        if ('INPUTS' in request): # or (options.VERBOSE > 6):
            #nutils.print_dict(product_request.inputs)
            logger.info(product_request.inputs)

        #        if (product_request.status.value >= 500):
        #            print (product_request.stdout)
        #            print (product_request.stderr)
        #            logger.critical(product_request.status)
        #            exit(5)
        #        elif (product_request.status.value >= 400):
        #            print (product_request.stdout)
        #            print (product_request.stderr)
        #            logger.error(product_request.status)
        #            exit(4)
        #        elif (product_request.status.value >= 300):
        #            print (product_request.stdout)
        #            print (product_request.stderr)
        #            logger.warning(product_request.status)
        #            exit(3)
        #        else:            
        logger.info(product_request.status)    
            
            
    else:
        logger.warning('Could not parse product')
        exit(1)
        #print('Could not parse product')
        
    exit(0)
