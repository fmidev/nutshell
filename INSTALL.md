# ![NutShell cover](./img/nutshell-logo-small.png) NutShell

# Installation
## Java versions

Available: plain `java` and `tomcat` version. The later comes with a WWW servlet.

### TomCat

1. Install Tomcat web server (preferably version 8 or 9). For example:
```
sudo apt install tomcat8
```

2. Create a config file:
```
util/configure.sh tomcat
```

3. Add write permissions to Tomcat. 

In latest versions of Tomcat, you have to add permissions for Tomcat service
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


Install 

```
util/install.sh tomcat
```