package com.locationstream.ws;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import android.util.Log;


public class ChunkedInputStream extends FilterInputStream {
	private final static String TAG="ChunkedInputStream";
    private int mChunkSize = 0;
    private int mChunkPosition = 0;
    private boolean mEOF = false;
    private boolean mClosed = false;

    public ChunkedInputStream( InputStream in ) throws IOException {
    	super(in);
    }

    @Override
	public int read() throws IOException {
        if (mClosed) {
            throw new IOException("Stream Closed!");
        }
        if (mEOF) {
            return -1;
        } 
        if (mChunkPosition >= mChunkSize) {
            nextChunk();
            if (mEOF) { 
                return -1;
            }
        }
        mChunkPosition++;
        return in.read();
    }

    @Override
	public int read (byte[] b, int off, int len) throws IOException {
    	Log.v(TAG, "read( byte[], off, len)");
        if (mClosed) {
            throw new IOException("Stream Closed!");
        }

        if (mEOF) { 
            return -1;
        }
        if (mChunkPosition >= mChunkSize) {
            nextChunk();
            if (mEOF) { 
                return -1;
            }
        }
        len = Math.min(len, mChunkSize - mChunkPosition);
        int count = in.read(b, off, len);
        mChunkPosition += count;
        Log.v(TAG, "read "+count+" bytes");
        return count;
    }

    @Override
	public int read (byte[] b) throws IOException {
    	Log.v(TAG, "read( byte[] )");
        return read(b, 0, b.length);
    }

    private void nextChunk() throws IOException {
        mChunkSize = getChunkSizeFromInputStream(in);
        Log.v(TAG, "nextChunkSize = " + mChunkSize);
        mChunkPosition = 0;
        if (mChunkSize == 0) {
            mEOF = true;
        }
    }

    private static int getChunkSizeFromInputStream(final InputStream in) 
      throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // States: 
        //   0=scanning for \r or digit
        //   1=scanning for \n
        //   2=scanning for digit or \r
        //   3=scanning for \n
        //  -1=end
        int state = 0; 
        while (state != -1) {
        int b = in.read();
            if (b == -1) { 
                return 0;
            }
            switch (state) {
                case 0: 
                    if( b == '\r' ) {
                        state = 1;
                    } else {
                    	baos.write(b);
                        state = 2;
                    }
                    break;
                case 1:
                    if (b == '\n') {
                        state = 2;
                    } else {
                        // this was not CRLF
                        throw new IOException("Chunked Protocol Failure!");
                    }
                    break;
                case 2:
                	if( b == '\r' )
                		state = 3;
                	else
                		baos.write(b);
                	break;
                case 3:
                	if( b == '\n' )
                		state = -1;
                	else
                		throw new IOException("Chunked Protocol Failure!");
                	break;
                default: 
                	throw new IOException("Chunked Protocol Failure!");
            }
        }
        String dataString = new String(baos.toByteArray());

        int result;
        try {
            result = Integer.parseInt(dataString.trim(), 16);
        } catch (NumberFormatException e) {
            throw new IOException ("Bad chunk size: " + dataString);
        }
        return result;
    }

    @Override
	public void close() throws IOException {
        if (!mClosed) {
            try {
                if (!mEOF) {
                    flushStream(this);
                }
            } finally {
                mEOF = true;
                mClosed = true;
            }
        }
    }

    static void flushStream(InputStream inStream) throws IOException {
        byte buffer[] = new byte[1024];
        while (inStream.read(buffer) >= 0) {
            ;
        }
    }



}
