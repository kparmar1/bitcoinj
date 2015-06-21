/*
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

import java.io.Serializable;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractStored implements Serializable {
    private static final long serialVersionUID = 1807855642326414939L;

    // A BigInteger representing the total amount of work done so far on this chain. As of May 2011 it takes 8
    // bytes to represent this field, so 12 bytes should be plenty for now.
    public static final int CHAIN_WORK_BYTES = 12;
    public static final byte[] EMPTY_BYTES = new byte[CHAIN_WORK_BYTES];
    public static final int COMPACT_SERIALIZED_SIZE = Block.HEADER_SIZE + CHAIN_WORK_BYTES + 4;  // for height

    protected Block block;
    protected BigInteger chainWork;
    protected int height;

    protected AbstractStored(Block block, BigInteger chainWork, int height, boolean removeTx) {
        if(removeTx && block.transactions != null) {
            this.block = block.cloneAsHeader();
        } else {
            this.block = block;
        }
        this.chainWork = chainWork;
        this.height = height;
    }

    /**
     * The block block this object wraps. The referenced block object must not have any transactions in it.
     */
    public Block getBlock() {
        return block;
    }

    /**
     * The total sum of work done in this block, and all the blocks below it in the chain. Work is a measure of how
     * many tries are needed to solve a block. If the target is set to cover 10% of the total hash value space,
     * then the work represented by a block is 10.
     */
    public BigInteger getChainWork() {
        return chainWork;
    }

    /**
     * Position in the chain for this block. The genesis block has a height of zero.
     */
    public int getHeight() {
        return height;
    }

    /** Returns true if this objects chainWork is higher than the others. */
    public boolean moreWorkThan(AbstractStored other) {
        return chainWork.compareTo(other.chainWork) > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractStored other = (AbstractStored) o;
        return block.equals(other.block) &&
                chainWork.equals(other.chainWork) &&
                height == other.height;
    }

    @Override
    public int hashCode() {
        // A better hashCode is possible, but this works for now.
        return block.hashCode() ^ chainWork.hashCode() ^ height;
    }

    /**
     * Creates a new AbstractStored, calculating the additional fields by adding to the values in this block.
     */
    abstract public AbstractStored build(Block block) throws VerificationException;

    /**
     * Given a block store, looks up the previous block in this chain. Convenience method for doing
     * <tt>store.get(this.getBlock().getPrevBlockHash())</tt>.
     *
     * @return the previous block in the chain or null if it was not found in the store.
     */
    public <T extends AbstractStored> T getPrev(BlockStore<T> store) throws BlockStoreException {
        return store.get(getBlock().getPrevBlockHash());
    }

    @Override
    public String toString() {
        return String.format("Block %s at height %d: %s",
                getBlock().getHashAsString(), getHeight(), getBlock().toString());
    }
}
