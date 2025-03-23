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

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.impl.lazy.parallel.AbstractBatch;
import org.eclipse.collections.impl.lazy.parallel.bag.CollectUnsortedBagBatch;
import org.eclipse.collections.impl.lazy.parallel.bag.FlatCollectUnsortedBagBatch;
import org.eclipse.collections.impl.lazy.parallel.bag.UnsortedBagBatch;
import org.eclipse.collections.impl.lazy.parallel.set.RootUnsortedSetBatch;
import org.eclipse.collections.impl.lazy.parallel.set.SelectUnsortedSetBatch;
import org.eclipse.collections.impl.lazy.parallel.set.UnsortedSetBatch;

public final class UnifiedUnsortedSetBatch extends AbstractBatch<T> implements RootUnsortedSetBatch<T>
{
    private final UnifiedSet<T> unifiedSet;
    private final int chunkStartIndex;
    private final int chunkEndIndex;

    public UnifiedUnsortedSetBatch(UnifiedSet<T> parent, int chunkStartIndex, int chunkEndIndex)
    {
        this.unifiedSet = parent;
        this.chunkStartIndex = chunkStartIndex;
        this.chunkEndIndex = chunkEndIndex;
    }

    @Override
    public void forEach(Procedure<? super T> procedure)
    {
        this.unifiedSet.each(procedure, this.chunkStartIndex, this.chunkEndIndex);
    }

    @Override
    public boolean anySatisfy(Predicate<? super T> predicate)
    {
        return this.unifiedSet.shortCircuit(predicate, true, true, false, this.chunkStartIndex, this.chunkEndIndex);
    }

    @Override
    public boolean allSatisfy(Predicate<? super T> predicate)
    {
        return this.unifiedSet.shortCircuit(predicate, false, false, true, this.chunkStartIndex, this.chunkEndIndex);
    }

    @Override
    public T detect(Predicate<? super T> predicate)
    {
        return this.unifiedSet.detect(predicate, this.chunkStartIndex, this.chunkEndIndex);
    }

    @Override
    public UnsortedSetBatch<T> select(Predicate<? super T> predicate)
    {
        return new SelectUnsortedSetBatch<>(this, predicate);
    }

    @Override
    public <V> UnsortedBagBatch<V> collect(Function<? super T, ? extends V> function)
    {
        return new CollectUnsortedBagBatch<>(this, function);
    }

    @Override
    public <V> UnsortedBagBatch<V> flatCollect(Function<? super T, ? extends Iterable<V>> function)
    {
        return new FlatCollectUnsortedBagBatch<>(this, function);
    }
}