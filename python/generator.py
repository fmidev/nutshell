#!/bin/python3
# -*- coding: utf-8 -*-
"""
Product generator service appicable on the command line or as a library

Code documentation
==================

"""

__version__ = '0.2'
__author__ = 'Markus.Peura@fmi.fi'


import subprocess # for shell escape

from   pathlib import Path
from   http    import HTTPStatus

import logging
logging.basicConfig(format='%(levelname)s\t %(name)s: %(message)s')



class Task:
    """Something that has a script and stdin, stdout, env and log"""

    script = None
    stdout = None
    stderr = None
    log    = None
    env    = None
    returncode = 0
    
    def __init__(self, script, env=None, log=None):
        
        if (type(script) == str):
            self.script = Path(script) 
        else:
            self.script = script

        self.stdout = subprocess.PIPE
        self.stderr = subprocess.PIPE

        if log:
            self.log = log
        else:
            self.log = logging.getLogger('Task')

        if env:
            self.env = env
        else:
            self.env = {}

        if (self.script.exists()):
            if (False):  # TODO Exec p.stat().st_mode & 0o77
                self.log.debug('Script exist, but is not executable: {0}'.format(self.script))
        else:
            self.log.debug('Script does not exist: {0}'.format(self.script))
            
        self.returncode = 0
            
    def run(self):
        """Runs a task object containing task.script and task.stdout"""
        
        
        p = subprocess.Popen(str(self.script),
                             cwd=str(self.script.parent),
                             stdout=self.stdout, # always
                             stderr=self.stderr, # stdout for cmd-line and subprocess.PIPE (separate) for http usage
                             shell=True,
                             env=self.env)
                            
 
        if (not p):
            self.log.warn('Could not run process: {0}'.format(self.script)) 
            self.returncode = -1
            self.status = HTTPStatus.NOT_FOUND
            return

        stdout,stderr = p.communicate()
        self.returncode = p.returncode        

        if (stdout):
            stdout = stdout.decode(encoding='UTF-8')
            if (p.returncode != 0):
                lines = stdout.strip().split('\n')
                self.error_info = lines.pop()
                self.log.warn(self.error_info)
                try:             
                    status = int(self.error_info.split(' ')[0])
                    self.status = HTTPStatus(status)
                except ValueError:
                    self.log.warn('Not HTTP error code: {0} '.format(self.status))
                    self.status = HTTPStatus.CONFLICT
        if (stderr):
            stderr = stderr.decode(encoding='UTF-8')
        self.stdout = stdout  
        self.stderr = stderr


        
if __name__ == '__main__':

    logger = logging.getLogger('NutShell')
    logger.setLevel(logging.INFO)
    
    #parser = ProductServer.get_arg_parser() # ProductInfo.get_arg_parser(parser)
    
    #(options, args) = parser.parse_args()
#    options = parser.parse_args()
#    
#    if (not options):
#        parser.print_help()
#        exit(1)
#
#    
#    if (options.VERBOSE):
#        options.LOG_LEVEL = "DEBUG"
#        
#    if (options.LOG_LEVEL):
#        if hasattr(logging, options.LOG_LEVEL):
#            logger.setLevel(getattr(logging, options.LOG_LEVEL))
#        else:
#            logger.setLevel(int(options.LOG_LEVEL))
#    
#    logger.debug(options)   
    
    exit(0)
