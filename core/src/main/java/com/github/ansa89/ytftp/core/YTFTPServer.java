package com.github.ansa89.ytftp.core;

/*
 * YTFTPServer.java - Class that contains configurations and allows to start the main server thread, which will create
 *                    workers and dispatch requests to them.
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


import com.github.ansa89.ytftp.core.enums.YTFTPServerType;
import com.github.ansa89.ytftp.core.error.YTFTPError;
import com.github.ansa89.ytftp.core.log.YTFTPLogger;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPPacket;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YTFTPServer implements Runnable, AutoCloseable {
    public static final YTFTPServerType DEFAULT_TYPE = YTFTPServerType.GET_ONLY;
    public static final int DEFAULT_PORT = 69;

    private static final Map<String, YTFTPWorker> workers = new HashMap<>();
    private volatile boolean running = false;
    private Throwable runningException;
    private TFTP master;
    private Thread masterThread;

    @NonNull
    private final Path readDirectory;
    @NonNull
    private final Path writeDirectory;
    @NonNull
    private final YTFTPServerType type;
    @NonNull
    private final Integer port;
    private InetAddress inetAddress;
    @NonNull
    @Getter
    private Integer maxRetries = 3;
    @NonNull
    @Getter
    private Integer socketTimeoutMs = TFTP.DEFAULT_TIMEOUT;


    /**
     * Start a TFTP server.
     *
     * @param serverReadDirectory  directory for GET requests
     * @param serverWriteDirectory directory for PUT requests
     * @param type                 server type
     * @param port                 local port to bind to
     * @param inetAddress          local address to bind to
     */
    public YTFTPServer(Path serverReadDirectory, Path serverWriteDirectory, YTFTPServerType type, int port, InetAddress inetAddress) {
        this.readDirectory = serverReadDirectory;
        this.writeDirectory = serverWriteDirectory;
        this.type = type;
        this.port = port;
        this.inetAddress = inetAddress;
    }

    /**
     * Create a TFTP server.
     *
     * @param serverReadDirectory  directory for GET requests
     * @param serverWriteDirectory directory for PUT requests
     * @param type                 server type
     * @param port                 local port to bind to
     * @param iface                local network interface to bind to (interface's first address will be used)
     */
    public YTFTPServer(Path serverReadDirectory, Path serverWriteDirectory, YTFTPServerType type, int port, NetworkInterface iface) {
        this.readDirectory = serverReadDirectory;
        this.writeDirectory = serverWriteDirectory;
        this.type = type;
        this.port = port;

        if (iface != null) {
            for (InterfaceAddress interfaceAddress : iface.getInterfaceAddresses()) {
                inetAddress = interfaceAddress.getAddress();
                break;
            }
        }
    }

    /**
     * Create a TFTP server.
     *
     * @param serverReadDirectory  directory for GET requests
     * @param serverWriteDirectory directory for PUT requests
     * @param type                 server type
     * @param port                 local port to bind to
     */
    public YTFTPServer(Path serverReadDirectory, Path serverWriteDirectory, YTFTPServerType type, int port) {
        this(serverReadDirectory, serverWriteDirectory, type, port, (InetAddress) null);
    }

    /**
     * Create a TFTP server on default port.
     *
     * @param serverReadDirectory  directory for GET requests
     * @param serverWriteDirectory directory for PUT requests
     * @param type                 server type
     */
    public YTFTPServer(Path serverReadDirectory, Path serverWriteDirectory, YTFTPServerType type) {
        this(serverReadDirectory, serverWriteDirectory, type, DEFAULT_PORT, (InetAddress) null);
    }

    /**
     * Create a TFTP server with default type, on default port.
     *
     * @param serverReadDirectory  directory for GET requests
     * @param serverWriteDirectory directory for PUT requests
     */
    public YTFTPServer(Path serverReadDirectory, Path serverWriteDirectory) {
        this(serverReadDirectory, serverWriteDirectory, DEFAULT_TYPE, DEFAULT_PORT, (InetAddress) null);
    }

    /**
     * Set the maximum number of retries when a timeout occur.
     * Default 3.
     *
     * @param maxRetries number of retries, must be greater or equal than 0
     * @throws YTFTPError if an invalid values is specified
     */
    public void setMaxRetries(@NonNull Integer maxRetries) {
        if (maxRetries < 0) {
            throw new YTFTPError("Specify a retry value greater or equal than 0");
        }

        this.maxRetries = maxRetries;
    }

    /**
     * Set the socket timeout in milliseconds used in transfers.
     * Default TFTP.DEFAULT_TIMEOUT.
     *
     * @param socketTimeoutMs timeout in milliseconds, must be greater or equal than 10
     * @throws YTFTPError if an invalid values is specified
     */
    public void setSocketTimeoutMs(@NonNull Integer socketTimeoutMs) {
        if (socketTimeoutMs < 10) {
            throw new YTFTPError("Specify a timeout value greater or equal than 10ms");
        }

        this.socketTimeoutMs = socketTimeoutMs;
    }

    /**
     * Check if the server thread is still running.
     *
     * @return true if running, false otherwise
     * @throws YTFTPError if the server has been stopped by an exception
     */
    public boolean isRunning() {
        if (!running && runningException != null) {
            throw new YTFTPError("TFTP server stopped unexpectedly", runningException);
        }

        return running;
    }

    /**
     * Start the TFTP server.
     *
     * @throws YTFTPError if any error occurs during start
     */
    public void start() {
        if (type == YTFTPServerType.GET_AND_PUT || type == YTFTPServerType.GET_ONLY) {
            if (!Files.exists(readDirectory) || !Files.isDirectory(readDirectory)) {
                throw new YTFTPError("Read directory " + readDirectory.toString() + " does not exist");
            }

            if (!Files.isReadable(readDirectory)) {
                throw new YTFTPError("Cannot read directory " + readDirectory.toString());
            }
        }

        if (type == YTFTPServerType.GET_AND_PUT || type == YTFTPServerType.PUT_ONLY) {
            if (!Files.exists(writeDirectory) || !Files.isDirectory(writeDirectory)) {
                throw new YTFTPError("Write directory " + writeDirectory.toString() + " does not exist");
            }

            if (!Files.isWritable(writeDirectory)) {
                throw new YTFTPError("Cannot write to directory " + writeDirectory.toString());
            }
        }

        String msg = "Starting TFTP server:\n" +
                "\t- type: " + type.name() + "\n" +
                "\t- address: " + (inetAddress != null ? inetAddress.getHostAddress() : "0.0.0.0") + "\n" +
                "\t- port: " + port + "\n" +
                "\t- read dir: " + readDirectory.toString() + "\n" +
                "\t- write dir: " + writeDirectory.toString();
        YTFTPLogger.info(msg);

        master = new TFTP();
        socketTimeoutMs = master.getDefaultTimeout();
        master.setDefaultTimeout(0);

        try {
            if (inetAddress != null) {
                master.open(port, inetAddress);
            } else {
                master.open(port);
            }
        } catch (SocketException e) {
            throw new YTFTPError("Error starting TFTP server", e);
        }

        masterThread = new Thread(this, "YTFTP-Master");
        masterThread.setDaemon(true);
        masterThread.start();
    }

    /**
     * Stop the TFTP server.
     */
    public synchronized void shutdown() {
        running = false;

        if (master != null) {
            master.close();
        }

        synchronized (workers) {
            for (Map.Entry<String, YTFTPWorker> workerEntry : workers.entrySet()) {
                workerEntry.getValue().close();
            }

            workers.clear();
        }

        if (masterThread != null) {
            try {
                masterThread.join(10000);
            } catch (InterruptedException e) {
                throw new YTFTPError("Error while trying to join master thread", e);
            }
        }
    }

    @Override
    public void run() {
        running = true;

        try {
            while (running) {
                int workerNum;
                TFTPPacket tftpPacket = master.receive();
                String workerId = UUID.randomUUID().toString();
                YTFTPWorker worker = new YTFTPWorker(new YTFTPWorkerInfo(workerId, type, maxRetries, socketTimeoutMs, readDirectory, writeDirectory, tftpPacket));

                synchronized (workers) {
                    workers.put(workerId, worker);
                    workerNum = workers.size();
                }

                Thread thread = new Thread(worker, "YTFTP-Wrk" + workerNum);
                thread.setDaemon(true);
                thread.start();
            }
        } catch (Throwable t) {
            if (running) {
                runningException = t;
                throw new YTFTPError("Aborting TFTP server due to unexpected error", t);
            }
        } finally {
            shutdown();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    protected static synchronized void removeWorker(String id) {
        workers.remove(id);
    }
}
