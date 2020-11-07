import nutshell.nutshell
import nutshell.product

# Create server instance 
product_server = nutshell.nutshell.ProductServer('nutshell.cnf')

# Create product request info - the quick way
product_info = nutshell.product.Info( '201708121600_radar.rack.comp_SIZE=800,800_SITES=fikor,fivan,fiika.png')

# Create product request info - the longer way
product_info = nutshell.product.Info('radar.rack.comp')
product_info.set_format('h5')
product_info.set_timestamp('2017/08/12 16:00')  # or '201708121600'
product_info.set_parameters({'SITES': 'fikor,fivan,fiika', 'SIZE': '800,800'})

# Debugging: check status. Notice params in alphabetical order.
product_info.filename()
# Outputs: '201708121600_radar.rack.comp_SITES=fikor,fivan,fiika_SIZE=800,800.h5'


# Create product (if not in cache already)
r = product_server.make_request(product_info, ['DELETE','MAKE']) 
print (r.returncode, r.path)
# Outputs: 0 /home/mpeura/venison/cache/2017/08/12/radar/rack/comp/201708121600_radar.rack.comp_SITES=fikor,fivan,fiika_SIZE=800,800.h5

print (r.inputs)
# Outputs:  {'FIKOR': '.../201708121600_radar.polar.fikor.h5', 'FIVAN': '201708121600_radar.polar.fivan.h5', 'FIIKA': '201708121600_radar.polar.fiika.h5'}
