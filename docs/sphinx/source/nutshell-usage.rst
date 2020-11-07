.. NutShell documentation 

==============
Using NutShell
==============

This sections explains how product queries are made from command line, Python code and HTTP requests.

Instructions for setting up product generators is explained in :ref:`generators` .  

.. _command-line-usage:
      
Command-line usage
==================

Basic format for all the command line invocations is::

    python3 -m nutshell.nutshell  <args>
    
By default, NutShell tries to read configuration file ``nutshell.cnf`` in the current directory.
A file in other location is given with ``-c <config-file>`` .    
    
Online help is optained with ``-h``::

    python3 -m nutshell.nutshell -h
    
Simple query using configuration file and product definition::

    python3 -m nutshell.nutshell --request 'MAKE' \
      -p 201708121500_radar.rack.comp_SIZE=800,800_SITES=fi_BBOX=20,62,30,70.png 

Other commands       

.. _nutshell-api:

NutShell API
============

Simple example::

    import nutshell.nutshell

    # Initialize service
    server = nutshell.ProductServer('nutshell/nutshell.cnf')

    # Retrieve / generate a product
    response = server.make_request("201708121600_radar.rack.comp_SITES=fikor,fivan,fiika_SIZE=800,800.png", "MAKE")

    # Results:
    print("Return code: {0} ".format(response.returncode))
    print("Status (HTTP code): {0}:  ".format(response.status))
    print("File path: {0} ".format(response.path))

    # Example: further processing (image data)
    from PIL import Image
    file = Image.open(response.path)
    print(file.info)


.. _http-server:

NutShell HTTP Server
====================


Code documentation: :ref:`nutshell.http`
