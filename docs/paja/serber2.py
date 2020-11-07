import BaseHTTPServer
#import urllib.parse
##from urllib.parse import urlparse, parse_qs
#import urllib
 
HOST_NAME = ''
PORT_NUMBER=8088

#postVars = ''

def dump(obj):
    d = dir(obj)
    for i in d:
        a = getattr(obj, i)
        if (not callable(a)):
            print "MIKA",i, a


class MyHandler(BaseHTTPServer.BaseHTTPRequestHandler):

    def do_POST(s):
        s.send_response(200)
        s.end_headers()
        varLen = int(s.headers['Content-Length'])
        postVars = s.rfile.read(varLen)
        print postVars

    def do_GET(s):
        s.send_response(200)
        #s.send_header('Content-type:', 'image/png')
        s.end_headers()
        # varLen = int(s.headers['Content-Length'])
        # postVars = s.rfile.read(varLen)
        # print postVars
        # dump(s)
        data = {}
        query = s.path.split('?')
        basepath = query[0]
        if (len(query) > 1):
            vars = query[1].split('&')
        else:
            vars = []
                
        s.wfile.write("<html>")
        s.wfile.write("<h1>Hello!</h1>")
        s.wfile.write("<pre>")
        s.wfile.write(s.path)
        s.wfile.write('\n')
        #fields = urlparse.parse_qs(s.path)
        for i in vars:
            s.wfile.write(i)
            s.wfile.write('\n')
            #data[x[0]] = i
        #dump (data)
        #print "<html>Hello!</html>"
        #s.send_response("<html>Hello!</html>")
        s.wfile.write("</pre>")
        s.wfile.write("</html>")

server_class = BaseHTTPServer.HTTPServer
httpd = server_class((HOST_NAME, PORT_NUMBER), MyHandler)


httpd.serve_forever()
#try:
#    httpd.handle_request()
#except KeyboardInterrupt:
#    pass

#print postVars
#httpd.server_close()
