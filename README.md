# ![NutShell cover](./img/nutshell-logo-small.png) NutShell

## Service for generating image and data products

### Makefile-like concept
* Given a filename, derives the product generator building the file
* A product file can require other product files which are generated recursively

### Product generators

NutShell delegates making the files to independent _product generators_
* Generators are organized as subdirectories
* Minimally, a generator directory contains script `generate.sh`
* Product parameters are communicated as environment variables to the script
* If a product requires other products as input, they are declared in `input.sh`
* Generators can be run independently from NutShell code

### Interfaces

## Java version

The package is primarily developed for Tomcat 10, under name `nutshell10`.
For now, an older version for Tomcat 9 is shipped as well, named `nutshell9`.
Essentially, the lower version is obtained by converting `jakarta` to `javax` .

# Usage:

* Command line:
`java -cp Nutlet.jar nutshell.ProductServer --config nutshell/nutshell.cnf --request MAKE --product 201708121600_my.product_SIZE=800x800.png`
* HTTP server:
`http://127.0.0.1:8080/nutshell/NutShell?action=GENERATE&output=STREAM&product=201708121600_my.product_SIZE=800x800.png`
* Java API:
`Task task = productServer.new Task("201708121600_my.product_SIZE=800x800.png", actions.value, null); task.start()`
* [Installation](./INSTALL.md)

## Python version

* Command line:
`python3 -m nutshell.nutshell -c nutshell/nutshell.cnf --request MAKE --product 201708121600_my.product_SIZE=800x800.png`
* HTTP server:
`http://127.0.0.1:8088/nutshell/server?request=MAKE&product=201708121600_my.product_SIZE=800x800.png`
* Python API:
`response = product_server.make_request('201708121600_my.product_SIZE=800x800.png', ['MAKE'])`

### Documentation

* Online [documentation](https://fmidev.github.io/nutshell/)

