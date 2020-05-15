#!/bin/python3
# -*- coding: utf-8 -*-
"""

HTTP Server -- nutshell.httpd
==============================

Server can be started with command::

    python3  -m nutshell.httpd -c nutshell.cnf

By default, it returns a status page (HTML) containing
 	   
- query form
- error messages 
- product info
- input info
- server status

	  


"""
__version__ = '0.1'
__author__ = 'Markus.Peura@fmi.fi'

#import argparse
import urllib.request, urllib.parse, urllib.error
import http.server
import xml.etree.ElementTree as ET
#from xml.dom import minidom
import os
import mimetypes
import logging
logging.basicConfig(format='%(levelname)s\t %(name)s:%(funcName)s: %(message)s')
#logging.basicConfig(format='%(asctime)s %(name)s : %(message)s', datefmt='%Y%m%d%I:%M:%S %p')
#logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.DEBUG)
#logging.basicConfig(format='%(asctime)s %(message)s', datefmt='%m/%d/%Y %I:%M:%S %p')



# Pretty printing
# from xml.dom import minidom


from . import nutils
from . import nutxml    # HTML utils
#from . import nutproduct
from . import product
from . import nutshell

HTTPStatus = nutshell.HTTPStatus

product_server = nutshell.ProductServer()
product_server.counter = 1
    
# http://127.0.0.1:8088/rauno/ahonen?request=MAKE&product=201708121600_radar.rack.comp_SIZE=800,800_SITES=fikor,fivan,fiika.png

