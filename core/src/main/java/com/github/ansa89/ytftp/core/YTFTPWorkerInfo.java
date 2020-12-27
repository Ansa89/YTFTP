package com.github.ansa89.ytftp.core;

/*
 * YTFTPWorkerInfo.java - Class that contains workers parameters.
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.net.tftp.TFTPPacket;

import java.nio.file.Path;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class YTFTPWorkerInfo {
    @NonNull
    private String id;
    @NonNull
    private YTFTPServerType mode;
    @NonNull
    private Integer maxRetries;
    @NonNull
    private Integer socketTimeoutMs;
    @NonNull
    private Path readDirectory;
    @NonNull
    private Path writeDirectory;
    @NonNull
    private TFTPPacket tftpPacket;
}
