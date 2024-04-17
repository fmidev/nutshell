
/**
   Markus Peura @fmi.fi
*/
function readFile(filename, callback, params){

    /// Thanks to: http://stackoverflow.com/questions/14446447/javascript-read-local-text-file

    var file = new XMLHttpRequest();

    file.onreadystatechange = function (){
        if (file.readyState === 4){
	    if (file.status === 200 || file.status === 0){
		callback(file.responseText, params)
	    }
	    else {
		console.error('File read error: file="'+filename+'", status=' + file.status)
	    }
        }
    }

    try {
	file.open("GET", filename, true)  // asynch
	file.send(null) // null
    }
    catch (error) {
	console.error('could not read: ' + filename)
    }
    
}

function populateSelect(text, params){

    //console.debug(text)
    var json = JSON.parse(text)
    // console.info(json)
    // console.warn(params)
    console.info("Adding products in menu")

    const ARRAY = Array.isArray(json)
    for (var i in json){
	//console.info(i + typeof(i) + ' = ' + json[i])
	var elem = document.createElement("option")
	elem.setAttribute("value", json[i])
	if (!ARRAY){
	    elem.setAttribute("label", i)
	    elem.setAttribute("title", i)
	    elem.textContent = i
	}
	// console.info(elem)
	product_examples.add(elem)
    }
}

function nutshell_body(){

    // Modern browsers should support
    if (URLSearchParams){
	var params = new URLSearchParams(document.location.search)
	console.info(params)
	product.value = params.get('product')
    }

    if (product.value){
	//console.info(p)
	// Option:
	STATUS.checked = true 
    }
    else {
	const filename = "product-examples.json"
	console.info("Reading " + filename)
	readFile(filename+'?'+Date.now(), populateSelect, ["test", 1, "test2", 2 ] )
    }
    
    
    
}

// console.warn("Completed form.js")