# SimpleHTTPRequestHandler
# class NutHandler(BaseHTTPServer.BaseHTTPRequestHandler):
class NutHandler(http.server.SimpleHTTPRequestHandler):
    """Forwards a HTTP request to  nutshell.ProductServer instance
    
        
    """

    counter = 0
    #htmlDoc = ''

    @staticmethod
    def get_html_template():
        """Read HTML template dir and return it for modifications."""
        try:
            os.chdir(product_server.HTML_ROOT) # !!
            html = ET.parse(product_server.HTML_TEMPLATE).getroot()
        except IOError as err:
            html = ET.Element('html')
            body = ET.Element('body')
            pre  = ET.Element('pre')
            pre.set('style', 'color:red')
            pre.text = 'Note: Template file "{0}" not found: '.format(product_server.HTML_TEMPLATE)
            pre.text += str(err)
            body.append(pre)
            html.append(body)
        return html


    @staticmethod
    def parse_url(url, data=None):
        """ splitquery('/path?query') --> '/path', 'query'.
            If query returned also when None        
        """ # 
        sep = ','
        (path,query) = urllib.parse.splitquery(url)
        if (not data):
            data = {}
        if (query):
            params = urllib.parse.unquote(query)
            for param in params.split('&'):
                # splitvalue('attr=value') --> 'attr', 'value'.
                (key,value) = urllib.parse.splitvalue(param)
                if (key in data): # multiple
                    if (type(data[key]) == list):
                        data[key].extend(value.split(sep))
                    else:
                        data[key] = data[key]+sep+value # sepa
                else:
                    data[key] = value
        # NOTE: unquote also path, because:
        # 201708121600_radar.rack.comp_BBOX=20,60,30,70_SITES=fikor,fivan,fiika_SIZE=800,800.png
        # 201708121600_radar.rack.comp_BBOX%3D20%2C60%2C30%2C70_SITES%3Dfikor%2Cfivan%2Cfiika_SIZE%3D800%2C800.png 
        #if (not data):
        #    data = None?
        return (urllib.parse.unquote(path), data)

    @staticmethod
    def send_to_stream(s, product_request):
        """ Send to stream            
        """
        mtype,encoding = mimetypes.guess_type(product_request.path.name)
        fileformat = product_request.product_info.FORMAT.lower()
        if (not mtype):
            mtype = 'application/' + fileformat
        #print (product_request.product_info.filename())
        #print (mtype, encoding, product_request.path)
        s.send_response(product_request.status.value) # 200
        s.send_header('Content-Type', mtype)
        # s.send_header('Content-Disposition', 'attachment; filename="{0}"'.format(product_request.product_info.filename()))
        s.send_header('Content-Disposition', 'attachment; filename="{0}"'.format(product_request.path.name))
        s.end_headers()
        
        # content = None
        # if (fileformat in ['txt', 'json', 'sh', 'dat', 'cnf']):
        #    content = product_request.path.read_text()
        # else:
        content = product_request.path.read_bytes()   
        # file_in = open(product_request.path, 'rb')
        # content = file_in.read()
        # file_in.close()
        s.wfile.write(content)
 
    @staticmethod
    def redirect(s, url):
         s.send_response(HTTPStatus.SEE_OTHER.value)
         s.log.info(url)
         s.send_header("location", url)
         s.end_headers()
   
    
    def do_POST(s):
        print(('POST method not implemented, redirecting to do_GET(): {0}'.format(s.path)))
 
    def do_GET(s):
        """Main function. Receives the http request and sends a response.
                
        """

        # Consider renaming NutShell "request" to "actions"
        s.log = logging.getLogger("NutHandler" + str(++product_server.counter))
        s.log.setLevel(product_server.logger.level)
        
        #++NutHandler.counter
        s.log.debug("started")

        # Declare request and directives as lists
        querydata = {'request': [],  'directives': []} # 'product': '',
        
        basepath,querydata = NutHandler.parse_url(s.path, querydata)
        # basepath.replace(HTTP_PATH_PREFIX, '')
        s.log.info(querydata)

        if (basepath == '/test'):
            s.send_response(HTTPStatus.OK.value)
            s.end_headers()
            s.wfile.write("<html><body>Test</body></html>\n")
            s.log.warning("test {0}".format(basepath))
            return 

        # Directory and file requests are directed to default HTTP handler
        # NOTE: use plus, otherways collapses to root
        system_path = nutshell.Path(s.directory + basepath) 
        s.log.info("system_path: {0}".format(system_path))
        if (system_path.exists()):
            # TODO: if empty product, allow generation
            #  503  Service Unavailable x  Busy, come back later
            os.chdir(s.directory) # !!
            s.log.info("File found, forwarding to standard handler") #  -> {0} ".format(system_path))
            http.server.SimpleHTTPRequestHandler.do_GET(s)
            return
            
        #s.log.error("PRDOCUT? {0}".format(system_path.suffix))
        #s.log.error("PRDOCUT? {0}".format(querydata))
        # system_path.suffix == format extension, so smells like a filename
        if (basepath.find('/cache/') == 0) and system_path.suffix and (s.path.find('?') == -1):
            # and file not found (above)
            s.log.error("PRDOCUT? {0}".format(system_path.suffix))
            url =  "/nutshell/server/?request=MAKE&product={0}".format(system_path.name)
            NutHandler.redirect(s, url)
            return 
                   
        if (basepath == '/stop'):
            s.send_response(HTTPStatus.OK.value)
            s.end_headers()
            s.wfile.write(b"<html>Stopped server</html>")
            #httpd.server_close()
            s.log.info('Goodbye, world!')
            raise SystemExit  #KeyboardInterrupt

        actions = None
        directives = []
        product_name = querydata.get('product', None)
        product_info = None 
        if (product_name):
            product_info = product.Info(filename = product_name)
            actions = querydata.get('request', ["MAKE"])
            directives      = querydata['directives']
        else:
            actions = querydata['request']
            directives = querydata.get('directives', ["STATUS"])
        
        s.log.info("Service-request: {0} ".format(actions))
        s.log.info("Product-name: {0} ".format(product_name))
        s.log.info("Directives: {0} ".format(directives))

        #product_info = nutproduct.ProductInfo()
        
        product_request = None
        
        if (product_info):
            
            product_info.parse_filename(product_name)
            s.log.info("Product info: {0} ".format(product_info))
            
            # "MAIN"
            product_request = product_server.make_request(product_info, actions, directives, s.log.getChild("Make_Request"))
            # print("Phase 3", actions)
            # TODO: 'PATH'
            if ('MAKE' in actions) and ('STATUS' not in directives):
                if (product_request.status == HTTPStatus.OK) and (product_request.path != ''):
                    # Redirect 
                    url = '/cache/' + str(product_request.path_relative) + '?no_redirect'
                    #url = '/'+product_server.get_relative_path(product_info)+'?no_redirect'
                    #print('CONSIDER', url)
                    NutHandler.redirect(s, url)
                    return 
                else:
                     s.log.error("MAKE failed for: {0}".format(product_info.filename()))
                    #product_request.log
            else:
                product_request.status = HTTPStatus.OK
                #print("Nokemake", actions)
                
        if product_request:
            s.send_response(product_request.status.value)
            s.end_headers()
        else:
            s.send_response(HTTPStatus.OK.value)                    
            s.end_headers()
        
    
        # Proceed to HTML page response             
        html = NutHandler.get_html_template()
        #head = nutxml.get_head(html)
        body = nutxml.get_body(html)

        # Consider single table for all
        # table = ET.Element('table')
        # append_table(table, data, attributes)
        if actions:
            elem = nutxml.get_by_id(body, 'request')
            elem.set("value", ','.join(actions))
            # OLD
            # elem = nutxml.get_by_id(body, 'request_elem')
            # elem.append(nutxml.get_table(querydata, {"title": "Request"}))

        if product_request:
            elem = nutxml.get_by_id(body, 'product')
            elem.set("value", product_request.product_info.filename())
        
        if directives:
            elem = nutxml.get_by_id(body, 'directives')
            elem.set("value", ','.join(directives))
                
        
        if product_request and (product_request.returncode != 0):
            responses = http.server.SimpleHTTPRequestHandler.responses
            msg, desc = responses.get(product_request.status, ('Unknown', 'Not a valid HTTP return code'))
            elem = nutxml.get_by_id(body, 'notif_head_elem')
            elem.text = 'HTTP {0}: {1} -- {2}'.format(product_request.status.value, msg, desc)
            #lem.text = 'HTTP {0}'.format(product_request.status)
            #elem = nutxml.get_by_id(body, 'notif_elem')
            #elem.text = '"{0}"'.format(desc)
            elem = nutxml.get_by_id(body, 'error_elem')
            elem.text = str(product_request.log)
 
        elem =  nutxml.get_by_id(body, 'status_elem')
        elem.append(nutxml.get_table(product_server.get_status(), {"title": "Server status"}))
    
        elem =  nutxml.get_by_id(body, 'version_elem', 'p')
        elem.text = product_server.__class__.__name__ + ' : ' + product_server.__doc__

        #elem =  nutxml.get_by_id(body, 'misc', 'pre')
        #elem.text = "Cwd: " + os.cwd + '\n'
            
        if (product_info):
            if ('INPUTS' in actions):
                if (not product_request.inputs): # what if None?
                    product_request.inputs = product_server.get_input_list(product_info).inputs

                #layout = '<tr><td>{0}</td><td><a href="/nutshell/server?request=INPUTS&product={1}">{1}</a></td></tr>\n'
                elem = nutxml.get_by_id(body, 'inputs_elem')
                elem.append(nutxml.get_table(product_request.inputs, {"title": "Product inputs", "border": "1"}))
                    
            env = product_info.get_param_env()
            elem = nutxml.get_by_id(body, 'product_elem')
            elem.append(nutxml.get_table(env, {"title": "Product ({0}) parameters".format(product_info.ID)}))

        #elem = nutxml.get_by_tag(body, 'pre', {'id': 'dump'})
        elem = nutxml.get_by_id(body, 'dump_elem')
        elem.text = str(dir(product_server)) + '\n'
        elem.text += str(dir(s)) + '\n'
        elem.text += "Load: " + str(os.getloadavg()) + '\n'
        elem.text += "Cwd: " + s.directory + '\n'
        elem.text += "System side path: " + str(system_path) + '\n'

        s.wfile.write(ET.tostring(html))
        #s.wfile.write(minidom.parseString(ET.tostring(html)).toprettyxml(indent = "   "))
        #minidom.parseString(etree.toString(root)).toprettyxml(indent = "   ")

        return
     


