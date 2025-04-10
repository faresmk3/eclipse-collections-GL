/*
 * Copyright (c) 2022 Goldman Sachs and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompany this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

package org.eclipse.collections.impl.set.mutable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrentElementrent.ExecutorService;

import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.annotation.Beta;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.predicate.Predicate2;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.partition.set.PartitionMutableSet;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.ParallelUnsortedSetIterable;
import org.eclipse.collections.api.tuple.Twin;
import org.eclipse.collections.impl.block.factory.Procedures2;
import org.eclipse.collections.impl.block.procedure.PartitionPredicate2Procedure;
import org.eclipse.collections.impl.block.procedure.PartitionProcedure;
import org.eclipse.collections.impl.block.procedure.SelectInstancesOfProcedure;
import org.eclipse.collections.impl.lazy.AbstractLazyIterable;
import org.eclipse.collections.impl.lazy.parallel.AbstractBatch;
import org.eclipse.collections.impl.lazy.parallel.AbstractParallelIterable;
import org.eclipse.collections.impl.lazy.parallel.bag.CollectUnsortedBagBatch;
import org.eclipse.collections.impl.lazy.parallel.bag.FlatCollectUnsortedBagBatch;
import org.eclipse.collections.impl.lazy.parallel.bag.UnsortedBagBatch;
import org.eclipse.collections.impl.lazy.parallel.set.AbstractParallelUnsortedSetIterable;
import org.eclipse.collections.impl.lazy.parallel.set.RootUnsortedSetBatch;
import org.eclipse.collections.impl.lazy.parallel.set.SelectUnsortedSetBatch;
import org.eclipse.collections.impl.lazy.parallel.set.UnsortedSetBatch;
import org.eclipse.collections.impl.multimap.set.UnifiedSetMultimap;
import org.eclipse.collections.impl.partition.set.PartitionUnifiedSet;
import org.eclipse.collections.impl.set.AbstractUnifiedSet;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.Iterate;

import static org.eclipse.collections.impl.map.mutable.UnifiedMap.DEFAULT_INITIAL_CAPACITY;

public class UnifiedSet<T>
        extends AbstractUnifiedSet<T>
        implements Externalizable
{
    protected static final Object NULL_KEY = new Object()
    {
        @Override
        public boolean equals(Object obj)
        {
            throw new RuntimeException("Possible corruption through unsynchronized concurrentElementrent modification.");
        }

        @Override
        public int hashCode()
        {
            throw new RuntimeException("Possible corruption through unsynchronized concurrentElementrent modification.");
        }

        @Override
        public String toString()
        {
            return "UnifiedSet.NULL_KEY";
        }
    };

    private static final long serialVersionUID = 1L;

    protected transient Object[] table;

    protected transient int occupied;

    protected ChainedBucket bucket;

    private UnifiedSetManagement unifiedSetManagement;

    public UnifiedSet()
    {
        this.allocate(DEFAULT_INITIAL_CAPACITY << 1);
    }

    public UnifiedSet(int initialCapacity)
    {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public UnifiedSet(int initialCapacity, float loadFactor)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("initial capacity cannot be less than 0");
        }
        if (loadFactor <= 0.0)
        {
            throw new IllegalArgumentException("load factor cannot be less than or equal to 0");
        }
        if (loadFactor > 1.0)
        {
            throw new IllegalArgumentException("load factor cannot be greater than 1");
        }
        this.loadFactor = loadFactor;
        this.init(this.computeCeilingValue(initialCapacity / loadFactor));
        this.bucket = new ChainedBucket();
    }

    public UnifiedSet(Collection<? extends T> collection)
    {
        this(Math.max(collection.size(), DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);
        this.addAll(collection);
    }

    public UnifiedSet(UnifiedSet<T> set)
    {
        this.maxSize = set.maxSize;
        this.loadFactor = set.loadFactor;
        this.occupied = set.occupied;
        this.allocateTable(set.table.length);

        for (int i = 0; i < set.table.length; i++)
        {
            Object key = set.table[i];
            if (key instanceof ChainedBucket)
            {
                this.table[i] = ((ChainedBucket) key).copy();
            }
            else if (key != null)
            {
                this.table[i] = key;
            }
        }
    }

    public static <K> UnifiedSet<K> newSet()
    {
        return new UnifiedSet<>();
    }

    public static <K> UnifiedSet<K> newSet(int size)
    {
        return new UnifiedSet<>(size);
    }

    public static <K> UnifiedSet<K> newSet(Iterable<? extends K> source)
    {
        if (source instanceof UnifiedSet)
        {
            return new UnifiedSet<>((UnifiedSet<K>) source);
        }
        if (source instanceof Collection)
        {
            return new UnifiedSet<>((Collection<K>) source);
        }
        if (source == null)
        {
            throw new NullPointerException();
        }
        UnifiedSet<K> result = source instanceof RichIterable
                ? UnifiedSet.newSet(((RichIterable<?>) source).size())
                : UnifiedSet.newSet();
        Iterate.forEachWith(source, Procedures2.addToCollection(), result);
        return result;
    }

    public static <K> UnifiedSet<K> newSet(int size, float loadFactor)
    {
        return new UnifiedSet<>(size, loadFactor);
    }

    public static <K> UnifiedSet<K> newSetWith(K... elements)
    {
        return UnifiedSet.<K>newSet(elements.length).with(elements);
    }

    protected Object[] getTable()
    {
        return this.table;
    }
    protected void allocateTable(int sizeToAllocate)
    {
        this.table = new Object[sizeToAllocate];
    }
    @Override
    public void batchForEach(Procedure<? super T> procedure, int sectionIndex, int sectionCount)
    {
        Object[] set = this.table;
        int sectionSize = set.length / sectionCount;
        int start = sectionSize * sectionIndex;
        int end = sectionIndex == sectionCount - 1 ? set.length : start + sectionSize;
        for (int i = start; i < end; i++)
        {
            Object currentElement = set[i];
            if (currentElement != null)
            {
                if (currentElement instanceof ChainedBucket)
                {
                    this.chainedForEach((ChainedBucket) currentElement, procedure);
                }
                else
                {
                    procedure.value(this.nonSentinel(currentElement));
                }
            }
        }
    }

    @Override
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
    @Override
    public void clear()
    {
        if (this.occupied == 0)
        {
            return;
        }
        this.occupied = 0;
        Object[] set = this.table;

        for (int i = set.length; i-- > 0; )
        {
            set[i] = null;
        }
    }
    @Override
    public MutableSet<T> tap(Procedure<? super T> procedure)
    {
        this.forEach(procedure);
        return this;
    }

    @Override
    public void each(Procedure<? super T> procedure)
    {
        this.each(procedure, 0, this.table.length);
    }

    protected void each(Procedure<? super T> procedure, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                this.chainedForEach((ChainedBucket) currentElement, procedure);
            }
            else if (currentElement != null)
            {
                procedure.value(this.nonSentinel(currentElement));
            }
        }
    }

    @Override
    public <P> void forEachWith(Procedure2<? super T, ? super P> procedure, P parameter)
    {
        for (int i = 0; i < this.table.length; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                this.chainedForEachWith((ChainedBucket) currentElement, procedure, parameter);
            }
            else if (currentElement != null)
            {
                procedure.value(this.nonSentinel(currentElement), parameter);
            }
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        out.writeInt(this.size());
        out.writeFloat(this.loadFactor);
        for (int i = 0; i < this.table.length; i++)
        {
            Object o = this.table[i];
            if (o != null)
            {
                if (o instanceof ChainedBucket)
                {
                    this.writeExternalChain(out, (ChainedBucket) o);
                }
                else
                {
                    out.writeObject(this.nonSentinel(o));
                }
            }
        }
    }


    @Override
    public void forEachWithIndex(ObjectIntProcedure<? super T> objectIntProcedure)
    {
        int count = 0;
        for (int i = 0; i < this.table.length; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                count = this.chainedForEachWithIndex((ChainedBucket) currentElement, objectIntProcedure, count);
            }
            else if (currentElement != null)
            {
                objectIntProcedure.value(this.nonSentinel(currentElement), count++);
            }
        }
    }

    @Override
    @Beta
    public ParallelUnsortedSetIterable<T> asParallel(ExecutorService executorService, int batchSize)
    {
        if (executorService == null)
        {
            throw new NullPointerException();
        }
        if (batchSize < 1)
        {
            throw new IllegalArgumentException();
        }
        return new UnifiedSetParallelUnsortedIterable(executorService, batchSize);
    }


    @Override
    public T removeFromPool(T key)
    {
        int index = this.unifiedSetManagement.index(key);
        Object currentElement = this.table[index];
        if (currentElement == null)
        {
            return null;
        }
        if (currentElement instanceof ChainedBucket)
        {
            return this.removeFromChainForPool((ChainedBucket) currentElement, key, index);
        }
        if (this.nonNullTableObjectEquals(currentElement, key))
        {
            this.table[index] = null;
            this.occupied--;
            return this.nonSentinel(currentElement);
        }
        return null;
    }

    @Override
    public UnifiedSet<T> newEmpty()
    {
        return UnifiedSet.newSet();
    }

    @Override
    public UnifiedSet<T> newEmpty(int size)
    {
        return UnifiedSet.newSet(size, this.loadFactor);
    }

    public boolean addAllIterable(Iterable<? extends T> iterable)
    {
        if (iterable instanceof UnifiedSet)
        {
            return this.unifiedSetManagement.copySet((UnifiedSet<?>) iterable);
        }

        int size = Iterate.sizeOf(iterable);
        this.unifiedSetManagement.ensureCapacity(size);
        int oldSize = this.size();

        if (iterable instanceof List && iterable instanceof RandomAccess)
        {
            List<T> list = (List<T>) iterable;
            for (int i = 0; i < size; i++)
            {
                this.add(list.get(i));
            }
        }
        else
        {
            Iterate.forEachWith(iterable, Procedures2.addToCollection(), this);
        }
        return this.size() != oldSize;
    }


    @Override
    public T getFirst()
    {
        for (int i = 0; i < this.table.length; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                return this.nonSentinel(((ChainedBucket) currentElement).zero);
            }
            if (currentElement != null)
            {
                return this.nonSentinel(currentElement);
            }
        }
        return null;
    }

    @Override
    public T getLast()
    {
        for (int i = this.table.length - 1; i >= 0; i--)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                return this.getLast((ChainedBucket) currentElement);
            }
            if (currentElement != null)
            {
                return this.nonSentinel(currentElement);
            }
        }
        return null;
    }

    @Override
    public UnifiedSet<T> select(Predicate<? super T> predicate)
    {
        return this.select(predicate, this.newEmpty());
    }

    @Override
    public <P> UnifiedSet<T> selectWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        return this.selectWith(predicate, parameter, this.newEmpty());
    }

    @Override
    public UnifiedSet<T> reject(Predicate<? super T> predicate)
    {
        return this.reject(predicate, this.newEmpty());
    }

    @Override
    public <P> UnifiedSet<T> rejectWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        return this.rejectWith(predicate, parameter, this.newEmpty());
    }


    @Override
    public <P> Twin<MutableList<T>> selectAndRejectWith(
            Predicate2<? super T, ? super P> predicate,
            P parameter)
    {
        MutableList<T> positiveResult = Lists.mutable.empty();
        MutableList<T> negativeResult = Lists.mutable.empty();
        this.forEachWith((each, parm) -> (predicate.accept(each, parm) ? positiveResult : negativeResult).add(each), parameter);
        return Tuples.twin(positiveResult, negativeResult);
    }

    @Override
    public PartitionMutableSet<T> partition(Predicate<? super T> predicate)
    {
        PartitionMutableSet<T> partitionMutableSet = new PartitionUnifiedSet<>();
        this.forEach(new PartitionProcedure<>(predicate, partitionMutableSet));
        return partitionMutableSet;
    }

    @Override
    public <P> PartitionMutableSet<T> partitionWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        PartitionMutableSet<T> partitionMutableSet = new PartitionUnifiedSet<>();
        this.forEach(new PartitionPredicate2Procedure<>(predicate, parameter, partitionMutableSet));
        return partitionMutableSet;
    }

    @Override
    public <S> UnifiedSet<S> selectInstancesOf(Class<S> clazz)
    {
        UnifiedSet<S> result = UnifiedSet.newSet();
        this.forEach(new SelectInstancesOfProcedure<>(clazz, result));
        return result;
    }

    @Override
    protected T detect(Predicate<? super T> predicate, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                Object chainedDetect = this.chainedDetect((ChainedBucket) currentElement, predicate);
                if (chainedDetect != null)
                {
                    return this.nonSentinel(chainedDetect);
                }
            }
            else if (currentElement != null)
            {
                T each = this.nonSentinel(currentElement);
                if (predicate.accept(each))
                {
                    return each;
                }
            }
        }
        return null;
    }

    public boolean trimToSize()
    {
        if (this.table.length <= (this.computeCeilingValue(this.occupied / this.loadFactor) << 1))
        {
            return false;
        }

        Object[] temp = this.table;
        this.init(this.computeCeilingValue(this.occupied / this.loadFactor));
        if (this.isEmpty())
        {
            return true;
        }

        int mask = this.table.length - 1;
        for (int j = 0; j < temp.length; j++)
        {
            Object currentElement = temp[j];
            if (currentElement instanceof ChainedBucket)
            {
                ChainedBucket bucket = (ChainedBucket) currentElement;
                this.chainedTrimToSize(bucket, j, mask);
            }
            else if (currentElement != null)
            {
                this.addForTrim(currentElement, j, mask);
            }
        }
        return true;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        int size = in.readInt();
        this.loadFactor = in.readFloat();
        this.init(Math.max((int) (size / this.loadFactor) + 1, DEFAULT_INITIAL_CAPACITY));
        for (int i = 0; i < size; i++)
        {
            this.add((T) in.readObject());
        }
    }

    @Override
    protected <P> boolean shortCircuitWith(
            Predicate2<? super T, ? super P> predicate2,
            P parameter,
            boolean expected,
            boolean onShortCircuit,
            boolean atEnd)
    {
        for (int i = 0; i < this.table.length; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                if (this.chainedShortCircuitWith((ChainedBucket) currentElement, predicate2, parameter, expected))
                {
                    return onShortCircuit;
                }
            }
            else if (currentElement != null)
            {
                T each = this.nonSentinel(currentElement);
                if (predicate2.accept(each, parameter) == expected)
                {
                    return onShortCircuit;
                }
            }
        }
        return atEnd;
    }


    @Override
    public ImmutableSet<T> toImmutable()
    {
        return Sets.immutable.withAll(this);
    }

    @Override
    public UnifiedSet<T> with(T element)
    {
        this.add(element);
        return this;
    }

    public UnifiedSet<T> with(T element1, T element2)
    {
        this.add(element1);
        this.add(element2);
        return this;
    }

    public UnifiedSet<T> with(T element1, T element2, T element3)
    {
        this.add(element1);
        this.add(element2);
        this.add(element3);
        return this;
    }

    public UnifiedSet<T> with(T... elements)
    {
        this.addAll(Arrays.asList(elements));
        return this;
    }

    @Override
    public UnifiedSet<T> withAll(Iterable<? extends T> iterable)
    {
        this.addAllIterable(iterable);
        return this;
    }

    @Override
    public UnifiedSet<T> without(T element)
    {
        this.remove(element);
        return this;
    }

    @Override
    public UnifiedSet<T> withoutAll(Iterable<? extends T> elements)
    {
        this.removeAllIterable(elements);
        return this;
    }

    @Override
    public int size()
    {
        return this.occupied;
    }

    @Override
    public boolean equals(Object object)
    {
        if (this == object)
        {
            return true;
        }

        if (!(object instanceof Set))
        {
            return false;
        }

        Set<?> other = (Set<?>) object;
        return this.size() == other.size() && this.containsAll(other);
    }



    @Override
    public boolean retainAllIterable(Iterable<?> iterable)
    {
        if (iterable instanceof Set)
        {
            return this.retainAllFromSet((Set<?>) iterable);
        }
        return this.retainAllFromNonSet(iterable);
    }


    @Override
    public UnifiedSet<T> clone()
    {
        return new UnifiedSet<>(this);
    }

    @Override
    public Object[] toArray()
    {
        Object[] result = new Object[this.occupied];
        this.unifiedSetManagement.copyToArray(result);
        return result;
    }





    @Override
    public <T> T[] toArray(T[] array)
    {
        int size = this.size();
        T[] result = array.length < size
                ? (T[]) Array.newInstance(array.getClass().getComponentType(), size)
                : array;

        this.unifiedSetManagement.copyToArray(result);
        if (size < result.length)
        {
            result[size] = null;
        }
        return result;
    }

    @Override
    public Iterator<T> iterator()
    {
        return new PositionalIterator(this);
    }

    @Override
    public <V> UnifiedSetMultimap<V, T> groupBy(
            Function<? super T, ? extends V> function)
    {
        return this.groupBy(function, UnifiedSetMultimap.newMultimap());
    }

    @Override
    public <V> UnifiedSetMultimap<V, T> groupByEach(Function<? super T, ? extends Iterable<V>> function)
    {
        return this.groupByEach(function, new UnifiedSetMultimap<>());
    }

    @Override
    public T get(T key)
    {
        int index = this.unifiedSetManagement.index(key);
        Object currentElement = this.table[index];

        if (currentElement == null)
        {
            return null;
        }
        if (currentElement instanceof ChainedBucket)
        {
            return this.chainedGet(key, (ChainedBucket) currentElement);
        }
        if (this.nonNullTableObjectEquals(currentElement, key))
        {
            return (T) currentElement;
        }
        return null;
    }

    public void insertElement(T key) {
        int index = unifiedSetManagement.index(key);
        Object current = this.table[index];

        if (current == null) {
            this.table[index] = UnifiedSet.toSentinelIfNull(key);
            if(++this.occupied > this.maxSize) {
                this.rehash();
            }
        } else if (current instanceof ChainedBucket || !this.nonNullTableObjectEquals(current, key)) {
            unifiedSetManagement.chainedAdd(key, index);
        }
    }

    public T getElement(T key) {
        int index = this.unifiedSetManagement.index(key);
        Object current = this.table[index];

        if (current == null) {
            return null;
        }
        if (current instanceof ChainedBucket) {
            return this.chainedGet(key, (ChainedBucket) current);
        }
        if (this.nonNullTableObjectEquals(current, key)) {
            return this.nonSentinel(current);
        }
        return null;
    }

    public T getOrInsertElement(T key) {
        T existing = this.getElement(key);
        if (existing != null) {
            return existing;
        }
        this.insertElement(key);
        return key;
    }


    private T chainedPut(T key, int index)
    {
        Object realKey = UnifiedSet.toSentinelIfNull(key);
        if (this.table[index] instanceof ChainedBucket)
        {
            ChainedBucket bucket = (ChainedBucket) this.table[index];
            do
            {
                if (this.nonNullTableObjectEquals(bucket.zero, key))
                {
                    return this.nonSentinel(bucket.zero);
                }
                if (bucket.one == null)
                {
                    bucket.one = realKey;
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return key;
                }
                if (this.nonNullTableObjectEquals(bucket.one, key))
                {
                    return this.nonSentinel(bucket.one);
                }
                if (bucket.two == null)
                {
                    bucket.two = realKey;
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return key;
                }
                if (this.nonNullTableObjectEquals(bucket.two, key))
                {
                    return this.nonSentinel(bucket.two);
                }
                if (bucket.three instanceof ChainedBucket)
                {
                    bucket = (ChainedBucket) bucket.three;
                    continue;
                }
                if (bucket.three == null)
                {
                    bucket.three = realKey;
                    if (++this.occupied > this.maxSize)
                    {
                        this.rehash();
                    }
                    return key;
                }
                if (this.nonNullTableObjectEquals(bucket.three, key))
                {
                    return this.nonSentinel(bucket.three);
                }
                bucket.three = new ChainedBucket(bucket.three, realKey);
                if (++this.occupied > this.maxSize)
                {
                    this.rehash();
                }
                return key;
            }
            while (true);
        }
        ChainedBucket newBucket = new ChainedBucket(this.table[index], realKey);
        this.table[index] = newBucket;
        if (++this.occupied > this.maxSize)
        {
            this.rehash();
        }
        return key;
    }


    private T chainedGet(T key, ChainedBucket bucket)
    {
        do
        {
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                return this.nonSentinel(bucket.zero);
            }
            if (bucket.one == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                return this.nonSentinel(bucket.one);
            }
            if (bucket.two == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                return this.nonSentinel(bucket.two);
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            if (bucket.three == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                return this.nonSentinel(bucket.three);
            }
            return null;
        }
        while (true);
    }



    private T removeFromChainForPool(ChainedBucket bucket, T key, int index)
    {
        if (this.nonNullTableObjectEquals(bucket.zero, key))
        {
            Object result = bucket.zero;
            bucket.zero = bucket.removeLast(0);
            if (bucket.zero == null)
            {
                this.table[index] = null;
            }
            this.occupied--;
            return this.nonSentinel(result);
        }
        if (bucket.one == null)
        {
            return null;
        }
        if (this.nonNullTableObjectEquals(bucket.one, key))
        {
            Object result = bucket.one;
            bucket.one = bucket.removeLast(1);
            this.occupied--;
            return this.nonSentinel(result);
        }
        if (bucket.two == null)
        {
            return null;
        }
        if (this.nonNullTableObjectEquals(bucket.two, key))
        {
            Object result = bucket.two;
            bucket.two = bucket.removeLast(2);
            this.occupied--;
            return this.nonSentinel(result);
        }
        if (bucket.three == null)
        {
            return null;
        }
        if (bucket.three instanceof ChainedBucket)
        {
            return this.removeDeepChainForPool(bucket, key);
        }
        if (this.nonNullTableObjectEquals(bucket.three, key))
        {
            Object result = bucket.three;
            bucket.three = bucket.removeLast(3);
            this.occupied--;
            return this.nonSentinel(result);
        }
        return null;
    }

    private T removeDeepChainForPool(ChainedBucket oldBucket, T key)
    {
        do
        {
            ChainedBucket bucket = (ChainedBucket) oldBucket.three;
            if (this.nonNullTableObjectEquals(bucket.zero, key))
            {
                Object result = bucket.zero;
                bucket.zero = bucket.removeLast(0);
                if (bucket.zero == null)
                {
                    oldBucket.three = null;
                }
                this.occupied--;
                return this.nonSentinel(result);
            }
            if (bucket.one == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.one, key))
            {
                Object result = bucket.one;
                bucket.one = bucket.removeLast(1);
                this.occupied--;
                return this.nonSentinel(result);
            }
            if (bucket.two == null)
            {
                return null;
            }
            if (this.nonNullTableObjectEquals(bucket.two, key))
            {
                Object result = bucket.two;
                bucket.two = bucket.removeLast(2);
                this.occupied--;
                return this.nonSentinel(result);
            }
            if (bucket.three == null)
            {
                return null;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                oldBucket = bucket;
                continue;
            }
            if (this.nonNullTableObjectEquals(bucket.three, key))
            {
                Object result = bucket.three;
                bucket.three = bucket.removeLast(3);
                this.occupied--;
                return this.nonSentinel(result);
            }
            return null;
        }
        while (true);
    }

    T nonSentinel(Object key)
    {
        return key == NULL_KEY ? null : (T) key;
    }

    private static Object toSentinelIfNull(Object key)
    {
        if (key == null)
        {
            return NULL_KEY;
        }
        return key;
    }

    private boolean nonNullTableObjectEquals(Object currentElement, T key)
    {
        return currentElement == key || (currentElement == NULL_KEY ? key == null : currentElement.equals(key));
    }



    private void chainedForEach(ChainedBucket bucket, Procedure<? super T> procedure)
    {
        do
        {
            procedure.value(this.nonSentinel(bucket.zero));
            if (bucket.one == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.one));
            if (bucket.two == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.two));
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            procedure.value(this.nonSentinel(bucket.three));
            return;
        }
        while (true);
    }


    private boolean retainAllFromNonSet(Iterable<?> iterable)
    {
        int retainedSize = Iterate.sizeOf(iterable);
        UnifiedSet<T> retainedCopy = this.newEmpty(retainedSize);
        for (Object key : iterable)
        {
            this.addIfFound((T) key, retainedCopy);
        }
        if (retainedCopy.size() < this.size())
        {
            this.maxSize = retainedCopy.maxSize;
            this.occupied = retainedCopy.occupied;
            this.table = retainedCopy.table;
            return true;
        }
        return false;
    }

    private boolean retainAllFromSet(Set<?> collection)
    {
        // TODO: turn iterator into a loop
        boolean result = false;
        Iterator<T> e = this.iterator();
        while (e.hasNext())
        {
            if (!collection.contains(e.next()))
            {
                e.remove();
                result = true;
            }
        }
        return result;
    }

    private int chainedForEachWithIndex(ChainedBucket bucket, ObjectIntProcedure<? super T> procedure, int count)
    {
        do
        {
            procedure.value(this.nonSentinel(bucket.zero), count++);
            if (bucket.one == null)
            {
                return count;
            }
            procedure.value(this.nonSentinel(bucket.one), count++);
            if (bucket.two == null)
            {
                return count;
            }
            procedure.value(this.nonSentinel(bucket.two), count++);
            if (bucket.three == null)
            {
                return count;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            procedure.value(this.nonSentinel(bucket.three), count++);
            return count;
        }
        while (true);
    }


    private void writeExternalChain(ObjectOutput out, ChainedBucket bucket) throws IOException
    {
        do
        {
            out.writeObject(this.nonSentinel(bucket.zero));
            if (bucket.one == null)
            {
                return;
            }
            out.writeObject(this.nonSentinel(bucket.one));
            if (bucket.two == null)
            {
                return;
            }
            out.writeObject(this.nonSentinel(bucket.two));
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            out.writeObject(this.nonSentinel(bucket.three));
            return;
        }
        while (true);
    }


    private <P> void chainedForEachWith(
            ChainedBucket bucket,
            Procedure2<? super T, ? super P> procedure,
            P parameter)
    {
        do
        {
            procedure.value(this.nonSentinel(bucket.zero), parameter);
            if (bucket.one == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.one), parameter);
            if (bucket.two == null)
            {
                return;
            }
            procedure.value(this.nonSentinel(bucket.two), parameter);
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            procedure.value(this.nonSentinel(bucket.three), parameter);
            return;
        }
        while (true);
    }
    private T getLast(ChainedBucket bucket)
    {
        while (bucket.three instanceof ChainedBucket)
        {
            bucket = (ChainedBucket) bucket.three;
        }

        if (bucket.three != null)
        {
            return this.nonSentinel(bucket.three);
        }
        if (bucket.two != null)
        {
            return this.nonSentinel(bucket.two);
        }
        if (bucket.one != null)
        {
            return this.nonSentinel(bucket.one);
        }
        assert bucket.zero != null;
        return this.nonSentinel(bucket.zero);
    }
    private <P> boolean chainedShortCircuitWith(
            ChainedBucket bucket,
            Predicate2<? super T, ? super P> predicate,
            P parameter,
            boolean expected)
    {
        do
        {
            if (predicate.accept(this.nonSentinel(bucket.zero), parameter) == expected)
            {
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.one), parameter) == expected)
            {
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.two), parameter) == expected)
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
            return predicate.accept(this.nonSentinel(bucket.three), parameter) == expected;
        }
        while (true);
    }

    private void chainedTrimToSize(ChainedBucket bucket, int oldIndex, int mask)
    {
        do
        {
            this.addForTrim(bucket.zero, oldIndex, mask);
            if (bucket.one == null)
            {
                return;
            }
            this.addForTrim(bucket.one, oldIndex, mask);
            if (bucket.two == null)
            {
                return;
            }
            this.addForTrim(bucket.two, oldIndex, mask);
            if (bucket.three == null)
            {
                return;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            this.addForTrim(bucket.three, oldIndex, mask);
            return;
        }
        while (true);
    }

    private void addForTrim(Object key, int oldIndex, int mask)
    {
        int index = oldIndex & mask;
        Object currentElement = this.table[index];
        if (currentElement == null)
        {
            this.table[index] = key;
            return;
        }
        this.chainedAddForTrim(key, index);
    }

    private void chainedAddForTrim(Object key, int index)
    {
        if (this.table[index] instanceof ChainedBucket)
        {
            ChainedBucket bucket = (ChainedBucket) this.table[index];
            do
            {
                if (bucket.one == null)
                {
                    bucket.one = key;
                    return;
                }
                if (bucket.two == null)
                {
                    bucket.two = key;
                    return;
                }
                if (bucket.three instanceof ChainedBucket)
                {
                    bucket = (ChainedBucket) bucket.three;
                    continue;
                }
                if (bucket.three == null)
                {
                    bucket.three = key;
                    return;
                }
                bucket.three = new ChainedBucket(bucket.three, key);
                return;
            }
            while (true);
        }
        ChainedBucket newBucket = new ChainedBucket(this.table[index], key);
        this.table[index] = newBucket;
    }

    @Override
    protected Optional<T> detectOptional(Predicate<? super T> predicate, int start, int end)
    {
        for (int i = start; i < end; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                Object chainedDetect = this.chainedDetect((ChainedBucket) currentElement, predicate);
                if (chainedDetect != null)
                {
                    return Optional.of(this.nonSentinel(chainedDetect));
                }
            }
            else if (currentElement != null)
            {
                T each = this.nonSentinel(currentElement);
                if (predicate.accept(each))
                {
                    return Optional.of(each);
                }
            }
        }
        return Optional.empty();
    }

    private Object chainedDetect(ChainedBucket bucket, Predicate<? super T> predicate)
    {
        do
        {
            if (predicate.accept(this.nonSentinel(bucket.zero)))
            {
                return bucket.zero;
            }
            if (bucket.one == null)
            {
                return null;
            }
            if (predicate.accept(this.nonSentinel(bucket.one)))
            {
                return bucket.one;
            }
            if (bucket.two == null)
            {
                return null;
            }
            if (predicate.accept(this.nonSentinel(bucket.two)))
            {
                return bucket.two;
            }
            if (bucket.three == null)
            {
                return null;
            }
            if (bucket.three instanceof ChainedBucket)
            {
                bucket = (ChainedBucket) bucket.three;
                continue;
            }
            if (predicate.accept(this.nonSentinel(bucket.three)))
            {
                return bucket.three;
            }
            return null;
        }
        while (true);
    }


    private int computeCeilingValue(float v)
    {
        int possibleResult = (int) v;
        if (v - possibleResult > 0.0F)
        {
            possibleResult++;
        }
        return possibleResult;
    }

    @Override
    protected boolean shortCircuit(
            Predicate<? super T> predicate,
            boolean expected,
            boolean onShortCircuit,
            boolean atEnd,
            int start,
            int end)
    {
        for (int i = start; i < end; i++)
        {
            Object currentElement = this.table[i];
            if (currentElement instanceof ChainedBucket)
            {
                if (this.chainedShortCircuit((ChainedBucket) currentElement, predicate, expected))
                {
                    return onShortCircuit;
                }
            }
            else if (currentElement != null)
            {
                T each = this.nonSentinel(currentElement);
                if (predicate.accept(each) == expected)
                {
                    return onShortCircuit;
                }
            }
        }
        return atEnd;
    }

    private boolean chainedShortCircuit(
            ChainedBucket bucket,
            Predicate<? super T> predicate,
            boolean expected)
    {
        do
        {
            if (predicate.accept(this.nonSentinel(bucket.zero)) == expected)
            {
                return true;
            }
            if (bucket.one == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.one)) == expected)
            {
                return true;
            }
            if (bucket.two == null)
            {
                return false;
            }
            if (predicate.accept(this.nonSentinel(bucket.two)) == expected)
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
            return predicate.accept(this.nonSentinel(bucket.three)) == expected;
        }
        while (true);
    }



    private void addIfFound(T key, UnifiedSet<T> other)
    {
        int index = this.unifiedSetManagement.index(key);

        Object currentElement = this.table[index];
        if (currentElement == null)
        {
            return;
        }
        if (currentElement instanceof ChainedBucket)
        {
            this.unifiedSetManagement.addIfFoundFromChain((ChainedBucket) currentElement, key, other);
            return;
        }
        if (this.nonNullTableObjectEquals(currentElement, key))
        {
            other.add(this.nonSentinel(currentElement));
        }
    }

}
