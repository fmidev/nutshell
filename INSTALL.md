# ![NutShell cover](./img/nutshell-logo-small.png) NutShell

# Installation

## Java versions

Available: plain Java version and TomCat version, which also provides a WWW interface.

In the following instructions, variable $NUTSHELL_VERSION` has one of the following values:

* `java` – plain Java version, to be used on command line
* `tomcat10` – version with TomCat 10 www interface and command line support
* `tomcat9` – as above, but with TomCat 9

If you are selecting a TomCat version, install TomCat web server first. For example:
```
sudo apt install tomcat10
```


### Configuration

For NutShell installation, create a config file:
```
util/configure-nutshell.sh $NUTSHELL_VERSION
```
The file can be edited later.

### Installation

Install program code (and WWW page support, if TomCat version)
```
util/install-nutshell.sh $NUTSHELL_VERSION
```

### Quick check

After installation, you can test the command line operation simply with following commands
```
nutshell
nutshell --version
nutshell --help
```

Note that `nutshell` command linked to a bash script `$CMD_SCRIPT_DIR/nutshell-$NUTSHELL_VERSION` .
If $CMD_SCRIPT_DIR is not in your $PATH variable, consider adding it or use `$CMD_SCRIPT_DIR/nutshell` 

#### Adjusting file write permissions (TomCat 9 & 10)

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

Look for `[Service]` section. It is essential to grant write access to directory `CACHE_ROOT`,
and optionally `STORARGE_ROOT`. For example as follows:
```
ReadWritePaths=/opt/cache
ReadWritePaths=/opt/storage
```

Make sure that all the mentioned directories exist. Then restart the server:
```
sudo systemctl start tomcat10
```

#### Debugging

Check for error messages in the log directory of TomCat, for example
``/var/log/tomcat10`` . There are files like ``localhost.2025-08-07.log``.


#### Developer's notes

Downgrading `nutshell10` -> `nutshell9' :

* Ensure nutshell10 works
* Run `make convert10to9`, that is, `cd ./java && ./downgrade-code.sh nutshell10/*.java`
* Refresh `nutshell9' in IDE
* Compile jar(s): make -B java/Nutlet{10,9}.jar
* Commit changes in version control