def run_http(product_server):
    """A convenience function for starting and stopping the HTTP server.

    """
    NutHandler.directory = product_server.HTML_ROOT # has no effect as such...
    http.server.SimpleHTTPRequestHandler.directory = product_server.HTML_ROOT # has no effect...

    # server_class = http.server.HTTPServer  
    # httpd = server_class((product_server.HTTP_NAME, int(product_server.HTTP_PORT)), NutHandler)
    httpd = http.server.HTTPServer((product_server.HTTP_NAME, int(product_server.HTTP_PORT)), NutHandler)

    #if (options.VERBOSE > 1):
    print(( 'Starting server (port={0}, root={1})'.format(product_server.HTTP_PORT, NutHandler.directory) ))
    nutils.print_dict(nutils.get_entries(httpd))

    try:
        httpd.serve_forever()
        # httpd.handle_request() # single
    except KeyboardInterrupt:
        print()
        print( "Keyboard interrupt (CTRL-C)" )
        #pass
    except SystemExit:
        print()
        print( "System Exit" )
    except:
        print("Unexpected error:") # , sys.exc_info()[0])
    finally:
        httpd.server_close()
        
        
if __name__ == '__main__':

    product_server.logger = logging.getLogger("NutServer")

    parser = nutshell.ProductServer.get_arg_parser()
    #arser = argparse.ArgumentParser()

    # parser.add_argument("-c", "--conf", dest="CONF",
    # default="nutshell.cnf", #ProductServer.CONF_FILE?
    # help="read config file", metavar="<file>")

    parser.add_argument("-P", "--port",
                        dest="HTTP_PORT",
                        default=None,
                        type=int,
                        help="HTTP port", metavar="<int>")
    
