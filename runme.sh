#!/bin/bash

function compile_all {
  #excutable
  chmod +x runme.sh

  # Check for the existence of the compiled/ folder and create it if it doesn't exist
  mkdir -p compiled/
  mkdir -p compiled/OrderService
  mkdir -p compiled/UserService
  mkdir -p compiled/ProductService
  mkdir -p compiled/ISCS

  # Install workload parser dependency
  pip install simplejson

  # Compile everything
  javac ".\src\UserService\Main.java" -cp ".\compiled\json-20231013.jar" -d ".\compiled"
  javac ".\src\ProductService\Main.java" -cp ".\compiled\json-20231013.jar" -d ".\compiled"
  javac ".\src\ISCS\Main.java" -cp ".\compiled\json-20231013.jar;.\compiled\httpclient-4.5.14.jar;.\compiled\httpcore-4.4.16.jar" -d ".\compiled"
  javac ".\src\OrderService\Main.java" -cp ".\compiled\json-20231013.jar;.\compiled\httpclient-4.5.14.jar;.\compiled\httpcore-4.4.16.jar" -d ".\compiled"
}

 # Start the UserService
function start_user_service {
  java -cp ".\compiled:.\compiled\json-20231013.jar:.\compiled\sqlite-jdbc-3.35.0.jar:.\compiled\slf4j-api-2.0.11.jar" UserService.Main
}

# Start the ProductService
function start_product_service {
  java -cp ".\compiled:.\compiled\json-20231013.jar:.\compiled\sqlite-jdbc-3.35.0.jar:.\compiled\slf4j-api-2.0.11.jar" ProductService.Main
}

# Start the ISCS
function start_iscs {
  java -cp ".\compiled:.\compiled\json-20231013.jar:.\compiled\httpclient-4.5.14.jar:.\compiled\httpcore-4.4.16.jar" ISCS.Main
}

# Start the OrderService
function start_order_service {
  java -cp ".\compiled:.\compiled\json-20231013.jar:.\compiled\httpclient-4.5.14.jar:.\compiled\httpcore-4.4.16.jar" OrderService.Main
}

# Start the workload generator with the provided workload file
function start_workload_parser {
  python3 src/OrderService/workloadParser.py "$1"
}

case "$1" in
  -c) compile_all ;;
  -u) start_user_service ;;
  -p) start_product_service ;;
  -i) start_iscs ;;
  -o) start_order_service ;;
  -w) shift; start_workload_parser "$@" ;; # shift allows the shell to visit the next parameter
    # because $@ was added, it allows the shell kernel to accept any variable number of commands without shifting each time
  *) echo "Usage: $0 {-c|-u|-p|-i|-o|-w workloadfile}"; exit 1 ;;
esac