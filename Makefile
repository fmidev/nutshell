# Local utilities
# Markus.Peura@fmi.fi

JAVA_CLASS_DIR=out/production/nutshell

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
install-%:
	util/install.sh $*

configure-%:
	util/configure.sh $*


tests-java:
	LOOP=java util/make-tests.sh


Nutlet.jar: META-INF  ${JAVA_CLASS_DIR}/nutshell
	jar cvfm $@ META-INF/*.* -C ${JAVA_CLASS_DIR} nutshell/
# 
# ${JAVA_CLASS_DIR}/nutshell/resources/nutshell-logo.png
	@ cp -v $@ html/WEB-INF/lib/

Nutlet.zip: java/nutshell
	zip $@ -R java/nutshell/*.java
	. ./.VERSION.sh && ln $@ $@-${VERSION}
# export VERSION=$( java -cp out/production/nutshell/  nutshell.ProductServer --log_level WARNING --version )

META-INF:
	@mkdir --parents $@
	echo 'Main-Class: nutshell.Nutlet' > $@/MANIFEST.MF
#cat $< | HTTP_PREFIX=${HTML_PREFIX} envsubst > $@/context.xml 


# Prepare files for Git export
prepack: Nutlet.jar html/template
	for i in html/template/*.HTML; do make $${i%.*}.html; done
	cp -v Nutlet.jar html/WEB-INF/lib/
	@echo -n "Version: "
	java -cp Nutlet.jar nutshell.ProductServer --log WARNING  --version


# Only validates & re-formats. No variable substitution.
# Consider %.html.tpl instead of .HTML ?
%.html: %.HTML
# Check syntax and indent
	xmllint --format $? > $@
# (Ignore error)
#	-diff -q $? $@.tmp || mv -v $@.tmp $@
	-diff -q $? $@
	cp -vu $@ ./html/nutweb

