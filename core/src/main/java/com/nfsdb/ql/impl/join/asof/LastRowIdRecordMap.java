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

package com.nfsdb.ql.impl.join.asof;

import com.nfsdb.factory.configuration.RecordColumnMetadata;
import com.nfsdb.factory.configuration.RecordMetadata;
import com.nfsdb.ql.Record;
import com.nfsdb.ql.RecordCursor;
import com.nfsdb.ql.StorageFacade;
import com.nfsdb.ql.impl.join.ByteMetadata;
import com.nfsdb.ql.impl.join.LongMetadata;
import com.nfsdb.ql.impl.map.MapValues;
import com.nfsdb.ql.impl.map.MultiMap;
import com.nfsdb.std.CharSequenceHashSet;
import com.nfsdb.std.IntHashSet;
import com.nfsdb.std.ObjHashSet;
import com.nfsdb.std.ObjList;
import com.nfsdb.store.ColumnType;
import com.nfsdb.store.SymbolTable;

public class LastRowIdRecordMap implements LastRecordMap {
    private static final ObjList<RecordColumnMetadata> valueMetadata = new ObjList<>();
    private final MultiMap map;
    private final IntHashSet slaveKeyIndexes;
    private final IntHashSet masterKeyIndexes;
    private final ObjList<ColumnType> slaveKeyTypes;
    private final ObjList<ColumnType> masterKeyTypes;
    private final RecordMetadata metadata;
    private RecordCursor slaveCursor;

    public LastRowIdRecordMap(
            RecordMetadata masterMetadata,
            RecordMetadata slaveMetadata,
            CharSequenceHashSet masterKeyColumns,
            CharSequenceHashSet slaveKeyColumns
    ) {
        final int ksz = masterKeyColumns.size();
        this.masterKeyTypes = new ObjList<>(ksz);
        this.slaveKeyTypes = new ObjList<>(ksz);
        this.masterKeyIndexes = new IntHashSet(ksz);
        this.slaveKeyIndexes = new IntHashSet(ksz);

        // collect key field indexes for slave
        ObjHashSet<String> keyCols = new ObjHashSet<>(ksz);

        for (int i = 0; i < ksz; i++) {
            int idx;
            idx = masterMetadata.getColumnIndex(masterKeyColumns.get(i));
            masterKeyTypes.add(masterMetadata.getColumnQuick(idx).getType());
            masterKeyIndexes.add(idx);

            idx = slaveMetadata.getColumnIndex(slaveKeyColumns.get(i));
            slaveKeyIndexes.add(idx);
            slaveKeyTypes.add(slaveMetadata.getColumnQuick(idx).getType());
            keyCols.add(slaveMetadata.getColumnName(idx));
        }
        this.map = new MultiMap(slaveMetadata, keyCols, valueMetadata, null);
        this.metadata = slaveMetadata;
    }

    @Override
    public void close() {
        map.close();
    }

    public Record get(Record master) {
        MapValues values = getByMaster(master);
        if (values == null || values.getByte(1) == 1) {
            return null;
        }
        values.putByte(1, (byte) 1);
        return slaveCursor.getByRowId(values.getLong(0));
    }

    public RecordMetadata getMetadata() {
        return metadata;
    }

    @Override
    public StorageFacade getStorageFacade() {
        return slaveCursor.getStorageFacade();
    }

    public void put(Record record) {
        MapValues values = getBySlave(record);
        values.putLong(0, record.getRowId());
        values.putByte(1, (byte) 0);
    }

    @Override
    public void reset() {
        map.clear();
    }

    public void setSlaveCursor(RecordCursor cursor) {
        this.slaveCursor = cursor;
    }

    public SymbolTable getSymbolTable(String name) {
        return getStorageFacade().getSymbolTable(name);
    }

    private MapValues getByMaster(Record record) {
        return map.getValues(RecordUtils.createKey(map, record, masterKeyIndexes, masterKeyTypes));
    }

    private MapValues getBySlave(Record record) {
        return map.getOrCreateValues(RecordUtils.createKey(map, record, slaveKeyIndexes, slaveKeyTypes));
    }

    static {
        valueMetadata.add(LongMetadata.INSTANCE);
        valueMetadata.add(ByteMetadata.INSTANCE);
    }
}