#    parser.add_argument("-u", "--url_prefix",
#                        dest="HTTP_PREFIX",
#                        default=None,
#                        type=str,
#                        help="HTTP path prefix", metavar="<string>")
    
    parser.add_argument("-N", "--server_name",
                        dest="HTTP_NAME",
                        default=None,
                        type=str,
                        help="http server name", metavar="<ip_name>")

    parser.add_argument("-T", "--response_template",
                        dest="HTML_TEMPLATE",
                        default=None,
                        help="Template file for html responses", metavar="<file>.html")
    
    parser.add_argument("-R", "--html_root",
                        dest="HTML_ROOT",
                        default=None,
                        help="HTML document root", metavar="<dir>")
    
    
    
    options = parser.parse_args()
    
    if (not options):
        parser.print_help()
        exit(1)
      
    if (options.VERBOSE):
        options.LOG_LEVEL = "DEBUG"
        
    if (options.LOG_LEVEL):
        if hasattr(logging, options.LOG_LEVEL):
            product_server.logger.setLevel(getattr(logging, options.LOG_LEVEL))
        else:
            product_server.logger.setLevel(int(options.LOG_LEVEL))
    
    conf = ("nutshell/nutshell.cnf", False) # lenient, skip silently if missing
    if (options.CONF):
        conf = (options.CONF, True) # strict, explicit 

    #    print("Reading conf: {0} = ".format(*conf))
    product_server.read_conf(*conf)
        
    # Override confs
    opts = options.__dict__  #nutils.get_entries(options)
    conf = dir(product_server)
    for k,v in opts.items():
        if hasattr(product_server, k) and (v != None):
            value = getattr(product_server, k)
            t = type(value)
            product_server.logger.info("conf: {0}={1} [{2}] {3}".format(k, value, t , v) )
            setattr(product_server, k, t(v))

    product_server.init_path('PRODUCT_ROOT')
    product_server.init_path('CACHE_ROOT')
    product_server.init_path('HTML_ROOT') # not here!
    #product_server.init_path('HTML_ROOT'+'/'+'HTML_TEMPLATE') # not here!

    #if (options.VERBOSE > 6):
    #    nutils.print_dict(product_server.get_status())
    product_server.logger.debug(product_server.get_status()) 
       
    # Testing only. Use nutshell.py for command-line use
    if (options.MAKE):
        product_server.logger.info("Making product: {0}".format(options.MAKE) )
        product_info = product.ProductInfo(options.MAKE)
        prod = product_server.make_request(product_info, ['MAKE'])
        print (prod.log)
        exit(0)
                    
    # TODO if (file not exits options.HTTM_TEMPLATE):
    
    product_server.logger.info("Proceed to start the server.")
    run_http(product_server)

    # print 'http request example: ' + 'http://127.0.0.1:8088/nutshell/server?request=INPUTS&product=201708121600_radar.rack.comp_SIZE=800,800_SITES=fikor,fivan,fiika_BBOX=20,60,30,70.png'
    product_server.logger.info("Stopped server." )



