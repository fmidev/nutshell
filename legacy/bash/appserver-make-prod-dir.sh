#!/bin/bash

for i in generate.log deer_appserver.log ; do
    touch $i
    chmod -v a+w $i
done

#PRODUCT_ROOT=${PWD%%/products*} 
PRODUCT_DIR=${PWD##*/products/} 
echo $PRODUCT_DIR

RELATIVE_ROOT=`echo $PRODUCT_DIR | tr -d '[:alpha:]'`
RELATIVE_ROOT=${RELATIVE_ROOT//\//../}..
echo $RELATIVE_ROOT


for i in log query latest; do
    dir=$RELATIVE_ROOT/$i
    ln -vs $dir .
    svn add $i
done

echo 
JAVA_NAME=${PRODUCT_DIR//\//.}.Product.java
echo Consider:
for i in generate.sh generate.py $JAVA_NAME; do
    echo "touch $i; svn add $i"
done

