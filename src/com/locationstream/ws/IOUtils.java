
package com.locationstream.ws;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

//import android.util.Base64;   // API_LEVEL 8

/**
 * @author pcockerell
 * This class implements a subset of the methods of Apache Commons IOUtils without requiring the whole class.
 */
public class IOUtils {

    public static final int CONNECTION_TIMEOUT = 30 * 1000;
    public static final int SOCKET_TIMEOUT = 180 * 1000;
    public static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

    /**
     * Copies the contents of the given input stream to the writer, using the
     * given encoding for the input stream.
     *
     * @param is The InputStream to use for reading.
     * @param w The Writer to which to write the read characters.
     * @param encoding The encoding used to interpret the input stream.
     * @throws IOException If a problem occurs with one of the streams, or the encoding is invalid.
     */
    public static void copy(InputStream is, PrintWriter w, String encoding) throws IOException {
        copy(is, w, encoding, -1);
    }

    /**
     * Copies the contents of the given input stream to the writer, using the
     * given encoding for the input stream.
     *
     * @param is The InputStream to use for reading.
     * @param w The Writer to which to write the read characters.
     * @param maxLen The maximum number of characters to copy. -1 means the whole stream.
     * @param encoding The encoding used to interpret the input stream.
     * @throws IOException If a problem occurs with one of the streams, or the encoding is invalid.
     */
    public static void copy(InputStream is, PrintWriter w, String encoding, int maxLen) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(is, encoding), DEFAULT_BUFFER_SIZE);
        String line;
        // The remaining number of characters to copy.
        int toCopy = maxLen == -1 ? Integer.MAX_VALUE : maxLen;
        int newlineLen = System.getProperty("line.separator", "\n").length(); //$NON-NLS-1$ //$NON-NLS-2$
        while (toCopy > 0 && (line = r.readLine()) != null) {
            // If we're limiting the number of characters...
            if (maxLen >= 0) {
                // Account for the (potential) trailing newline
                int writeCount = line.length() + newlineLen;
                // If that would take over the limit
                if (writeCount > toCopy) {
                    // Just output as any characters as we have left
                    w.print(line.substring(0, toCopy));
                    break;
                }
                // Decrement by the number of chars new println() below will write.
                toCopy -= writeCount;
            }
            Thread.yield();
            w.println(line);
        }
        w.flush();
    }

    /**
     * Closes the given InputStream without throwing any exceptions.
     * @param is The InputStream to close.
     */
    public static void closeQuietly(InputStream is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            // Ignore exception
        }
    }

    /**
     * Closes the given OutputStream without throwing any exceptions.
     * @param os The OutputStream to close.
     */
    public static void closeQuietly(OutputStream os) {
        try {
            if (os != null) {
                os.close();
            }
        } catch (IOException e) {
            // Ignore exception
        }
    }

    /**
     * Closes the given Reader without throwing any exceptions.
     * @param r The Reader to close.
     */
    public static void closeQuietly(Reader r) {
        try {
            if (r != null) {
                r.close();
            }
        } catch (IOException e) {
            // Ignore exception
        }
    }

    /**
     * Closes the given Writer without throwing any exceptions.
     * @param w The Writer to close.
     */
    public static void closeQuietly(Writer w) {
        try {
            if (w != null) {
                w.close();
            }
        } catch (IOException e) {
            // Ignore exception
        }
    }

    /**
     * Reads the contents of the given stream into a byte[] and returns it.
     * @param is The stream to read.
     * @return The byte array containing the contents of the stream.
     * @throws IOException
     */
    public static byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte [] buff = new byte[4096];
        int len;
        while ((len = is.read(buff)) > 0) {
            os.write(buff, 0, len);
        }
        return os.toByteArray();
    }

    /**
     * Get the contents of an <code>InputStream</code> as a String
     * using the specified character encoding.
     * @param input  the <code>InputStream</code> to read from
     * @param encoding  the encoding to use, null means platform default
     * @return the requested String
     * @throws NullPointerException if the input is null
     * @throws IOException if an I/O error occurs
     */
    public static String toString(InputStream input, String encoding)
    throws IOException {
        return toString(input, encoding, -1);
    }

    /**
     * Get the contents of an <code>InputStream</code> as a String
     * using the specified character encoding.
     * @param input  the <code>InputStream</code> to read from
     * @param encoding  the encoding to use, null means platform default
     * @param maxLen The maximum length of the string to return. -1 means the whole string.
     * @return the requested String
     * @throws NullPointerException if the input is null
     * @throws IOException if an I/O error occurs
     */
    public static String toString(InputStream input, String encoding, int maxLen)
    throws IOException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        copy(input, pw, encoding, maxLen);
        pw.close();
        return sw.toString();
    }

    /**
     * Copy bytes from an <code>InputStream</code> to an
     * <code>OutputStream</code>.
     * @param input  the <code>InputStream</code> to read from
     * @param output  the <code>OutputStream</code> to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException if an I/O error occurs
     */
    public static int copy(InputStream input, OutputStream output) throws IOException {
        int count = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read = 0;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            count += read;
        }
        return count;
    }

    private static final char MOD_UTF7_FIRST         = 0x20;
    private static final char MOD_UTF7_LAST          = 0x7e;
    private static final char MOD_UTF7_START_SHIFT_CHAR    = '&';
    private static final char MOD_UTF7_END_SHIFT_CHAR = '-';
    private static final char BASE64_PAD             = '=';

    /**
     * Returns a byte array representing a "Modified UTF-7" encoding of the
     * string passed as an argument. See section 5.1.3 of RFC 3501 for a description
     * of the modifications, and RFC 2152 for a description of UTF-7.
     * @param str The Java String to be encoded.
     * @return The array of US-ASCII 7-bit bytes encoding the string.
     */
    public static byte[] modifiedUTF7Encode(String str) {

        int len = str.length();
        // We build it using a StringBuilder to make it easy to append,
        // the convert to a byte array.
        StringBuilder sb = new StringBuilder(len*2);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);

            if (c >= MOD_UTF7_FIRST && c <= MOD_UTF7_LAST) {
                // Any printable ASCII character (and space) is represented as itself, except for the shift char &...
                sb.append(c);
                // ...which is represented as &-
                if (c == MOD_UTF7_START_SHIFT_CHAR) {
                    sb.append(MOD_UTF7_END_SHIFT_CHAR);
                }
            } else {
                // Otherwise BASE64 encode the Unicode two-byte values
                int start = i;
                do {
                    i++;
                } while (i < len && ((c = str.charAt(i)) < MOD_UTF7_FIRST || c > MOD_UTF7_LAST));
                int sublen = i - start;
                // Store the Unicode as an array of high-byte, low-bytes
                byte[] utf16Bytes = new byte[2*sublen];
                int byteIndex = 0;
                for (int j = start; j < i; j++) {
                    c = str.charAt(j);
                    utf16Bytes[byteIndex++] = (byte)(c >>> 8);
                    utf16Bytes[byteIndex++] = (byte)(c & 0x0ff);
                }
                // Use the standard library to encode them in BASE64  
                byte[] encodedBytes = "0".getBytes();  //Base64.encode(utf16Bytes, 0);
                // Append the start shift then the encoded US-ASCII chars, then the end shift
                sb.append(MOD_UTF7_START_SHIFT_CHAR);
                int encodedLen = encodedBytes.length;
                for (int j = 0; j < encodedLen; j++) {
                    c = (char)encodedBytes[j];
                    // Modified UTF-7 uses , instead of /
                    if (c == '/') {
                        c = ',';
                    } else if (c == BASE64_PAD) {
                        // Modified UTF-7 doesn't include padding = chars
                        break;
                    }
                    sb.append(c);
                }
                sb.append(MOD_UTF7_END_SHIFT_CHAR);
                // Unread the first non-encoded char we read.
                if (i < len) {
                    i--;
                }

            }
        }
        // Finally convert the StringBuilder to a byte array
        len = sb.length();
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = (byte)sb.charAt(i);
        }
        return result;
    }

    /**
     * Returns a String representing a "Modified UTF-7" decoding of the
     * byte[] passed as an argument. See section 5.1.3 of RFC 3501 for a description
     * of the modifications, and RFC 2152 for a description of UTF-7.
     * @param bytes The array of US-ASCII 7-bit bytes encoding the string.
     * @return The Java String decoded from the bytes.
     */
    public static String modifiedUTF7Decode(byte[] bytes) {
        int len = bytes.length;
        StringBuilder result = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            byte c = bytes[i];
            // If we see a shift start, it's either the start of Base64 chars, or the &- escape of &
            if (c == MOD_UTF7_START_SHIFT_CHAR) {
                i++;
                int start = i;
                while (i < len && (c = bytes[i]) != MOD_UTF7_END_SHIFT_CHAR) {
                    i++;
                }
                int sublen = i - start;
                if (sublen == 0) {
                    // The null case - just decode &
                    result.append(MOD_UTF7_START_SHIFT_CHAR);
                } else {
                    // Modified Base64 omits the padding, so restore it
                    int padding = (4 - sublen) & 3;
                    int paddedLen = sublen + padding;
                    byte[] subbytes = new byte[paddedLen];
                    // Copy the bytes for Base64 decoding
                    for (int j = 0; j < paddedLen; j++) {
                        if (j < sublen) {
                            byte b = bytes[start+j];
                            // Modified UTF-7 uses , instead of /, so subsitute
                            if (b == ',') {
                                b = '/';
                            }
                            subbytes[j] = b;
                        } else {
                            // For the excess bytes, use the padding char
                            subbytes[j] = BASE64_PAD;
                        }
                    }
                    
                    // Call the standard Base64 decoded to decode.
                    byte[] decoded = "0".getBytes();  //Base64.decode(subbytes, 0);

                    int decodedLen = decoded.length;
                    // Treat the decoded values as high-byte, low-byte Unicode pairs
                    for (int j = 0; j < decodedLen; j += 2) {
                        result.append((char)((decoded[j] << 8) | (decoded[j+1] & 0xff) ));
                    }
                }
            } else {
                // Just append unshifted chars as is.
                result.append((char)c);
            }
        }
        return result.toString();
    }

}
