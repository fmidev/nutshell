<?php
/*
    Copyright 2003 - 2008  Markus Peura, Finnish Meteorological Institute (First.Last@fmi.fi)


    This file is part of DeerAppServer.

    DeerAppServer is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    DeerAppServer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser Public License for more details.

    You should have received a copy of the GNU General Public License
    along with DeerAppServer.  If not, see <http://www.gnu.org/licenses/>.
*/
/*! \mainpage
 *
 *
 * \section operation Operation
 *
 * \htmlinclude deer_appserver_variables.html
 *
 * \section configuration Configuration
 *
 * \subsection www_conf WWW server configuration
 *
 * The basic purpose of a \c .htaccess file is to redirect all
 * the syntactically acceptable requests to deer_appserver.php.
 *
 * \include deer_appserver_htaccess.txt
 *
 * \subsection inifile General system configuration
 *
 * First you have to ... \var PRODUCT_DIR
 *
 * \include deer_appserver_example.ini
 *
 * \subsection phpcnffile General system configuration2
 *
 * First you have to deer_appserver.cnf
 *
 * \include deer_appserver.cnf
 *
 * First you have to ... \var PRODUCT_DIR
 *
 * \include deer_appserver_cnf.php
 *
 * \section modes Service modes
 *
 * DeerAppserver may respond in various ways:
 *
 * - returning the requested file 
 * - reporting success/failure of generation, \see \ref report
 * - listing the file(s) generated or retrieved (plain text or html)
 * - showing a simple web page
 *
 * \section request Requests 
 *
 * Requests can be done in two ways: using http or command line.
 *
 *  \dot
  digraph example {  

  node [shape=record, fontname=Helvetica, fontsize=10]; 
  
  { rank = same; "http"; "cmd";};

  { rank = same; "php";};

  { rank = same; "p1"; "p2"; "p3";};

  { rank = max; "cache_l"; "cache_r"; "cache_f"};

  
  http [ label="http request" color="red"  URL="\ref http_request"];

  cmd [ label="system request" color="red" URL="\ref system_request"];

  sh [ label="deer_appserver[.sh]" URL="\ref bash_wrapper"];

  php [ label="deer_appserver.php" URL="\ref c"];


  p  [ label="product directory" URL="\ref PRODUCT_ROOT"];

  p1 [ label="prod 1" ];
  p2 [ label="prod 2" ];
  p3 [ label="prod 3" ];

  generator [ label="generate.[sh\|php\|py] "];

  apache [ label="apache WWW server" URL="\ref A"];
  cache_f [ label="disk (local, fast)" URL="\ref fastdisk"];
  cache_l [ label="disk (local)" URL="\ref disk"];
  cache_r  [ label="disk (remote)" URL="\ref cache"];
//  cmd -> cache_l;
//  cmd -> cache_r;

  cmd -> sh [color=red];
  sh -> php [color=red];

  http -> apache [color=red ];
  apache -> php [color=red ];
  apache -> cache_l;
  php -> p [color=red ];
  p -> {p1; p2; p3;} [color=red ];

  p3 -> generator;
//  p3 -> cache_l;

  }
  \enddot
  *
  * \subsection http_request HTTP requests 
  *
  \subsection system_request HTTP requests 
  
  The bash \ref  system_request_code wrapper code is attached in the appendix

 
  * \author Markus.Peura@fmi.fi

  \section appendix Appendix

  \subsection system_request_code System level wrapper code

\include deer_appserver.sh

  */

ini_set("display_errors",true);
ini_set("display_startup_errors",true);
error_reporting(E_ALL ^ E_NOTICE);
umask(0111);
$LOG_STRING='';

/*! Special.
 *
 */

// Default values

if (!empty($_SERVER['SERVER_NAME'] )){
  $QUERY['PRODUCT_SERVER'] = $_SERVER['SERVER_NAME'];
  $QUERY['PRODUCT_BASEURL'] = "http://$QUERY[PRODUCT_SERVER]/products";
};
     

