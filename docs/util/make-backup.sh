TIME=`date +'%y-%m-%d_%H%M'`
tar -cvzf bak/nutshell-$TIME.tgz --exclude *~ --exclude *~ *.pyc python nutshell html img sphinx/source
