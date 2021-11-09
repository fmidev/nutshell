#!/bin/bash


export PRODUCT_ROOT

if [ -e ./nutshell.cnf ]; then
    echo "# Using ./nutshell.cnf as basis"
    source ./nutshell.cnf
    echo
else
    echo "# Main conf file ./nutshell.cnf missing, run util/configure.sh first"
    exit 1
fi

echo "Under construction..."
echo "Consider: "
echo ln -sf $PWD/demo/products/demo $PRODUCT_ROOT/
