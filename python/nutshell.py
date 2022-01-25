#!/bin/python3
# -*- coding: utf-8 -*-


"""

Main module -- nutshell.nutshell
==================================

This module contains ProductServer class, which receives product
requests and forwards them to product generators. A ProductServer
instance also manages disk resources defined in 
:ref:`configuration`.

The module uses :any:`nutshell.product`  for defining products (:any:`nutshell.product.Info`) 
and :any:`nutshell.request` for generating them using :any:`nutshell.request.Generator` . 

HTTP server provided by :any:`nutshell.httpd` essentially forwards
HTTP requests to :any:`nutshell.ProductServer`.



"""

__version__ = '1.0'
__author__ = 'Markus.Peura@fmi.fi'

import os
import time
import subprocess # for shell escape

from pathlib import Path
import shutil # for copy cmd  only...
from http import HTTPStatus
#import http.server
#HTTPresponses = http.server.SimpleHTTPRequestHandler.responses

import logging
logging.basicConfig(format='%(levelname)s\t %(name)s: %(message)s')
#logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.DEBUG)
#logging.basicConfig(format='%(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p')
#logging.basicConfig(format='%(asctime)s %(levelname)s %(name)s : %(message)s', datefmt='%Y%m%d%H:%M:%S')

from nutshell import nutils
from nutshell import product 
from nutshell import request

# from . import nutils
# from . import product 
# from . import request



class ProductServer:
    """Service designed for generating image and data products served as files 
    """

    PRODUCT_ROOT = '.'
    CACHE_ROOT = '.'
    STORAGE_ROOT = '.'
    TIME_DIR_SYNTAX = '{YEAR}/{MONTH}/{DAY}'
    SHELL_GENERATOR_SCRIPT = 'generate.sh'
    SHELL_INPUT_SCRIPT = 'input.sh'

    TIMEOUT = 90

    # HTTP Server Options (forward defs HTTP server, so perhaps later moved to NutServer )
    HTTP_PORT = 8088
    HTTP_NAME = ''
    HTTP_PREFIX = 'nutshell/' # TODO
    HTTP_ROOT = '.' 
    #HTML_TEMPLATE = 'template.html' 
    HTML_TEMPLATE = 'index.html' 

    stdout = subprocess.PIPE
    stderr = subprocess.PIPE

    #verbosity = 5
    logger = None

    counter = 0

    #error_code_regexp = re.compile("^\s*([0-9]+)\\.(zip|gz)$")
    supported_instructions = ['DELETE','EXISTS','MAKE','GENERATE','INPUTS','SHORTCUT','LATEST','LINK','MOVE','COPY']
    
    def init_path(self, dirname, verify=False):
        """ Expand relative path to absolute, optionally check that exists. """ 
        #if (hasattr(self, dirname)):
        path = Path(getattr(self, dirname)).absolute()
        self.logger.warning('  {0} =>  {1}'.format(dirname, path))
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
        # self._init_dir('HTTP_ROOT') # not here!
        # self._init_dir('HTTP_ROOT'+'/'+'HTML_TEMPLATE') # not here!
        
    #def read_conf(self, conffile = 'nutshell.cnf', strict=True):
    def read_conf(self, conffile = None):
        """
        Read given conf file, if it exists. Raise error, if strict.
        The entries are copied to respective member of self.
        """

        strict = True
        if not conffile:
            conffile = 'nutshell.cnf'
            strict   = False
            
        if (os.path.exists(conffile)):
            self.logger.info("Reading conf file {0} ".format(conffile))
            result = nutils.read_conf(conffile)
            # print(result)
            nutils.set_entries(self, result)
            return True
        elif strict:
            self.logger.error("Conf file not found: " + conffile)
            raise FileNotFoundError("Conf file not found: ", conffile)
        else:
            self.logger.debug("Local conf file not found (ok): " + conffile)
            #print ("Conf file not found: ", conffile)  
        return False

    # Rename... Missleading name.
    def get_status(self):  
        return nutils.get_entries(self)

    def get_cache_root(self):
        """Return the abolute path (Path) to CACHE_ROOT directory. """
        return Path(self.CACHE_ROOT).absolute()
    
    def get_storage_root(self):
        """Return the abolute path (Path) to CACHE_ROOT directory. """
        return Path(self.STORAGE_ROOT).absolute()
    
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
        # return self.PRODUCT_ROOT+os.sep+product_info.PRODUCT_ID.replace('.', os.sep)

   

    # Generalize?
    def ensure_output_dir(self, outdir):
        """
        Creates a writable directory, if non-existent

        Currently, uses mask 777
        """

        # Note: https://docs.python.org/3/library/os.html
        # version 3.7: The mode argument no longer affects the file permission bits of newly-created intermediate-level directories

        try:
            m = os.umask(0)
            #os.makedirs(str(outdir), 0o775, True)
            os.makedirs(outdir, 0o777, True)
        finally:
            os.umask(m)
        return outdir

