# ![NutShell cover](./img/nutshell-logo-small.png) NutShell
## Service for generating image and data products

### Makefile-like concept
* Given a filename, derives a product generator that can build the file
* A product file can require other product files which are generated recursively

### Product generators

NutShell delegates making the files to independent _product generators_
* Generators are organized as subdirectories
* Minimally, a generator directory contains script `generate.sh`
* Product parameters are communicated as environment variables to the script
* If a product requires other products as input, they are declared in `input.sh`
* Generators can be run independently from NutShell code

### Interfaces

* Command line:
`python3 -m nutshell.nutshell -c nutshell/nutshell.cnf --request MAKE --product 201708121600_my.product_SIZE=800x800.png`
* HTTP server:
`http://127.0.0.1:8088/nutshell/server?request=MAKE&product=201708121600_my.product_SIZE=800x800.png`
* Python API:
`response = product_server.make_request('201708121600_my.product_SIZE=800x800.png', ['MAKE'])`

### Documentation

* Online [documentation](https://fmidev.github.io/nutshell/)
