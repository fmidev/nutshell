# ![NutShell cover](./img/nutshell-logo-small.png) NutShell

# Installation

## Java versions

Available: plain Java version and TomCat version, which also provides a WWW interface.

### TomCat

1. Install TomCat web server (preferably version 10, at least 9). For example:
```
sudo apt install tomcat10
```

2. Create a config file:
```
util/configure-nutshell.sh tomcat10
```


3. 
Install 

```
util/install-nutshell.sh tomcat10
```

4. 
Test 

```
/usr/local/bin/nutshell-tomcat10
```

5.
Debugging

Check for error messages in the log directory of TomCat, for example ``/var/log/tomcat10`` .
There files like ``localhost.2025-08-07.log``.



#### Note on TomCat 9 permission management


In versions 9.xx of TomCat, you have to add permissions for Tomcat service
(daemon). The path of the file varies, it can be for example:

```
/etc/systemd/system/multi-user.target.wants/tomcat9.service
```

The file can be searched for with:
```
find /etc/systemd/system -type f -name '*tomcat*'
```
or even
```
find /etc/ -type f -name '*tomcat*'
```
which may take a while. More information: [search](https://www.google.com/search?q=how+to+grant+tomcat+write+access)

It is essential to grant write access to directory `CACHE_ROOT`, and optionally `STORARGE_ROOT`. For example as follows:
```
ReadWritePaths=/opt/cache
ReadWritePaths=/opt/storage
```

You may have to run `systemctl daemon-reload` after editing the file.


