#!/bin/python3
# -*- coding: utf-8 -*-
"""FMI code for using NutShell in AirFlow"""

__version__ = '0.1beta'
__author__ = 'Markus.Peura@fmi.fi'

#import argparse
import re

from . import product 

                  
class DummyPythonOperator:
    """Imitates PythonOperator"""
    
    def __init__(self, task_id, dag, python_callable, op_kwargs=None):
        self.task_id = task_id
        self.callable = python_callable
        
        print (self.id)
        pass

def get_task_id(product_info):
    # without leading timestamp
    s = product_info.get_static_filename() 
    s = re.sub(r"=","-", s)
    s = re.sub(r"[^0-9A-Za-z\-]","_", s)
    return s
    #return re.sub("\W","_", product_info.get_static_filename() )

def make_nutshell_product(TIMESTAMP, product_server, product_info):

    # At this stage, Jinja macros have been interpreted
    if (TIMESTAMP):
        product_info.set_timestamp(TIMESTAMP)

    print("AIRFLOW-NUTSHELL: making: {0}".format(product_info.get_filename()))

    product_request = product_server.make_request(product_info, ['MAKE'], ['LINK', 'LATEST'])  
    print("AIRFLOW-NUTSHELL: making: {0}".format(product_request.path))     

    #context['task_instance'].xcom_push('file', str(product_request.path))

    return product_request.returncode     


def create_operator(product_request):
    task = PythonOperator(
        task_id = task_id,
        python_callable = fmi.nutflow.make_nutshell_product,
        op_kwargs={'TIMESTAMP': TIMESTAMP, 
                   #fmi.setup.env['TIMESTAMP_MINUS10MIN'], 
                   'product_info': product_info, 
                   'actions': actions, 
                   'directives': directives},
        dag=dag, 
    )
    return task
    

def create_operator_old(dag, product_server, product_info, params=None, task_id = None, 
  op_cls=DummyPythonOperator):
    
    if (type(product_info) == str):
        product_info = product.Info(filename = product_info)
  
    if (params):
        product_info.set_parameters(params)
    
    if (not task_id):
        task_id = product_info.get_static_filename().replace(".", "")
        task_id = re.sub("\W","_", task_id)
    
    result = op_cls(
        task_id = task_id,  
        python_callable = make_nutshell_product,   
        dag = dag,  
        op_kwargs = {"product_server": product_server, 
                     "product_info": product_info})
  
    return result
    
    
    
# def create_operator0(dag, product_id=None, filename=None, task_id = None, 
#   op_cls=DummyPythonOperator,  **kwargs):
    
#     if (product_id) and (filename):
#   raise KeyError("'product_id' and 'filename' are mutually exclusive")

#     product_info = product.Info()

#     if (filename):
#   product_info.set(filename = filename)
#     else:
#   product_info.set(product_id = product_id)  


#     if (not task_id):
#   task_id = product_info.get_static_filename().replace(".", "")
#   task_id = re.sub("\W","_")
    
#     result = op_cls(
# task_id=task_id,                    
#             python_callable=make_nutshell_product,                                                                                 
#             dag = dag,            
#             op_kwargs = kwargs)  
   
#     return result


if __name__ == '__main__':

    # parser = argparse.ArgumentParser()
    parser = product.Info.get_arg_parser()

    # Consider leaving "rare" or specific params in-class
    parser.add_argument("-s", "--set", metavar="[<id>|<filename>]",
                        dest="SET",
                        default="",
                        help="product to be handled, recognises argument type")

    options = parser.parse_args()

    if (not options):
        parser.print_help()
        exit(1)


    product_info = product.Info()

    if (options.PRODUCT):
        product_info.set_product(options.PRODUCT)
    #else:
    #    logger.warning("product not defined")
 
    op = create_operator()
    print (op)
