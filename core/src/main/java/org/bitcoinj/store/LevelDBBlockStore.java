package org.bitcoinj.store;

import org.bitcoinj.core.*;
import org.fusesource.leveldbjni.*;
import org.iq80.leveldb.*;

import javax.annotation.*;
import java.io.*;
import java.nio.*;

/**
 * An SPV block store that writes every header it sees to a <a href="https://github.com/fusesource/leveldbjni">LevelDB</a>.
 * This allows for fast lookup of block headers by block hash at the expense of more costly inserts and higher disk
 * usage than the {@link SPVBlockStore}. If all you want is a regular wallet you don't need this class: it exists for
 * specialised applications where you need to quickly verify a standalone SPV proof.
 */
public class LevelDBBlockStore implements BlockStore<StoredHeader> {
    private static final byte[] CHAIN_HEAD_KEY = "chainhead".getBytes();

    private final Context context;
    private DB db;
    private final ByteBuffer buffer = ByteBuffer.allocate(StoredHeader.COMPACT_SERIALIZED_SIZE);
    private final File path;

    /** Creates a LevelDB SPV block store using the JNI/C++ version of LevelDB. */
    public LevelDBBlockStore(Context context, File directory) throws BlockStoreException {
        this(context, directory, JniDBFactory.factory);
    }

    /** Creates a LevelDB SPV block store using the given factory, which is useful if you want a pure Java version. */
    public LevelDBBlockStore(Context context, File directory, DBFactory dbFactory) throws BlockStoreException {
        this.context = context;
        this.path = directory;
        Options options = new Options();
        options.createIfMissing();

        try {
            tryOpen(directory, dbFactory, options);
        } catch (IOException e) {
            try {
                dbFactory.repair(directory, options);
                tryOpen(directory, dbFactory, options);
            } catch (IOException e1) {
                throw new BlockStoreException(e1);
            }
        }
    }

    private synchronized void tryOpen(File directory, DBFactory dbFactory, Options options) throws IOException, BlockStoreException {
        db = dbFactory.open(directory, options);
        initStoreIfNeeded();
    }

    private synchronized void initStoreIfNeeded() throws BlockStoreException {
        if (db.get(CHAIN_HEAD_KEY) != null)
            return;   // Already initialised.
        Block genesis = context.getParams().getGenesisBlock().cloneAsHeader();
        StoredHeader storedGenesis = new StoredHeader(genesis, genesis.getWork(), 0);
        put(storedGenesis);
        setChainHead(storedGenesis);
    }

    @Override
    public synchronized void put(StoredHeader block) throws BlockStoreException {
        buffer.clear();
        block.serializeCompact(buffer);
        db.put(block.getBlock().getHash().getBytes(), buffer.array());
    }

    @Override @Nullable
    public synchronized StoredHeader get(Sha256Hash hash) throws BlockStoreException {
        byte[] bits = db.get(hash.getBytes());
        if (bits == null)
            return null;
        return StoredHeader.deserializeCompact(context.getParams(), ByteBuffer.wrap(bits));
    }

    @Override
    public synchronized StoredHeader getChainHead() throws BlockStoreException {
        return get(new Sha256Hash(db.get(CHAIN_HEAD_KEY)));
    }

    @Override
    public synchronized void setChainHead(StoredHeader chainHead) throws BlockStoreException {
        db.put(CHAIN_HEAD_KEY, chainHead.getBlock().getHash().getBytes());
    }

    @Override
    public synchronized void close() throws BlockStoreException {
        try {
            db.close();
        } catch (IOException e) {
            throw new BlockStoreException(e);
        }
    }

    /** Erases the contents of the database (but NOT the underlying files themselves) and then reinitialises with the genesis block. */
    public synchronized void reset() throws BlockStoreException {
        try {
            WriteBatch batch = db.createWriteBatch();
            try {
                DBIterator it = db.iterator();
                try {
                    it.seekToFirst();
                    while (it.hasNext())
                        batch.delete(it.next().getKey());
                    db.write(batch);
                } finally {
                    it.close();
                }
            } finally {
                batch.close();
            }
            initStoreIfNeeded();
        } catch (IOException e) {
            throw new BlockStoreException(e);
        }
    }

    public synchronized void destroy() throws IOException {
        JniDBFactory.factory.destroy(path, new Options());
    }

    @Override
    public NetworkParameters getParams() {
        return context.getParams();
    }
}
