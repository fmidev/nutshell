#!/bin/python3
# -*- coding: utf-8 -*-
"""Utilities for main modules of NutShell """

__version__ = '0.2'
__author__ = 'Markus.Peura@fmi.fi'


import xml.etree.ElementTree as ET


class TupleLayout:
    
    attrs        = '{0}="{1}" '

    tableTr        = '<tr><td>{0}</td><td>{1}</td></tr>\n'
    tableTrLinked  = '<tr><td>{0}</td><td><a href="{1}">{1}</a></td></tr>\n'

    selectOption = '<option value="{1}">{0}</option>\n'

    @classmethod 
    def get(cls, data, layout):
        s = ''
        for k,v in data.items():
            s += layout.format(k,v)
        return s

def value_table(data, title=None, attributes={'border': 1}, layout=TupleLayout.tableTr):
     s = ''
     s = '<table ' + TupleLayout.get(attributes,  TupleLayout.attrs) + '>\n'
     if (title):
         s += '  <tr><th colspan="2" class="title">{0}</th></tr>\n'.format(title)
     s += TupleLayout.get(data, layout)
     s += '</table>\n'
     return s

    
#def key_value_row(key, value=None):
#    if (type(key) == tuple):
#        value = key[1]
#        key   = key[0]
#    tr = ET.Element('tr') # {"mika": "maki"})
#    tdKey   = ET.Element('td')
#    tdKey.text = key
#    tdValue = ET.Element('td')
#    tdValue.text = value

class KeyValueRowGenerator:
    """ Tool for generating elements that display <key>=<value> pairs for example as table rows or list items.
    """
    
    @classmethod
    def get(cls, entry):
        """ Entry should be an array or tuple.
        """
        #key,value = entry
        attributes = {"class": "lead"}
        tr = ET.Element('tr')
        for i in entry:
            td = ET.Element('td', attributes)
            td.text = str(i)
            tr.append(td)
            attributes = {}
        return tr
    
def get_table(data, attributes={}, row_generator = KeyValueRowGenerator):
    table = ET.Element('table', attributes)
    append_table(table, data, attributes, row_generator)
    return table

def append_table(table, data, attributes={}, row_generator = KeyValueRowGenerator):
    if ('title' in attributes):
        tr = ET.Element('tr')
        elem = ET.Element('th', {'colspan':'2', 'class': 'lead'} )
        elem.text = attributes['title']
        tr.append(elem)
        table.append(tr)
    for i in data.items():
        table.append(row_generator.get(i))
    return table


    
def get_by_tag(html, tag='span', attributes={}):
    for i in html:
        if i.tag == tag:
            return i
    elem = ET.Element(tag, attributes)
    html.append(elem)
    return elem

def get_head(html):
    return get_by_tag(html, 'head')

def get_body(html):
    return get_by_tag(html, 'body')

    
def get_by_id(html, id, tag='*'):
    #query = ".//{0}[@id='{1}']".format(tag,id)
    #query = './/{0}[@id="{1}"]'.format(tag,id)
    query = './/*[@id="{0}"]'.format(id)
    try:
        elems = html.findall(query)
        if elems:
            return elems[0]
    except AttributeError as err:
        print ("Failed: ",html,id,tag,query)
        #print err

    #if tag=='*':
    tag = 'span' # neutral element
    elem = ET.Element(tag, {'id': id})
    html.append(elem)
    return elem
