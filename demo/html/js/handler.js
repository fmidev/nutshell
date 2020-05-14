

function remake(){

    // DEBUG.form = elem.form
    //console.warn(this)
    elem = this 
    // ELEM = this

    const span = elem.parentElement
    //console.log(span)
    //console.log(span.getElementsByTagName('input'))

    const id = span.id.split('_')

    var product = {
	"TIMESTAMP": undefined,
	"ID": id.join("."),
	"PARAMETERS": {},
	"FORMAT": "png"	
    }

    
    console.log(product.ID)
    

    // INPUT elems
    elems = span.getElementsByTagName('input')
    for (var i=0; i<elems.length; ++i){
	item = elems.item(i)
	
	if (item.type == 'checkbox'){
	    if (item.checked)
		product.PARAMETERS[item.name] = item.value
	}
	else
	    product.PARAMETERS[item.name] = item.value
    }

    // SELECT elems
    elems = span.getElementsByTagName('select')
    for (var i=0; i<elems.length; ++i){
	item = elems.item(i)
	product.PARAMETERS[item.name] = item[item.selectedIndex].value
    }
    //console.log(product)

    // Parameter entries (actual parameters)
    product.parameters = []
    
    for (var key in product.PARAMETERS){
	value = product.PARAMETERS[key].replace(/\s/,'') // remove whitespace 
	switch (key){
	case 'TIMESTAMP':
	    product.TIMESTAMP = value.replace(/[^0-9]/g, '') // remove also non-digits
	    break
	case 'ID':
	    product.ID = value
	    break
	case 'FORMAT':
	    product.FORMAT = value
	    break
	default:
	    product.parameters.push(key + '=' + value)
	}
    }

    var filename = []

    if (product.TIMESTAMP)
	filename.push(product.TIMESTAMP)

    filename.push(product.ID)

    filename = filename.concat(product.parameters)

    filename_base = filename.join('_') 

    elem.form.elements.product.value = filename_base+'.'+product.FORMAT
    elem.form.target = product.ID

    img = document.getElementById('PROD_' + span.id)
    if (img) // always png
	url = 'nutshell/?request=MAKE&product=' + filename_base+'.png'
	img.src = url

    elem.form.target = product.ID

    console.log(filename_base)
    
}


// 'remake' has to be defined (above)
function addLabels(form){

    for (var i=0; i<form.elements.length; ++i){
	var elem = form.elements[i]
	var parent = elem.parentElement

	if ((elem.name) && (parent.id != "main")){
	    

	    if (!parent.header){
		
		parent.header =  document.createElement("b")

		parent.prepend(document.createElement("br"))
		parent.prepend(document.createElement("br"))
		
		parent.button =  document.createElement("button")
		parent.button.type = "button"
		parent.button.textContent = "Update"
		parent.button.onclick = remake // "concole.warn(this)" // remake // "remake(this)"
		parent.prepend(parent.button)
		parent.button.click() // !!
		BUTTON = parent.button
		
		parent.header.textContent = elem.parentElement.id.replace('_', '.')

		parent.prepend(parent.header)
		
		//<button  type="button" onclick="remake(this)
		//<button  type="button" onclick="remake(this)">Update</button><br/>
	    }
	}

    }

    for (var i=0; i<form.elements.length; ++i){

	var elem = form.elements[i]
	var parent = elem.parentElement

	if ((elem.name) && (parent.id != "main")){

	    var label = document.createElement("label")
	    label.setAttribute("for", elem.name)
	    label.textContent = elem.name[0] + elem.name.substr(1).toLowerCase() + ": "
	    //console.warn(label)
	    elem.before(label)
	    elem.after(document.createElement("br"))
	    LABEL = label
	    ELEM = elem
	}
	
    }
}
