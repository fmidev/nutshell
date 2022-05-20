



appserver-${HOSTNAME}.cnf:
	cp appserver-example.cnf $@

appserver.cnf: | appserver-${HOSTNAME}.cnf
	ln -s appserver-${HOSTNAME}.cnf $@ 

install:
	echo "Use install-dev or install-prod instead."

install-dev: appserver.sh appserver-lib.sh | appserver.cnf 
	cp -vu appserver.sh appserver-lib.sh appserver-make-prod-dir.sh /fmi/dev/bin

install-prod: appserver.sh appserver-lib.sh | appserver.cnf 
	cp -vu appserver.sh appserver-lib.sh appserver-make-prod-dir.sh /fmi/prod/bin
#	cp -vu appserver.cnf /fmi/prod/conf
