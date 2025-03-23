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

public final class ChainedBucket
{
    public Object zero;
    public Object one;
    public Object two;
    public Object three;

    public ChainedBucket()
    {
    }

    public ChainedBucket(Object first, Object second)
    {
        this.zero = first;
        this.one = second;
    }

    public void remove(int i)
    {
        if (i > 3)
        {
            this.removeLongChain(this, i - 3);
        }
        else
        {
            switch (i)
            {
                case 0:
                    this.zero = this.removeLast(0);
                    return;
                case 1:
                    this.one = this.removeLast(1);
                    return;
                case 2:
                    this.two = this.removeLast(2);
                    return;
                case 3:
                    if (this.three instanceof ChainedBucket)
                    {
                        this.removeLongChain(this, i - 3);
                        return;
                    }
                    this.three = null;
                    return;
                default:
                    throw new AssertionError();
            }
        }
    }

    private void removeLongChain(ChainedBucket oldBucket, int i)
    {
        do
        {
            ChainedBucket bucket = (ChainedBucket) oldBucket.three;
            switch (i)
            {
                case 0:
                    bucket.zero = bucket.removeLast(0);
                    return;
                case 1:
                    bucket.one = bucket.removeLast(1);
                    return;
                case 2:
                    bucket.two = bucket.removeLast(2);
                    return;
                case 3:
                    if (bucket.three instanceof ChainedBucket)
                    {
                        i -= 3;
                        oldBucket = bucket;
                        continue;
                    }
                    bucket.three = null;
                    return;
                default:
                    if (bucket.three instanceof ChainedBucket)
                    {
                        i -= 3;
                        oldBucket = bucket;
                        continue;
                    }
                    throw new AssertionError();
            }
        }
        while (true);
    }

    public Object get(int i)
    {
        ChainedBucket bucket = this;
        while (i > 3 && bucket.three instanceof ChainedBucket)
        {
            bucket = (ChainedBucket) bucket.three;
            i -= 3;
        }
        do
        {
            switch (i)
            {
                case 0:
                    return bucket.zero;
                case 1:
                    return bucket.one;
                case 2:
                    return bucket.two;
                case 3:
                    if (bucket.three instanceof ChainedBucket)
                    {
                        i -= 3;
                        bucket = (ChainedBucket) bucket.three;
                        continue;
                    }
                    return bucket.three;
                case 4:
                    return null; // this happens when a bucket is exactly full, and we're iterating
                default:
                    throw new AssertionError();
            }
        }
        while (true);
    }

    public Object removeLast(int cur)
    {
        if (this.three instanceof ChainedBucket)
        {
            return this.removeLast(this);
        }
        if (this.three != null)
        {
            Object result = this.three;
            this.three = null;
            return cur == 3 ? null : result;
        }
        if (this.two != null)
        {
            Object result = this.two;
            this.two = null;
            return cur == 2 ? null : result;
        }
        if (this.one != null)
        {
            Object result = this.one;
            this.one = null;
            return cur == 1 ? null : result;
        }
        this.zero = null;
        return null;
    }

    private Object removeLast(ChainedBucket oldBucket)
    {
        do
        {
            ChainedBucket bucket = (ChainedBucket) oldBucket.three;
            if (bucket.three instanceof ChainedBucket)
            {
                oldBucket = bucket;
                continue;
            }
            if (bucket.three != null)
            {
                Object result = bucket.three;
                bucket.three = null;
                return result;
            }
            if (bucket.two != null)
            {
                Object result = bucket.two;
                bucket.two = null;
                return result;
            }
            if (bucket.one != null)
            {
                Object result = bucket.one;
                bucket.one = null;
                return result;
            }
            Object result = bucket.zero;
            oldBucket.three = null;
            return result;
        }
        while (true);
    }

    public ChainedBucket copy()
    {
        ChainedBucket result = new ChainedBucket();
        ChainedBucket dest = result;
        ChainedBucket src = this;
        do
        {
            dest.zero = src.zero;
            dest.one = src.one;
            dest.two = src.two;
            if (src.three instanceof ChainedBucket)
            {
                dest.three = new ChainedBucket();
                src = (ChainedBucket) src.three;
                dest = (ChainedBucket) dest.three;
                continue;
            }
            dest.three = src.three;
            return result;
        }
        while (true);
    }
}