# Local utilities
# Markus.Peura@fmi.fi

# Java build settings. The API jars can be overridden, for example:
#   make TOMCAT10_SERVLET_API=/path/to/servlet-api.jar VALIDATION_API=/path/to/validation-api.jar
JAVAC ?= javac
JAVA ?= java
JAVAC_FLAGS ?= -encoding UTF-8 -Xlint:all

JAVA_SOURCE_DIR=java
JAVA_BUILD_DIR=build

TOMCAT10_SERVLET_API ?= $(firstword $(wildcard \
	/usr/share/java/tomcat10-servlet-api*.jar \
	/usr/share/tomcat10/lib/servlet-api.jar \
	$(HOME)/.p2/pool/plugins/jakarta.servlet-api_*.jar))
TOMCAT9_SERVLET_API ?= $(firstword $(wildcard \
	/usr/share/java/tomcat9-servlet-api*.jar \
	/usr/share/tomcat9/lib/servlet-api.jar))
VALIDATION_API ?= $(firstword $(wildcard \
	/usr/share/java/validation-api*.jar \
	$(HOME)/validation-api-*/validation-api-*.jar))

NUTSHELL10_SOURCES=$(wildcard $(JAVA_SOURCE_DIR)/nutshell10/*.java)
NUTSHELL9_SOURCES=$(wildcard $(JAVA_SOURCE_DIR)/nutshell9/*.java)
NUTSHELL10_CLASSES=$(JAVA_BUILD_DIR)10/classes
NUTSHELL9_CLASSES=$(JAVA_BUILD_DIR)9/classes
NUTSHELL10_STAMP=$(NUTSHELL10_CLASSES)/.compiled
NUTSHELL9_STAMP=$(NUTSHELL9_CLASSES)/.compiled

#
#JAVA_CLASS_DIR_OLD=out/production/nutshell
JAVA_CLASS_DIR_OLD=$(NUTSHELL9_CLASSES)
JAVA_CLASS_DIR=$(JAVA_BUILD_DIR)/classes

# Source dir 
# TOMCAT_SRC=html

# include nutshell.cnf
# VERSION=$(shell .VERSION.sh && echo $VERSION)

help:
	@grep '^[Na-z].\+:' Makefile | cut -d: -f1 | tr '\n' ' '
	@echo
#@grep '^[a-z].\+:' Makefile | tr '\n:' ' '

# update-pkg: Nutlet.jar
# html/template

NUTSHELL_VERSION=tomcat10

configure-nutshell:
	util/configure-nutshell.sh ${NUTSHELL_VERSION}

install-nutshell:
	util/install-nutshell.sh ${NUTSHELL_VERSION}

configure-nutweb:
	util/configure-nutweb.sh

install-nutweb:
	util/install-nutweb.sh


#install-%:
#	util/install.sh $*

#configure-%:
#	util/configure.sh $*


tests-java:
	LOOP=java util/make-tests.sh

.PHONY: help compile-java10 compile-java9 clean-java

NutSo%.jar:
	echo $? ${*} $*
	echo $@
	echo $%

#java/Nutlet8.jar: META-INF  ${JAVA_CLASS_DIR_OLD}/nutshell8
#	jar cvfm $@ META-INF/*.* -C ${JAVA_CLASS_DIR_OLD} nutshell8/

convert10to9:
	cd ./java && ./downgrade-code.sh nutshell10/*.java || echo

compile-java10: $(NUTSHELL10_STAMP)

compile-java9: $(NUTSHELL9_STAMP)

$(NUTSHELL10_STAMP): $(NUTSHELL10_SOURCES) $(TOMCAT10_SERVLET_API) $(VALIDATION_API)
	@test -n "$(TOMCAT10_SERVLET_API)" && test -f "$(TOMCAT10_SERVLET_API)" || { echo "Missing Tomcat 10 servlet API; set TOMCAT10_SERVLET_API" >&2; exit 1; }
	@test -n "$(VALIDATION_API)" && test -f "$(VALIDATION_API)" || { echo "Missing validation API; set VALIDATION_API" >&2; exit 1; }
	@mkdir --parents $(NUTSHELL10_CLASSES)
	$(JAVAC) $(JAVAC_FLAGS) -cp "$(TOMCAT10_SERVLET_API):$(VALIDATION_API)" -d $(NUTSHELL10_CLASSES) $(NUTSHELL10_SOURCES)
	@touch $@

$(NUTSHELL9_STAMP): $(NUTSHELL9_SOURCES) $(TOMCAT9_SERVLET_API) $(VALIDATION_API)
	@test -n "$(TOMCAT9_SERVLET_API)" && test -f "$(TOMCAT9_SERVLET_API)" || { echo "Missing Tomcat 9 servlet API; set TOMCAT9_SERVLET_API" >&2; exit 1; }
	@test -n "$(VALIDATION_API)" && test -f "$(VALIDATION_API)" || { echo "Missing validation API; set VALIDATION_API" >&2; exit 1; }
	@mkdir --parents $(NUTSHELL9_CLASSES)
	$(JAVAC) $(JAVAC_FLAGS) -cp "$(TOMCAT9_SERVLET_API):$(VALIDATION_API)" -d $(NUTSHELL9_CLASSES) $(NUTSHELL9_SOURCES)
	@touch $@

java/Nutlet%.jar: $(JAVA_BUILD_DIR)%/classes/.compiled
	@mkdir --parents META-INF/
	@echo 'Main-Class: nutshell'${*}'.Nutlet' >  META-INF/MANIFEST.MF
	@cat META-INF/MANIFEST.MF
	jar cvfm $@ META-INF/*.* -C $(JAVA_BUILD_DIR)${*}/classes nutshell${*}/
	$(JAVA) -cp $@  nutshell${*}.ProductServer --log WARNING  --version
	@rm -v META-INF/MANIFEST.MF

clean-java:
	rm -rf $(NUTSHELL10_CLASSES) $(NUTSHELL9_CLASSES)

java/NutXXXXlet10.jar: META-INF  ${JAVA_CLASS_DIR}/nutshell
	jar cvfm $@ META-INF/*.* -C ${JAVA_CLASS_DIR} nutshell/
	@java -cp $@  nutshell.ProductServer --log WARNING  --version
# 
# ${JAVA_CLASS_DIR}/nutshell/resources/nutshell-logo.png
#@cp -v $@ html/WEB-INF/lib/

java/Nutlet10.zip: java/nutshell
	zip $@ -R java/nutshell/*.java
	. ./.VERSION.sh && cp $@ $@-${VERSION}
# export VERSION=$( java -cp out/production/nutshell/  nutshell.ProductServer --log_level WARNING --version )

META-INF:
	@mkdir --parents $@
	echo 'Main-Class: nutshell.Nutlet' > $@/MANIFEST.MF
#cat $< | HTTP_PREFIX=${HTML_PREFIX} envsubst > $@/context.xml 


# Prepare files for Git export
prepack: Nutlet10.jar #html/nutweb
# @ mkdir --parents log # Same as for testing
# for i in html/template/*.HTML; do make $${i%.*}.html; done
	for i in www/nut*/*.html; do xmllint --noout $${i}; done
#cp -v Nutlet10.jar html/WEB-INF/lib/
	@echo -n "Version: "
	java -cp Nutlet10.jar nutshell.ProductServer --log WARNING  --version


# Only validates & re-formats. No variable substitution.
# Consider %.html.tpl instead of .HTML ?
%.html: %.HTML
# Check syntax and indent
	xmllint --format $? > $@
# (Ignore error)
#	-diff -q $? $@.tmp || mv -v $@.tmp $@
	-diff -q $? $@
	echo "DEPRECATED: cp -vu $@ ./html/nutweb"


