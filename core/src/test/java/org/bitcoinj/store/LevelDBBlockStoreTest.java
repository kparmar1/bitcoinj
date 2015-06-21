package org.bitcoinj.store;

import org.bitcoinj.core.*;
import org.bitcoinj.params.*;
import org.junit.*;

import java.io.*;

import static org.junit.Assert.assertEquals;

public class LevelDBBlockStoreTest {
    @Test
    public void basics() throws Exception {
        File f = File.createTempFile("leveldbblockstore", null);
        f.delete();

        NetworkParameters params = UnitTestParams.get();
        Context context = new Context(params);
        LevelDBBlockStore store = new LevelDBBlockStore(context, f);
        store.reset();

        // Check the first block in a new store is the genesis block.
        StoredHeader genesis = store.getChainHead();
        assertEquals(params.getGenesisBlock(), genesis.getBlock());
        assertEquals(0, genesis.getHeight());

        // Build a new block.
        Address to = new Address(params, "mrj2K6txjo2QBcSmuAzHj4nD1oXSEJE1Qo");
        StoredHeader b1 = genesis.build(genesis.getBlock().createNextBlock(to).cloneAsHeader());
        store.put(b1);
        store.setChainHead(b1);
        store.close();

        // Check we can get it back out again if we rebuild the store object.
        store = new LevelDBBlockStore(context, f);
        try {
            StoredHeader b2 = store.get(b1.getBlock().getHash());
            assertEquals(b1, b2);
            // Check the chain head was stored correctly also.
            StoredHeader chainHead = store.getChainHead();
            assertEquals(b1, chainHead);
        } finally {
            store.close();
            store.destroy();
        }
    }
}