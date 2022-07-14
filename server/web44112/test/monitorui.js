QUnit.test( "hello test", function( assert ) {

	var editmode; 

	$.getJSON("./state.json?", function(data){
		console.log(data);
		editmode = data.editmode;
	});
	
	assert.ok( editmode === true, "Passed!" );
})