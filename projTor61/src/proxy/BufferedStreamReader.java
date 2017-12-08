package proxy;

import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** BufferedStreamReader wraps an InputStream for the following benefits:
    - Capable of switching between line-based and byte-based processing.
    - Doesn't trim line termination characters when doing line-based processing.
    - Returns null or -1 when there's an error reading instead of throwing IOException. */
public class BufferedStreamReader {
  /// The InputStream to read from.
  private InputStream inputStream;

  private BlockingQueue<byte[]> bufStream;
  byte[] leftover;

  /** Sole constructor.
      @param inputStream The InputStream to read from. */
  public BufferedStreamReader(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  public BufferedStreamReader(BlockingQueue<byte[]> bufStream) {
    this.bufStream = bufStream;
  }

  /** Reads and returns the next line from the InputStream (terminated by '\n').
      @return The line read, or null if there is no line or an error. */
  public String readLine() {
    StringBuilder lineBuilder = new StringBuilder();
    String line;
    if (bufStream != null) {
      if (leftover != null) {
        for (int i = 0; i < leftover.length; i++) {
          char current = (char) leftover[i];
          lineBuilder.append(current);
          if (current == (int) '\n') {
            byte[] newLeftover = new byte[leftover.length - i];
            for (int j = i + 1; j < leftover.length; j++) {
              newLeftover[j - i - 1] = leftover[j];
            }
            leftover = newLeftover;
            return lineBuilder.toString();
          }
        }
        leftover = null;
      }
      while (true) {
        byte[] buf;
        try {
          buf = bufStream.poll(25000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          return null;
        }
        if (buf == null || buf[2] != 3 || buf[13] != 2) {
          if (lineBuilder.length() == 0) {
            return null;
          } else {
            return lineBuilder.toString();
          }
        }
        final int length = ((buf[11] & 0xFF) << 8) | (buf[12] & 0xFF);
        for (int i = 14; i < 14 + length; i++) {
          char current = (char) buf[i];
          lineBuilder.append(current);
          if (current == (int) '\n') {
            if (i == 13 + length) {
              leftover = null;
              return lineBuilder.toString();
            }
            byte[] newLeftover = new byte[length - (i - 14)];
            for (int j = i + 1; j < 14 + length; j++) {
              newLeftover[j - i - 1] = buf[j];
            }
            leftover = newLeftover;
            return lineBuilder.toString();
          }
        }
      }
    }

    int current;
    try {
      while ((current = inputStream.read()) != -1) {
        lineBuilder.append((char) current);
        if (current == (int) '\n') {
          return lineBuilder.toString();
        }
      }
    } catch (IOException e) {
      // no op
    }

    if (lineBuilder.length() == 0) {
      return null;
    } else {
      return lineBuilder.toString();
    }
  }

  /** Reads and returns the next byte from the InputStream.
      @return The byte read, or -1 if there is no byte or an error. */
  public int read() {
    if (bufStream != null) {
      if (leftover != null) { // TODO: remove this loop to speed up
        if (leftover.length == 1) {
          int result = leftover[0];
          leftover = null;
          return result;
        } else {
          byte[] newLeftover = new byte[leftover.length - 1];
          for (int i = 0; i < newLeftover.length; i++) {
            newLeftover[i] = leftover[i + 1];
          }
          int result = leftover[0];
          leftover = newLeftover;
          return result;
        }
      } else {
        byte[] buf;
        try {
          buf = bufStream.poll(25000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          return -1;
        }
        if (buf == null || buf[2] != 3 || buf[13] != 2) {
          return -1;
        }
        final int length = ((buf[11] & 0xFF) << 8) | (buf[12] & 0xFF);
        if (length != 1) {
          byte[] newLeftover = new byte[length - 1];
          for (int i = 0; i < newLeftover.length; i++) {
            newLeftover[i] = buf[i + 15];
          }
          leftover = newLeftover;
        }
        return buf[14];
      }
    }
    try {
      return inputStream.read();
    } catch (IOException e) {
      return -1;
    }
  }
}
