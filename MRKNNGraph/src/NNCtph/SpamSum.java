package NNCtph;

/**
 *
 * @author tibo
 */
public class SpamSum {

    protected static final int MIN_BLOCKSIZE = 3;
    protected static final long HASH_PRIME = 0x01000193;
    protected static final long HASH_INIT = 0x28021967;
    protected static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    protected int SPAMSUM_LENGTH = 2;
    protected int blocksize;
    protected char[] left;
    protected char[] right;
    
    public SpamSum(int length) {
        SPAMSUM_LENGTH = length;
    }
    
    public SpamSum() {
        
    }

    public String HashString(String string) {
        return HashString(string, 0);
    }
    
    /**
     *
     * @param string
     * @param bsize
     * @return
     */
    public String HashString(String string, int bsize) {
        int length = string.length();

        char[] in = string.toCharArray();

        if (bsize == 0) {
            /* guess a reasonable block size */
            blocksize = MIN_BLOCKSIZE;
            while (blocksize * SPAMSUM_LENGTH < length) {
                blocksize = blocksize * 2;
            }

        } else {
            blocksize = bsize;
        }

        while (true) {

            left = new char[SPAMSUM_LENGTH];
            right = new char[SPAMSUM_LENGTH];

            int k = 0;
            int j = 0;
            long h3 = HASH_INIT;
            long h2 = HASH_INIT;
            long h = rolling_hash_reset();

            for (int i = 0; i < length; i++) {

                /* at each character we update the rolling hash and the normal 
                 * hash. When the rolling hash hits the reset value then we emit 
                 * the normal hash as a element of the signature and reset both 
                 * hashes
                 */
                
                h = rolling_hash((byte)in[i]);
                h2 = sum_hash((byte) in[i], h2);
                h3 = sum_hash((byte) in[i], h3);

                if (h % blocksize == (blocksize - 1)) {

                    /* we have hit a reset polong. We now emit a hash which is based
                     * on all chacaters in the piece of the string between the last 
                     * reset polong and this one
                     */
                    
                    left[j] = B64[(int) (h2 % 64)];
                    if (j < SPAMSUM_LENGTH - 1) {

                        /* we can have a problem with the tail overflowing. The easiest way
                         * to cope with this is to only reset the second hash if we have 
                         * room for more characters in our signature. This has the effect of
                         * combining the last few pieces of the message longo a single piece
                         */
                        h2 = HASH_INIT;
                        j++;
                    }
                }

                /* this produces a second signature with a block size of block_size*2. 
                 * By producing dual signatures in this way the effect of small changes
                 * in the string near a block size boundary is greatly reduced.
                 */
                if (h % (blocksize * 2) == ((blocksize * 2) - 1)) {
                    right[k] = B64[(int) (h3 % 64)];
                    if (k < SPAMSUM_LENGTH / 2 - 1) {
                        h3 = HASH_INIT;
                        k++;
                    }
                }
            }

            /* If we have anything left then add it to the end. This ensures that the
             * last part of the string is always considered
             */
            if (h != 0) {
                left[j] = B64[(int) (h2 % 64)];
                right[k] = B64[(int) (h3 % 64)];
            }

            /* Our blocksize guess may have been way off - repeat if necessary
             */
            if (bsize == 0
                    && blocksize > MIN_BLOCKSIZE
                    && j < SPAMSUM_LENGTH / 2) {
                blocksize = blocksize / 2;
                // loop...
            } else {
                break;
            }
        }

        return toString();
    }

    @Override
    public String toString() {
        return "" + blocksize + ":"
                + String.valueOf(left) + ":"
                + String.valueOf(right);
    }

    public long BlockSize() {
        return blocksize;
    }

    public String Left() {
        return String.valueOf(left);
    }

    public String Right() {
        return String.valueOf(right);
    }

    /* A simple non-rolling hash, based on the FNV hash
     * http://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function
     */
    protected static long sum_hash(long c, long h) {
        h = (h * HASH_PRIME) % 4294967296L;     
        h = (h ^ c) % 4294967296L;
        return h;
    }

    /* A rolling hash, based on the Adler checksum. By using a rolling hash
     * we can perform auto resynchronisation after inserts/deletes longernally,
     * h1 is the sum of the bytes in the window and h2 is the sum of the bytes 
     * times the index. h3 is a shift/xor based rolling hash, and is mostly 
     * needed to ensure that we can cope with large blocksize values
     */
    protected static final int ROLLING_WINDOW = 7;

    protected long[] rolling_window;
    protected long rolling_h1;
    protected long rolling_h2;
    protected long rolling_h3;
    protected long rolling_n;

    protected long rolling_hash(long c) {
        rolling_h2 -= rolling_h1;     
        rolling_h2 += ROLLING_WINDOW * c;

        rolling_h1 += c;
        rolling_h1 -= rolling_window[(int) rolling_n % ROLLING_WINDOW];

        rolling_window[(int) rolling_n % ROLLING_WINDOW] = c;
        rolling_n++;

        rolling_h3 = (rolling_h3 << 5) & 0xFFFFFFFF;
        rolling_h3 ^= c;
        
        return rolling_h1 + rolling_h2 + rolling_h3;
    }

    protected long rolling_hash_reset() {
        rolling_window = new long[ROLLING_WINDOW];

        rolling_h1 = 0;
        rolling_h2 = 0;
        rolling_h3 = 0;
        rolling_n = 0;

        return 0;
    }
}
