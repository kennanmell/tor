package proxy;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
  List<Integer> leftover;

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
        for (int i = 0; i < leftover.size(); i++) {
          char current = (char) leftover.get(i).byteValue();
          lineBuilder.append(current);
          if (current == '\n') {
            if (i == leftover.size() - 1) {
              leftover = null;
              return lineBuilder.toString();
            }
            leftover = new ArrayList<>(leftover.subList(i + 1, leftover.size()));
            return lineBuilder.toString();
          }
        }
        leftover = null;
      }
      while (true) {
        byte[] buf;
        try {
          buf = bufStream.poll(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          if (lineBuilder.length() == 0) {
            return null;
          } else {
            return lineBuilder.toString();
          }
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
          if (current == '\n') {
            if (i == 13 + length) {
              leftover = null;
              return lineBuilder.toString();
            }
            leftover = new ArrayList<>();
            for (int j = 0; j < length - (i - 14) - 1; j++) {
              leftover.add(buf[j + i + 1] & 0xFF);
            }
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

  public int readChunk(byte[] cell) {
    if (bufStream != null) {
      throw new IllegalStateException("not yet implemented");
    }
    try {
      return inputStream.read(cell);
    } catch (IOException e) {
      return 0;
    }
  }

  /** Reads and returns the next byte from the InputStream.
      @return The byte read, or -1 if there is no byte or an error. */
  public int read() {
    if (bufStream != null) {
      if (leftover != null) {
        if (leftover.size() == 1) {
          int result = leftover.get(0);
          leftover = null;
          return result;
        } else {
          return leftover.remove(0);
        }
      } else {
        byte[] buf;
        try {
          buf = bufStream.poll(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          return -1;
        }
        if (buf == null || buf[2] != 3 || buf[13] != 2) {
          return -1;
        }
        final int length = ((buf[11] & 0xFF) << 8) | (buf[12] & 0xFF);
        if (length > 1) {
          leftover = new ArrayList<>();
          for (int i = 0; i < length - 1; i++) {
            leftover.add(buf[i + 15] & 0xFF);
          }
        }
        if (length == 0) {
          return -1;
        } else {
          return buf[14] & 0xFF;
        }
      }
    }
    try {
      return inputStream.read();
    } catch (IOException e) {
      return -1;
    }
  }
}
