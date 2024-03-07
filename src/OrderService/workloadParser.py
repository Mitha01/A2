import sys
import os
import time

import requests
import simplejson as json
import subprocess

from pathlib import Path


shutdown_flag_file = Path(__file__).resolve().parent / "shutdown_status.txt"

def write_shutdown_status():
    if shutdown_flag_file.exists():
        with open(shutdown_flag_file, "w") as file:
           file.write("1")
    return False

def check_shutdown_flag():
    if shutdown_flag_file.exists():
        with open(shutdown_flag_file, "r") as file:
            flag = file.read().strip()
            return flag == "1"
    return False

def reset_shutdown_flag():
    with open(shutdown_flag_file, "w") as file:
        file.write("0")

def start_iscs_service():
    # Adjust the path to your JAR file
    command = 'open -a Terminal "`pwd`"'
    jar_path = "//Users/susmi/Desktop/SCHOOL/CSC301/a1/compiled/ISCS/ISCS-1.0-SNAPSHOT-jar-with-dependencies.jar"
    subprocess.run(["osascript", "-e", f'tell application "Terminal" to do script "java -jar {jar_path}"'], cwd="/Users/susmi/Desktop/SCHOOL/CSC301/a1")

def parse_order_line(line):
    parts = line.split()
    return {
        "service": "ORDER",
        "command": "place",
        "product_id": int(parts[1]),
        "user_id": int(parts[2]),
        "qty": int(parts[3])
    }


def parse_product_line(line):
    parts = line.split()

    if parts[1] == "create":
        return {
            "service": "PRODUCT",
            "command": "create",
            "id": int(parts[2]),
            "name": str(parts[3]),
            "description": str(parts[4]),
            "price": float(parts[5]),
            "qty": int(parts[6])
        }

    elif parts[1] == "info":
        return {
            "service": "PRODUCT",
            "command": "info",
            "id": int(parts[2])
        }

    elif parts[1] == "update":
        product_info = {
            "service": "PRODUCT",
            "command": "update",
            "id": int(parts[2])
        }

        for part in parts[3:]:
            if part.startswith("name:"):
                product_info["name"] = str(part.replace("name:", ""))
            elif part.startswith("description:"):
                product_info["description"] = str(part.replace("description:", ""))
            elif part.startswith("price:"):
                product_info["price"] = float(part.replace("price:", ""))
            elif part.startswith("quantity:"):
                product_info["qty"] = int(part.replace("quantity:", ""))

        return product_info

    elif parts[1] == "delete":
        return {
            "service": "PRODUCT",
            "command": "delete",
            "id": int(parts[2]),
            "name": str(parts[3]),
            "price": float(parts[4]),
            "qty": int(parts[5])
        }


def parse_user_line(line):
    parts = line.split()

    if len(parts) == 1:
        if parts[0] == "shutdown":
            return {
                "service" : "shutdown"
            }
        elif parts[0] == "restart":
            return {
                "service" : "restart"
            }

    if parts[1] == "update":
        user_info = {
            "service": "USER",
            "command": "update",
            "id": int(parts[2])
        }

        for part in parts[3:]:
            if part.startswith("username:"):
                user_info["username"] = str(part.replace("username:", ""))
            elif part.startswith("email:"):
                user_info["email"] = str(part.replace("email:", ""))
            elif part.startswith("password:"):
                user_info["password"] = str(part.replace("password:", ""))

        return user_info

    elif parts[1] == "get":
        return {
            "service": "USER",
            "command": "get",
            "id": int(parts[2]),
        }

    elif parts[1] == "purchased":
        return {
            "service": "USER",
            "command": "purchased",
            "id": int(parts[2]),
        }

    elif parts[1] == "create":
        return {
            "service": "USER",
            "command": "create",
            "id": int(parts[2]),
            "username": str(parts[3]),
            "email": str(parts[4]),
            "password": str(parts[5])
        }

    elif parts[1] == "delete":
        return {
            "service": "USER",
            "command": "delete",
            "id": int(parts[2]),
            "username": str(parts[3]),
            "email": str(parts[4]),
            "password": str(parts[5])
        }



