# YTFTP
Java library for instantiating a TFTP server.

Based on [Apache Commons Net](https://commons.apache.org/proper/commons-net/) libraries and examples.


### Compiling
* Install [Maven](https://maven.apache.org/)
* Run `mvn clean package` from within the main project directory
* Optionally run `mvn -pl :ytftp-core install` to install the library into local maven repo


### Example code
The ["app" module](app) contains an example showing how to use this library inside another project.

This example code is build as part of the compiling process described previously, and the compiled program can be run with `java -jar app/target/ytftp-app-<VERSION>.jar` (from the main project directory).

These are the options supported by the example program:
```text
java -jar app/target/ytftp-app-0.0.1.jar

usage: YTFTP Server
 -h,--help                       Show this help
 -i,--listen-interface <IFACE>   Interface to listen on
 -l,--listen-address <IP>        IP to listen on
 -p,--port <PORT>                Port to listen on
 -r,--read-dir <READ_DIR>        Directory used to serve files
 -t,--type <SERVER_TYPE>         Server type (GET_ONLY, PUT_ONLY,
                                 GET_AND_PUT)
 -w,--write-dir <WRITE_DIR>      Directory used to save files
 -x,--log-level <LOG_LEVEL>      Log level (DEBUG, INFO, WARNING, ERROR)
```