/// The system path of \c PRODUCT_BASEURL.
if ( ! isset($QUERY['PRODUCT_ROOT']) )
     $QUERY['PRODUCT_ROOT'] = realpath(dirname($_SERVER['SCRIPT_FILENAME']));
//realpath(dirname($_SERVER['SCRIPT_FILENAME']).'/..');

/// The HTTP URL under which the appserver functions.


/// The file in which the log dump produced by the appserver is directed. 
/*! The appserver tries to open the file. If successful, thereafter log is written there.
 */
$QUERY['LOG_FILE_APPSERVER'] = 'deer_appserver.log';


/// The file in which the log dump produced by the application (and its wrapper). 
/*! The appserver tries to open the file. If successful, thereafter log is written there.
 */
$QUERY['LOG_FILE']  = 'generate.log';


/// www-server mode
if (!empty($_GET)){
  $QUERY['LOG_LEVEL'] = 1;
  $QUERY['REPORT'] = 0;
  $QUERY['PIPE'] = true;
  $QUERY['HTTPHEADERS'] = true;
  $QUERY['FILELIST'] = false;
  // print '=='.print_r($_GET,true);
}
// command line-mode
else { 
  $QUERY['LOG_LEVEL']  = 10;
  $QUERY['REPORT'] = 0; // 1
  $QUERY['PIPE'] = false;
  $QUERY['HTTPHEADERS'] = false;
  //  $QUERY['HTTPHEADERS'] = true;
  $QUERY['FILELIST'] = true;
};

/// Override the above declared.
/*
if (file_exists($f = 'deer_appserver_conf.ini')){
  $QUERY['CONF_FILE_INI'] = $f;
  $QUERY = array_merge($QUERY,parse_ini_file($f,true));
};
*/



/*! \var Test
 *  \brief A member function.
 *  \param c a character.
 *  \param n an integer.
 *  \exception std::out_of_range parameter is out of range.
 *  \return a character pointer.
 */
$Test = 5;


/// Macros
function SYSTEM_FILE_PATH(){
  global $QUERY;
  return $QUERY['PRODUCT_ROOT'].'/'.$QUERY['TARGET_DIR'].'/'.$QUERY['FILE'];
}

/// Macros
function SYSTEM_DIR_PATH(){
  global $QUERY;
  return $QUERY['PRODUCT_ROOT'].'/'.$QUERY['TARGET_DIR'];
}


// consider functions for sending the file back with short expiration date

/// Getting obsolete?
function debug($var){
  global $$var;
  print "<PRE>\n$var=\n";
  print_r($$var);
  print "</PRE>\n";
};

/// 
function read_conf($file){
  global $QUERY;
  write_log("Searching for conf file: ".$file);
  if (file_exists($file)){
    write_log("Found, reading it...".$file);
    $QUERY['CONF_FILE_PHP'][] = $file;
    include_once($file);
  }
  else {
    write_log("(Not found.)");
  }
  write_log_ok();
};

/* under consideration
function read_arguments(){
  global $QUERY;
  foreach ($argv as $a)
    if (ereg("^([^=]+)=(.*)$",$REG))
      $QUERY[ trim($REG[1]) ] = trim($REG[2]);
};
*/

/// 
function read_user_parameters(){
  global $QUERY, $_ENV, $_GET;
  /// Finally, override with environment
  $QUERY = array_merge($QUERY,$_ENV);
  /// Finally, override with http get variables
  $QUERY = array_merge($QUERY,$_GET);
};

