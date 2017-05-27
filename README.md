# Pollster

Pollster is an application for tracking whether your favourite services are up and performing as expected, or whether you are having issues connecting.

![Screenshot](https://cloud.githubusercontent.com/assets/2118/26524380/30a694fa-4331-11e7-9a5e-e787797c8fcf.png)

Pollster is the result of exploring vertx.io in Java. The main application is written in Java with a small front-end using Bootstrap and JQuery on the same host. It's not super configurable, but comes ready to run out of the box.

## Running
Pollster is packaged as a fat jar. After cloning the repository you should be able to run Maven and then run the resulting jar:

    mvn clean package
    java -jar target/pollster-1.0-SNAPSHOT-fat.jar

## Storage and configuration
Pollster binds on port 8080 by default and this is hardcoded into the user interface. You can view the UI by loading http://localhost:8080/ on the machine running Pollster.

The services to check are stored in a file called `services.json` in the working directory. This file has a simple JSON format.

    {
      "services" : [ {
        "id" : "e1a4ac3d-75d0-4f79-be69-5fbba28e4711",
        "name" : "google-https",
        "url" : "https://google.com/",
        "status" : "OK",
        "lastCheck" : "2017-05-27 11:15"
      }, {
        "id" : "a3650c6b-4c19-464b-bda6-892314a33020",
        "name" : "google-http",
        "url" : "http://google.com/",
        "status" : "OK",
        "lastCheck" : "2017-05-27 11:15"
      } ]
    }

It is not necessary to edit this file directly. You can add a new entry by clicking the "Add Service" button in the UI, and existing entries can be removed by clicking the "Delete" button on that row.

![Add service dialog](https://cloud.githubusercontent.com/assets/2118/26524381/44905a96-4331-11e7-9567-4377e2de151f.png)