#        
                   
    def get_input_list(self, product_info, directives, log):
        """ Used for reading dynamic input configuration generated by input.sh.
        directives determine how the product is generated. 
        """

        # TODO: directives

        input_query = request.InputQuery(self, product_info) # TODO: directives
        
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
                #r = self.make_request(input_prod_info, ['MAKE'], [], product_generator.log.getChild("input[{0}]".format(i)))
                r = self.make_request(input_prod_info, log = product_generator.log.getChild("input[{0}]".format(i)))
                # r = self.make_request(input_prod_info, log = product_generator.log)
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


    def query_file(self, pr):
        """
        Check if file exits or is under generation.

        A file is interpreted as being under generation if a corresponding,
        "relatively new" empty file exists.
        
        :param pr[nutshell.product.Info]: description of the product (string or nutshell.product.Info)
        """

            
        if (pr.path.exists()):  
            pr.log.debug('File exists: {0}'.format(pr.path))
            stat = pr.path.stat()
            age_mins = round((time.time() - stat.st_mtime) / 60)
            if (stat.st_size > 0): # Non-empty
                pr.product_obj = pr.path
                pr.log.info('File found (age {1}mins, size {2}): {0}'.format(pr.path, age_mins, stat.st_size))
                pr.set_status(HTTPStatus.OK)
            elif (age_mins > 10):
                pr.log.warning("Empty file found, but over 10 mins old...")
                # set status? WAIT?
            else:    
                pr.log.warning('BUSY (empty file, under generation?)') # TODO raise (prevent deletion)
                pr.product_obj  = '' # BUSY
                total_time = 0
                for i in range(1,10):
                    i = i*i
                    total_time += i                        
                    stat = pr.path.stat()
                    if (stat.st_size > 0): 
                        pr.set_status(HTTPStatus.OK)
                        pr.log.info("OK, finally received: {0}".format(pr.path))
                        return
                    else:
                        pr.log.info("Sleep {0} seconds...".format(i))
                        time.sleep(i)

                    if (total_time > self.TIMEOUT):
                        pr.log.warning("timeout ({0}s) exceeded".format(self.TIMEOUT))
                        break
                
                pr.set_status(HTTPStatus.REQUEST_TIMEOUT)
                # pr.set_status(HTTPStatus.SERVICE_UNAVAILABLE)  # 503
                # return 
        elif (pr.path_storage.exists()):
            pr.log.info('Stored file exists: {0}'.format(pr.path_storage))
            pr.log.info('Linking to: {0}'.format(pr.path))
            # LINK
            # pr.path.symlink_to(pr.path_storage.resolve())
            self.ensure_output_dir(pr.path.parent)
            nutils.symlink(pr.path, pr.path_storage)
            pr.set_status(HTTPStatus.OK)
        else:
            pr.log.debug('File not found: {0}'.format(pr.path))
            if (pr.product_info.TIMESTAMP == 'LATEST'):
                pr.log.warning("LATEST-file not found (cannot generate it)")
                pr.set_status(HTTPStatus.NOT_FOUND) 
                # return 
        
    def make_prod(self, pr, directives = None, TEST=False):
        """
        Main function.

        :param pr[nutshell.product.Info]: description of the product
        """

        #self.query_file(pr)
        #if (pr.status == HTTPStatus.OK):
        #    pr.returncode = 0
        #    return
        
        # only TEST at this point
        if (pr.script.exists()):
            pr.log.debug('Generator script ok: {0}'.format(pr.script))
        else:
            pr.log.warning('Generator script not found: {0}'.format(pr.script))
            # Consider case of copied valid product (without local generator)            
            if (not TEST):
                pr.set_status(HTTPStatus.NOT_IMPLEMENTED) # Not Implemented
                return
            #pr.path = Path()

        # TODO: if not stream?
        pr.log.debug('Ensuring cache dir for: {0}'.format(pr.path))
        self.ensure_output_dir(pr.path_tmp.parent)
        self.ensure_output_dir(pr.path.parent)
        if (not pr.path.exists()):
            pr.path.touch()
            
        # Runs input.sh
        #if (MAKE or INPUTS or CHECK):  
        pr.log.debug('Querying input list (dependencies)')
        input_info = pr.get_input_list(directives)
        if (input_info.returncode != 0):
            pr.log.debug('Input script problem, return code: {0}'.format(input_info.returncode))
            if (not TEST):
                pr.set_status(HTTPStatus.PRECONDITION_FAILED)
                pr.remove_files()
                return
        if (not TEST): 
            self.retrieve_inputs(pr)

        # MAIN
        pr.log.info('Generating:  {0}'.format(pr.path.name))
        pr.log.debug('Environment: {0}'.format(pr.env))

        try:
            pr.run2(directives)
        except KeyboardInterrupt:
            pr.log.warning('Hey, HEY! Keyboard interrupt on main level')
            pr.status = HTTPStatus.REQUEST_TIMEOUT
            pr.remove_files()
            raise
                

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
        
        pr.log.debug("Finally, move main product from tmp")
        if (pr.path_tmp.is_symlink()):
            pr.path.unlink()
            pr.path.symlink_to(pr.path_tmp.resolve())
        else:    
            pr.path_tmp.replace(pr.path)

        globber = "{0}*".format(pr.path_tmp.stem) # note: dot omitten on purpose
        pr.log.debug("Move remaiming (auxiliary) files from tmp: {0}".format(globber))
        #pr.log.warning(pr.path_tmp.parent)
        #pr.log.warning(pr.path_tmp.parent.glob(globber))
        for p in pr.path_tmp.parent.glob(globber):
            pr.log.debug("Moving {0}".format(p))
            #pr.log.debug("move {0}".format(p))
            p.replace(pr.path.parent.joinpath(p.name))
        
        pr.log.debug("Removing tmp dir: {0}".format(pr.path_tmp.parent))
        try:
            os.rmdir(pr.path_tmp.parent)
        except Exception as err:
            pr.log.error("RmDir failed: {0}".format(err))
            
        pr.set_status(HTTPStatus.OK)


        

    def make_request(self, product_info, instructions = ['MAKE'], directives = None, log = None):
        """
        Main function.

        :param product_info: description of the product (string or nutshell.product.Info)
        :param instructions: what should be done about the product
            (``MAKE``, ``DELETE``, ``CHECK`` , ``RETURN``), see :ref:`commands` .
        :param directives: how the product is generated etc 
        :param log: optional logging.logger 

        :returns: Instance of product.Generator that contains the path of 
            the file (if succefully generated) and information about the process.
        
        """

        if (type(instructions) == str):
            instructions = instructions.split(',')

        if (type(instructions) == list):
            #instructions = set(instructions)
            instructions = nutils.read_conf_text(instructions)
            
        if (type(directives) == str):
            directives = directives.split(',')

        if (type(directives) == list):
            directives = nutils.read_conf_text(directives)
            
        pr = request.Generator(self, product_info, log) #, instructions, directives, log)

        # Future option
        #if ('GENERATE' in instructions):
        #   instructions['DELETE'] = True
         #  instructions['MAKE'] = True
            
        # Boolean:
        LATEST   = ('LATEST' in instructions)
        SHORTCUT = ('SHORTCUT' in instructions)

        LINK = instructions.get('LINK') #   in instructions)
        COPY = instructions.get('COPY')  # in instructions) # directives)
        MOVE = instructions.get('MOVE') # in instructions) # directives)
         
        if (SHORTCUT or LATEST or LINK or COPY or MOVE):
            instructions['MAKE'] = True
            #instructions['CHECK'] = True
        
        # TODO: redesign
        DELETE = ('DELETE' in instructions) #or ('GENERATE' in instructions) 
        EXISTS = ('EXISTS' in instructions)
        MAKE   = ('MAKE'   in instructions) 
        GENERATE = ('GENERATE' in instructions) 
        INPUTS = ('INPUTS' in instructions)
        TEST   = ('CHECK'  in instructions)
                
        # LOG =    ('LOG'    in pr.directives)        
        # DEBUG =  ('DEBUG'  in pr.directives)        

        # MAIN
        if (DELETE or GENERATE):
            if (pr.path.is_file()):
                pr.path.unlink()
            if (pr.path.exists()):
                pr.log.warning(f"Could not delete file: {pr.path}") 
                pr.set_status(HTTPStatus.CONFLICT)
            else:
                pr.set_status(HTTPStatus.OK)
            # else ?

        if (EXISTS or MAKE):
            self.query_file(pr)
            """
            Check if file exits or is under generation.

            A file is interpreted as being under generation if a corresponding,
            "relatively new" empty file exists.  
            """
            if (pr.status == HTTPStatus.OK):
                pr.returncode = 0                
                #GENERATE = False
                EXISTS   = False # No second check needed
                #return
            elif MAKE:
                GENERATE = True
                EXISTS   = True
            else:
                pr.set_status(HTTPStatus.NOT_FOUND)
          

        # MAIN
        if (GENERATE):
            pr.log.info("Making/generating... {0}".format(pr.path.name)) 
            self.make_prod(pr, directives, TEST) 
        
        if (EXISTS):
            pr.log.info(f"Exists? {pr.path.name}")
            if (pr.path.exists()):
                pr.set_status(HTTPStatus.OK)
            else:
                pr.set_status(HTTPStatus.NOT_FOUND) 
        elif (INPUTS):
            pr.log.info("Inputs... {0}".format(pr.path.name))
            input_info = pr.get_input_list(directives)
            print(input_info.inputs)
        else:
            pr.log.info(f"No further main instructions for  {pr.path.name}") 

        if (pr.status != HTTPStatus.OK):
            pr.log.warning("Action status: {0} for: {1}".format(pr.status, instructions)) 
            pr.log.warning(pr.status) 
            pr.log.warning("Action failed: {0}".format(pr.path)) 
            return pr              
            
          
        try:

            if SHORTCUT: #and pr.product_info.TIMESTAMP:
                pr.log.info('SHORTCUT: {0}'.format(pr.path_static))
                self.ensure_output_dir(pr.path_static.parent)
                nutils.symlink(pr.path_static, pr.path)
     
            if LATEST:
                pr.log.info('LATEST: {0} '.format(pr.path_latest))
                self.ensure_output_dir(pr.path_latest.parent)
                nutils.symlink(pr.path_latest, pr.path, True)
                
        except Exception as err:
            pr.set_status(HTTPStatus.INTERNAL_SERVER_ERROR)
            pr.log.warning("Routine linking file failed: {0}".format(err))               


        try:

            if LINK:
                #COPY = directives['COPY']
                pr.log.info('Linking: {1} <=  {0}'.format(pr.path, LINK) )    
                path = Path(LINK)
                if (path.is_dir()): # shutil does not need this...
                    if (not path.exists()):
                        self.ensure_output_dir(path)
                    path = path.joinpath(pr.path.name)
                if (path.exists()):
                    path.unlink()
                    
                #shutil.copy(str(pr.path), str(COPY))
                nutils.symlink(path, pr.path, True)  

            if COPY:
                #COPY = directives['COPY']
                pr.log.info('Copying: {1} <=  {0}'.format(pr.path, COPY) )    
                path = Path(COPY)
                if (path.is_dir()): # shutil does not need this here either...
                    if (not path.exists()):
                        self.ensure_output_dir(path)
                    path = path.joinpath(pr.path.name)
                if (path.exists()):
                    path.unlink()
                shutil.copy(str(pr.path), str(COPY))
   
            if MOVE:
                #MOVE = directives['MOVE']
                pr.log.info('Moving: {1} <=  {0}'.format(pr.path, MOVE) )    
                # product_request.path.rename(options.MOVE) does not accept plain dirname
                path = Path(MOVE)
                if (path.is_dir()): # ...but shutil needs this
                    if (not path.exists()):
                        self.ensure_output_dir(path)
                    path = path.joinpath(pr.path.name)
                if (path.exists()):
                    path.unlink()
                shutil.move(str(pr.path), str(MOVE))

        except Exception as err:
            pr.set_status(HTTPStatus.INTERNAL_SERVER_ERROR)
            pr.log.warning("Copying/moving/linking file failed: {0}".format(err))               

        pr.log.info('Success: {0}'.format(pr.path))
              
        return pr
    


   
    @classmethod
    def get_arg_parser(cls, parser = None):
        """Populates parser with options of this class"""

        parser = product.Info.get_arg_parser(parser)
        # parser = argparse.ArgumentParser()

        #supported_instructions = 'DELETE,EXISTS,MAKE,GENERATE,INPUTS,SHORTCUT,LATEST,LINK,MOVE,COPY'
        
        parser.add_argument("-c", "--conf", dest="CONF",
                            default=None, # "nutshell.cnf", #ProductServer.CONF_FILE?
                            help="Read config file", 
                            metavar="<file>")
     
        parser.add_argument("-a", "--instructions", metavar="<string>",
                            dest="INSTRUCTIONS",
                            default="",
                            help=f"Comma-separated string of instructions: {ProductServer.supported_instructions}")

        parser.add_argument("-r", "--request", metavar="<string>",
                            dest="INSTRUCTIONS",
                            default="",
                            help="(Deprecating) Use --instructions")
    
        parser.add_argument("-d", "--delete",
                            dest="DELETE",
                            action="store_true",
                            #default=False,
                            help="Delete product file, same as --instructions DELETE")

        parser.add_argument("-e", "--exists",
                            dest="EXISTS",
                            action="store_true",
                            #default=False,
                            help="Check only if product exists")

        
        parser.add_argument("-i", "--inputList",
                            dest="INPUTS",
                            action="store_true",
                            help="list input for a product, same as --instructions INPUTS")
    
        parser.add_argument("-m", "--make",
                            dest="MAKE",
                            action="store_true",
                            #default=False,
                            help="Make product, same as --instructions MAKE")
    
        parser.add_argument("-g", "--generate",
                            dest="GENERATE",
                            action="store_true",
                            help="Generate product, same as --instructions DELETE,MAKE")
    
        parser.add_argument("-t", "--timeout",
                            dest="TIMEOUT",
                            default=90,
                            type=int,
                            help="Time limit for generating or waiting for a product")
    
        return parser    


        
