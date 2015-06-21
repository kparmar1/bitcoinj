/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.core;

import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkState;

/**
 * Wraps a {@link Block} object with extra data that can be derived from the block chain but is slow or inconvenient to
 * calculate. By storing it alongside the block block we reduce the amount of work required significantly.
 * Recalculation is slow because the fields are cumulative - to find the chainWork you have to iterate over every
 * block in the chain back to the genesis block, which involves lots of seeking/loading etc. So we just keep a
 * running total: it's a disk space vs cpu/io tradeoff.<p>
 *
 * StoredHeaders are put inside a {@link BlockStore} which saves them to memory or disk.
 */
public class StoredHeader extends AbstractStored implements Serializable {
    private static final long serialVersionUID = -6097565241243701771L;

    protected StoredHeader(Block block, BigInteger chainWork, int height, boolean removeTx) {
        super(block, chainWork, height, removeTx);
    }

    public StoredHeader(Block block, BigInteger chainWork, int height) {
        this(block, chainWork, height, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ 37;
    }

    /** Serializes the stored block to a custom packed format. Used by {@link CheckpointManager}. */
    public void serializeCompact(ByteBuffer buffer) {
        byte[] chainWorkBytes = getChainWork().toByteArray();
        checkState(chainWorkBytes.length <= CHAIN_WORK_BYTES, "Ran out of space to store chain work!");
        if (chainWorkBytes.length < CHAIN_WORK_BYTES) {
            // Pad to the right size.
            buffer.put(EMPTY_BYTES, 0, CHAIN_WORK_BYTES - chainWorkBytes.length);
        }
        buffer.put(chainWorkBytes);
        buffer.putInt(getHeight());
        // Using unsafeBitcoinSerialize here can give us direct access to the same bytes we read off the wire,
        // avoiding serialization round-trips.
        byte[] bytes = getBlock().unsafeBitcoinSerialize();
        buffer.put(bytes, 0, Block.HEADER_SIZE);  // Trim the trailing 00 byte (zero transactions).
    }

    /** De-serializes the stored block from a custom packed format. Used by {@link CheckpointManager}. */
    public static StoredHeader deserializeCompact(NetworkParameters params, ByteBuffer buffer) throws ProtocolException {
        byte[] chainWorkBytes = new byte[AbstractStored.CHAIN_WORK_BYTES];
        buffer.get(chainWorkBytes);
        BigInteger chainWork = new BigInteger(1, chainWorkBytes);
        int height = buffer.getInt();  // +4 bytes
        byte[] header = new byte[Block.HEADER_SIZE + 1];    // Extra byte for the 00 transactions length.
        buffer.get(header, 0, Block.HEADER_SIZE);
        return new StoredHeader(new Block(params, header), chainWork, height);
    }

    /**
     * Creates a new StoredHeader, calculating the additional fields by adding to the values in this block.
     */
    public StoredHeader build(Block block) throws VerificationException {
        // Stored blocks track total work done in this chain, because the canonical chain is the one that represents
        // the largest amount of work done not the tallest.
        BigInteger chainWork = this.chainWork.add(block.getWork());
        int height = this.height + 1;
        return new StoredHeader(block, chainWork, height);
    }
}
