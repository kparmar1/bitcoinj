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

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Wraps a {@link Block} object with extra data that can be derived from the block chain but is slow or inconvenient to
 * calculate. By storing it alongside the block block we reduce the amount of work required significantly.
 * Recalculation is slow because the fields are cumulative - to find the chainWork you have to iterate over every
 * block in the chain back to the genesis block, which involves lots of seeking/loading etc. So we just keep a
 * running total: it's a disk space vs cpu/io tradeoff.<p>
 *
 * StoredBlocks are put inside a {@link org.bitcoinj.store.BlockStore} which saves them to memory or disk.
 */
public class StoredBlock extends StoredHeader implements Serializable {
    private static final long serialVersionUID = -1213935701044971119L;

    public StoredBlock(Block block, BigInteger chainWork, int height) {
        super(block, chainWork, height, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ 33;
    }

    /**
     * Creates a new StoredBlock, calculating the additional fields by adding to the values in this block.
     */
    public StoredBlock build(Block block) throws VerificationException {
        // Stored blocks track total work done in this chain, because the canonical chain is the one that represents
        // the largest amount of work done not the tallest.
        BigInteger chainWork = this.chainWork.add(block.getWork());
        int height = this.height + 1;
        return new StoredBlock(block, chainWork, height);
    }

    /**
     * Get the Header only that represents this StoredBlock.
     * @return The header.
     */
    public StoredHeader getHeader() {
        return new StoredHeader(block, chainWork, height, true);
    }
}
