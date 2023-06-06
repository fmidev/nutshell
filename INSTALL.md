# ![NutShell cover](./img/nutshell-logo-small.png) NutShell

### Installation

## Java versions

Plain 'java' and `tomcat` version. The later comes with a WWW servlet.

### TomCat

1. Install tomcat (preferably version 8 or 9)

Example 1:
```
sudo apt install tomcat8
```

Configure local environment.

The most important variables:

- `NUTSHELL_ROOT` - directory
- `TOMCAT_ROOT` -
- `CACHE_ROOT` - primary directory for generated products
- `STORARGE_ROOT` - secondary directory, for frequently requested products 


```
util/configure.sh tomcat
```

In latest versions of Tomcat, you have to add permissions for Tomcat service
(daemon). The path of the file varies, it can be for example:

```
/etc/systemd/system/multi-user.target.wants/tomcat9.service
```

The file can be searched for with:
```
find /etc/systemd/system -iname '*tomca*'
```
or even
```
find /etc/ -iname '*tomca*'
```
which may take a while. More information: [search](https://www.google.com/search?q=how+to+grant+tomcat+write+access)

The essential settings to add are `CACHE_ROOT` and `STORARGE_ROOT`, for example:
```
ReadWritePaths=/opt/cache
ReadWritePaths=/opt/storage
```

You may have to run `systemctl daemon-reload` after editing the file.


Install 

```
util/install.sh tomcat
```