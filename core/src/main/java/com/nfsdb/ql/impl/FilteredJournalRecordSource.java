/*
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
 */

package com.nfsdb.ql.impl;

import com.nfsdb.ex.JournalException;
import com.nfsdb.factory.JournalReaderFactory;
import com.nfsdb.factory.configuration.RecordMetadata;
import com.nfsdb.ql.Record;
import com.nfsdb.ql.RecordCursor;
import com.nfsdb.ql.RecordSource;
import com.nfsdb.ql.StorageFacade;
import com.nfsdb.ql.ops.AbstractRecordSource;
import com.nfsdb.ql.ops.VirtualColumn;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class FilteredJournalRecordSource extends AbstractRecordSource {

    private final RecordSource delegate;
    private final VirtualColumn filter;
    private RecordCursor cursor;
    private Record record;

    public FilteredJournalRecordSource(RecordSource delegate, VirtualColumn filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public Record getByRowId(long rowId) {
        return cursor.getByRowId(rowId);
    }

    @Override
    public StorageFacade getStorageFacade() {
        return cursor.getStorageFacade();
    }

    @Override
    public RecordMetadata getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public RecordCursor prepareCursor(JournalReaderFactory factory) throws JournalException {
        this.cursor = delegate.prepareCursor(factory);
        filter.prepare(cursor.getStorageFacade());
        return this;
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public boolean supportsRowIdAccess() {
        return delegate.supportsRowIdAccess();
    }

    @Override
    public boolean hasNext() {
        while (cursor.hasNext()) {
            record = cursor.next();
            if (filter.getBool(record)) {
                return true;
            }
        }
        return false;
    }

    @SuppressFBWarnings({"IT_NO_SUCH_ELEMENT"})
    @Override
    public Record next() {
        return record;
    }
}