/// This will...
function open_appserver_log_file(){
  global $QUERY,$LOG_STRING;;
  //  $QUERY['LOG_FILE_APPSERVER'] = getcwd().'/'.$QUERY['LOG_FILE_APPSERVER'];
  $QUERY['LOG_FILE_APPSERVER_ID'] = fopen($QUERY['LOG_FILE_APPSERVER'],'w');
  //  fwrite($QUERY['LOG_FILE_APPSERVER_ID'],"Log opened on ".gmdate('Y M d H:i')."by '".__FILE__."'\n\n");
  if ($QUERY['LOG_FILE_APPSERVER_ID']){
    write_log($LOG_STRING,0);
    write_log("Succesfully opened... $QUERY[LOG_FILE_APPSERVER]");
    $LOG_STRING='';
  }
  else
    write_log_warn("Failed in opening log file ($QUERY[LOG_FILE_APPSERVER]).");
};



/// This will...
function write_log($str,$level = 0){
  global $QUERY,$LOG_STRING;
  $ID = $QUERY['LOG_FILE_APPSERVER_ID'];
  //  if (!$QUERY['LOG_FILE_APPSERVER_ID'])    return;
  if ($level <= $QUERY['LOG_LEVEL'])
    if ($ID)
      fwrite($ID,$str."\n");
    else
      $LOG_STRING .= $str."\n";
  if ($level == -2){
    //    fwrite($QUERY['LOG_FILE_APPSERVER_ID'],"\nQuitting...\n");
    close_log_file();
    //give_up();
    send_response();
    die();
    //exit;
  };
};

/// This will...
function close_log_file(){
  global $QUERY;
  global $LOG_STRING;
  if ($ID =& $QUERY['LOG_FILE_ID']){
    fclose($ID);
    $ID = 0;
  }
  /*
  else {
    if ((bool)$QUERY['HTTPHEADERS'])
      header("Content-type: text/plain\n\n");
    print $LOG_STRING;
  }
  */
};


/*
function give_up(){
  global $QUERY;
  //  if ($QUERY[''])

  if ($QUERY['REPORT'] > 0){

    if ((bool)$QUERY['HTTPHEADERS'])
      header("Content-type: text/plain\n\n");

    print "******* LOG_FILE_APPSERVER *******\n";
    /// Either of these has it.
    print $LOG_STRING;
    readfile($QUERY['LOG_FILE_APPSERVER']);

    print "******* LOG_FILE *******\n";
    readfile($QUERY['LOG_FILE']);

  };

};
*/

/// This will...
function write_log_ok($level = 1){
  write_log("[ OK ]\n\n",$level);
};

/// This will...
function write_log_warn($str = '',$level = -1){
  write_log("[ WARNING ] $str\n\n",$level);
};

function write_log_error($str = '', $level = -2){
  write_log("[ ERROR ] $str\n\n",$level);
};


/// 
function write_log_variable($var,$level = -1){
  global $QUERY;
  global $$var;
  write_log("$var = ".print_r($$var,true),$level);
  if (!empty($QUERY[$var]))
    write_log("QUERY[$var] = ".print_r($QUERY[$var],true),$level);
};



/// This will...
function write_parser_log($var,$Results){
  global $QUERY;
  write_log("Parsing...\n",2);
  write_log("$var: (".$QUERY[$var].")\n",1);
  $str='';
  foreach ($Results as $v)
    $str .= "\t$v = ".$QUERY[$v]."\n";
  write_log($str,0);
};


