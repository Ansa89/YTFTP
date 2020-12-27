package com.github.ansa89.ytftp.core;

/*
 * YTFTPWorker.java - Class that contains configurations and allows to start a worker thread, which will
 *                    manage a request.
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
import lombok.NonNull;
import org.apache.commons.net.io.FromNetASCIIOutputStream;
import org.apache.commons.net.io.ToNetASCIIInputStream;
import org.apache.commons.net.tftp.*;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class YTFTPWorker implements Runnable, AutoCloseable {
    private boolean shutdownTransfer = false;
    @NonNull
    private final String id;
    @NonNull
    private final YTFTPServerType mode;
    @NonNull
    private final Integer maxRetries;
    @NonNull
    private final Integer socketTimeoutMs;
    @NonNull
    private final Path readDirectory;
    @NonNull
    private final Path writeDirectory;
    @NonNull
    private final TFTPPacket tftpPacket;
    private TFTP worker;

    protected YTFTPWorker(YTFTPWorkerInfo workerInfo) {
        this.id = workerInfo.getId();
        this.mode = workerInfo.getMode();
        this.maxRetries = workerInfo.getMaxRetries();
        this.socketTimeoutMs = workerInfo.getSocketTimeoutMs();
        this.readDirectory = workerInfo.getReadDirectory();
        this.writeDirectory = workerInfo.getWriteDirectory();
        this.tftpPacket = workerInfo.getTftpPacket();
    }

    /**
     * Stop the TFTP worker.
     */
    public synchronized void shutdown() {
        shutdownTransfer = true;

        if (worker != null) {
            worker.endBufferedOps();
            worker.close();
        }

        YTFTPServer.removeWorker(id);
    }

    @Override
    public void run() {
        try {
            worker = new TFTP();
            worker.beginBufferedOps();
            worker.setDefaultTimeout(socketTimeoutMs);
            worker.open();

            if (tftpPacket instanceof TFTPReadRequestPacket) {
                TFTPReadRequestPacket readPkt = (TFTPReadRequestPacket) tftpPacket;
                YTFTPLogger.info("New  GET request: " + readPkt.getAddress() + ":" + readPkt.getPort() + " - " + readPkt.getFilename());
                handleRead(readPkt);
                YTFTPLogger.info("Done GET request: " + readPkt.getAddress() + ":" + readPkt.getPort() + " - " + readPkt.getFilename());
            } else if (tftpPacket instanceof TFTPWriteRequestPacket) {
                TFTPWriteRequestPacket writePkt = (TFTPWriteRequestPacket) tftpPacket;
                YTFTPLogger.info("New  PUT request: " + writePkt.getAddress() + ":" + writePkt.getPort() + " - " + writePkt.getFilename());
                handleWrite(writePkt);
                YTFTPLogger.info("Done PUT request: " + writePkt.getAddress() + ":" + writePkt.getPort() + " - " + writePkt.getFilename());
            } else {
                YTFTPLogger.warn("Ignored unsupported TFTP request (" + tftpPacket.toString() + ")");
            }
        } catch (IOException e) {
            if (!shutdownTransfer) {
                YTFTPLogger.error("Error during TFTP transfer", e);
            }
        } finally {
            shutdown();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    private void handleRead(final TFTPReadRequestPacket trrp) throws IOException {
        if (mode == YTFTPServerType.PUT_ONLY) {
            worker.bufferedSend(new TFTPErrorPacket(trrp.getAddress(), trrp.getPort(), TFTPErrorPacket.ILLEGAL_OPERATION, "Read not allowed by server"));
            return;
        }

        try (InputStream in = trrp.getMode() == TFTP.NETASCII_MODE ? new ToNetASCIIInputStream(getBufferedInputStream(trrp.getFilename())) : getBufferedInputStream(trrp.getFilename())) {
            final byte[] temp = new byte[TFTPDataPacket.MAX_DATA_LENGTH];
            boolean sendNext = true;
            int block = 1;
            int readLength = TFTPDataPacket.MAX_DATA_LENGTH;
            TFTPDataPacket lastSentData = null;

            // send the requested file
            while (!shutdownTransfer && readLength == TFTPDataPacket.MAX_DATA_LENGTH) {
                int timeoutCount = 0;
                TFTPPacket answer = null;

                if (sendNext) {
                    readLength = in.read(temp);

                    if (readLength == -1) {
                        readLength = 0;
                    }

                    lastSentData = new TFTPDataPacket(trrp.getAddress(), trrp.getPort(), block, temp, 0, readLength);
                    worker.bufferedSend(lastSentData);
                }

                // listen for client answer
                while (!shutdownTransfer && (answer == null || !answer.getAddress().equals(trrp.getAddress()) || answer.getPort() != trrp.getPort())) {
                    if (answer != null) {
                        // answer came from unexpected client
                        YTFTPLogger.warn("Ignoring TFTP message from unexpected client (" + answer.getAddress().getHostAddress() + ":" + answer.getPort() + ")");
                        worker.bufferedSend(new TFTPErrorPacket(answer.getAddress(), answer.getPort(), TFTPErrorPacket.UNKNOWN_TID, "Unexpected host or port"));
                    }

                    try {
                        answer = worker.bufferedReceive();
                    } catch (SocketTimeoutException e) {
                        if (timeoutCount >= maxRetries) {
                            throw new YTFTPError("Too many retries waiting answer from TFTP client", e);
                        }

                        // try to resend last sent data
                        worker.bufferedSend(lastSentData);
                        timeoutCount++;
                    } catch (IOException | TFTPPacketException e) {
                        throw new YTFTPError("Error waiting answer from TFTP client", e);
                    }
                }

                if (!(answer instanceof TFTPAckPacket)) {
                    if (!shutdownTransfer) {
                        throw new YTFTPError("Unexpected response from TFTP client during transfer (" + answer.toString() + ")");
                    }

                    break;
                }

                // at this point, answer is an ack packet
                TFTPAckPacket ack = (TFTPAckPacket) answer;

                if (ack.getBlockNumber() != block) {
                    sendNext = false;
                } else {
                    block++;

                    if (block > 65535) {
                        // wrap the block number
                        block = 0;
                    }

                    sendNext = true;
                }
            }
        } catch (FileNotFoundException e) {
            worker.bufferedSend(new TFTPErrorPacket(trrp.getAddress(), trrp.getPort(), TFTPErrorPacket.FILE_NOT_FOUND, e.getMessage()));
        }
    }

    private void handleWrite(final TFTPWriteRequestPacket twrp) throws IOException {
        if (mode == YTFTPServerType.GET_ONLY) {
            worker.bufferedSend(new TFTPErrorPacket(twrp.getAddress(), twrp.getPort(), TFTPErrorPacket.ILLEGAL_OPERATION, "Write not allowed by server."));
            return;
        }

        Path temp = buildSafePath(writeDirectory, twrp.getFilename(), true);
        if (Files.exists(temp)) {
            worker.bufferedSend(new TFTPErrorPacket(twrp.getAddress(), twrp.getPort(), TFTPErrorPacket.FILE_EXISTS, "File already exists"));
            return;
        }

        int lastBlock = 0;
        try (OutputStream bos = twrp.getMode() == TFTP.NETASCII_MODE ? new FromNetASCIIOutputStream(getBufferedOutputStream(temp)) : getBufferedOutputStream(temp)) {
            TFTPAckPacket lastSentAck = new TFTPAckPacket(twrp.getAddress(), twrp.getPort(), 0);
            worker.bufferedSend(lastSentAck);

            // receive the file
            while (true) {
                TFTPPacket dataPacket = null;
                int timeoutCount = 0;

                // listen for client answer
                while (!shutdownTransfer && (dataPacket == null || !dataPacket.getAddress().equals(twrp.getAddress()) || dataPacket.getPort() != twrp.getPort())) {
                    if (dataPacket != null) {
                        // answer came from unexpected client
                        YTFTPLogger.warn("Ignoring TFTP message from unexpected client (" + dataPacket.getAddress().getHostAddress() + ":" + dataPacket.getPort() + ")");
                        worker.bufferedSend(new TFTPErrorPacket(dataPacket.getAddress(), dataPacket.getPort(), TFTPErrorPacket.UNKNOWN_TID, "Unexpected host or port"));
                    }

                    try {
                        dataPacket = worker.bufferedReceive();
                    } catch (SocketTimeoutException e) {
                        if (timeoutCount >= maxRetries) {
                            throw new YTFTPError("Too many retries waiting data from TFTP client", e);
                        }

                        // try to resend last sent ack
                        worker.bufferedSend(lastSentAck);
                        timeoutCount++;
                    } catch (IOException | TFTPPacketException e) {
                        throw new YTFTPError("Error waiting data from TFTP client", e);
                    }
                }

                // client missed initial ack? try to send new one
                if (dataPacket instanceof TFTPWriteRequestPacket) {
                    lastSentAck = new TFTPAckPacket(twrp.getAddress(), twrp.getPort(), 0);
                    worker.bufferedSend(lastSentAck);
                } else if (!(dataPacket instanceof TFTPDataPacket)) {
                    if (!shutdownTransfer) {
                        throw new YTFTPError("Unexpected response from TFTP client during transfer (" + dataPacket.toString() + ")");
                    }

                    break;
                } else {
                    final int block = ((TFTPDataPacket) dataPacket).getBlockNumber();
                    final byte[] data = ((TFTPDataPacket) dataPacket).getData();
                    final int dataLength = ((TFTPDataPacket) dataPacket).getDataLength();
                    final int dataOffset = ((TFTPDataPacket) dataPacket).getDataOffset();

                    // write only if new block is received
                    if (block > lastBlock || (lastBlock == 65535 && block == 0)) {
                        bos.write(data, dataOffset, dataLength);
                        lastBlock = block;
                    }

                    lastSentAck = new TFTPAckPacket(twrp.getAddress(), twrp.getPort(), block);
                    worker.bufferedSend(lastSentAck);

                    if (dataLength < TFTPDataPacket.MAX_DATA_LENGTH) {
                        // check if client missed last ack (and resend last one)
                        for (int i = 0; i < maxRetries; i++) {
                            try {
                                dataPacket = worker.bufferedReceive();
                            } catch (final SocketTimeoutException e) {
                                // all good
                                break;
                            } catch (IOException | TFTPPacketException e) {
                                throw new YTFTPError("Error waiting data from TFTP client", e);
                            }

                            if (!dataPacket.getAddress().equals(twrp.getAddress()) || dataPacket.getPort() != twrp.getPort()) {
                                // answer came from unexpected client
                                worker.bufferedSend(new TFTPErrorPacket(dataPacket.getAddress(), dataPacket.getPort(), TFTPErrorPacket.UNKNOWN_TID, "Unexpected host or port"));
                            } else {
                                // resend last sent ack
                                worker.bufferedSend(lastSentAck);
                            }
                        }

                        break;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            worker.bufferedSend(new TFTPErrorPacket(twrp.getAddress(), twrp.getPort(), TFTPErrorPacket.FILE_NOT_FOUND, e.getMessage()));
        }
    }

    private InputStream getBufferedInputStream(String filename) throws IOException {
        return new BufferedInputStream(new FileInputStream(buildSafePath(readDirectory, filename, false).toFile()));
    }

    private OutputStream getBufferedOutputStream(Path path) throws IOException {
        if (!isSubdirectory(writeDirectory, path)) {
            throw new YTFTPError("Destination path is outside server directory");
        }

        return new BufferedOutputStream(new FileOutputStream(path.toFile()));
    }

    // return the path of fileName if it is inside serverDirectory (eventually creating subdirectories), otherwise
    // throw an error
    private Path buildSafePath(Path serverDirectory, String fileName, boolean createSubDirs) {
        Path temp = serverDirectory.resolve(Paths.get(fileName));

        if (!isSubdirectory(serverDirectory, temp)) {
            throw new YTFTPError("Destination path is outside server directory");
        }

        if (createSubDirs) {
            try {
                Files.createDirectories(temp.getParent());
            } catch (IOException e) {
                throw new YTFTPError("Error creating destination directory " + temp.getParent().toString(), e);
            }
        }

        return temp.normalize();
    }

    // check if child is inside parent
    private boolean isSubdirectory(Path parent, Path child) {
        return child.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize());
    }
}
