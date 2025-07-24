# Local utilities
# Markus.Peura@fmi.fi

#
JAVA_CLASS_DIR_OLD=out/production/nutshell
JAVA_CLASS_DIR=build/classes

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
configure-nutweb:
	util/configure-nutweb.sh

install-nutweb:
	util/install-nutweb.sh


install-%:
	util/install.sh $*

configure-%:
	util/configure.sh $*


tests-java:
	LOOP=java util/make-tests.sh


Nutlet8.jar: META-INF  ${JAVA_CLASS_DIR_OLD}/nutshell8
	jar cvfm $@ META-INF/*.* -C ${JAVA_CLASS_DIR_OLD} nutshell8/

Nutlet10.jar: META-INF  ${JAVA_CLASS_DIR}/nutshell
	jar cvfm $@ META-INF/*.* -C ${JAVA_CLASS_DIR} nutshell/
	@java -cp Nutlet10.jar nutshell.ProductServer --log WARNING  --version
# 
# ${JAVA_CLASS_DIR}/nutshell/resources/nutshell-logo.png
	@cp -v $@ html/WEB-INF/lib/

Nutlet10.zip: java/nutshell
	zip $@ -R java/nutshell/*.java
	. ./.VERSION.sh && cp $@ $@-${VERSION}
# export VERSION=$( java -cp out/production/nutshell/  nutshell.ProductServer --log_level WARNING --version )

META-INF:
	@mkdir --parents $@
	echo 'Main-Class: nutshell.Nutlet' > $@/MANIFEST.MF
#cat $< | HTTP_PREFIX=${HTML_PREFIX} envsubst > $@/context.xml 


# Prepare files for Git export
prepack: Nutlet10.jar html/nutweb
# @ mkdir --parents log # Same as for testing
# for i in html/template/*.HTML; do make $${i%.*}.html; done
	for i in html/nutweb/*.html; do xmllint --noout $${i}; done
	cp -v Nutlet10.jar html/WEB-INF/lib/
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
	cp -vu $@ ./html/nutweb