/// The most important is $FILE.
function parse_file_variables(){ //&$FILE){
  global $QUERY;

  $wildcard_regexp = '[\*\?\{\}]';

  if (ereg($wildcard_regexp,$QUERY['FILE'])){
    $QUERY['FILE_MATCH'] = $QUERY['FILE'];
    $QUERY['FILE'] = ereg_replace($wildcard_regexp,'',$QUERY['FILE']);
  };

  /// First, it detects and cuts COMPRESSION, if used.
  $Reg = array();
  if (ereg('^(.*)\.(Z|gz|zip|bz)$',$QUERY['FILE'],$Reg)){
    $QUERY['FILE'] = $Reg[1];
    $QUERY['COMPRESSION'] = $Reg[2];
  }
  write_parser_log('FILE',array('FILE','COMPRESSION'));

  /// Second, extract other basic fields in the filename.
  /// There has to be a format (= problem?)
  if (ereg('^((([0-9]{4,12})([A-Za-z]{3})?)_)?([a-zA-Z][^_/]*)(_([^/]+))?(\.([a-zA-Z0-9]+))$',$QUERY['FILE'],$Reg)){
    $QUERY['TIMESTAMP']  = $Reg[3];
    $QUERY['TIMEZONE']   = $Reg[4];
    $QUERY['PRODUCT']    = $Reg[5];
    $QUERY['PARAMETERS'] = $Reg[7];
    $QUERY['FORMAT']     = $Reg[9];
    $QUERY['PRODUCT_DIR']= strtr($QUERY['PRODUCT'],'.','/');
  };

  write_parser_log('FILE',array('TIMESTAMP','TIMEZONE','PRODUCT','PARAMETERS','FORMAT','PRODUCT_DIR') );

  if ( !empty($QUERY['COMPRESSION']) )    
    $QUERY['FILE'] .= '.'.$QUERY['COMPRESSION'];
    
  /// Extract futher variables from TIMESTAMP, if supplied
  if (!empty($QUERY['TIMESTAMP'])){
    $SYSTEM['DYNAMIC'] = 1;  //true

    ereg('^([0-9]{4})([0-9]{2})([0-9]{2})?([0-9]{2})?([0-9]{2})?',$QUERY['TIMESTAMP'],$Reg);
    $QUERY['YEAR']   = $Reg[1];
    $QUERY['MONTH']  = $Reg[2];
    $QUERY['DAY']    = $Reg[3];
    $QUERY['HOUR']   = $Reg[4];
    $QUERY['MINUTE'] = $Reg[5];

    $QUERY['UNIXSECONDS'] = gmmktime($QUERY['HOUR'],$QUERY['MINUTE'],0,$QUERY['MONTH'],$QUERY['DAY'],$QUERY['YEAR']);

    $QUERY['TIMESTAMP_DIR'] = $QUERY['YEAR'].'/'.$QUERY['MONTH'].'/'.$QUERY['DAY'];

    write_parser_log('TIMESTAMP',array('YEAR','MONTH','DAY','HOUR','MINUTE','UNIXSECONDS','TIMESTAMP_DIR'));
      
    if ($QUERY['UNIXSECONDS'] <= 0)
      write_log_error('TIMESTAMP parsing error.');
    
    //   $QUERY['TIMESTAMP_DIR']; already defined in .htaccess
  } 
  else
    $SYSTEM['DYNAMIC'] = 0;  //false
  
  if (!empty($QUERY['PARAMETERS'])){
    //$QUERY['Parameter']  
    $Parameter = explode('_',$QUERY['PARAMETERS']);
    
    $i = 0;
    $P  = array();
    $PN = array();
    //    foreach ($QUERY['PARAMETER'] as $parameter){
    foreach ($Parameter as $parameter){
      /*! Each value of form KEY=VALUE is recognized by default.
     *  In addition to '=',
     *  the filenames are allowed to have the following characters:
     *  +,-,.
     */
      $Reg = array();
      if (ereg('^(.*)=(.*)$',$parameter,$Reg)){
	$QUERY[ $Reg[1] ] =  $Reg[2];
	$P[] = $Reg[1];
      }
      /*! In addition, parameters are assigned to 
        $QUERY['P0'], $QUERY['P1'], ...
	This is to help bash, which does not export arrays.
      */
      $QUERY['P'.$i] = $parameter;
      $PN[] = 'P'.$i;
      $i++;
      
    }
    write_parser_log('PARAMETERS',$PN);
    write_parser_log('PARAMETERS',$P);
  }
  
  
  if (empty($QUERY['TARGET_DIR']))
    if (empty($QUERY['TIMESTAMP_DIR']))
      $QUERY['TARGET_DIR'] = 'query/'.$QUERY['PRODUCT_DIR'];
    else
      $QUERY['TARGET_DIR'] = 'query/'.$QUERY['TIMESTAMP_DIR'].'/'.$QUERY['PRODUCT_DIR'];

  //  print("td=$QUERY[TARGET_DIR]\n");

  if (empty($QUERY['TMP_DIR']))
    if (empty($QUERY['TIMESTAMP_DIR']))
      $QUERY['TMP_DIR'] = 'query_tmp/'.$QUERY['PRODUCT_DIR'];
    else
      $QUERY['TMP_DIR'] = 'query_tmp/'.$QUERY['TIMESTAMP_DIR'].'/'.$QUERY['PRODUCT_DIR'];

  write_parser_log('FILE',array('TARGET_DIR','TMP_DIR'));

  write_log("Syntax check for TARGET_DIR...\n",1);
  if (ereg("^query(_[a-z0-9]+)?(/.*)?$",$QUERY['TARGET_DIR']))
    write_log_ok();
  else
    write_log_error();

  write_log("Syntax check for TMP_DIR...\n",1);
  if (ereg("^[a-z0-9_]*tmp(/.*)?$",$QUERY['TMP_DIR']))
    write_log_ok();
  else
    write_log_error();

  /// Konsider unset PARAMETERS!!!

  /*
  if (!empty( $QUERY['FILE_MATCH'] )){
    unset( $QUERY['FILE'] );
    //    $QUERY['FILE'] = ereg_replace($wildcard_regexp,'',$QUERY['FILE']);
    //    print $QUERY['FILE']."\n";
  }
  */
  //strtr($QUERY['FILE'],'*?{}');

};


