package com.github.ansa89.ytftp.app;

/*
 * YTFTPApp.java - Example code to show how to create/use an instance of YTFTP server.
 *
 * Copyright 2020 Stefano Ansaloni.
 *
 *
 * This file is part of YTFTP.
 *
 * YTFTP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YTFTP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with YTFTP.  If not, see <http://www.gnu.org/licenses/>.
 */


import com.github.ansa89.ytftp.core.YTFTPServer;
import com.github.ansa89.ytftp.core.enums.YTFTPLogLevel;
import com.github.ansa89.ytftp.core.enums.YTFTPServerType;
import com.github.ansa89.ytftp.core.log.YTFTPLogger;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

public class YTFTPApp {
    private static final String OPT_HELP = "help";
    private static final String OPT_READ_DIR = "read-dir";
    private static final String OPT_WRITE_DIR = "write-dir";
    private static final String OPT_TYPE = "type";
    private static final String OPT_PORT = "port";
    private static final String OPT_ADDR = "listen-address";
    private static final String OPT_IFACE = "listen-interface";
    private static final String OPT_LOG = "log-level";
    private static final Options CMD_OPTIONS = new Options();

    public static void main(String[] args) {
        HelpFormatter formatter = new HelpFormatter();
        CommandLine line = null;
        Path readDir;
        Path writeDir;
        YTFTPServerType type;
        int port;
        InetAddress addr;
        NetworkInterface iface;

        createCmdOptions();

        try {
            CommandLineParser parser = new DefaultParser();
            line = parser.parse(CMD_OPTIONS, args);

            if (line.hasOption(OPT_HELP)) {
                formatter.printHelp("YTFTP Server", CMD_OPTIONS);
                System.exit(0);
            }

            if (line.hasOption(OPT_LOG)) {
                YTFTPLogger.setLevel(YTFTPLogLevel.valueOf(line.getOptionValue(OPT_LOG)));
            }

            readDir = line.hasOption(OPT_READ_DIR) ? Paths.get(line.getOptionValue(OPT_READ_DIR)) : Paths.get("");
            writeDir = line.hasOption(OPT_WRITE_DIR) ? Paths.get(line.getOptionValue(OPT_WRITE_DIR)) : Paths.get("");
            type = line.hasOption(OPT_TYPE) ? YTFTPServerType.valueOf(line.getOptionValue(OPT_TYPE)) : YTFTPServer.DEFAULT_TYPE;
            port = line.hasOption(OPT_PORT) ? Integer.parseInt(line.getOptionValue(OPT_PORT)) : YTFTPServer.DEFAULT_PORT;
            addr = line.hasOption(OPT_ADDR) ? InetAddress.getByName(line.getOptionValue(OPT_ADDR)) : null;
            iface = line.hasOption(OPT_IFACE) ? NetworkInterface.getByName(line.getOptionValue(OPT_IFACE)) : null;

            startServer(readDir, writeDir, type, port, addr, iface);
        } catch (ParseException e) {
            formatter.printHelp("YTFTP Server", CMD_OPTIONS);

            if (line == null || !line.hasOption(OPT_HELP)) {
                throw new RuntimeException("Error parsing options", e);
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException("Unknown address", e);
        } catch (SocketException e) {
            throw new RuntimeException("Unknown interface", e);
        }
    }

    private static void startServer(Path readDir, Path writeDir, YTFTPServerType type, int port, InetAddress addr, NetworkInterface iface) {
        try (
                InputStreamReader in = new InputStreamReader(System.in);
                YTFTPServer srv = addr != null ? new YTFTPServer(readDir, writeDir, type, port, addr) : new YTFTPServer(readDir, writeDir, type, port, iface)
        ) {
            srv.start();
            System.out.println("Enter 'q' to quit");

            int buf = 0;
            while (buf != 'q') {
                buf = in.read();

                if (buf == -1) {
                    throw new RuntimeException("No more character to read");
                } else if (buf == 'q') {
                    System.out.println("Closing sockets...");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading keyboard input", e);
        }
    }

    private static void createCmdOptions() {
        CMD_OPTIONS.addOption(Option.builder("h")
                .longOpt(OPT_HELP)
                .hasArg(false)
                .desc("Show this help")
                .required(false)
                .build()
        );
        CMD_OPTIONS.addOption(Option.builder("r")
                .longOpt(OPT_READ_DIR)
                .argName("READ_DIR")
                .hasArg(true)
                .desc("Directory used to serve files")
                .required(false)
                .build()
        );
        CMD_OPTIONS.addOption(Option.builder("w")
                .longOpt(OPT_WRITE_DIR)
                .argName("WRITE_DIR")
                .hasArg(true)
                .desc("Directory used to save files")
                .required(false)
                .build()
        );
        CMD_OPTIONS.addOption(Option.builder("t")
                .longOpt(OPT_TYPE)
                .argName("SERVER_TYPE")
                .hasArg(true)
                .desc("Server type (" + Arrays.stream(YTFTPServerType.values()).map(YTFTPServerType::name).collect(Collectors.joining(", ")) + ")")
                .required(false)
                .type(YTFTPServerType.class)
                .build()
        );
        CMD_OPTIONS.addOption(Option.builder("p")
                .longOpt(OPT_PORT)
                .argName("PORT")
                .hasArg(true)
                .desc("Port to listen on")
                .required(false)
                .type(Integer.class)
                .build()
        );
        CMD_OPTIONS.addOption(Option.builder("l")
                .longOpt(OPT_ADDR)
                .argName("IP")
                .hasArg(true)
                .desc("IP to listen on")
                .required(false)
                .build()
        );
        CMD_OPTIONS.addOption(Option.builder("i")
                .longOpt(OPT_IFACE)
                .argName("IFACE")
                .hasArg(true)
                .desc("Interface to listen on")
                .required(false)
                .build()
        );
        CMD_OPTIONS.addOption(Option.builder("x")
                .longOpt(OPT_LOG)
                .argName("LOG_LEVEL")
                .hasArg(true)
                .desc("Log level (" + Arrays.stream(YTFTPLogLevel.values()).map(YTFTPLogLevel::name).collect(Collectors.joining(", ")) + ")")
                .required(false)
                .build()
        );
    }
}
