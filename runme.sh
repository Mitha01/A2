#!/bin/bash

function compile_all {
  # Check for the existence of the compiled/ folder and create it if it doesn't exist
  mkdir -p compiled/
  mkdir -p compiled/OrderService
  mkdir -p compiled/UserService
  mkdir -p compiled/ProductService
  mkdir -p compiled/ISCS

  # Compile everything, move JAR files to the compiled directory, and delete non-dependency JARs
  (cd src/OrderService && mvn clean package && cp target/*-jar-with-dependencies.jar ../../compiled/OrderService/ && rm -f target/*-SNAPSHOT.jar)
  (cd src/UserService && mvn clean package && cp target/*-jar-with-dependencies.jar ../../compiled/UserService/ && rm -f target/*-SNAPSHOT.jar)
  (cd src/ProductService && mvn clean package && cp target/*-jar-with-dependencies.jar ../../compiled/ProductService/ && rm -f target/*-SNAPSHOT.jar)
  (cd src/ISCS && mvn clean package && cp target/*-jar-with-dependencies.jar ../../compiled/ISCS/ && rm -f target/*-SNAPSHOT.jar)
}

 # Start the UserService
function start_user_service {
  java -jar compiled/UserService/UserService-1.0-SNAPSHOT-jar-with-dependencies.jar
}

# Start the ProductService
function start_product_service {
  java -jar compiled/ProductService/ProductService-1.0-SNAPSHOT-jar-with-dependencies.jar
}

# Start the ISCS
function start_iscs {
  java -jar compiled/ISCS/ISCS-1.0-SNAPSHOT-jar-with-dependencies.jar
}

# Start the OrderService
function start_order_service {
  java -jar compiled/OrderService/OrderService-1.0-SNAPSHOT-jar-with-dependencies.jar
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