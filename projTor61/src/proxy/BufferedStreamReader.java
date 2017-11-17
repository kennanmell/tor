package proxy;

import java.io.InputStream;
import java.io.IOException;

/** BufferedStreamReader wraps an InputStream for the following benefits:
    - Capable of switching between line-based and byte-based processing.
    - Doesn't trim line termination characters when doing line-based processing.
    - Returns null or -1 when there's an error reading instead of throwing IOException. */
public class BufferedStreamReader {
  /// The InputStream to read from.
  private InputStream inputStream;

  /** Sole constructor.
      @param inputStream The InputStream to read from. */
  public BufferedStreamReader(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  /** Reads and returns the next line from the InputStream (terminated by '\n').
      @return The line read, or null if there is no line or an error. */
  public String readLine() {
    StringBuilder lineBuilder = new StringBuilder();
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
    try {
      return inputStream.read();
    } catch (IOException e) {
      return -1;
    }
  }
}
