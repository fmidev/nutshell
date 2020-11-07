.. NutShell documentation master file, created by
   sphinx-quickstart on Sat May  2 15:54:03 2020.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.


.. _configuration:
   
Configuration
=============

NutShell needs only a simple configuration file. Helper script ``util/configure.sh``
can be applied to build one, ``nutshell.cnf``.

Example configuration
---------------------

A configration file might look like this: ::

  # Root of cache directory, often on separate resource:
  CACHE_ROOT='/home/mpeura/venison/cache'

  # Root of product generator (script) directories
  PRODUCT_ROOT='/home/mpeura/venison/products'

  # Root directory for HTTP server (optional).
  HTML_ROOT='./html'

  # Port for HTTP server.
  HTTP_PORT='8088'

  # URL directory prefix (future option).
  HTTP_PREFIX='nutshell/'


Configuration file may contain relative paths, which are expanded
with respect to working directory upon invocation. So for example::

  cd /some/working/dir/
  python3 -m nutshell.nutshell -c ./sub/dir/nutshell.cnf

will yield ``HTML_ROOT=/some/working/dir/html``, hence
**not** ``/some/working/dir/sub/dir/python``.
Whenever possible, absolute paths should be used.