function check_product_directory(){
  global $QUERY;
  write_log("Does product directory exist?\n",1);
  $d = $QUERY['PRODUCT_ROOT'].'/'.$QUERY['PRODUCT_DIR'];
  write_log("$d\n");
  if (is_dir($d)){
    write_log_ok();  
  }
  else {
    write_log_error('(does not exist)');  
  };
};


/// The log file to be written by the deer appserver host routines.
function detect_appserver_log_file(){
  global $QUERY; 
  extract($QUERY);
  $d = "$PRODUCT_ROOT/$PRODUCT_DIR"; //dirname(SYSTEM_FILE_PATH());
  $Candidates = array(
		      "$d/$LOG_FILE_APPSERVER", 
		      "$d/http_query.log", // old style
		      "$LOG_FILE_APPSERVER",
		      "$d/log/$FILE.$PID.deer_appserver.log",
		      "log/$FILE.$PID.deer_appserver.log",
		      "$d/deer_appserver.log",
		      "./deer_appserver_$PRODUCT.log",
		      "$FILE.log"
		      );
  //  create_file($QUERY['LOG_FILE_APPSERVER'],$Candidates);
  detect_log_file($QUERY['LOG_FILE_APPSERVER'],$Candidates);
};


/// The log file to be written by the generator (wrapper).
function detect_generator_log_file(){
  global $QUERY; 
  extract($QUERY);
  $d = dirname(__FILE__);
  $Candidates = array(
		      "$LOG_FILE",
		      "log/$PRODUCT.log",
		      "$PRODUCT_ROOT/log/$PRODUCT.log",
		      "log/$FILE.log",
		      "log/$FILE.$PID.log",
		      "$PRODUCT_ROOT/log/$FILE.$PID.log",
		      SYSTEM_FILE_PATH().".log",
		      );
  //  create_file($QUERY['LOG_FILE'],$Candidates);
  //  detect_log_file($QUERY['LOG_FILE'],$Candidates);
  detect_log_file($QUERY['LOG_FILE'],$Candidates);
};




/// Returns (LOG_FILE); which relative to product dir, or absolute.
function detect_log_file(&$file,$Candidates = array()){
  //  global $QUERY; 
  //extract($QUERY);
  write_log("Detecting log files...\n",1);

  if (empty($Candidates))
    $Candidates = array($file);

  foreach ($Candidates as $f){
    //    $f = realpath(dirname($file)).''.basename($file);
    //    $d = readpath($file);
    //print "$f\n";
    write_log("? '$f'",1);
    if (create_file($f,0)){
      write_log_ok("($f)",1);
      $file = $f;
      return;
    };
  };
  write_log_error("Failed in creating log file (LOG_FILE)");
};


