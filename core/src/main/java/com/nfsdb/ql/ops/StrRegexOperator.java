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

package com.nfsdb.ql.ops;

import com.nfsdb.ql.Record;
import com.nfsdb.std.ObjectFactory;
import com.nfsdb.store.ColumnType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrRegexOperator extends AbstractBinaryOperator {

    public final static ObjectFactory<Function> FACTORY = new ObjectFactory<Function>() {
        @Override
        public Function newInstance() {
            return new StrRegexOperator();
        }
    };

    private Matcher matcher;

    private StrRegexOperator() {
        super(ColumnType.BOOLEAN);
    }

    @Override
    public boolean getBool(Record rec) {
        return matcher.reset(lhs.getFlyweightStr(rec)).find();
    }

    @Override
    public void setRhs(VirtualColumn rhs) {
        super.setRhs(rhs);
        matcher = Pattern.compile(rhs.getStr(null).toString()).matcher("");
    }
}
