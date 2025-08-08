# ![NutShell cover](./img/nutshell-logo-small.png) NutShell

# Installation

## Java versions

Available: plain Java version and TomCat version, which also provides a WWW interface.

### TomCat

First, install TomCat web server (preferably version 10, at least 9). For example:
```
sudo apt install tomcat10
```

For NutShell installation, create a config file:
```
util/configure-nutshell.sh tomcat10
```
The file can be edited later.

Install program code and WWW page support:
```
util/install-nutshell.sh tomcat10
```

Set file write permissions (TomCat 9 & 10)

In these versions, file permissions are managed "externally", by the operating system (e.g. Ubuntu Linux),
not by TomCat or Java security policies. (This is sometimes called *sandboxing* .)

At least in Ubuntu 24.0, the permissions are granted as follows.

Search for systemd configuration file for TomCat.
The path of the file varies, it can be for example:
```
/etc/systemd/system/tomcat10.service.d/override.conf
```
or 
```
/etc/systemd/system/multi-user.target.wants/tomcat9.service
```

The file can be searched for example with:
```
find /etc/systemd/system -type f -name '*tomcat*'
```
or even
```
find /etc/ -type f -name '*tomcat*'
```
which may take a while. More information: [search](https://www.google.com/search?q=how+to+grant+tomcat+write+access)


After finding the file take a look on it – and consider taking a backup.

Then, stop the TomCat server:
```
sudo systemctl stop tomcat10
```

Invoke the editing tool, with argument matching the configuratiib file found above.
```
sudo systemctl edit tomcat10
```

Edit permissions, adding lines like
```
ReadWritePaths=/opt/cache
```
in the `[Service]` section.

Make sure that all the mentioned directories exist. Then restart the server:
```
sudo systemctl start tomcat10
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




It is essential to grant write access to directory `CACHE_ROOT`, and optionally `STORARGE_ROOT`. For example as follows:
```
ReadWritePaths=/opt/cache
ReadWritePaths=/opt/storage
```