/// Starting from system-path $root, creates directory down to $dir
/// giving full read and write permissions.
/// 
/*! \fn create_directory($root,$dir,&$LOGTEXT)
 *  \brief A member function.
 *  \param c a character.
 *  \param n an integer.
 *  \exception std::out_of_range parameter is out of range.
 *  \return a character pointer.
 */
function create_directory($root,$dir){
  global $QUERY;
  $fulldir = "$root/$dir";

  write_log("Searching for directory '$fulldir'\n");

  write_log("Does it exist already?\n");

  if (is_dir($fulldir)){
    write_log_ok();
    write_log("Is it writable?\n");
    //    $LOGTEXT .= "Directory '$fulldir' is writable?\n";
    if (is_writable($fulldir)){
      write_log_ok();
    } 
    else {
      write_log_error();
    }
    return;
  }
  else {
    write_log("Directory does not exist; trying to create it...\n");
    //    $LOGTEXT .= " [ ?? ] Directory does not exist; trying to create it...\n";
    // This command relies on a proper group, see 'chmod -R g+s *'
    $u = umask(0000);
    $Dir = explode('/',$dir);
    $fulldir = $root.'/.';
    foreach ($Dir as $d){
      $fulldir .= '/'.$d;
      write_log($fulldir."\n",1);
      if (!is_dir($fulldir) && !is_link($fulldir))
	@mkdir($fulldir);
      if (!is_dir($fulldir)){
	write_log_error();
	return;
      }
    }
    write_log_ok();
    umask($u);
  }
}

/// Create a file of zero size
function create_file($file,$error_code_on_fail=-2){
  //  global $QUERY;
  //  $F = SYSTEM_FILE_PATH();
  write_log("Creating empty file:\n$file");

  $result = false;

  if (file_exists($file)){
    if (!@touch($file))
      write_log("Could not touch file.", $error_code_on_fail);
  }
  else {
    if (!@touch($file))
      write_log("Could not touch file.", $error_code_on_fail);
    else
      if (@chmod ($file, 0666))
	//$result = true;
	//else
	write_log_warn("Could not set permissions."); //, $error_code_on_fail);
  }



  if (!is_writable($file)){
    //    write_log("File not writable.", $error_code_on_fail);
    write_log("File not writable.", $error_code_on_fail);
    $result = false;
  }
  else {
    write_log_ok();
    $result = true;
  }

  return  $result;
}

function variable_assignment_string($Format = array('prefix' => "",
						    'assign' => "='",
						    'suffix' => "';\n",
						    )){
  global $QUERY;
  extract($Format);
  $result = '';
  foreach ($QUERY as $key => $value){
    $result .= $prefix.$key.$assign.$value.$suffix;
  };
  return $result;
};


