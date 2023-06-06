# Local utilities
# Markus.Peura@fmi.fi

JAVA_CLASS_DIR=out/production/nutshell

# Source dir 
TOMCAT_SRC=html

# include nutshell.cnf

help:
	@grep '^[a-z].\+:' Makefile | tr '\n:' ' '
	@echo

update-pkg: Nutlet.jar
# html/template
	cp -v Nutlet.jar html/WEB-INF/lib/


Nutlet.jar: META-INF  ${JAVA_CLASS_DIR}/nutshell
	jar cvfm $@ META-INF/*.* -C ${JAVA_CLASS_DIR} nutshell/
# 
# ${JAVA_CLASS_DIR}/nutshell/resources/nutshell-logo.png
	cp $@ ${TOMCAT_SRC}/WEB-INF/lib/

META-INF:
	@mkdir --parents $@
	echo 'Main-Class: nutshell.Nutlet' > $@/MANIFEST.MF
#cat $< | HTTP_PREFIX=${HTML_PREFIX} envsubst > $@/context.xml 


# Prepare files for export
prepack: Nutlet.jar ${TOMCAT_SRC}/template
	for i in ${TOMCAT_SRC}/template/*.HTML; do make $${i%.*}.html; done


# Only validates & re-formats. No variable substitution.
# Consider %.html.tpl instead of .HTML ?
%.html: %.HTML
# Check syntax and indent
	xmllint --format $? > $@
# (Ignore error)
#	-diff -q $? $@.tmp || mv -v $@.tmp $@
	-diff -q $? $@
	cp -vu $@ ./html/nutweb

