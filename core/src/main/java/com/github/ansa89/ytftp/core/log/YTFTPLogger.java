package com.github.ansa89.ytftp.core.log;

/*
 * YTFTPLogger.java - Class that manage log printing.
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


import com.github.ansa89.ytftp.core.enums.YTFTPLogLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class YTFTPLogger {
    public static final YTFTPLogLevel DEFAULT_LEVEL = YTFTPLogLevel.WARNING;

    @NonNull
    @Getter
    @Setter
    private static YTFTPLogLevel level = DEFAULT_LEVEL;

    private YTFTPLogger() {
        super();
    }

    public static void debug(String msg) {
        if (canPrintLog(YTFTPLogLevel.DEBUG)) {
            log.debug(msg);
        }
    }

    public static void debug(String msg, Throwable t) {
        if (canPrintLog(YTFTPLogLevel.DEBUG)) {
            log.debug(msg, t);
        }
    }

    public static void info(String msg) {
        if (canPrintLog(YTFTPLogLevel.INFO)) {
            log.info(msg);
        }
    }

    public static void info(String msg, Throwable t) {
        if (canPrintLog(YTFTPLogLevel.INFO)) {
            log.info(msg, t);
        }
    }

    public static void warn(String msg) {
        if (canPrintLog(YTFTPLogLevel.WARNING)) {
            log.warn(msg);
        }
    }

    public static void warn(String msg, Throwable t) {
        if (canPrintLog(YTFTPLogLevel.WARNING)) {
            log.warn(msg, t);
        }
    }

    public static void error(String msg) {
        if (canPrintLog(YTFTPLogLevel.ERROR)) {
            log.error(msg);
        }
    }

    public static void error(String msg, Throwable t) {
        if (canPrintLog(YTFTPLogLevel.ERROR)) {
            log.error(msg, t);
        }
    }

    private static boolean canPrintLog(@NonNull YTFTPLogLevel level) {
        return level.compareTo(YTFTPLogger.level) >= 0;
    }
}
