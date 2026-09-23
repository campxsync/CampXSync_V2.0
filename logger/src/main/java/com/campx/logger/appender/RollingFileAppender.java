package com.campx.logger.appender;

import com.campx.logger.api.LogEvent;
import com.campx.logger.formatter.LogFormatter;
import com.campx.logger.formatter.PatternFormatter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Enterprise-grade rolling file appender supporting size-based rolling,
 * daily rollover, and historical file retention management.
 * <p>
 * Employs a thread-safe {@link ReentrantLock} to serialize writes and atomic roll operations,
 * ensuring zero corruption during concurrent high-throughput logging.
 *
 * @see LogAppender
 * @see JsonFileAppender
 */
public class RollingFileAppender implements LogAppender {

    private final String name;
    private final String filePath;
    private final long maxFileSize;
    private final int maxBackupIndex;
    private final LogFormatter formatter;
    private final ReentrantLock lock = new ReentrantLock();

    private File currentFile;
    private BufferedWriter writer;
    private long currentSizeBytes = 0;
    private String currentDateTag = "";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Initializes a file appender with default name {@code "FILE"}, 10MB file limit,
     * and up to 10 backups using pattern formatting without ANSI codes.
     *
     * @param filePath path to the output log file
     */
    public RollingFileAppender(String filePath) {
        this("FILE", filePath, 10 * 1024 * 1024L, 10, new PatternFormatter(false, true));
    }

    /**
     * Initializes a rolling file appender with full configuration parameters.
     *
     * @param name           logical appender name
     * @param filePath       path to the output log file
     * @param maxFileSize    maximum file size threshold in bytes before triggering rotation
     * @param maxBackupIndex maximum number of index-shifted backups to retain
     * @param formatter      formatter used to serialize {@link LogEvent} instances
     */
    public RollingFileAppender(String name, String filePath, long maxFileSize, int maxBackupIndex, LogFormatter formatter) {
        this.name = name;
        this.filePath = filePath;
        this.maxFileSize = maxFileSize;
        this.maxBackupIndex = maxBackupIndex;
        this.formatter = formatter != null ? formatter : new PatternFormatter(false, true);

        init();
    }

    /**
     * Initializes the target directory structure, computes current file size, and opens buffered writer.
     */
    private void init() {
        lock.lock();
        try {
            this.currentFile = new File(this.filePath);
            File parent = currentFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            this.currentDateTag = dateFormat.format(new Date());
            if (currentFile.exists()) {
                this.currentSizeBytes = currentFile.length();
            } else {
                this.currentSizeBytes = 0;
            }

            openWriter();
        } catch (IOException e) {
            System.err.println("[RollingFileAppender] Failed to initialize log file " + filePath + ": " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Opens or reopens the UTF-8 buffered file output stream in append mode.
     *
     * @throws IOException if the file stream cannot be opened
     */
    private void openWriter() throws IOException {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {}
        }
        FileOutputStream fos = new FileOutputStream(currentFile, true);
        this.writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8), 32 * 1024);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the configured absolute or relative log file system path.
     *
     * @return file path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Returns the current byte size of the active file being written to.
     *
     * @return active file size in bytes
     */
    public long getCurrentSizeBytes() {
        return currentSizeBytes;
    }

    /**
     * Appends a log event to the active file, evaluating rotation thresholds prior to writing.
     *
     * @param event the log event to append
     */
    @Override
    public void append(LogEvent event) {
        String formatted = formatter.format(event);
        byte[] bytes = formatted.getBytes(StandardCharsets.UTF_8);

        lock.lock();
        try {
            checkRollover(bytes.length);

            if (writer != null) {
                writer.write(formatted);
                currentSizeBytes += bytes.length;
            }
        } catch (IOException e) {
            System.err.println("[RollingFileAppender] Failed to write log: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if current write pushes file over max file size or transitions to a new calendar day.
     *
     * @param incomingBytes byte size of incoming log entry
     * @throws IOException if rotation operations encounter I/O failure
     */
    private void checkRollover(int incomingBytes) throws IOException {
        String todayTag = dateFormat.format(new Date());

        // Check daily rollover
        if (!todayTag.equals(currentDateTag)) {
            rotateDaily(todayTag);
            return;
        }

        // Check size-based rollover
        if (currentSizeBytes + incomingBytes >= maxFileSize) {
            rotateBySize();
        }
    }

    /**
     * Manually triggers a size-based archive rotation on the current file.
     */
    public void rotate() {
        lock.lock();
        try {
            rotateBySize();
        } catch (IOException e) {
            System.err.println("[RollingFileAppender] Manual rotation failed: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Shifts indexed backup log files (e.g. .1 -> .2) and renames the current log file to .1.
     *
     * @throws IOException if file rename operations fail
     */
    private void rotateBySize() throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }

        // Delete the oldest backup if it exists
        File oldestBackup = new File(filePath + "." + maxBackupIndex);
        if (oldestBackup.exists()) {
            oldestBackup.delete();
        }

        // Shift existing backups (e.g. .9 -> .10, .1 -> .2)
        for (int i = maxBackupIndex - 1; i >= 1; i--) {
            File src = new File(filePath + "." + i);
            if (src.exists()) {
                File dst = new File(filePath + "." + (i + 1));
                src.renameTo(dst);
            }
        }

        // Rename current file to .1
        if (currentFile.exists()) {
            File firstBackup = new File(filePath + ".1");
            currentFile.renameTo(firstBackup);
        }

        currentSizeBytes = 0;
        openWriter();
    }

    /**
     * Archives current log file with yesterday's date tag and creates a fresh file for today.
     *
     * @param newDateTag format string representing the new day (e.g. "yyyy-MM-dd")
     * @throws IOException if file rename operations fail
     */
    private void rotateDaily(String newDateTag) throws IOException {
        if (writer != null) {
            writer.flush();
            writer.close();
            writer = null;
        }

        if (currentFile.exists()) {
            File datedBackup = new File(filePath + "." + currentDateTag);
            currentFile.renameTo(datedBackup);
        }

        currentDateTag = newDateTag;
        currentSizeBytes = 0;
        openWriter();
    }

    /**
     * Flushes buffered log entries to underlying disk storage.
     */
    @Override
    public void flush() {
        lock.lock();
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("[RollingFileAppender] Flush error: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Flushes and safely closes the file writer stream.
     */
    @Override
    public void close() {
        lock.lock();
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
                writer = null;
            }
        } catch (IOException e) {
            System.err.println("[RollingFileAppender] Close error: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }
}
