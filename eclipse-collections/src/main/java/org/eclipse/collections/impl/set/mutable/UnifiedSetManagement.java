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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.Set;

import org.eclipse.collections.impl.block.factory.Procedures2;
import org.eclipse.collections.impl.set.AbstractUnifiedSet;
import org.eclipse.collections.impl.utility.Iterate;

import static org.eclipse.collections.impl.map.mutable.UnifiedMap.DEFAULT_INITIAL_CAPACITY;
import static org.eclipse.collections.impl.set.mutable.UnifiedSet.NULL_KEY;

public class UnifiedSetManagement
{
    T nonSentinel(Object key)
    {
        return key == NULL_KEY ? null : (T) key;
    }
    private Object[] table;
    private int occupied;
    private float loadFactor;
    private int maxSize;
    private UnifiedSet unifiedSet;
    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;

    public UnifiedSetManagement(int initialCapacity, float loadFactor) {
        this.loadFactor = loadFactor;
        this.table = new Object[initialCapacity];
        this.occupied = 0;
        this.maxSize = (int)(initialCapacity * loadFactor);
        this.unifiedSet = UnifiedSet.newSet();
    }

    protected int index(Object key)
    {
        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        int h = key == null ? 0 : key.hashCode();
        h ^= h >>> 20 ^ h >>> 12;
        h ^= h >>> 7 ^ h >>> 4;
        return h & this.table.length - 1;
    }

    public boolean add(T key)
    {
        int index = this.index(key);
        Object cur = this.table[index];
        if (cur == null)
        {
            this.table[index] = this.toSentinelIfNull(key);
            if (++this.occupied > this.maxSize)
            {
                this.rehash();
            }
            return true;
        }
        if (cur instanceof ChainedBucket || !this.nonNullTableObjectEquals(cur, key))
        {
            return this.chainedAdd(key, index);
        }
        return false;
    }

    private void incrementAndRehashIfNeeded() {
        if (++this.occupied > this.maxSize) {
            this.rehash();
        }
    }

    private boolean chainedAdd(T key, int index) {
        Object realKey = UnifiedSet.toSentinelIfNull(key);
        Object tableEntry = this.table[index];
        // If the current entry is not a bucket, wrap it in one:
        if (!(tableEntry instanceof ChainedBucket)) {
            this.table[index] = new ChainedBucket(tableEntry, realKey);
            this.incrementAndRehashIfNeeded();
            return true;
        }
        ChainedBucket bucket = (ChainedBucket) tableEntry;
        while (true) {
            // Loop for the first three fields (indices 0, 1, 2)
            for (int i = 0; i < 3; i++) {
                Object currentField = bucket.get(i);
                if (this.nonNullTableObjectEquals(currentField, key)) {
                    return false;
                }
                if (currentField == null) {
                    bucket.set(i, realKey);
                    incrementAndRehashIfNeeded();
                    return true;
                }
            }
            // Handle the fourth field (index 3)
            Object fourthField = bucket.get(3);
            if (fourthField instanceof ChainedBucket) {
                bucket = (ChainedBucket) fourthField;
                continue;
            }
            if (this.nonNullTableObjectEquals(fourthField, key)) {
                return false;
            }
            if (fourthField == null) {
                bucket.set(3, realKey);
                this.incrementAndRehashIfNeeded();
                return true;
            }
            bucket.set(3, new ChainedBucket(fourthField, realKey));
            this.incrementAndRehashIfNeeded();
            return true;
        }
    }


    private boolean chainContains(ChainedBucket bucket, T key)
    {
        do
        {
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                return true;
            }
            if (bucket.three == null)
            {
                return false;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            return this.nonNullTableObjectEquals(bucket.three, key);
        }
        while (true);
    }

    public boolean contains(Object key)
    {
        int index = this.index(key);
        Object cur = this.table[index];
        if (cur == null)
        {
            return false;
        }
        if (cur instanceof ChainedBucket)
        {
            return this.chainContains((ChainedBucket) cur, (T) key);
        }
        return this.nonNullTableObjectEquals(cur, (T) key);
    }