function generate_product(){
  global $QUERY;
  global $Files;
  $prefix = 'generate';

  // Konsider this earlier!!!
  //  $QUERY['FILE_PATH'] = $QUERY['PRODUCT_ROOT'].'/'.$QUERY['TARGET_DIR'].'/'.$QUERY['FILE'];
  $FILE_PATH = SYSTEM_FILE_PATH();


  $d = $QUERY['PRODUCT_ROOT'].'/'.$QUERY['PRODUCT_DIR'];
  write_log("Entering product dir...");
  write_log($d);
  chdir($d);

  write_log("Starting generator log...");
  detect_generator_log_file(); 
  write_log("$d/$QUERY[LOG_FILE]");

  //  $Files = glob("$d/$prefix*.*");
  $Files = glob("$prefix.{php,py,sh,make}",GLOB_BRACE);
  write_log("Found potential generator files:\n".print_r($Files,true));
  foreach ($Files as $f){
    $Path = pathinfo($f);
    write_log("Preparing to execute '$f'");
    //    $fullpath = getcwd().'/'.$file;
    switch ($ext = $Path['extension']){
    case 'php':
      generate_product_php($f);
      break;
    case 'sh':
	generate_product_sh($f);
	//      else
	//write_log("Not executable.",-1);
      break;
    default: 
      write_log("Found no handler of type '$ext' ($file)");
    };
  };

  // MUST!!!
  clearstatcache();
  if (filesize( $FILE_PATH ) > 0){
    write_log("Success.");
    write_log("Generated:\n $FILE_PATH\n");
    close_log_file(); // or flush
  }
  else {
    // print "filesize=".filesize( $FILE_PATH )."\n";
    // print exec("ls -ltr $FILE_PATH");
    unlink($FILE_PATH);
    write_log_error("Could not generate: \n $FILE_PATH");
  };

}

function write_application_log($string){
  global $QUERY;
  // $ID = &$QUERY['LOG_FILE_ID'];
  //if ($ID)
  fwrite($QUERY['LOG_FILE_ID'],$string);
}

/// Simple as that
function generate_product_php($file){
  global $QUERY;
  //  $AID = $QUERY['LOG_FILE_APPSERVER_ID'];
  // kludge... stealing dap's log handle for a while...
  write_log("Opening: ##### ".getcwd()."///$QUERY[LOG_FILE]");
  //  $QUERY['LOG_FILE_ID'] = fopen($QUERY['LOG_FILE'],'w');
  $QUERY['LOG_FILE_ID'] = fopen($QUERY['LOG_FILE'],'w');
  write_log("Appserver speaking...  $QUERY[LOG_FILE_ID] go!");
  ob_start("write_application_log");
  ob_implicit_flush(true);
  print ("generate_product_php including $file...\n");
  include_once($file);
  ob_end_clean();
  //  ob_implicit_flush(0);  // END
  fclose($QUERY['LOG_FILE_ID']);
  $QUERY['LOG_FILE_ID'] = 0;
  //  $QUERY['LOG_FILE_APPSERVER_ID'] = $AID;
}

function generate_product_sh($file){
  global $QUERY;
  //  $Result = array();
  $dir = getcwd();
  //  $full_path = $dir.'/'.$file;
  if (!is_executable($file)){
    write_log_warn("Not executable!");
  };
  $format  = array('prefix' => "export ",'assign' => "='",'suffix' => "';\n");
  $command = variable_assignment_string($format);
  //  $command .= "nice -5 ./$file 2> $QUERY[LOG_FILE] >> $QUERY[LOG_FILE];\n";
  $command .= "nice -5 ./$file > $QUERY[LOG_FILE] 2>&1;\n"; // see `man bash`
  //2>&
  write_log("$command\n");
  exec($command); //,$Result);
  return; // $Result;
}


function exec_input_sh($file){
  global $QUERY;
  $Result = array(); 
  $dir = getcwd();
  /*
  if (!is_executable($file)){
    write_log_warn("Not executable!");
  };
  */
  // $command = "cd $dir; ";
  $format  = array('prefix' => "export ",'assign' => "='",'suffix' => "';\n");
  $command = variable_assignment_string($format);
  //  $command .= "nice -5 ./$file"; 
  //  $command .= "nice -5 $file"; 
  $command .= "$file"; 
  //write_log("$command\n");
  exec($command,$Result);
  // $Result[] = "$command";
  return $Result;
};


