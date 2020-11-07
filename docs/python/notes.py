import nutshell
s = nutshell.ProductServer()
p = s.createProduct('2002121_mika_A=1_SIZE=21_JEO=1_B=SK.pgm.gz')
p.str()

reload(nutshell); s = nutshell.ProductServer()

import nutshell
s = nutshell.ProductServer()
s.readConf('nutshell.cnf')

reload(nutshell); s = nutshell.ProductServer('nutshell.cnf')

p = s.createProduct('200206121845_radar.rack.comp_SITES=fi_BBOX=20,60,30,70.png')

# "201708121600_radar.rack.comp_PPROD=_BBOX=17.13,57.93,29.41,64.08_SIZE=800,800_PROJ=4326_SITES=fikor,fivan,fiika_METHOD=MAXIMUM_PALETTE=default_TRANSP2=0.1:0.3,0,0.8.png"

p = s.createProductInfo('200206121845_radar.rack.comp_SITES=fik_BBOX=20,60,30,70.png')

p = s.createProductInfo('201708121600_radar.rack.comp_PPROD=_BBOX=17.13,57.93,29.41,64.08_SIZE=800,800_PROJ=4326_SITES=fikor,fivan,fiika_METHOD=MAXIMUM_PALETTE=default_TRANSP2=0.1:0.3,0,0.8.png')

p = nutshell.ProductInfo()
p.parse('koe.png')
                  
p = nutshell.ProductInfo('201708121600_radar.rack.comp_PPROD=_BBOX=17.13,57.93,29.41,64.08_SIZE=800,800_PROJ=4326_SITES=fikor,fivan,fiika_METHOD=MAXIMUM_PALETTE=default_TRANSP2=0.1:0.3,0,0.8.png')


s.makeProduct(p)
