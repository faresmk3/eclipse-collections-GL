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
import java.util.concurrent.ExecutorService;

import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.impl.lazy.AbstractLazyIterable;
import org.eclipse.collections.impl.lazy.parallel.AbstractParallelIterable;
import org.eclipse.collections.impl.lazy.parallel.set.AbstractParallelUnsortedSetIterable;
import org.eclipse.collections.impl.lazy.parallel.set.RootUnsortedSetBatch;

public final class UnifiedSetParallelUnsortedIterable extends AbstractParallelUnsortedSetIterable<T, RootUnsortedSetBatch<T>>
{
    private final ExecutorService executorService;
    private final int batchSize;

    public UnifiedSetParallelUnsortedIterable(ExecutorService executorService, int batchSize)
    {
        this.executorService = executorService;
        this.batchSize = batchSize;
    }

    @Override
    public ExecutorService getExecutorService()
    {
        return this.executorService;
    }

    @Override
    public int getBatchSize()
    {
        return this.batchSize;
    }

    @Override
    public LazyIterable<RootUnsortedSetBatch<T>> split()
    {
        return new UnifiedSetParallelSplitLazyIterable();
    }

    @Override
    public void forEach(Procedure<? super T> procedure)
    {
        AbstractParallelIterable.forEach(this, procedure);
    }

    @Override
    public boolean anySatisfy(Predicate<? super T> predicate)
    {
        return AbstractParallelIterable.anySatisfy(this, predicate);
    }

    @Override
    public boolean allSatisfy(Predicate<? super T> predicate)
    {
        return AbstractParallelIterable.allSatisfy(this, predicate);
    }

    @Override
    public T detect(Predicate<? super T> predicate)
    {
        return AbstractParallelIterable.detect(this, predicate);
    }

    @Override
    public Object[] toArray()
    {
        // TODO: Implement in parallel
        return UnifiedSet.this.toArray();
    }

    @Override
    public <E> E[] toArray(E[] array)
    {
        // TODO: Implement in parallel
        return UnifiedSet.this.toArray(array);
    }

    private class UnifiedSetParallelSplitIterator implements Iterator<RootUnsortedSetBatch<T>>
    {
        protected int chunkIndex;

        @Override
        public boolean hasNext()
        {
            return this.chunkIndex * UnifiedSetParallelUnsortedIterable.this.batchSize < UnifiedSet.this.table.length;
        }

        @Override
        public RootUnsortedSetBatch<T> next()
        {
            int chunkStartIndex = this.chunkIndex * UnifiedSetParallelUnsortedIterable.this.batchSize;
            int chunkEndIndex = (this.chunkIndex + 1) * UnifiedSetParallelUnsortedIterable.this.batchSize;
            int truncatedChunkEndIndex = Math.min(chunkEndIndex, UnifiedSet.this.table.length);
            this.chunkIndex++;
            return new UnifiedSet.UnifiedUnsortedSetBatch(chunkStartIndex, truncatedChunkEndIndex);
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException("Cannot call remove() on " + this.getClass().getSimpleName());
        }
    }

    private class UnifiedSetParallelSplitLazyIterable
            extends AbstractLazyIterable<RootUnsortedSetBatch<T>>
    {
        @Override
        public void each(Procedure<? super RootUnsortedSetBatch<T>> procedure)
        {
            for (RootUnsortedSetBatch<T> chunk : this)
            {
                procedure.value(chunk);
            }
        }

        @Override
        public Iterator<RootUnsortedSetBatch<T>> iterator()
        {
            return new UnifiedSetParallelSplitIterator();
        }
    }
}