function send_response(){
  global $QUERY;

  //  print_r($QUERY);
  //print SYSTEM_FILE_PATH()."\n";
  //print SYSTEM_DIR_PATH()."\n";
  //  print "#\n";
  $file = SYSTEM_FILE_PATH();

# && file_exists($file)

  if ( ((bool)$QUERY['PIPE']) &&(filesize($file) > 0))
     {
	  
	  // Send resulting file directly to the client.
	  if ((bool)$QUERY['HTTPHEADERS'])
	    header("Content-type: ".derive_mimetype($QUERY['FORMAT']));
	    //@header("Content-type: ".derive_mimetype($QUERY['FORMAT'])."\n\n");
	  // Consider expires-here
	  //    header("Content-type: ".mimetype($QUERY['FORMAT'])."\n\n");
	  //	  print "*";
	  readfile( $file );
	  //print "</BR>\n".derive_mimetype($QUERY['FORMAT']);
	}
      else {
	header("Content-type: text/txt");
      }

  if ($QUERY['FILELIST']){
    if (!empty($QUERY['FILE_MATCH'])){
      //$dir = SYSTEM_DIR_PATH();
      //      $Pattern = SYSTEM_FILE_PATH().'/'.$QUERY['FILE_MATCH'];  WORKS???
      $Pattern = SYSTEM_DIR_PATH().'/'.$QUERY['FILE_MATCH']; 
      //      print  $Pattern."\n";
      foreach (glob($Pattern,GLOB_BRACE) as $file)
	print $file."\n";
    }
    else
      if (file_exists($file = SYSTEM_FILE_PATH()))
	print "$file\n";
	//$file."\n";
      //  $Pattern = SYSTEM_FILE_PATH();
    

  };


  if ($QUERY['REPORT'] > 0) { 
    /// 
    if ((bool)$QUERY['HTTPHEADERS'])
      header("Content-type: text/plain\n\n");
    //    print "Query successful\n\n";
    print "Perhaps Created: '";
    print SYSTEM_FILE_PATH();
    print "'\n\n";
    if ($QUERY['REPORT'] > 1){
      /// Either of these has it.
      print "******* LOG_FILE_APPSERVER *******\n";
      print $LOG_STRING;
      readfile($QUERY['LOG_FILE_APPSERVER']);

      print "******* LOG_FILE *******\n";
      readfile($QUERY['LOG_FILE']);
    };
  };


  if ($QUERY['DELETE']){
    unlink( SYSTEM_FILE_PATH() );
  };
  
}

// utility
function derive_mimetype($extension){
  
  switch (strtolower($extension)){
  case 'gif':
  case 'png':
  case 'ppm':
  case 'pgm':
  case 'jpg':
  case 'jpeg':
  case 'tif': 
  case 'bmp':
    return ("image/$extension");
  case 'txt':
  case '':
    return ("text/plain");
  case 'html':
  case 'xml':
    return ("text/$extension");
  default:
    return ("application/$extension");
  }
  
}







/// Note: LOG will go to string buffer until open_appserver_log_file().
write_log("deer_appserver.php version 2.0b");
write_log("started on ".gmdate('Y m d H:M:i z'));
write_log("script: '".__FILE__."'");
write_log('Markus.Peura@fmi.fi');
write_log("\n\n");

read_conf(dirname(__FILE__).'/deer_appserver_cnf.php');
read_conf('deer_appserver_cnf.php');

#read_arguments();

read_user_parameters();



// todo: realpath LOG problem

// todo: quit-on ready 

write_log_variable('_GET');

write_log_variable('_SERVER');

write_log_variable('QUERY');


if ( !empty($QUERY['FILE']) ){
  parse_file_variables($QUERY['FILE']);
}
else {
  write_log_error("No FILE supplied, and failed in constructing it.");
}


//print("TD = $QUERY[TARGET_DIR]\n");

check_product_directory();

detect_appserver_log_file();
open_appserver_log_file();

//detect_generator_log_file(); 




// Basically, this is unnecessary in some response modes.
create_directory($QUERY['PRODUCT_ROOT'],$QUERY['TARGET_DIR']);

create_file($f = SYSTEM_FILE_PATH());
//create_directory($QUERY['PRODUCT_ROOT'],$QUERY['TMP_DIR']);



//prepare_input();

generate_product();

send_response();

write_log("FINISHED\n");  

?>