    public boolean remove(Object key)
    {
        int index = this.index(key);

        Object cur = this.table[index];
        if (cur == null)
        {
            return false;
        }
        if (cur instanceof ChainedBucket)
        {
            return this.removeFromChain((ChainedBucket) cur, (T) key, index);
        }
        if (this.nonNullTableObjectEquals(cur, (T) key))
        {
            this.table[index] = null;
            this.occupied--;
            return true;
        }
        return false;
    }

    private boolean removeFromChain(ChainedBucket bucket, T key, int index)
    {
        if (this.nonNullTableObjectEquals(bucket.zero, key))
        {
            bucket.zero = bucket.removeLast(0);
            if (bucket.zero == null)
            {
                this.table[index] = null;
            }
            this.occupied--;
            return true;
        }
        if (bucket.one == null)
        {
            return false;
        }
        if (this.nonNullTableObjectEquals(bucket.one, key))
        {
            bucket.one = bucket.removeLast(1);
            this.occupied--;
            return true;
        }
        if (bucket.two == null)
        {
            return false;
        }
        if (this.nonNullTableObjectEquals(bucket.two, key))
        {
            bucket.two = bucket.removeLast(2);
            this.occupied--;
            return true;
        }
        if (bucket.three == null)
        {
            return false;
        }
        if (bucket.three instanceof ChainedBucket)
        {
            return this.removeDeepChain(bucket, key);
        }
        if (this.nonNullTableObjectEquals(bucket.three, key))
        {
            bucket.three = bucket.removeLast(3);
            this.occupied--;
            return true;
        }
        return false;
    }