def parse_file(file_path):
    commands = []
    with open(file_path, 'r') as file:
        for line in file:
            line = line.strip()
            if line.startswith("ORDER"):
                order_data = parse_order_line(line)
                commands.append(order_data)

            elif line.startswith("PRODUCT"):
                product_data = parse_product_line(line)
                commands.append(product_data)

            elif line.startswith("USER"):
                user_data = parse_user_line(line)
                commands.append(user_data)

            elif line.startswith("shutdown"):
                user_data = parse_user_line(line)
                commands.append(user_data)

            elif line.startswith("restart"):
                user_data = parse_user_line(line)
                commands.append(user_data)


    return commands

def ResponseBody(response):
    statusCode_messages = {200: "OK", 400: "Bad Request", 404:"Not Found", 405:"Not Allowed", 409:"Conflict", 500:"Internal Server Error"}
    status_message = statusCode_messages[response.status_code]
    status_code = response.status_code

    print(f"{status_code}:{status_message}", response.text)



def main():
    statusCode_messages = {200: "OK", 400: "Bad Request", 404:"Not Found", 405:"Not Allowed", 409:"Conflict", 500:"Internal Server Error"}

    # Construct the path to the config.json file
    base_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(base_dir, '../../config.json')

    # Read and parse the config.json file
    with open(config_path, 'r') as config_file:
        json_contents = json.load(config_file)

    # get order and iscs destinations
    order_service_ip, order_service_port = json_contents["OrderService"]['ip'], json_contents["OrderService"]['port']
    iscs_service_ip, iscs_service_port = (json_contents["InterServiceCommunication"]['ip'],
                                          json_contents["InterServiceCommunication"]['port'])
    order_url = f"http://{order_service_ip}:{order_service_port}/order"
    iscs_url = f"http://{iscs_service_ip}:{iscs_service_port}"

    # parse the workload file
    file_path = sys.argv[1]
    commands = parse_file(file_path)
    session = requests.Session()


    for command in commands:
        shutdown_flag = check_shutdown_flag()

        response = ""
        if command["service"] != "restart" and shutdown_flag:
            print("HERE")
            restart_iscs_url_for_product = iscs_url + "/product/restart"
            restart_iscs_url_for_user = iscs_url + "/user/restart"

            response1 = session.get(restart_iscs_url_for_user)
            response2 = session.get(restart_iscs_url_for_product)
            reset_shutdown_flag();

        if command["service"] == "ORDER":
            del command["service"]
            response = session.post(order_url, json=command)
            ResponseBody(response)

        elif command["service"] == "USER":
            del command["service"]
            iscs_url_for_user = iscs_url + "/user"
            if command["command"] == "get" or command["command"] == "purchased":
                get_iscs_url_for_user = iscs_url_for_user + "/" + str(command["command"]) + "/" +str(command["id"])
                response = session.get(get_iscs_url_for_user)
            else:
                response = session.post(iscs_url_for_user, json=command)
            ResponseBody(response)

        elif command["service"] == "PRODUCT":
            del command["service"]
            iscs_url_for_product = iscs_url + "/product"
            # print(iscs_url_for_product, command)
            if command["command"] == "info":
                get_iscs_url_for_product = iscs_url_for_product + "/" + str(command["id"])
                response = session.get(get_iscs_url_for_product)
            else:
                response = session.post(iscs_url_for_product, json=command)
            ResponseBody(response)

        elif command["service"] == "shutdown":
            write_shutdown_status()

            shutdown_iscs_url_for_product = iscs_url + "/product/shutdown"
            shutdown_iscs_url_for_user = iscs_url + "/user/shutdown"
            shutdown_order_url = order_url + "/shutdown"

            response1 = session.get(shutdown_iscs_url_for_user)
            response2 = session.get(shutdown_iscs_url_for_product)
            response3 = session.get(shutdown_order_url)

            if response1.status_code == 200 and response2.status_code == 200 and response3.status_code == 200:
                print('{"command": "shutdown"}, 200 OK status code')
            return

        elif command["service"] == "restart":
            if shutdown_flag:
                reset_shutdown_flag();
                print('{"command": "restart"}, 200 OK status code')
            else:
                restart_iscs_url_for_product = iscs_url + "/product/restart"
                restart_iscs_url_for_user = iscs_url + "/user/restart"

                response1 = session.get(restart_iscs_url_for_user)
                response2 = session.get(restart_iscs_url_for_product)
                return

if __name__ == "__main__":
    main()

