package src;

import java.io.InputStream;
import java.io.IOException;

public class BufferedHttpReader {
  private InputStream inputStream;

  public BufferedHttpReader(InputStream inputStream) {
    this.inputStream = inputStream;
  }

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

  public int read() {
    try {
      return inputStream.read();
    } catch (IOException e) {
      return -1;
    }
  }
}
