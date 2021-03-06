/*******************************************************************************
 *  _  _ ___ ___     _ _
 * | \| | __/ __| __| | |__
 * | .` | _|\__ \/ _` | '_ \
 * |_|\_|_| |___/\__,_|_.__/
 *
 * Copyright (c) 2014-2016. The NFSdb project and its contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.nfsdb.query.spi;

import com.nfsdb.Journal;
import com.nfsdb.Partition;
import com.nfsdb.ex.JournalException;
import com.nfsdb.ex.JournalRuntimeException;
import com.nfsdb.iter.*;
import com.nfsdb.misc.Interval;
import com.nfsdb.misc.Rows;
import com.nfsdb.query.OrderedResultSet;
import com.nfsdb.query.OrderedResultSetBuilder;
import com.nfsdb.query.api.QueryAll;
import com.nfsdb.query.api.QueryAllBuilder;
import com.nfsdb.std.ObjList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Iterator;

@SuppressFBWarnings({"EXS_EXCEPTION_SOFTENING_NO_CHECKED", "EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS"})
public class QueryAllImpl<T> implements QueryAll<T> {

    private final Journal<T> journal;

    public QueryAllImpl(Journal<T> journal) {
        this.journal = journal;
    }

    @Override
    public OrderedResultSet<T> asResultSet() throws JournalException {
        return journal.iteratePartitions(new OrderedResultSetBuilder<T>() {
            @Override
            public void read(long lo, long hi) throws JournalException {
                result.ensureCapacity((int) (hi - lo + 1));
                for (long i = lo; i < hi + 1; i++) {
                    result.add(Rows.toRowID(partition.getPartitionIndex(), i));
                }
            }
        });
    }

    @Override
    public JournalPeekingIterator<T> bufferedIterator() {
        return new JournalBufferedIterator<>(journal, createRanges());
    }

    @Override
    public JournalPeekingIterator<T> bufferedIterator(Interval interval) {
        return new JournalBufferedIterator<>(journal, createRanges(interval));
    }

    @Override
    public JournalPeekingIterator<T> bufferedIterator(long rowid) {
        return new JournalBufferedIterator<>(journal, createRanges(rowid));
    }

    @Override
    public JournalConcurrentIterator<T> concurrentIterator() {
        return new JournalConcurrentIterator<>(journal, createRanges(), 1024);
    }

    @Override
    public JournalConcurrentIterator<T> concurrentIterator(Interval interval) {
        return new JournalConcurrentIterator<>(journal, createRanges(interval), 1024);
    }

    @Override
    public JournalConcurrentIterator<T> concurrentIterator(long rowid) {
        return new JournalConcurrentIterator<>(journal, createRanges(rowid), 1024);
    }

    @Override
    public JournalPeekingIterator<T> incrementBufferedIterator() {
        try {
            long lo = journal.getMaxRowID();
            journal.refresh();
            return new JournalBufferedIterator<>(journal, createRanges(journal.incrementRowID(lo)));
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
    }

    @Override
    public JournalPeekingIterator<T> incrementIterator() {
        try {
            long lo = journal.getMaxRowID();
            journal.refresh();
            return new JournalIteratorImpl<>(journal, createRanges(journal.incrementRowID(lo)));
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
    }

    @Override
    public JournalIterator<T> iterator(Interval interval) {
        return new JournalIteratorImpl<>(journal, createRanges(interval));
    }

    @Override
    public JournalPeekingIterator<T> iterator(long rowid) {
        return new JournalIteratorImpl<>(journal, createRanges(rowid));
    }

    @Override
    public long size() {
        try {
            return journal.size();
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
    }

    @Override
    public QueryAllBuilder<T> withKeys(String... values) {
        return withSymValues(journal.getMetadata().getKeyColumn(), values);
    }

    @Override
    public QueryAllBuilder<T> withSymValues(String symbol, String... values) {
        QueryAllBuilderImpl<T> result = new QueryAllBuilderImpl<>(journal);
        result.setSymbol(symbol, values);
        return result;
    }

    @Override
    public Iterator<T> iterator() {
        return new JournalIteratorImpl<>(journal, createRanges());
    }

    private ObjList<JournalIteratorRange> createRanges() {
        final int partitionCount = journal.getPartitionCount();
        ObjList<JournalIteratorRange> ranges = new ObjList<>(partitionCount);
        try {
            for (int i = 0; i < partitionCount; i++) {
                Partition<T> p = journal.getPartition(i, true);
                long size = p.size();
                if (size > 0) {
                    ranges.add(new JournalIteratorRange(p.getPartitionIndex(), 0, size - 1));
                }
            }
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
        return ranges;
    }

    private ObjList<JournalIteratorRange> createRanges(Interval interval) {
        final ObjList<JournalIteratorRange> ranges = new ObjList<>();
        try {
            journal.iteratePartitions(new OrderedResultSetBuilder<T>(interval) {
                @Override
                public void read(long lo, long hi) throws JournalException {
                    ranges.add(new JournalIteratorRange(partition.getPartitionIndex(), lo, hi));
                }
            });
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
        return ranges;
    }

    private ObjList<JournalIteratorRange> createRanges(long lo) {
        ObjList<JournalIteratorRange> ranges = new ObjList<>();
        int loPartitionID = Rows.toPartitionIndex(lo);
        long loLocalRowID = Rows.toLocalRowID(lo);

        try {
            int count = journal.getPartitionCount();
            for (int i = loPartitionID; i < count; i++) {
                long localRowID = 0;
                if (i == loPartitionID) {
                    localRowID = loLocalRowID;
                }

                Partition<T> p = journal.getPartition(i, true);
                long size = p.size();
                if (size > 0) {
                    ranges.add(new JournalIteratorRange(p.getPartitionIndex(), localRowID, size - 1));
                }
            }
            return ranges;
        } catch (JournalException e) {
            throw new JournalRuntimeException(e);
        }
    }
}