    private boolean removeDeepChain(ChainedBucket oldBucket, T key)
    {
        do
        {
            ChainedBucket bucket = (ChainedBucket) oldBucket.three;
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                bucket.zero = bucket.removeLast(0);
                if (bucket.zero == null)
                {
                    oldBucket.three = null;
                }
                this.occupied--;
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                bucket.one = bucket.removeLast(1);
                this.occupied--;
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                bucket.two = bucket.removeLast(2);
                this.occupied--;
                return true;
            }
            if (bucket.three == null)
            {
                return false;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                oldBucket = bucket;
                continue;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                bucket.three = bucket.removeLast(3);
                this.occupied--;
                return true;
            }
            return false;
        }
        while (true);
    }



    public void ensureCapacity(int size)
    {
        if (size > this.maxSize)
        {
            size = (int) (size / this.loadFactor) + 1;
            int capacity = Integer.highestOneBit(size);
            if (size != capacity)
            {
                capacity <<= 1;
            }
            this.rehash();
        }
    }

    public void copyToArray(Object[] result)
    {
        Object[] table = this.table;
        int count = 0;
        for (int i = 0; i < table.length; i++)
        {
            Object cur = table[i];
            if (cur != null)
            {
                if (cur instanceof ChainedBucket)
                {
                    ChainedBucket bucket = (ChainedBucket) cur;
                    count = this.copyBucketToArray(result, bucket, count);
                }
                else
                {
                    result[count++] = this.nonSentinel(cur);
                }
            }
        }
    }

    private int copyBucketToArray(Object[] result, ChainedBucket bucket, int count)
    {
        do
        {
            result[count++] = this.nonSentinel(bucket.zero);
            if (bucket.one == null)
            {
                break;
            }
            result[count++] = this.nonSentinel(bucket.one);
            if (bucket.two == null)
            {
                break;
            }
            result[count++] = this.nonSentinel(bucket.two);
            if (bucket.three == null)
            {
                break;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            result[count++] = this.nonSentinel(bucket.three);
            break;
        }
        while (true);
        return count;
    }

    protected boolean copySet(UnifiedSet<?> unifiedset)
    {
        //todo: optimize for current size == 0
        boolean changed = false;
        for (int i = 0; i < unifiedset.table.length; i++)
        {
            Object cur = unifiedset.table[i];
            if (cur instanceof ChainedBucket)
            {
                changed |= this.copyChain((ChainedBucket) cur);
            }
            else if (cur != null)
            {
                changed |= this.add(this.nonSentinel(cur));
            }
        }
        return changed;
    }

    private boolean copyChain(ChainedBucket bucket)
    {
        boolean changed = false;
        do
        {
            changed |= this.add(this.nonSentinel(bucket.zero));
            if (bucket.one == null)
            {
                return changed;
            }
            changed |= this.add(this.nonSentinel(bucket.one));
            if (bucket.two == null)
            {
                return changed;
            }
            changed |= this.add(this.nonSentinel(bucket.two));
            if (bucket.three == null)
            {
                return changed;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            changed |= this.add(this.nonSentinel(bucket.three));
            return changed;
        }
        while (true);
    }

    private Object toSentinelIfNull(Object key)
    {
        if (key == null)
        {
            return NULL_KEY;
        }
        return key;
    }
    private boolean nonNullTableObjectEquals(Object cur, T key)
    {
        return cur == key || (cur == NULL_KEY ? key == null : cur.equals(key));
    }

    public int hashCode()
    {
        int hashCode = 0;
        for (int i = 0; i < this.table.length; i++)
        {
            Object cur = this.table[i];
            if (cur instanceof ChainedBucket)
            {
                hashCode += this.chainedHashCode((ChainedBucket) cur);
            }
            else if (cur != null)
            {
                hashCode += cur == NULL_KEY ? 0 : cur.hashCode();
            }
        }
        return hashCode;
    }

    private int chainedHashCode(ChainedBucket bucket)
    {
        int hashCode = 0;
        do
        {
            hashCode += bucket.zero == NULL_KEY ? 0 : bucket.zero.hashCode();
            if (bucket.one == null)
            {
                return hashCode;
            }
            hashCode += bucket.one == NULL_KEY ? 0 : bucket.one.hashCode();
            if (bucket.two == null)
            {
                return hashCode;
            }
            hashCode += bucket.two == NULL_KEY ? 0 : bucket.two.hashCode();
            if (bucket.three == null)
            {
                return hashCode;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            hashCode += bucket.three == NULL_KEY ? 0 : bucket.three.hashCode();
            return hashCode;
        }
        while (true);
    }

    public void addIfFoundFromChain(ChainedBucket bucket, T key, UnifiedSet<T> other)
    {
        do
        {
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                other.add(this.nonSentinel(bucket.zero));
                return;
            }
            if (bucket.one == null)
            {
                return;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                other.add(this.nonSentinel(bucket.one));
                return;
            }
            if (bucket.two == null)
            {
                return;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                other.add(this.nonSentinel(bucket.two));
                return;
            }
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                other.add(this.nonSentinel(bucket.three));
                return;
            }
            return;
        }
        while (true);
    }

    protected void rehash()
    {
        this.rehash(this.getTable().length << 1);
    }

    protected void rehash(int newCapacity)
    {
        int oldLength = this.table.length;
        Object[] old = this.table;
        this.allocate(newCapacity);
        this.occupied = 0;

        for (int i = 0; i < oldLength; i++)
        {
            Object oldKey = old[i];
            if (oldKey instanceof ChainedBucket)
            {
                ChainedBucket bucket = (ChainedBucket) oldKey;
                do
                {
                    if (bucket.zero != null)
                    {
                        this.add(this.nonSentinel(bucket.zero));
                    }
                    if (bucket.one == null)
                    {
                        break;
                    }
                    this.add(this.nonSentinel(bucket.one));
                    if (bucket.two == null)
                    {
                        break;
                    }
                    this.add(this.nonSentinel(bucket.two));
                    if (bucket.three != null)
                    {
                        if (bucket.three instanceof ChainedBucket)
                        {
                            bucket = (ChainedBucket) bucket.three;
                            continue;
                        }
                        this.add(this.nonSentinel(bucket.three));
                    }
                    break;
                }
                while (true);
            }
            else if (oldKey != null)
            {
                this.add(this.nonSentinel(oldKey));
            }
        }
    }
    protected Object[] getTable()
    {
        return this.table;
    }

    protected void allocateTable(int sizeToAllocate)
    {
        this.table = new Object[sizeToAllocate];
    }



    public UnifiedSet<T> newEmpty()
    {
        return UnifiedSet.newSet();
    }

    public UnifiedSet<T> newEmpty(int size)
    {
        return UnifiedSet.newSet(size, this.loadFactor);
    }
}
