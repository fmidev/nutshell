#!/bin/python
# -*- coding: utf-8 -*-
"""
Utilities -- nutshell.nutils
----------------------------

Utilities for handling objects and configuration files. 

"""

__version__ = '0.1'
__author__ = 'Markus.Peura@fmi.fi'

import re
#from pathlib import Path


def read_conf(path, result = None): 
    """Read plain-text configuration file consisting of <key>=<value> pairs.
    """
    if (result == None):
        result = {}
        
    file = open(path, 'r')
    read_conf_text(file, result)
    return result


def read_conf_text(text, result = None): #, regexp='^([A-Za-z][\w]*)=([^#]*)(#.*)?'
    """Traverse array of text lines consisting of <key>=<value> pairs.
    """

    if (result == None):
        result = {}

    if (not text):
        #print ("Could not handle text: " + text)
        return result
        
    if (type(text) == str):
      text = text.split('\n')         
       
    #regexp='^([A-Za-z][\w]*)=([^#]*)(#.*)?'
    regexp='^([A-Za-z][\w]*)(=([^#]*))?(#.*)?'
       
    r = re.compile(regexp)
    for line in text:
        line = str(line).strip()
        if (line):
            m = r.match(line)
            if m:
                key = m.group(1).strip()
                if (m.group(3)):
                    result[key] = m.group(3).strip('"\n\'')
                else:
                    result[key] = 'True'
    return result


def dict_str(d, format='  {0}="{1}"\n'):
    """Convert a dictionary to a string."""
    s = ''
    for k,v in d:
        s += format.format(k,v)
    return s

def debug_dict(d):
    """dictionary to a string."""
    for k,v in d.items():
        print('  {0}="{1}"\t# '.format(k, v), type(v))


def print_dict(d, format='  {0}="{1}"\n'):
    """Dump a dictionary to stdout."""
    if (d):
        print( dict_str(d.items(), format))


def get_entries(obj, regexp=None, dst_type=None):
    """Gets member values as a dictionary
    """    
 
    if (type(regexp) == str):
        regexp = re.compile(regexp)
        
    entries = {}
    for i in dir(obj):
        if (not regexp) or (regexp.match(i)):
            x = getattr(obj, i)
            if (dst_type):
                x = dst_type(x)  # often str()           
            if (not callable(x)):
                entries[i] = x
    return entries

def symlink(link, target, overwrite = False):
    #from pathlib import Path
    if (link.exists()):                
        if (overwrite):
            link.unlink()
        else:
            return
    link.symlink_to(target)  

# Test
def set_entries(obj, entries=None, lenient = True):
    """Given a dictionary, sets corresponding member values
    """
    
    if (entries == None):
        return
    
    members = dir(obj) # or: limit to ["CACHE_ROOT", ...]
    for i in entries:
        if i in members:
            if not callable(i):
                setattr(obj, i, entries[i])
        elif not lenient:
            raise KeyError("Object has no key '{0}'".format(i))
            # print '# Warning: key not found for assignment: {0}="{1}"'.format(i, result[i])


#class Log:
#    """Logging utility
#    """
#
#    prefix = 'LOG: '
#    
#    log = []
#
#    def __str__(self):
#        return '\n'.join(self.log)
#
#    def __init__(self, prefix = 'LOG: '):
#        """Logging utility
#        
#        Attributes:
#        prefix: leading string attached to each line
#        """
#        self.log = []
#        self.prefix = prefix
#
#    def __call__(self, line, *args, **kwargs):
#        """Logging utility
#        """
#        self.log.append('{0}{1}'.format(self.prefix, line))
#        for i in args:
#            self.log.append('{0}{1}'.format(self.prefix, i))
#        for k,v in kwargs:
#            self.append('{0}{1]={2}'.format(self.prefix, k,v))

# Demo
if __name__ == '__main__':

    import argparse
    
    parser = argparse.ArgumentParser()

    parser.add_argument("-c", "--conf", dest="CONF",
        default="nutshell.cnf",
        help="read config file", metavar="<file>")

    parser.add_argument("-t", "--test", dest="TEST",
        default="",
        help="simple assignment", metavar="<key>=<value>")

#    parser.add_argument("-i", "--inputConf", dest="INPUTCONF",
#        default="",
#        help="exec input config script", metavar="<file>")

    parser.add_argument("-v", "--verbose", dest="verbose", default=False, metavar="<level>",
        help="Print status messages to stdout")


    
    #(options, args) = parser.parse_args()
    options = parser.parse_args()

    if (not options):
        parser.print_help()
        exit(1)
    
    if options.verbose:
        print(options)
 
    result = {}
    
    if (options.CONF):
        read_conf(options.CONF, result)

    if (options.TEST):
        read_conf_text([options.TEST], result)
        #readConfScript(options.INPUTCONF, result)

    #print result
    print_dict (result)
    #for key,value in result.items():
    #    print '  {0}="{1}"'.format(key, value)
    
    exit(0)
        