if __name__ == '__main__':

    NUTSHELL_DIR = None
    if ("NUTSHELL_DIR" in os.environ):
        NUTSHELL_DIR = os.environ["NUTSHELL_DIR"]      
            
    product_server = ProductServer()

    logger = logging.getLogger('NutShell')
    logger.setLevel(logging.INFO)
    #logger.debug("parsing arguments")

    # ProductInfo.get_arg_parser(parser)
    parser = ProductServer.get_arg_parser()

    #supported_actions = 'DELETE,MAKE,GENERATE,INPUTS,SHORTCUT,LATEST,LINK,MOVE,COPY'

    parser.add_argument("PRODUCTS",
                        nargs='*',
                        help="Products to be requested")

    parser.add_argument("-S", "--shortcut",
                        dest="SHORTCUT",
                        action="store_true",
                        help="Add link from static to timestamped dir, equals -r SHORTCUT")

    parser.add_argument("-Z", "--latest",
                        dest="LATEST",
                        action="store_true",
                        help="Add link with timestamp replaced with 'LATEST', same as -r LATEST")
    
    parser.add_argument("-M", "--move", metavar="<path>",
                        dest="MOVE",
                        default='',
                        help="Move resulting file, equals -r MOVE=<path>")
    
    parser.add_argument("-L", "--link", metavar="<path>",
                        dest="LINK",
                        default='',
                        help="Link resulting file, equals -r LINK=<path>")

    parser.add_argument("-C", "--copy", metavar="<path>",
                        dest="COPY",
                        help="Copy resulting file, equals -r COPY=<path>")


    # Raise? Could be http default directives?
    parser.add_argument("-D", "--directives",
                        dest="DIRECTIVES",
                        default='',
                        help="pipe-separated app instructions: DOUBLE|SCHEME=TILE|...")
    
    
    options = parser.parse_args()
    
    if (not options):
        parser.print_help()
        exit(1)

    
    if (options.VERBOSE):
        options.LOG_LEVEL = "DEBUG"
        
    if (options.LOG_LEVEL):
        if hasattr(logging, options.LOG_LEVEL):
            logger.setLevel(getattr(logging, options.LOG_LEVEL))
        else:
            logger.setLevel(int(options.LOG_LEVEL))
    
    logger.debug(options)   
    logger.debug('NUTSHELL_DIR={0}'.format(NUTSHELL_DIR))   
    
    product_server.verbosity = int(options.VERBOSE)
    product_server.logger = logger # NEW
    product_info = product.Info()

    if (options.PRODUCT):
        options.PRODUCTS.append(options.PRODUCT)
        
    if (not options.PRODUCTS):
        logger.debug('Product(s) not defined')
        parser.print_help()
        exit(1)

        
    logger.info('Products: {0}'.format(options.PRODUCTS))
    
        

    if (options.CONF):
        product_server.read_conf(options.CONF)
    else:
        if not product_server.read_conf(): # "nutshell.cnf", False):  # Local, lenient
            if NUTSHELL_DIR:
                #logger.info('Reading ' + NUTSHELL_DIR + "/nutshell.cnf")
                product_server.read_conf(NUTSHELL_DIR + "/nutshell.cnf") #, False)
                
    logger.debug(product_server.get_status())
     
    instructions = {} # []

    if (options.INSTRUCTIONS):
        # instructions.extend(options.INSTRUCTIONS.split(','))
        instructions = nutils.read_conf_text(options.INSTRUCTIONS.split(',')) # No values using ','?
    
    # , 'TEST'
    #for i in ['DELETE', 'MAKE', 'GENERATE', 'INPUTS', 'SHORTCUT', 'LATEST', 'LINK', 'MOVE', 'COPY']:
    for i in ProductServer.supported_instructions:
        value = getattr(options, i)
        if (value): # Boolean ok, no numeric args expected, especially not zero
            instructions[i] = value
    
    if (not instructions):
        instructions['MAKE'] = True

    logger.info(f'Requests: {instructions}')

    directives = {}
    if (options.DIRECTIVES):
        # directives = nutils.read_conf_text(options.DIRECTIVES.split(',')) # whattabout comma in arg?
        # directives = nutils.read_conf_text(options.DIRECTIVES.split(',')) # whattabout comma in arg?
        directives =  nutils.read_conf_text(options.DIRECTIVES.split('|')) 
        logger.info('Directives: {0}'.format(directives))

    
    fail=False
    
    for PRODUCT in options.PRODUCTS:

        logger.info('PRODUCT={0}'.format(PRODUCT))
             
        product_info.set_product(filename = PRODUCT)
         
        if (product_info.PRODUCT_ID and product_info.FORMAT):
            
            product_request = product_server.make_request(product_info, instructions, directives) #, logger.getChild("make_request")
    
            if ('INPUTS' in instructions): # or (options.VERBOSE > 6):
                #nutils.print_dict(product_request.inputs)
                logger.warning("Inputs:")
                logger.info(product_request.inputs)
        
            logger.info(product_request.status)    
            if (product_request.status != HTTPStatus.OK):
                fail = True

        else:
            logger.warning('Could not parse product')
            fail = True
            #exit(1)

    if (fail):
        exit(1)
    else:
        exit(0)
