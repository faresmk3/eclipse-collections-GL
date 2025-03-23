/*
 * Copyright (c) 2025 Goldman Sachs and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompany this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.eclipse.collections.impl.set.mutable;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class PositionalIterator implements Iterator<T>
{
    private final UnifiedSet<T> parent;
    protected int count;
    protected int position;
    protected int chainPosition;
    protected boolean lastReturned;

    public PositionalIterator(UnifiedSet<T> parent) {
        this.parent = parent;
        this.count = 0;
        this.position = 0;
        this.chainPosition = 0;
        this.lastReturned = false;
    }
    @Override
    public boolean hasNext()
    {
        return this.count < parent.size();
    }

    @Override
    public void remove()
    {
        if (!this.lastReturned)
        {
            throw new IllegalStateException("next() must be called as many times as remove()");
        }
        this.count--;
        parent.occupied--;

        if (this.chainPosition != 0)
        {
            this.removeFromChain();
            return;
        }

        int pos = this.position - 1;
        Object key = parent.table[pos];
        if (key instanceof ChainedBucket)
        {
            this.removeLastFromChain((ChainedBucket) key, pos);
            return;
        }
        parent.table[pos] = null;
        this.position = pos;
        this.lastReturned = false;
    }

    protected void removeFromChain()
    {
        ChainedBucket chain = (ChainedBucket) parent.table[this.position];
        chain.remove(--this.chainPosition);
        this.lastReturned = false;
    }

    protected void removeLastFromChain(ChainedBucket bucket, int tableIndex)
    {
        bucket.removeLast(0);
        if (bucket.zero == null)
        {
            parent.this.table[tableIndex] = null;
        }
        this.lastReturned = false;
    }

    protected T nextFromChain()
    {
        ChainedBucket bucket = (ChainedBucket) parent.table[this.position];
        Object cur = bucket.get(this.chainPosition);
        this.chainPosition++;
        if (bucket.get(this.chainPosition) == null)
        {
            this.chainPosition = 0;
            this.position++;
        }
        this.lastReturned = true;
        return parent.nonSentinel(cur);
    }

    @Override
    public T next()
    {
        if (!this.hasNext())
        {
            throw new NoSuchElementException("next() called, but the iterator is exhausted");
        }
        this.count++;
        Object[] table = parent.table;
        if (this.chainPosition != 0)
        {
            return this.nextFromChain();
        }
        while (table[this.position] == null)
        {
            this.position++;
        }
        Object cur = table[this.position];
        if (cur instanceof ChainedBucket)
        {
            return this.nextFromChain();
        }
        this.position++;
        this.lastReturned = true;
        return parent.nonSentinel(cur);
    }
}