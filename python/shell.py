#!/bin/python3
# -*- coding: utf-8 -*-
"""

Escape from Python -- nutshell.shell
------------------------------------

This module contains utilities for running shell scripts.

"""

__version__ = '0.2'
__author__ = 'Markus.Peura@fmi.fi'


import subprocess # shell escape
import os         # pid

from   pathlib import Path
from   http    import HTTPStatus

import logging
logging.basicConfig(format='%(levelname)s\t %(name)s: %(message)s')



class Task:
    """Something that has a script, stdin, stdout, env, and log."""

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
            
            
    def run(self, logfile_basename=None, logfile_level = logging.ERROR, directives=None):
        """Runs a task object containing task.script and task.stdout
        
        :param logfile_basename: Leading part of logs, '.err' and '.out' will be added.
        :param logfile_level: Save stderr and stdout to logs, if
            this threshold is at least logging.ERROR and logging.DEBUG, respecively 
        :param directives[dict]: Additional instructions to the script 
        :returns: Return code of the process. Will be also stored to ``self``.
        
        """
        
        env = {}
        env.update(self.env)        
        if (directives):
            env.update(directives)
        
        p = subprocess.Popen(str(self.script),
                             cwd=str(self.script.parent),
                             stdout=self.stdout, # always
                             stderr=self.stderr, # stdout for cmd-line and subprocess.PIPE (separate) for http usage
                             shell=True,
                             env=env)           
 
        if (not p):
            self.log.warn('Could not run process: {0}'.format(self.script)) 
            self.returncode = -1
            self.status = HTTPStatus.NOT_FOUND
            return

        stdout,stderr = p.communicate()
        self.returncode = p.returncode        


        # TODO: test also stderr
        if (logfile_basename):
            logfile_basename = str(logfile_basename)
        else:
            logfile_basename = 'nutshell-{0}'.format(os.getpid())      

        # Add sensitivity
        if (p.returncode != 0):
            logfile_level -= 10

        print ("LOG LEVEL: ", logfile_level)            
            
        if (stdout):
            self.stdout = stdout.decode(encoding='UTF-8').strip()
            if (p.returncode != 0):
                lines = self.stdout.split('\n')
                self.error_info = lines.pop()
                self.log.warn('Error code: {0}'.format(p.returncode))
                self.log.warn('Error msg:  {0}'.format(self.error_info))
                try: 
                    status = int(self.error_info.split(' ')[0])
                    self.status = HTTPStatus(status)                   
                except ValueError:
                    self.log.warn('Could not extract numeric HTTP error code from: {0} '.format(self.error_info))
                    self.status = HTTPStatus.CONFLICT
                #except FileError:
                #    self.log.error('Failed writing log: {0} '.format(log_stdout))
            if (logfile_level <= logging.DEBUG):
                log_stdout = Path(logfile_basename + ".stdout")
                self.log.info('Dumping log: {0}'.format(log_stdout))
                log_stdout.write_text(self.stdout)
                    
        if (stderr):
            self.stderr = stderr.decode(encoding='UTF-8')
            if (logfile_level <= logging.WARN):
                log_stderr = Path(logfile_basename + ".stderr")
                self.log.warn('Dumping log: {0}'.format(log_stderr))
                log_stderr.write_text(self.stderr)
                
        return  p.returncode  
        

        
if __name__ == '__main__':

    logger = logging.getLogger('Shell')
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
