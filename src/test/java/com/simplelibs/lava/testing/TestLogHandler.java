package com.simplelibs.lava.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Minimal replacement for guava-testlib's {@code TestLogHandler}: a {@link Handler} that stores
 * the {@link LogRecord}s it is given, so tests can inspect what a class logged.
 */
public class TestLogHandler extends Handler {
  private final List<LogRecord> list = new ArrayList<>();

  @Override
  public synchronized void publish(LogRecord record) {
    list.add(record);
  }

  @Override
  public void flush() {}

  @Override
  public void close() {}

  /** Returns a snapshot of the records published so far, oldest first. */
  public synchronized List<LogRecord> getStoredLogRecords() {
    return Collections.unmodifiableList(new ArrayList<>(list));
  }

  /** Returns the most recent record, or null if there is none. */
  public synchronized LogRecord getLastRecord() {
    return list.isEmpty() ? null : list.get(list.size() - 1);
  }

  /** Forgets the records published so far. */
  public synchronized void clear() {
    list.clear();
  }
}
