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

package com.nfsdb.ql.ops.col;

import com.nfsdb.io.sink.CharSink;
import com.nfsdb.ql.Record;
import com.nfsdb.ql.StorageFacade;
import com.nfsdb.ql.ops.AbstractVirtualColumn;
import com.nfsdb.store.ColumnType;
import com.nfsdb.store.SymbolTable;
import com.nfsdb.store.VariableColumn;

public class SymRecordSourceColumn extends AbstractVirtualColumn {
    private final int index;
    private SymbolTable symbolTable;

    public SymRecordSourceColumn(int index) {
        super(ColumnType.SYMBOL);
        this.index = index;
    }

    @Override
    public CharSequence getFlyweightStr(Record rec) {
        return rec.getSym(index);
    }

    @Override
    public int getInt(Record rec) {
        return rec.getInt(index);
    }

    @Override
    public CharSequence getStr(Record rec) {
        return rec.getSym(index);
    }

    @Override
    public void getStr(Record rec, CharSink sink) {
        sink.put(rec.getSym(index));
    }

    @Override
    public int getStrLen(Record rec) {
        CharSequence cs = rec.getSym(index);
        return cs == null ? VariableColumn.NULL_LEN : cs.length();
    }

    @Override
    public String getSym(Record rec) {
        return rec.getSym(index);
    }

    @Override
    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public void prepare(StorageFacade facade) {
        this.symbolTable = facade.getSymbolTable(index);
    }
}
