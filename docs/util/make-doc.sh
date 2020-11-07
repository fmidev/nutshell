#!/bin/bash

echo "Make images..."
pushd img; make all; popd;

echo "Make Sphinx doc..."
pushd sphinx
make html
popd

echo "Copy publication..."
cp -vu ~/Publ/ERAD08/erad2008-0004-extended.pdf sphinx/build/html/

echo "Update git doc..."
echo "Don't care about git warnings (yet)"
git-doc-refresh.sh sphinx/build/html docs

echo "Commit & push"
git commit -m 'Automatic' /home/mpeura/nutshell/docs/
echo "https_proxy='' HTTPS_PROXY='' git push"
